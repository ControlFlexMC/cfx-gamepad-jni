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

            // Step 1: Determine the native library directory
            Path nativeDir = findNativeDir();

            // Step 2: Load SDL3 shared library
            loadSDL3(nativeDir);

            // Step 3: Load the JNI library
            loadJNILibrary(nativeDir);

            loaded = true;
        }
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
