# Windows 上 JNA / JNI 双绑定 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `cfx-gamepad-jni` 在同一份 JAR 里同时带 JNI 与 JNA 两套 SDL3 绑定，运行时先试 JNI、失败回退 JNA，调用方零改动。

**Architecture:** `GamepadJNI` 变成公开门面，所有方法转发给一个包私有的 `GamepadBinding` 实例；`JniBinding` 转发到搬走后的 `JniGamepad`（原 native 声明），`JnaBinding` 转发到用 JNA direct mapping 绑定 SDL3 的 `JnaGamepad`。`NativeLibraryLoader` 先加载 SDL3，再尝试 JNI，失败且 JNA 在场时注册 JNA。

**Tech Stack:** Java 8（`--release 8`）、JNA 3.4.0（`compileOnly`，运行期用宿主提供的那一份）、JNI/C、Gradle、CMake。

**Spec:** `docs/superpowers/specs/2026-11-11-windows-jna-jni-dual-binding-design.zh.md`

## Global Constraints

- JNA 只出现在 `compileOnly` 与 `testImplementation`，值固定 `net.java.dev.jna:jna:3.4.0`；**不打包、不 shade、不声明运行时依赖**。
- 编译目标 Java 8 字节码，`--release 8`；新增代码不得使用 Java 9+ API。
- 开关属性 `cfx.gamepadjni.binding`，环境变量 `CFX_GAMEPADJNI_BINDING`，合法值 `jni` / `jna` / `auto`（大小写不敏感，缺省 `auto`，非法值按 `auto` 并打 warn）。
- 接口方法总数 **76**：75 个 SDL 方法（含 macOS 的 `getGCControllerCount`）+ `showCursor`。`GamepadJNI` 公开方法数保持 **75**。
- JNI 符号前缀改为 `Java_com_ifels_gamepadjni_JniGamepad_`；覆盖 9 个平台：`windows-x86_64`、`windows-aarch64`、`darwin-x86_64`、`darwin-aarch64`、`linux-x86_64`、`linux-aarch64`、`android-arm64-v8a`、`android-armeabi-v7a`、`android-x86_64`。
- 不做 GLFW 兜底；不改 `Sdl3Source` 语义；不给 `SdlLogJNI` 做 JNA 回调。
- `bool` 返回值在 JNA 绑定里一律 `byte` + `!= 0`，**不得写成 `boolean`**。
- 未安装绑定时 `Bindings.b()` 必须抛 `UnsatisfiedLinkError`，不得抛 `NullPointerException`。
- 构建产物时间戳取 `SOURCE_DATE_EPOCH` → `git log -1 --format=%ct` → 失败退出，不得回退到当前时间。

## Review Focus

- `GamepadJNI.SDL_GetError()` 在 `initialize()` 之前被调用：必须抛 `UnsatisfiedLinkError`（ControlFlex 只 catch 它），不能是 NPE。
- 绑定的异常类型在 `SystemCursor.setHidden` / `SDL3Manager.initialize` 里被当作控制流用：回退失败、JNA 注册失败都必须以 `UnsatisfiedLinkError` 形式到达调用方。
- 非 Windows 上强制 `binding=jna`：`showCursor` 必须返回 0 而不是抛异常（`user32` 不存在）。
- 中文 Windows 上的手柄名：JNA 侧若使用 `String` 返回类型会按 GBK 解出乱码，必须走 `Pointer` + UTF-8。
- `GamepadJNI` 传 `handle = 0` 的调用：JNA 适配层必须复刻 C 侧 `gp == NULL` 的早退返回值，而不是直接调用 SDL。

---

### Task 1: 绑定开关解析（`BindingMode` / `BindingPolicy`）

**Files:**
- Create: `src/main/java/com/ifels/gamepadjni/BindingMode.java`
- Create: `src/main/java/com/ifels/gamepadjni/BindingPolicy.java`
- Test: `src/test/java/com/ifels/gamepadjni/BindingPolicyTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `BindingMode` 枚举 `AUTO` / `JNI_ONLY` / `JNA_ONLY`；`BindingPolicy.mode()` → `BindingMode`；`BindingPolicy.mode(String property, String env)` → `BindingMode`（纯函数）；`BindingPolicy.jnaPresent()` → `boolean`；`BindingPolicy.jnaPresent(ClassLoader)` → `boolean`。

- [ ] **Step 1: 写失败测试**

```java
// src/test/java/com/ifels/gamepadjni/BindingPolicyTest.java
package com.ifels.gamepadjni;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BindingPolicyTest {

    @Test
    void parsesKnownValues() {
        assertEquals(BindingMode.AUTO, BindingPolicy.parse(null));
        assertEquals(BindingMode.AUTO, BindingPolicy.parse(""));
        assertEquals(BindingMode.AUTO, BindingPolicy.parse("auto"));
        assertEquals(BindingMode.JNI_ONLY, BindingPolicy.parse("jni"));
        assertEquals(BindingMode.JNA_ONLY, BindingPolicy.parse("jna"));
    }

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(BindingMode.JNI_ONLY, BindingPolicy.parse("JNI"));
        assertEquals(BindingMode.JNA_ONLY, BindingPolicy.parse("Jna"));
        assertEquals(BindingMode.AUTO, BindingPolicy.parse("AUTO"));
    }

    @Test
    void unknownValueFallsBackToAuto() {
        assertEquals(BindingMode.AUTO, BindingPolicy.parse("garbage"));
        assertFalse(BindingPolicy.isKnown("garbage"));
        assertTrue(BindingPolicy.isKnown("jna"));
        assertTrue(BindingPolicy.isKnown(null));
    }

    @Test
    void propertyWinsOverEnv() {
        assertEquals(BindingMode.JNI_ONLY, BindingPolicy.mode("jni", "jna"));
        assertEquals(BindingMode.JNA_ONLY, BindingPolicy.mode(null, "jna"));
        assertEquals(BindingMode.JNA_ONLY, BindingPolicy.mode("", "jna"));
        assertEquals(BindingMode.AUTO, BindingPolicy.mode(null, null));
    }

    @Test
    void invalidPropertyDoesNotFallThroughToEnv() {
        assertEquals(BindingMode.AUTO, BindingPolicy.mode("garbage", "jna"));
    }

    @Test
    void jnaPresentUsesGivenLoader() {
        assertTrue(BindingPolicy.jnaPresent(BindingPolicyTest.class.getClassLoader()));
        ClassLoader isolated = new ClassLoader(null) { };
        assertFalse(BindingPolicy.jnaPresent(isolated));
    }
}
```

- [ ] **Step 2: 运行，确认编译失败**

Run: `./gradlew test --tests '*BindingPolicyTest*'`
Expected: 编译错误，`BindingMode` / `BindingPolicy` 不存在。

- [ ] **Step 3: 实现**

`BindingMode.java`：`enum BindingMode { AUTO, JNI_ONLY, JNA_ONLY }`。

`BindingPolicy.java`：

```java
final class BindingPolicy {
    static final String PROPERTY = "cfx.gamepadjni.binding";
    static final String ENV = "CFX_GAMEPADJNI_BINDING";

