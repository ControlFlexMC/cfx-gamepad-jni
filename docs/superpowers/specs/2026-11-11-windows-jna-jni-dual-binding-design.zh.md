# Windows 上 JNA / JNI 双绑定

日期：2026-11-11

先试 JNI，JNI 不可用再回退 JNA，全平台同一条逻辑。调用方不感知。

背景与排查数据见 `~/Documents/mc/discussion/2026-11-11-smart-app-control-controlify-research.md`：Smart App Control 拦截的是本库自产的 `gamepadjni.dll`（未签名、无云端信誉）。回退到 JNA 之后，这台机器上根本不加载那个文件。

## 目标

1. 同一个 JAR 里同时存在 JNI 与 JNA 两套绑定，**运行顺序统一为先 JNI、失败回退 JNA**，所有平台同一条规则。
2. `GamepadJNI` 的公开签名一个都不变，ControlFlex 12 个版本仓库零改动。
3. 不做 GLFW 兜底：两套绑定都不可用就是没有手柄，与今天一致。
4. 库不新增运行时依赖、不增加 JAR 体积、不打包 JNA；JNA 一律用宿主（Minecraft）classpath 上那一份。
5. 1.7.10 没有 JNA，它在 Windows 上两套都不可用时仍然是「`initialize` 返回 false + 无手柄」，但错误文案要说清是 JNI 加载失败、且这一版根本没有 JNA。

## 非目标

- 不改 `Sdl3Source`（`HOST` / `BUNDLED`）语义。
- 不实现 GLFW 手柄后端，也不做「SDL 失败换后端」。
- 不给 `SdlLogJNI` 做 JNA 版回调（见「SDL 日志捕获」）。
- 不解决 SAC 拦截 `SDL3.dll` 的可能性（见「风险」第 2 条）。

## 事实基线

### 各版本 Windows 上 classpath 里的 JNA

由原版 version JSON（`meta/net.minecraft/<版本>.json` 的 `libraries`，`libraries.minecraft.net` URL + sha1）按 `rules` 以 Windows 求值得到。加载器只在原版库之上追加，从不删除，因此与 Forge / Fabric / NeoForge 无关。

| MC | Windows 上的 JNA |
|---|---|
| 1.7.10 | **无** |
| 1.8.9 / 1.9.4 / 1.10.2 / 1.11.2 | 3.4.0 |
| 1.12.2 → 1.16.5 | 4.4.0 |
| 1.17.1 | 5.8.0 |
| 1.18.2 / 1.19.2 | 5.10.0 |
| 1.20.1 | 5.12.1 |
| 1.21.1 | 5.14.0 |
| 1.21.11 / 26.x | 5.17.0 |

1.17.1 与 1.20.1 的 JSON 里各有两个 JNA 条目，靠 `rules` 按操作系统二选一，macOS 分支才是 5.13.0；Windows 上不存在它。

「在 classpath 上」由启动器执行原版 JSON 得到。官方启动器、Prism、HMCL、PCL2、Modrinth App 都照做；自研启动器或被人手工精简的整合包可能剔掉 jna。所以能力探测必须有，不能假设它在。

### 为什么编译目标是 JNA 3.4.0

要用的只有 direct mapping 这一小块，四个版本都在：

| API | 3.4.0 | 4.4.0 | 5.12.1 | 5.17.0 |
|---|---|---|---|---|
| `NativeLibrary.getInstance(String)` | ✓ | ✓ | ✓ | ✓ |
| `Native.register(Class, NativeLibrary)` | ✓ | ✓ | ✓ | ✓ |
| `Pointer(long)` / `nativeValue` / `getByte` | ✓ | ✓ | ✓ | ✓ |
| `IntByReference` / `FloatByReference` / `ByteByReference` | ✓ | ✓ | ✓ | ✓ |
| `Memory` | ✓ | ✓ | ✓ | ✓ |
| `Pointer.getString(long, String)` | **✗** | ✓ | ✓ | ✓ |
| `Library.OPTION_STRING_ENCODING` | **✗** | ✓ | ✓ | ✓ |
| `Native.register(Class, String)` | **✗** | ✓ | ✓ | ✓ |

