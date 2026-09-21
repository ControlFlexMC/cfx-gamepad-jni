# Android 判定改为只认 os.version 前缀

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

- [English version](2026-09-19-android-detect-os-version.en.md)

**Goal:** 把 Android 平台判定改成只认 `os.version` 以 `android-` 开头（忽略大小写）；`os.name` 只留在诊断日志里。

**Architecture:** `AndroidPlatform.detect` 从两参数 AND（`os.name==linux` 且 version 前缀）收成单参数 `detect(osVersion)`。`isAndroid()` 类加载时只读 `os.version`。`NativeLibraryLoader.logAndroidDiagnostics` 已经输出 `os.name`，不用改加载逻辑。测试先红后绿。对照文档里“判定要 os.name=Linux”的句子一并改掉。

**Tech Stack:** Java 17, JUnit 5, Gradle (`./gradlew test`)。本机若默认 JDK 过新，用 `JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-17.jdk/Contents/Home`。

**Spec:** `docs/superpowers/specs/2026-09-19-android-platform-and-sdl-load-design.zh.md` / `.en.md`

**Commits:** 用户未要求提交时不要 `git commit`。下面 Commit 步骤标为可选。

---

## 文件

| 文件 | 职责 |
|------|------|
| `src/test/java/com/ifels/gamepadjni/AndroidPlatformTest.java` | `detect(String)` 单参数用例 |
| `src/main/java/com/ifels/gamepadjni/AndroidPlatform.java` | 判定实现；删掉 `LAUNCHER_OS_NAME` |
| `docs/android/launcher-jvm-args.{zh,en}.md` | 「对检测器的含义」改为只认 version 前缀 |
| `docs/android/launcher-env-vars.{zh,en}.md` | 同上 |
| `docs/superpowers/specs/2026-09-19-android-platform-and-sdl-load-design.{zh,en}.md` | 删掉「当前代码仍 AND os.name」那句 |
| `README.md` | Android 段补 Mojo，并写明判定只认 `os.version` |

不改：`loadLauncherSdl3`、`Sdl3Source`、`logAndroidDiagnostics`（已有 `os.name=` 日志）。

---

### Task 1: 先改测试（红）

**Files:**
- Modify: `src/test/java/com/ifels/gamepadjni/AndroidPlatformTest.java`（`detect` 相关方法；`nativePlatformDir` / `launcherSdlPath` 不动）

- [ ] **Step 1: 把 `detect` 测试改成单参数**

删除所有 `detect(osName, osVersion)` 调用。用下面整段替换 class 里从「detect()：必须同时命中」注释起到 `nativePlatformDir()` 注释之前的测试（保留 `nativePlatformDir` 及之后）。Java 注释用英文：

```java
    // ── detect(): os.version must start with android- (case-insensitive) ──

    @Test
    void detectLauncherAndroidVersion() {
        assertTrue(AndroidPlatform.detect("Android-14"));
        assertTrue(AndroidPlatform.detect("Android-13"));
        assertTrue(AndroidPlatform.detect("Android-10"));
    }

    @Test
    void detectIgnoresCaseOnOsVersionPrefix() {
        assertTrue(AndroidPlatform.detect("android-14"));
        assertTrue(AndroidPlatform.detect("ANDROID-14"));
        assertTrue(AndroidPlatform.detect("AnDrOiD-8.1.0"));
    }

    @Test
    void detectKernelVersionIsNotAndroid() {
        assertFalse(AndroidPlatform.detect("6.1.0"));
        assertFalse(AndroidPlatform.detect("10.0"));
        assertFalse(AndroidPlatform.detect("24.6.0"));
    }

    @Test
    void detectAndroidPrefixWithoutHyphenIsNotAndroid() {
        assertFalse(AndroidPlatform.detect("Android"));
        assertFalse(AndroidPlatform.detect("Android14"));
    }

    @Test
    void detectNullOrEmptyIsNotAndroid() {
        assertFalse(AndroidPlatform.detect(null));
        assertFalse(AndroidPlatform.detect(""));
    }
```

旧测试 `detectAndroidVersionWithoutLinuxOsNameIsNotAndroid` 和 `detectGnuLinuxOsNameIsNotExactLinux` **删掉**：`os.name` 不再是入参，Win/Mac/`GNU/Linux` 只要 version 是 `Android-14` 就算 Android。

