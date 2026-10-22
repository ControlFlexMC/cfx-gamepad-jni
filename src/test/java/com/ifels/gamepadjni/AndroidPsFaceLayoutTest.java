package com.ifels.gamepadjni;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-function tests for the Android PlayStation face-button layout correction.
 *
 * <p>Detection and rewriting take explicit arguments, so tests do not mock
 * system properties or touch SDL.</p>
 */
class AndroidPsFaceLayoutTest {

    /** Verbatim SDL entry for a DualSense enumerated by Android's generic HID driver. */
    private static final String SDL_PS5_MAPPING =
            "050000004c050000e60c0000fffe3f80,PS5 Controller,a:b0,b:b1,back:b15,dpdown:b12,"
            + "dpleft:b13,dpright:b14,dpup:b11,guide:b5,leftshoulder:b3,leftstick:b4,"
            + "lefttrigger:a2,leftx:a0,lefty:a1,rightshoulder:b18,rightstick:b6,righttrigger:a3,"
            + "rightx:a4,righty:a5,start:b16,x:b2,y:b17,";

    private static final String CORRECTED_PS5_MAPPING =
            "050000004c050000e60c0000fffe3f80,PS5 Controller,a:b1,b:b17,back:b15,dpdown:b12,"
            + "dpleft:b13,dpright:b14,dpup:b11,guide:b5,leftshoulder:b3,leftstick:b4,"
            + "lefttrigger:a2,leftx:a0,lefty:a1,rightshoulder:b18,rightstick:b6,righttrigger:a3,"
            + "rightx:a4,righty:a5,start:b16,x:b0,y:b2,";

    // ── corrected(): rewrite only the four face buttons ──────────────────

    @Test
    void correctsTheFourFaceButtonsAndNothingElse() {
        assertEquals(CORRECTED_PS5_MAPPING, AndroidPsFaceLayout.corrected(SDL_PS5_MAPPING));
    }

    @Test
    void correctionIsIdempotent() {
        String once = AndroidPsFaceLayout.corrected(SDL_PS5_MAPPING);

        assertEquals(once, AndroidPsFaceLayout.corrected(once));
    }

    @Test
    void doesNotTouchAxisKeysEndingInXOrY() {
        String corrected = AndroidPsFaceLayout.corrected(SDL_PS5_MAPPING);

        assertTrue(corrected.contains(",leftx:a0,"));
        assertTrue(corrected.contains(",lefty:a1,"));
        assertTrue(corrected.contains(",rightx:a4,"));
        assertTrue(corrected.contains(",righty:a5,"));
    }

    @Test
    void nullMappingIsReturnedUnchanged() {
        assertNull(AndroidPsFaceLayout.corrected(null));
    }

    @Test
    void mappingWithoutFaceButtonsIsReturnedUnchanged() {
        String input = "050000004c050000e60c0000fffe3f80,PS5 Controller,leftx:a0,lefty:a1,";

        assertEquals(input, AndroidPsFaceLayout.corrected(input));
    }

    // ── shouldApply(): Android + DualSense + no FireTV override ──────────

    @Test
    void appliesOnAndroidPs5WithoutOverride() {
        assertTrue(AndroidPsFaceLayout.shouldApply(
                true, GamepadType.PS5, (short) 0x054C, (short) 0x0CE6, null));
    }

    @Test
    void appliesOnAndroidDualSenseIdentifiedByVidPid() {
        assertTrue(AndroidPsFaceLayout.shouldApply(
                true, GamepadType.UNKNOWN, (short) 0x054C, (short) 0x0CE6, null));
    }

    @Test
    void appliesOnAndroidDualSenseEdgeIdentifiedByVidPid() {
        assertTrue(AndroidPsFaceLayout.shouldApply(
                true, GamepadType.UNKNOWN, (short) 0x054C, (short) 0x0DF2, null));
    }

    @Test
    void doesNotApplyOutsideAndroid() {
        assertFalse(AndroidPsFaceLayout.shouldApply(
                false, GamepadType.PS5, (short) 0x054C, (short) 0x0CE6, null));
    }

    @Test
    void doesNotApplyToNonSonyPads() {
        assertFalse(AndroidPsFaceLayout.shouldApply(
                true, GamepadType.XBOXONE, (short) 0x045E, (short) 0x0B12, null));
    }

    @Test
    void fireTvOverrideKeepsSdlMapping() {
        assertFalse(AndroidPsFaceLayout.shouldApply(
                true, GamepadType.PS5, (short) 0x054C, (short) 0x0CE6, "FireTV"));
        assertFalse(AndroidPsFaceLayout.shouldApply(
                true, GamepadType.PS5, (short) 0x054C, (short) 0x0CE6, "firetv"));
    }

    @Test
    void unknownOverrideValueFallsBackToCorrection() {
        assertTrue(AndroidPsFaceLayout.shouldApply(
                true, GamepadType.PS5, (short) 0x054C, (short) 0x0CE6, "something-else"));
    }
}
