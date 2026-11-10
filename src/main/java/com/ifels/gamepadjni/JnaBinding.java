package com.ifels.gamepadjni;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;

import java.nio.charset.StandardCharsets;

/**
 * {@link GamepadBinding} backed by JNA direct mapping onto SDL3.
 *
 * <p>Three conversions happen here and nowhere else:</p>
 * <ul>
 *   <li>SDL's {@code bool} is one byte, so {@link JnaGamepad} returns {@code byte}
 *       and this class turns it into {@code boolean};</li>
 *   <li>every {@code char*} is a {@link Pointer} decoded as UTF-8 — the platform
 *       default would mangle controller names on a Chinese Windows install;</li>
 *   <li>{@code long} handles become {@link Pointer}, and a zero handle short-circuits
 *       to the same value the C implementation returns.</li>
 * </ul>
 *
 * <p>{{@code SDL_GetGamepadGUIDForID}} is derived from the mapping string, whose first
 * comma-separated field is the same 32-character GUID — that avoids returning a
 * 16-byte struct by value, which is ABI-specific.</p>
 */
final class JnaBinding implements GamepadBinding {

    static final JnaBinding INSTANCE = new JnaBinding();

    private JnaBinding() {
    }

    @Override
    public boolean SDL_Init(int flags) {
        return JnaGamepad.SDL_Init(flags) != 0;
    }
    @Override
    public boolean SDL_InitSubSystem(int flags) {
        return JnaGamepad.SDL_InitSubSystem(flags) != 0;
    }
    @Override
    public void SDL_Quit() {
        JnaGamepad.SDL_Quit();
    }
    @Override
    public int SDL_GetVersion() {
        return JnaGamepad.SDL_GetVersion();
    }
    @Override
    public void SDL_QuitSubSystem(int flags) {
        JnaGamepad.SDL_QuitSubSystem(flags);
    }
    @Override
    public int SDL_WasInit(int flags) {
        return JnaGamepad.SDL_WasInit(flags);
    }
    @Override
    public String SDL_GetError() {
        return utf8(JnaGamepad.SDL_GetError());
    }
    @Override
    public void SDL_ClearError() {
        JnaGamepad.SDL_ClearError();
    }
    @Override
    public boolean SDL_SetHintWithPriority(String name, String value, int priority) {
        Memory nameMem = cstr(name);
        Memory valueMem = cstr(value);
        return JnaGamepad.SDL_SetHintWithPriority(nameMem, valueMem, priority) != 0;
    }
    @Override
    public boolean SDL_SetHint(String name, String value) {
        Memory nameMem = cstr(name);
        Memory valueMem = cstr(value);
        return JnaGamepad.SDL_SetHint(nameMem, valueMem) != 0;
    }
    @Override
    public int SDL_AddGamepadMapping(String mapping) {
        Memory mappingMem = cstr(mapping);
        return JnaGamepad.SDL_AddGamepadMapping(mappingMem);
    }
    @Override
    public int SDL_AddGamepadMappingsFromFile(String file) {
        Memory fileMem = cstr(file);
        return JnaGamepad.SDL_AddGamepadMappingsFromFile(fileMem);
    }
    @Override
    public boolean SDL_ReloadGamepadMappings() {
        return JnaGamepad.SDL_ReloadGamepadMappings() != 0;
    }
    @Override
    public String SDL_GetGamepadMappingForID(int instanceId) {
        Pointer text = JnaGamepad.SDL_GetGamepadMappingForID(instanceId);
        if (text == null) {
            return null;
        }
        try {
            return utf8(text);
        } finally {
            JnaGamepad.SDL_free(text);
        }
    }
    @Override
    public String SDL_GetGamepadMapping(long gamepad) {
        if (gamepad == 0) {
            return null;
        }
        Pointer text = JnaGamepad.SDL_GetGamepadMapping(handle(gamepad));
        if (text == null) {
            return null;
        }
        try {
            return utf8(text);
        } finally {
            JnaGamepad.SDL_free(text);
        }
    }
    @Override
    public boolean SDL_SetGamepadMapping(int instanceId, String mapping) {
        Memory mappingMem = cstr(mapping);
        return JnaGamepad.SDL_SetGamepadMapping(instanceId, mappingMem) != 0;
    }
    @Override
    public boolean SDL_HasGamepad() {
        return JnaGamepad.SDL_HasGamepad() != 0;
    }
    @Override
    public int[] SDL_GetGamepads() {
        IntByReference count = new IntByReference();
        Pointer ids = JnaGamepad.SDL_GetGamepads(count);
        if (ids == null) {
            return null;
        }
        try {
            int n = count.getValue();
            return n <= 0 ? null : ids.getIntArray(0, n);
        } finally {
            JnaGamepad.SDL_free(ids);
        }
    }
    @Override
    public boolean SDL_IsGamepad(int instanceId) {
        return JnaGamepad.SDL_IsGamepad(instanceId) != 0;
    }
    @Override
    public String SDL_GetGamepadNameForID(int instanceId) {
        return utf8(JnaGamepad.SDL_GetGamepadNameForID(instanceId));
    }
    @Override
    public String SDL_GetGamepadPathForID(int instanceId) {
        return utf8(JnaGamepad.SDL_GetGamepadPathForID(instanceId));
    }
    @Override
    public int SDL_GetGamepadPlayerIndexForID(int instanceId) {
        return JnaGamepad.SDL_GetGamepadPlayerIndexForID(instanceId);
    }
    @Override
    public String SDL_GetGamepadGUIDForID(int instanceId) {
        Pointer mapping = JnaGamepad.SDL_GetGamepadMappingForID(instanceId);
        if (mapping == null) {
            return ZERO_GUID;
        }
        try {
            String text = utf8(mapping);
            int comma = text == null ? -1 : text.indexOf(',');
            return comma > 0 ? text.substring(0, comma) : ZERO_GUID;
        } finally {
            JnaGamepad.SDL_free(mapping);
        }
    }
    @Override
    public short SDL_GetGamepadVendorForID(int instanceId) {
        return JnaGamepad.SDL_GetGamepadVendorForID(instanceId);
    }
    @Override
    public short SDL_GetGamepadProductForID(int instanceId) {
        return JnaGamepad.SDL_GetGamepadProductForID(instanceId);
    }
    @Override
    public short SDL_GetGamepadProductVersionForID(int instanceId) {
        return JnaGamepad.SDL_GetGamepadProductVersionForID(instanceId);
    }
    @Override
    public int SDL_GetGamepadTypeForID(int instanceId) {
        return JnaGamepad.SDL_GetGamepadTypeForID(instanceId);
    }
    @Override
    public int SDL_GetRealGamepadTypeForID(int instanceId) {
        return JnaGamepad.SDL_GetRealGamepadTypeForID(instanceId);
    }
    @Override
    public long SDL_OpenGamepad(int instanceId) {
        return Pointer.nativeValue(JnaGamepad.SDL_OpenGamepad(instanceId));
    }
    @Override
    public long SDL_GetGamepadFromID(int instanceId) {
        return Pointer.nativeValue(JnaGamepad.SDL_GetGamepadFromID(instanceId));
    }
    @Override
    public long SDL_GetGamepadFromPlayerIndex(int playerIndex) {
        return Pointer.nativeValue(JnaGamepad.SDL_GetGamepadFromPlayerIndex(playerIndex));
    }
    @Override
    public void SDL_CloseGamepad(long gamepad) {
        JnaGamepad.SDL_CloseGamepad(handle(gamepad));
    }
    @Override
    public int SDL_GetGamepadID(long gamepad) {
        return JnaGamepad.SDL_GetGamepadID(handle(gamepad));
    }
    @Override
    public String SDL_GetGamepadName(long gamepad) {
        return utf8(JnaGamepad.SDL_GetGamepadName(handle(gamepad)));
    }
    @Override
    public String SDL_GetGamepadPath(long gamepad) {
        return utf8(JnaGamepad.SDL_GetGamepadPath(handle(gamepad)));
    }
    @Override
    public int SDL_GetGamepadType(long gamepad) {
        return JnaGamepad.SDL_GetGamepadType(handle(gamepad));
    }
    @Override
    public int SDL_GetRealGamepadType(long gamepad) {
        return JnaGamepad.SDL_GetRealGamepadType(handle(gamepad));
    }
    @Override
    public int SDL_GetGamepadPlayerIndex(long gamepad) {
        if (gamepad == 0) {
            return -1;
        }
        return JnaGamepad.SDL_GetGamepadPlayerIndex(handle(gamepad));
    }
    @Override
    public boolean SDL_SetGamepadPlayerIndex(long gamepad, int playerIndex) {
        if (gamepad == 0) {
            return false;
        }
        return JnaGamepad.SDL_SetGamepadPlayerIndex(handle(gamepad), playerIndex) != 0;
    }
    @Override
    public short SDL_GetGamepadVendor(long gamepad) {
        return JnaGamepad.SDL_GetGamepadVendor(handle(gamepad));
    }
    @Override
    public short SDL_GetGamepadProduct(long gamepad) {
        return JnaGamepad.SDL_GetGamepadProduct(handle(gamepad));
    }
    @Override
    public short SDL_GetGamepadProductVersion(long gamepad) {
        return JnaGamepad.SDL_GetGamepadProductVersion(handle(gamepad));
    }
    @Override
    public short SDL_GetGamepadFirmwareVersion(long gamepad) {
        return JnaGamepad.SDL_GetGamepadFirmwareVersion(handle(gamepad));
    }
    @Override
    public String SDL_GetGamepadSerial(long gamepad) {
        return utf8(JnaGamepad.SDL_GetGamepadSerial(handle(gamepad)));
    }
    @Override
    public long SDL_GetGamepadSteamHandle(long gamepad) {
        if (gamepad == 0) {
            return 0L;
        }
        return JnaGamepad.SDL_GetGamepadSteamHandle(handle(gamepad));
    }
    @Override
    public int SDL_GetGamepadConnectionState(long gamepad) {
        if (gamepad == 0) {
            return -1;
        }
        return JnaGamepad.SDL_GetGamepadConnectionState(handle(gamepad));
    }
    @Override
    public int[] SDL_GetGamepadPowerInfo(long gamepad) {
        if (gamepad == 0) {
            return null;
        }
        IntByReference percent = new IntByReference(-1);
        int state = JnaGamepad.SDL_GetGamepadPowerInfo(handle(gamepad), percent);
        return new int[]{state, percent.getValue()};
    }
    @Override
    public boolean SDL_GamepadConnected(long gamepad) {
        if (gamepad == 0) {
            return false;
        }
        return JnaGamepad.SDL_GamepadConnected(handle(gamepad)) != 0;
    }
    @Override
    public void SDL_UpdateGamepads() {
        JnaGamepad.SDL_UpdateGamepads();
    }
    @Override
    public void SDL_SetGamepadEventsEnabled(boolean enabled) {
        JnaGamepad.SDL_SetGamepadEventsEnabled(enabled);
    }
    @Override
    public boolean SDL_GamepadEventsEnabled() {
        return JnaGamepad.SDL_GamepadEventsEnabled() != 0;
    }
    @Override
    public boolean SDL_GamepadHasAxis(long gamepad, int axis) {
        if (gamepad == 0) {
            return false;
        }
        return JnaGamepad.SDL_GamepadHasAxis(handle(gamepad), axis) != 0;
    }
    @Override
    public short SDL_GetGamepadAxis(long gamepad, int axis) {
        return JnaGamepad.SDL_GetGamepadAxis(handle(gamepad), axis);
    }
    @Override
    public boolean SDL_GamepadHasButton(long gamepad, int button) {
        if (gamepad == 0) {
            return false;
        }
        return JnaGamepad.SDL_GamepadHasButton(handle(gamepad), button) != 0;
    }
    @Override
    public boolean SDL_GetGamepadButton(long gamepad, int button) {
        return JnaGamepad.SDL_GetGamepadButton(handle(gamepad), button) != 0;
    }
    @Override
    public int SDL_GetGamepadButtonLabelForType(int type, int button) {
        return JnaGamepad.SDL_GetGamepadButtonLabelForType(type, button);
    }
    @Override
    public int SDL_GetGamepadButtonLabel(long gamepad, int button) {
        return JnaGamepad.SDL_GetGamepadButtonLabel(handle(gamepad), button);
    }
    @Override
    public int SDL_GetGamepadTypeFromString(String str) {
        Memory strMem = cstr(str);
        return JnaGamepad.SDL_GetGamepadTypeFromString(strMem);
    }
    @Override
    public String SDL_GetGamepadStringForType(int type) {
        return utf8(JnaGamepad.SDL_GetGamepadStringForType(type));
    }
    @Override
    public int SDL_GetGamepadAxisFromString(String str) {
        Memory strMem = cstr(str);
        return JnaGamepad.SDL_GetGamepadAxisFromString(strMem);
    }
    @Override
    public String SDL_GetGamepadStringForAxis(int axis) {
        return utf8(JnaGamepad.SDL_GetGamepadStringForAxis(axis));
    }
    @Override
    public int SDL_GetGamepadButtonFromString(String str) {
        Memory strMem = cstr(str);
        return JnaGamepad.SDL_GetGamepadButtonFromString(strMem);
    }
    @Override
    public String SDL_GetGamepadStringForButton(int button) {
        return utf8(JnaGamepad.SDL_GetGamepadStringForButton(button));
    }
    @Override
    public boolean SDL_RumbleGamepad(long gamepad, int lowFrequencyRumble, int highFrequencyRumble, int durationMs) {
        if (gamepad == 0) {
            return false;
        }
        return JnaGamepad.SDL_RumbleGamepad(handle(gamepad), lowFrequencyRumble, highFrequencyRumble, durationMs) != 0;
    }
    @Override
    public boolean SDL_RumbleGamepadTriggers(long gamepad, int leftRumble, int rightRumble, int durationMs) {
        if (gamepad == 0) {
            return false;
        }
        return JnaGamepad.SDL_RumbleGamepadTriggers(handle(gamepad), leftRumble, rightRumble, durationMs) != 0;
    }
    @Override
    public boolean SDL_SetGamepadLED(long gamepad, byte red, byte green, byte blue) {
        if (gamepad == 0) {
            return false;
        }
        return JnaGamepad.SDL_SetGamepadLED(handle(gamepad), red, green, blue) != 0;
    }
    @Override
    public int SDL_GetNumGamepadTouchpads(long gamepad) {
        return JnaGamepad.SDL_GetNumGamepadTouchpads(handle(gamepad));
    }
    @Override
    public int SDL_GetNumGamepadTouchpadFingers(long gamepad, int touchpad) {
        return JnaGamepad.SDL_GetNumGamepadTouchpadFingers(handle(gamepad), touchpad);
    }
    @Override
    public float[] SDL_GetGamepadTouchpadFinger(long gamepad, int touchpad, int finger) {
        if (gamepad == 0) {
            return null;
        }
        ByteByReference down = new ByteByReference();
        FloatByReference x = new FloatByReference();
        FloatByReference y = new FloatByReference();
        FloatByReference pressure = new FloatByReference();
        if (JnaGamepad.SDL_GetGamepadTouchpadFinger(
                handle(gamepad), touchpad, finger, down, x, y, pressure) == 0) {
            return null;
        }
        return new float[]{down.getValue() != 0 ? 1.0f : 0.0f, x.getValue(), y.getValue(), pressure.getValue()};
    }
    @Override
    public boolean SDL_GamepadHasSensor(long gamepad, int type) {
        if (gamepad == 0) {
            return false;
        }
        return JnaGamepad.SDL_GamepadHasSensor(handle(gamepad), type) != 0;
    }
    @Override
    public boolean SDL_SetGamepadSensorEnabled(long gamepad, int type, boolean enabled) {
        if (gamepad == 0) {
            return false;
        }
        return JnaGamepad.SDL_SetGamepadSensorEnabled(handle(gamepad), type, enabled) != 0;
    }
    @Override
    public boolean SDL_GamepadSensorEnabled(long gamepad, int type) {
        if (gamepad == 0) {
            return false;
        }
        return JnaGamepad.SDL_GamepadSensorEnabled(handle(gamepad), type) != 0;
    }
    @Override
    public float SDL_GetGamepadSensorDataRate(long gamepad, int type) {
        if (gamepad == 0) {
            return 0.0f;
        }
        return JnaGamepad.SDL_GetGamepadSensorDataRate(handle(gamepad), type);
    }
    @Override
    public float[] SDL_GetGamepadSensorData(long gamepad, int type, int numValues) {
        if (gamepad == 0 || numValues <= 0) {
            return null;
        }
        float[] data = new float[numValues];
        return JnaGamepad.SDL_GetGamepadSensorData(handle(gamepad), type, data, numValues) != 0
                ? data : null;
    }
    @Override
    public int getGCControllerCount() {
        // Our own macOS helper, not an SDL3 export: it cannot be bound here, and
        // Native.register resolves every method of the class in one shot, so a
        // missing symbol would take the whole backend down with it.
        // -1 makes GamepadManager.checkMacDisconnect skip, i.e. "not supported".
        return -1;
    }
    @Override
    public int showCursor(boolean hide) {
        return JnaUser32.isInstalled() ? JnaUser32.ShowCursor(hide) : 0;
    }

    private static final String ZERO_GUID = "00000000000000000000000000000000";

    private static Pointer handle(long value) {
        return value == 0 ? null : new Pointer(value);
    }

    private static Memory cstr(String value) {
        if (value == null) {
            return null;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        Memory memory = new Memory(bytes.length + 1L);
        memory.write(0, bytes, 0, bytes.length);
        memory.setByte(bytes.length, (byte) 0);
        return memory;
    }

    private static String utf8(Pointer text) {
        if (text == null) {
            return null;
        }
        int length = 0;
        while (text.getByte(length) != 0) {
            length++;
        }
        return new String(text.getByteArray(0, length), StandardCharsets.UTF_8);
    }
}