结论：**编译期钉 3.4.0，运行期兼容 3.4.0–5.17.0**；反过来（对 5.x 编译、在 1.8.9 上跑）会 `NoSuchMethodError`。两个「3.4.0 没有」的项直接不用：`register` 一律写成 `Native.register(cls, NativeLibrary.getInstance(...))`，`char*` 一律走 `Pointer` 手工按 UTF-8 解。

最后一条顺带解决一个真实缺陷：JNA 的 `String` 默认按平台编码解（中文 Windows 是 GBK），手柄名与错误串会乱码。

### 绑定面的 SDL3 版本下限

`GamepadJNI` 的 75 个方法中，74 个 SDL 函数在 `prebuilt/sdl/include/SDL3/*.h` 里 `\since` 全部是 `SDL 3.2.0`。所以 JNA 侧可以一次性注册，不需要按 SDL 版本拆类；低于 3.2 的 SDL 注册失败即视为「JNA 也不可用」。

### 谁在调 `GamepadJNI`

12 个 control-flex 仓库都直接调用，不是内部细节：

| 调用点 | 方法 |
|---|---|
| `input/selection/GamepadIdentity`、`GamepadRegistry` | `GamepadJNI.SDL_GetGamepadGUIDForID(int)` |
| `sdl/SDL3Manager` | `GamepadJNI.SDL_GetError()` |

现有代码只 catch `UnsatisfiedLinkError`（`SystemCursor.setHidden`、`SDL3Manager.initialize`）。因此 `GamepadJNI` 只能是**门面**，且门面在没有绑定可用时必须继续抛 `UnsatisfiedLinkError`——不能变成 `NullPointerException`。

## 绑定选择

只有两种东西要决定：开关模式，以及 JNA 在不在。

```
forced = -Dcfx.gamepadjni.binding，缺省读 CFX_GAMEPADJNI_BINDING，再缺省 "auto"
        取值 jni / jna / auto，大小写不敏感，其余值当 auto 并打 warn
```

| forced | 行为 |
|---|---|
| `jni` | 只试 JNI，失败即失败（不回退）。排障用 |
| `jna` | 只试 JNA，不试 JNI。排障用 |
| `auto`（默认） | 先 JNI，失败且 JNA 可用则回退 JNA |

`auto` 下没有任何平台判断：Windows 之外的平台 JNI 通常成功，回退分支天然不走。而 Android 上 JNA 一般不在 classpath，`jnaPresent` 为 false，回退同样不发生——不需要为平台写特例。

`jnaPresent` = 用 `GamepadJNI.class.getClassLoader()` **非初始化**加载 `com.sun.jna.Native` 与 `com.sun.jna.NativeLibrary` 都成功（`Class.forName(name, false, cl)`）。

非初始化很关键：`Native` 的静态初始化会去解压 `jnidispatch.dll`，探测阶段不能触发它。

## 架构

| 文件 | 职责 |
|---|---|
| `GamepadJNI.java` | 公开门面。75 个 `public static` 方法，方法体只有一行：`Bindings.b().X(...)` |
| `GamepadBinding.java` | 包私有接口。75 个 SDL 方法 + `showCursor`，共 76 |
| `JniBinding.java` | 实现。一行转发到 `JniGamepad` / `SystemCursorJNI` |
| `JnaBinding.java` | 实现。转发到 `JnaGamepad` / `JnaUser32`，含类型适配 |
| `JniGamepad.java` | 今天 `GamepadJNI` 的 75 个 `public static native` 原样搬过来 |
| `JnaGamepad.java` | JNA direct mapping 到 SDL3，75 个 `public static native`（签名按 JNA 规则，见下） |
| `JnaUser32.java` | JNA direct mapping 到 `user32`，只有 `ShowCursor(boolean)` |
| `Bindings.java` | 持有当前 `GamepadBinding`；`b()` 在未安装时抛 `UnsatisfiedLinkError` |
| `BindingPolicy.java` | 纯函数：把 forced + jnaPresent 映射成尝试序列 |

`SystemCursorJNI` 与 `SdlLogJNI` 保持 native 类不动，前者符号名仍是 `Java_com_ifels_gamepadjni_SystemCursorJNI_showCursor`，只被 `JniBinding` 调用。

### 未安装绑定时的契约

