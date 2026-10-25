package com.ifels.gamepadjni;

/**
 * Native bridge for capturing SDL's own log output.
 *
 * <p>SDL filters most categories at ERROR by default, so the DEBUG lines that
 * explain a silently degraded backend (udev/libudev backend selection, a
 * missing libusb) never reach any logger. {@link #installLogCapture()} lowers
 * only {@code SDL_LOG_CATEGORY_INPUT} to DEBUG and stores each line in a native
 * ring buffer; {@link #drainLogs(int)} hands them to a Java thread.</p>
 *
 * <p>The callback never calls into the JVM: SDL may invoke it on a non-Java
 * thread and while holding its internal log mutex, so the native side only
 * copies bytes under its own mutex. Draining is cheap and allocation-free
 * until lines are actually pending.</p>
 *
 * <p>Kept out of {@link GamepadJNI} on purpose: {@code verifyNativeSymbols}
 * requires every {@code GamepadJNI} entry point to exist in every packaged
 * binary, and not all platform natives are rebuilt together. Callers must
 * therefore catch {@link UnsatisfiedLinkError}; the capture is diagnostics
 * only, and its absence must never affect initialization.</p>
 */
public final class SdlLogJNI {

    private SdlLogJNI() {
    }

    /**
     * Install the SDL log capture and lower the input category to DEBUG.
     *
     * <p>Idempotent. Call before SDL initialization to capture backend
     * selection messages.</p>
     *
     * @return true if the capture is installed
     */
    public static native boolean installLogCapture();

    /**
     * Drain up to {@code maxLines} captured messages.
     *
     * <p>Each entry is formatted as {@code [SDL][input][DEBUG] message}.</p>
     *
     * @param maxLines maximum number of lines to return
     * @return pending lines, or {@code null} when none are queued
     */
    public static native String[] drainLogs(int maxLines);

    /**
     * Restore the previous SDL log output function and input log priority.
     */
    public static native void uninstallLogCapture();
}
