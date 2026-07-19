package com.ifels.gamepadjni;

/**
 * SDL3 power state for gamepad battery.
 */
public enum PowerState {
    ERROR(-1),
    UNKNOWN(0),
    ON_BATTERY(1),
    NO_BATTERY(2),
    CHARGING(3),
    CHARGED(4);

    private final int value;

    PowerState(int value) {
        this.value = value;
    }

    /** Get the native SDL3 enum value. */
    public int getValue() {
        return value;
    }

    /** Lookup power state by native value. */
    public static PowerState fromValue(int value) {
        for (PowerState state : values()) {
            if (state.value == value) return state;
        }
        return UNKNOWN;
    }
}
