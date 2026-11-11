package com.ifels.gamepadjni;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;

/**
 * SDL3 gamepad API bound through JNA direct mapping.
 *
 * <p>Signatures here are not the ones callers see: SDL's {@code bool} is one byte
 * while JNA maps Java {@code boolean} to four, so every predicate returns
 * {@code byte}; {@code const char*} becomes {@link Pointer} so the UTF-8
 * decoding stays on our side; and opaque handles become {@link Pointer}.
 * {@link JnaBinding} does the conversions.</p>
 *
 * <p>{@code SDL_GetGamepadGUIDForID} is deliberately absent: it returns a 16-byte
 * struct by value, which is ABI-specific. {@link JnaBinding} derives the same
 * string from the mapping string instead.</p>
 */
final class JnaGamepad {

    private JnaGamepad() {
    }

    /**
     * Binds every method above to the SDL3 module already loaded into this process.
     *
     * @throws UnsatisfiedLinkError when the library cannot be opened or a symbol is
     *                              missing — the caller falls back to JNI
     */
    static void install(SdlHandle sdl) {
        NativeLibrary library = NativeLibrary.getInstance(sdl.target());
        Native.register(JnaGamepad.class, library);
    }

    public static native byte SDL_Init(int flags);
    public static native byte SDL_InitSubSystem(int flags);
    public static native void SDL_Quit();
    public static native int SDL_GetVersion();
    public static native void SDL_QuitSubSystem(int flags);
    public static native int SDL_WasInit(int flags);
    public static native Pointer SDL_GetError();
    public static native void SDL_ClearError();
    public static native byte SDL_SetHintWithPriority(Pointer name, Pointer value, int priority);
    public static native byte SDL_SetHint(Pointer name, Pointer value);
    public static native int SDL_AddGamepadMapping(Pointer mapping);
    public static native int SDL_AddGamepadMappingsFromFile(Pointer file);
    public static native byte SDL_ReloadGamepadMappings();
    public static native Pointer SDL_GetGamepadMappingForID(int instanceId);
    public static native Pointer SDL_GetGamepadMapping(Pointer gamepad);
    public static native byte SDL_SetGamepadMapping(int instanceId, Pointer mapping);
    public static native byte SDL_HasGamepad();
    public static native Pointer SDL_GetGamepads(IntByReference count);
    public static native byte SDL_IsGamepad(int instanceId);
    public static native Pointer SDL_GetGamepadNameForID(int instanceId);
    public static native Pointer SDL_GetGamepadPathForID(int instanceId);
    public static native int SDL_GetGamepadPlayerIndexForID(int instanceId);
    public static native short SDL_GetGamepadVendorForID(int instanceId);
    public static native short SDL_GetGamepadProductForID(int instanceId);
    public static native short SDL_GetGamepadProductVersionForID(int instanceId);
    public static native int SDL_GetGamepadTypeForID(int instanceId);
    public static native int SDL_GetRealGamepadTypeForID(int instanceId);
    public static native Pointer SDL_OpenGamepad(int instanceId);
    public static native Pointer SDL_GetGamepadFromID(int instanceId);
    public static native Pointer SDL_GetGamepadFromPlayerIndex(int playerIndex);
    public static native void SDL_CloseGamepad(Pointer gamepad);
    public static native int SDL_GetGamepadID(Pointer gamepad);
    public static native Pointer SDL_GetGamepadName(Pointer gamepad);
    public static native Pointer SDL_GetGamepadPath(Pointer gamepad);
    public static native int SDL_GetGamepadType(Pointer gamepad);
    public static native int SDL_GetRealGamepadType(Pointer gamepad);
    public static native int SDL_GetGamepadPlayerIndex(Pointer gamepad);
    public static native byte SDL_SetGamepadPlayerIndex(Pointer gamepad, int playerIndex);
    public static native short SDL_GetGamepadVendor(Pointer gamepad);
    public static native short SDL_GetGamepadProduct(Pointer gamepad);
    public static native short SDL_GetGamepadProductVersion(Pointer gamepad);
    public static native short SDL_GetGamepadFirmwareVersion(Pointer gamepad);
    public static native Pointer SDL_GetGamepadSerial(Pointer gamepad);
    public static native long SDL_GetGamepadSteamHandle(Pointer gamepad);
    public static native int SDL_GetGamepadConnectionState(Pointer gamepad);
    public static native int SDL_GetGamepadPowerInfo(Pointer gamepad, IntByReference percent);
    public static native byte SDL_GamepadConnected(Pointer gamepad);
    public static native void SDL_UpdateGamepads();
    public static native void SDL_SetGamepadEventsEnabled(boolean enabled);
    public static native byte SDL_GamepadEventsEnabled();
    public static native byte SDL_GamepadHasAxis(Pointer gamepad, int axis);
    public static native short SDL_GetGamepadAxis(Pointer gamepad, int axis);
    public static native byte SDL_GamepadHasButton(Pointer gamepad, int button);
    public static native byte SDL_GetGamepadButton(Pointer gamepad, int button);
    public static native int SDL_GetGamepadButtonLabelForType(int type, int button);
    public static native int SDL_GetGamepadButtonLabel(Pointer gamepad, int button);
    public static native int SDL_GetGamepadTypeFromString(Pointer str);
    public static native Pointer SDL_GetGamepadStringForType(int type);
    public static native int SDL_GetGamepadAxisFromString(Pointer str);
    public static native Pointer SDL_GetGamepadStringForAxis(int axis);
    public static native int SDL_GetGamepadButtonFromString(Pointer str);
    public static native Pointer SDL_GetGamepadStringForButton(int button);
    public static native byte SDL_RumbleGamepad(Pointer gamepad, int lowFrequencyRumble, int highFrequencyRumble, int durationMs);
    public static native byte SDL_RumbleGamepadTriggers(Pointer gamepad, int leftRumble, int rightRumble, int durationMs);
    public static native byte SDL_SetGamepadLED(Pointer gamepad, byte red, byte green, byte blue);
    public static native int SDL_GetNumGamepadTouchpads(Pointer gamepad);
    public static native int SDL_GetNumGamepadTouchpadFingers(Pointer gamepad, int touchpad);
    public static native byte SDL_GetGamepadTouchpadFinger(Pointer gamepad, int touchpad, int finger, ByteByReference down, FloatByReference x, FloatByReference y, FloatByReference pressure);
    public static native byte SDL_GamepadHasSensor(Pointer gamepad, int type);
    public static native byte SDL_SetGamepadSensorEnabled(Pointer gamepad, int type, boolean enabled);
    public static native byte SDL_GamepadSensorEnabled(Pointer gamepad, int type);
    public static native float SDL_GetGamepadSensorDataRate(Pointer gamepad, int type);
    public static native byte SDL_GetGamepadSensorData(Pointer gamepad, int type, float[] data, int numValues);
    public static native void SDL_free(Pointer p);

    /** Used by SDL_UpdateGamepads on Linux to decide whether to pump the event loop. */
    public static native byte SDL_IsMainThread();

    public static native void SDL_PumpEvents();
}