```java
static GamepadBinding b() {
    GamepadBinding b = CURRENT;      // volatile
    if (b == null) {
        throw new UnsatisfiedLinkError(
            "[gamepad-jni] no binding installed; call GamepadManager.initialize() first");
    }
    return b;
}
```

理由：ControlFlex 在初始化前后都可能调 `GamepadJNI.SDL_GetError()`，而那些调用点只 catch `UnsatisfiedLinkError`。改成 NPE 会穿过它们。

### 为什么用接口而不是三元分支

`GamepadJNI` 里出现 `JnaBinding` 这个名字，就等于让每一次 `invokestatic` 的解析在所有平台上都可能要求 JNA 类存在（Android 上有验证器风险）。接口 + 一个可替换字段把所有 `com.sun.jna.*` 挡在 `JnaBinding` 里面，`GamepadJNI` 的常量池里一个 JNA 符号都没有。两边实现同一个接口，方法集由编译器保证一致，不需要额外的 parity 检查。

## 加载流程

`GamepadManager.initialize(Sdl3Source)` 的调用序列不变，改动集中在 `NativeLibraryLoader.load(source)`。核心变化：**SDL3 变成两套绑定共用的前置条件**，绑定退到第二步。

```
NativeLibraryLoader.load(source):
    mode = BindingPolicy.mode()               // AUTO / JNI_ONLY / JNA_ONLY
    sdl  = loadSdl3(source)                   // 失败直接抛：两套绑定都无库可绑

    jniFailure = null
    if mode != JNA_ONLY:
        try:
            loadJniBridge()                   // 自己解析目录、解压、System.load
            Bindings.set(JNI)
            return
        catch (Throwable t):
            jniFailure = t
            if mode == JNI_ONLY:
                throw BindingFailure.of(mode, t, null, false)   // 明确指定，不兜底

    if !BindingPolicy.jnaPresent():
        throw BindingFailure.of(mode, jniFailure, null, false)

    try:
        JnaGamepad.install(sdl)               // NativeLibrary.getInstance + Native.register
        JnaUser32.install()                   // 可选，失败只影响光标
        Bindings.set(new JnaBinding())
        log(WARN, "JNI unavailable ({}), fell back to JNA", describe(jniFailure))
    catch (Throwable t):
        throw BindingFailure.of(mode, jniFailure, t, true)
```

四条硬约束：

1. **SDL3 先加载**。JNI 的 `DT_NEEDED` / 导入表按模块名绑定，JNA 的 `NativeLibrary.getInstance(path)` 同样要取到已加载的那一份。今天 `HOST` 的三级搜索与 `BUNDLED` 的解压逻辑都保留，只是被提到绑定之前。
2. **回退只发生在 `load()` 内部**，且必须在任何 SDL 调用之前完成。`JnaGamepad` 是独立类，注册到一半失败也不会污染 JNI 路径。
3. **`loadSdl3` 返回 `SdlHandle{Path dir, Path file, String name}`**。`dir` 是解压目录（HOST 模式下为 null）；`file` 是 SDL3 的实际路径；走 `System.loadLibrary("SDL3")` 时 `file == null, name == "SDL3"`，JNA 用名字绑定。`loadJniBridge(sdl)` 把 `sdl.dir` 当提示：目录里已有 `gamepadjni.dll` 就直接用（BUNDLED 已把两个文件解压到同一目录），否则回退到今天的 `findNativeDir(true)`。这样 BUNDLED 不会解压两次，HOST 也不需要 `dir`。
4. **回退时复用同一个 `SdlHandle`**，不重新解压、不重新搜索。`Sdl3Source.BUNDLED` 下临时目录里的 SDL3 已经加载，JNA 直接绑它。

`loaded` 标志仍然只在整段成功后才置位，失败可重试；`Bindings` 保持未安装状态。

`NativeLibraryLoader` 新增 `binding()` 访问器；`usedHostSdl3()` 语义不变。

### 两套绑定都不可用时的错误文案

这是 1.7.10 的主路径（没有 JNA，JNI 又加载失败），也是 SAC 拦截后 JNA 也起不来的路径。必须让调用方一眼看出是哪一段断的，而不是一句「Failed to load native library」。

`BindingFailure.of(mode, jniFailure, jnaFailure, jnaPresent)` 生成一条 `UnsatisfiedLinkError`。场景 A，1.7.10（JNI 加载失败，且这一版没有 JNA）：

