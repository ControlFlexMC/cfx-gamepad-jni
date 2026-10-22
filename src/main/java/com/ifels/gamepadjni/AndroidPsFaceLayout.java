package com.ifels.gamepadjni;

/**
 * Android-only correction for PlayStation face-button bindings.
 *
 * <p>Android enumerates a DualSense through the generic HID driver rather than
 * {@code hid-playstation}, so the face buttons arrive in Sony's HID report order
 * but carry Xbox keycode names: Square→{@code BUTTON_A}, Cross→{@code BUTTON_B},
 * Circle→{@code BUTTON_C}, Triangle→{@code BUTTON_X}. SDL's Android driver turns
 * those into raw indices 0/1/17/2, while the built-in {@code PS5 Controller}
 * entry (GUID {@code 050000004c050000e60c0000fffe3f80}) binds
 * {@code a:b0,b:b1,x:b2,y:b17} — a one-step rotation of the four face buttons.</p>
 *
 * <p>SDL changed that entry in "Fixed the PS5 controller face buttons on Amazon
 * Fire TV, Android 11" (09495059d). Both layouts share the same GUID, because a
 * GUID encodes vendor/product plus capability bitmaps and cannot express which
 * physical button sits on which index. This class therefore defaults to the
 * Sony order and keeps the Fire TV order behind an explicit override.</p>
 *
 * <p>Pure functions only: no SDL calls, no logging, no I/O.</p>
 */
public final class AndroidPsFaceLayout {

    /** System property that selects SDL's Fire TV layout instead of the Sony order. */
    public static final String LAYOUT_PROPERTY = "cfx.gamepadjni.androidPsFaceLayout";

    /** Override value that leaves SDL's built-in mapping untouched. */
    public static final String LAYOUT_FIRE_TV = "FireTV";

    private static final int SONY_VENDOR_ID = 0x054C;
    private static final int DUALSENSE_PRODUCT_ID = 0x0CE6;
    private static final int DUALSENSE_EDGE_PRODUCT_ID = 0x0DF2;

    /** Raw SDL indices this device class reports each face button on. */
    private static final String IDX_CROSS = "b1";
    private static final String IDX_CIRCLE = "b17";
    private static final String IDX_SQUARE = "b0";
    private static final String IDX_TRIANGLE = "b2";

    private AndroidPsFaceLayout() {
    }

    /**
     * Whether the Sony face-button order should be applied to this gamepad.
     *
     * @param android    whether the process runs under an Android launcher
     * @param type       SDL gamepad type reported for the device
     * @param vendorId   USB vendor ID
     * @param productId  USB product ID
     * @param override   value of {@link #LAYOUT_PROPERTY}; {@code null} when unset
     * @return {@code true} when the four face buttons must be rewritten
     */
    public static boolean shouldApply(boolean android, GamepadType type,
                                      short vendorId, short productId, String override) {
        if (!android) {
            return false;
        }
        if (isFireTvOverride(override)) {
            return false;
        }
        return isDualSense(type, vendorId, productId);
    }

    /**
     * Rewrite the {@code a}/{@code b}/{@code x}/{@code y} fields to the Sony order.
     *
     * <p>Every other field is copied verbatim, so keys that merely end in
     * {@code x} or {@code y} ({@code leftx}, {@code righty}, …) are untouched.
     * The rewrite is idempotent, and a mapping without face buttons — or
     * {@code null} — comes back unchanged.</p>
     *
     * @param mapping SDL mapping string, e.g. {@code "guid,name,a:b0,b:b1,..."}
     * @return the mapping with Sony-ordered face buttons
     */
    public static String corrected(String mapping) {
        if (mapping == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(mapping.length() + 8);
        int start = 0;
        while (start <= mapping.length()) {
            int comma = mapping.indexOf(',', start);
            int end = comma < 0 ? mapping.length() : comma;
            appendField(out, mapping.substring(start, end));
            if (comma < 0) {
                break;
            }
            out.append(',');
            start = comma + 1;
        }
        return out.toString();
    }

    private static void appendField(StringBuilder out, String field) {
        int colon = field.indexOf(':');
        if (colon > 0) {
            String key = field.substring(0, colon);
            String index = faceButtonIndex(key);
            if (index != null) {
                out.append(key).append(':').append(index);
                return;
            }
        }
        out.append(field);
    }

    private static String faceButtonIndex(String key) {
        if ("a".equals(key)) {
            return IDX_CROSS;
        }
        if ("b".equals(key)) {
            return IDX_CIRCLE;
        }
        if ("x".equals(key)) {
            return IDX_SQUARE;
        }
        if ("y".equals(key)) {
            return IDX_TRIANGLE;
        }
        return null;
    }

    private static boolean isFireTvOverride(String override) {
        return override != null && LAYOUT_FIRE_TV.equalsIgnoreCase(override.trim());
    }

    private static boolean isDualSense(GamepadType type, short vendorId, short productId) {
        if (type == GamepadType.PS5) {
            return true;
        }
        if ((vendorId & 0xFFFF) != SONY_VENDOR_ID) {
            return false;
        }
        int pid = productId & 0xFFFF;
        return pid == DUALSENSE_PRODUCT_ID || pid == DUALSENSE_EDGE_PRODUCT_ID;
    }
}
