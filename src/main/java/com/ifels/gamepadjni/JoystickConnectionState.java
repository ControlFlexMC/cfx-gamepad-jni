package com.ifels.gamepadjni;

/**
 * SDL3 joystick connection state.
 */
public enum JoystickConnectionState {
    INVALID(-1),
    UNKNOWN(0),
    WIRED(1),
    WIRELESS(2);

    private final int value;

    JoystickConnectionState(int value) {
        this.value = value;
    }

    /** Get the native SDL3 enum value. */
    public int getValue() {
        return value;
    }

    /** Lookup connection state by native value. */
    public static JoystickConnectionState fromValue(int value) {
        for (JoystickConnectionState state : values()) {
            if (state.value == value) return state;
        }
        return INVALID;
    }
}