```
[gamepad-jni] no usable gamepad backend (binding=auto)
  JNI: java.lang.UnsatisfiedLinkError: ...\Temp\gamepadjni-1234\gamepadjni.dll: An Application Control policy has blocked this file
  JNA: unavailable - com.sun.jna.Native not on the classpath (this Minecraft version ships no JNA)
  hint: this Minecraft version has no JNA, so only the JNI backend is possible; Smart App Control on Windows 11 is blocking the unsigned gamepadjni.dll - see docs/... 
```

场景 B，1.20.1：SAC 拦了 JNI，JNA 在但注册失败：

```
[gamepad-jni] no usable gamepad backend (binding=auto)
  JNI: java.lang.UnsatisfiedLinkError: ...\Temp\gamepadjni-1234\gamepadjni.dll: An Application Control policy has blocked this file
  JNA: java.lang.UnsatisfiedLinkError: Unable to load library 'SDL3': ...
  hint: Smart App Control on Windows 11 is blocking the unsigned gamepadjni.dll; see docs/...
```

场景 C，`-Dcfx.gamepadjni.binding=jni` 且 JNI 失败：只有首行 + `JNI:` 两段，不提 JNA。

三段固定，一段可选：

| 段 | 内容 | 何时出现 |
|---|---|---|
| 首行 | 模式（`auto` / `jni` / `jna`）+ 两套都失败 | 恒有 |
| `JNI:` | `jniFailure` 的类名 + 消息 | `mode != JNA_ONLY` 时恒有；`JNA_ONLY` 时整行省略 |
| `JNA:` | `jnaPresent == false` 写「`com.sun.jna.Native` 不在 classpath 上，这一版不分发 JNA」；否则写异常类名 + 消息 | 恒有（`JNA_ONLY` 也要有） |
| `hint:` | JNI 消息含 `Application Control policy` → 指向 SAC 文档；Windows 且 JNA 缺席 → 说明这一版只能用 JNI | 命中条件才有 |

`mode == JNI_ONLY` 时只有首行 + `JNI:` 两段，不提 JNA。

`GamepadManager.initialize()` 现有实现是 `catch (UnsatisfiedLinkError) → log.error("Failed to load native library: {}", e.getMessage()) → return false`，整段会原样进日志，调用方的既有处理（`SDL3Manager` 打日志后放弃手柄）不需要改。返回值语义仍是 false，不改成抛异常——ControlFlex 12 个版本都按布尔值处理。

`BindingFailure.of()` 是纯函数（四个入参，两个 `Throwable` 可空），单元测试直接断言三种形态的文案。

## JNA 绑定细节

### 注册

```java
NativeLibrary lib = NativeLibrary.getInstance(sdl.file != null ? sdl.file.toString() : sdl.name);
Native.register(JnaGamepad.class, lib);
```

`Native.register` 会为类里每个 `native` 方法做符号解析，任一符号缺失即抛 `UnsatisfiedLinkError`。这正好是「SDL 太老就不走 JNA」的判据。

### 类型映射规则

| C | Java | 说明 |
|---|---|---|
| `bool` 返回值 | `byte` | **不能用 `boolean`**：JNA 把 `boolean` 当 4 字节，C `bool` 是 1 字节。适配层统一 `!= 0` |
| `bool` 参数 | `boolean` | x64 上参数走寄存器低字节，安全；与 libsdl4j 一致 |
| `char*` 返回值 | `Pointer` | 手工按 UTF-8 解；两个 mapping getter 要 `SDL_free`，其余不得释放 |
| `const char*` 参数 | `Pointer` | `Memory(len+1)` + 写 UTF-8 + 结尾 0，不用 `String` |
| `SDL_Gamepad*` | `Pointer` | 句柄在 Java 侧仍是 `long`，边界上 `new Pointer(handle)` 互转；`0` 即 NULL |
| `Uint8` / `Sint16` | `byte` / `short` | 与今天 JNI 的 `jbyte` / `jshort` 一致 |
| `Uint16` 参数 | `int` | 同今天：高 16 位被调用方忽略 |

UTF-8 解码统一走 `Pointer.getByte` 扫到 0 再 `getByteArray` + `new String(bytes, UTF_8)`，因为 3.4.0 没有 `getString(offset, encoding)`。

