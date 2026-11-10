package com.ifels.gamepadjni;

/**
 * Public facade over the SDL3 Gamepad API.
 *
 * <p>The backend behind it is chosen at load time by {@link GamepadManager#initialize}:
 * the JNI bridge when it loads, and the JNA direct-mapping backend when it does not.
 * Callers do not choose and cannot tell the difference — every method here keeps the
 * signature it had when this class was the JNI declaration list.</p>
 *
 * <p>Methods throw {@link UnsatisfiedLinkError} when no backend is installed, which is
 * also what they did before; nothing here throws {@link NullPointerException} for that
 * case.</p>
 *
 * <p>For higher-level object-oriented access, use {@link Gamepad} and
 * {@link GamepadManager} instead.</p>
 */
public final class GamepadJNI {

    private GamepadJNI() {
        // Prevent instantiation
    }

    // ═══════════════════════════════════════════
    // SDL Init / Quit
    // ═══════════════════════════════════════════

    /**
     * Initialize SDL subsystems.
     *
     * @param flags combination of SDL_INIT_* flags (e.g. SDL_INIT_GAMEPAD)
     * @return true on success, false on failure
     */
    public static boolean SDL_Init(int flags) {
        return Bindings.b().SDL_Init(flags);
    }

    /**
     * Initialize specific SDL subsystem.
     *
     * @param flags subsystem flags
     * @return true on success, false on failure
     */
    public static boolean SDL_InitSubSystem(int flags) {
        return Bindings.b().SDL_InitSubSystem(flags);
    }

    /** Shut down all SDL subsystems. */
    public static void SDL_Quit() {
        Bindings.b().SDL_Quit();
    }

    /**
     * Get the version of the linked SDL3.
     *
     * @return packed version: {@code major * 1000000 + minor * 1000 + patch}
     */
    public static int SDL_GetVersion() {
        return Bindings.b().SDL_GetVersion();
    }

    /**
     * Shut down specific SDL subsystems.
     *
     * <p>Prefer this over {@link #SDL_Quit()} when sharing a process-wide SDL
     * (e.g. Minecraft 26.3 already initialized video).</p>
     *
     * @param flags subsystem flags previously passed to {@link #SDL_InitSubSystem}
     */
    public static void SDL_QuitSubSystem(int flags) {
        Bindings.b().SDL_QuitSubSystem(flags);
    }

    /**
     * Return the SDL subsystems currently initialized (bitwise AND of {@code flags}).
     *
     * @param flags subsystem flags to query, or 0 for all
     * @return initialized flags
     */
    public static int SDL_WasInit(int flags) {
        return Bindings.b().SDL_WasInit(flags);
    }

    /** Get the last SDL error string. */
    public static String SDL_GetError() {
        return Bindings.b().SDL_GetError();
    }

    /** Clear the last SDL error. */
    public static void SDL_ClearError() {
        Bindings.b().SDL_ClearError();
    }

    // ═══════════════════════════════════════════
    // SDL Hints
    // ═══════════════════════════════════════════

    /**
     * Set a hint with a specific priority.
     *
     * @param name     hint name
     * @param value    hint value
     * @param priority priority level (0=default, 1=normal, 2=override)
     * @return true on success
     */
    public static boolean SDL_SetHintWithPriority(String name, String value, int priority) {
        return Bindings.b().SDL_SetHintWithPriority(name, value, priority);
    }

    /**
     * Set a hint with normal priority.
     *
     * @param name  hint name
     * @param value hint value
     * @return true on success
     */
    public static boolean SDL_SetHint(String name, String value) {
        return Bindings.b().SDL_SetHint(name, value);
    }

    // ═══════════════════════════════════════════
    // Gamepad Mapping
    // ═══════════════════════════════════════════

    /**
     * Add a gamepad mapping string.
     *
     * @param mapping the mapping string
     * @return 1 if new mapping added, 0 if updated, -1 on error
     */
    public static int SDL_AddGamepadMapping(String mapping) {
        return Bindings.b().SDL_AddGamepadMapping(mapping);
    }

    /**
     * Load gamepad mappings from a file.
     *
     * @param file path to mappings file
     * @return number of mappings added, or -1 on error
     */
    public static int SDL_AddGamepadMappingsFromFile(String file) {
        return Bindings.b().SDL_AddGamepadMappingsFromFile(file);
    }

