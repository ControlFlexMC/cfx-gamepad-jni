# Gate Android detection on the os.version prefix only

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

- [中文版](2026-09-19-android-detect-os-version.zh.md)

**Goal:** Change Android platform detection so it only treats `os.version` as Android when it starts with `android-` (case-insensitive). Keep `os.name` in diagnostic logs only.

**Architecture:** Collapse `AndroidPlatform.detect` from a two-argument AND (`os.name==linux` and version prefix) to a single-argument `detect(osVersion)`. `isAndroid()` reads only `os.version` at class load. `NativeLibraryLoader.logAndroidDiagnostics` already logs `os.name`; do not change load logic. Tests go red then green. Update any spec/docs sentence that still says detection requires `os.name=Linux`.

**Tech Stack:** Java 17, JUnit 5, Gradle (`./gradlew test`). If the default JDK is too new, use `JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-17.jdk/Contents/Home`.

**Spec:** `docs/superpowers/specs/2026-09-19-android-platform-and-sdl-load-design.zh.md` / `.en.md`

**Commits:** Do not `git commit` unless the user asked. The Commit steps below are optional.

---

## Files

| File | Role |
|------|------|
| `src/test/java/com/ifels/gamepadjni/AndroidPlatformTest.java` | Single-argument `detect(String)` cases |
| `src/main/java/com/ifels/gamepadjni/AndroidPlatform.java` | Detection implementation; drop `LAUNCHER_OS_NAME` |
| `docs/android/launcher-jvm-args.{zh,en}.md` | “What this means for the detector” gates on the version prefix only |
| `docs/android/launcher-env-vars.{zh,en}.md` | Same |
| `docs/superpowers/specs/2026-09-19-android-platform-and-sdl-load-design.{zh,en}.md` | Drop the “current code still ANDs os.name” sentence |
| `README.md` | Android section: add Mojo, and state that detection uses `os.version` only |

Do not change: `loadLauncherSdl3`, `Sdl3Source`, `logAndroidDiagnostics` (already logs `os.name=`).

---

### Task 1: Tests first (red)

**Files:**
- Modify: `src/test/java/com/ifels/gamepadjni/AndroidPlatformTest.java` (`detect` methods only; leave `nativePlatformDir` / `launcherSdlPath` alone)

- [ ] **Step 1: Switch `detect` tests to a single argument**

Delete every `detect(osName, osVersion)` call. Replace the tests from the “detect() must match both” comment through the comment before `nativePlatformDir()` (keep `nativePlatformDir` and everything after it) with:

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

Delete the old tests `detectAndroidVersionWithoutLinuxOsNameIsNotAndroid` and `detectGnuLinuxOsNameIsNotExactLinux`: `os.name` is no longer an argument. Win/Mac/`GNU/Linux` still count as Android whenever the version is `Android-14`.

- [ ] **Step 2: Run tests and confirm they fail to compile**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-17.jdk/Contents/Home
./gradlew test --tests com.ifels.gamepadjni.AndroidPlatformTest
```

Expected: compile failure. `detect(String)` does not exist, or is still two-argument, so `method detect in class AndroidPlatform cannot be applied to given types`.

If the tests are unexpectedly all green before the production change, stop: the production code was already changed, or the tests were not switched to a single argument.

---

### Task 2: Minimal implementation (green)

**Files:**
- Modify: `src/main/java/com/ifels/gamepadjni/AndroidPlatform.java`

- [ ] **Step 1: Change `detect` / `isAndroid`**

Rewrite the class javadoc so it mentions only `os.version`:

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

Drop `LAUNCHER_OS_NAME`. Change `ANDROID` and `detect` to:

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

An empty string is not null; `"".startsWith("android-")` is false, which matches the tests.

- [ ] **Step 2: Re-run AndroidPlatformTest**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-17.jdk/Contents/Home
./gradlew test --tests com.ifels.gamepadjni.AndroidPlatformTest --tests com.ifels.gamepadjni.Sdl3SourceTest
```

Expected: BUILD SUCCESSFUL, all PASS. `Sdl3SourceTest` still uses `resolveLoadKind(true, …)` and does not depend on the `detect` signature.

- [ ] **Step 3: Full unit tests**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-17.jdk/Contents/Home
./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4 (optional): Commit**

Only if the user explicitly asked to commit:

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

### Task 3: Align docs and spec status sentence

**Files:**
- Modify: `docs/android/launcher-jvm-args.zh.md` (“对检测器的含义” items 1 and 5)
- Modify: `docs/android/launcher-jvm-args.en.md` (same section)
- Modify: `docs/android/launcher-env-vars.zh.md` (`POJAV_NATIVEDIR` section + “对检测器的含义” items 2 and 5)
- Modify: `docs/android/launcher-env-vars.en.md` (same)
- Modify: `docs/superpowers/specs/2026-09-19-android-platform-and-sdl-load-design.zh.md` (opening status sentence)
- Modify: `docs/superpowers/specs/2026-09-19-android-platform-and-sdl-load-design.en.md` (opening status sentence)
- Modify: `README.md` (`### Android` section)

