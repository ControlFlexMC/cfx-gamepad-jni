package com.ifels.gamepadjni;

import com.sun.jna.Memory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Behaviour of the JNA adapter that does not need a registered library.
 *
 * <p>Everything here returns before touching {@link JnaGamepad}: with no backend
 * installed, any call that reached a native method would raise
 * {@link UnsatisfiedLinkError}, so a passing test proves the early return exists.</p>
 */
class JnaBindingTest {

    private static final JnaBinding BINDING = JnaBinding.INSTANCE;

    @Test
    void handleMethodsShortCircuitOnZero() {
        assertEquals(0, BINDING.SDL_GetGamepadID(0));
        assertNull(BINDING.SDL_GetGamepadName(0));
        assertNull(BINDING.SDL_GetGamepadPath(0));
        assertEquals(0, BINDING.SDL_GetGamepadType(0));
        assertEquals(0, BINDING.SDL_GetRealGamepadType(0));
        assertEquals(-1, BINDING.SDL_GetGamepadPlayerIndex(0));
        assertFalse(BINDING.SDL_SetGamepadPlayerIndex(0, 0));
        assertEquals(0, BINDING.SDL_GetGamepadVendor(0));
        assertEquals(0, BINDING.SDL_GetGamepadProduct(0));
        assertEquals(0, BINDING.SDL_GetGamepadProductVersion(0));
        assertEquals(0, BINDING.SDL_GetGamepadFirmwareVersion(0));
        assertNull(BINDING.SDL_GetGamepadSerial(0));
        assertEquals(0L, BINDING.SDL_GetGamepadSteamHandle(0));
        assertEquals(-1, BINDING.SDL_GetGamepadConnectionState(0));
        assertNull(BINDING.SDL_GetGamepadPowerInfo(0));
        assertNull(BINDING.SDL_GetGamepadMapping(0));
        assertFalse(BINDING.SDL_GamepadConnected(0));
        assertFalse(BINDING.SDL_GamepadHasAxis(0, 0));
        assertEquals(0, BINDING.SDL_GetGamepadAxis(0, 0));
        assertFalse(BINDING.SDL_GamepadHasButton(0, 0));
        assertFalse(BINDING.SDL_GetGamepadButton(0, 0));
        assertEquals(0, BINDING.SDL_GetGamepadButtonLabel(0, 0));
        assertFalse(BINDING.SDL_RumbleGamepad(0, 0, 0, 0));
        assertFalse(BINDING.SDL_RumbleGamepadTriggers(0, 0, 0, 0));
        assertFalse(BINDING.SDL_SetGamepadLED(0, (byte) 0, (byte) 0, (byte) 0));
        assertEquals(0, BINDING.SDL_GetNumGamepadTouchpads(0));
        assertEquals(0, BINDING.SDL_GetNumGamepadTouchpadFingers(0, 0));
        assertNull(BINDING.SDL_GetGamepadTouchpadFinger(0, 0, 0));
        assertFalse(BINDING.SDL_GamepadHasSensor(0, 0));
        assertFalse(BINDING.SDL_SetGamepadSensorEnabled(0, 0, true));
        assertFalse(BINDING.SDL_GamepadSensorEnabled(0, 0));
        assertEquals(0.0f, BINDING.SDL_GetGamepadSensorDataRate(0, 0));
        assertNull(BINDING.SDL_GetGamepadSensorData(0, 0, 3));
        BINDING.SDL_CloseGamepad(0);
    }

    @Test
    void showCursorDoesNotTouchUser32OffWindows() {
        if (JnaUser32.isWindows()) {
            return;
        }
        assertEquals(0, BINDING.showCursor(true));
    }

    @Test
    void cstrAndUtf8RoundTripNonAscii() {
        String name = "Xbox \u65e0\u7ebf\u63a7\u5236\u5668";
        Memory encoded = JnaBinding.cstr(name);
        assertEquals(name, JnaBinding.utf8(encoded));
        assertEquals(name.getBytes(StandardCharsets.UTF_8).length + 1, encoded.size());
    }

    @Test
    void utf8HandlesNullAndEmpty() {
        assertNull(JnaBinding.utf8(null));
        assertEquals("", JnaBinding.utf8(JnaBinding.cstr("")));
        assertNull(JnaBinding.cstr(null));
    }
}