    static BindingMode parse(String raw)            // null/空白/"auto"/无法识别 -> AUTO
    static boolean isKnown(String raw)              // null/空白/"auto"/"jni"/"jna"（忽略大小写）
    static BindingMode mode()                       // System.getProperty -> getenv -> "auto"；非 isKnown 时 warn
    static BindingMode mode(String property, String env)   // 纯函数，property 非空白时优先
    static boolean jnaPresent()                     // = jnaPresent(GamepadJNI.class.getClassLoader())
    static boolean jnaPresent(ClassLoader cl)       // Class.forName("com.sun.jna.Native", false, cl)
                                                    // 且 Class.forName("com.sun.jna.NativeLibrary", false, cl)
}
```

`parse` 与 `mode(String, String)` 必须是纯函数，不读系统状态。`jnaPresent` 用 `Class.forName(name, false, cl)`，**不得**触发 `Native` 的静态初始化。两个类都用 `private` 构造器。

- [ ] **Step 4: 运行，确认通过**

Run: `./gradlew test --tests '*BindingPolicyTest*'`
Expected: PASS，6 个用例全绿。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/ifels/gamepadjni/BindingMode.java \
        src/main/java/com/ifels/gamepadjni/BindingPolicy.java \
        src/test/java/com/ifels/gamepadjni/BindingPolicyTest.java
git commit -m "feat(binding): add backend switch policy (jni/jna/auto)"
```

---

### Task 2: 失败文案（`BindingFailure`）

**Files:**
- Create: `src/main/java/com/ifels/gamepadjni/BindingFailure.java`
- Test: `src/test/java/com/ifels/gamepadjni/BindingFailureTest.java`

**Interfaces:**
- Consumes: `BindingMode`（Task 1）
- Produces: `BindingFailure.message(BindingMode, Throwable jniFailure, Throwable jnaFailure, boolean jnaPresent, boolean windows)` → `String`；`BindingFailure.of(BindingMode, Throwable, Throwable, boolean)` → `UnsatisfiedLinkError`。

- [ ] **Step 1: 写失败测试**

```java
package com.ifels.gamepadjni;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BindingFailureTest {

    private static final Throwable SAC = new UnsatisfiedLinkError(
            "C:\\Temp\\gamepadjni-1\\gamepadjni.dll: An Application Control policy has blocked this file");

    @Test
    void jnaAbsentNamesTheMissingJar() {
        String msg = BindingFailure.message(BindingMode.AUTO, new UnsatisfiedLinkError("jni boom"), null, false, true);
        assertTrue(msg.startsWith("[gamepad-jni] no usable gamepad backend (binding=auto)"));
        assertTrue(msg.contains("JNI: java.lang.UnsatisfiedLinkError: jni boom"));
        assertTrue(msg.contains("JNA: unavailable"), msg);
        assertTrue(msg.contains("ships no JNA"), msg);
    }

    @Test
    void jnaFailureCarriesItsOwnReason() {
        String msg = BindingFailure.message(BindingMode.AUTO, new UnsatisfiedLinkError("jni boom"),
                new UnsatisfiedLinkError("register boom"), true, true);
        assertTrue(msg.contains("JNA: java.lang.UnsatisfiedLinkError: register boom"), msg);
        assertFalse(msg.contains("unavailable"), msg);
    }

    @Test
    void jniOnlyModeNeverMentionsJna() {
        String msg = BindingFailure.message(BindingMode.JNI_ONLY, new UnsatisfiedLinkError("jni boom"), null, true, true);
        assertTrue(msg.contains("binding=jni"));
        assertTrue(msg.contains("JNI: java.lang.UnsatisfiedLinkError: jni boom"));
        assertFalse(msg.contains("JNA:"), msg);
    }

    @Test
    void sacFailureAddsTheHint() {
        String msg = BindingFailure.message(BindingMode.AUTO, SAC, new UnsatisfiedLinkError("x"), true, true);
        assertTrue(msg.contains("Smart App Control"), msg);
    }

    @Test
    void nonWindowsAndNoJnaStillExplains() {
        String msg = BindingFailure.message(BindingMode.AUTO, new UnsatisfiedLinkError("jni boom"), null, false, false);
        assertFalse(msg.contains("Smart App Control"), msg);
        assertTrue(msg.contains("JNA: unavailable"), msg);
    }

    @Test
    void ofReturnsUnsatisfiedLinkError() {
        UnsatisfiedLinkError e = BindingFailure.of(BindingMode.AUTO, new UnsatisfiedLinkError("a"), null, false);
        assertTrue(e.getMessage().contains("no usable gamepad backend"));
    }
}
```

