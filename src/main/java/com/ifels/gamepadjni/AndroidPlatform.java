package com.ifels.gamepadjni;

import java.util.Locale;

/**
 * Android platform detection and packaging-path mapping.
 *
 * <p>Android Minecraft: Java Edition launchers run the game in a second JVM in
 * the same process and set {@code os.version=Android-<release>}. A value that
 * starts with {@code Android-} (case-insensitive) is treated as Android.
 * {@code os.name} is not part of detection; it appears in diagnostic logs only.</p>
 *
 * <p>This class intentionally uses no logging and no I/O so unit tests can drive
 * {@link #detect} with explicit arguments.</p>
 */
public final class AndroidPlatform {

    /** On Android, SDL3 comes from the launcher APK native-library directory. */
    public static final String SDL3_LIBRARY_NAME = "libSDL3.so";

    private static final String LAUNCHER_OS_VERSION_PREFIX = "android-";

    private static final boolean ANDROID = detect(
            System.getProperty("os.version", ""));

    private AndroidPlatform() {
    }

    /** Whether this process is running in a game JVM provided by an Android launcher. */
    public static boolean isAndroid() {
        return ANDROID;
    }

    /**
     * Gates only on the launcher-overridden {@code os.version} prefix.
     *
     * @param osVersion {@code os.version}; launchers set {@code Android-<release>}
     */
    static boolean detect(String osVersion) {
        if (osVersion == null) {
            return false;
        }
        return osVersion.toLowerCase(Locale.ROOT).startsWith(LAUNCHER_OS_VERSION_PREFIX);
    }

    /**
     * Platform directory name inside the JAR; same contract as the packaging
     * allowlist in {@code build.gradle}.
     *
     * @return e.g. {@code "android-arm64-v8a"}; unsupported ABI returns {@code null}
     */
    public static String nativePlatformDir() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "android-arm64-v8a";
        }
        if (arch.equals("arm") || arch.equals("armv7l") || arch.equals("armv8l")) {
            return "android-armeabi-v7a";
        }
        if (arch.equals("x86_64") || arch.equals("amd64")) {
            return "android-x86_64";
        }
        return null;
    }

    /**
     * Absolute path of the launcher-provided SDL3.
     *
     * @return e.g. {@code "<POJAV_NATIVEDIR>/libSDL3.so"}; {@code null} if it cannot be built
     */
    public static String launcherSdlPath(String pojavNativeDir) {
        if (pojavNativeDir == null || pojavNativeDir.isEmpty()) {
            return null;
        }
        return pojavNativeDir + "/" + SDL3_LIBRARY_NAME;
    }
}
