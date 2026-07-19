package com.ifels.gamepadjni;

/**
 * SDL3 standard gamepad types.
 *
 * <p>This does not necessarily map to first-party controllers;
 * third-party controllers can report as these types.</p>
 */
public enum GamepadType {
    UNKNOWN(0),
    STANDARD(1),
    XBOX360(2),
    XBOXONE(3),
    PS3(4),
    PS4(5),
    PS5(6),
    NINTENDO_SWITCH_PRO(7),
    NINTENDO_SWITCH_JOYCON_LEFT(8),
    NINTENDO_SWITCH_JOYCON_RIGHT(9),
    NINTENDO_SWITCH_JOYCON_PAIR(10),
    GAMECUBE(11);

    private final int value;

    GamepadType(int value) {
        this.value = value;
    }

    /** Get the native SDL3 enum value. */
    public int getValue() {
        return value;
    }

    /** Lookup type by native value. */
    public static GamepadType fromValue(int value) {
        for (GamepadType type : values()) {
            if (type.value == value) return type;
        }
        return UNKNOWN;
    }
}
