package com.ifels.gamepadjni;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Loads native libraries (SDL3 and gamepad-jni JNI) from the JAR or filesystem.
 *
 * <p>Loading strategy:</p>
 * <ol>
 *   <li>Extract from classpath (bundled in JAR) to a temp directory</li>
 *   <li>Try system property {@code CFX_LIB_PATH} or env var {@code CFX_LIB_PATH}</li>
 *   <li>Try java.library.path</li>
 * </ol>
 *
 * <p>The loading order is: SDL3 shared library first, then the JNI native library.</p>
 */
final class NativeLibraryLoader {

    private static final String JNI_LIB_NAME = "gamepadjni";
    private static final String SDL_LIB_NAME = "SDL3";

    /** Flag to prevent double loading. */
    private static volatile boolean loaded = false;

    private NativeLibraryLoader() {
    }

    /**
     * Load both the SDL3 shared library and the JNI native library.
     *
     * <p>This is called automatically by {@link GamepadManager#initialize()}.</p>
     *
     * @throws UnsatisfiedLinkError if libraries cannot be loaded
     */
    static void load() {
        if (loaded) return;

        synchronized (NativeLibraryLoader.class) {
            if (loaded) return;

            // Step 1: Android and desktop use different loading chains.
            if (AndroidPlatform.isAndroid()) {
                loadAndroid();
            } else {
                loadDesktop();
            }

            loaded = true;
        }
    }

    /**
     * Desktop chain: extract SDL3 and the JNI library from
     * {@code native/<platform>/} in the classpath.
     *
     * <p>Behaviour is identical to before the Android work.</p>
     */
    private static void loadDesktop() {
        // Step 1: Determine the native library directory
        Path nativeDir = findNativeDir();

        // Step 2: Load SDL3 shared library
        loadSDL3(nativeDir);

        // Step 3: Load the JNI library
        loadJNILibrary(nativeDir);
    }

    /**
     * Android chain: SDL3 comes from the launcher APK (whose ART side has already
     * installed the {@code org.libsdl.app.*} glue), and we only supply our own
     * {@code libgamepadjni.so}.
     *
     * <p>The order is a hard constraint: SDL3 must be loaded first, because the
     * JNI library's {@code DT_NEEDED} is {@code libSDL3.so} and the dynamic linker
     * binds it by SONAME to that very instance.</p>
     */
    private static void loadAndroid() {
        logAndroidDiagnostics();

        // (1) SDL3: the launcher's copy only. CFX_LIB_PATH deliberately does NOT
        //     apply here - loading SDL3 from another path creates a second instance
        //     which has no launcher glue and can never see a gamepad.
        Path sdl = loadLauncherSdl3();
        if (sdl == null) {
            throw new UnsatisfiedLinkError(
                    "SDL3 unavailable on Android. Tried: "
                            + AndroidPlatform.launcherSdlPath(System.getenv("POJAV_NATIVEDIR"))
                            + " and System.loadLibrary(\"SDL3\"). "
                            + "See the [gamepad-jni] Android diagnostic lines above.");
        }
        GamepadLog.info("[gamepad-jni] Android: loaded SDL3 from {}", sdl);

        // (2) libgamepadjni.so: CFX_LIB_PATH only takes effect at this step.
        Path override = libPathOverride();
        if (override != null) {
            Path jni = override.resolve(getJNIFileName());
            if (Files.exists(jni)) {
                loadJniBridge(jni);
                GamepadLog.info("[gamepad-jni] Android: loaded JNI bridge from CFX_LIB_PATH {}", jni);
                return;
            }
        }
        String platformDir = AndroidPlatform.nativePlatformDir();
        if (platformDir == null) {
            throw new UnsatisfiedLinkError(
                    "Unsupported Android ABI: os.arch=" + System.getProperty("os.arch")
                            + " (supported: arm64-v8a, armeabi-v7a, x86_64)");
        }
        Path dir = extractFromClasspath(platformDir);
        if (dir == null) {
            throw new UnsatisfiedLinkError(
                    "No native/" + platformDir + "/" + getJNIFileName()
                            + " found in the classpath. Is the mod jar built with Android natives?");
        }
        Path jni = dir.resolve(getJNIFileName());
        loadJniBridge(jni);
        GamepadLog.info("[gamepad-jni] Android: loaded JNI bridge from {}", jni);
    }