- [ ] **Step 2: 运行，确认编译失败**

Run: `./gradlew test --tests '*BindingFailureTest*'`
Expected: 编译错误，`BindingFailure` 不存在。

- [ ] **Step 3: 实现**

固定格式（行首两个空格缩进）：

```
[gamepad-jni] no usable gamepad backend (binding=<auto|jni|jna>)
  JNI: <ClassName>: <message>          ← mode == JNA_ONLY 时整行省略
  JNA: unavailable - com.sun.jna.Native not on the classpath (this Minecraft version ships no JNA)
                                        ← 或 "  JNA: <ClassName>: <message>"
  hint: <分号分隔的提示>                 ← 无命中则省略
```

`hint` 的两种来源：JNI 消息包含 `Application Control policy` → `Smart App Control on Windows 11 is blocking the unsigned gamepadjni.dll`；`!jnaPresent && windows` → `this Minecraft version has no JNA, so only the JNI backend is possible`。两条同时命中时用 `"; "` 连接成一条 `hint:`。

`mode` 到文案的映射：`AUTO`→`auto`，`JNI_ONLY`→`jni`，`JNA_ONLY`→`jna`。`describe(Throwable)` 空 message 时只写类名。`of(...)` 用 `System.getProperty("os.name")` 判 Windows 后调用 `message(...)`。

- [ ] **Step 4: 运行，确认通过**

Run: `./gradlew test --tests '*BindingFailureTest*'`
Expected: PASS，6 个用例。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/ifels/gamepadjni/BindingFailure.java \
        src/test/java/com/ifels/gamepadjni/BindingFailureTest.java
git commit -m "feat(binding): report both backends' reasons when neither loads"
```

---

### Task 3: 绑定接口 + JNI 实现（`GamepadJNI` 变门面，native 声明搬到 `JniGamepad`）

这一步是纯搬家，但 Java、C、Gradle 必须同一次提交，否则构建中断。

**Files:**
- Create: `src/main/java/com/ifels/gamepadjni/GamepadBinding.java`
- Create: `src/main/java/com/ifels/gamepadjni/JniGamepad.java`
- Create: `src/main/java/com/ifels/gamepadjni/JniBinding.java`
- Create: `src/main/java/com/ifels/gamepadjni/Bindings.java`
- Modify: `src/main/java/com/ifels/gamepadjni/GamepadJNI.java`（整体替换为门面）
- Modify: `src/main/java/com/ifels/gamepadjni/SystemCursor.java:61-81`
- Modify: `src/main/c/gamepad_jni.c`（74 处符号）
- Modify: `src/main/c/gamepad_jni_macos.m`（1 处符号）
- Modify: `build.gradle:140-241`（`verifyNativeSymbols`）
- Test: `src/test/java/com/ifels/gamepadjni/GamepadFacadeTest.java`
- Test: `src/test/java/com/ifels/gamepadjni/BindingsTest.java`

**Interfaces:**
- Consumes: `BindingFailure`（Task 2）
- Produces:
  - `interface GamepadBinding`（包私有）：75 个 SDL 方法 + `int showCursor(boolean hide)`，全部实例方法。
  - `final class JniGamepad`：75 个 `public static native`，类名用于 JNI 符号。
  - `final class JniBinding implements GamepadBinding`，含 `static final JniBinding INSTANCE`。
  - `final class Bindings`：`static GamepadBinding b()`、`static void set(GamepadBinding)`、`static void clear()`、`static boolean isJni()`、`static String describe()`。

- [ ] **Step 1: 写失败测试**

```java
// GamepadFacadeTest.java
package com.ifels.gamepadjni;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GamepadFacadeTest {

    private static Map<String, String> signatureOf(Class<?> c) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Method m : c.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic()) continue;
            map.put(m.getName(), Arrays.toString(m.getParameterTypes()) + "->" + m.getReturnType().getName());
        }
        return map;
    }

    @Test
    void facadeMirrorsTheBindingInterface() {
        Map<String, String> api = signatureOf(GamepadBinding.class);
        Map<String, String> facade = signatureOf(GamepadJNI.class);
        assertEquals(76, api.size(), "GamepadBinding must declare 75 SDL methods + showCursor");
        assertEquals(75, facade.size(), "GamepadJNI must keep its 75 public methods");
        assertTrue(api.keySet().containsAll(facade.keySet()), "facade has methods the interface lacks");
        for (Map.Entry<String, String> e : facade.entrySet()) {
            assertEquals(e.getValue(), api.get(e.getKey()), "signature drift on " + e.getKey());
        }
    }

    @Test
    void nativeDeclarationsMovedToJniGamepad() {
        long natives = Arrays.stream(JniGamepad.class.getDeclaredMethods())
                .filter(m -> Modifier.isNative(m.getModifiers())).count();
        assertEquals(75, natives);
        assertFalse(Arrays.stream(GamepadJNI.class.getDeclaredMethods())
                .anyMatch(m -> Modifier.isNative(m.getModifiers())), "GamepadJNI must not declare natives anymore");
    }
}
```

```java
// BindingsTest.java
package com.ifels.gamepadjni;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class BindingsTest {

    @BeforeEach @AfterEach
    void reset() { Bindings.clear(); }

    @Test
    void unsetBindingThrowsUnsatisfiedLinkErrorNotNpe() {
        UnsatisfiedLinkError e = assertThrows(UnsatisfiedLinkError.class, Bindings::b);
        assertTrue(e.getMessage().contains("no binding installed"));
    }

    @Test
    void reportsJniAndJna() {
        assertEquals("none", Bindings.describe());
        Bindings.set(JniBinding.INSTANCE);
        assertTrue(Bindings.isJni());
        assertEquals("jni", Bindings.describe());
    }
}
```

- [ ] **Step 2: 运行，确认失败**

Run: `./gradlew test --tests '*GamepadFacadeTest*' --tests '*BindingsTest*'`
Expected: 编译错误（`GamepadBinding` / `JniGamepad` / `Bindings` 不存在）。

- [ ] **Step 3: 搬 native 声明到 `JniGamepad`**

把今天 `GamepadJNI.java` 的**全部内容**复制到 `JniGamepad.java`：只改两处——`package` 之后保留原 javadoc 但首句改成「JNI native method declarations for SDL3 Gamepad API」；类名 `GamepadJNI` → `JniGamepad`。75 个方法、参数、返回类型、javadoc 一字不改。类本身保持 `public final`（JNI 不要求 public，但保持与今天一致便于阅读）。

- [ ] **Step 4: 写 `GamepadBinding`**

包私有接口，76 个方法，签名逐字来自 `JniGamepad` 的 75 个方法（去掉 `native`、改为实例方法），末尾追加：

```java
int showCursor(boolean hide);
```

- [ ] **Step 5: 写 `JniBinding`**

```java
final class JniBinding implements GamepadBinding {
    static final JniBinding INSTANCE = new JniBinding();
    private JniBinding() { }
    // 75 个方法：public <ret> X(...) { return JniGamepad.X(...); }
    // showCursor：public int showCursor(boolean hide) { return SystemCursorJNI.showCursor(hide); }
}
```

- [ ] **Step 6: 把 `GamepadJNI` 改成门面**

删掉 75 个 native 声明，换成同签名的 `public static` 方法，方法体只有 `return Bindings.b().X(...);`。类 javadoc 改成：本类是 SDL3 gamepad API 的公开门面，真正的绑定由 `GamepadManager.initialize()` 决定（JNI 优先，JNA 回退），调用方不需要关心。

- [ ] **Step 7: 写 `Bindings`**

```java
final class Bindings {
    private static volatile GamepadBinding current;

