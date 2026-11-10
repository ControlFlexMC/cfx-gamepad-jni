package com.ifels.gamepadjni;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BindingFailureTest {

    private static final Throwable SAC = new UnsatisfiedLinkError(
            "C:\\Temp\\gamepadjni-1\\gamepadjni.dll: An Application Control policy has blocked this file");

    @Test
    void jnaAbsentNamesTheMissingJar() {
        String msg = BindingFailure.message(BindingMode.AUTO, new UnsatisfiedLinkError("jni boom"), null, false, true);
        assertTrue(msg.startsWith("[gamepad-jni] no usable gamepad backend (binding=auto)"), msg);
        assertTrue(msg.contains("JNI: java.lang.UnsatisfiedLinkError: jni boom"), msg);
        assertTrue(msg.contains("JNA: unavailable"), msg);
        assertTrue(msg.contains("ships no JNA"), msg);
    }

    @Test
    void jnaFailureCarriesItsOwnReason() {
        String msg = BindingFailure.message(BindingMode.AUTO, new UnsatisfiedLinkError("jni boom"),
                new UnsatisfiedLinkError("register boom"), true, true);
        assertTrue(msg.contains("JNA: java.lang.UnsatisfiedLinkError: register boom"), msg);
        assertFalse(msg.contains("unavailable"), msg);
    }

    @Test
    void jniOnlyModeNeverMentionsJna() {
        String msg = BindingFailure.message(BindingMode.JNI_ONLY, new UnsatisfiedLinkError("jni boom"), null, true, true);
        assertTrue(msg.contains("binding=jni"), msg);
        assertTrue(msg.contains("JNI: java.lang.UnsatisfiedLinkError: jni boom"), msg);
        assertFalse(msg.contains("JNA:"), msg);
    }

    @Test
    void sacFailureAddsTheHint() {
        String msg = BindingFailure.message(BindingMode.AUTO, SAC, new UnsatisfiedLinkError("x"), true, true);
        assertTrue(msg.contains("Smart App Control"), msg);
    }

    @Test
    void nonWindowsAndNoJnaStillExplains() {
        String msg = BindingFailure.message(BindingMode.AUTO, new UnsatisfiedLinkError("jni boom"), null, false, false);
        assertFalse(msg.contains("Smart App Control"), msg);
        assertTrue(msg.contains("JNA: unavailable"), msg);
    }

    @Test
    void ofReturnsUnsatisfiedLinkError() {
        UnsatisfiedLinkError e = BindingFailure.of(BindingMode.AUTO, new UnsatisfiedLinkError("a"), null, false);
        assertTrue(e.getMessage().contains("no usable gamepad backend"));
        assertEquals(UnsatisfiedLinkError.class, e.getClass());
    }
}