    /**
     * Load our own JNI bridge, converting every failure into an
     * {@link UnsatisfiedLinkError} so callers keep a single failure mode.
     *
     * <p>{@code Throwable} rather than {@code UnsatisfiedLinkError}: a native load
     * can also surface an {@code Error} (for example a {@code NoClassDefFoundError}
     * left pending by a {@code JNI_OnLoad} the JVM ran on this handle). Per spec
     * 5.3 any such failure must degrade to "no controller", never reach the game
     * entrypoint.</p>
     */
    private static void loadJniBridge(Path jni) {
        try {
            System.load(jni.toString());
        } catch (Throwable t) {
            throw new UnsatisfiedLinkError(
                    "Failed to load " + jni + ": " + t.getClass().getName()
                            + (t.getMessage() != null ? ": " + t.getMessage() : ""));
        }
    }

    /**
     * Load the launcher-provided SDL3 on Android.
     *
     * @return the path actually used, or {@code null} if both strategies failed
     */
    private static Path loadLauncherSdl3() {
        String path = AndroidPlatform.launcherSdlPath(System.getenv("POJAV_NATIVEDIR"));
        if (path != null) {
            try {
                System.load(path);
                return Paths.get(path);
            } catch (Throwable t) {
                // Throwable, not just UnsatisfiedLinkError: the JVM resolves JNI_OnLoad on
                // this handle and can end up running SDL's (see gamepad_jni.c), which leaves
                // a pending NoClassDefFoundError. An Error must never escape this method.
                GamepadLog.warn("[gamepad-jni] Android: System.load({}) failed: {}: {}",
                        path, t.getClass().getName(), t.getMessage());
            }
        }
        try {
            System.loadLibrary("SDL3");
            return Paths.get("libSDL3.so (resolved via java.library.path)");
        } catch (Throwable t) {
            GamepadLog.warn("[gamepad-jni] Android: System.loadLibrary(\"SDL3\") failed: {}: {}",
                    t.getClass().getName(), t.getMessage());
            return null;
        }
    }

    /** The CFX_LIB_PATH override directory, or {@code null} when unset / not a directory. */
    private static Path libPathOverride() {
        String libPath = System.getProperty("CFX_LIB_PATH");
        if (libPath == null || libPath.isEmpty()) {
            libPath = System.getenv("CFX_LIB_PATH");
        }
        if (libPath == null || libPath.isEmpty()) {
            return null;
        }
        Path dir = Paths.get(libPath);
        return Files.isDirectory(dir) ? dir : null;
    }

    /**
     * One-shot diagnostic block. Reported "SDL3 not found" issues are diagnosed
     * from exactly these lines, so keep them in one place and log them once.
     */
    private static void logAndroidDiagnostics() {
        GamepadLog.info("[gamepad-jni] Android platform: os.name={} os.version={} os.arch={} vm={}",
                System.getProperty("os.name"), System.getProperty("os.version"),
                System.getProperty("os.arch"), System.getProperty("java.vm.name"));
        GamepadLog.info("[gamepad-jni] Android env: POJAV_NATIVEDIR={} tmpdir={} abiDir={}",
                System.getenv("POJAV_NATIVEDIR"), System.getProperty("java.io.tmpdir"),
                AndroidPlatform.nativePlatformDir());
        GamepadLog.info("[gamepad-jni] Android java.library.path={}",
                System.getProperty("java.library.path"));
    }

    /**
     * Find the directory containing native libraries.
     */
    private static Path findNativeDir() {
        // Strategy 1: Extract from classpath (bundled natives)
        String platformDir = getPlatformDir();
        if (platformDir != null) {
            Path extracted = extractFromClasspath(platformDir);
            if (extracted != null) {
                return extracted;
            }
        }

        // Strategy 2: User-specified override (system property or env var)
        String libPath = System.getProperty("CFX_LIB_PATH");
        if (libPath == null || libPath.isEmpty()) {
            libPath = System.getenv("CFX_LIB_PATH");
        }
        if (libPath != null && !libPath.isEmpty()) {
            Path dir = Paths.get(libPath);
            if (Files.isDirectory(dir)) {
                return dir;
            }
        }

        // Strategy 3: Fallback - let JVM use java.library.path
        return null;
    }

