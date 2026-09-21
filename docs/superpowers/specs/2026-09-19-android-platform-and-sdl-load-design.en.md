# Android platform detection and launcher SDL3 loading

Date: 2026-09-19

- [中文版](2026-09-19-android-platform-and-sdl-load-design.zh.md)

SDL search/load and platform detection follow this document: gate on the `os.version` prefix only; `os.name` is diagnostic. Desktop HOST vs BUNDLED exclusive loading is out of scope except as the non-Android branch. Per-launcher JVM flags and environment variables are in [docs/android](../../android/README.md).

## Goals

In the game JVM provided by an Android Minecraft: Java Edition launcher:

1. Detect the platform from `os.version`, not from environment variables and not from `os.name`.
2. Load **the launcher APK’s** `libSDL3.so` (the launcher’s ART side has already installed the `org.libsdl.app.*` glue).
3. Then load this library’s `libgamepadjni.so`. On failure, initialization returns false; the game continues without a controller.

## Supported launchers

This is a compatibility note, **not a runtime allowlist**. Detection uses only `os.version`. An unlisted launcher that sets `os.version` to an `Android-` prefix also takes the Android path. All four still inject `-Dos.name=Linux` by default; that value appears in diagnostics only.

| Launcher | Source baseline | Launcher default `-D` | How the launcher `libSDL3.so` is found |
|----------|-----------------|------------------------------|----------------------------------------|
| Fold Craft Launcher (FCL) | See [docs/android](../../android/README.md) | `-Dos.name=Linux` `-Dos.version=Android-<RELEASE>` | Sets `POJAV_NATIVEDIR` to the APK native dir; absolute `System.load` |
| ZalithLauncher2 (ZL2) | See [docs/android](../../android/README.md) | Same | Same |
| Mojo Launcher | **`v3_openjdk` branch only** | Same | **Does not set** `POJAV_NATIVEDIR`; APK native dir is on `LD_LIBRARY_PATH`; `System.loadLibrary("SDL3")` |
| Amethyst | See [docs/android](../../android/README.md) | Same | Sets `POJAV_NATIVEDIR`; absolute `System.load` |

All four inject those two `-D` flags by default. User JVM arguments that override the same prefix can break detection (for example a later `-Dos.version=5.0`). That is launcher behavior; this library does not compensate.

On Android, SDL3 comes **only from the launcher APK**. Not used:

- Desktop SDL3 packaged in the JAR
- The caller’s `Sdl3Source.HOST` / `Sdl3Source.BUNDLED`
- A second SDL copy via `CFX_LIB_PATH`
- Mojo’s `-Dorg.lwjgl.sdl.libname=<apk>/libSDL3.so` (written for LWJGL; this library does not read it; LWJGL and `loadLibrary("SDL3")` should bind the same APK file)

## Platform detection

Entry point: `AndroidPlatform.isAndroid()`. `os.version` is read once at class load and cached. The pure function `detect(osVersion)` takes an explicit value for tests.

A desktop JVM’s `os.version` is a kernel number (`10.0`, `24.6.0`, `6.1.0`). Only Android launchers rewrite it to `Android-<RELEASE>`. That prefix is enough to separate Android from Windows / macOS / GNU/Linux desktops. `os.name=Linux` is also the PC Linux value, so it is not a discriminator.

Algorithm (case-insensitive, `Locale.ROOT`):

1. `osVersion` is non-null.
2. `osVersion.toLowerCase(ROOT).startsWith("android-")` (hyphen required; `Android` and `Android14` do not match).

If that fails → not Android.

`os.name` is **not** part of detection. It is logged in `loadAndroid()` diagnostics so a field log can show whether the launcher also set `-Dos.name=Linux`.

**Not used for detection:** `os.name`, `POJAV_NATIVEDIR`, `FCL_NATIVEDIR`, vendor strings, `os.arch`, `java.vm.name`, `org.lwjgl.sdl.libname`, leftover Windows / macOS environment variables. A desktop process that exports `POJAV_NATIVEDIR` is still not Android.

Empty / missing `os.version` (`""`) does not start with `android-`, so not Android.

## Load dispatch

`GamepadManager.initialize(Sdl3Source source)` → `NativeLibraryLoader.load(source)` → `Sdl3Source.resolveLoadKind(isAndroid, source)`:

| `isAndroid` | Caller `Sdl3Source` | `LoadKind` | Behavior |
|-------------|---------------------|------------|----------|
| true | HOST or BUNDLED (both ignored) | `LAUNCHER` | `loadAndroid()`; `hostSdl3 = true` (share in-process SDL with the launcher; do not `SDL_Quit` the whole library later) |
| false | HOST | `HOST` | Desktop host SDL; missing means failure, no fallback to BUNDLED |
| false | BUNDLED | `BUNDLED` | JAR SDL only |
| any | null | — | `NullPointerException("sdl3 source")` |

