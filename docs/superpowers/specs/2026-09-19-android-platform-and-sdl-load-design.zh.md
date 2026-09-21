# Android 平台判定与启动器 SDL3 加载

日期：2026-09-19

- [English version](2026-09-19-android-platform-and-sdl-load-design.en.md)

SDL 搜索加载与平台判定均以本文为准：只认 `os.version` 前缀；`os.name` 只写诊断日志。桌面 HOST / BUNDLED 互斥加载不在本文展开，只说明判定为非 Android 时的分流。启动器 JVM 参数与环境变量的逐项对照见 [docs/android](../../android/README.md)。

## 目标

在 Android 版 Minecraft Java 启动器提供的游戏 JVM 里：

1. 用 `os.version` 判定平台，不靠环境变量，也不靠 `os.name`。
2. 加载**启动器 APK 自带**的 `libSDL3.so`（启动器 ART 侧已安装 `org.libsdl.app.*` glue）。
3. 再加载本库的 `libgamepadjni.so`。失败时初始化返回 false，游戏继续、无手柄。

## 支持的启动器

兼容说明，**不是运行时白名单**。判定只看 `os.version`。未列入的启动器只要把 `os.version` 设成 `Android-` 前缀，也会走 Android 路径。四家默认仍会同时注入 `-Dos.name=Linux`，但那条只出现在诊断日志里。

| 启动器 | 源码基准 | 启动器默认注入 | 如何找到启动器 `libSDL3.so` |
|--------|----------|--------------------|------------------------------|
| Fold Craft Launcher (FCL) | 见 [docs/android](../../android/README.md) | `-Dos.name=Linux` `-Dos.version=Android-<RELEASE>` | 设 `POJAV_NATIVEDIR` 为 APK native 目录，走绝对路径 |
| ZalithLauncher2 (ZL2) | 见 [docs/android](../../android/README.md) | 同上 | 同上 |
| Mojo Launcher | **只认 `v3_openjdk` 分支** | 同上 | **不设** `POJAV_NATIVEDIR`；`LD_LIBRARY_PATH` 含 APK native 目录；`System.loadLibrary("SDL3")` |
| Amethyst | 见 [docs/android](../../android/README.md) | 同上 | 设 `POJAV_NATIVEDIR`，走绝对路径 |

四家默认都会注入那两条 `-D`。用户自定义 JVM 参数若覆盖同名前缀，可能破坏判定（例如后写入的 `-Dos.version=5.0`）。那是启动器行为，本库不补偿。

Android 上的 SDL3 **只来自启动器 APK**。不使用：

- JAR 内打包的桌面 SDL3
- 调用方传入的 `Sdl3Source.HOST` / `Sdl3Source.BUNDLED`
- `CFX_LIB_PATH` 指向的另一份 SDL
- Mojo 写给 LWJGL 的 `-Dorg.lwjgl.sdl.libname=<apk>/libSDL3.so`（本库不读；LWJGL 与 `loadLibrary("SDL3")` 应对准 APK 里同一份库）

## 平台判定

入口：`AndroidPlatform.isAndroid()`。类加载时读一次 `os.version`，结果缓存。纯函数 `detect(osVersion)` 供测试传入显式值。

桌面 JVM 的 `os.version` 是内核号（如 `10.0`、`24.6.0`、`6.1.0`）。只有 Android 启动器会写成 `Android-<RELEASE>`。因此前缀本身就能和 Win / Mac / GNU/Linux 桌面分开。`os.name=Linux` 与 PC Linux 相同，不能当判定条件。

算法（忽略大小写，`Locale.ROOT`）：

1. `osVersion` 非 null。
2. `osVersion.toLowerCase(ROOT).startsWith("android-")`（必须带连字符；`Android`、`Android14` 不算）。

不成立 → 不是 Android。

`os.name` **不参与判定**，只在 `loadAndroid()` 的诊断日志里输出，方便对照启动器是否同时写了 `-Dos.name=Linux`。

**判定不使用：** `os.name`、`POJAV_NATIVEDIR`、`FCL_NATIVEDIR`、厂商字符串、`os.arch`、`java.vm.name`、`org.lwjgl.sdl.libname`、Windows / macOS 残留环境变量。桌面进程即使 export 了 `POJAV_NATIVEDIR` 也不会被判成 Android。

空 / 缺省 `os.version`（`""`）不以 `android-` 开头，因此不是 Android。

## 加载分流

`GamepadManager.initialize(Sdl3Source source)` → `NativeLibraryLoader.load(source)` → `Sdl3Source.resolveLoadKind(isAndroid, source)`：

| `isAndroid` | 调用方 `Sdl3Source` | `LoadKind` | 行为 |
|-------------|---------------------|------------|------|
| true | HOST 或 BUNDLED（均被忽略） | `LAUNCHER` | `loadAndroid()`；`hostSdl3 = true`（与启动器共享进程内 SDL，后续不 `SDL_Quit` 整库） |
| false | HOST | `HOST` | 桌面宿主 SDL；找不到则失败，不回退 BUNDLED |
| false | BUNDLED | `BUNDLED` | 只用 JAR 内 SDL |
| 任意 | null | — | `NullPointerException("sdl3 source")` |