    static GamepadBinding b() {
        GamepadBinding b = current;
        if (b == null) {
            throw new UnsatisfiedLinkError(
                "[gamepad-jni] no binding installed; call GamepadManager.initialize() first");
        }
        return b;
    }
    static void set(GamepadBinding binding) { current = binding; }
    static void clear() { current = null; }
    static boolean isJni() { return current == JniBinding.INSTANCE; }
    static String describe() { return current == null ? "none" : (isJni() ? "jni" : "jna"); }
}
```

- [ ] **Step 8: `SystemCursor` 改调 `Bindings`**

`SystemCursor.setHidden` 里 `SystemCursorJNI.showCursor(hidden)` → `Bindings.b().showCursor(hidden)`。两个 `catch` 分支与 `unavailable` 标志保持原样——`Bindings.b()` 抛的正是 `UnsatisfiedLinkError`，行为不变。`SystemCursorJNI` 保持原样。

- [ ] **Step 9: C 符号改名**

```bash
cd src/main/c
perl -pi -e 's/Java_com_ifels_gamepadjni_GamepadJNI_/Java_com_ifels_gamepadjni_JniGamepad_/g' gamepad_jni.c gamepad_jni_macos.m
grep -c "JniGamepad_" gamepad_jni.c gamepad_jni_macos.m     # 期望 74 / 1
grep -c "GamepadJNI_" gamepad_jni.c gamepad_jni_macos.m     # 期望 0 / 0
```

`SdlLogJNI_`（3 个）与 `SystemCursorJNI_`（1 个）不得被改动。

- [ ] **Step 10: 更新 `verifyNativeSymbols`**

`build.gradle` 140-241 行：`jniSymbolFor` 前缀改 `Java_com_ifels_gamepadjni_JniGamepad_`；`declaredNativeMethods` 读 `JniGamepad.java`；`darwinOnlyMethods` 的正则同样改前缀（漏掉这一处会让除 macOS 外的平台也被要求导出 `getGCControllerCount`）；删掉 `getVersionPendingPlatforms` 与引用它的过滤条件；在 `doLast` 开头加：

```groovy
def allMethods = declaredNativeMethods()
if (allMethods.isEmpty()) {
    throw new GradleException('verifyNativeSymbols: parsed 0 native methods from JniGamepad.java — the scanner is broken')
}
```

- [ ] **Step 11: 运行测试**

Run: `./gradlew test --tests '*GamepadFacadeTest*' --tests '*BindingsTest*' --tests '*BindingPolicyTest*' --tests '*BindingFailureTest*'`
Expected: 全部 PASS。

- [ ] **Step 12: 确认 Java 侧编译干净**

Run: `./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL。此时 `gradle jar` 会因 `verifyNativeSymbols` 失败——`prebuilt/jni` 下还是旧符号，属预期，由 Task 8 的 CI 重编解决。

- [ ] **Step 13: 提交**

```bash
git add -A
git commit -m "refactor(binding): split GamepadJNI into facade + JNI backend"
```

---

### Task 4: JNA 实现（`JnaGamepad` / `JnaUser32` / `JnaBinding`）

