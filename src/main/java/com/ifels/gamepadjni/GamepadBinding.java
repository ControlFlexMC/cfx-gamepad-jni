package com.ifels.gamepadjni;

/**
 * One installed backend for the SDL3 Gamepad API.
 *
 * <p>{@link Bindings} holds exactly one of these for the lifetime of the
 * process: {@link JniBinding} when the native bridge loaded, {@link JnaBinding}
 * when it did not and the host ships JNA. {@link GamepadJNI} forwards to it, so
 * every caller sees the same API either way.</p>
 *
 * <p>Both implementations are checked against this interface at compile time and
 * against {@link GamepadJNI} by {@code GamepadFacadeTest}, which is what keeps the
 * three method sets from drifting apart.</p>
 */
interface GamepadBinding {

    boolean SDL_Init(int flags);
    boolean SDL_InitSubSystem(int flags);
    void SDL_Quit();
    int SDL_GetVersion();
    void SDL_QuitSubSystem(int flags);
    int SDL_WasInit(int flags);
    String SDL_GetError();
    void SDL_ClearError();
    boolean SDL_SetHintWithPriority(String name, String value, int priority);
    boolean SDL_SetHint(String name, String value);
    int SDL_AddGamepadMapping(String mapping);
    int SDL_AddGamepadMappingsFromFile(String file);
    boolean SDL_ReloadGamepadMappings();
    String SDL_GetGamepadMappingForID(int instanceId);
    String SDL_GetGamepadMapping(long gamepad);
    boolean SDL_SetGamepadMapping(int instanceId, String mapping);
    boolean SDL_HasGamepad();
    int[] SDL_GetGamepads();
    boolean SDL_IsGamepad(int instanceId);
    String SDL_GetGamepadNameForID(int instanceId);
    String SDL_GetGamepadPathForID(int instanceId);
    int SDL_GetGamepadPlayerIndexForID(int instanceId);
    String SDL_GetGamepadGUIDForID(int instanceId);
    short SDL_GetGamepadVendorForID(int instanceId);
    short SDL_GetGamepadProductForID(int instanceId);
    short SDL_GetGamepadProductVersionForID(int instanceId);
    int SDL_GetGamepadTypeForID(int instanceId);
    int SDL_GetRealGamepadTypeForID(int instanceId);
    long SDL_OpenGamepad(int instanceId);
    long SDL_GetGamepadFromID(int instanceId);
    long SDL_GetGamepadFromPlayerIndex(int playerIndex);
    void SDL_CloseGamepad(long gamepad);
    int SDL_GetGamepadID(long gamepad);
    String SDL_GetGamepadName(long gamepad);
    String SDL_GetGamepadPath(long gamepad);
    int SDL_GetGamepadType(long gamepad);
    int SDL_GetRealGamepadType(long gamepad);
    int SDL_GetGamepadPlayerIndex(long gamepad);
    boolean SDL_SetGamepadPlayerIndex(long gamepad, int playerIndex);
    short SDL_GetGamepadVendor(long gamepad);
    short SDL_GetGamepadProduct(long gamepad);
    short SDL_GetGamepadProductVersion(long gamepad);
    short SDL_GetGamepadFirmwareVersion(long gamepad);
    String SDL_GetGamepadSerial(long gamepad);
    long SDL_GetGamepadSteamHandle(long gamepad);
    int SDL_GetGamepadConnectionState(long gamepad);
    int[] SDL_GetGamepadPowerInfo(long gamepad);
    boolean SDL_GamepadConnected(long gamepad);
    void SDL_UpdateGamepads();
    void SDL_SetGamepadEventsEnabled(boolean enabled);
    boolean SDL_GamepadEventsEnabled();
    boolean SDL_GamepadHasAxis(long gamepad, int axis);
    short SDL_GetGamepadAxis(long gamepad, int axis);
    boolean SDL_GamepadHasButton(long gamepad, int button);
    boolean SDL_GetGamepadButton(long gamepad, int button);
    int SDL_GetGamepadButtonLabelForType(int type, int button);
    int SDL_GetGamepadButtonLabel(long gamepad, int button);
    int SDL_GetGamepadTypeFromString(String str);
    String SDL_GetGamepadStringForType(int type);
    int SDL_GetGamepadAxisFromString(String str);
    String SDL_GetGamepadStringForAxis(int axis);
    int SDL_GetGamepadButtonFromString(String str);
    String SDL_GetGamepadStringForButton(int button);
    boolean SDL_RumbleGamepad(long gamepad, int lowFrequencyRumble, int highFrequencyRumble, int durationMs);
    boolean SDL_RumbleGamepadTriggers(long gamepad, int leftRumble, int rightRumble, int durationMs);
    boolean SDL_SetGamepadLED(long gamepad, byte red, byte green, byte blue);
    int SDL_GetNumGamepadTouchpads(long gamepad);
    int SDL_GetNumGamepadTouchpadFingers(long gamepad, int touchpad);
    float[] SDL_GetGamepadTouchpadFinger(long gamepad, int touchpad, int finger);
    boolean SDL_GamepadHasSensor(long gamepad, int type);
    boolean SDL_SetGamepadSensorEnabled(long gamepad, int type, boolean enabled);
    boolean SDL_GamepadSensorEnabled(long gamepad, int type);
    float SDL_GetGamepadSensorDataRate(long gamepad, int type);
    float[] SDL_GetGamepadSensorData(long gamepad, int type, int numValues);
    int getGCControllerCount();

    /**
     * Show or hide the OS pointer image. Windows only; elsewhere the caller sees
     * {@link SystemCursor#isSupported()} false and this is never reached.
     *
     * @return the {@code ShowCursor} display counter after the call
     */
    int showCursor(boolean hide);
}
