# Android launcher JVM arguments

Survey date: 2026-09-19.

JVM flags that four Android Minecraft: Java Edition launchers inject themselves. Used by gamepad-jni to detect Android (`os.version` starting with `Android-`; `os.name` is diagnostic) and to locate the launcher-provided `libSDL3.so`.

- [中文版](launcher-jvm-args.zh.md)

## Scope

Included: JVM arguments the launcher hard-codes or appends by default (`-D`, `-X`, `--add-exports`, `-javaagent`, and similar).

Excluded: Minecraft client arguments (`--username`, `--gameDir`, and similar).

Official JVM arguments from the version JSON (`-cp`, `-Dminecraft.launcher.brand`, and similar) are merged by all four launchers. See [Version JSON arguments](#version-json-arguments).

Most default `-D` flags are overridable: if the user already passed the same prefix, the launcher skips its own value (`addDefault` on FCL; “skip if present” on ZL2 / Mojo / Amethyst). FCL also appends version-JSON JVM args *after* its defaults, so a later `-Dos.version=` can replace the launcher’s `Android-*` value.

## Sources

| Launcher | Branch / paths |
| --- | --- |
| FCL | `main`: [DefaultLauncher.java](https://github.com/FCL-Team/FoldCraftLauncher/blob/main/FCL/src/main/java/com/tungsten/fclcore/launch/DefaultLauncher.java), [CacioJavaArgs.java](https://github.com/FCL-Team/FoldCraftLauncher/blob/main/FCL/src/main/java/com/tungsten/fclcore/launch/CacioJavaArgs.java) |
| Zalith Launcher 2 | `main`: [Launcher.kt](https://github.com/ZalithLauncher/ZalithLauncher2/blob/main/ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/launch/Launcher.kt), [LaunchArgs.kt](https://github.com/ZalithLauncher/ZalithLauncher2/blob/main/ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/launch/LaunchArgs.kt), [GameLauncher.kt](https://github.com/ZalithLauncher/ZalithLauncher2/blob/main/ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/launch/GameLauncher.kt) |
| Mojo Launcher | `v3_openjdk`: [JavaRunner.java](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/JavaRunner.java), [GameRunner.java](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/GameRunner.java) |
| Amethyst Android | `v3_openjdk`: [JREUtils.java](https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java), [Tools.java](https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/Tools.java) |

`POJAV_NATIVEDIR` is an **environment variable**, not a JVM argument.

---

## Arguments common to all four

These are injected by every launcher (paths differ by install location).

### OS disguise

These override the OpenJDK defaults on Android. They are not the phone’s real system properties.

```
-Dos.name=Linux
-Dos.version=Android-<Build.VERSION.RELEASE>
```

Example: `-Dos.version=Android-14`. A user JVM argument or a later version-JSON `-D` with the same name can replace them.

### Paths and process launch

```
-Djava.io.tmpdir=<cache>
-Djna.boot.library.path=<natives or JNA dir>
-Duser.home=<game home>
-Duser.language=<locale>          # Mojo hard-codes en
-Duser.timezone=<tz>
-Dext.net.resolvPath=<resolv.conf>
-Djdk.lang.Process.launchMechanism=FORK
```

POSIX_SPAWN (the OpenJDK default) needs `jspawnhelper`, which does not work on Android, so all four switch to `FORK`.

### Mod-loader workarounds

```
-Dfml.earlyprogresswindow=false
-Dloader.disable_forked_guis=true
-Dlog4j2.formatMsgNoLookups=true
```

### Memory and CPU

```
-Xms<RAM>
-Xmx<RAM>
-XX:ActiveProcessorCount=<available processors>
```

FCL uses a lowercase `m` (for example `-Xmx2048m`); the other three usually use `M`. ZL2 / Mojo / Amethyst strip user `-Xms` / `-Xmx` / `-XX:ActiveProcessorCount` before writing their own values.

### LWJGL

```
-Dorg.lwjgl.vulkan.libname=libvulkan.so
-Dorg.lwjgl.system.allocator=system
-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared
-Dorg.lwjgl.freetype.libname=<freetype.so>
-Dorg.lwjgl.opengl.libname=<renderer so>
```

`opengl.libname` values:

| Launcher | Typical value |
| --- | --- |
| FCL | Selected renderer (placeholder `${gl_lib_name}`) |
| ZL2 | Selected renderer `.so` |
| Mojo | Always `libGLMojo.so` |
| Amethyst | Current graphicsLib |

### Cacio (AWT stub)

All four use Caciocavallo so a headless Android JVM can run AWT:

```
-Djava.awt.headless=false
-Dcacio.managed.screensize=<width>x<height>
-Dcacio.font.fontmanager=sun.awt.X11FontManager
-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler
-Dswing.defaultlaf=<LookAndFeel>
-Dawt.toolkit=<CTCToolkit>
-Djava.awt.graphicsenv=<CTCGraphicsEnvironment>
```

- Java 8: `net.java.openjdk.cacio.ctc.*` and `-Xbootclasspath/p:<cacio jars>`
- Java 9+: `com.github.caciocavallosilano.cacio.ctc.*`, `-javaagent:<cacio-agent.jar>`, `-Xbootclasspath/a:<cacio jars>`, plus:

```
--add-exports=java.desktop/java.awt=ALL-UNNAMED
--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED
--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED
--add-exports=java.desktop/sun.java2d=ALL-UNNAMED
--add-exports=java.desktop/java.awt.dnd.peer=ALL-UNNAMED
--add-exports=java.desktop/sun.awt=ALL-UNNAMED
--add-exports=java.desktop/sun.awt.event=ALL-UNNAMED
--add-exports=java.desktop/sun.awt.datatransfer=ALL-UNNAMED
--add-exports=java.desktop/sun.font=ALL-UNNAMED
--add-exports=java.base/sun.security.action=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.desktop/java.awt=ALL-UNNAMED
--add-opens=java.desktop/sun.font=ALL-UNNAMED
--add-opens=java.desktop/sun.java2d=ALL-UNNAMED
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED
```

FCL / ZL2 / Amethyst also add `--add-opens=java.base/java.net=ALL-UNNAMED`. Mojo’s `JavaRunner.getCacioJavaArgs` does not.

LookAndFeel: FCL and ZL2 default to **Nimbus**; Mojo and Amethyst default to **Metal**.

---

## Version JSON arguments

These come from the official Minecraft launcher template (and loaders such as Forge). They are not unique to these four apps:

```
-Djava.library.path=${natives_directory}
-Dminecraft.launcher.brand=${launcher_name}
-Dminecraft.launcher.version=${launcher_version}
-cp ${classpath}
```

Newer versions often also include:

```
-Djna.tmpdir=...
-Dorg.lwjgl.system.SharedLibraryExtractPath=...
-Dio.netty.native.workdir=...
```

FCL falls back to `Arguments.DEFAULT_JVM_ARGUMENTS` when the version JSON has no JVM list. Mojo skips version-JSON flags that collide with its own `java.library.path` / `jna.tmpdir` / `SharedLibraryExtractPath` / `io.netty.native.workdir`.

---

## FCL (Fold Craft Launcher)

### Memory / VM

```
-Xmx<max>m
-Xms<min>m
-XX:ActiveProcessorCount=<n>
-Xss1m                    # 32-bit devices only
```

`-Xmx` is omitted when user Java arguments contain `noXmx`.

### Encoding / Log4j

```
-Dfile.encoding=<native charset>
-Dsun.stdout.encoding=... / -Dsun.stderr.encoding=...   # Java < 19
-Dstdout.encoding=... / -Dstderr.encoding=...           # Java >= 19
-Djava.rmi.server.useCodebaseOnly=true
-Dcom.sun.jndi.rmi.object.trustURLCodebase=false
-Dcom.sun.jndi.cosnaming.object.trustURLCodebase=false
-Dlog4j2.formatMsgNoLookups=true
-Dlog4j.configurationFile=<version>/log4j2.xml          # game version >= 1.7
```

### OS / paths

```
-Dos.name=Linux
-Dos.version=Android-<RELEASE>
-Djava.io.tmpdir=<cache>
-Dext.net.resolvPath=<java>/resolv.conf
-Duser.home=<game dir>
-Duser.language=...
-Duser.country=...
-Duser.timezone=...
-Djna.boot.library.path=<jna or APK native dir>
-Dcpu.name=<SoC>
-Dminecraft.client.jar=<client.jar>
```

FCL does **not** inject `-Djava.home`, `-Dpojav.path.minecraft`, `-Dpojav.path.private.account`, or `-Dglfwstub.windowWidth/Height`.

### Forge / loaders

```
-Dfml.ignoreInvalidMinecraftCertificates=true
-Dfml.ignorePatchDiscrepancies=true
-Dfml.earlyprogresswindow=false
-Dloader.disable_forked_guis=true
-Djdk.lang.Process.launchMechanism=FORK
-Dsodium.checks.issue2561=false
-Dsort.patch=true          # Forge 1.7.2 only
```

When Java ≠ 8:

```
--add-exports
<main-class-package>/<main-class-package>=ALL-UNNAMED
```

### LWJGL

```
-Dorg.lwjgl.opengl.libname=${gl_lib_name}
-Dorg.lwjgl.egl.libname=${egl_lib_name}
-Dorg.lwjgl.openal.libname=<apk>/libopenal.so
-Dorg.lwjgl.freetype.libname=<lwjgl natives>/libfreetype.so
-Dorg.lwjgl.librarypath=<lwjgl natives>
-Dorg.lwjgl.system.allocator=system
-Dorg.lwjgl.vulkan.libname=libvulkan.so
-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared
-Dglfwstub.initEgl=false
```

### Conditional

- MioLibPatcher enabled: `-javaagent:<LIB_PATCHER>` plus `MioLibPatcherManager.getJvmOptions()`
- Native plugins: extra `-D...` from `NativeLibPlugin.getJVMEnv()`

---

## Zalith Launcher 2

### Overridable default `-D` flags

```
-Djava.home=<runtime>
-Djava.io.tmpdir=<cache>
-Djna.boot.library.path=<native lib or versioned JNA dir>
-Duser.home=<userHome>
-Duser.language=...                    # when useLocalLanguage
-Duser.country=...                     # when useLocalLanguage
-Duser.timezone=...
-Dos.name=Linux
-Dos.version=Android-<RELEASE>
-Dpojav.path.minecraft=...
-Dpojav.path.private.account=...
-Dorg.lwjgl.vulkan.libname=libvulkan.so
-Dorg.lwjgl.librarypath=<per-version lwjgl natives>
-Dglfwstub.windowWidth=<w>
-Dglfwstub.windowHeight=<h>
-Dglfwstub.initEgl=false
-Dext.net.resolvPath=...
-Dlog4j2.formatMsgNoLookups=true
-Djava.rmi.server.useCodebaseOnly=true
-Dcom.sun.jndi.rmi.object.trustURLCodebase=false
-Dcom.sun.jndi.cosnaming.object.trustURLCodebase=false
-Dnet.minecraft.clientmodname=<launcher name>
-Dfml.earlyprogresswindow=false
-Dfml.ignoreInvalidMinecraftCertificates=true
-Dfml.ignorePatchDiscrepancies=true
-Dloader.disable_forked_guis=true
-Djdk.lang.Process.launchMechanism=FORK
-Dsodium.checks.issue2561=false
-Dfile.encoding=UTF-8
-Dsun.stdout.encoding=UTF-8
-Dsun.stderr.encoding=UTF-8
-Dcpu.name=<SoC>
```

### Forced (matching user prefixes are stripped first)

```
-javaagent:<MioLibPatcher.jar>
-Xms<RAM>M
-Xmx<RAM>M
-Dorg.lwjgl.openal.libname=<apk>/libopenal.so
-Dorg.lwjgl.freetype.libname=<lwjgl natives>/libfreetype.so
-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared
-Dorg.lwjgl.system.allocator=system
-XX:ActiveProcessorCount=<n>
-Dorg.lwjgl.opengl.libname=<renderer so>    # when a renderer is selected
```

### Added in LaunchArgs

```
-Dlog4j.configurationFile=...
-Dminecraft.client.jar=...
-Dminecraft.launcher.brand=...
-Dminecraft.launcher.version=...
```

- Java > 8: `--add-exports <main-class-package>/<main-class-package>=ALL-UNNAMED`
- Third-party auth: `-javaagent:authlib-injector.jar=<url>` plus `-Dauthlibinjector.side=client`, or the nide8 agent
- Forge 1.7.2: `-Dsort.patch=true`
- Native plugins: `NativePluginManager.getJVMEnv()`

Cacio matches FCL; LookAndFeel is Nimbus.

---

## Mojo Launcher

### Forced

```
-Xms<RAM>M
-Xmx<RAM>M
-XX:ActiveProcessorCount=<n>
-Djava.class.path=<classpath>     # not -cp
-Xrs                              # x86 + Android 6 (API 23) only
```

User `-Xms` / `-Xmx` / `-Xint` / `-d32` / `-d64` / TransparentHugePages (and similar) are stripped first.

### Overridable default `-D` flags

```
-Djava.home=<runtime>
-Djava.io.tmpdir=<cache>
-Djna.boot.library.path=<NATIVE_LIB_DIR>
-Duser.home=<DIR_GAME_HOME>
-Duser.language=en                 # hard-coded
-Duser.country=US                  # hard-coded
-Dos.name=Linux
-Dos.version=Android-<RELEASE>
-Dpojav.path.minecraft=...
-Dpojav.path.private.account=...
-Duser.timezone=...
-Dorg.lwjgl.vulkan.libname=libvulkan.so
-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared
-Dorg.lwjgl.sdl.libname=<apk>/libSDL3.so
-Dorg.lwjgl.system.allocator=system
-Dext.net.resolvPath=...
-Dlog4j2.formatMsgNoLookups=true
-Dfml.earlyprogresswindow=false
-Dloader.disable_forked_guis=true
-Djdk.lang.Process.launchMechanism=FORK
```

**Only Mojo** among the four defaults `-Dorg.lwjgl.sdl.libname=.../libSDL3.so`.

### Added in GameRunner

```
-Dlog4j.configurationFile=...                         # when the version has logging
-Djava.library.path=<version natives>:<apk natives>   # if version natives dir exists
-Djna.boot.library.path=<version natives>             # same
-Dorg.lwjgl.librarypath=<version natives>             # same
-Dorg.lwjgl.system.SharedLibraryExtractPath=<cache>/lwjgl_native/<version>
-Dorg.lwjgl.opengl.libname=libGLMojo.so
-Dorg.lwjgl.freetype.libname=<apk>/libfreetype.so
-javaagent:<authlib-injector>=<url>                   # third-party auth
```

**No** `glfwstub.windowWidth/Height` (resolution is configured in native GLFW). **No** launcher-default `fml.ignore*`, `file.encoding`, `cpu.name`, or MioLibPatcher.

Cacio LookAndFeel is Metal.

---

## Amethyst Android

### Overridable default `-D` flags

```
-Djava.home=<runtime>
-Djava.io.tmpdir=<cache>
-Djna.boot.library.path=<NATIVE_LIB_DIR>
-Duser.home=<DIR_GAME_HOME>
-Duser.language=<system property>   # no user.country
-Dos.name=Linux
-Dos.version=Android-<RELEASE>
-Dpojav.path.minecraft=...
-Dpojav.path.private.account=...
-Duser.timezone=...
-Dorg.lwjgl.vulkan.libname=libvulkan.so
-Dglfwstub.windowWidth=...
-Dglfwstub.windowHeight=...
-Dglfwstub.initEgl=false
-Dext.net.resolvPath=...
-Dlog4j2.formatMsgNoLookups=true
-Dnet.minecraft.clientmodname=<APP_NAME>
-Dfml.earlyprogresswindow=false
-Dloader.disable_forked_guis=true
-Djdk.lang.Process.launchMechanism=FORK
-javaagent:arc_dns_injector.jar=23.95.137.176    # PREF_ARC_CAPES only
```

### Forced in launchJavaVM

```
-Xms<RAM>M
-Xmx<RAM>M
-Dorg.lwjgl.opengl.libname=<graphicsLib>         # when LOCAL_RENDERER is set
-Dorg.lwjgl.freetype.libname=<lwjgl natives>/libfreetype.so
-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared
-Dorg.lwjgl.system.allocator=system
-XX:ActiveProcessorCount=<n>
-javaagent:<MioLibPatcher.jar>
-Dmiolibpatcher.alc10=true
```

### Added in Tools.launchMinecraft

```
-Dlog4j.configurationFile=...                    # when the version has logging
-Djava.library.path=<lwjgl natives>:<apk natives>:...
-Djna.boot.library.path=<version natives>        # written again if that dir exists
-Dorg.lwjgl.librarypath=<lwjgl natives>
-Dfml.ignoreInvalidMinecraftCertificates=true
-Dimgui.library.name=imgui-java
-DZstdNativePath=<apk>/libzstd-jni-...so
-Dsodium.checks.issue2561=false                  # when a Sodium-family mod is detected
```

Cacio LookAndFeel is Metal.

---

## Difference cheat sheet

| Flag | FCL | ZL2 | Mojo | Amethyst |
| --- | --- | --- | --- | --- |
| `-Dos.name=Linux` | yes | yes | yes | yes |
| `-Dos.version=Android-*` | yes | yes | yes | yes |
| `-Djava.home` | no | yes | yes | yes |
| `-Dpojav.path.minecraft` / `private.account` | no | yes | yes | yes |
| `-Dglfwstub.windowWidth/Height` | no | yes | no | yes |
| `-Dglfwstub.initEgl` | yes | yes | no | yes |
| `-Dorg.lwjgl.sdl.libname=.../libSDL3.so` | no | no | **yes** | no |
| `-Dcpu.name` | yes | yes | no | no |
| `-Duser.country` | yes | yes | hard-coded `US` | no |
| MioLibPatcher `-javaagent` | optional | always | no | always |
| `-Dfml.ignoreInvalidMinecraftCertificates` | yes | yes | no (launcher default) | yes |
| `-Dfile.encoding` | yes | yes (UTF-8) | no | no |
| Log4j RCE rmi/jndi `-D` flags | yes | yes | no | no |

---

## Implications for Android detection

1. All four launchers **default** to `-Dos.name=Linux` and `-Dos.version=Android-<RELEASE>`. This library gates only on the latter: `os.version` starting with `Android-` (case-insensitive). `os.name` is diagnostic.
2. It is not guaranteed: user JVM args, or FCL appending version-JSON flags later (field logs have shown a trailing `-Dos.version=5.0`), can replace it.
3. On a real phone, the JVM’s own `os.version` is the kernel version and does **not** start with `Android-`. The `Android-` prefix is a launcher override.
4. Only Mojo points at `libSDL3.so` through a JVM property by default. FCL / ZL2 / Amethyst keep SDL3 in the APK native dir / `POJAV_NATIVEDIR`, not via that `-D`.
5. `POJAV_NATIVEDIR`, leftover Win/Mac env vars, and `os.name` are not detection gates.
