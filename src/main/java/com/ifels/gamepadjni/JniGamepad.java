package com.ifels.gamepadjni;

/**
 * JNI declarations for the SDL3 Gamepad API — the target of every
 * {@code Java_com_ifels_gamepadjni_JniGamepad_*} export in {@code gamepad_jni.c}.
 *
 * <p>Package-private on purpose: {@link GamepadJNI} is the public surface, and
 * the backend that actually runs is chosen at load time. Do not call this class
 * directly from outside the package.</p>
 *
 * <p>Renaming this class renames every exported symbol in the native libraries.
 * The {@code verifyNativeSymbols} Gradle task scans this file for the exact
 * prefix, so the two cannot drift silently.</p>
 */
final class JniGamepad {

    private JniGamepad() {
    }

    public static native boolean SDL_Init(int flags);
    public static native boolean SDL_InitSubSystem(int flags);
    public static native void SDL_Quit();
    public static native int SDL_GetVersion();
    public static native void SDL_QuitSubSystem(int flags);
    public static native int SDL_WasInit(int flags);
    public static native String SDL_GetError();
    public static native void SDL_ClearError();
    public static native boolean SDL_SetHintWithPriority(String name, String value, int priority);
    public static native boolean SDL_SetHint(String name, String value);
    public static native int SDL_AddGamepadMapping(String mapping);
    public static native int SDL_AddGamepadMappingsFromFile(String file);
    public static native boolean SDL_ReloadGamepadMappings();
    public static native String SDL_GetGamepadMappingForID(int instanceId);
    public static native String SDL_GetGamepadMapping(long gamepad);
    public static native boolean SDL_SetGamepadMapping(int instanceId, String mapping);
    public static native boolean SDL_HasGamepad();
    public static native int[] SDL_GetGamepads();
    public static native boolean SDL_IsGamepad(int instanceId);
    public static native String SDL_GetGamepadNameForID(int instanceId);
    public static native String SDL_GetGamepadPathForID(int instanceId);
    public static native int SDL_GetGamepadPlayerIndexForID(int instanceId);
    public static native String SDL_GetGamepadGUIDForID(int instanceId);
    public static native short SDL_GetGamepadVendorForID(int instanceId);
    public static native short SDL_GetGamepadProductForID(int instanceId);
    public static native short SDL_GetGamepadProductVersionForID(int instanceId);
    public static native int SDL_GetGamepadTypeForID(int instanceId);
    public static native int SDL_GetRealGamepadTypeForID(int instanceId);
    public static native long SDL_OpenGamepad(int instanceId);
    public static native long SDL_GetGamepadFromID(int instanceId);
    public static native long SDL_GetGamepadFromPlayerIndex(int playerIndex);
    public static native void SDL_CloseGamepad(long gamepad);
    public static native int SDL_GetGamepadID(long gamepad);
    public static native String SDL_GetGamepadName(long gamepad);
    public static native String SDL_GetGamepadPath(long gamepad);
    public static native int SDL_GetGamepadType(long gamepad);
    public static native int SDL_GetRealGamepadType(long gamepad);
    public static native int SDL_GetGamepadPlayerIndex(long gamepad);
    public static native boolean SDL_SetGamepadPlayerIndex(long gamepad, int playerIndex);
    public static native short SDL_GetGamepadVendor(long gamepad);
    public static native short SDL_GetGamepadProduct(long gamepad);
    public static native short SDL_GetGamepadProductVersion(long gamepad);
    public static native short SDL_GetGamepadFirmwareVersion(long gamepad);
    public static native String SDL_GetGamepadSerial(long gamepad);
    public static native long SDL_GetGamepadSteamHandle(long gamepad);
    public static native int SDL_GetGamepadConnectionState(long gamepad);
    public static native int[] SDL_GetGamepadPowerInfo(long gamepad);
    public static native boolean SDL_GamepadConnected(long gamepad);
    public static native void SDL_UpdateGamepads();
    public static native void SDL_SetGamepadEventsEnabled(boolean enabled);
    public static native boolean SDL_GamepadEventsEnabled();
    public static native boolean SDL_GamepadHasAxis(long gamepad, int axis);
    public static native short SDL_GetGamepadAxis(long gamepad, int axis);
    public static native boolean SDL_GamepadHasButton(long gamepad, int button);
    public static native boolean SDL_GetGamepadButton(long gamepad, int button);
    public static native int SDL_GetGamepadButtonLabelForType(int type, int button);
    public static native int SDL_GetGamepadButtonLabel(long gamepad, int button);
    public static native int SDL_GetGamepadTypeFromString(String str);
    public static native String SDL_GetGamepadStringForType(int type);
    public static native int SDL_GetGamepadAxisFromString(String str);
    public static native String SDL_GetGamepadStringForAxis(int axis);
    public static native int SDL_GetGamepadButtonFromString(String str);
    public static native String SDL_GetGamepadStringForButton(int button);
    public static native boolean SDL_RumbleGamepad(long gamepad, int lowFrequencyRumble, int highFrequencyRumble, int durationMs);
    public static native boolean SDL_RumbleGamepadTriggers(long gamepad, int leftRumble, int rightRumble, int durationMs);
    public static native boolean SDL_SetGamepadLED(long gamepad, byte red, byte green, byte blue);
    public static native int SDL_GetNumGamepadTouchpads(long gamepad);
    public static native int SDL_GetNumGamepadTouchpadFingers(long gamepad, int touchpad);
    public static native float[] SDL_GetGamepadTouchpadFinger(long gamepad, int touchpad, int finger);
    public static native boolean SDL_GamepadHasSensor(long gamepad, int type);
    public static native boolean SDL_SetGamepadSensorEnabled(long gamepad, int type, boolean enabled);
    public static native boolean SDL_GamepadSensorEnabled(long gamepad, int type);
    public static native float SDL_GetGamepadSensorDataRate(long gamepad, int type);
    public static native float[] SDL_GetGamepadSensorData(long gamepad, int type, int numValues);
    public static native int getGCControllerCount();
}
