package com.ifels.gamepadjni;

/**
 * Where SDL3 is loaded from. The caller must choose on desktop; Android always
 * uses the launcher APK's {@code libSDL3.so} and ignores this value.
 */
public enum Sdl3Source {

    /**
     * Share SDL3 already mapped into this JVM (Minecraft / LWJGL).
     *
     * <p>Desktop only. If that copy cannot be found, initialization fails —
     * there is no fallback to the bundled library.</p>
     */
    HOST,

    /**
     * Load the SDL3 copy packaged inside the gamepad-jni JAR.
     *
     * <p>Desktop only. Host / LWJGL SDL3 is never opened in this mode.</p>
     */
    BUNDLED;

    /**
     * Resolved load path after applying the Android override.
     *
     * <p>Not part of the public caller API; {@link GamepadManager#initialize}
     * takes {@link Sdl3Source}.</p>
     */
    enum LoadKind {
        LAUNCHER,
        HOST,
        BUNDLED
    }

    /**
     * @param android   whether this process is the Android game JVM
     * @param requested caller's desktop choice; ignored when {@code android}
     * @throws NullPointerException if {@code requested} is null
     */
    static LoadKind resolveLoadKind(boolean android, Sdl3Source requested) {
        if (requested == null) {
            throw new NullPointerException("sdl3 source");
        }
        if (android) {
            return LoadKind.LAUNCHER;
        }
        return requested == HOST ? LoadKind.HOST : LoadKind.BUNDLED;
    }
}
