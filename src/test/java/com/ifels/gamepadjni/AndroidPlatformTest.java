package com.ifels.gamepadjni;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-function tests for AndroidPlatform.
 *
 * <p>Detection and mapping are static methods that take explicit arguments, so
 * tests do not mock environment variables or process properties.</p>
 */
class AndroidPlatformTest {

    // ── detect(): os.version must start with android- (case-insensitive) ──

    @Test
    void detectLauncherAndroidVersion() {
        assertTrue(AndroidPlatform.detect("Android-14"));
        assertTrue(AndroidPlatform.detect("Android-13"));
        assertTrue(AndroidPlatform.detect("Android-10"));
    }

    @Test
    void detectIgnoresCaseOnOsVersionPrefix() {
        assertTrue(AndroidPlatform.detect("android-14"));
        assertTrue(AndroidPlatform.detect("ANDROID-14"));
        assertTrue(AndroidPlatform.detect("AnDrOiD-8.1.0"));
    }

    @Test
    void detectKernelVersionIsNotAndroid() {
        assertFalse(AndroidPlatform.detect("6.1.0"));
        assertFalse(AndroidPlatform.detect("10.0"));
        assertFalse(AndroidPlatform.detect("24.6.0"));
    }

    @Test
    void detectAndroidPrefixWithoutHyphenIsNotAndroid() {
        assertFalse(AndroidPlatform.detect("Android"));
        assertFalse(AndroidPlatform.detect("Android14"));
    }

    @Test
    void detectNullOrEmptyIsNotAndroid() {
        assertFalse(AndroidPlatform.detect(null));
        assertFalse(AndroidPlatform.detect(""));
    }

    // ── nativePlatformDir(): read os.arch, check each alias ───────────────

    @Test
    void nativePlatformDirMapsAliases() {
        assertDir("aarch64", "android-arm64-v8a");
        assertDir("arm64", "android-arm64-v8a");
        assertDir("arm", "android-armeabi-v7a");
        assertDir("armv7l", "android-armeabi-v7a");
        assertDir("armv8l", "android-armeabi-v7a");
        assertDir("x86_64", "android-x86_64");
        assertDir("amd64", "android-x86_64");
    }

    @Test
    void nativePlatformDirReturnsNullForUnsupportedArch() {
        assertNull(dirForArch("i386"));
        assertNull(dirForArch("i686"));
        assertNull(dirForArch(""));
        assertNull(dirForArch("riscv64"));
    }

    @Test
    void nativePlatformDirIsCaseInsensitive() {
        assertDir("AARCH64", "android-arm64-v8a");
        assertDir("AMD64", "android-x86_64");
    }

    // ── launcherSdlPath() ───────────────────────────────────────────────

    @Test
    void launcherSdlPathJoinsDirAndLibraryName() {
        assertEquals("/data/app/lib/arm64/libSDL3.so",
                AndroidPlatform.launcherSdlPath("/data/app/lib/arm64"));
    }

    @Test
    void launcherSdlPathIsNullWhenDirMissing() {
        assertNull(AndroidPlatform.launcherSdlPath(null));
        assertNull(AndroidPlatform.launcherSdlPath(""));
    }

    @Test
    void gamepadManagerIsAndroidMatchesAndroidPlatform() {
        assertEquals(AndroidPlatform.isAndroid(), GamepadManager.isAndroid());
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static void assertDir(String arch, String expected) {
        assertEquals(expected, dirForArch(arch), "os.arch=" + arch);
    }

    /** Temporarily override os.arch and restore it so tests do not leak. */
    private static String dirForArch(String arch) {
        String saved = System.getProperty("os.arch");
        try {
            System.setProperty("os.arch", arch);
            return AndroidPlatform.nativePlatformDir();
        } finally {
            if (saved == null) {
                System.clearProperty("os.arch");
            } else {
                System.setProperty("os.arch", saved);
            }
        }
    }
}
