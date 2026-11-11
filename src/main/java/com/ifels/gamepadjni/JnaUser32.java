package com.ifels.gamepadjni;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;

import java.util.Locale;

/**
 * Windows cursor visibility through JNA, so the JNA backend keeps the feature that
 * used to live in {@code gamepadjni.dll}.
 *
 * <p>{@code ShowCursor} lives in {@code user32}, and a class can only be registered
 * against one library — hence a second, tiny binding class. Failing to bind is not
 * an error: the cursor simply stays visible, exactly as it did when an older native
 * library lacked the entry point.</p>
 *
 * <p>{@code ShowCursor} takes a 4-byte {@code BOOL}, which is what JNA maps a Java
 * {@code boolean} to, so no conversion is needed here.</p>
 */
final class JnaUser32 {

    private static volatile boolean installed;

    private JnaUser32() {
    }

    /**
     * Binds {@code user32!ShowCursor}.
     *
     * @return true when the entry point is available
     */
    static boolean install() {
        if (!isWindows()) {
            return false;
        }
        try {
            Native.register(JnaUser32.class, NativeLibrary.getInstance("user32"));
            installed = true;
        } catch (Throwable t) {
            GamepadLog.warn("[gamepad-jni] system cursor unavailable on the JNA backend ({}: {})",
                    t.getClass().getName(), t.getMessage());
            installed = false;
        }
        return installed;
    }

    static boolean isInstalled() {
        return installed;
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    static native int ShowCursor(boolean show);
}