### 需要 Java 侧适配的方法

| `GamepadJNI` 声明 | `JnaGamepad` 原生签名 | 适配 |
|---|---|---|
| `int[] SDL_GetGamepads()` | `Pointer SDL_GetGamepads(IntByReference)` | 取 `count`、`Pointer.getIntArray`、`SDL_free`；`null`/0 返回 `null` |
| `int[] SDL_GetGamepadPowerInfo(long)` | `int SDL_GetGamepadPowerInfo(Pointer, IntByReference)` | 打包成 `int[2]` |
| `float[] SDL_GetGamepadSensorData(long,int,int)` | `byte ...(Pointer, int, float[], int)` | 分配 `float[numValues]`，false 返回 `null` |
| `float[] SDL_GetGamepadTouchpadFinger(long,int,int)` | `byte ...(Pointer, int, int, ByteByReference, FloatByReference, FloatByReference, FloatByReference)` | 打包成 `float[4]`，首元素 `down ? 1f : 0f` |
| `String SDL_GetGamepadGUIDForID(int)` | — | 见下 |
| `String SDL_GetError()` | `Pointer SDL_GetError()` | UTF-8，不释放 |
| 其余 11 个 `String` 返回值 | `Pointer` | UTF-8；`SDL_GetGamepadMapping` / `...ForID` 两个要 `SDL_free` |
| 20 个 `boolean` 返回值 | `byte` | `!= 0` |

另需绑定 `void SDL_free(Pointer)`；`int SDL_GetVersion()` 签名不变。

**句柄为空时必须复刻 C 侧行为**。`GamepadJNI` 是公开 API，调用方可以传 `0`；今天的 C 实现对 `gp == NULL` 逐条有早退（返回 0 / false / null / `SDL_JOYSTICK_CONNECTION_INVALID`）。JNA 适配层照抄这些判断，先把 `handle` 转成 `Pointer`，`null` 就按 C 的分支返回，不要直接调用。

`getGCControllerCount` 只有 macOS 的 JNI 实现；`JnaGamepad` 里返回 `-1`（"不支持"），`GamepadManager.checkMacDisconnect` 遇到负数直接 return，行为与老 native 一致。

### GUID

`SDL_GetGamepadGUIDForID` 返回 `SDL_GUID`，16 字节结构体按值。JNA direct mapping 的结构体按值返回在 3.4.0 上没有验证过，且 32/64 位 ABI 不同。**不动它**：

```
SDL_GetGamepadMappingForID(instanceId)  →  "<guid>,<name>,<mappings>"
取第一个逗号之前的部分
```

SDL 的 mapping 字符串首字段就是 `SDL_GUIDToString` 的输出（`third_party/SDL/src/SDL_guid.c`：小写十六进制，32 字符，无分隔）。语义与今天的返回值等价，代价是每次多分配/释放一个映射串；ControlFlex 只在枚举时调用，不在每帧路径上。

**边界情况必须对齐今天的行为**：`SDL_GetJoystickGUIDForID` 对未知/已拔出的 instance id 是 `SDL_zero(guid)`，所以 C 侧永远返回 32 个 `0` 而不是 null（`SDL_joystick.c:3494`）。JNA 适配层在 mapping 取不到时返回同样的 `"00000000000000000000000000000000"`，不返回 null。

### SystemCursor

`ShowCursor` 在 `user32`，单独一个 `JnaUser32` 类注册（一个类只能绑一个库）。参数是 4 字节 `BOOL`，Java `boolean` 正好对应。

`SystemCursor` 改为调用 `Bindings.b().showCursor(hidden)`，于是 `GamepadJNI` 仍然是 75 个方法，`showCursor` 只存在于 `GamepadBinding` 上。非 Windows 上 `SystemCursor.isSupported()` 早已是 false，JNA 实现里那条分支返回 0，不会被调用。

`JnaUser32.install()` 失败只降级光标功能：`SystemCursor.setHidden` 返回 `null`，与今天拿到老 native 时的表现一致，不影响手柄（该方法只 catch `UnsatisfiedLinkError`，所以 JNA 侧的失败也必须以 ULE 形式抛出或直接返回 null）。

### SDL 日志捕获

