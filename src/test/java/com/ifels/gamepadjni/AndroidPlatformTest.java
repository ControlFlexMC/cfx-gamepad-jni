package com.ifels.gamepadjni;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AndroidPlatform 的纯函数测试。
 *
 * <p>判定与映射都做成接受显式入参的静态方法，因此无需模拟环境变量或进程属性。</p>
 */
class AndroidPlatformTest {

    private static final String HOTSPOT = "OpenJDK 64-Bit Server VM";

    // ── detect()：四条信号任意一条命中即 Android ──────────────────────────

    @Test
    void detectPojavNativeDirSignal() {
        assertTrue(AndroidPlatform.detect(
                "Linux", "6.1.0", HOTSPOT, "/data/app/lib/arm64", false));
    }

    @Test
    void detectAndroidOsVersionSignal() {
        assertTrue(AndroidPlatform.detect(
                "Linux", "Android-13", HOTSPOT, null, false));
    }

    @Test
    void detectSystemBuildPropSignal() {
        assertTrue(AndroidPlatform.detect(
                "Linux", "6.1.0", HOTSPOT, null, true));
    }

    @Test
    void detectDalvikVmNameSignal() {
        assertTrue(AndroidPlatform.detect(
                "Linux", "6.1.0", "dalvik", null, false));
        assertTrue(AndroidPlatform.detect(
                "Linux", "6.1.0", "Dalvik", null, false));
    }

    // ── detect()：桌面必须零误判 ────────────────────────────────────────

    @Test
    void detectDesktopLinuxIsNotAndroid() {
        assertFalse(AndroidPlatform.detect(
                "Linux", "6.1.0", HOTSPOT, null, false));
    }

    @Test
    void detectDesktopMacIsNotAndroid() {
        assertFalse(AndroidPlatform.detect(
                "Mac OS X", "14.5", HOTSPOT, null, false));
    }

    @Test
    void detectDesktopWindowsIsNotAndroid() {
        assertFalse(AndroidPlatform.detect(
                "Windows 11", "10.0", HOTSPOT, null, false));
    }

    @Test
    void detectEmptyPropertiesIsNotAndroid() {
        assertFalse(AndroidPlatform.detect("", "", "", null, false));
    }

    @Test
    void detectNullVmNameIsNotAndroid() {
        assertFalse(AndroidPlatform.detect("Linux", "6.1.0", null, null, false));
    }

    // ── nativePlatformDir()：读 os.arch，逐个别名验证 ────────────────────

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

    // ── helpers ─────────────────────────────────────────────────────────

    private static void assertDir(String arch, String expected) {
        assertEquals(expected, dirForArch(arch), "os.arch=" + arch);
    }

    /** 临时改写 os.arch 并还原，避免测试间互相污染。 */
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
