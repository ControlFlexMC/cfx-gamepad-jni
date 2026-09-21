# gamepad-jni

A lightweight Java JNI wrapper exposing the SDL3 Gamepad API, built for the **[Control Flex](https://www.curseforge.com/minecraft/mc-mods/control-flex)** Minecraft mod.

Control Flex uses this library to provide cross-platform gamepad support (Xbox, PlayStation, Switch Pro, and other controllers) with rumble, LED, touchpad, and motion sensor capabilities — all through SDL3's unified gamepad API.

## Supported Platforms

| Platform  | Architectures                  |
| --------- | ------------------------------ |
| macOS     | `aarch64` (Apple Silicon), `x86_64` (Intel) |
| Windows   | `x86_64`, `aarch64`            |
| Linux     | `x86_64`, `aarch64`            |
| Android   | `arm64-v8a`, `armeabi-v7a`, `x86_64` (see [Android](#android)) |

## Native Libraries

This project produces a JNI library per platform. The caller chooses which SDL3
to bind to when calling `GamepadManager.initialize(Sdl3Source)`:

| `Sdl3Source` | Where SDL3 comes from | When to use |
| --- | --- | --- |
| `HOST` | Minecraft / LWJGL SDL3 already in this JVM | Desktop Minecraft 26.3+ |
| `BUNDLED` | `prebuilt/sdl/lwjgl-sdl-3.4.3/<platform>/` inside this JAR | Desktop when the host has no SDL3 |

The two desktop choices are exclusive: `HOST` fails if that copy is not found;
it does not fall back to `BUNDLED`. On Android the argument is ignored and the
launcher APK's `libSDL3.so` is always used.

| Library | Source | Description |
| --- | --- | --- |
| `libgamepadjni.so` / `.dylib` / `.dll` | `src/main/c/` (CMake) | JNI bridge to the SDL3 gamepad API |
| `libSDL3.so` / `libSDL3.0.dylib` / `SDL3.dll` | LWJGL 3.4.3 (Minecraft 26.3) | Bundled SDL3 used only with `Sdl3Source.BUNDLED` |

How the JNI library reaches SDL3 differs per platform. Each mechanism binds to
whichever copy is already mapped into the process:

| Platform | Mechanism | Consequence when rebuilding |
| -------- | --------- | --------------------------- |
| macOS | `-undefined dynamic_lookup` | Resolved from the process image; nothing to link. |
| Linux | `DT_NEEDED libSDL3.so.0`, matched **by SONAME** | The SDL3 you link against must keep SONAME `libSDL3.so.0`, or the host copy stops matching. `RUNPATH` is `$ORIGIN` so the bundled copy still resolves. |
| Windows | Import by module name `SDL3.dll` | Resolved from the loaded-module list; no path or soname involved. |

### Library file names by platform

| Platform | JNI Library            | SDL3 Library         |
| -------- | ---------------------- | -------------------- |
| macOS    | `libgamepadjni.dylib`  | `libSDL3.0.dylib`    |
| Windows  | `gamepadjni.dll`       | `SDL3.dll`           |
| Linux    | `libgamepadjni.so`     | `libSDL3.so`       |

## Adding Platform Support for Control Flex

Control Flex loads native libraries from the JAR at runtime. To add or update a platform's native libraries, place the compiled `.so`/`.dylib`/`.dll` files into the corresponding `prebuilt/` directories, then rebuild the JAR.

### Directory structure inside the JAR

```
gamepad-jni-<version>.jar
└── native/
    ├── android-arm64-v8a/          # JNI bridge only - SDL3 comes from the launcher
    │   └── libgamepadjni.so
    ├── android-armeabi-v7a/
    │   └── libgamepadjni.so
    ├── android-x86_64/
    │   └── libgamepadjni.so
    ├── darwin-aarch64/
    │   ├── libSDL3.0.dylib
    │   └── libgamepadjni.dylib
    ├── darwin-x86_64/
    │   ├── libSDL3.0.dylib
    │   └── libgamepadjni.dylib
    ├── linux-aarch64/
    │   ├── libSDL3.so
    │   └── libgamepadjni.so
    ├── linux-x86_64/
    │   ├── libSDL3.so
    │   └── libgamepadjni.so
    ├── windows-aarch64/
    │   ├── SDL3.dll
    │   └── gamepadjni.dll
    └── windows-x86_64/
        ├── SDL3.dll
        └── gamepadjni.dll
```

`verifyJarContents` (part of `check`) asserts these native directories are present
and that no Android `libSDL3.so` is ever packaged.

### Android

Android launchers (FCL, ZalithLauncher2, Mojo `v3_openjdk`, Amethyst) run Minecraft
in a second JVM inside the same app process, and they ship `libSDL3.so` in the APK
**together with SDL's Java glue** (`org.libsdl.app.*`), which they install into the
ART runtime. That glue is what SDL's Android joystick driver talks to, so a copy of
SDL3 we built ourselves could never enumerate a gamepad.
Detection is `os.version` starting with `Android-` (case-insensitive). `os.name` is logged only.
We therefore:

- ignore `Sdl3Source` and always load the launcher's SDL3 —
  `$POJAV_NATIVEDIR/libSDL3.so`, falling back to `System.loadLibrary("SDL3")`;
- ship only our own `libgamepadjni.so` under `native/android-<abi>/`;
- call `SDL_InitSubSystem` rather than `SDL_Init`, because the launchers' bytehook
  patches that specific symbol and only then installs the glue;
- release with `SDL_QuitSubSystem` rather than `SDL_Quit`, so we never tear down
  SDL subsystems the launcher (or Minecraft 26.3+, which uses SDL itself) owns.

**`CFX_LIB_PATH` on Android only overrides `libgamepadjni.so`.** It must not point
at another `libSDL3.so`: loading SDL3 from a different path yields a second
instance with no launcher glue, which can never see a gamepad.

Build the Android natives with `prebuilt/build-jni-android.sh` (needs the Android
NDK); SDL3 at link time is fetched from the official `SDL3-devel-3.4.14-android`
release into `prebuilt/link-only/`, which is gitignored and never packaged. The
resulting `.so` files are committed, because JitPack only zips what is already in
`prebuilt/`.

### Step 1: Build a local trimmed SDL3 (optional)

JNI linking and JAR packaging use `prebuilt/sdl/lwjgl-sdl-3.4.3/`. The scripts
below write a separate input-only SDL3 under `prebuilt/sdl/trimmed/` for local
experimentation. They do **not** overwrite `prebuilt/sdl/include/`.

```bash
cd prebuilt
chmod +x build-sdl3.sh
./build-sdl3.sh
```

This produces per-platform SDL3 binaries under `prebuilt/sdl/trimmed/<platform>/`, stripped down to only input subsystems (keyboard, mouse, joystick/gamepad, sensor). No audio, GPU, render, camera, or haptic subsystems are included.

### Step 2: Build the JNI native library

```bash
# Ensure JAVA_HOME is set
export JAVA_HOME=/path/to/jdk

mkdir build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build . --config Release -j$(nproc)
```

The JNI library is automatically copied to `prebuilt/jni/<platform>/` after the build. CMake auto-detects the target platform and architecture.

`prebuilt/build-jni.sh` wraps that build (plus the Windows ARM64 cross build) for every platform:

```bash
cd prebuilt
./build-jni.sh                # native architecture
./build-jni.sh --all          # every architecture this host can produce
./build-jni.sh --arch aarch64 # cross-compile windows-aarch64 (Windows hosts)
```

#### Windows ARM64 cross-compile

`windows-aarch64` is cross-compiled from an x86_64 Windows host, so it does not need an ARM64 machine. CMake cannot drive this target — the host MSYS2/MinGW toolchain is x86_64-only and ships no aarch64 sysroot — so a clang/lld cross toolchain is used instead: either [zig](https://ziglang.org) or [llvm-mingw](https://github.com/mstorsjo/llvm-mingw), whichever `build-jni.sh` finds on PATH.

```bash
# 1. Get a cross toolchain — zig is a plain zip, no installer or admin rights
#    (unzip anywhere, put the directory on PATH)
# 2. JNI headers: JAVA_HOME, or a headers-only directory when the host has no JDK
#    (the win32 JNI headers are architecture neutral)
cd prebuilt
JNI_HEADERS_DIR=/d/toolchains/jni-headers ./build-jni.sh --arch aarch64
```

`WINDOWS_AARCH64_CC` overrides the compiler command. The script asserts the built image really is ARM64 (PE machine `0xaa64`) before installing it, because nothing else in the pipeline checks machine type — `verifyNativeSymbols` only scans for exported symbol names.

Two details worth knowing when rebuilding this artifact:

- SDL3 is resolved by reading the export table of `prebuilt/sdl/lwjgl-sdl-3.4.3/windows-aarch64/SDL3.dll` directly, so no ARM64 import library is stored in the repository (same reason the x86_64 `libSDL3.dll.a` was removed in `0e24ee3`).
- The ARM64 artifact imports the UCRT (`api-ms-win-crt-*.dll`) while the x86_64 artifact — built by MSYS2 MinGW — imports `msvcrt.dll`. Both are OS components on Windows 10/11 ARM64.

**Cross-compiling anything else** is done on the target OS. Repeat steps 1–2 on each platform you want to support.

### Step 3: Package the JAR

```bash
./build-snapshot.sh
```

This runs `gradle clean jar`, which pulls native libraries from `prebuilt/sdl/lwjgl-sdl-3.4.3/` and `prebuilt/jni/` into the JAR under `native/<platform>/`.

### Overriding native library path at runtime

Control Flex users can override the native library location:

```bash
# Environment variable
export CFX_LIB_PATH=/path/to/custom/natives

# Or JVM system property
-DCFX_LIB_PATH=/path/to/custom/natives
```

The directory must contain the SDL3 and JNI library files for the current platform.

## Using as a Dependency (JitPack)

This library is published to [JitPack](https://jitpack.io) for easy consumption in other Gradle projects.

### Add JitPack repository

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
```

### Add dependency

**Using custom coordinates** (recommended):

```gradle
dependencies {
    implementation 'com.github.ControlFlexMC:cfx-gamepad-jni:<version>'
}
```

Replace `<version>` with a release version (e.g. `0.8.7`). Check [JitPack](https://jitpack.io/#ControlFlexMC/cfx-gamepad-jni) for available versions.

JitPack builds the JAR from source on JDK 11. The resulting artifact includes Java classes and bundled native libraries for all supported platforms — no additional native compilation needed on the consumer side.

## Creating a Release

Use the release script to build, tag, and publish a GitHub Release (which JitPack picks up automatically):

```bash
# 1. Edit gradle.properties to set the release version
#    version=0.8.7

# 2. Run the release script (reads version from gradle.properties)
chmod +x publish-release.sh
./publish-release.sh
```

This script:

1. Commits the version bump in `gradle.properties`
2. Builds the JAR (`gradle clean jar`)
3. Pushes the commit + creates and pushes a Git tag (`v0.8.7`)
4. Creates a GitHub Release via `gh` CLI
5. Uploads the JAR as a release asset (with retry)
6. JitPack automatically picks up the release and publishes the Maven artifact

**Prerequisites:** [GitHub CLI (`gh`)](https://cli.github.com/) installed and authenticated (`gh auth login`).

## Project Structure

```
cfx-gamepad-jni/
├── src/main/
│   ├── c/                          # JNI C source
│   │   ├── gamepad_jni.c           # Core JNI implementation
│   │   └── gamepad_jni_macos.m     # macOS-specific (GCController queries)
│   └── java/com/ifels/gamepadjni/  # Java API
│       ├── GamepadManager.java     # Entry point: initialize, poll events, open/close gamepads
│       ├── Gamepad.java            # Per-gamepad state: axes, buttons, rumble, LED, sensors
│       ├── GamepadJNI.java         # Native method declarations
│       ├── NativeLibraryLoader.java # Extracts and loads natives from JAR
│       ├── Sdl3Source.java         # HOST vs BUNDLED (desktop); ignored on Android
│       ├── GamepadAxis.java        # Axis enum (LEFTX, LEFTY, TRIGGERS, etc.)
│       ├── GamepadButton.java      # Button enum (SOUTH, EAST, DPAD, etc.)
│       └── ...                     # Supporting enums and types
├── prebuilt/
│   ├── build-sdl3.sh                 # Script to build trimmed SDL3 (all platforms)
│   ├── build-sdl3-windows.bat         # Windows launcher for build-sdl3.sh
│   ├── build-jni.sh                 # Script to build JNI native library (all platforms)
│   ├── build-jni-windows.bat         # Windows launcher for build-jni.sh
│   ├── jni/                        # Prebuilt JNI libraries (per platform)
│   └── sdl/
│       ├── include/SDL3/               # SDL3 headers (JNI compile)
│       ├── lwjgl-sdl-3.4.3/            # Bundled desktop SDL3 (JAR + link)
│       └── trimmed/                    # Local build-sdl3.sh output
├── third_party/SDL/                # SDL3 source (git submodule)
├── CMakeLists.txt                  # CMake build for the JNI native library
├── build.gradle                    # Gradle build for the Java JAR
├── settings.gradle                 # Gradle project settings
├── gradle.properties               # Project coordinates and Gradle settings
├── build-snapshot.sh               # Local dev build (auto -SNAPSHOT suffix)
├── publish-release.sh              # Script to create a GitHub Release
└── jitpack.yml                     # JitPack CI configuration
```

## License

### gamepad-jni (our code)

MIT License — see [LICENSE](LICENSE) for the full text.

Copyright (c) 2026 ifels

### SDL3

SDL3 is distributed under the ZLib license — see [LICENSE_SDL3](LICENSE_SDL3) for the full text.

Copyright (C) 1997-2026 Sam Lantinga \<slouken@libsdl.org\>

The SDL3 source is a git submodule at `third_party/SDL/`, pinned to
[libsdl-org/SDL `release-3.4.14`](https://github.com/libsdl-org/SDL/releases/tag/release-3.4.14)
(`147a8ee32dbf9ac02f3794964490687b6bbda1bc`). It is compiled into a trimmed
shared library. No modifications are made to the SDL3 source code.