    /**
     * Load the SDL3 shared library.
     */
    private static void loadSDL3(Path nativeDir) {
        if (nativeDir != null) {
            String sdlFileName = getSDL3FileName();
            Path sdlPath = nativeDir.resolve(sdlFileName);
            if (Files.exists(sdlPath)) {
                System.load(sdlPath.toString());
                return;
            }
        }

        // Fallback to system library path
        try {
            System.loadLibrary(SDL_LIB_NAME);
        } catch (UnsatisfiedLinkError e) {
            throw new UnsatisfiedLinkError(
                    "Failed to load SDL3 library. " +
                            "Ensure " + getSDL3FileName() + " is in the native directory, " +
                            "set CFX_LIB_PATH env var, or add to java.library.path. " +
                            "Original error: " + e.getMessage());
        }
    }

    /**
     * Load the JNI native library.
     */
    private static void loadJNILibrary(Path nativeDir) {
        if (nativeDir != null) {
            String jniFileName = getJNIFileName();
            Path jniPath = nativeDir.resolve(jniFileName);
            if (Files.exists(jniPath)) {
                System.load(jniPath.toString());
                return;
            }
        }

        // Fallback to system library path
        try {
            System.loadLibrary(JNI_LIB_NAME);
        } catch (UnsatisfiedLinkError e) {
            throw new UnsatisfiedLinkError(
                    "Failed to load gamepad-jni native library. " +
                            "Ensure " + getJNIFileName() + " is in the native directory, " +
                            "set CFX_LIB_PATH env var, or add to java.library.path. " +
                            "Original error: " + e.getMessage());
        }
    }

    /**
     * Extract native libraries from the classpath to a temp directory.
     *
     * @param platformDir platform-specific directory name (e.g. "darwin-aarch64")
     * @return path to extracted directory, or null on failure
     */
    private static Path extractFromClasspath(String platformDir) {
        try {
            Path tempDir = Files.createTempDirectory("gamepadjni-");
            tempDir.toFile().deleteOnExit();

            // Extract SDL3 library
            String sdlResource = "native/" + platformDir + "/" + getSDL3FileName();
            boolean sdlExtracted = extractResource(sdlResource, tempDir.resolve(getSDL3FileName()));

            // Extract JNI library
            String jniResource = "native/" + platformDir + "/" + getJNIFileName();
            boolean jniExtracted = extractResource(jniResource, tempDir.resolve(getJNIFileName()));

            if (sdlExtracted || jniExtracted) {
                return tempDir;
            }

            // Nothing extracted, clean up
            deleteDirectory(tempDir);
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Extract a single resource from the classpath.
     *
     * @return true if successfully extracted
     */
    private static boolean extractResource(String resourcePath, Path targetPath) {
        URL url = NativeLibraryLoader.class.getClassLoader().getResource(resourcePath);
        if (url == null) return false;

        try (InputStream in = url.openStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            targetPath.toFile().setReadable(true, false);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get the platform-specific directory name.
     */
    private static String getPlatformDir() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String osArch = System.getProperty("os.arch", "").toLowerCase();

        if (osName.contains("mac")) {
            if (osArch.contains("aarch64") || osArch.contains("arm")) {
                return "darwin-aarch64";
            } else {
                return "darwin-x86_64";
            }
        } else if (osName.contains("linux")) {
            if (osArch.contains("aarch64") || osArch.contains("arm")) {
                return "linux-aarch64";
            } else {
                return "linux-x86_64";
            }
        } else if (osName.contains("win")) {
            if (osArch.contains("aarch64") || osArch.contains("arm")) {
                return "windows-aarch64";
            } else {
                return "windows-x86_64";
            }
        }

        return null;
    }

    /**
     * Get the SDL3 library file name for the current platform.
     */
    private static String getSDL3FileName() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac")) {
            return "libSDL3.0.dylib";
        } else if (osName.contains("win")) {
            return "SDL3.dll";
        } else {
            return "libSDL3.so";
        }
    }

    /**
     * Get the JNI library file name for the current platform.
     */
    private static String getJNIFileName() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac")) {
            return "libgamepadjni.dylib";
        } else if (osName.contains("win")) {
            return "gamepadjni.dll";
        } else {
            return "libgamepadjni.so";
        }
    }

    /**
     * Recursively delete a directory.
     */
    private static void deleteDirectory(Path dir) {
        try {
            Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a))
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException ignored) {
        }
    }
}
