# Android 启动器 JVM 参数对照

对照日期：2026-09-19。

本文记录四款 Android Minecraft: Java Edition 启动器**自己注入**的 JVM 参数，供 gamepad-jni 判断运行环境（判定只认 `os.version` 以 `Android-` 开头；`os.name` 只写诊断日志）以及 Android 上如何找到启动器的 `libSDL3.so`。

- [English version](launcher-jvm-args.en.md)

## 范围

包含：启动器源码里写死或默认追加的 JVM 参数（`-D`、`-X`、`--add-exports`、`-javaagent` 等）。

不包含：Minecraft 客户端参数（`--username`、`--gameDir` 等）。

版本 JSON 里的官方 JVM 参数（`-cp`、`-Dminecraft.launcher.brand` 等）四家都会合并进去，见 [版本 JSON 带来的参数](#版本-json-带来的参数)。

多数默认 `-D` 可被用户自定义 JVM 参数按**同名前缀**覆盖。FCL 的 `addDefault`、ZL2 / Mojo / Amethyst 的 “已存在则跳过” 都是这个逻辑。FCL 还会在默认参数之后再追加版本 JSON，后出现的 `-Dos.version=` 可能覆盖启动器自己的 `Android-*`。

## 源码

| 启动器 | 分支 / 路径 |
| --- | --- |
| FCL | `main`：[DefaultLauncher.java](https://github.com/FCL-Team/FoldCraftLauncher/blob/main/FCL/src/main/java/com/tungsten/fclcore/launch/DefaultLauncher.java)、[CacioJavaArgs.java](https://github.com/FCL-Team/FoldCraftLauncher/blob/main/FCL/src/main/java/com/tungsten/fclcore/launch/CacioJavaArgs.java) |
| Zalith Launcher 2 | `main`：[Launcher.kt](https://github.com/ZalithLauncher/ZalithLauncher2/blob/main/ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/launch/Launcher.kt)、[LaunchArgs.kt](https://github.com/ZalithLauncher/ZalithLauncher2/blob/main/ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/launch/LaunchArgs.kt)、[GameLauncher.kt](https://github.com/ZalithLauncher/ZalithLauncher2/blob/main/ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/launch/GameLauncher.kt) |
| Mojo Launcher | `v3_openjdk`：[JavaRunner.java](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/JavaRunner.java)、[GameRunner.java](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/GameRunner.java) |
| Amethyst Android | `v3_openjdk`：[JREUtils.java](https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java)、[Tools.java](https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/Tools.java) |

`POJAV_NATIVEDIR` 是**环境变量**，不在 JVM 参数列表里。

---

## 四家共同参数

下列参数四家启动器都会自己加上（值的路径因安装位置而异）。

### OS 伪装

这是 Android 上 OpenJDK 的默认内核属性会被覆盖后的结果，不是手机系统属性。

```
-Dos.name=Linux
-Dos.version=Android-<Build.VERSION.RELEASE>
```

例如 `-Dos.version=Android-14`。用户自定义或版本 JSON 里的同名 `-D` 可以把它换掉。

### 路径与进程

```
-Djava.io.tmpdir=<cache>
-Djna.boot.library.path=<natives 或 JNA 目录>
-Duser.home=<游戏主目录>
-Duser.language=<语言>          # Mojo 写死 en
-Duser.timezone=<时区>
-Dext.net.resolvPath=<resolv.conf>
-Djdk.lang.Process.launchMechanism=FORK
```

Android 上不能用默认的 POSIX_SPAWN（需要 `jspawnhelper`），所以四家都改成 `FORK`。

### 模组加载器兜底

```
-Dfml.earlyprogresswindow=false
-Dloader.disable_forked_guis=true
-Dlog4j2.formatMsgNoLookups=true
```

### 内存与 CPU

```
-Xms<RAM>
-Xmx<RAM>
-XX:ActiveProcessorCount=<可用核数>
```

FCL 用小写 `m`（如 `-Xmx2048m`），其余三家多用 `M`。ZL2 / Mojo / Amethyst 会先删掉用户传入的 `-Xms`/`-Xmx`/`-XX:ActiveProcessorCount` 再写入自己的值。

### LWJGL

```
-Dorg.lwjgl.vulkan.libname=libvulkan.so
-Dorg.lwjgl.system.allocator=system
-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared
-Dorg.lwjgl.freetype.libname=<freetype.so>
-Dorg.lwjgl.opengl.libname=<渲染器 so>
```

`opengl.libname` 的值不同：

| 启动器 | 典型值 |
| --- | --- |
| FCL | 所选渲染器（占位 `${gl_lib_name}`） |
| ZL2 | 所选渲染器 so |
| Mojo | 固定 `libGLMojo.so` |
| Amethyst | 当前 graphicsLib |

### Cacio（AWT 模拟）

四家都用 Caciocavallo 给无头 Android JVM 提供 AWT：

```
-Djava.awt.headless=false
-Dcacio.managed.screensize=<宽>x<高>
-Dcacio.font.fontmanager=sun.awt.X11FontManager
-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler
-Dswing.defaultlaf=<LookAndFeel>
-Dawt.toolkit=<CTCToolkit>
-Djava.awt.graphicsenv=<CTCGraphicsEnvironment>
```

- Java 8：`net.java.openjdk.cacio.ctc.*`，以及 `-Xbootclasspath/p:<cacio jars>`
- Java 9+：`com.github.caciocavallosilano.cacio.ctc.*`，`-javaagent:<cacio-agent.jar>`，`-Xbootclasspath/a:<cacio jars>`，以及下面这组模块参数：

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

FCL / ZL2 / Amethyst 额外有 `--add-opens=java.base/java.net=ALL-UNNAMED`。Mojo 的 `JavaRunner.getCacioJavaArgs` 没有这一条。

LookAndFeel：FCL 与 ZL2 默认 **Nimbus**；Mojo 与 Amethyst 默认 **Metal**。

---

## 版本 JSON 带来的参数

Minecraft 官方启动器模板（及 Forge 等加载器）会再追加，不是这四家独有的：

```
-Djava.library.path=${natives_directory}
-Dminecraft.launcher.brand=${launcher_name}
-Dminecraft.launcher.version=${launcher_version}
-cp ${classpath}
```

较新版本还常见：

```
-Djna.tmpdir=...
-Dorg.lwjgl.system.SharedLibraryExtractPath=...
-Dio.netty.native.workdir=...
```

FCL 无版本 JSON 时的兜底见 `Arguments.DEFAULT_JVM_ARGUMENTS`。Mojo 会跳过版本 JSON 里与自己冲突的 `java.library.path` / `jna.tmpdir` / `SharedLibraryExtractPath` / `io.netty.native.workdir`。

---

## FCL（Fold Craft Launcher）

### 内存 / VM

```
-Xmx<max>m
-Xms<min>m
-XX:ActiveProcessorCount=<核数>
-Xss1m                    # 仅 32 位设备
```

用户 Java 参数含 `noXmx` 时不加 `-Xmx`。

### 编码 / Log4j

```
-Dfile.encoding=<系统编码>
-Dsun.stdout.encoding=... / -Dsun.stderr.encoding=...   # Java < 19
-Dstdout.encoding=... / -Dstderr.encoding=...           # Java >= 19
-Djava.rmi.server.useCodebaseOnly=true
-Dcom.sun.jndi.rmi.object.trustURLCodebase=false
-Dcom.sun.jndi.cosnaming.object.trustURLCodebase=false
-Dlog4j2.formatMsgNoLookups=true
-Dlog4j.configurationFile=<version>/log4j2.xml          # 游戏版本 >= 1.7
```

### OS / 路径

```
-Dos.name=Linux
-Dos.version=Android-<RELEASE>
-Djava.io.tmpdir=<cache>
-Dext.net.resolvPath=<java>/resolv.conf
-Duser.home=<游戏目录>
-Duser.language=...
-Duser.country=...
-Duser.timezone=...
-Djna.boot.library.path=<jna 或 APK native dir>
-Dcpu.name=<SoC>
-Dminecraft.client.jar=<client.jar>
```

FCL **不**注入 `-Djava.home`、`-Dpojav.path.minecraft`、`-Dpojav.path.private.account`、`-Dglfwstub.windowWidth/Height`。

### Forge / 加载器

```
-Dfml.ignoreInvalidMinecraftCertificates=true
-Dfml.ignorePatchDiscrepancies=true
-Dfml.earlyprogresswindow=false
-Dloader.disable_forked_guis=true
-Djdk.lang.Process.launchMechanism=FORK
-Dsodium.checks.issue2561=false
-Dsort.patch=true          # 仅 Forge 1.7.2
```

Java ≠ 8：

```
--add-exports
<主类包>/<主类包>=ALL-UNNAMED
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

### 条件项

- 开启 MioLibPatcher：`-javaagent:<LIB_PATCHER>`，以及 `MioLibPatcherManager.getJvmOptions()`
- Native 插件：`NativeLibPlugin.getJVMEnv()` 再追加 `-D...`

---

## Zalith Launcher 2

### 可被用户覆盖的默认 `-D`

```
-Djava.home=<runtime>
-Djava.io.tmpdir=<cache>
-Djna.boot.library.path=<native lib 或版本 JNA 目录>
-Duser.home=<userHome>
-Duser.language=...                    # useLocalLanguage 时
-Duser.country=...                     # useLocalLanguage 时
-Duser.timezone=...
-Dos.name=Linux
-Dos.version=Android-<RELEASE>
-Dpojav.path.minecraft=...
-Dpojav.path.private.account=...
-Dorg.lwjgl.vulkan.libname=libvulkan.so
-Dorg.lwjgl.librarypath=<per-version lwjgl natives>
-Dglfwstub.windowWidth=<宽>
-Dglfwstub.windowHeight=<高>
-Dglfwstub.initEgl=false
-Dext.net.resolvPath=...
-Dlog4j2.formatMsgNoLookups=true
-Djava.rmi.server.useCodebaseOnly=true
-Dcom.sun.jndi.rmi.object.trustURLCodebase=false
-Dcom.sun.jndi.cosnaming.object.trustURLCodebase=false
-Dnet.minecraft.clientmodname=<启动器名>
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

### 强制追加（会先清掉用户同名前缀）

```
-javaagent:<MioLibPatcher.jar>
-Xms<RAM>M
-Xmx<RAM>M
-Dorg.lwjgl.openal.libname=<apk>/libopenal.so
-Dorg.lwjgl.freetype.libname=<lwjgl natives>/libfreetype.so
-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared
-Dorg.lwjgl.system.allocator=system
-XX:ActiveProcessorCount=<核数>
-Dorg.lwjgl.opengl.libname=<renderer so>    # 有有效渲染器时
```

### LaunchArgs 再加

```
-Dlog4j.configurationFile=...
-Dminecraft.client.jar=...
-Dminecraft.launcher.brand=...
-Dminecraft.launcher.version=...
```

- Java > 8：`--add-exports <主类包>/<主类包>=ALL-UNNAMED`
- 外置登录：`-javaagent:authlib-injector.jar=<url>` + `-Dauthlibinjector.side=client`，或 nide8 agent
- Forge 1.7.2：`-Dsort.patch=true`
- Native 插件：`NativePluginManager.getJVMEnv()`

Cacio 与 FCL 同类，LookAndFeel 为 Nimbus。

---

## Mojo Launcher

### 强制

```
-Xms<RAM>M
-Xmx<RAM>M
-XX:ActiveProcessorCount=<核数>
-Djava.class.path=<classpath>     # 不是 -cp
-Xrs                              # 仅 x86 + Android 6 (API 23)
```

会先删除用户参数里的 `-Xms`/`-Xmx`/`-Xint`/`-d32`/`-d64`、TransparentHugePages 等。

### 可覆盖默认 `-D`

```
-Djava.home=<runtime>
-Djava.io.tmpdir=<cache>
-Djna.boot.library.path=<NATIVE_LIB_DIR>
-Duser.home=<DIR_GAME_HOME>
-Duser.language=en                 # 写死
-Duser.country=US                  # 写死
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

四家里**只有 Mojo 默认**带 `-Dorg.lwjgl.sdl.libname=.../libSDL3.so`。

### GameRunner 再加

```
-Dlog4j.configurationFile=...                         # 版本有 logging 时
-Djava.library.path=<version natives>:<apk natives>   # 版本 natives 目录存在时
-Djna.boot.library.path=<version natives>             # 同上
-Dorg.lwjgl.librarypath=<version natives>             # 同上
-Dorg.lwjgl.system.SharedLibraryExtractPath=<cache>/lwjgl_native/<version>
-Dorg.lwjgl.opengl.libname=libGLMojo.so
-Dorg.lwjgl.freetype.libname=<apk>/libfreetype.so
-javaagent:<authlib-injector>=<url>                   # 外置登录时
```

**没有** `glfwstub.windowWidth/Height`（分辨率走 native GLFW）。**没有** `fml.ignore*`、`file.encoding`、`cpu.name`、MioLibPatcher。

Cacio LookAndFeel 为 Metal。

---

## Amethyst Android

### 可覆盖默认 `-D`

```
-Djava.home=<runtime>
-Djava.io.tmpdir=<cache>
-Djna.boot.library.path=<NATIVE_LIB_DIR>
-Duser.home=<DIR_GAME_HOME>
-Duser.language=<系统属性>          # 无 user.country
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
-javaagent:arc_dns_injector.jar=23.95.137.176    # 仅 PREF_ARC_CAPES
```

### launchJavaVM 强制追加

```
-Xms<RAM>M
-Xmx<RAM>M
-Dorg.lwjgl.opengl.libname=<graphicsLib>         # LOCAL_RENDERER 非空时
-Dorg.lwjgl.freetype.libname=<lwjgl natives>/libfreetype.so
-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared
-Dorg.lwjgl.system.allocator=system
-XX:ActiveProcessorCount=<核数>
-javaagent:<MioLibPatcher.jar>
-Dmiolibpatcher.alc10=true
```

### Tools.launchMinecraft 再加

```
-Dlog4j.configurationFile=...                    # 版本有 logging 时
-Djava.library.path=<lwjgl natives>:<apk natives>:...
-Djna.boot.library.path=<version natives>        # 该目录存在时再写一次
-Dorg.lwjgl.librarypath=<lwjgl natives>
-Dfml.ignoreInvalidMinecraftCertificates=true
-Dimgui.library.name=imgui-java
-DZstdNativePath=<apk>/libzstd-jni-...so
-Dsodium.checks.issue2561=false                  # 检测到 Sodium 系模组时
```

Cacio LookAndFeel 为 Metal。

---

## 差异速查

| 参数 | FCL | ZL2 | Mojo | Amethyst |
| --- | --- | --- | --- | --- |
| `-Dos.name=Linux` | 有 | 有 | 有 | 有 |
| `-Dos.version=Android-*` | 有 | 有 | 有 | 有 |
| `-Djava.home` | 无 | 有 | 有 | 有 |
| `-Dpojav.path.minecraft` / `private.account` | 无 | 有 | 有 | 有 |
| `-Dglfwstub.windowWidth/Height` | 无 | 有 | 无 | 有 |
| `-Dglfwstub.initEgl` | 有 | 有 | 无 | 有 |
| `-Dorg.lwjgl.sdl.libname=.../libSDL3.so` | 无 | 无 | **有** | 无 |
| `-Dcpu.name` | 有 | 有 | 无 | 无 |
| `-Duser.country` | 有 | 有 | 写死 `US` | 无 |
| MioLibPatcher `-javaagent` | 可开关 | 强制 | 无 | 强制 |
| `-Dfml.ignoreInvalidMinecraftCertificates` | 有 | 有 | 无（启动器默认） | 有 |
| `-Dfile.encoding` | 有 | 有（UTF-8） | 无 | 无 |
| Log4j RCE 的 rmi/jndi `-D` | 有 | 有 | 无 | 无 |

---

## 对检测器的含义

1. 四家**默认**都会带 `-Dos.name=Linux` 和 `-Dos.version=Android-<RELEASE>`。本库判定只认后者：`os.version` 以 `Android-` 开头（忽略大小写）。`os.name` 只写诊断日志。
2. 不能当成不可推翻的事实：用户 JVM 参数、FCL 后追加的版本 JSON（现场出现过后面的 `-Dos.version=5.0`）都能改掉。
3. 真机默认的 `os.version` 是内核版本，**不会**以 `Android-` 开头；`Android-` 前缀是启动器覆盖出来的。
4. 只有 Mojo 默认通过 JVM 参数指向 `libSDL3.so`。FCL / ZL2 / Amethyst 的 SDL3 在 APK native 目录 / `POJAV_NATIVEDIR`，不靠这条 `-D`。
5. `POJAV_NATIVEDIR`、Win/Mac 残留环境变量、`os.name` 都不是判定条件。