JNA 路径**不安装**。`SDL_SetLogOutputFunction` 需要一个 JNA `Callback`，而 SDL 会在自己的内部日志锁里、可能非 Java 线程上回调；现有 JNI 实现刻意只写原生环形缓冲、从不进 JVM，正是为了避开这一点。用 JNA 复刻等于把死锁风险引进来，换几条 DEBUG 日志不值。

`GamepadManager.installSdlLogCapture()` 增加 `Bindings.isJni()` 条件，启动日志里说明一次。`drainSdlLogs()` 已有 `sdlLogCaptureInstalled` 守卫，不需要改。

## 原生库改动

`GamepadJNI` 变成门面后不再是 native 类，75 个 JNI 导出函数的符号必须换到新类：

```
Java_com_ifels_gamepadjni_GamepadJNI_*   →   Java_com_ifels_gamepadjni_JniGamepad_*
```

`src/main/c/gamepad_jni.c` 是一次前缀替换；`gamepad_jni_macos.m` 里的 `getGCControllerCount` 同样处理。需要重编 **9 个平台**：`windows-x86_64`、`windows-aarch64`、`darwin-x86_64`、`darwin-aarch64`、`linux-x86_64`、`linux-aarch64`、`android-arm64-v8a`、`android-armeabi-v7a`、`android-x86_64`。

`build.gradle` 的 `verifyNativeSymbols`：

- 扫描目标从 `GamepadJNI.java` 改为 `JniGamepad.java`；
- 符号前缀改为 `Java_com_ifels_gamepadjni_JniGamepad_`；
- **新增断言：解析出的方法数不得为 0**。否则文件改名后正则失配，检查会静默通过；
- 删掉 `getVersionPendingPlatforms` 那组豁免（`SDL_GetVersion` 在 Linux/Windows 上缺失是 0.10.x 的历史遗留）。这次 9 个平台全部重编，豁免没有存在理由，留着只会掩盖下一轮漂移。

**注意**：符号改名后，`prebuilt/jni` 下 9 个平台必须重编，`gradle jar`（`jar` 依赖 `verifyNativeSymbols`）才会通过。本地只改 Java 时可以跑 `gradle test` 和 `gradle compileJava`；最终产物必须由 CI 的 `build-natives` 工作流重新产出。

`verifyJarContents` 不变（Windows 仍打包 `gamepadjni.dll`，回退路径要用）。CI 的 `build-natives` 工作流不变。

## 兼容性

| 项 | 结论 |
|---|---|
| 消费方源码 | `GamepadManager` / `Gamepad` / `GamepadJNI` / `SystemCursor` / `Sdl3Source` 签名全部不变 |
| 消费方二进制 | ControlFlex 重编即可，无 API 删除；`GamepadJNI` 的 75 个方法仍是 `public static` |
| Java 版本 | 仍是 Java 8 字节码，`--release 8`，JNA 只在 `compileOnly` |
| JAR 体积 | 不变（不打包 JNA） |
| 运行时依赖 | 不变（不声明 JNA） |
| 默认路径 | 与今天完全相同（JNI 成功就不碰 JNA） |

`compileOnly 'net.java.dev.jna:jna:3.4.0'`，并加 `testImplementation` 同一坐标供反射测试用。

文档面同步：README 增补一节（双绑定顺序、`-Dcfx.gamepadjni.binding`、JNA 版本矩阵、1.7.10 说明）；`GamepadJNI` 的类 javadoc 从「JNI native method declarations」改成门面说明，详细方法文档随 native 声明搬到 `JniGamepad`。

## 失败模式

| 情形 | 表现 |
|---|---|
| JNI 加载成功（绝大多数机器） | 与今天逐行一致，JNA 代码不加载 |
| Windows + SAC 拦 `gamepadjni.dll` | `System.load` 抛 ULE → 回退 JNA，手柄可用 |
| Windows + 无 JNA（1.7.10） | 回退不可用 → `BindingFailure` 两段文案（JNI 原因 + JNA 不在 classpath）+ `initialize` 返回 false |
| JNA 注册时符号缺失（SDL3 < 3.2） | 抛 ULE → 两段文案（JNI 原因 + JNA 原因），`initialize` 返回 false |
| `forced=jni` | 不回退，只有 `JNI:` 一段 |
| `forced=jna` + 无 JNA | 直接失败，`JNA:` 段写明不在 classpath 上 |
| `forced=jna` + 非 Windows | 照走 JNA，`showCursor` 返回 0 |
| Android 上 JNI 失败 | `jnaPresent` 一般为 false，回退不发生，文案同 1.7.10 |
| 未调用 `initialize` 就调 `GamepadJNI.*` | `UnsatisfiedLinkError`，与今天同类 |

