package com.ifels.gamepadjni;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Loads native libraries (SDL3 and gamepad-jni JNI) from the host process, the JAR,
 * or the filesystem.
 *
 * <p>Loading strategy for SDL3:</p>
 * <ol>
 *   <li>Prefer Minecraft/LWJGL's SDL3 from {@code org.lwjgl.librarypath}
 *       ({@code libSDL3.dylib} / {@code libSDL3.so} / {@code SDL3.dll}).
 *       {@code System.load} that host file so JNI shares the same mapping;
 *       never extract or load the bundled SDL3 in that case.</li>
 *   <li>Otherwise load from {@code org.lwjgl.librarypath}, {@code java.library.path},
 *       or an extracted bundled native.</li>
 * </ol>
 *
 * <p>The JNI library is always extracted/loaded after SDL3 is available in the
 * process. Set {@code cfx.gamepadjni.forceBundledSdl3=true} to skip host sharing.</p>
 */
final class NativeLibraryLoader {

    private static final String JNI_LIB_NAME = "gamepadjni";
    private static final String SDL_LIB_NAME = "SDL3";

    /** Flag to prevent double loading. */
    private static volatile boolean loaded = false;

    /** True when SDL3 was adopted from the host / LWJGL instead of the bundled copy. */
    private static volatile boolean hostSdl3 = false;

    private NativeLibraryLoader() {
    }

    /** Whether this process is using a host-provided SDL3 (not the bundled native). */
    static boolean usedHostSdl3() {
        return hostSdl3;
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

            boolean adoptHost = !forceBundledSdl3() && tryAdoptHostSdl3();
            hostSdl3 = adoptHost;

            Path nativeDir = findNativeDir(adoptHost);
            if (!adoptHost) {
                loadSDL3(nativeDir);
            } else {
                GamepadLog.info("[gamepad-jni] using host SDL3 (Minecraft/LWJGL); bundled SDL3 not loaded");
            }

            loadJNILibrary(nativeDir);

            loaded = true;
        }
    }

    private static boolean forceBundledSdl3() {
        return Boolean.parseBoolean(System.getProperty("cfx.gamepadjni.forceBundledSdl3", "false"));
    }

    /**
     * Reuse SDL3 already mapped into this JVM (LWJGL) or load it from LWJGL's
     * native directory without extracting our bundled copy.
     */
    private static boolean tryAdoptHostSdl3() {
        if (isLwjglSdlClassPresent()) {
            Path lwjglDir = firstExistingDir(
                    System.getProperty("org.lwjgl.librarypath"),
                    System.getProperty("org.lwjgl.librarypath.sdl"));
            Path sdl = lwjglDir != null ? findSdl3File(lwjglDir) : null;
            if (sdl != null) {
                // ControlFlex initializes before Minecraft creates the window, so SDL3
                // is not mapped yet. Load the same host file LWJGL will use — not a
                // second copy. System.load of an already-mapped path is a no-op.
                System.load(sdl.toString());
                GamepadLog.info("[gamepad-jni] loaded host SDL3 from {}", sdl);
                return true;
            }
            GamepadLog.info("[gamepad-jni] LWJGL SDL class present but host native not found on library path");
        }

        Path fromEnv = firstExistingDir(
                System.getProperty("cfx.gamepadjni.hostSdl3Dir"),
                System.getenv("CFX_HOST_SDL3_DIR"));
        if (fromEnv != null) {
            Path sdl = findSdl3File(fromEnv);
            if (sdl != null) {
                System.load(sdl.toString());
                GamepadLog.info("[gamepad-jni] loaded host SDL3 from {}", sdl);
                return true;
            }
        }
        return false;
    }

    private static boolean isLwjglSdlClassPresent() {
        try {
            Class.forName("org.lwjgl.sdl.SDL", false, NativeLibraryLoader.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Path firstExistingDir(String... candidates) {
        if (candidates == null) return null;
        for (String raw : candidates) {
            if (raw == null || raw.isEmpty()) continue;
            Path dir = Paths.get(raw);
            if (Files.isDirectory(dir)) {
                return dir;
            }
        }
        return null;
    }

    private static Path findSdl3File(Path dir) {
        String primary = getSDL3FileName();
        Path exact = dir.resolve(primary);
        if (Files.isRegularFile(exact)) {
            return exact;
        }
        String osName = System.getProperty("os.name", "").toLowerCase();
        String[] aliases;
        if (osName.contains("mac")) {
            aliases = new String[]{"libSDL3.dylib", "libSDL3.0.dylib"};
        } else if (osName.contains("win")) {
            aliases = new String[]{"SDL3.dll"};
        } else {
            aliases = new String[]{"libSDL3.so", "libSDL3.so.0"};
        }
        for (String name : aliases) {
            Path p = dir.resolve(name);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (name.contains("SDL3") && Files.isRegularFile(p)) {
                    return p;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * Find the directory containing native libraries.
     *
     * @param hostSdl3AlreadyAvailable when true, bundled SDL3 is not extracted
     */
    private static Path findNativeDir(boolean hostSdl3AlreadyAvailable) {
        String platformDir = getPlatformDir();
        if (platformDir != null) {
            Path extracted = extractFromClasspath(platformDir, !hostSdl3AlreadyAvailable);
            if (extracted != null) {
                return extracted;
            }
        }

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

        return null;
    }

    /**
     * Load the SDL3 shared library.
     */
    private static void loadSDL3(Path nativeDir) {
        if (nativeDir != null) {
            Path sdlPath = findSdl3File(nativeDir);
            if (sdlPath != null) {
                System.load(sdlPath.toString());
                return;
            }
        }

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
     * @param extractSdl3 whether to extract bundled SDL3 (false when sharing host SDL3)
     * @return path to extracted directory, or null on failure
     */
    private static Path extractFromClasspath(String platformDir, boolean extractSdl3) {
        try {
            Path tempDir = Files.createTempDirectory("gamepadjni-");
            tempDir.toFile().deleteOnExit();

            boolean sdlExtracted = false;
            if (extractSdl3) {
                String sdlResource = "native/" + platformDir + "/" + getSDL3FileName();
                sdlExtracted = extractResource(sdlResource, tempDir.resolve(getSDL3FileName()));
            }

            String jniResource = "native/" + platformDir + "/" + getJNIFileName();
            boolean jniExtracted = extractResource(jniResource, tempDir.resolve(getJNIFileName()));

            if (sdlExtracted || jniExtracted) {
                return tempDir;
            }

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
            targetPath.toFile().setExecutable(true, false);
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
