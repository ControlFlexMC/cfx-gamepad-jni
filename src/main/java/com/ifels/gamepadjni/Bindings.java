package com.ifels.gamepadjni;

/**
 * Holds the {@link GamepadBinding} installed for this process.
 *
 * <p>{@link NativeLibraryLoader} installs exactly one binding during
 * {@link GamepadManager#initialize}, and every {@link GamepadJNI} call reads it
 * from here. Keeping the field in one place is what lets the public API stay
 * static while the implementation changes underneath.</p>
 *
 * <p>Before a binding is installed — and after a failed initialization — {@link #b()}
 * throws {@link UnsatisfiedLinkError}. That is deliberate: callers such as
 * ControlFlex catch that type around {@code GamepadJNI} calls, and a
 * {@link NullPointerException} would slip through them.</p>
 */
final class Bindings {

    private static volatile GamepadBinding current;

    private Bindings() {
    }

    /**
     * @return the installed binding
     * @throws UnsatisfiedLinkError when {@link GamepadManager#initialize} has not
     *                              succeeded yet
     */
    static GamepadBinding b() {
        GamepadBinding binding = current;
        if (binding == null) {
            throw new UnsatisfiedLinkError(
                    "[gamepad-jni] no binding installed; call GamepadManager.initialize() first");
        }
        return binding;
    }

    static void set(GamepadBinding binding) {
        current = binding;
    }

    /** Drops the binding. Only tests use this; a real process installs one. */
    static void clear() {
        current = null;
    }

    static boolean isJni() {
        return current == JniBinding.INSTANCE;
    }

    /** {@code "jni"}, {@code "jna"} or {@code "none"}, for logging. */
    static String describe() {
        GamepadBinding binding = current;
        if (binding == null) {
            return "none";
        }
        return binding == JniBinding.INSTANCE ? "jni" : "jna";
    }
}
