package com.ifels.gamepadjni;

import java.util.Locale;

/**
 * Resolves which backend to try and whether JNA is usable at all.
 *
 * <p>The property {@code cfx.gamepadjni.binding} wins over the environment
 * variable {@code CFX_GAMEPADJNI_BINDING}; both accept {@code jni}, {@code jna}
 * and {@code auto} (case-insensitive). An unrecognised value behaves like
 * {@code auto} and is reported once.</p>
 *
 * <p>{@link #mode(String, String)} and {@link #parse(String)} are pure so the
 * decision table is testable without touching process state.</p>
 */
final class BindingPolicy {

    static final String PROPERTY = "cfx.gamepadjni.binding";
    static final String ENV = "CFX_GAMEPADJNI_BINDING";

    private BindingPolicy() {
    }

    /**
     * @return the backend named by {@code raw}, or {@link BindingMode#AUTO} for
     *         null / blank / unrecognised values
     */
    static BindingMode parse(String raw) {
        String value = normalize(raw);
        if ("jni".equals(value)) {
            return BindingMode.JNI_ONLY;
        }
        if ("jna".equals(value)) {
            return BindingMode.JNA_ONLY;
        }
        return BindingMode.AUTO;
    }

    /** Whether {@code raw} is null, blank, or one of the three accepted values. */
    static boolean isKnown(String raw) {
        String value = normalize(raw);
        return value.isEmpty()
                || "auto".equals(value)
                || "jni".equals(value)
                || "jna".equals(value);
    }

    /** Reads the system property, then the environment variable, then {@code auto}. */
    static BindingMode mode() {
        String property = System.getProperty(PROPERTY);
        if (property != null && !property.trim().isEmpty() && !isKnown(property)) {
            GamepadLog.warn("[gamepad-jni] ignoring unknown {}={} (accepted: jni, jna, auto)",
                    PROPERTY, property);
        }
        return mode(property, System.getenv(ENV));
    }

    /**
     * Pure form of {@link #mode()}. A non-blank {@code property} always wins,
     * even when it is unrecognised — an invalid override must not silently fall
     * through to the environment.
     */
    static BindingMode mode(String property, String env) {
        String raw = property != null && !property.trim().isEmpty() ? property : env;
        return parse(raw);
    }

    /** Whether this process can load JNA classes through the library's class loader. */
    static boolean jnaPresent() {
        return jnaPresent(GamepadJNI.class.getClassLoader());
    }

    /**
     * Non-initialising presence check. {@code false} for the initializer: loading
     * {@code com.sun.jna.Native} runs its static block, which extracts
     * {@code jnidispatch} from the host's JNA jar. Probing must not do that.
     */
    static boolean jnaPresent(ClassLoader loader) {
        return canLoad("com.sun.jna.Native", loader)
                && canLoad("com.sun.jna.NativeLibrary", loader);
    }

    private static boolean canLoad(String className, ClassLoader loader) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
