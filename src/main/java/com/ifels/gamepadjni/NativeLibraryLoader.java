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
 * Loads the SDL3 shared library and installs a backend for it.
 *
 * <p>Desktop: the caller chooses {@link Sdl3Source#HOST} (Minecraft/LWJGL SDL3
 * already in this JVM) or {@link Sdl3Source#BUNDLED} (the copy inside this JAR).
 * The two are exclusive — HOST does not fall back to BUNDLED.</p>
 *
 * <p>Android always loads the launcher APK's {@code libSDL3.so} and only extracts
 * {@code libgamepadjni.so} from the JAR. The {@link Sdl3Source} argument is
 * ignored.</p>
 *
 * <p>SDL3 is loaded first because both backends bind to whatever is already mapped
 * into the process. Then the JNI bridge is tried, and when it cannot load the JNA
 * backend binds the same SDL3. {@link BindingPolicy} decides how hard to try;
 * {@link BindingFailure} explains the outcome when neither works.</p>
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

    /** {@code "jni"}, {@code "jna"} or {@code "none"}, for diagnostics. */
    static String binding() {
        return Bindings.describe();
    }

    /**
     * Load SDL3, then install a backend for it: JNI first, JNA when that fails.
     *
     * <p>SDL3 is a precondition for both backends, so it is resolved once and the
     * resulting {@link SdlHandle} is handed to whichever backend ends up running.
     * The fallback lives entirely inside this method — no SDL call happens before it
     * completes, so a half-installed backend can never be observed.</p>
     *
     * <p>This is called automatically by {@link GamepadManager#initialize}.</p>
     *
     * @param source desktop choice of host vs bundled SDL3; ignored on Android
     * @throws UnsatisfiedLinkError if no backend could be installed; the message
     *                              carries both backends' reasons
     * @throws NullPointerException if {@code source} is null
     */
    static void load(Sdl3Source source) {
        if (loaded) return;

        synchronized (NativeLibraryLoader.class) {
            if (loaded) return;

            BindingMode mode = BindingPolicy.mode();
            SdlHandle sdl = loadSdl3(source);

            Throwable jniFailure = null;
            if (mode != BindingMode.JNA_ONLY) {
                try {
                    loadJni(sdl);
                    Bindings.set(JniBinding.INSTANCE);
                    loaded = true;
                    GamepadLog.info("[gamepad-jni] binding=jni");
                    return;
                } catch (Throwable t) {
                    jniFailure = t;
                    if (mode == BindingMode.JNI_ONLY) {
                        throw BindingFailure.of(mode, t, null, BindingPolicy.jnaPresent());
                    }
                    GamepadLog.warn("[gamepad-jni] JNI backend unavailable: {}", t.toString());
                }
            }

            if (!BindingPolicy.jnaPresent()) {
                throw BindingFailure.of(mode, jniFailure, null, false);
            }

            try {
                JnaGamepad.install(sdl);
                JnaUser32.install();
                Bindings.set(JnaBinding.INSTANCE);
                loaded = true;
                GamepadLog.info("[gamepad-jni] binding=jna (SDL3 from {})", sdl);
            } catch (Throwable t) {
                throw BindingFailure.of(mode, jniFailure, t, true);
            }
        }
    }

    /**
     * Resolve and load SDL3 for this process.
     *
     * @return the handle the JNA backend binds to; the JNI backend ignores everything
     *         but {@link SdlHandle#dir}
     */
    private static SdlHandle loadSdl3(Sdl3Source source) {
        Sdl3Source.LoadKind kind = Sdl3Source.resolveLoadKind(
                AndroidPlatform.isAndroid(), source);
        switch (kind) {
            case LAUNCHER: {
                hostSdl3 = true;
                GamepadLog.info("[gamepad-jni] Android: using launcher libSDL3 (Sdl3Source.{} ignored)",
                        source);
                logAndroidDiagnostics();
                SdlHandle sdl = loadLauncherSdl3();
                if (sdl == null) {
                    throw new UnsatisfiedLinkError(
                            "SDL3 unavailable on Android. Tried: "
                                    + AndroidPlatform.launcherSdlPath(System.getenv("POJAV_NATIVEDIR"))
                                    + " and System.loadLibrary(\"SDL3\"). "
                                    + "See the [gamepad-jni] Android diagnostic lines above.");
                }
                GamepadLog.info("[gamepad-jni] Android: loaded SDL3 from {}", sdl);
                return sdl;
            }
            case HOST: {
                Path adopted = tryAdoptHostSdl3();
                if (adopted == null) {
                    throw new UnsatisfiedLinkError(
                            "Sdl3Source.HOST requested but host SDL3 was not found. "
                                    + "Looked at LWJGL SDL.getLibrary(), cfx.gamepadjni.hostSdl3Dir / "
                                    + "CFX_HOST_SDL3_DIR, org.lwjgl.librarypath, and java.library.path. "
                                    + "Pass Sdl3Source.BUNDLED to use the copy inside the gamepad-jni JAR.");
                }
                hostSdl3 = true;
                GamepadLog.info("[gamepad-jni] using host SDL3 (Minecraft/LWJGL); bundled SDL3 not loaded");
                return SdlHandle.ofFile(null, adopted);
            }
            case BUNDLED: {
                hostSdl3 = false;
                Path nativeDir = findNativeDir(false);
                return loadSDL3(nativeDir);
            }
            default:
                throw new IllegalStateException("unknown SDL3 load kind: " + kind);
        }
    }

    /**
     * Load our own JNI bridge and leave it mapped, ready for {@link JniBinding}.
     *
     * @param sdl when it carries an extraction directory, the bridge is taken from
     *            there so the bundled case does not unpack the natives twice
     */
    private static void loadJni(SdlHandle sdl) {
        if (AndroidPlatform.isAndroid()) {
            loadAndroidJni();
            return;
        }
        Path dir = sdl != null ? sdl.dir : null;
        loadJNILibrary(dir != null ? dir : findNativeDir(true));
    }

    /**
     * Reuse SDL3 already mapped into this JVM (LWJGL) or load it from LWJGL's
     * native directory without extracting our bundled copy.
     *
     * <p>Launchers differ in where they put native libraries (flat, or in
     * per-version/arch sub-directories) and in whether they pre-extract them or
     * let LWJGL extract lazily, so discovery must not depend on one layout.</p>
     */
    private static Path tryAdoptHostSdl3() {
        // 1) Ask LWJGL which SDL3 it uses. Authoritative, layout independent, and
        //    it makes LWJGL load Minecraft's own SDL3 if MC has not touched SDL yet.
        Path loaded = findLoadedLwjglSdl3();
        if (loaded != null) {
            System.load(loaded.toString());
            GamepadLog.info("[gamepad-jni] loaded host SDL3 from {}", loaded);
            return loaded;
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
                return sdl;
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
                return sdl;
            }
        }
        for (String entry : splitPathList(System.getProperty("java.library.path"))) {
            Path sdl = findSdl3File(firstExistingDir(entry));
            if (sdl != null) {
                System.load(sdl.toString());
                GamepadLog.info("[gamepad-jni] loaded host SDL3 from {}", sdl);
                return sdl;
            }
        }

        if (isLwjglSdlClassPresent()) {
            GamepadLog.info("[gamepad-jni] LWJGL SDL class present but host native not found on library path");
        }
        return null;
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
     * Android: load only {@code libgamepadjni.so}. SDL3 already came from the
     * launcher APK in {@link #loadSdl3}, and the dynamic linker bound the JNI
     * library's {@code DT_NEEDED} to that very instance.
     *
     * <p>{@code CFX_LIB_PATH} only takes effect at this step:</p>
     */
    private static void loadAndroidJni() {
        Path override = libPathOverride();
        if (override != null) {
            Path jni = override.resolve(getJNIFileName());
            if (Files.exists(jni)) {
                loadJniFile(jni);
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
        loadJniFile(jni);
        GamepadLog.info("[gamepad-jni] Android: loaded JNI bridge from {}", jni);
    }

    /**
     * Load our own JNI bridge, converting every failure into a
     * {@link UnsatisfiedLinkError} so callers keep a single failure mode.
     *
     * <p>{@code Throwable} rather than {@code UnsatisfiedLinkError}: a native load
     * can also surface an {@code Error} (for example a {@code NoClassDefFoundError}
     * left pending by a {@code JNI_OnLoad} the JVM ran on this handle). Per spec
     * 5.3 any such failure must degrade to "no controller", never reach the game
     * entrypoint.</p>
     */
    private static void loadJniFile(Path jni) {
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
     * @return how SDL3 was resolved, or {@code null} if both strategies failed
     */
    private static SdlHandle loadLauncherSdl3() {
        String path = AndroidPlatform.launcherSdlPath(System.getenv("POJAV_NATIVEDIR"));
        if (path != null) {
            try {
                System.load(path);
                return SdlHandle.ofFile(null, Paths.get(path));
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
            // Resolved through java.library.path, so there is no path to hand to JNA.
            return SdlHandle.ofName(SDL_LIB_NAME);
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
     *
     * @return where it came from: the extracted file, or the bare library name when
     *         the platform resolved it through {@code java.library.path}
     */
    private static SdlHandle loadSDL3(Path nativeDir) {
        if (nativeDir != null) {
            Path sdlPath = findSdl3File(nativeDir);
            if (sdlPath != null) {
                System.load(sdlPath.toString());
                return SdlHandle.ofFile(nativeDir, sdlPath);
            }
        }

        try {
            System.loadLibrary(SDL_LIB_NAME);
            return SdlHandle.ofName(SDL_LIB_NAME);
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
