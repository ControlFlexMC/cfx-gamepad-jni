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
import java.util.ArrayList;
import java.util.List;

/**
 * Loads native libraries (SDL3 and gamepad-jni JNI) from the host process, the JAR,
 * or the filesystem.
 *
 * <p>Desktop loading strategy for SDL3:</p>
 * <ol>
 *   <li>Prefer Minecraft/LWJGL's SDL3 from {@code org.lwjgl.librarypath}
 *       ({@code libSDL3.dylib} / {@code libSDL3.so} / {@code SDL3.dll}).
 *       {@code System.load} that host file so JNI shares the same mapping;
 *       never extract or load the bundled SDL3 in that case.</li>
 *   <li>Otherwise load from {@code org.lwjgl.librarypath}, {@code java.library.path},
 *       or an extracted bundled native.</li>
 * </ol>
 *
 * <p>Android uses a separate chain: SDL3 comes from the launcher APK, and only
 * {@code libgamepadjni.so} is extracted from the JAR.</p>
 *
 * <p>The JNI library is always extracted/loaded after SDL3 is available in the
 * process. Set {@code cfx.gamepadjni.forceBundledSdl3=true} to skip host sharing
 * on desktop.</p>
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

            if (AndroidPlatform.isAndroid()) {
                loadAndroid();
            } else {
                loadDesktop();
            }

            loaded = true;
        }
    }

    private static boolean forceBundledSdl3() {
        return Boolean.parseBoolean(System.getProperty("cfx.gamepadjni.forceBundledSdl3", "false"));
    }

    /**
     * Reuse SDL3 already mapped into this JVM (LWJGL) or load it from LWJGL's
     * native directory without extracting our bundled copy.
     *
     * <p>Launchers differ in where they put native libraries (flat, or in
     * per-version/arch sub-directories) and in whether they pre-extract them or
     * let LWJGL extract lazily, so discovery must not depend on one layout.</p>
     */
    private static boolean tryAdoptHostSdl3() {
        // 1) Ask LWJGL which SDL3 it uses. Authoritative, layout independent, and
        //    it makes LWJGL load Minecraft's own SDL3 if MC has not touched SDL yet.
        Path loaded = findLoadedLwjglSdl3();
        if (loaded != null) {
            System.load(loaded.toString());
            GamepadLog.info("[gamepad-jni] loaded host SDL3 from {}", loaded);
            return true;
        }

        // 2) Explicit override directory.
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

        // 3) Library-path style directories (search recursively: natives may sit in
        //    sub-directories such as <dir>/<lwjgl-version>/<arch>/).
        String[] dirProps = {
                System.getProperty("org.lwjgl.librarypath"),
                System.getProperty("org.lwjgl.librarypath.sdl"),
                System.getProperty("org.lwjgl.system.SharedLibraryExtractPath"),
        };
        for (String raw : dirProps) {
            Path sdl = findSdl3File(firstExistingDir(raw));
            if (sdl != null) {
                System.load(sdl.toString());
                GamepadLog.info("[gamepad-jni] loaded host SDL3 from {}", sdl);
                return true;
            }
        }
        for (String entry : splitPathList(System.getProperty("java.library.path"))) {
            Path sdl = findSdl3File(firstExistingDir(entry));
            if (sdl != null) {
                System.load(sdl.toString());
                GamepadLog.info("[gamepad-jni] loaded host SDL3 from {}", sdl);
                return true;
            }
        }

        if (isLwjglSdlClassPresent()) {
            GamepadLog.info("[gamepad-jni] LWJGL SDL class present but host native not found on library path");
        }
        return false;
    }

    /**
     * Ask LWJGL for the SDL3 shared library it uses. Invoking {@code getLibrary()}
     * loads the host native when it is not mapped yet; the JNI library then binds to
     * that same module by name, so we never pull in a second SDL3 copy.
     *
     * @return path of the host SDL3, or {@code null} when LWJGL is unavailable
     */
    private static Path findLoadedLwjglSdl3() {
        try {
            ClassLoader cl = NativeLibraryLoader.class.getClassLoader();
            Class<?> sdlClass = Class.forName("org.lwjgl.sdl.SDL", false, cl);
            Object library = sdlClass.getMethod("getLibrary").invoke(null);
            if (library == null) {
                return null;
            }
            Class<?> sharedLibrary = Class.forName("org.lwjgl.system.SharedLibrary", false, cl);
            Object raw = sharedLibrary.getMethod("getPath").invoke(library);
            if (raw instanceof String && !((String) raw).isEmpty()) {
                Path p = Paths.get((String) raw);
                if (Files.isRegularFile(p)) {
                    return p;
                }
            }
        } catch (Throwable ignored) {
            // LWJGL absent, or natives unavailable -> fall back to directory search
        }
        return null;
    }

    /** Split a {@code File.pathSeparator} separated list, skipping blanks. */
    private static String[] splitPathList(String value) {
        if (value == null || value.isEmpty()) {
            return new String[0];
        }
        String[] parts = value.split(java.util.regex.Pattern.quote(File.pathSeparator));
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                result.add(part.trim());
            }
        }
        return result.toArray(new String[0]);
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

    /**
     * Locate the SDL3 shared library inside {@code dir} (or a bounded number of
     * sub-directories below it).
     */
    private static Path findSdl3File(Path dir) {
        if (dir == null) {
            return null;
        }
        Path direct = findSdl3FileShallow(dir);
        return direct != null ? direct : findSdl3FileRecursive(dir, 4);
    }

    /** Look for the SDL3 library directly inside {@code dir}. */
    private static Path findSdl3FileShallow(Path dir) {
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
     * Bounded recursive search for the SDL3 library. Launchers may nest natives
     * (for example {@code <library path>/<lwjgl version>/<arch>/SDL3.dll}), which a
     * flat directory listing misses.
     */
    private static Path findSdl3FileRecursive(Path dir, int depth) {
        if (depth <= 0) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            List<Path> subDirs = new ArrayList<>();
            for (Path p : stream) {
                if (Files.isDirectory(p)) {
                    subDirs.add(p);
                } else if (isSdl3LibraryName(p.getFileName().toString())) {
                    return p;
                }
            }
            for (Path sub : subDirs) {
                Path hit = findSdl3FileShallow(sub);
                if (hit != null) {
                    return hit;
                }
                hit = findSdl3FileRecursive(sub, depth - 1);
                if (hit != null) {
                    return hit;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /** Whether a file name looks like the SDL3 shared library. */
    private static boolean isSdl3LibraryName(String name) {
        if (name == null || !name.contains("SDL3")) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.endsWith(".dll") || lower.endsWith(".dylib")
                || lower.endsWith(".so") || lower.contains(".so.");
    }

    /**
     * Desktop chain: prefer a host/LWJGL SDL3, otherwise extract SDL3 and the
     * JNI library from {@code native/<platform>/} in the classpath.
     */
    private static void loadDesktop() {
        boolean adoptHost = !forceBundledSdl3() && tryAdoptHostSdl3();
        hostSdl3 = adoptHost;

        Path nativeDir = findNativeDir(adoptHost);
        if (!adoptHost) {
            loadSDL3(nativeDir);
        } else {
            GamepadLog.info("[gamepad-jni] using host SDL3 (Minecraft/LWJGL); bundled SDL3 not loaded");
        }

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
     * Extract the JNI library only (used on Android, where SDL3 comes from the launcher).
     */
    private static Path extractFromClasspath(String platformDir) {
        return extractFromClasspath(platformDir, false);
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