**Files:**
- Create: `src/main/java/com/ifels/gamepadjni/JnaGamepad.java`
- Create: `src/main/java/com/ifels/gamepadjni/JnaUser32.java`
- Create: `src/main/java/com/ifels/gamepadjni/JnaBinding.java`
- Create: `src/main/java/com/ifels/gamepadjni/SdlHandle.java`
- Modify: `build.gradle`（`dependencies` 块）
- Test: `src/test/java/com/ifels/gamepadjni/JnaBindingShapeTest.java`

**Interfaces:**
- Consumes: `GamepadBinding`、`BindingPolicy`、`BindingMode`
- Produces:
  - `final class SdlHandle`：`static SdlHandle ofFile(Path dir, Path file)`、`static SdlHandle ofName(String name)`、字段 `final Path dir, file; final String name;`
  - `final class JnaGamepad`：`static void install(SdlHandle sdl)`；75 个 `public static native` + `static native void SDL_free(Pointer)`
  - `final class JnaUser32`：`static boolean install()`、`static native int ShowCursor(boolean show)`
  - `final class JnaBinding implements GamepadBinding`，含 `static final JnaBinding INSTANCE`

- [ ] **Step 1: 写失败测试**

```java
package com.ifels.gamepadjni;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class JnaBindingShapeTest {

    @Test
    void jnaBindingImplementsEverySlot() {
        assertEquals(76, Arrays.stream(JnaBinding.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).count());
    }

    @Test
    void everyBooleanReturningNativeIsByte() {
        // JNA maps Java boolean to a 4-byte int; SDL's bool is 1 byte.
        for (Method m : JnaGamepad.class.getDeclaredMethods()) {
            if (!Modifier.isNative(m.getModifiers())) continue;
            assertNotEquals(boolean.class, m.getReturnType(), m.getName() + " must return byte, not boolean");
        }
    }

    @Test
    void noStringTypesInTheNativeSurface() {
        for (Method m : JnaGamepad.class.getDeclaredMethods()) {
            if (!Modifier.isNative(m.getModifiers())) continue;
            assertEquals(-1, Arrays.asList(m.getParameterTypes()).indexOf(String.class), m.getName());
            assertNotEquals(String.class, m.getReturnType(), m.getName());
        }
    }

    @Test
    void sdlHandleCarriesEitherFileOrName() {
        SdlHandle byName = SdlHandle.ofName("SDL3");
        assertNull(byName.file);
        assertEquals("SDL3", byName.name);
        SdlHandle byFile = SdlHandle.ofFile(null, java.nio.file.Paths.get("C:/x/SDL3.dll"));
        assertEquals("C:/x/SDL3.dll", byFile.file.toString());
        assertNull(byFile.name);
    }
}
```

- [ ] **Step 2: 运行，确认编译失败**

Run: `./gradlew test --tests '*JnaBindingShapeTest*'`
Expected: 编译错误。

- [ ] **Step 3: 加依赖**

`build.gradle`：

