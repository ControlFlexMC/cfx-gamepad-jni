package com.ifels.gamepadjni;

/**
 * High-level object-oriented wrapper for an opened SDL3 gamepad.
 *
 * <p>Each Gamepad instance wraps an SDL_Gamepad handle and provides
 * convenient Java-friendly methods for reading input state, controlling
 * rumble/LED, and querying device info.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * Gamepad gamepad = GamepadManager.getInstance().openGamepad(instanceId);
 * if (gamepad != null) {
 *     float leftX = gamepad.getAxisFloat(GamepadAxis.LEFTX);
 *     boolean pressed = gamepad.getButton(GamepadButton.SOUTH);
 *     gamepad.rumble(0xFFFF, 0xFFFF, 500);
 *     gamepad.close();
 * }
 * }</pre>
 */
public class Gamepad {

    /** Opaque native gamepad handle (SDL_Gamepad* as long). */
    private long handle;

    /** Instance ID of this gamepad. */
    private final int instanceId;

    /**
     * Create a Gamepad wrapper.
     *
     * @param handle     native gamepad handle
     * @param instanceId joystick instance ID
     */
    Gamepad(long handle, int instanceId) {
        this.handle = handle;
        this.instanceId = instanceId;
    }

    /** Get the native handle (for advanced use). */
    public long getHandle() {
        return handle;
    }

    /** Get the instance ID. */
    public int getInstanceId() {
        return instanceId;
    }

    /** Check if this gamepad is still open and connected. */
    public boolean isConnected() {
        if (handle == 0) return false;
        return GamepadJNI.SDL_GamepadConnected(handle);
    }

    /** Close this gamepad. After calling this, the handle becomes invalid. */
    public void close() {
        if (handle != 0) {
            GamepadJNI.SDL_CloseGamepad(handle);
            handle = 0;
        }
    }

    // ═══════════════════════════════════════════
    // Device Info
    // ═══════════════════════════════════════════

    /** Get the implementation-dependent name. */
    public String getName() {
        if (handle == 0) return null;
        return GamepadJNI.SDL_GetGamepadName(handle);
    }

    /** Get the implementation-dependent path. */
    public String getPath() {
        if (handle == 0) return null;
        return GamepadJNI.SDL_GetGamepadPath(handle);
    }

    /** Get the gamepad type. */
    public GamepadType getType() {
        if (handle == 0) return GamepadType.UNKNOWN;
        return GamepadType.fromValue(GamepadJNI.SDL_GetGamepadType(handle));
    }

    /** Get the real gamepad type (ignoring mapping override). */
    public GamepadType getRealType() {
        if (handle == 0) return GamepadType.UNKNOWN;
        return GamepadType.fromValue(GamepadJNI.SDL_GetRealGamepadType(handle));
    }

    /** Get the player index (-1 if not available). */
    public int getPlayerIndex() {
        if (handle == 0) return -1;
        return GamepadJNI.SDL_GetGamepadPlayerIndex(handle);
    }

    /** Set the player index (-1 to clear). */
    public boolean setPlayerIndex(int playerIndex) {
        if (handle == 0) return false;
        return GamepadJNI.SDL_SetGamepadPlayerIndex(handle, playerIndex);
    }

    /** Get the USB vendor ID (0 if unavailable). */
    public short getVendor() {
        if (handle == 0) return 0;
        return GamepadJNI.SDL_GetGamepadVendor(handle);
    }

    /** Get the USB product ID (0 if unavailable). */
    public short getProduct() {
        if (handle == 0) return 0;
        return GamepadJNI.SDL_GetGamepadProduct(handle);
    }

    /** Get the product version (0 if unavailable). */
    public short getProductVersion() {
        if (handle == 0) return 0;
        return GamepadJNI.SDL_GetGamepadProductVersion(handle);
    }

    /** Get the firmware version (0 if unavailable). */
    public short getFirmwareVersion() {
        if (handle == 0) return 0;
        return GamepadJNI.SDL_GetGamepadFirmwareVersion(handle);
    }

    /** Get the serial number. */
    public String getSerial() {
        if (handle == 0) return null;
        return GamepadJNI.SDL_GetGamepadSerial(handle);
    }

    /** Get the connection state. */
    public JoystickConnectionState getConnectionState() {
        if (handle == 0) return JoystickConnectionState.INVALID;
        return JoystickConnectionState.fromValue(GamepadJNI.SDL_GetGamepadConnectionState(handle));
    }

    /** Get the battery power info. Returns int[2]: [0]=PowerState value, [1]=percent. */
    public int[] getPowerInfo() {
        if (handle == 0) return new int[]{PowerState.ERROR.getValue(), -1};
        return GamepadJNI.SDL_GetGamepadPowerInfo(handle);
    }

    // ═══════════════════════════════════════════
    // Input State
    // ═══════════════════════════════════════════

    /** Check if the gamepad has a particular axis. */
    public boolean hasAxis(GamepadAxis axis) {
        if (handle == 0) return false;
        return GamepadJNI.SDL_GamepadHasAxis(handle, axis.getValue());
    }

    /**
     * Get the raw axis value (short range).
     *
     * <p>Sticks: -32768 to 32767. Triggers: 0 to 32767.</p>
     */
    public short getAxisRaw(GamepadAxis axis) {
        if (handle == 0) return 0;
        return GamepadJNI.SDL_GetGamepadAxis(handle, axis.getValue());
    }