失败文案由 `BindingFailure.of()` 统一生成，三种形态（无 JNA / 有 JNA 但注册失败 / `forced=jni`）都有单测覆盖，见「两套绑定都不可用时的错误文案」。

对 SAC 特征串（`Application Control policy has blocked`）在 `NativeLibraryLoader` 里做一次识别，回退日志里标注「这是 Smart App Control 拦截，不是加载器缺文件」，并指向文档。不改控制流。

## 测试

单元测试（不需要真机）：

- `BindingPolicyTest`：`jni` / `jna` / `auto` / 非法值 × `jnaPresent` 有/无，断言尝试序列；另测大小写。
- `GamepadFacadeTest`：反射断言 `GamepadJNI` 的 `public static` 方法集合是 `GamepadBinding` 方法集合的子集，且同名方法参数/返回类型逐项相同。防止以后加方法只改一边。
- `BindingsTest`：未安装时 `b()` 抛 `UnsatisfiedLinkError` 而不是 NPE。
- `BindingFailureTest`：三种文案形态——JNA 不在 classpath、JNA 注册抛异常、`forced=jni`（不含 `JNA:` 段）；以及 SAC 特征串命中时 `hint:` 出现。
- 既有 `HostPlatformTest` / `Sdl3SourceTest` / `AndroidPlatformTest` 不受影响。

`compileOnly` 3.4.0 + `testImplementation` 3.4.0，反射只加载类不初始化，不触发 `jnidispatch` 解压（macOS ARM64 上没有它的原生库）。

真机清单（人工）：

1. 1.20.1 Forge，SAC 开启：日志出现「JNI failed → fell back to JNA」，手柄可用，任务管理器里 `gamepadjni.dll` 未加载。
2. 同上加 `-Dcfx.gamepadjni.binding=jni`：复现 SAC 拦截，确认对照组行为。
3. 1.8.9 Forge（JNA 3.4.0）+ `-Dcfx.gamepadjni.binding=jna`：枚举、名字（不乱码）、GUID、震动、电量、触摸板、传感器正常。
4. 1.12.2 / 1.16.5（4.4.0）同第 3 条。
5. 26.3 Fabric（5.17.0）+ `Sdl3Source.HOST` + 强制 jna：与 LWJGL 共用 SDL3，热插拔正常。
6. 1.7.10 Forge：`binding=jni` 成功；再强制 `jna` 应明确失败，日志里三段文案齐全、指得出「这一版没有 JNA」。
7. Linux / macOS 各强制一次 `jna`，确认回退路径在非 Windows 上也能跑通（`showCursor` 不再被调用）。
8. 无线断连/重连、双手柄、息屏唤醒。

第 3–5、7 条是这次的重点：默认路径几乎永远走 JNI，JNA 分支只有被强制时才会在真机上跑到，必须显式覆盖。

## 明确不做

- 不做 GLFW 兜底。
- 不打包 JNA、不 shade、不重定位。
- 不为 1.7.10 单独发一个内嵌 JNA 的变体。
- 不实现 JNA 版的 SDL 日志回调。
- 不用 `Structure.ByValue` 接 `SDL_GUID`。
- 不改 `Sdl3Source` 的语义，也不让 JNA 路径自动改选 SDL 来源。
- 不把 `GamepadBinding` 做成 public API。

## 风险