```groovy
dependencies {
    compileOnly 'org.apache.logging.log4j:log4j-api:2.20.0'
    compileOnly 'net.java.dev.jna:jna:3.4.0'

    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testImplementation 'net.java.dev.jna:jna:3.4.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

- [ ] **Step 4: 写 `SdlHandle`**

包私有 final 类，两个工厂方法 + 三个 final 字段；`ofFile(dir, file)` 允许 `dir == null`。

- [ ] **Step 5: 写 `JnaGamepad`**

75 个 `public static native`，签名按 spec 的映射规则逐条从 `JniGamepad` 翻译：

- `boolean` 返回值 → `byte`（20 个）；
- `String` 返回值 → `Pointer`（12 个：`SDL_GetError`、`SDL_GetGamepadMappingForID`、`SDL_GetGamepadMapping`、`SDL_GetGamepadNameForID`、`SDL_GetGamepadPathForID`、`SDL_GetGamepadGUIDForID`、`SDL_GetGamepadName`、`SDL_GetGamepadPath`、`SDL_GetGamepadSerial`、`SDL_GetGamepadStringForType`、`SDL_GetGamepadStringForAxis`、`SDL_GetGamepadStringForButton`）；
- `String` 参数 → `Pointer`（8 个：`SDL_SetHintWithPriority`、`SDL_SetHint`、`SDL_AddGamepadMapping`、`SDL_AddGamepadMappingsFromFile`、`SDL_SetGamepadMapping`、`SDL_GetGamepadTypeFromString`、`SDL_GetGamepadAxisFromString`、`SDL_GetGamepadButtonFromString`）；
- `long` 句柄 → `Pointer`；
- 其余（`int` / `short` / `byte` / `float` / `void`）原样。

spec「需要 Java 侧适配的方法」表里的 5 条按表写。注意 `SDL_GetGamepadMappingForID` 在 Java 侧返回 `String`，JNA 侧返回 `Pointer`，两条都靠 `JnaBinding` 的 UTF-8 层收口。另外追加一个非 SDL API 的辅助入口：

```java
static native void SDL_free(Pointer p);
```

`install` 的实现：

```java
static void install(SdlHandle sdl) {
    String target = sdl.file != null ? sdl.file.toString() : sdl.name;
    NativeLibrary lib = NativeLibrary.getInstance(target);
    Native.register(JnaGamepad.class, lib);
}
```

类之间不得出现 `String`（`JnaBindingShapeTest` 会拦），UTF-8 解码放在 `JnaBinding`。

- [ ] **Step 6: 写 `JnaUser32`**

```java
final class JnaUser32 {
    private static volatile boolean installed;
    static boolean install() {
        if (!isWindows()) return false;
        try {
            Native.register(JnaUser32.class, NativeLibrary.getInstance("user32"));
            installed = true;
        } catch (Throwable t) {
            GamepadLog.warn("[gamepad-jni] system cursor unavailable on the JNA backend: {}", t.toString());
            installed = false;
        }
        return installed;
    }
    static boolean isInstalled() { return installed; }
    static native int ShowCursor(boolean show);
}
```

`install()` 吞异常不抛出——光标是可选功能。

- [ ] **Step 7: 写 `JnaBinding`**

76 个实例方法。转发规则：

- 20 个 `boolean` 方法：`return JnaGamepad.X(...) != 0;`
- 12 个 `String` 方法：`rel = JnaGamepad.X(...); return utf8(rel);`，其中 `SDL_GetGamepadMapping` / `SDL_GetGamepadMappingForID` 在 `utf8` 之后必须 `JnaGamepad.SDL_free(rel)`；
- `SDL_GetGamepadGUIDForID(int)`：见 Step 8；
- 4 个数组方法：按 spec 表打包；
- 其余原样转发；
- `showCursor`：`return JnaUser32.isInstalled() ? JnaUser32.ShowCursor(hide) : 0;`

三个私有助手：

```java
private static String utf8(Pointer p)         // null -> null；扫到 0 取字节，new String(bytes, StandardCharsets.UTF_8)
private static Pointer cstr(String s)         // null -> null；Memory(len+1) 写 UTF-8 再补 0；调用方需保持强引用直到调用结束
private static Pointer handle(long h)         // h == 0 ? null : new Pointer(h)
```

`cstr` 的内存必须在调用期间可达——实现成「返回 `Memory` 并让局部变量持有」的形式，例如 `Memory m = cstr(s); nativeCall(m);`，不要写成 `nativeCall(cstr(s))` 之外丢掉引用的形式（JNA 不保证 GC 前后顺序）。

- [ ] **Step 8: `SDL_GetGamepadGUIDForID` 的适配**

```java
public String SDL_GetGamepadGUIDForID(int instanceId) {
    Pointer p = JnaGamepad.SDL_GetGamepadMappingForID(instanceId);
    if (p == null) return "00000000000000000000000000000000";
    try {
        String mapping = utf8(p);
        int comma = mapping == null ? -1 : mapping.indexOf(',');
        return comma > 0 ? mapping.substring(0, comma) : "00000000000000000000000000000000";
    } finally {
        JnaGamepad.SDL_free(p);
    }
}
```

- [ ] **Step 9: 空句柄早退**

`JnaBinding` 里所有接收句柄的方法，先做与 C 侧一致的空判断再调用。下表是按 `src/main/c/gamepad_jni.c` 逐条抄下来的，不要凭猜测：

| 方法 | `handle == 0` 时返回 |
|---|---|
| `SDL_GetGamepadMapping`、`SDL_GetGamepadName`、`SDL_GetGamepadPath`、`SDL_GetGamepadSerial`、`SDL_GetGamepadPowerInfo`、`SDL_GetGamepadTouchpadFinger`、`SDL_GetGamepadSensorData` | `null` |
| `SDL_GetGamepadPlayerIndex` | `-1` |
| `SDL_GetGamepadConnectionState` | `SDL_JOYSTICK_CONNECTION_INVALID`（`-1`） |
| `SDL_GamepadConnected`、`SDL_GamepadHasAxis`、`SDL_GamepadHasButton`、`SDL_GamepadHasSensor`、`SDL_GamepadSensorEnabled`、`SDL_RumbleGamepad`、`SDL_RumbleGamepadTriggers`、`SDL_SetGamepadLED`、`SDL_SetGamepadSensorEnabled`、`SDL_SetGamepadPlayerIndex` | `false` |
| `SDL_GetGamepadID`、`SDL_GetGamepadType`、`SDL_GetRealGamepadType`、`SDL_GetGamepadButtonLabel`、`SDL_GetGamepadVendor`、`SDL_GetGamepadProduct`、`SDL_GetGamepadProductVersion`、`SDL_GetGamepadFirmwareVersion`、`SDL_GetGamepadSteamHandle`、`SDL_GetGamepadAxis`、`SDL_GetNumGamepadTouchpads`、`SDL_GetNumGamepadTouchpadFingers` | `0` |
| `SDL_GetGamepadSensorDataRate` | `0.0f` |
| `SDL_CloseGamepad` | 直接 return（C 侧是 `if (gp) SDL_CloseGamepad(gp);`） |

- [ ] **Step 10: 运行测试**

Run: `./gradlew test --tests '*JnaBindingShapeTest*'`
Expected: PASS，4 个用例。

- [ ] **Step 11: 提交**

```bash
git add src/main/java/com/ifels/gamepadjni/Jna*.java \
        src/main/java/com/ifels/gamepadjni/SdlHandle.java \
        src/test/java/com/ifels/gamepadjni/JnaBindingShapeTest.java build.gradle
