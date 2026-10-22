package com.ifels.gamepadjni;

import java.util.Locale;

/**
 * Shows or hides the OS pointer image without grabbing the pointer.
 *
 * <p>Semantically GLFW's {@code GLFW_CURSOR_HIDDEN}: the pointer keeps its
 * position, {@code GetCursorPos}/{@code SetCursorPos} and the host's absolute
 * mouse coordinates keep working, and only the pointer's picture is taken away.
 * That distinction is the whole point on the LWJGL2 ports -- the only other
 * lever there, {@code Mouse.setGrabbed}, also relativises the pointer, which
 * freezes the coordinate a virtual cursor is built on.</p>
 *
 * <p><b>Why JNI and not JNA.</b> The 1.7.10 client ships no JNA on its launcher
 * classpath (1.8.9 ships 3.4.0, 1.12.2 ships 4.4.0, 1.7.10 ships none --
 * measured from both version jsons), so a reflective JNA call degrades to
 * "cursor stays visible" there. Shipping a JNA copy just for one call costs
 * roughly 1.8 MB per build; this library is bundled by the mod anyway.</p>
 *
 * <p><b>Windows only.</b> macOS and Linux have no implementation: callers see
 * {@link #isSupported()} false and keep the status quo (the system cursor stays
 * visible next to the drawn one). Android is not a desktop host either.</p>
 *
 * <p><b>Counter discipline is the caller's job.</b> {@code ShowCursor} moves a
 * display counter, so call {@link #setHidden(boolean)} on state <i>changes</i>
 * only. Calling it every frame drives the counter deeply negative and leaves the
 * pointer hidden for that many matching calls on the way back.</p>
 */
public final class SystemCursor {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    /** Set after the first failure, so the warning is printed once and never retried. */
    private static boolean unavailable;

    private SystemCursor() {
    }

    /**
     * Whether this host can hide the OS pointer at all.
     *
     * <p>Pure platform check -- does not load natives and does not prove the
     * loaded library actually exports the entry point (see
     * {@link #setHidden(boolean)} for that answer).</p>
     */
    public static boolean isSupported() {
        return IS_WINDOWS && HostPlatform.current().isDesktop();
    }

    /**
     * Hides or restores the OS pointer image. Call only on a state change.
     *
     * @param hidden true to take the pointer's image away, false to put it back
     * @return the {@code ShowCursor} display counter after the call, or
     *         {@code null} when the cursor cannot be controlled here (not
     *         Windows, no desktop host, or the loaded native library does not
     *         carry the entry point)
     */
    public static Integer setHidden(boolean hidden) {
        if (!isSupported() || unavailable) {
            return null;
        }
        try {
            return Integer.valueOf(SystemCursorJNI.showCursor(hidden));
        } catch (UnsatisfiedLinkError e) {
            // Older jar, or a Windows binary that was never rebuilt with the entry
            // point: report once, then stop trying. Losing the cursor feature must
            // never cost the caller anything else.
            unavailable = true;
            GamepadLog.warn("[gamepad-jni] this native library has no system-cursor entry point ({}); "
                    + "the OS cursor will stay visible", e.getMessage());
            return null;
        } catch (Throwable t) {
            unavailable = true;
            GamepadLog.warn("[gamepad-jni] system cursor control failed ({}: {}); "
                    + "the OS cursor will stay visible", t.getClass().getName(), t.getMessage());
            return null;
        }
    }
}
