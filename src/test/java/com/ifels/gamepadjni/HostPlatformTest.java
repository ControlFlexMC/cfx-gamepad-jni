package com.ifels.gamepadjni;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostPlatformTest {

    @Test
    void detectAndroidLauncherVersion() {
        assertEquals(HostPlatform.ANDROID, HostPlatform.detect("Android-14"));
        assertEquals(HostPlatform.ANDROID, HostPlatform.detect("android-13"));
        assertEquals(HostPlatform.ANDROID, HostPlatform.detect("ANDROID-10"));
    }

    @Test
    void detectDesktopKernelVersion() {
        assertEquals(HostPlatform.DESKTOP, HostPlatform.detect("6.1.0"));
        assertEquals(HostPlatform.DESKTOP, HostPlatform.detect("10.0"));
        assertEquals(HostPlatform.DESKTOP, HostPlatform.detect("24.6.0"));
        assertEquals(HostPlatform.DESKTOP, HostPlatform.detect(null));
        assertEquals(HostPlatform.DESKTOP, HostPlatform.detect(""));
    }

    @Test
    void detectDoesNotReturnIosYet() {
        assertEquals(HostPlatform.DESKTOP, HostPlatform.detect("iOS-18"));
        assertEquals(HostPlatform.DESKTOP, HostPlatform.detect("Darwin"));
        assertFalse(HostPlatform.detect("Android-14").isIos());
    }

    @Test
    void currentMatchesAndroidPlatform() {
        assertEquals(AndroidPlatform.isAndroid(), HostPlatform.current().isAndroid());
        assertEquals(HostPlatform.current(), GamepadManager.hostPlatform());
        assertEquals(HostPlatform.current().isAndroid(), GamepadManager.isAndroid());
        assertEquals(HostPlatform.current().isIos(), GamepadManager.isIos());
    }

    @Test
    void desktopHelpers() {
        assertTrue(HostPlatform.DESKTOP.isDesktop());
        assertFalse(HostPlatform.ANDROID.isDesktop());
        assertFalse(HostPlatform.IOS.isDesktop());
        assertTrue(HostPlatform.IOS.isIos());
    }
}