    /** Reinitialize the SDL mapping database. */
    public static boolean SDL_ReloadGamepadMappings() {
        return Bindings.b().SDL_ReloadGamepadMappings();
    }

    /**
     * Get the gamepad mapping string for a given instance ID.
     *
     * @param instanceId joystick instance ID
     * @return mapping string, or null
     */
    public static String SDL_GetGamepadMappingForID(int instanceId) {
        return Bindings.b().SDL_GetGamepadMappingForID(instanceId);
    }

    /**
     * Get the current mapping of an opened gamepad.
     *
     * @param gamepad gamepad handle
     * @return mapping string, or null
     */
    public static String SDL_GetGamepadMapping(long gamepad) {
        return Bindings.b().SDL_GetGamepadMapping(gamepad);
    }

    /**
     * Set the mapping for a joystick instance.
     *
     * @param instanceId joystick instance ID
     * @param mapping    mapping string, or null to clear
     * @return true on success
     */
    public static boolean SDL_SetGamepadMapping(int instanceId, String mapping) {
        return Bindings.b().SDL_SetGamepadMapping(instanceId, mapping);
    }

    // ═══════════════════════════════════════════
    // Gamepad Discovery
    // ═══════════════════════════════════════════

    /** Check if any gamepad is currently connected. */
    public static boolean SDL_HasGamepad() {
        return Bindings.b().SDL_HasGamepad();
    }

    /**
     * Get a list of currently connected gamepad instance IDs.
     *
     * @return array of instance IDs, or null on failure
     */
    public static int[] SDL_GetGamepads() {
        return Bindings.b().SDL_GetGamepads();
    }

    /**
     * Check if a joystick is supported by the gamepad interface.
     *
     * @param instanceId joystick instance ID
     * @return true if supported
     */
    public static boolean SDL_IsGamepad(int instanceId) {
        return Bindings.b().SDL_IsGamepad(instanceId);
    }

    // ═══════════════════════════════════════════
    // Gamepad Info (by Instance ID, no open required)
    // ═══════════════════════════════════════════

    /** Get the name of a gamepad by instance ID. */
    public static String SDL_GetGamepadNameForID(int instanceId) {
        return Bindings.b().SDL_GetGamepadNameForID(instanceId);
    }

    /** Get the path of a gamepad by instance ID. */
    public static String SDL_GetGamepadPathForID(int instanceId) {
        return Bindings.b().SDL_GetGamepadPathForID(instanceId);
    }

    /** Get the player index of a gamepad by instance ID. */
    public static int SDL_GetGamepadPlayerIndexForID(int instanceId) {
        return Bindings.b().SDL_GetGamepadPlayerIndexForID(instanceId);
    }

    /** Get the GUID of a gamepad by instance ID (returned as hex string). */
    public static String SDL_GetGamepadGUIDForID(int instanceId) {
        return Bindings.b().SDL_GetGamepadGUIDForID(instanceId);
    }

    /** Get the USB vendor ID of a gamepad by instance ID. */
    public static short SDL_GetGamepadVendorForID(int instanceId) {
        return Bindings.b().SDL_GetGamepadVendorForID(instanceId);
    }

    /** Get the USB product ID of a gamepad by instance ID. */
    public static short SDL_GetGamepadProductForID(int instanceId) {
        return Bindings.b().SDL_GetGamepadProductForID(instanceId);
    }

    /** Get the product version of a gamepad by instance ID. */
    public static short SDL_GetGamepadProductVersionForID(int instanceId) {
        return Bindings.b().SDL_GetGamepadProductVersionForID(instanceId);
    }

    /** Get the type of a gamepad by instance ID. */
    public static int SDL_GetGamepadTypeForID(int instanceId) {
        return Bindings.b().SDL_GetGamepadTypeForID(instanceId);
    }

    /** Get the real type of a gamepad by instance ID (ignoring mapping override). */
    public static int SDL_GetRealGamepadTypeForID(int instanceId) {
        return Bindings.b().SDL_GetRealGamepadTypeForID(instanceId);
    }

    // ═══════════════════════════════════════════
    // Gamepad Open / Close
    // ═══════════════════════════════════════════

    /**
     * Open a gamepad for use.
     *
     * @param instanceId joystick instance ID
     * @return gamepad handle (pointer as long), or 0 on failure
     */
    public static long SDL_OpenGamepad(int instanceId) {
        return Bindings.b().SDL_OpenGamepad(instanceId);
    }

