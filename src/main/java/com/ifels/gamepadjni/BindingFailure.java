package com.ifels.gamepadjni;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the {@link UnsatisfiedLinkError} raised when no backend could be
 * installed.
 *
 * <p>A single "failed to load native library" line hides which half broke:
 * callers cannot tell a locked-down Windows install (Smart App Control blocking
 * the unsigned {@code gamepadjni.dll}) from a Minecraft version that simply
 * ships no JNA. The message therefore carries both backends' reasons, one per
 * indented line, plus a hint when the cause is recognisable.</p>
 *
 * <p>{@link #message} is pure so the three shapes are unit-tested without a
 * native library.</p>
 */
final class BindingFailure {

    private static final String SAC_MARKER = "Application Control policy";

    private BindingFailure() {
    }

    /** Builds the error using this process's operating system. */
    static UnsatisfiedLinkError of(BindingMode mode, Throwable jniFailure,
                                   Throwable jnaFailure, boolean jnaPresent) {
        return new UnsatisfiedLinkError(
                message(mode, jniFailure, jnaFailure, jnaPresent, isWindows()));
    }

    /**
     * @param mode       which backends were attempted
     * @param jniFailure why the JNI load failed, or null when JNI was not tried
     * @param jnaFailure why JNA registration failed, or null when JNA is absent
     * @param jnaPresent whether JNA classes were on the classpath
     * @param windows    whether this is a Windows host, for the hint wording
     */
    static String message(BindingMode mode, Throwable jniFailure, Throwable jnaFailure,
                          boolean jnaPresent, boolean windows) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("[gamepad-jni] no usable gamepad backend (binding=")
                .append(label(mode)).append(')');

        if (mode != BindingMode.JNA_ONLY) {
            sb.append("\n  JNI: ").append(describe(jniFailure));
        }
        if (mode != BindingMode.JNI_ONLY) {
            if (!jnaPresent) {
                sb.append("\n  JNA: unavailable - com.sun.jna.Native not on the classpath")
                        .append(" (this Minecraft version ships no JNA)");
            } else {
                sb.append("\n  JNA: ").append(describe(jnaFailure));
            }
        }

        List<String> hints = new ArrayList<>(2);
        if (jniFailure != null && contains(jniFailure.getMessage(), SAC_MARKER)) {
            hints.add("Smart App Control on Windows 11 is blocking the unsigned gamepadjni.dll");
        }
        if (mode != BindingMode.JNI_ONLY && !jnaPresent && windows) {
            hints.add("this Minecraft version has no JNA, so only the JNI backend is possible");
        }
        if (!hints.isEmpty()) {
            sb.append("\n  hint: ").append(join(hints));
        }
        return sb.toString();
    }

    private static String label(BindingMode mode) {
        switch (mode) {
            case JNI_ONLY:
                return "jni";
            case JNA_ONLY:
                return "jna";
            default:
                return "auto";
        }
    }

    private static String describe(Throwable t) {
        if (t == null) {
            return "not attempted";
        }
        String message = t.getMessage();
        return message == null || message.isEmpty()
                ? t.getClass().getName()
                : t.getClass().getName() + ": " + message;
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.contains(needle);
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(part);
        }
        return sb.toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