git commit -m "feat(binding): add JNA direct-mapping backend for SDL3"
```

---

### Task 5: 加载流程（JNI → JNA 回退）

**Files:**
- Modify: `src/main/java/com/ifels/gamepadjni/NativeLibraryLoader.java`
- Modify: `src/main/java/com/ifels/gamepadjni/GamepadManager.java:159-165`（日志捕获守卫）、`245-257`

**Interfaces:**
- Consumes: `SdlHandle`、`JnaGamepad.install`、`Bindings`、`BindingPolicy`、`BindingFailure`
- Produces: `NativeLibraryLoader.binding()` → `String`（`"jni"` / `"jna"` / `"none"`）

- [ ] **Step 1: 拆出 SDL3 加载**

把现有 `load(Sdl3Source)` 的前半段抽成 `private static SdlHandle loadSdl3(Sdl3Source source)`，分支保持今天的语义：

| `LoadKind` | 返回 |
|---|---|
| `LAUNCHER` | `loadLauncherSdl3()` 改成返回 `SdlHandle`：绝对路径成功用 `SdlHandle.ofFile(null, path)`，`loadLibrary("SDL3")` 成功用 `SdlHandle.ofName("SDL3")` |
| `HOST` | `tryAdoptHostSdl3()` 的返回类型由 `boolean` 改成 `Path`（null 表示没找到），成功时 `SdlHandle.ofFile(null, path)`；null 时抛今天那条 `UnsatisfiedLinkError` |
| `BUNDLED` | `findNativeDir(false)` 拿目录、`findSdl3File(dir)` 拿文件、`System.load`，返回 `SdlHandle.ofFile(dir, sdlPath)` |

`loadSdl3` 内部保留 `hostSdl3` 标志的赋值与今天全部日志文案。

- [ ] **Step 2: 拆出 JNI 桥加载**

`private static void loadJniBridge(SdlHandle sdl)`：

1. `sdl.dir != null` 且目录里有 `getJNIFileName()` → `System.load` 该文件，返回。
2. Android → 今天 `loadAndroid()` 的第 (2) 段（`CFX_LIB_PATH` → ABI 目录解压）。
3. 其余 → `loadJNILibrary(findNativeDir(true))`。

失败一律转成 `UnsatisfiedLinkError`（沿用 `loadJniBridge` 现有的包装逻辑）。

- [ ] **Step 3: 重写 `load(Sdl3Source)`**

按 spec「加载流程」的伪代码实现：

```java
static void load(Sdl3Source source) {
    if (loaded) return;
    synchronized (NativeLibraryLoader.class) {
        if (loaded) return;
        BindingMode mode = BindingPolicy.mode();
        SdlHandle sdl = loadSdl3(source);
        Throwable jniFailure = null;
        if (mode != BindingMode.JNA_ONLY) {
            try {
                loadJniBridge(sdl);
                Bindings.set(JniBinding.INSTANCE);
                loaded = true;
                GamepadLog.info("[gamepad-jni] binding=jni");
                return;
            } catch (Throwable t) {
                jniFailure = t;
                if (mode == BindingMode.JNI_ONLY) {
                    throw BindingFailure.of(mode, t, null, BindingPolicy.jnaPresent());
                }
            }
        }
        if (!BindingPolicy.jnaPresent()) {
            throw BindingFailure.of(mode, jniFailure, null, false);
        }
        try {
            JnaGamepad.install(sdl);
            JnaUser32.install();
            Bindings.set(JnaBinding.INSTANCE);
            loaded = true;
            GamepadLog.warn("[gamepad-jni] JNI unavailable ({}), fell back to JNA",
                    jniFailure == null ? "not attempted" : jniFailure.toString());
        } catch (Throwable t) {
            throw BindingFailure.of(mode, jniFailure, t, true);
        }
    }
}
```

`sdl3Source == null` 仍由 `Sdl3Source.resolveLoadKind` 抛 `NullPointerException`，日志文案不变。

- [ ] **Step 4: `GamepadManager` 的日志捕获守卫**

`installSdlLogCapture()` 的调用点加条件 `Bindings.isJni()`；新增一条 debug 日志说明 JNA 路径不采集 SDL 日志。`drainSdlLogs()` 不动。

- [ ] **Step 5: 加 `binding()` 访问器**

```java
static String binding() { return Bindings.describe(); }
```

- [ ] **Step 6: 编译并跑全量测试**

Run: `./gradlew test`
Expected: 全部 PASS（既有 4 个测试类 + 新增 4 个）。

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/ifels/gamepadjni/NativeLibraryLoader.java \
        src/main/java/com/ifels/gamepadjni/GamepadManager.java
git commit -m "feat(binding): try JNI first and fall back to JNA"
```

---

### Task 6: 原生库重新构建（CI）

**Files:**
- 无源码改动；产物是 `prebuilt/jni/` 下 9 个平台

**Interfaces:**
- Consumes: Task 3 的符号改名
- Produces: 与 `JniGamepad` 匹配的原生库

- [ ] **Step 1: 本地能编的只有当前平台**

Run: `cd prebuilt && ./build-jni.sh`
Expected: `prebuilt/jni/darwin-aarch64/libgamepadjni.dylib` 更新；`nm -gU` 里出现 `_Java_com_ifels_gamepadjni_JniGamepad_SDL_1Init` 一类的符号。

- [ ] **Step 2: 用本地产物验证 `verifyNativeSymbols` 的正则**

Run: `./gradlew verifyNativeSymbols`
Expected: **任务失败**（其余 8 个平台还是旧符号），但日志里 `darwin-aarch64` 那一行必须是 `OK (75 declared entry points present)`。如果本平台也报缺失，说明符号改名或正则写错了。

- [ ] **Step 3: 交给 CI**

推送 tag 前先手动触发 `build natives` 工作流（`workflow_dispatch`），确认 9 个平台全绿、`natives-*` 产物齐全。本地不提交 `prebuilt/jni`。

- [ ] **Step 4: 无需提交**

这一步不产生仓库改动。

---

### Task 7: PE 时间戳

**Files:**
- Create: `prebuilt/patch-pe-timestamp.py`
- Modify: `prebuilt/build-jni.sh`（`organize_output_windows` 末尾、`verify_output` 的 Windows 分支）
- Modify: `CMakeLists.txt:162-175`（注释）

**Interfaces:**
- Consumes: 无
- Produces: `prebuilt/patch-pe-timestamp.py` 命令行：`patch-pe-timestamp.py <dll>...`，退出码非 0 表示失败

- [ ] **Step 1: 写脚本**

要求（纯 Python 3 标准库，不引第三方）：