1. **JNA 路径在默认配置下几乎不会被走到**，只有 JNI 加载失败才触发。也就是说它天然缺少现场验证。对策：真机清单里用 `-Dcfx.gamepadjni.binding=jna` 显式覆盖 1.8.9 / 1.12.2 / 1.16.5 / 26.3 四档，并要求发版前跑一遍。
2. **JNA 3.4.0 的 `Native.register` 未实测**（2012 年的实现）。若 1.8.9–1.11.2 上失败，把运行期最低线抬到 4.4.0，这四版回退即失败；编译目标仍是 3.4.0（更低只会更安全）。
3. **SAC 也可能拦 `SDL3.dll`**。调研文档第 6 节的待确认事项 2 还没闭环。若拦的是 `SDL3.dll`，JNA 只是把 `gamepadjni.dll` 从名单里去掉，问题不会消失，最终仍需代码签名。上线前应向报障者取 `%TEMP%` 下两个 DLL 的 SHA-256 与 CodeIntegrity 3077 事件比对。
4. **`bool` 返回值写成 `boolean` 会得到随机真假**。`JnaGamepad` 里所有 `bool` 返回必须是 `byte`；没有自动化测试能覆盖，只能靠评审与真机清单。
5. **`Native.register` 的失败是部分注册**。所以它只注册独立的 `JnaGamepad`，绝不注册 `GamepadJNI`；回退后两条路径互不影响。

---

## 附：Windows PE 时间戳

同一批改动里一起做。

### 现状（实测 `prebuilt/jni` 里两个 DLL 的 PE 头）

| 产物 | `IMAGE_FILE_HEADER.TimeDateStamp` | 解读 |
|---|---|---|
| `windows-x86_64/gamepadjni.dll` | `0` | 被 `-Wl,--no-insert-timestamp` 清零 |
| `windows-aarch64/gamepadjni.dll` | `2478577768` → 2048-07-17 | lld 的确定性哈希值，不是真实时间 |

两个都不正常：一个是 1970，一个在 2048 年。文件头里这两种值本身就是「被工具链处理过」的痕迹。

### 改法：一个后处理脚本，不碰链接器

两条工具链给出的值互不相干（GNU ld 写 0，lld 写哈希），靠各自的开关没法统一成一个值。所以**链接器参数一律不动**（保留 `-Wl,--no-insert-timestamp` 与 `-Wl,-s`，中间产物保持确定性），在 `organize_output_windows()` 复制完 DLL 之后统一 stamp：

`prebuilt/patch-pe-timestamp.py <dll>...`

1. 取时间戳：`SOURCE_DATE_EPOCH` → `git log -1 --format=%ct` → **报错退出**。最后一条是刻意的：静默退回当前时间会让可复现性悄悄失效。
2. 解析 PE，把两处 `TimeDateStamp` 写成同一个值：COFF 文件头，以及导出目录（按 `IMAGE_DIRECTORY_ENTRY_EXPORT` 的 RVA 定位，不硬编码 `0x4004`）。
3. 若原 PE 校验和非 0，按标准算法重算；原值为 0（lld 的产物）就保持 0。
4. 幂等：已经是目标值时不做任何写入，便于重复执行。

调用方按 `python3` → `python` → `py -3` 顺序探测；仓库的 `build-jni-android.sh` 本来就依赖 `python3`，不引入新前置条件。

构建末尾加断言，写进 `verify_output()` 的 Windows 分支（与 `verify_output_linux()` 风格一致）：两个 DLL 的 `TimeDateStamp` 必须相等、非 0、且不晚于当前时间 + 1 天。这条断言直接读 PE 头，不信任链接器。

**副作用**：`CMakeLists.txt:162-175` 那段注释要改——它现在说清零是为了「byte-identical run to run」，而最终字节由 stamp 决定。改成「中间产物可复现，最终时间戳由 patch-pe-timestamp.py 统一写入」。

单独跑 CMake（README 里的通用构建步骤）不会 stamp，DLL 仍是 0；这不影响发布，因为发布产物只走 `build-jni.sh` / CI。

### 为什么不直接吃「链接时刻」

`build.gradle` 现在宣称 JAR 字节可复现（`preserveFileTimestamps = false` + 固定条目顺序），README 用它支撑「同一 tag 重编得到同一个 hash，发布页的 checksum 谁都能验」。真实链接时间会让每次构建的 DLL 字节不同，`gamepadjni.dll` 一进 JAR 这个性质就没了。

更实际的理由：**固定时间戳让同一版本的 DLL 在所有用户机器上是同一个哈希**。SAC / Defender 的云端信誉是按文件哈希累积的，每次构建换一个哈希等于每次从零开始攒信誉。这与本次改动的初衷直接冲突。

所以取固定值：既有时间戳，又保住可复现与哈希稳定。