- [ ] **Step 2: 跑测试，确认编译失败**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-17.jdk/Contents/Home
./gradlew test --tests com.ifels.gamepadjni.AndroidPlatformTest
```

Expected: 编译失败。`detect(String)` 不存在，或仍是两参数导致 `method detect in class AndroidPlatform cannot be applied to given types`。

若测试在改实现之前意外全绿，停下来：说明生产代码已经被改过或测试没换成单参数。

---

### Task 2: 最小实现（绿）

**Files:**
- Modify: `src/main/java/com/ifels/gamepadjni/AndroidPlatform.java`

- [ ] **Step 1: 改 `detect` / `isAndroid`**

类注释改成只提 `os.version`（英文）：

```java
/**
 * Android platform detection and packaging-path mapping.
 *
 * <p>Android Minecraft: Java Edition launchers run the game in a second JVM in
 * the same process and set {@code os.version=Android-<release>}. A value that
 * starts with {@code Android-} (case-insensitive) is treated as Android.
 * {@code os.name} is not part of detection; it appears in diagnostic logs only.</p>
 *
 * <p>This class intentionally uses no logging and no I/O so unit tests can drive
 * {@link #detect} with explicit arguments.</p>
 */
```

删掉 `LAUNCHER_OS_NAME`。`ANDROID` 和 `detect` 改为：

```java
    private static final boolean ANDROID = detect(
            System.getProperty("os.version", ""));

    /**
     * Gates only on the launcher-overridden {@code os.version} prefix.
     *
     * @param osVersion {@code os.version}; launchers set {@code Android-<release>}
     */
    static boolean detect(String osVersion) {
        if (osVersion == null) {
            return false;
        }
        return osVersion.toLowerCase(Locale.ROOT).startsWith(LAUNCHER_OS_VERSION_PREFIX);
    }
```

空字符串不是 null，`"".startsWith("android-")` 为 false，与测试一致。

- [ ] **Step 2: 再跑 AndroidPlatformTest**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-17.jdk/Contents/Home
./gradlew test --tests com.ifels.gamepadjni.AndroidPlatformTest --tests com.ifels.gamepadjni.Sdl3SourceTest
```

Expected: BUILD SUCCESSFUL，全部 PASS。`Sdl3SourceTest` 仍用 `resolveLoadKind(true, …)`，不依赖 `detect` 签名。

- [ ] **Step 3: 全量单测**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-17.jdk/Contents/Home
./gradlew test
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4（可选）: Commit**

仅当用户明确要求提交时：

```bash
git add src/main/java/com/ifels/gamepadjni/AndroidPlatform.java \
        src/test/java/com/ifels/gamepadjni/AndroidPlatformTest.java
git commit -m "$(cat <<'EOF'
fix: detect Android from os.version prefix only

os.name=Linux is also PC Linux, so it is not a discriminator.
Keep it in Android diagnostic logs only.
EOF
)"
```

---

### Task 3: 对照文档与 spec 状态句

**Files:**
- Modify: `docs/android/launcher-jvm-args.zh.md`（「对检测器的含义」第 1、5 条）
- Modify: `docs/android/launcher-jvm-args.en.md`（同节）
- Modify: `docs/android/launcher-env-vars.zh.md`（`POJAV_NATIVEDIR` 段 + 「对检测器的含义」第 2、5 条）
- Modify: `docs/android/launcher-env-vars.en.md`（同上）
- Modify: `docs/superpowers/specs/2026-09-19-android-platform-and-sdl-load-design.zh.md`（文首状态句）
- Modify: `docs/superpowers/specs/2026-09-19-android-platform-and-sdl-load-design.en.md`（文首状态句）
- Modify: `README.md`（`### Android` 段）

四家启动器**仍然注入** `-Dos.name=Linux`；文档里作为启动器事实保留，只改「本库判定用什么」。

- [ ] **Step 1: `launcher-jvm-args.zh.md`「对检测器的含义」**

把第 1 条和第 5 条换成：

```markdown
1. 四家**默认**都会带 `-Dos.name=Linux` 和 `-Dos.version=Android-<RELEASE>`。本库判定只认后者：`os.version` 以 `Android-` 开头（忽略大小写）。`os.name` 只写诊断日志。
5. `POJAV_NATIVEDIR`、Win/Mac 残留环境变量、`os.name` 都不是判定条件。
```

第 2–4 条不动。

- [ ] **Step 2: `launcher-jvm-args.en.md` 对应段落**

