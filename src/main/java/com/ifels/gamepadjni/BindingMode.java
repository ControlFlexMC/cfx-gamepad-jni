package com.ifels.gamepadjni;

/**
 * Which backend {@link GamepadJNI} should try.
 *
 * <p>Parsed from {@code -Dcfx.gamepadjni.binding} / {@code CFX_GAMEPADJNI_BINDING}
 * by {@link BindingPolicy}. {@link #AUTO} is the default and the only mode that
 * falls back: it tries JNI first, then JNA.</p>
 */
enum BindingMode {

    /** Try JNI, fall back to JNA when JNA is on the classpath. */
    AUTO,

    /** Only JNI. No fallback — used to reproduce a failing JNI load. */
    JNI_ONLY,

    /** Only JNA. No JNI attempt — used to exercise the fallback path on purpose. */
    JNA_ONLY
}
