# Android launcher environment variables

Survey date: 2026-09-19.

Environment variables the four launchers write with `Os.setenv` / `bridge.setenv` before starting the game JVM. Renderer-related keys change with the selected backend.

- [中文版](launcher-env-vars.zh.md)
- JVM flags: [launcher-jvm-args.en.md](launcher-jvm-args.en.md)

## Sources

| Launcher | Paths |
| --- | --- |
| FCL | `FCLauncher.addCommonEnv` / `addRendererEnv` / `addModLoaderEnv` ([FCLauncher.java](https://github.com/FCL-Team/FoldCraftLauncher/blob/ef0f9a209cbf92015131f588e5eaceff9289de46/FCLauncher/src/main/java/com/tungsten/fclauncher/FCLauncher.java)) |
| Zalith Launcher 2 | [Launcher.kt](https://github.com/ZalithLauncher/ZalithLauncher2/blob/main/ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/launch/Launcher.kt) `setJavaEnv`, [GameLauncher.kt](https://github.com/ZalithLauncher/ZalithLauncher2/blob/main/ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/launch/GameLauncher.kt) `setRendererEnv` |
| Mojo Launcher | `v3_openjdk`: [JavaRunner](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/JavaRunner.java) `setImmutableEnvVars` / `relocateLdLibPath`, [JREUtils.setGameEnvironment](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java), [GameRenderer.setupEnvironment](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/game/renderer/GameRenderer.java) |
| Amethyst Android | `v3_openjdk`: [JREUtils.setJavaEnvironment](https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java) |

---

## Set by all four (process-wide)

Every launcher writes these when starting the game:

| Variable | Typical value |
| --- | --- |
| `JAVA_HOME` | Selected JRE root |
| `HOME` | Game / external files dir (FCL uses the log directory) |
| `TMPDIR` | App cache |
| `LD_LIBRARY_PATH` | JRE libs + `/system`/`vendor` libs + APK native dir (plus renderer/plugin paths) |

`PATH` is `<jre>/bin:` plus the previous `PATH` on FCL / ZL2 / Amethyst. Current Mojo `JavaRunner` does **not** set `PATH` (older field logs still had it).

---

## `POJAV_NATIVEDIR`

Points at the **APK `nativeLibraryDir`** (for example `/data/app/.../lib/arm64`), which holds `libSDL3.so`, `libopenal.so`, and related launcher natives.

| Launcher | Set? |
| --- | --- |
| FCL | Yes, and also `FCL_NATIVEDIR` (same path) |
| ZL2 | Yes |
| Amethyst | Yes |
| Mojo | **Present in older logs**; current `v3_openjdk` Java no longer `setenv(POJAV_NATIVEDIR)` and uses `MojoExec.setNativeLibraryDir` for native search instead |

Do not treat `POJAV_NATIVEDIR` as an Android gate; detection uses only `os.version` starting with `Android-` (case-insensitive). The variable is used to locate `libSDL3.so` after Android has already been identified.

---

## FCL

### Always (`addCommonEnv`)

```
FCL_VERSION_CODE=<versionCode>
HOME=<logDir>
JAVA_HOME=<javaPath>
FCL_NATIVEDIR=<apk nativeLibraryDir>
POJAV_NATIVEDIR=<same>
DRIVER_PATH=<selected driver plugin>
TMPDIR=<cache>
PATH=<java>/bin:<old PATH>          # FFmpeg plugin prepends its libraryPath
LD_LIBRARY_PATH=<JRE + system/vendor + jna + renderer + native plugins + MOD_RUNTIME + apk natives>
FORCE_VSYNC=false
MOD_ANDROID_RUNTIME=<app_runtime_mod or empty>
```

Conditional:

- System Vulkan driver: `VULKAN_DRIVER_SYSTEM=1`
- Big-core affinity: `POJAV_BIG_CORE_AFFINITY=1`
- Android &lt; 11 with a loaded renderer `.so`: `RENDERER_HANDLE=<dlopen handle>`

### Mod loaders (`addModLoaderEnv`, only if installed)

```
INST_FORGE=1
INST_CLEANROOM=1
INST_NEOFORGE=1
INST_LITELOADER=1
INST_FABRIC=1
INST_OPTIFINE=1
INST_QUILT=1
```

### Renderer (`addRendererEnv`, backend-specific)

Plugin renderers dump `renderer.getPojavEnv()` and set `POJAVEXEC_EGL`. Built-in backends:

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

**Zink / VirGL / Freedreno (Mesa)**

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
OSMESA_NO_FLUSH_FRONTBUFFER=1          # VirGL only
```

Plugin-renderer field logs also often show `LIBGL_NAME`, `LIBGL_STRING`, `LIBEGL_NAME`, `GALLIUM_DRIVER`, `LIB_MESA_NAME`.

---

## Zalith Launcher 2

### Always (`setJavaEnv`)

```
POJAV_NATIVEDIR=<apk natives>
JAVA_HOME=<runtime>
HOME=<external files dir>
TMPDIR=<cache>
LD_LIBRARY_PATH=<lwjgl natives + system/vendor + plugins + runtime_mod + apk natives>
PATH=<runtime>/bin:<old PATH>
AWTSTUB_WIDTH=<w>
AWTSTUB_HEIGHT=<h>
MOD_ANDROID_RUNTIME=<runtime_mod or empty>
DALVIK_JAVAVM=<JavaVM pointer>
DALVIK_APPLICATION=<Application jobject>
ALSOFT_DRIVERS=opensl
```

Conditional:

- `LIBGL_VGPU_DUMP=1` (dump shaders)
- `POJAV_ZINK_PREFER_SYSTEM_DRIVER=1`
- `POJAV_VSYNC_IN_ZINK=1`
- `POJAV_FFMPEG_PATH=<ffmpeg>`

### Extra on game launch (`GameLauncher.initEnv`)

```
DRIVER_PATH=<selected driver>
ZALITH_VERSION_CODE=<VERSION_CODE>
JSP=<libjsph17.so | libjsph21.so>     # Java >= 11 when the lib exists
<loaderEnvKey>=1                      # loader-specific INST_* style key
```

### Renderer (`setRendererEnv`)

```
SDL_OPENGL_LIBRARY=<rendererId>
POJAV_RENDERER=<rendererId>
POJAVEXEC_EGL=<egl so>                # when the renderer supplies EGL
SDL_EGL_LIBRARY=<nativeDir>/<egl so>
```

For `opengles2*`:

```
LIBGL_ES=2
LIBGL_MIPMAP=3
LIBGL_NOERROR=1
LIBGL_NOINTOVLHACK=1
LIBGL_NORMALIZE=1
```

Non-GL4ES / NG-GL4ES also get Mesa:

```
MESA_LOADER_DRIVER_OVERRIDE=zink
MESA_GLSL_CACHE_DIR=<cache>
MESA_GL_VERSION_OVERRIDE=4.6
MESA_GLSL_VERSION_OVERRIDE=460
force_glsl_extensions_warn=true
allow_higher_compat_version=true
allow_glsl_extension_directive_midshader=true
LIB_MESA_NAME=<renderer library>
LIBGL_ES=<2 or 3 from detection>
```

Plus whatever `renderer.getRendererEnv()` returns.

---

## Mojo Launcher (current `v3_openjdk`)

Split across three places; much thinner than the old Pojav `setJavaEnvironment`.

### `JavaRunner` (always when starting the VM)

```
JAVA_HOME=<jre>
HOME=<DIR_GAME_HOME>
TMPDIR=<cache>
LD_LIBRARY_PATH=<jre lib>:<apk natives>:<vm dir>:<jli>
```

### `JREUtils.setGameEnvironment`

```
MOD_ANDROID_RUNTIME=<cache>/app_runtime_mod
POJAV_FFMPEG_PATH=...                 # FFmpeg plugin present
POJAV_BIG_CORE_AFFINITY=1             # preference
ALSOFT_DRIVERS=opensl                 # PREF_ALSOFT_FORCE_OPENSL
```

`custom_env.txt` in the game home can override any key.

### `GameRenderer.setupEnvironment` (renderer-specific)

**GLES (GL4ES / LTW) base**

```
force_glsl_extensions_warn=true
allow_higher_compat_version=true
allow_glsl_extension_directive_midshader=true
LIBGL_NOERROR=1
LIBGL_VGPU_DUMP=1                     # dump shaders
LIBGL_EGL=... / LIBGL_GLES=...        # ANGLE providers only; native GLES writes nothing
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
FD_MESA_DEBUG=sysmem                  # preference
MESA_GL_VERSION_OVERRIDE=3.3          # Adreno 5xx and below
MESA_GLSL_VERSION_OVERRIDE=330
MESA_GLSL_CACHE_DIR=<cache>
TU_DEBUG=sysmem                       # overrideVulkanDriver + PREF_FREEDRENO_SYSMEM
```

Older field logs also had `POJAV_NATIVEDIR`, `PATH`, `MOJO_RENDERER`, `FORCE_VSYNC`, `LIBGL_MIPMAP`, and similar from the pre-split `setJavaEnvironment`. **Current Java source no longer writes those.**

---

## Amethyst Android

### Always (`setJavaEnvironment`)

```
POJAV_NATIVEDIR=<apk natives>
JAVA_HOME=<jre>
HOME=<DIR_GAME_HOME>
TMPDIR=<cache>
LIBGL_MIPMAP=3
LIBGL_NOERROR=1
LIBGL_NOINTOVLHACK=1
LIBGL_NORMALIZE=1
LIBGL_ES=<preferred GL version, then overridden per backend>
FORCE_VSYNC=<PREF_FORCE_VSYNC>
MESA_GLSL_CACHE_DIR=<cache>
force_glsl_extensions_warn=true
allow_higher_compat_version=true
allow_glsl_extension_directive_midshader=true
VTEST_SOCKET_NAME=<cache>/.virgl_test
LD_LIBRARY_PATH=<jre + system/vendor + apk natives + lwjgl natives>
PATH=<jre>/bin:<old PATH>
AWTSTUB_WIDTH / AWTSTUB_HEIGHT
DALVIK_APPLICATION
DALVIK_JAVAVM
```

Conditional:

- `LIBGL_VGPU_DUMP=1`, `POJAV_VSYNC_IN_ZINK=1`, `POJAV_EMUI_ITERATOR_MITIGATE=1`
- `POJAV_FFMPEG_PATH`
- `POJAV_BIG_CORE_AFFINITY=1`
- Adreno without preferring the system driver: `POJAV_LOAD_TURNIP=1`
- `custom_env.txt` applied last

### Per `LOCAL_RENDERER`

```
AMETHYST_RENDERER=<renderer id>
```

| Renderer | Extra env |
| --- | --- |
| `opengles3_ltw` | `LIBGL_ES=3`, `POJAVEXEC_EGL=libltw.so` |
| `opengles_mobileglues` | `MG_DIR_PATH`, `LIBGL_ES=3`, `POJAVEXEC_EGL=libmobileglues.so` |
| `opengles2` + ANGLE | `LIBGL_ES=2`, `LIBGL_GLES=libGLESv2_angle.so`, `LIBGL_EGL=libEGL_angle.so`, `POJAVEXEC_EGL=libEGL_angle.so` |
| `opengles_system_gles` + ANGLE | `POJAVEXEC_EGL=libEGL_angle.so` |
| `opengles3_desktopgl_zink_kopper` | `POJAVEXEC_EGL=libEGL_mesa.so`, optional `FD_DEV_FEATURES` |
| name contains `zink` | `MESA_GL_VERSION_OVERRIDE=4.6COMPAT`, `MESA_GLSL_VERSION_OVERRIDE=460` |
| `useSFPEW` | `SFPEW_EGL=<POJAVEXEC_EGL>` |

Before launching the VM (unless renderer is `opengles_system_gles`):

```
SDL_OPENGL_LIBRARY=<graphicsLib>
SDL_EGL_LIBRARY=<apk natives>/<POJAVEXEC_EGL>
```

---

## Difference cheat sheet

| Variable | FCL | ZL2 | Mojo (current Java) | Amethyst |
| --- | --- | --- | --- | --- |
| `JAVA_HOME` / `HOME` / `TMPDIR` / `LD_LIBRARY_PATH` | yes | yes | yes | yes |
| `PATH` | yes | yes | no | yes |
| `POJAV_NATIVEDIR` | yes | yes | not in current Java (older logs yes) | yes |
| `FCL_NATIVEDIR` | **yes** | no | no | no |
| `DRIVER_PATH` | yes | yes | no | no |
| `MOD_ANDROID_RUNTIME` | yes | yes | yes | no |
| `AWTSTUB_WIDTH/HEIGHT` | no | yes | no | yes |
| `DALVIK_JAVAVM` / `DALVIK_APPLICATION` | no | yes | no | yes |
| `ALSOFT_DRIVERS=opensl` | no | always | optional | no |
| `POJAV_RENDERER` | yes | yes | no (older logs used `MOJO_RENDERER`) | no (uses `AMETHYST_RENDERER`) |
| `AMETHYST_RENDERER` | no | no | no | **yes** |
| `SDL_OPENGL_LIBRARY` / `SDL_EGL_LIBRARY` | no | yes | no | yes |
| `POJAVEXEC_EGL` | often | often | no (EGL via `MojoExec.prepareEgl`) | often |
| `INST_*` mod-loader markers | yes | yes (loader key) | no | no |

---

## Implications for Android detection

1. The **only stable four-way intersection** is `JAVA_HOME`, `HOME`, `TMPDIR`, and `LD_LIBRARY_PATH`. Desktop JVMs can have those too, so they are not Android signals by themselves.
2. **`POJAV_NATIVEDIR` is only used to locate `libSDL3.so`, not as an Android gate.** A PC Linux process can export the same variable. Detection requires `os.version` starting with `Android-`.
3. `POJAV_RENDERER` is **not** shared: FCL/ZL2 use it, Amethyst uses `AMETHYST_RENDERER`, current Mojo Java does not set either.
4. Renderer variables (`LIBGL_*`, `MESA_*`) change with the backend and should not be used for platform detection.
5. Windows/macOS leftover `POJAV_NATIVEDIR` is ignored for detection; only `os.version` starting with `Android-` counts.