All four launchers **still inject** `-Dos.name=Linux`. Keep that as a launcher fact; only change “what this library uses for detection”.

- [ ] **Step 1: `launcher-jvm-args.zh.md` “对检测器的含义”**

Replace items 1 and 5 with:

```markdown
1. 四家**默认**都会带 `-Dos.name=Linux` 和 `-Dos.version=Android-<RELEASE>`。本库判定只认后者：`os.version` 以 `Android-` 开头（忽略大小写）。`os.name` 只写诊断日志。
5. `POJAV_NATIVEDIR`、Win/Mac 残留环境变量、`os.name` 都不是判定条件。
```

Leave items 2–4 unchanged.

- [ ] **Step 2: Matching section in `launcher-jvm-args.en.md`**

```markdown
1. All four launchers **default** to `-Dos.name=Linux` and `-Dos.version=Android-<RELEASE>`. This library gates only on the latter: `os.version` starting with `Android-` (case-insensitive). `os.name` is diagnostic.
5. `POJAV_NATIVEDIR`, leftover Win/Mac env vars, and `os.name` are not detection gates.
```

- [ ] **Step 3: `launcher-env-vars.zh.md`**

In the `POJAV_NATIVEDIR` section:

```markdown
检测器不把 `POJAV_NATIVEDIR` 当入场券；判定只认 `os.version` 以 `Android-` 开头（忽略大小写）。该变量只用于已经判定为 Android 之后定位 `libSDL3.so`。
```

“对检测器的含义”:

```markdown
2. **`POJAV_NATIVEDIR` 只用来定位 `libSDL3.so`，不当 Android 入场券。** PC Linux 也能 export 同名变量。判定只认 `os.version` 以 `Android-` 开头。
5. Windows/macOS 上残留的 `POJAV_NATIVEDIR` 不参与判定；只认 `os.version` 以 `Android-` 开头。
```

- [ ] **Step 4: Matching English in `launcher-env-vars.en.md`**

```markdown
Do not treat `POJAV_NATIVEDIR` as an Android gate; detection uses only `os.version` starting with `Android-` (case-insensitive). The variable is used to locate `libSDL3.so` after Android has already been identified.
```

```markdown
2. **`POJAV_NATIVEDIR` is only used to locate `libSDL3.so`, not as an Android gate.** A PC Linux process can export the same variable. Detection requires `os.version` starting with `Android-`.
5. Windows/macOS leftover `POJAV_NATIVEDIR` is ignored for detection; only `os.version` starting with `Android-` counts.
```

- [ ] **Step 5: Spec opening: drop “code still ANDs”**

Chinese first paragraph:

```markdown
SDL 搜索加载与平台判定均以本文为准：只认 `os.version` 前缀；`os.name` 只写诊断日志。桌面 HOST / BUNDLED 互斥加载不在本文展开，只说明判定为非 Android 时的分流。启动器 JVM 参数与环境变量的逐项对照见 [docs/android](../../android/README.md)。
```

English:

```markdown
SDL search/load and platform detection follow this document: gate on the `os.version` prefix only; `os.name` is diagnostic. Desktop HOST vs BUNDLED exclusive loading is out of scope except as the non-Android branch. Per-launcher JVM flags and environment variables are in [docs/android](../../android/README.md).
```

- [ ] **Step 6: `README.md` `### Android`**

Add Mojo to the launcher list and a detection sentence. Replace:

```markdown
Android launchers (Amethyst, Zalith Launcher 2, FoldCraftLauncher) run Minecraft
```

with:

```markdown
Android launchers (FCL, ZalithLauncher2, Mojo `v3_openjdk`, Amethyst) run Minecraft
```

Insert before “We therefore:”:

```markdown
Detection is `os.version` starting with `Android-` (case-insensitive). `os.name` is logged only.
```

- [ ] **Step 7: Search leftover “os.name=Linux AND …” detection sentences**

```bash
rg -n 'os\.name=Linux 且|os\.name=Linux and os\.version|requires `os\.name=Linux`' docs README.md src
```

Expected: launcher comparison tables may still mention `-Dos.name=Linux` (that is a launcher-injection fact). There must be no remaining “detection requires both os.name and os.version”.

- [ ] **Step 8 (optional): Commit docs**

Only if the user explicitly asked to commit, commit Task 3 markdown on its own with message: `docs: Android detection uses os.version prefix only`.

---

## Spec coverage

| Spec item | Task |
|-----------|------|
| `detect(osVersion)` gates on the `android-` prefix only | Task 1–2 |
| null / empty / no hyphen / kernel number is false | Task 1 |
| Case-insensitive | Task 1–2 |
| `os.name` is not a detection input; logs only | Task 2 (implementation) + existing `logAndroidDiagnostics` |
| Launcher list is not a runtime allowlist | No code change |
| Two-step SDL search | No change |
| Mojo: `v3_openjdk` only | Docs Task 3 README |
| Align docs/android detection sentences | Task 3 |

No placeholders. The `detect` signature is the same in Task 1 and Task 2.