    /**
     * Get an already-opened gamepad by instance ID.
     *
     * @param instanceId joystick instance ID
     * @return gamepad handle, or 0 if not opened
     */
    public static long SDL_GetGamepadFromID(int instanceId) {
        return Bindings.b().SDL_GetGamepadFromID(instanceId);
    }

    /**
     * Get a gamepad by player index.
     *
     * @param playerIndex player index
     * @return gamepad handle, or 0 if not found
     */
    public static long SDL_GetGamepadFromPlayerIndex(int playerIndex) {
        return Bindings.b().SDL_GetGamepadFromPlayerIndex(playerIndex);
    }

    /**
     * Close a previously opened gamepad.
     *
     * @param gamepad gamepad handle
     */
    public static void SDL_CloseGamepad(long gamepad) {
        Bindings.b().SDL_CloseGamepad(gamepad);
    }

    // ═══════════════════════════════════════════
    // Gamepad Properties (opened gamepad)
    // ═══════════════════════════════════════════

    /** Get the instance ID of an opened gamepad. */
    public static int SDL_GetGamepadID(long gamepad) {
        return Bindings.b().SDL_GetGamepadID(gamepad);
    }

    /** Get the name of an opened gamepad. */
    public static String SDL_GetGamepadName(long gamepad) {
        return Bindings.b().SDL_GetGamepadName(gamepad);
    }

    /** Get the path of an opened gamepad. */
    public static String SDL_GetGamepadPath(long gamepad) {
        return Bindings.b().SDL_GetGamepadPath(gamepad);
    }

    /** Get the type of an opened gamepad. */
    public static int SDL_GetGamepadType(long gamepad) {
        return Bindings.b().SDL_GetGamepadType(gamepad);
    }

    /** Get the real type of an opened gamepad (ignoring mapping override). */
    public static int SDL_GetRealGamepadType(long gamepad) {
        return Bindings.b().SDL_GetRealGamepadType(gamepad);
    }

    /** Get the player index of an opened gamepad. */
    public static int SDL_GetGamepadPlayerIndex(long gamepad) {
        return Bindings.b().SDL_GetGamepadPlayerIndex(gamepad);
    }

    /**
     * Set the player index of an opened gamepad.
     *
     * @param gamepad     gamepad handle
     * @param playerIndex player index, or -1 to clear
     * @return true on success
     */
    public static boolean SDL_SetGamepadPlayerIndex(long gamepad, int playerIndex) {
        return Bindings.b().SDL_SetGamepadPlayerIndex(gamepad, playerIndex);
    }

    /** Get the USB vendor ID of an opened gamepad. */
    public static short SDL_GetGamepadVendor(long gamepad) {
        return Bindings.b().SDL_GetGamepadVendor(gamepad);
    }

    /** Get the USB product ID of an opened gamepad. */
    public static short SDL_GetGamepadProduct(long gamepad) {
        return Bindings.b().SDL_GetGamepadProduct(gamepad);
    }

    /** Get the product version of an opened gamepad. */
    public static short SDL_GetGamepadProductVersion(long gamepad) {
        return Bindings.b().SDL_GetGamepadProductVersion(gamepad);
    }

    /** Get the firmware version of an opened gamepad. */
    public static short SDL_GetGamepadFirmwareVersion(long gamepad) {
        return Bindings.b().SDL_GetGamepadFirmwareVersion(gamepad);
    }

    /** Get the serial number of an opened gamepad. */
    public static String SDL_GetGamepadSerial(long gamepad) {
        return Bindings.b().SDL_GetGamepadSerial(gamepad);
    }

    /** Get the Steam Input handle of an opened gamepad. */
    public static long SDL_GetGamepadSteamHandle(long gamepad) {
        return Bindings.b().SDL_GetGamepadSteamHandle(gamepad);
    }

    /** Get the connection state of an opened gamepad. */
    public static int SDL_GetGamepadConnectionState(long gamepad) {
        return Bindings.b().SDL_GetGamepadConnectionState(gamepad);
    }

    /**
     * Get the battery state of an opened gamepad.
     *
     * @param gamepad gamepad handle
     * @return int[2] array: [0]=PowerState, [1]=percent (0-100, or -1 if unknown)
     */
    public static int[] SDL_GetGamepadPowerInfo(long gamepad) {
        return Bindings.b().SDL_GetGamepadPowerInfo(gamepad);
    }

