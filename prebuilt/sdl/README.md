# Prebuilt SDL3

Desktop SDL3 binaries and headers used by gamepad-jni. This directory is the
source of truth for **linking** `libgamepadjni` and for **packaging** the
bundled copy inside the JAR. Gradle does not download these natives from Maven.

Minecraft 26.3+ already ships LWJGL 3.4.3 (SDL 3.4.14). Callers use
`Sdl3Source.HOST` there. Older Minecraft still needs a copy of SDL3 in this
JAR (`Sdl3Source.BUNDLED`). That copy is the trimmed, joystick/gamepad-only
build — not the full LWJGL SDL3.

## Layout

```
prebuilt/sdl/
  README.md                 # this file
  include/SDL3/             # headers for compiling the JNI library
  trimmed/<os-arch>         # CMake link input + JAR contents (build-sdl3.sh)
  lwjgl-sdl-3.4.3/<os-arch> # full LWJGL 3.4.3 SDL3; not linked, not packaged
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

## `trimmed/`

Input-only SDL3 built from the `third_party/SDL` submodule (`release-3.4.14`)
by `prebuilt/build-sdl3.sh` / `build-sdl3-windows.bat`. Video, audio, GPU,
render, camera, and windowing backends are off; joystick/gamepad and sensor
stay on. That is what ControlFlex actually calls, and it is about 2 MB smaller
in the published JAR than a full LWJGL SDL3.

CMake and Gradle always use this directory. Linking against the same binaries
that are packaged means `libgamepadjni` cannot grow a dependency on a symbol
that exists only in the full LWJGL build. `Sdl3Source.HOST` still works: the
Minecraft/LWJGL SDL3 is a superset of these gamepad APIs, and the JNI library
matches by SONAME / `SDL3.dll` / `dynamic_lookup`, not by file identity.

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

Refreshing the binaries: run `prebuilt/build-sdl3.sh` (or
`build-sdl3-windows.bat`) and commit the new files under `trimmed/`. Keep the
file names in the table above.

## `lwjgl-sdl-3.4.3/`

Full SDL3 shared libraries taken from LWJGL **3.4.3** (the same SDL 3.4.14
that Minecraft 26.3 uses). Not linked and not packaged. Kept as a size/ABI
reference for `Sdl3Source.HOST`. Do not point CMake or Gradle at this
directory.

## Licenses

- SDL3 (`trimmed/`, headers, submodule): zlib — see [LICENSE_SDL3](../../LICENSE_SDL3)
- LWJGL (the unused `lwjgl-sdl-3.4.3/` copies): BSD-3-Clause
