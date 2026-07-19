package com.ifels.gamepadjni;

/**
 * SDL3 gamepad axis indices.
 *
 * <p>Thumbstick axis values range from -32768 to 32767, centered near zero.
 * Trigger axis values range from 0 (released) to 32767 (fully pressed).</p>
 */
public enum GamepadAxis {
    INVALID(-1),
    LEFTX(0),
    LEFTY(1),
    RIGHTX(2),
    RIGHTY(3),
    LEFT_TRIGGER(4),
    RIGHT_TRIGGER(5);

    private final int value;

    GamepadAxis(int value) {
        this.value = value;
    }

    /** Get the native SDL3 enum value. */
    public int getValue() {
        return value;
    }

    /** Lookup axis by native value. */
    public static GamepadAxis fromValue(int value) {
        for (GamepadAxis axis : values()) {
            if (axis.value == value) return axis;
        }
        return INVALID;
    }
}