    /** Check if an opened gamepad is currently connected. */
    public static boolean SDL_GamepadConnected(long gamepad) {
        return Bindings.b().SDL_GamepadConnected(gamepad);
    }

    // ═══════════════════════════════════════════
    // Gamepad Input State
    // ═══════════════════════════════════════════

    /** Update gamepad state (call if events are disabled). */
    public static void SDL_UpdateGamepads() {
        Bindings.b().SDL_UpdateGamepads();
    }

    /** Set whether gamepad events are processed. */
    public static void SDL_SetGamepadEventsEnabled(boolean enabled) {
        Bindings.b().SDL_SetGamepadEventsEnabled(enabled);
    }

    /** Query whether gamepad events are being processed. */
    public static boolean SDL_GamepadEventsEnabled() {
        return Bindings.b().SDL_GamepadEventsEnabled();
    }

    /** Check if a gamepad has a particular axis. */
    public static boolean SDL_GamepadHasAxis(long gamepad, int axis) {
        return Bindings.b().SDL_GamepadHasAxis(gamepad, axis);
    }

    /**
     * Get the current state of an axis on a gamepad.
     *
     * <p>Returns short value: sticks -32768 to 32767, triggers 0 to 32767.</p>
     *
     * @param gamepad gamepad handle
     * @param axis    SDL_GamepadAxis value
     * @return axis state as short
     */
    public static short SDL_GetGamepadAxis(long gamepad, int axis) {
        return Bindings.b().SDL_GetGamepadAxis(gamepad, axis);
    }

    /** Check if a gamepad has a particular button. */
    public static boolean SDL_GamepadHasButton(long gamepad, int button) {
        return Bindings.b().SDL_GamepadHasButton(gamepad, button);
    }

    /**
     * Get the current state of a button on a gamepad.
     *
     * @param gamepad gamepad handle
     * @param button  SDL_GamepadButton value
     * @return true if pressed, false otherwise
     */
    public static boolean SDL_GetGamepadButton(long gamepad, int button) {
        return Bindings.b().SDL_GetGamepadButton(gamepad, button);
    }

    /** Get the label of a button for a gamepad type. */
    public static int SDL_GetGamepadButtonLabelForType(int type, int button) {
        return Bindings.b().SDL_GetGamepadButtonLabelForType(type, button);
    }

    /** Get the label of a button on a gamepad. */
    public static int SDL_GetGamepadButtonLabel(long gamepad, int button) {
        return Bindings.b().SDL_GetGamepadButtonLabel(gamepad, button);
    }

    // ═══════════════════════════════════════════
    // String <-> Enum Conversion
    // ═══════════════════════════════════════════

    /** Convert a string to SDL_GamepadType. */
    public static int SDL_GetGamepadTypeFromString(String str) {
        return Bindings.b().SDL_GetGamepadTypeFromString(str);
    }

    /** Convert an SDL_GamepadType to string. */
    public static String SDL_GetGamepadStringForType(int type) {
        return Bindings.b().SDL_GetGamepadStringForType(type);
    }

    /** Convert a string to SDL_GamepadAxis. */
    public static int SDL_GetGamepadAxisFromString(String str) {
        return Bindings.b().SDL_GetGamepadAxisFromString(str);
    }

    /** Convert an SDL_GamepadAxis to string. */
    public static String SDL_GetGamepadStringForAxis(int axis) {
        return Bindings.b().SDL_GetGamepadStringForAxis(axis);
    }

    /** Convert a string to SDL_GamepadButton. */
    public static int SDL_GetGamepadButtonFromString(String str) {
        return Bindings.b().SDL_GetGamepadButtonFromString(str);
    }

    /** Convert an SDL_GamepadButton to string. */
    public static String SDL_GetGamepadStringForButton(int button) {
        return Bindings.b().SDL_GetGamepadStringForButton(button);
    }

    // ═══════════════════════════════════════════
    // Rumble & LED
    // ═══════════════════════════════════════════

    /**
     * Start a rumble effect on a gamepad.
     *
     * @param gamepad             gamepad handle
     * @param lowFrequencyRumble  low frequency intensity (0-65535)
     * @param highFrequencyRumble high frequency intensity (0-65535)
     * @param durationMs          duration in milliseconds
     * @return true on success
     */
    public static boolean SDL_RumbleGamepad(long gamepad, int lowFrequencyRumble, int highFrequencyRumble, int durationMs) {
        return Bindings.b().SDL_RumbleGamepad(gamepad, lowFrequencyRumble, highFrequencyRumble, durationMs);
    }

