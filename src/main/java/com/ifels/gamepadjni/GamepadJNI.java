package com.ifels.gamepadjni;

/**
 * JNI native method declarations for SDL3 Gamepad API.
 *
 * <p>This class provides a 1:1 mapping to the SDL3 C gamepad functions.
 * The {@code long} type is used for opaque pointer handles (SDL_Gamepad*).
 * All native methods are static and follow the naming convention of the SDL3 C API.</p>
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
    public static native boolean SDL_Init(int flags);

    /**
     * Initialize specific SDL subsystem.
     *
     * @param flags subsystem flags
     * @return true on success, false on failure
     */
    public static native boolean SDL_InitSubSystem(int flags);

    /** Shut down all SDL subsystems. */
    public static native void SDL_Quit();

    /** Get the last SDL error string. */
    public static native String SDL_GetError();

    /** Clear the last SDL error. */
    public static native void SDL_ClearError();

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
    public static native boolean SDL_SetHintWithPriority(String name, String value, int priority);

    /**
     * Set a hint with normal priority.
     *
     * @param name  hint name
     * @param value hint value
     * @return true on success
     */
    public static native boolean SDL_SetHint(String name, String value);

    // ═══════════════════════════════════════════
    // Gamepad Mapping
    // ═══════════════════════════════════════════

    /**
     * Add a gamepad mapping string.
     *
     * @param mapping the mapping string
     * @return 1 if new mapping added, 0 if updated, -1 on error
     */
    public static native int SDL_AddGamepadMapping(String mapping);

    /**
     * Load gamepad mappings from a file.
     *
     * @param file path to mappings file
     * @return number of mappings added, or -1 on error
     */
    public static native int SDL_AddGamepadMappingsFromFile(String file);

    /** Reinitialize the SDL mapping database. */
    public static native boolean SDL_ReloadGamepadMappings();

    /**
     * Get the gamepad mapping string for a given instance ID.
     *
     * @param instanceId joystick instance ID
     * @return mapping string, or null
     */
    public static native String SDL_GetGamepadMappingForID(int instanceId);

    /**
     * Get the current mapping of an opened gamepad.
     *
     * @param gamepad gamepad handle
     * @return mapping string, or null
     */
    public static native String SDL_GetGamepadMapping(long gamepad);

    /**
     * Set the mapping for a joystick instance.
     *
     * @param instanceId joystick instance ID
     * @param mapping    mapping string, or null to clear
     * @return true on success
     */
    public static native boolean SDL_SetGamepadMapping(int instanceId, String mapping);

    // ═══════════════════════════════════════════
    // Gamepad Discovery
    // ═══════════════════════════════════════════

    /** Check if any gamepad is currently connected. */
    public static native boolean SDL_HasGamepad();

    /**
     * Get a list of currently connected gamepad instance IDs.
     *
     * @return array of instance IDs, or null on failure
     */
    public static native int[] SDL_GetGamepads();

    /**
     * Check if a joystick is supported by the gamepad interface.
     *
     * @param instanceId joystick instance ID
     * @return true if supported
     */
    public static native boolean SDL_IsGamepad(int instanceId);

    // ═══════════════════════════════════════════
    // Gamepad Info (by Instance ID, no open required)
    // ═══════════════════════════════════════════

    /** Get the name of a gamepad by instance ID. */
    public static native String SDL_GetGamepadNameForID(int instanceId);

    /** Get the path of a gamepad by instance ID. */
    public static native String SDL_GetGamepadPathForID(int instanceId);

    /** Get the player index of a gamepad by instance ID. */
    public static native int SDL_GetGamepadPlayerIndexForID(int instanceId);

    /** Get the GUID of a gamepad by instance ID (returned as hex string). */
    public static native String SDL_GetGamepadGUIDForID(int instanceId);

    /** Get the USB vendor ID of a gamepad by instance ID. */
    public static native short SDL_GetGamepadVendorForID(int instanceId);

    /** Get the USB product ID of a gamepad by instance ID. */
    public static native short SDL_GetGamepadProductForID(int instanceId);

    /** Get the product version of a gamepad by instance ID. */
    public static native short SDL_GetGamepadProductVersionForID(int instanceId);

    /** Get the type of a gamepad by instance ID. */
    public static native int SDL_GetGamepadTypeForID(int instanceId);

    /** Get the real type of a gamepad by instance ID (ignoring mapping override). */
    public static native int SDL_GetRealGamepadTypeForID(int instanceId);

    // ═══════════════════════════════════════════
    // Gamepad Open / Close
    // ═══════════════════════════════════════════

    /**
     * Open a gamepad for use.
     *
     * @param instanceId joystick instance ID
     * @return gamepad handle (pointer as long), or 0 on failure
     */
    public static native long SDL_OpenGamepad(int instanceId);

    /**
     * Get an already-opened gamepad by instance ID.
     *
     * @param instanceId joystick instance ID
     * @return gamepad handle, or 0 if not opened
     */
    public static native long SDL_GetGamepadFromID(int instanceId);

    /**
     * Get a gamepad by player index.
     *
     * @param playerIndex player index
     * @return gamepad handle, or 0 if not found
     */
    public static native long SDL_GetGamepadFromPlayerIndex(int playerIndex);

    /**
     * Close a previously opened gamepad.
     *
     * @param gamepad gamepad handle
     */
    public static native void SDL_CloseGamepad(long gamepad);

    // ═══════════════════════════════════════════
    // Gamepad Properties (opened gamepad)
    // ═══════════════════════════════════════════

    /** Get the instance ID of an opened gamepad. */
    public static native int SDL_GetGamepadID(long gamepad);

    /** Get the name of an opened gamepad. */
    public static native String SDL_GetGamepadName(long gamepad);

    /** Get the path of an opened gamepad. */
    public static native String SDL_GetGamepadPath(long gamepad);

    /** Get the type of an opened gamepad. */
    public static native int SDL_GetGamepadType(long gamepad);

    /** Get the real type of an opened gamepad (ignoring mapping override). */
    public static native int SDL_GetRealGamepadType(long gamepad);

    /** Get the player index of an opened gamepad. */
    public static native int SDL_GetGamepadPlayerIndex(long gamepad);

    /**
     * Set the player index of an opened gamepad.
     *
     * @param gamepad     gamepad handle
     * @param playerIndex player index, or -1 to clear
     * @return true on success
     */
    public static native boolean SDL_SetGamepadPlayerIndex(long gamepad, int playerIndex);

    /** Get the USB vendor ID of an opened gamepad. */
    public static native short SDL_GetGamepadVendor(long gamepad);

    /** Get the USB product ID of an opened gamepad. */
    public static native short SDL_GetGamepadProduct(long gamepad);

    /** Get the product version of an opened gamepad. */
    public static native short SDL_GetGamepadProductVersion(long gamepad);

    /** Get the firmware version of an opened gamepad. */
    public static native short SDL_GetGamepadFirmwareVersion(long gamepad);

    /** Get the serial number of an opened gamepad. */
    public static native String SDL_GetGamepadSerial(long gamepad);

    /** Get the Steam Input handle of an opened gamepad. */
    public static native long SDL_GetGamepadSteamHandle(long gamepad);

    /** Get the connection state of an opened gamepad. */
    public static native int SDL_GetGamepadConnectionState(long gamepad);

    /**
     * Get the battery state of an opened gamepad.
     *
     * @param gamepad gamepad handle
     * @return int[2] array: [0]=PowerState, [1]=percent (0-100, or -1 if unknown)
     */
    public static native int[] SDL_GetGamepadPowerInfo(long gamepad);

    /** Check if an opened gamepad is currently connected. */
    public static native boolean SDL_GamepadConnected(long gamepad);

    // ═══════════════════════════════════════════
    // Gamepad Input State
    // ═══════════════════════════════════════════

    /** Update gamepad state (call if events are disabled). */
    public static native void SDL_UpdateGamepads();

    /** Set whether gamepad events are processed. */
    public static native void SDL_SetGamepadEventsEnabled(boolean enabled);

    /** Query whether gamepad events are being processed. */
    public static native boolean SDL_GamepadEventsEnabled();

    /** Check if a gamepad has a particular axis. */
    public static native boolean SDL_GamepadHasAxis(long gamepad, int axis);

    /**
     * Get the current state of an axis on a gamepad.
     *
     * <p>Returns short value: sticks -32768 to 32767, triggers 0 to 32767.</p>
     *
     * @param gamepad gamepad handle
     * @param axis    SDL_GamepadAxis value
     * @return axis state as short
     */
    public static native short SDL_GetGamepadAxis(long gamepad, int axis);

    /** Check if a gamepad has a particular button. */
    public static native boolean SDL_GamepadHasButton(long gamepad, int button);

    /**
     * Get the current state of a button on a gamepad.
     *
     * @param gamepad gamepad handle
     * @param button  SDL_GamepadButton value
     * @return true if pressed, false otherwise
     */
    public static native boolean SDL_GetGamepadButton(long gamepad, int button);

    /** Get the label of a button for a gamepad type. */
    public static native int SDL_GetGamepadButtonLabelForType(int type, int button);

    /** Get the label of a button on a gamepad. */
    public static native int SDL_GetGamepadButtonLabel(long gamepad, int button);

    // ═══════════════════════════════════════════
    // String <-> Enum Conversion
    // ═══════════════════════════════════════════

    /** Convert a string to SDL_GamepadType. */
    public static native int SDL_GetGamepadTypeFromString(String str);

    /** Convert an SDL_GamepadType to string. */
    public static native String SDL_GetGamepadStringForType(int type);

    /** Convert a string to SDL_GamepadAxis. */
    public static native int SDL_GetGamepadAxisFromString(String str);

    /** Convert an SDL_GamepadAxis to string. */
    public static native String SDL_GetGamepadStringForAxis(int axis);

    /** Convert a string to SDL_GamepadButton. */
    public static native int SDL_GetGamepadButtonFromString(String str);

    /** Convert an SDL_GamepadButton to string. */
    public static native String SDL_GetGamepadStringForButton(int button);

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
    public static native boolean SDL_RumbleGamepad(long gamepad, int lowFrequencyRumble,
                                                   int highFrequencyRumble, int durationMs);

    /**
     * Start a rumble effect in the gamepad's triggers.
     *
     * @param gamepad     gamepad handle
     * @param leftRumble  left trigger rumble (0-65535)
     * @param rightRumble right trigger rumble (0-65535)
     * @param durationMs  duration in milliseconds
     * @return true on success
     */
    public static native boolean SDL_RumbleGamepadTriggers(long gamepad, int leftRumble,
                                                           int rightRumble, int durationMs);

    /**
     * Update a gamepad's LED color.
     *
     * @param gamepad gamepad handle
     * @param red     red intensity (0-255)
     * @param green   green intensity (0-255)
     * @param blue    blue intensity (0-255)
     * @return true on success
     */
    public static native boolean SDL_SetGamepadLED(long gamepad, byte red, byte green, byte blue);

    // ═══════════════════════════════════════════
    // Touchpad & Sensor
    // ═══════════════════════════════════════════

    /** Get the number of touchpads on a gamepad. */
    public static native int SDL_GetNumGamepadTouchpads(long gamepad);

    /** Get the number of fingers supported on a touchpad. */
    public static native int SDL_GetNumGamepadTouchpadFingers(long gamepad, int touchpad);

    /**
     * Get the current state of a touchpad finger.
     *
     * @param gamepad  gamepad handle
     * @param touchpad touchpad index
     * @param finger   finger index
     * @return float[4]: [0]=down(1.0/0.0), [1]=x, [2]=y, [3]=pressure, or null on error
     */
    public static native float[] SDL_GetGamepadTouchpadFinger(long gamepad, int touchpad, int finger);

    /** Check if a gamepad has a particular sensor. */
    public static native boolean SDL_GamepadHasSensor(long gamepad, int type);

    /**
     * Set whether a gamepad sensor is enabled.
     *
     * @param gamepad gamepad handle
     * @param type    sensor type
     * @param enabled whether to enable
     * @return true on success
     */
    public static native boolean SDL_SetGamepadSensorEnabled(long gamepad, int type, boolean enabled);

    /** Query whether a gamepad sensor is enabled. */
    public static native boolean SDL_GamepadSensorEnabled(long gamepad, int type);

    /** Get the data rate of a gamepad sensor. */
    public static native float SDL_GetGamepadSensorDataRate(long gamepad, int type);

    /**
     * Get the current state of a gamepad sensor.
     *
     * @param gamepad   gamepad handle
     * @param type      sensor type
     * @param numValues number of values to read
     * @return sensor data array, or null on error
     */
    public static native float[] SDL_GetGamepadSensorData(long gamepad, int type, int numValues);

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
    public static native int getGCControllerCount();
}
