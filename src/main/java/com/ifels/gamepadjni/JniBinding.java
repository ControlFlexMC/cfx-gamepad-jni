package com.ifels.gamepadjni;

/**
 * {@link GamepadBinding} backed by our own JNI bridge.
 *
 * <p>Every method is a one-line forward to {@link JniGamepad}; the class exists
 * only so the interface can dispatch to it. Nothing here loads a library —
 * {@link NativeLibraryLoader} does that before installing this binding.</p>
 */
final class JniBinding implements GamepadBinding {

    static final JniBinding INSTANCE = new JniBinding();

    private JniBinding() {
    }

    @Override
    public boolean SDL_Init(int flags) {
        return JniGamepad.SDL_Init(flags);
    }
    @Override
    public boolean SDL_InitSubSystem(int flags) {
        return JniGamepad.SDL_InitSubSystem(flags);
    }
    @Override
    public void SDL_Quit() {
        JniGamepad.SDL_Quit();
    }
    @Override
    public int SDL_GetVersion() {
        return JniGamepad.SDL_GetVersion();
    }
    @Override
    public void SDL_QuitSubSystem(int flags) {
        JniGamepad.SDL_QuitSubSystem(flags);
    }
    @Override
    public int SDL_WasInit(int flags) {
        return JniGamepad.SDL_WasInit(flags);
    }
    @Override
    public String SDL_GetError() {
        return JniGamepad.SDL_GetError();
    }
    @Override
    public void SDL_ClearError() {
        JniGamepad.SDL_ClearError();
    }
    @Override
    public boolean SDL_SetHintWithPriority(String name, String value, int priority) {
        return JniGamepad.SDL_SetHintWithPriority(name, value, priority);
    }
    @Override
    public boolean SDL_SetHint(String name, String value) {
        return JniGamepad.SDL_SetHint(name, value);
    }
    @Override
    public int SDL_AddGamepadMapping(String mapping) {
        return JniGamepad.SDL_AddGamepadMapping(mapping);
    }
    @Override
    public int SDL_AddGamepadMappingsFromFile(String file) {
        return JniGamepad.SDL_AddGamepadMappingsFromFile(file);
    }
    @Override
    public boolean SDL_ReloadGamepadMappings() {
        return JniGamepad.SDL_ReloadGamepadMappings();
    }
    @Override
    public String SDL_GetGamepadMappingForID(int instanceId) {
        return JniGamepad.SDL_GetGamepadMappingForID(instanceId);
    }
    @Override
    public String SDL_GetGamepadMapping(long gamepad) {
        return JniGamepad.SDL_GetGamepadMapping(gamepad);
    }
    @Override
    public boolean SDL_SetGamepadMapping(int instanceId, String mapping) {
        return JniGamepad.SDL_SetGamepadMapping(instanceId, mapping);
    }
    @Override
    public boolean SDL_HasGamepad() {
        return JniGamepad.SDL_HasGamepad();
    }
    @Override
    public int[] SDL_GetGamepads() {
        return JniGamepad.SDL_GetGamepads();
    }
    @Override
    public boolean SDL_IsGamepad(int instanceId) {
        return JniGamepad.SDL_IsGamepad(instanceId);
    }
    @Override
    public String SDL_GetGamepadNameForID(int instanceId) {
        return JniGamepad.SDL_GetGamepadNameForID(instanceId);
    }
    @Override
    public String SDL_GetGamepadPathForID(int instanceId) {
        return JniGamepad.SDL_GetGamepadPathForID(instanceId);
    }
    @Override
    public int SDL_GetGamepadPlayerIndexForID(int instanceId) {
        return JniGamepad.SDL_GetGamepadPlayerIndexForID(instanceId);
    }
    @Override
    public String SDL_GetGamepadGUIDForID(int instanceId) {
        return JniGamepad.SDL_GetGamepadGUIDForID(instanceId);
    }
    @Override
    public short SDL_GetGamepadVendorForID(int instanceId) {
        return JniGamepad.SDL_GetGamepadVendorForID(instanceId);
    }
    @Override
    public short SDL_GetGamepadProductForID(int instanceId) {
        return JniGamepad.SDL_GetGamepadProductForID(instanceId);
    }
    @Override
    public short SDL_GetGamepadProductVersionForID(int instanceId) {
        return JniGamepad.SDL_GetGamepadProductVersionForID(instanceId);
    }
    @Override
    public int SDL_GetGamepadTypeForID(int instanceId) {
        return JniGamepad.SDL_GetGamepadTypeForID(instanceId);
    }
    @Override
    public int SDL_GetRealGamepadTypeForID(int instanceId) {
        return JniGamepad.SDL_GetRealGamepadTypeForID(instanceId);
    }
    @Override
    public long SDL_OpenGamepad(int instanceId) {
        return JniGamepad.SDL_OpenGamepad(instanceId);
    }
    @Override
    public long SDL_GetGamepadFromID(int instanceId) {
        return JniGamepad.SDL_GetGamepadFromID(instanceId);
    }
    @Override
    public long SDL_GetGamepadFromPlayerIndex(int playerIndex) {
        return JniGamepad.SDL_GetGamepadFromPlayerIndex(playerIndex);
    }
    @Override
    public void SDL_CloseGamepad(long gamepad) {
        JniGamepad.SDL_CloseGamepad(gamepad);
    }
    @Override
    public int SDL_GetGamepadID(long gamepad) {
        return JniGamepad.SDL_GetGamepadID(gamepad);
    }
    @Override
    public String SDL_GetGamepadName(long gamepad) {
        return JniGamepad.SDL_GetGamepadName(gamepad);
    }
    @Override
    public String SDL_GetGamepadPath(long gamepad) {
        return JniGamepad.SDL_GetGamepadPath(gamepad);
    }
    @Override
    public int SDL_GetGamepadType(long gamepad) {
        return JniGamepad.SDL_GetGamepadType(gamepad);
    }
    @Override
    public int SDL_GetRealGamepadType(long gamepad) {
        return JniGamepad.SDL_GetRealGamepadType(gamepad);
    }
    @Override
    public int SDL_GetGamepadPlayerIndex(long gamepad) {
        return JniGamepad.SDL_GetGamepadPlayerIndex(gamepad);
    }
    @Override
    public boolean SDL_SetGamepadPlayerIndex(long gamepad, int playerIndex) {
        return JniGamepad.SDL_SetGamepadPlayerIndex(gamepad, playerIndex);
    }
    @Override
    public short SDL_GetGamepadVendor(long gamepad) {
        return JniGamepad.SDL_GetGamepadVendor(gamepad);
    }
    @Override
    public short SDL_GetGamepadProduct(long gamepad) {
        return JniGamepad.SDL_GetGamepadProduct(gamepad);
    }
    @Override
    public short SDL_GetGamepadProductVersion(long gamepad) {
        return JniGamepad.SDL_GetGamepadProductVersion(gamepad);
    }
    @Override
    public short SDL_GetGamepadFirmwareVersion(long gamepad) {
        return JniGamepad.SDL_GetGamepadFirmwareVersion(gamepad);
    }
    @Override
    public String SDL_GetGamepadSerial(long gamepad) {
        return JniGamepad.SDL_GetGamepadSerial(gamepad);
    }
    @Override
    public long SDL_GetGamepadSteamHandle(long gamepad) {
        return JniGamepad.SDL_GetGamepadSteamHandle(gamepad);
    }
    @Override
    public int SDL_GetGamepadConnectionState(long gamepad) {
        return JniGamepad.SDL_GetGamepadConnectionState(gamepad);
    }
    @Override
    public int[] SDL_GetGamepadPowerInfo(long gamepad) {
        return JniGamepad.SDL_GetGamepadPowerInfo(gamepad);
    }
    @Override
    public boolean SDL_GamepadConnected(long gamepad) {
        return JniGamepad.SDL_GamepadConnected(gamepad);
    }
    @Override
    public void SDL_UpdateGamepads() {
        JniGamepad.SDL_UpdateGamepads();
    }
    @Override
    public void SDL_SetGamepadEventsEnabled(boolean enabled) {
        JniGamepad.SDL_SetGamepadEventsEnabled(enabled);
    }
    @Override
    public boolean SDL_GamepadEventsEnabled() {
        return JniGamepad.SDL_GamepadEventsEnabled();
    }
    @Override
    public boolean SDL_GamepadHasAxis(long gamepad, int axis) {
        return JniGamepad.SDL_GamepadHasAxis(gamepad, axis);
    }
    @Override
    public short SDL_GetGamepadAxis(long gamepad, int axis) {
        return JniGamepad.SDL_GetGamepadAxis(gamepad, axis);
    }
    @Override
    public boolean SDL_GamepadHasButton(long gamepad, int button) {
        return JniGamepad.SDL_GamepadHasButton(gamepad, button);
    }
    @Override
    public boolean SDL_GetGamepadButton(long gamepad, int button) {
        return JniGamepad.SDL_GetGamepadButton(gamepad, button);
    }
    @Override
    public int SDL_GetGamepadButtonLabelForType(int type, int button) {
        return JniGamepad.SDL_GetGamepadButtonLabelForType(type, button);
    }
    @Override
    public int SDL_GetGamepadButtonLabel(long gamepad, int button) {
        return JniGamepad.SDL_GetGamepadButtonLabel(gamepad, button);
    }
    @Override
    public int SDL_GetGamepadTypeFromString(String str) {
        return JniGamepad.SDL_GetGamepadTypeFromString(str);
    }
    @Override
    public String SDL_GetGamepadStringForType(int type) {
        return JniGamepad.SDL_GetGamepadStringForType(type);
    }
    @Override
    public int SDL_GetGamepadAxisFromString(String str) {
        return JniGamepad.SDL_GetGamepadAxisFromString(str);
    }
    @Override
    public String SDL_GetGamepadStringForAxis(int axis) {
        return JniGamepad.SDL_GetGamepadStringForAxis(axis);
    }
    @Override
    public int SDL_GetGamepadButtonFromString(String str) {
        return JniGamepad.SDL_GetGamepadButtonFromString(str);
    }
    @Override
    public String SDL_GetGamepadStringForButton(int button) {
        return JniGamepad.SDL_GetGamepadStringForButton(button);
    }
    @Override
    public boolean SDL_RumbleGamepad(long gamepad, int lowFrequencyRumble, int highFrequencyRumble, int durationMs) {
        return JniGamepad.SDL_RumbleGamepad(gamepad, lowFrequencyRumble, highFrequencyRumble, durationMs);
    }
    @Override
    public boolean SDL_RumbleGamepadTriggers(long gamepad, int leftRumble, int rightRumble, int durationMs) {
        return JniGamepad.SDL_RumbleGamepadTriggers(gamepad, leftRumble, rightRumble, durationMs);
    }
    @Override
    public boolean SDL_SetGamepadLED(long gamepad, byte red, byte green, byte blue) {
        return JniGamepad.SDL_SetGamepadLED(gamepad, red, green, blue);
    }
    @Override
    public int SDL_GetNumGamepadTouchpads(long gamepad) {
        return JniGamepad.SDL_GetNumGamepadTouchpads(gamepad);
    }
    @Override
    public int SDL_GetNumGamepadTouchpadFingers(long gamepad, int touchpad) {
        return JniGamepad.SDL_GetNumGamepadTouchpadFingers(gamepad, touchpad);
    }
    @Override
    public float[] SDL_GetGamepadTouchpadFinger(long gamepad, int touchpad, int finger) {
        return JniGamepad.SDL_GetGamepadTouchpadFinger(gamepad, touchpad, finger);
    }
    @Override
    public boolean SDL_GamepadHasSensor(long gamepad, int type) {
        return JniGamepad.SDL_GamepadHasSensor(gamepad, type);
    }
    @Override
    public boolean SDL_SetGamepadSensorEnabled(long gamepad, int type, boolean enabled) {
        return JniGamepad.SDL_SetGamepadSensorEnabled(gamepad, type, enabled);
    }
    @Override
    public boolean SDL_GamepadSensorEnabled(long gamepad, int type) {
        return JniGamepad.SDL_GamepadSensorEnabled(gamepad, type);
    }
    @Override
    public float SDL_GetGamepadSensorDataRate(long gamepad, int type) {
        return JniGamepad.SDL_GetGamepadSensorDataRate(gamepad, type);
    }
    @Override
    public float[] SDL_GetGamepadSensorData(long gamepad, int type, int numValues) {
        return JniGamepad.SDL_GetGamepadSensorData(gamepad, type, numValues);
    }
    @Override
    public int getGCControllerCount() {
        return JniGamepad.getGCControllerCount();
    }
    @Override
    public int showCursor(boolean hide) {
        return SystemCursorJNI.showCursor(hide);
    }
}
