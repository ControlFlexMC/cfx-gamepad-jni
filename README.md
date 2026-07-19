# gamepad-jni

A lightweight Java JNI wrapper exposing the SDL3 Gamepad API, built for the **[Control Flex](https://www.curseforge.com/minecraft/mc-mods/control-flex)** Minecraft mod.

Control Flex uses this library to provide cross-platform gamepad support (Xbox, PlayStation, Switch Pro, and other controllers) with rumble, LED, touchpad, and motion sensor capabilities — all through SDL3's unified gamepad API.

## Supported Platforms

| Platform  | Architectures                  |
| --------- | ------------------------------ |
| macOS     | `aarch64` (Apple Silicon), `x86_64` (Intel) |
| Windows   | `x86_64`, `aarch64`            |
| Linux     | `x86_64`, `aarch64`            |

## Native Libraries

This project produces two native libraries per platform:

| Library              | Source                               | Description                          |
| -------------------- | ------------------------------------ | ------------------------------------ |
| `libgamepadjni.so` / `.dylib` / `.dll` | `src/main/c/` (built via CMake)       | JNI bridge between Java and SDL3     |
| `libSDL3.so.0` / `libSDL3.0.dylib` / `SDL3.dll` | `third_party/SDL/` (built via `prebuilt/build_sdl_input_only.sh`) | Trimmed SDL3 — input devices only    |

### Library file names by platform

| Platform | JNI Library            | SDL3 Library         |
| -------- | ---------------------- | -------------------- |
| macOS    | `libgamepadjni.dylib`  | `libSDL3.0.dylib`    |
| Windows  | `gamepadjni.dll`       | `SDL3.dll`           |
| Linux    | `libgamepadjni.so`     | `libSDL3.so.0`       |

## Adding Platform Support for Control Flex

Control Flex loads native libraries from the JAR at runtime. To add or update a platform's native libraries, place the compiled `.so`/`.dylib`/`.dll` files into the corresponding `prebuilt/` directories, then rebuild the JAR.

### Directory structure inside the JAR

```
gamepad-jni-<version>.jar
└── native/
    ├── darwin-aarch64/
    │   ├── libSDL3.0.dylib
    │   └── libgamepadjni.dylib
    ├── darwin-x86_64/
    │   ├── libSDL3.0.dylib
    │   └── libgamepadjni.dylib
    ├── linux-aarch64/
    │   ├── libSDL3.so.0
    │   └── libgamepadjni.so
    ├── linux-x86_64/
    │   ├── libSDL3.so.0
    │   └── libgamepadjni.so
    ├── windows-aarch64/
    │   ├── SDL3.dll
    │   └── gamepadjni.dll
    └── windows-x86_64/
        ├── SDL3.dll
        └── gamepadjni.dll
```

### Step 1: Build the trimmed SDL3 library

```bash
cd prebuilt
chmod +x build_sdl_input_only.sh
./build_sdl_input_only.sh
```

This produces per-platform SDL3 binaries under `prebuilt/sdl/<platform>/`, stripped down to only input subsystems (keyboard, mouse, joystick/gamepad, sensor). No audio, GPU, render, camera, or haptic subsystems are included.

### Step 2: Build the JNI native library

```bash
# Ensure JAVA_HOME is set
export JAVA_HOME=/path/to/jdk

mkdir build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build . --config Release -j$(nproc)
```

The JNI library is automatically copied to `prebuilt/jni/<platform>/` after the build. CMake auto-detects the target platform and architecture.

**Cross-compiling for other platforms** must be done on the target OS (or via a cross-compilation toolchain). Repeat steps 1–2 on each platform you want to support.

### Step 3: Package the JAR

```bash
./build_jar.sh
```

This runs `gradle clean jar`, which pulls native libraries from `prebuilt/sdl/` and `prebuilt/jni/` into the JAR under `native/<platform>/`.

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
    implementation 'com.ifels:gamepad-jni:<version>'
}
```

**Using GitHub coordinates**:

```gradle
dependencies {
    implementation 'com.github.ifels:cfx-gamepad-jni:<tag>'
}
```

Replace `<version>` with a release version (e.g. `1.0.0.8`) or `<tag>` with a Git tag (e.g. `v1.0.0.8`). Check [JitPack](https://jitpack.io/#ifels/cfx-gamepad-jni) for available versions.

JitPack builds the JAR from source on JDK 11. The resulting artifact includes Java classes and bundled native libraries for all supported platforms — no additional native compilation needed on the consumer side.

## Creating a Release

Use the release script to build, tag, and publish a GitHub Release (which JitPack picks up automatically):

```bash
chmod +x publish_release.sh
./publish_release.sh 1.0.0.8
```

On Windows:

```cmd
publish_release.bat 1.0.0.8
```

This script:

1. Builds the JAR with the specified version (`gradle clean jar -Pversion=1.0.0.8`)
2. Creates and pushes a Git tag (`v1.0.0.8`)
3. Creates a GitHub Release via `gh` CLI
4. Uploads the JAR as a release asset
5. JitPack automatically picks up the release and publishes the Maven artifact

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
│       ├── GamepadAxis.java        # Axis enum (LEFTX, LEFTY, TRIGGERS, etc.)
│       ├── GamepadButton.java      # Button enum (SOUTH, EAST, DPAD, etc.)
│       └── ...                     # Supporting enums and types
├── prebuilt/
│   ├── build_sdl_input_only.sh     # Script to build trimmed SDL3
│   └── sdl/                        # Prebuilt SDL3 libraries (per platform)
│       └── jni/                    # Prebuilt JNI libraries (per platform)
├── third_party/SDL/                # SDL3 source (git submodule)
├── CMakeLists.txt                  # CMake build for the JNI native library
├── build.gradle                    # Gradle build for the Java JAR
├── settings.gradle                 # Gradle project settings
├── build_jar.sh                    # Script to package the JAR
├── build_jar.bat                   # Windows batch variant
├── publish_release.sh                # Script to create a GitHub Release
├── publish_release.bat               # Windows batch variant
└── jitpack.yml                     # JitPack CI configuration
```

## License

### gamepad-jni (our code)

MIT License — see [LICENSE](LICENSE) for the full text.

Copyright (c) 2026 ifels

### SDL3

SDL3 is distributed under the ZLib license — see [LICENSE_SDL3](LICENSE_SDL3) for the full text.

Copyright (C) 1997-2026 Sam Lantinga \<slouken@libsdl.org\>

The SDL3 source is included as a git submodule in `third_party/SDL/` and compiled into a trimmed shared library. No modifications are made to the SDL3 source code.