Android 命中后不再走桌面 HOST 搜索（LWJGL `SDL.getLibrary()`、`CFX_HOST_SDL3_DIR`、`org.lwjgl.librarypath` 等）。

## Android：搜索并加载启动器 SDL3

`loadAndroid()` 先打一组诊断日志（`os.name` / `os.version` / `os.arch` / `java.vm.name`、`POJAV_NATIVEDIR`、`java.io.tmpdir`、ABI 目录、`java.library.path`），再调用 `loadLauncherSdl3()`。

顺序是硬约束：先 SDL3，再 `libgamepadjni.so`。Android 上 JNI 的 `DT_NEEDED` 是 `libSDL3.so`，动态链接器必须绑到刚刚 `System.load` / `loadLibrary` 的那一份。

### 第一步：`POJAV_NATIVEDIR`

`AndroidPlatform.launcherSdlPath(System.getenv("POJAV_NATIVEDIR"))`：

- 变量为 null 或空 → 跳过。
- 否则拼 `$POJAV_NATIVEDIR/libSDL3.so`（正斜杠，不做 canonical / exists 检查），`System.load(绝对路径)`。
- 成功 → 使用该路径。
- 失败 → 打 warn，**继续第二步**。捕获 `Throwable`（不仅是 `UnsatisfiedLinkError`），因为 `JNI_OnLoad` 可能留下 `Error`。

FCL / ZL2 / Amethyst 默认走通这一步。Mojo `v3_openjdk` 的 Java 层不 `setenv(POJAV_NATIVEDIR)`，除非用户在 `custom_env.txt` 里自己写。

### 第二步：`System.loadLibrary("SDL3")`

JVM 在 `java.library.path` 里找 `libSDL3.so`。若启动器未显式设该属性，HotSpot 默认会把 `LD_LIBRARY_PATH` 收进搜索路径。

Mojo `v3_openjdk`：

- `JavaRunner.relocateLdLibPath` 始终把 APK native 目录（`Tools.NATIVE_LIB_DIR`）写入 `LD_LIBRARY_PATH`。
- 仅当 `cache/natives/<versionId>` 存在时才额外写 `-Djava.library.path=<version natives>:<apk natives>`。
- 版本 JSON 里的 `-Djava.library.path=` 会被 Mojo 丢掉，不会冲掉上面两套。

成功则仍是启动器 APK 里的 `libSDL3.so`。

### 两步都失败

抛 `UnsatisfiedLinkError`，文案包含试过的 `POJAV_NATIVEDIR` 路径和 `System.loadLibrary("SDL3")`。`initialize` 捕获后返回 false。

`CFX_LIB_PATH` **不**用于 SDL：再加载一份没有启动器 glue 的 SDL3 会让手柄永远不可见。

## Android：加载 `libgamepadjni.so`

仅在 SDL3 成功之后：

1. 若 `CFX_LIB_PATH`（系统属性优先于环境变量）是存在的目录，且其中有 `libgamepadjni.so`，则 `System.load` 该文件。
2. 否则按 `os.arch` 映射 JAR 目录并解压：
   - `aarch64` / `arm64` → `native/android-arm64-v8a/`
   - `arm` / `armv7l` / `armv8l` → `native/android-armeabi-v7a/`
   - `x86_64` / `amd64` → `native/android-x86_64/`
   - 其他（含 `i386`）→ `UnsatisfiedLinkError`（不支持的 ABI）
3. classpath 里没有对应 native → `UnsatisfiedLinkError`。

JAR **不**打包 Android 版 `libSDL3.so`。

## 错误处理

| 失败 | 对外表现 |
|------|----------|
| SDL3 两步都失败 | `UnsatisfiedLinkError` → `initialize` 返回 false |
| JNI 桥 `System.load` 抛任何 `Throwable` | 转成 `UnsatisfiedLinkError` → 返回 false |
| `SDL_InitSubSystem` 失败 | 返回 false（库已加载） |
| 其他 `Throwable` | 返回 false，不让 Error 冲到游戏入口 |

Android 上 `hostSdl3 == true`，即使本库先 `InitSubSystem`，也不在销毁时 `SDL_Quit` 整库，以免拆掉启动器的 video / glue。

## 测试

`AndroidPlatformTest` 覆盖：

- 正例：`Android-14` / `13` / `10`；大小写混合。与 `os.name` 无关（`Windows 11` + `Android-14` 也是 Android）。
- 反例：内核版本号（`6.1.0`）、`Android` 无连字符、`Android14`、null / 空。
- `launcherSdlPath` 拼接与空目录。
- ABI 目录映射（与 SDL 搜索无关，供 JNI 解压）。

`Sdl3SourceTest` 覆盖 Android 时 HOST / BUNDLED 都解析为 `LAUNCHER`。

不在单元测试里 `System.load` 真机 `.so`。

## 明确不做

- 不把启动器名单做成运行时检测。
- 不把 `POJAV_NATIVEDIR` 当 Android 入场券。
- 不读 `-Dorg.lwjgl.sdl.libname`。
- 不为 Android 打包第二份 SDL3。
- 不核对 Mojo 非 `v3_openjdk` 分支。
- 不把 `os.name` 当判定条件（只打日志）。
- 不把 Win/Mac 残留环境变量当 Android 信号。