```markdown
1. All four launchers **default** to `-Dos.name=Linux` and `-Dos.version=Android-<RELEASE>`. This library gates only on the latter: `os.version` starting with `Android-` (case-insensitive). `os.name` is diagnostic.
5. `POJAV_NATIVEDIR`, leftover Win/Mac env vars, and `os.name` are not detection gates.
```

- [ ] **Step 3: `launcher-env-vars.zh.md`**

`POJAV_NATIVEDIR` 节里：

```markdown
检测器不把 `POJAV_NATIVEDIR` 当入场券；判定只认 `os.version` 以 `Android-` 开头（忽略大小写）。该变量只用于已经判定为 Android 之后定位 `libSDL3.so`。
```

「对检测器的含义」：

```markdown
2. **`POJAV_NATIVEDIR` 只用来定位 `libSDL3.so`，不当 Android 入场券。** PC Linux 也能 export 同名变量。判定只认 `os.version` 以 `Android-` 开头。
5. Windows/macOS 上残留的 `POJAV_NATIVEDIR` 不参与判定；只认 `os.version` 以 `Android-` 开头。
```

- [ ] **Step 4: `launcher-env-vars.en.md` 对应英文**

```markdown
Do not treat `POJAV_NATIVEDIR` as an Android gate; detection uses only `os.version` starting with `Android-` (case-insensitive). The variable is used to locate `libSDL3.so` after Android has already been identified.
```

```markdown
2. **`POJAV_NATIVEDIR` is only used to locate `libSDL3.so`, not as an Android gate.** A PC Linux process can export the same variable. Detection requires `os.version` starting with `Android-`.
5. Windows/macOS leftover `POJAV_NATIVEDIR` is ignored for detection; only `os.version` starting with `Android-` counts.
```

- [ ] **Step 5: spec 文首，去掉「代码仍 AND」**

中文第一段改成：

```markdown
SDL 搜索加载与平台判定均以本文为准：只认 `os.version` 前缀；`os.name` 只写诊断日志。桌面 HOST / BUNDLED 互斥加载不在本文展开，只说明判定为非 Android 时的分流。启动器 JVM 参数与环境变量的逐项对照见 [docs/android](../../android/README.md)。
```

英文：

```markdown
SDL search/load and platform detection follow this document: gate on the `os.version` prefix only; `os.name` is diagnostic. Desktop HOST vs BUNDLED exclusive loading is out of scope except as the non-Android branch. Per-launcher JVM flags and environment variables are in [docs/android](../../android/README.md).
```

- [ ] **Step 6: `README.md` `### Android`**

把启动器列举补上 Mojo，并加判定句。将：

```markdown
Android launchers (Amethyst, Zalith Launcher 2, FoldCraftLauncher) run Minecraft
```

换成：

```markdown
Android launchers (FCL, ZalithLauncher2, Mojo `v3_openjdk`, Amethyst) run Minecraft
```

在 “We therefore:” 之前插入：

```markdown
Detection is `os.version` starting with `Android-` (case-insensitive). `os.name` is logged only.
```

- [ ] **Step 7: 全文搜残留的「os.name=Linux 且」判定句**

```bash
rg -n 'os\.name=Linux 且|os\.name=Linux and os\.version|requires `os\.name=Linux`' docs README.md src
```

Expected: 启动器对照表里仍会出现 `-Dos.name=Linux`（那是启动器注入事实）。不能再出现「判定必须同时命中 os.name 和 os.version」。

- [ ] **Step 8（可选）: Commit 文档**

仅当用户明确要求提交时，把 Task 3 改过的 markdown 单独提交，message：`docs: Android detection uses os.version prefix only`。

---

## Spec 覆盖

| Spec 条目 | 任务 |
|-----------|------|
| `detect(osVersion)` 只认 `android-` 前缀 | Task 1–2 |
| null / 空 / 无连字符 / 内核号为 false | Task 1 |
| 忽略大小写 | Task 1–2 |
| `os.name` 不参与判定、只打日志 | Task 2（实现）+ 已有 `logAndroidDiagnostics` |
| 启动器名单不是运行时白名单 | 不改代码 |
| SDL 两步搜索 | 不改 |
| Mojo 只认 v3_openjdk | 文档 Task 3 README |
| 对照 docs/android 判定句 | Task 3 |

无占位符。`detect` 签名在 Task 1 与 Task 2 一致。