    /**
     * Start a rumble effect in the gamepad's triggers.
     *
     * @param gamepad     gamepad handle
     * @param leftRumble  left trigger rumble (0-65535)
     * @param rightRumble right trigger rumble (0-65535)
     * @param durationMs  duration in milliseconds
     * @return true on success
     */
    public static boolean SDL_RumbleGamepadTriggers(long gamepad, int leftRumble, int rightRumble, int durationMs) {
        return Bindings.b().SDL_RumbleGamepadTriggers(gamepad, leftRumble, rightRumble, durationMs);
    }

    /**
     * Update a gamepad's LED color.
     *
     * @param gamepad gamepad handle
     * @param red     red intensity (0-255)
     * @param green   green intensity (0-255)
     * @param blue    blue intensity (0-255)
     * @return true on success
     */
    public static boolean SDL_SetGamepadLED(long gamepad, byte red, byte green, byte blue) {
        return Bindings.b().SDL_SetGamepadLED(gamepad, red, green, blue);
    }

    // ═══════════════════════════════════════════
    // Touchpad & Sensor
    // ═══════════════════════════════════════════

    /** Get the number of touchpads on a gamepad. */
    public static int SDL_GetNumGamepadTouchpads(long gamepad) {
        return Bindings.b().SDL_GetNumGamepadTouchpads(gamepad);
    }

    /** Get the number of fingers supported on a touchpad. */
    public static int SDL_GetNumGamepadTouchpadFingers(long gamepad, int touchpad) {
        return Bindings.b().SDL_GetNumGamepadTouchpadFingers(gamepad, touchpad);
    }

    /**
     * Get the current state of a touchpad finger.
     *
     * @param gamepad  gamepad handle
     * @param touchpad touchpad index
     * @param finger   finger index
     * @return float[4]: [0]=down(1.0/0.0), [1]=x, [2]=y, [3]=pressure, or null on error
     */
    public static float[] SDL_GetGamepadTouchpadFinger(long gamepad, int touchpad, int finger) {
        return Bindings.b().SDL_GetGamepadTouchpadFinger(gamepad, touchpad, finger);
    }

    /** Check if a gamepad has a particular sensor. */
    public static boolean SDL_GamepadHasSensor(long gamepad, int type) {
        return Bindings.b().SDL_GamepadHasSensor(gamepad, type);
    }

    /**
     * Set whether a gamepad sensor is enabled.
     *
     * @param gamepad gamepad handle
     * @param type    sensor type
     * @param enabled whether to enable
     * @return true on success
     */
    public static boolean SDL_SetGamepadSensorEnabled(long gamepad, int type, boolean enabled) {
        return Bindings.b().SDL_SetGamepadSensorEnabled(gamepad, type, enabled);
    }

    /** Query whether a gamepad sensor is enabled. */
    public static boolean SDL_GamepadSensorEnabled(long gamepad, int type) {
        return Bindings.b().SDL_GamepadSensorEnabled(gamepad, type);
    }

    /** Get the data rate of a gamepad sensor. */
    public static float SDL_GetGamepadSensorDataRate(long gamepad, int type) {
        return Bindings.b().SDL_GetGamepadSensorDataRate(gamepad, type);
    }

    /**
     * Get the current state of a gamepad sensor.
     *
     * @param gamepad   gamepad handle
     * @param type      sensor type
     * @param numValues number of values to read
     * @return sensor data array, or null on error
     */
    public static float[] SDL_GetGamepadSensorData(long gamepad, int type, int numValues) {
        return Bindings.b().SDL_GetGamepadSensorData(gamepad, type, numValues);
    }

    // ═══════════════════════════════════════════
    // Platform-specific helpers (macOS)
    // ═══════════════════════════════════════════

    /**
     * Get the number of GCControllers currently known to macOS.
     *
     * <p>This directly calls {@code [GCController controllers].count} and does
     * NOT depend on NSNotification delivery. Use this to detect disconnects
     * that SDL's MFI driver misses because GCControllerDidDisconnectNotification
     * is never delivered when SDL is initialized on a non-main thread.</p>
     *
     * @return controller count, or -1 if not supported (pre-macOS 11)
     */
    public static int getGCControllerCount() {
        return Bindings.b().getGCControllerCount();
    }
}
