# Prebuilt SDL3

Desktop SDL3 binaries and headers used by gamepad-jni. This directory is the
source of truth for **linking** `libgamepadjni` and for **packaging** the
bundled copy inside the JAR. Gradle does not download these natives from Maven.

Minecraft 26.3+ already ships LWJGL 3.4.3 (SDL 3.4.14). Callers use
`Sdl3Source.HOST` there. Older Minecraft still needs a copy of SDL3 in this
JAR (`Sdl3Source.BUNDLED`). The files here are that copy.

## Layout

```
prebuilt/sdl/
  README.md                 # this file
  include/SDL3/             # headers for compiling the JNI library
  lwjgl-sdl-3.4.3/<os-arch> # LWJGL 3.4.3 SDL3: CMake link input + JAR contents
  trimmed/<os-arch>         # local output of build-sdl3.sh (not packaged)
```

`build/` under this directory is a CMake cache from `build-sdl3.sh` and is
gitignored.

Android is not stored here. Link-time `libSDL3.so` for Android comes from the
official `SDL3-devel-3.4.14-android` zip into `prebuilt/link-only/` (gitignored,
never packaged). Runtime SDL3 on Android is the launcher APK’s library.

## `include/`

CMake (`CMakeLists.txt`) and `prebuilt/build-jni.sh` compile against
`prebuilt/sdl/include`. Do not replace this tree from `build-sdl3.sh`; that
script is not allowed to overwrite these headers.

These headers match SDL 3.4.14 (`SDL_MAJOR_VERSION` / `SDL_MINOR_VERSION` /
`SDL_MICRO_VERSION` in `include/SDL3/SDL_version.h`).

## `lwjgl-sdl-3.4.3/`

SDL3 shared libraries taken from LWJGL **3.4.3** (the same SDL 3.4.14 that
Minecraft 26.3 uses). They stay in git so `./gradlew jar` and JNI rebuilds work
offline, including on JitPack.

| Path | File | Used by |
|------|------|---------|
| `darwin-aarch64/` | `libSDL3.0.dylib` | CMake existence check (macOS links with `dynamic_lookup`); Gradle JAR |
| `darwin-x86_64/` | `libSDL3.0.dylib` | same |
| `linux-x86_64/` | `libSDL3.so` | CMake `DT_NEEDED`; Gradle JAR |
| `linux-aarch64/` | `libSDL3.so` | same |
| `windows-x86_64/` | `SDL3.dll` | CMake link; Gradle JAR |
| `windows-aarch64/` | `SDL3.dll` | `build-jni.sh` ARM64 cross-link (export table, no import lib); Gradle JAR |

`build.gradle` copies these files into `native/<os-arch>/` in the JAR. At
runtime `NativeLibraryLoader` extracts that path when the caller passes
`Sdl3Source.BUNDLED`.

Do not point CMake or Gradle at `trimmed/`. Linking and packaging always use
this directory.

Refreshing the binaries: copy the SDL3 shared library out of the matching
LWJGL artifact (`org.lwjgl:lwjgl-sdl:3.4.3` with classifier
`natives-macos-arm64`, `natives-macos`, `natives-linux`, `natives-linux-arm64`,
`natives-windows`, or `natives-windows-arm64`) and replace the file in the
platform folder. Keep the file names in the table above. A Gradle Maven fetch
at `jar` time would produce the same bytes and would not change how
`libgamepadjni` is built; it is intentionally not used.

## `trimmed/`

Output of `prebuilt/build-sdl3.sh` / `build-sdl3-windows.bat`: an input-only
SDL3 built from the `third_party/SDL` submodule (`release-3.4.14`). Useful for
local experiments. Not linked, not packaged.

## Licenses

- SDL3: zlib — see [LICENSE_SDL3](../../LICENSE_SDL3)
- LWJGL (distribution of these binaries): BSD-3-Clause