After an Android hit, desktop HOST discovery is not used (LWJGL `SDL.getLibrary()`, `CFX_HOST_SDL3_DIR`, `org.lwjgl.librarypath`, and similar).

## Android: search and load launcher SDL3

`loadAndroid()` logs diagnostics first (`os.name` / `os.version` / `os.arch` / `java.vm.name`, `POJAV_NATIVEDIR`, `java.io.tmpdir`, ABI dir, `java.library.path`), then calls `loadLauncherSdl3()`.

Order is a hard constraint: SDL3 first, then `libgamepadjni.so`. On Android the JNI library’s `DT_NEEDED` is `libSDL3.so`; the dynamic linker must bind that name to the instance just loaded.

### Step 1: `POJAV_NATIVEDIR`

`AndroidPlatform.launcherSdlPath(System.getenv("POJAV_NATIVEDIR"))`:

- Null or empty → skip.
- Otherwise join `$POJAV_NATIVEDIR/libSDL3.so` (forward slash; no canonical / exists check) and `System.load(absolute path)`.
- Success → use that path.
- Failure → warn and **continue to step 2**. Catch `Throwable` (not only `UnsatisfiedLinkError`), because `JNI_OnLoad` may leave a pending `Error`.

FCL / ZL2 / Amethyst take this step by default. Mojo `v3_openjdk` Java does not `setenv(POJAV_NATIVEDIR)` unless the user writes it in `custom_env.txt`.

### Step 2: `System.loadLibrary("SDL3")`

The JVM looks for `libSDL3.so` on `java.library.path`. If the launcher does not set that property, HotSpot’s default search path includes `LD_LIBRARY_PATH`.

Mojo `v3_openjdk`:

- `JavaRunner.relocateLdLibPath` always puts the APK native dir (`Tools.NATIVE_LIB_DIR`) on `LD_LIBRARY_PATH`.
- `-Djava.library.path=<version natives>:<apk natives>` is added only when `cache/natives/<versionId>` exists.
- Version-JSON `-Djava.library.path=` is dropped by Mojo and does not override the two paths above.

Success still means the launcher APK’s `libSDL3.so`.

### Both steps fail

Throw `UnsatisfiedLinkError` naming the attempted `POJAV_NATIVEDIR` path and `System.loadLibrary("SDL3")`. `initialize` catches it and returns false.

`CFX_LIB_PATH` is **not** used for SDL: a second SDL3 without launcher glue can never see a gamepad.

## Android: load `libgamepadjni.so`

Only after SDL3 succeeds:

1. If `CFX_LIB_PATH` (system property before environment variable) is an existing directory that contains `libgamepadjni.so`, `System.load` that file.
2. Otherwise map `os.arch` to a JAR directory and extract:
   - `aarch64` / `arm64` → `native/android-arm64-v8a/`
   - `arm` / `armv7l` / `armv8l` → `native/android-armeabi-v7a/`
   - `x86_64` / `amd64` → `native/android-x86_64/`
   - other (including `i386`) → `UnsatisfiedLinkError` (unsupported ABI)
3. Missing classpath native → `UnsatisfiedLinkError`.

The JAR does **not** package an Android `libSDL3.so`.

## Error handling

| Failure | External result |
|---------|-----------------|
| Both SDL3 steps fail | `UnsatisfiedLinkError` → `initialize` returns false |
| JNI bridge `System.load` throws any `Throwable` | Converted to `UnsatisfiedLinkError` → false |
| `SDL_InitSubSystem` fails | false (libraries already loaded) |
| Any other `Throwable` | false; Errors must not reach the game entrypoint |

On Android `hostSdl3 == true`. Even if this library calls `InitSubSystem` first, it does not `SDL_Quit` the whole library on teardown, so the launcher’s video / glue stay intact.

## Tests

`AndroidPlatformTest` covers:

- Positives: `Android-14` / `13` / `10`; mixed case. Independent of `os.name` (`Windows 11` + `Android-14` is Android).
- Negatives: kernel versions (`6.1.0`), `Android` without a hyphen, `Android14`, null / empty.
- `launcherSdlPath` join and empty dir.
- ABI directory mapping (for JNI extract, not SDL search).

`Sdl3SourceTest` covers HOST / BUNDLED both resolving to `LAUNCHER` when Android.

Unit tests do not `System.load` a real device `.so`.

## Non-goals

- Do not turn the launcher list into runtime detection.
- Do not treat `POJAV_NATIVEDIR` as an Android gate.
- Do not read `-Dorg.lwjgl.sdl.libname`.
- Do not ship a second Android SDL3 in the JAR.
- Do not survey Mojo branches other than `v3_openjdk`.
- Do not use `os.name` as a detection gate (log it only).
- Do not treat leftover Win/Mac environment variables as Android signals.
