package com.ifels.gamepadjni;

/**
 * SDL3 gamepad button labels.
 *
 * <p>Face button labels vary by controller type:
 * Xbox uses A/B/X/Y, PlayStation uses Cross/Circle/Square/Triangle.</p>
 */
public enum GamepadButtonLabel {
    UNKNOWN(0),
    A(1),
    B(2),
    X(3),
    Y(4),
    CROSS(5),
    CIRCLE(6),
    SQUARE(7),
    TRIANGLE(8);

    private final int value;

    GamepadButtonLabel(int value) {
        this.value = value;
    }

    /** Get the native SDL3 enum value. */
    public int getValue() {
        return value;
    }

    /** Lookup label by native value. */
    public static GamepadButtonLabel fromValue(int value) {
        for (GamepadButtonLabel label : values()) {
            if (label.value == value) return label;
        }
        return UNKNOWN;
    }
}