    /**
     * Get the axis value normalized to float.
     *
     * <p>Sticks: -1.0 to 1.0. Triggers: 0.0 to 1.0.</p>
     */
    public float getAxisFloat(GamepadAxis axis) {
        if (handle == 0) return 0.0f;
        short raw = GamepadJNI.SDL_GetGamepadAxis(handle, axis.getValue());
        if (axis == GamepadAxis.LEFT_TRIGGER || axis == GamepadAxis.RIGHT_TRIGGER) {
            if (raw < 0) raw = 0;
            return raw / 32767.0f;
        }
        return raw / 32767.0f;
    }

    /** Check if the gamepad has a particular button. */
    public boolean hasButton(GamepadButton button) {
        if (handle == 0) return false;
        return GamepadJNI.SDL_GamepadHasButton(handle, button.getValue());
    }

    /** Get the current state of a button. */
    public boolean getButton(GamepadButton button) {
        if (handle == 0) return false;
        return GamepadJNI.SDL_GetGamepadButton(handle, button.getValue());
    }

    /** Get the label for a button. */
    public GamepadButtonLabel getButtonLabel(GamepadButton button) {
        if (handle == 0) return GamepadButtonLabel.UNKNOWN;
        return GamepadButtonLabel.fromValue(GamepadJNI.SDL_GetGamepadButtonLabel(handle, button.getValue()));
    }

    // ═══════════════════════════════════════════
    // Rumble & LED
    // ═══════════════════════════════════════════

    /**
     * Start a rumble effect.
     *
     * @param lowFrequency  low frequency intensity (0-65535)
     * @param highFrequency high frequency intensity (0-65535)
     * @param durationMs    duration in milliseconds
     * @return true on success
     */
    public boolean rumble(int lowFrequency, int highFrequency, int durationMs) {
        if (handle == 0) return false;
        return GamepadJNI.SDL_RumbleGamepad(handle, lowFrequency, highFrequency, durationMs);
    }

    /**
     * Start a trigger rumble effect.
     *
     * @param leftRumble  left trigger rumble (0-65535)
     * @param rightRumble right trigger rumble (0-65535)
     * @param durationMs  duration in milliseconds
     * @return true on success
     */
    public boolean rumbleTriggers(int leftRumble, int rightRumble, int durationMs) {
        if (handle == 0) return false;
        return GamepadJNI.SDL_RumbleGamepadTriggers(handle, leftRumble, rightRumble, durationMs);
    }

    /**
     * Set the gamepad LED color.
     *
     * @param red   red intensity (0-255)
     * @param green green intensity (0-255)
     * @param blue  blue intensity (0-255)
     * @return true on success
     */
    public boolean setLED(int red, int green, int blue) {
        if (handle == 0) return false;
        return GamepadJNI.SDL_SetGamepadLED(handle, (byte) red, (byte) green, (byte) blue);
    }

    // ═══════════════════════════════════════════
    // Touchpad
    // ═══════════════════════════════════════════

    /** Get the number of touchpads. */
    public int getTouchpadCount() {
        if (handle == 0) return 0;
        return GamepadJNI.SDL_GetNumGamepadTouchpads(handle);
    }

    /** Get the number of fingers on a touchpad. */
    public int getTouchpadFingerCount(int touchpad) {
        if (handle == 0) return 0;
        return GamepadJNI.SDL_GetNumGamepadTouchpadFingers(handle, touchpad);
    }

    /**
     * Get touchpad finger state.
     *
     * @param touchpad touchpad index
     * @param finger   finger index
     * @return float[4]: [0]=down(1.0/0.0), [1]=x, [2]=y, [3]=pressure
     */
    public float[] getTouchpadFinger(int touchpad, int finger) {
        if (handle == 0) return null;
        return GamepadJNI.SDL_GetGamepadTouchpadFinger(handle, touchpad, finger);
    }

    // ═══════════════════════════════════════════
    // Sensor
    // ═══════════════════════════════════════════

    /** Check if the gamepad has a particular sensor. */
    public boolean hasSensor(int sensorType) {
        if (handle == 0) return false;
        return GamepadJNI.SDL_GamepadHasSensor(handle, sensorType);
    }

    /** Enable or disable a gamepad sensor. */
    public boolean setSensorEnabled(int sensorType, boolean enabled) {
        if (handle == 0) return false;
        return GamepadJNI.SDL_SetGamepadSensorEnabled(handle, sensorType, enabled);
    }

    /** Check if a gamepad sensor is enabled. */
    public boolean isSensorEnabled(int sensorType) {
        if (handle == 0) return false;
        return GamepadJNI.SDL_GamepadSensorEnabled(handle, sensorType);
    }

    /** Get the data rate of a gamepad sensor. */
    public float getSensorDataRate(int sensorType) {
        if (handle == 0) return 0.0f;
        return GamepadJNI.SDL_GetGamepadSensorDataRate(handle, sensorType);
    }

    /**
     * Get the current sensor data.
     *
     * @param sensorType sensor type
     * @param numValues  number of values to read
     * @return sensor data array
     */
    public float[] getSensorData(int sensorType, int numValues) {
        if (handle == 0) return null;
        return GamepadJNI.SDL_GetGamepadSensorData(handle, sensorType, numValues);
    }

    @Override
    public String toString() {
        return "Gamepad{" +
                "instanceId=" + instanceId +
                ", name=" + getName() +
                ", connected=" + isConnected() +
                '}';
    }
}
