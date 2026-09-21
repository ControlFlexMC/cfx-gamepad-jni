# Android 启动器环境变量对照

对照日期：2026-09-19。

四款启动器在拉起游戏 JVM 之前通过 `Os.setenv` / `bridge.setenv` 写入的环境变量。渲染器相关项随所选后端变化。

- [English version](launcher-env-vars.en.md)
- JVM 参数见 [launcher-jvm-args.zh.md](launcher-jvm-args.zh.md)

## 源码

| 启动器 | 路径 |
| --- | --- |
| FCL | `FCLauncher.addCommonEnv` / `addRendererEnv` / `addModLoaderEnv`（[FCLauncher.java](https://github.com/FCL-Team/FoldCraftLauncher/blob/ef0f9a209cbf92015131f588e5eaceff9289de46/FCLauncher/src/main/java/com/tungsten/fclauncher/FCLauncher.java)） |
| Zalith Launcher 2 | [Launcher.kt](https://github.com/ZalithLauncher/ZalithLauncher2/blob/main/ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/launch/Launcher.kt) `setJavaEnv`、[GameLauncher.kt](https://github.com/ZalithLauncher/ZalithLauncher2/blob/main/ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/launch/GameLauncher.kt) `setRendererEnv` |
| Mojo Launcher | `v3_openjdk`：[JavaRunner](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/JavaRunner.java) `setImmutableEnvVars` / `relocateLdLibPath`、[JREUtils.setGameEnvironment](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java)、[GameRenderer.setupEnvironment](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/game/renderer/GameRenderer.java) |
| Amethyst Android | `v3_openjdk`：[JREUtils.setJavaEnvironment](https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java) |

---

## 四家都会设的（进程级）

这四项每家启动游戏时都会写：

| 变量 | 典型值 |
| --- | --- |
| `JAVA_HOME` | 所选 JRE 根目录 |
| `HOME` | 游戏/外部文件目录（FCL 写成日志目录） |
| `TMPDIR` | 应用 cache |
| `LD_LIBRARY_PATH` | JRE lib + `/system`/`vendor` lib + APK native dir（再拼渲染器/插件） |

`PATH` 由 FCL / ZL2 / Amethyst 写成 `<jre>/bin:` + 原 `PATH`。当前 Mojo `JavaRunner` **不再**设 `PATH`（旧现场日志里有）。

---

## `POJAV_NATIVEDIR`

指向 **APK 的 nativeLibraryDir**（如 `/data/app/.../lib/arm64`），里面有 `libSDL3.so`、`libopenal.so` 等。

| 启动器 | 是否设置 |
| --- | --- |
| FCL | 有，且同时写 `FCL_NATIVEDIR`（同路径） |
| ZL2 | 有 |
| Amethyst | 有 |
| Mojo | **旧版日志有**；当前 `v3_openjdk` 的 Java 层不再 `setenv(POJAV_NATIVEDIR)`，改由 `MojoExec.setNativeLibraryDir` 管 native 搜索路径 |

检测器不把 `POJAV_NATIVEDIR` 当入场券；判定只认 `os.version` 以 `Android-` 开头（忽略大小写）。该变量只用于已经判定为 Android 之后定位 `libSDL3.so`。

---

## FCL

### 始终（`addCommonEnv`）

```
FCL_VERSION_CODE=<versionCode>
HOME=<logDir>
JAVA_HOME=<javaPath>
FCL_NATIVEDIR=<apk nativeLibraryDir>
POJAV_NATIVEDIR=<同上>
DRIVER_PATH=<所选驱动插件路径>
TMPDIR=<cache>
PATH=<java>/bin:<原 PATH>          # 有 FFmpeg 插件时再前置其 libraryPath
LD_LIBRARY_PATH=<JRE + system/vendor + jna + 渲染器 + native 插件 + MOD_RUNTIME + apk natives>
FORCE_VSYNC=false
MOD_ANDROID_RUNTIME=<app_runtime_mod 或空>
```

条件项：

- 使用系统 Vulkan 驱动：`VULKAN_DRIVER_SYSTEM=1`
- 大核亲和：`POJAV_BIG_CORE_AFFINITY=1`
- Android &lt; 11 且加载了渲染器 so：`RENDERER_HANDLE=<dlopen handle>`

### 模组加载器（`addModLoaderEnv`，装了才写）

```
INST_FORGE=1
INST_CLEANROOM=1
INST_NEOFORGE=1
INST_LITELOADER=1
INST_FABRIC=1
INST_OPTIFINE=1
INST_QUILT=1
```

### 渲染器（`addRendererEnv`，随后端变）

插件渲染器会把 `renderer.getPojavEnv()` 整表灌进去，并设 `POJAVEXEC_EGL`。内置后端常见：

**GL4ES / VGPU**

```
LIBGL_ES=2
LIBGL_MIPMAP=3
LIBGL_NORMALIZE=1
LIBGL_NOINTOVLHACK=1
LIBGL_NOERROR=1
POJAV_RENDERER=opengles2 | opengles2_vgpu
```

**NG-GL4ES**

```
LIBGL_USE_MC_COLOR=1
DLOPEN=libspirv-cross-c-shared.so
LIBGL_GL=31
LIBGL_ES=3
LIBGL_NORMALIZE=1
LIBGL_NOINTOVLHACK=1
LIBGL_NOERROR=1
POJAV_RENDERER=opengles3
POJAVEXEC_EGL=libEGL.so
```

**Zink / VirGL / Freedreno（Mesa）**

```
MESA_GLSL_CACHE_DIR=<cache>
MESA_GL_VERSION_OVERRIDE=4.6 | 4.3
MESA_GLSL_VERSION_OVERRIDE=460 | 430
force_glsl_extensions_warn=true
allow_higher_compat_version=true
allow_glsl_extension_directive_midshader=true
MESA_LOADER_DRIVER_OVERRIDE=zink
VTEST_SOCKET_NAME=<cache>/.virgl_test
POJAV_RENDERER=vulkan_zink | gallium_virgl | gallium_freedreno
OSMESA_NO_FLUSH_FRONTBUFFER=1          # 仅 VirGL
```

现场日志里插件渲染器还常见 `LIBGL_NAME`、`LIBGL_STRING`、`LIBEGL_NAME`、`GALLIUM_DRIVER`、`LIB_MESA_NAME`。

---

## Zalith Launcher 2

### 始终（`setJavaEnv`）

```
POJAV_NATIVEDIR=<apk natives>
JAVA_HOME=<runtime>
HOME=<外部 files 目录>
TMPDIR=<cache>
LD_LIBRARY_PATH=<lwjgl natives + system/vendor + 插件 + runtime_mod + apk natives>
PATH=<runtime>/bin:<原 PATH>
AWTSTUB_WIDTH=<宽>
AWTSTUB_HEIGHT=<高>
MOD_ANDROID_RUNTIME=<runtime_mod 或空>
DALVIK_JAVAVM=<JavaVM 指针>
DALVIK_APPLICATION=<Application jobject>
ALSOFT_DRIVERS=opensl
```

条件项：

- `LIBGL_VGPU_DUMP=1`（dump shaders）
- `POJAV_ZINK_PREFER_SYSTEM_DRIVER=1`
- `POJAV_VSYNC_IN_ZINK=1`
- `POJAV_FFMPEG_PATH=<ffmpeg>`

### 游戏启动再加（`GameLauncher.initEnv`）

```
DRIVER_PATH=<所选驱动>
ZALITH_VERSION_CODE=<VERSION_CODE>
JSP=<libjsph17.so | libjsph21.so>     # Java >= 11 且库存在
<loaderEnvKey>=1                      # 模组加载器自己的 INST_* 类 key
```

### 渲染器（`setRendererEnv`）

```
SDL_OPENGL_LIBRARY=<rendererId>
POJAV_RENDERER=<rendererId>
POJAVEXEC_EGL=<egl so>                # 渲染器提供了 EGL 时
SDL_EGL_LIBRARY=<nativeDir>/<egl so>
```

`opengles2*`：

```
LIBGL_ES=2
LIBGL_MIPMAP=3
LIBGL_NOERROR=1
LIBGL_NOINTOVLHACK=1
LIBGL_NORMALIZE=1
```

非 GL4ES / NG-GL4ES 时再加 Mesa：

```
MESA_LOADER_DRIVER_OVERRIDE=zink
MESA_GLSL_CACHE_DIR=<cache>
MESA_GL_VERSION_OVERRIDE=4.6
MESA_GLSL_VERSION_OVERRIDE=460
force_glsl_extensions_warn=true
allow_higher_compat_version=true
allow_glsl_extension_directive_midshader=true
LIB_MESA_NAME=<renderer library>
LIBGL_ES=<2 或 3，按检测>
```

另合并 `renderer.getRendererEnv()`。

---

## Mojo Launcher（当前 `v3_openjdk`）

拆成三处，比 Pojav 旧 `setJavaEnvironment` 瘦很多。

### `JavaRunner`（启动 VM 时必写）

```
JAVA_HOME=<jre>
HOME=<DIR_GAME_HOME>
TMPDIR=<cache>
LD_LIBRARY_PATH=<jre lib>:<apk natives>:<vm dir>:<jli>
```

### `JREUtils.setGameEnvironment`

```
MOD_ANDROID_RUNTIME=<cache>/app_runtime_mod
POJAV_FFMPEG_PATH=...                 # FFmpeg 插件存在时
POJAV_BIG_CORE_AFFINITY=1             # 选项
ALSOFT_DRIVERS=opensl                 # PREF_ALSOFT_FORCE_OPENSL
```

还可读游戏目录下 `custom_env.txt` 覆盖任意键。

### `GameRenderer.setupEnvironment`（随渲染器）

**GLES（GL4ES / LTW）基类**

```
force_glsl_extensions_warn=true
allow_higher_compat_version=true
allow_glsl_extension_directive_midshader=true
LIBGL_NOERROR=1
LIBGL_VGPU_DUMP=1                     # dump shaders
LIBGL_EGL=... / LIBGL_GLES=...        # ANGLE 提供者才会写；系统 GLES 不写
```

**Zink**

```
MESA_LOADER_DRIVER_OVERRIDE=zink
MESA_GLSL_VERSION_OVERRIDE=460
MESA_GL_VERSION_OVERRIDE=4.6
MESA_GLSL_CACHE_DIR=<cache>
```

**Freedreno**

```
MESA_LOADER_DRIVER_OVERRIDE=kgsl
FD_MESA_DEBUG=sysmem                  # 选项
MESA_GL_VERSION_OVERRIDE=3.3          # Adreno 5xx 及以下
MESA_GLSL_VERSION_OVERRIDE=330
MESA_GLSL_CACHE_DIR=<cache>
TU_DEBUG=sysmem                       # overrideVulkanDriver + PREF_FREEDRENO_SYSMEM
```

旧现场日志里还有 `POJAV_NATIVEDIR`、`PATH`、`MOJO_RENDERER`、`FORCE_VSYNC`、`LIBGL_MIPMAP` 等，那是拆分前的 `setJavaEnvironment`。**当前 Java 源码不再写这些。**

---

## Amethyst Android

### 始终（`setJavaEnvironment`）

```
POJAV_NATIVEDIR=<apk natives>
JAVA_HOME=<jre>
HOME=<DIR_GAME_HOME>
TMPDIR=<cache>
LIBGL_MIPMAP=3
LIBGL_NOERROR=1
LIBGL_NOINTOVLHACK=1
LIBGL_NORMALIZE=1
LIBGL_ES=<偏好 OpenGL 版本，随后端再覆盖>
FORCE_VSYNC=<PREF_FORCE_VSYNC>
MESA_GLSL_CACHE_DIR=<cache>
force_glsl_extensions_warn=true
allow_higher_compat_version=true
allow_glsl_extension_directive_midshader=true
VTEST_SOCKET_NAME=<cache>/.virgl_test
LD_LIBRARY_PATH=<jre + system/vendor + apk natives + lwjgl natives>
PATH=<jre>/bin:<原 PATH>
AWTSTUB_WIDTH / AWTSTUB_HEIGHT
DALVIK_APPLICATION
DALVIK_JAVAVM
```

条件项：

- `LIBGL_VGPU_DUMP=1`、`POJAV_VSYNC_IN_ZINK=1`、`POJAV_EMUI_ITERATOR_MITIGATE=1`
- `POJAV_FFMPEG_PATH`
- `POJAV_BIG_CORE_AFFINITY=1`
- Adreno 且不优先系统驱动：`POJAV_LOAD_TURNIP=1`
- `custom_env.txt` 最后覆盖

### 按 `LOCAL_RENDERER`

```
AMETHYST_RENDERER=<renderer id>
```

| 渲染器 | 额外环境变量 |
| --- | --- |
| `opengles3_ltw` | `LIBGL_ES=3`，`POJAVEXEC_EGL=libltw.so` |
| `opengles_mobileglues` | `MG_DIR_PATH`，`LIBGL_ES=3`，`POJAVEXEC_EGL=libmobileglues.so` |
| `opengles2` + ANGLE | `LIBGL_ES=2`，`LIBGL_GLES=libGLESv2_angle.so`，`LIBGL_EGL=libEGL_angle.so`，`POJAVEXEC_EGL=libEGL_angle.so` |
| `opengles_system_gles` + ANGLE | `POJAVEXEC_EGL=libEGL_angle.so` |
| `opengles3_desktopgl_zink_kopper` | `POJAVEXEC_EGL=libEGL_mesa.so`，可选 `FD_DEV_FEATURES` |
| 名称含 `zink` | `MESA_GL_VERSION_OVERRIDE=4.6COMPAT`，`MESA_GLSL_VERSION_OVERRIDE=460` |
| `useSFPEW` | `SFPEW_EGL=<POJAVEXEC_EGL>` |

启动 VM 前还会设 SDL（非 `opengles_system_gles` 时）：

```
SDL_OPENGL_LIBRARY=<graphicsLib>
SDL_EGL_LIBRARY=<apk natives>/<POJAVEXEC_EGL>
```

---

## 差异速查

| 变量 | FCL | ZL2 | Mojo（当前 Java） | Amethyst |
| --- | --- | --- | --- | --- |
| `JAVA_HOME` / `HOME` / `TMPDIR` / `LD_LIBRARY_PATH` | 有 | 有 | 有 | 有 |
| `PATH` | 有 | 有 | 无 | 有 |
| `POJAV_NATIVEDIR` | 有 | 有 | 当前 Java 无（旧日志有） | 有 |
| `FCL_NATIVEDIR` | **有** | 无 | 无 | 无 |
| `DRIVER_PATH` | 有 | 有 | 无 | 无 |
| `MOD_ANDROID_RUNTIME` | 有 | 有 | 有 | 无 |
| `AWTSTUB_WIDTH/HEIGHT` | 无 | 有 | 无 | 有 |
| `DALVIK_JAVAVM` / `DALVIK_APPLICATION` | 无 | 有 | 无 | 有 |
| `ALSOFT_DRIVERS=opensl` | 无 | 强制 | 可选 | 无 |
| `POJAV_RENDERER` | 有 | 有 | 无（改 `MOJO_RENDERER` 旧日志） | 无（改 `AMETHYST_RENDERER`） |
| `AMETHYST_RENDERER` | 无 | 无 | 无 | **有** |
| `SDL_OPENGL_LIBRARY` / `SDL_EGL_LIBRARY` | 无 | 有 | 无 | 有 |
| `POJAVEXEC_EGL` | 常有 | 常有 | 无（EGL 走 `MojoExec.prepareEgl`） | 常有 |
| `INST_*` 模组加载器标记 | 有 | 有（loader key） | 无 | 无 |

---

## 对检测器的含义

1. **四家稳定共有的只有** `JAVA_HOME`、`HOME`、`TMPDIR`、`LD_LIBRARY_PATH`。这些在桌面 JVM 里也可能存在，单独不够当 Android 信号。
2. **`POJAV_NATIVEDIR` 只用来定位 `libSDL3.so`，不当 Android 入场券。** PC Linux 也能 export 同名变量。判定只认 `os.version` 以 `Android-` 开头。
3. `POJAV_RENDERER` **不是**四家共有：FCL/ZL2 用它，Amethyst 用 `AMETHYST_RENDERER`，当前 Mojo Java 不设。
4. 渲染器变量（`LIBGL_*`、`MESA_*`）随后端变化，不能当平台检测依据。
5. Windows/macOS 上残留的 `POJAV_NATIVEDIR` 不参与判定；只认 `os.version` 以 `Android-` 开头。
