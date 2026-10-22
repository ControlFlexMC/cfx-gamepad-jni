package com.ifels.gamepadjni;

/**
 * Native entry points for OS-level cursor visibility.
 *
 * <p>Deliberately <b>not</b> part of {@link GamepadJNI}: that class is the 1:1
 * SDL3 mapping, and the build's {@code verifyNativeSymbols} task requires every
 * method declared there to be exported by <b>every</b> packaged binary. A
 * Windows-only entry point in it would therefore force a rebuild of the
 * Android, macOS and Linux libraries as well. This class is outside that
 * contract, and callers go through {@link SystemCursor}, which degrades to
 * "unsupported" when the symbol is missing from the loaded library.</p>
 *
 * <p>Package-private on purpose: this is an implementation detail, not public
 * API. Use {@link SystemCursor}.</p>
 */
final class SystemCursorJNI {

    private SystemCursorJNI() {
    }

    /**
     * Show or hide the OS pointer image ({@code user32!ShowCursor}).
     *
     * <p>Only implemented on Windows. On other platforms the symbol exists but
     * is a no-op returning 0, and {@link SystemCursor#isSupported()} is false
     * so it is never called.</p>
     *
     * @param hide true to hide the pointer image, false to restore it
     * @return the {@code ShowCursor} display counter after the call
     */
    static native int showCursor(boolean hide);
}
