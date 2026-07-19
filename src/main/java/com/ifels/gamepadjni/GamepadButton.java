package com.ifels.gamepadjni;

/**
 * SDL3 gamepad button indices.
 *
 * <p>Face buttons use south/east/west/north naming which maps to
 * different labels depending on controller type:
 * Xbox: A/B/X/Y, PlayStation: Cross/Circle/Square/Triangle,
 * Nintendo Switch: B/A/Y/X.</p>
 */
public enum GamepadButton {
    INVALID(-1),
    SOUTH(0),           // Xbox A, PS Cross, Switch B
    EAST(1),            // Xbox B, PS Circle, Switch A
    WEST(2),            // Xbox X, PS Square, Switch Y
    NORTH(3),           // Xbox Y, PS Triangle, Switch X
    BACK(4),
    GUIDE(5),
    START(6),
    LEFT_STICK(7),
    RIGHT_STICK(8),
    LEFT_SHOULDER(9),
    RIGHT_SHOULDER(10),
    DPAD_UP(11),
    DPAD_DOWN(12),
    DPAD_LEFT(13),
    DPAD_RIGHT(14),
    MISC1(15),          // Xbox Share, PS5 Mic, Switch Capture
    RIGHT_PADDLE1(16),
    LEFT_PADDLE1(17),
    RIGHT_PADDLE2(18),
    LEFT_PADDLE2(19),
    TOUCHPAD(20),       // PS4/PS5 touchpad button
    MISC2(21),
    MISC3(22),
    MISC4(23),
    MISC5(24),
    MISC6(25);

    private final int value;

    GamepadButton(int value) {
        this.value = value;
    }

    /** Get the native SDL3 enum value. */
    public int getValue() {
        return value;
    }

    /** Lookup button by native value. */
    public static GamepadButton fromValue(int value) {
        for (GamepadButton btn : values()) {
            if (btn.value == value) return btn;
        }
        return INVALID;
    }
}