1. 时间戳来源：`os.environ["SOURCE_DATE_EPOCH"]` → `git log -1 --format=%ct` → `sys.exit("...")` 并打印两条来源都说明。
2. 只用标准库解析 PE：`MZ` → `e_lfanew` → `PE\0\0` → COFF 头（`TimeDateStamp` 在 `e_lfanew + 8`）。
3. 导出目录时间戳：读可选头 `NumberOfRvaAndSizes` / `DataDirectory[0]`（`IMAGE_DIRECTORY_ENTRY_EXPORT`）拿 RVA，再按节表把 RVA 转文件偏移，写该结构偏移 +4 的 32 位字段。RVA 为 0 时跳过。
4. 校验和：读可选头 `CheckSum` 字段；非 0 时按 PE 算法重算（16 位累加，折叠进位，加上文件长度）。为 0 时保持 0。
5. 幂等：目标值已等于现有值时不写文件。
6. 每个文件打印 `patched <path>: 0x%08x -> 0x%08x` 或 `unchanged <path>`。

- [ ] **Step 2: 在副本上验证**

```bash
cp prebuilt/jni/windows-x86_64/gamepadjni.dll /tmp/t.dll
SOURCE_DATE_EPOCH=1700000000 python3 prebuilt/patch-pe-timestamp.py /tmp/t.dll
python3 - <<'EOF'
import struct
d = open('/tmp/t.dll','rb').read()
e = struct.unpack_from('<I', d, 0x3c)[0]
print(struct.unpack_from('<I', d, e+8)[0])
EOF
```

Expected: 打印 `1700000000`。

- [ ] **Step 3: 幂等性验证**

Run: 同一条命令再跑一次
Expected: 打印 `unchanged /tmp/t.dll`，文件 mtime 不变。

- [ ] **Step 4: 接到 `build-jni.sh`**

在 `organize_output_windows()` 的 `done` 之后、函数返回之前插入：

```bash
    local patcher="${GAMEPAD_JNI_ROOT}/prebuilt/patch-pe-timestamp.py"
    local py
    for py in python3 python; do
        command -v "$py" >/dev/null && break
        py=""
    done
    [ -n "$py" ] || { echo "Error: python3/python not found; needed to stamp PE timestamps"; exit 1; }
    "$py" "$patcher" "${INSTALL_DIR}"/windows-*/gamepadjni.dll || exit 1
```

并把 542-546 行那段「No post-link strip here on purpose … --no-insert-timestamp just bought us」的注释改写成：链接期仍然可复现，最终时间戳由 `patch-pe-timestamp.py` 统一写入。

- [ ] **Step 5: 在 `verify_output()` 加断言**

Windows 分支里（现在打印 DLL 依赖的那段之后）加：用 `python3` 读两个 DLL 的 `TimeDateStamp`，断言两者相等、非 0、且 `<= now + 86400`，不满足则 `exit 1` 并打印实际值。

- [ ] **Step 6: 改 CMake 注释**

`CMakeLists.txt:162-175` 只改注释，不动编译选项：说明清零是让**中间产物**可复现，最终时间戳由 `prebuilt/patch-pe-timestamp.py` 在打包前统一写入。

- [ ] **Step 7: 提交**

```bash
git add prebuilt/patch-pe-timestamp.py prebuilt/build-jni.sh CMakeLists.txt
git commit -m "build(win): stamp a fixed PE timestamp derived from SOURCE_DATE_EPOCH"
```

---

### Task 8: README

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: 全部实现
- Produces: 用户文档

- [ ] **Step 1: 加一节**

在「Native Libraries」之后插入「Binding: JNI first, JNA fallback」，内容：两套绑定的顺序、`-Dcfx.gamepadjni.binding` / `CFX_GAMEPADJNI_BINDING` 的三个取值、JNA 版本矩阵（1.7.10 无 / 1.8.9–1.11.2 3.4.0 / 1.12.2–1.16.5 4.4.0 / 1.17.1 5.8.0 / 1.18.2–1.19.2 5.10.0 / 1.20.1 5.12.1 / 1.21.1 5.14.0 / 1.21.11+ 5.17.0）、1.7.10 只能用 JNI、失效时的日志长什么样。

- [ ] **Step 2: 更新「Who owns prebuilt/」**

补一句：Windows DLL 的 PE 时间戳由 `prebuilt/patch-pe-timestamp.py` 写成 `SOURCE_DATE_EPOCH`（缺省取 HEAD 提交时间），因此同一 tag 重编仍得到同一 hash。

- [ ] **Step 3: 提交**

```bash
git add README.md
git commit -m "docs: describe the dual JNI/JNA binding and its switch"
```

---

## 自查

- **Spec 覆盖**：绑定选择（Task 1）、失败文案（Task 2）、门面与接口（Task 3）、JNA 细节/GUID/SystemCursor（Task 4）、加载与回退（Task 5）、重编（Task 6）、时间戳（Task 7）、README（Task 8）。spec 的「明确不做」没有任何任务去实现它。
- **类型一致**：`GamepadBinding` 76、`GamepadJNI` 75、`JniGamepad` 75、`JnaGamepad` 76（75 + `SDL_free`）；`SdlHandle.ofFile/ofName` 在 Task 4 定义、Task 5 使用。
- **Review Focus 对应**：NPE→ULE 由 `BindingsTest` 覆盖；异常类型由 `BindingFailureTest` 覆盖；`showCursor` 非 Windows 由 `JnaBinding` 的 `isInstalled()` 分支覆盖（真机清单第 7 条）；UTF-8 与空句柄无法在无 Windows 的机器上单测，已列入 spec 的真机清单第 3–5 条。
