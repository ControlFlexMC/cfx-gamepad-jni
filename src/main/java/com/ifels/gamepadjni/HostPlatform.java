package com.ifels.gamepadjni;

/**
 * Which kind of game JVM this process is running in.
 *
 * <p>Public platform API for callers such as ControlFlex. Prefer
 * {@link #current()} / {@link GamepadManager#hostPlatform()} over stacking
 * boolean helpers: Android and iOS can be added without changing the call
 * shape. Detection does not load natives and does not require
 * {@link GamepadManager#initialize}.</p>
 *
 * <p>{@link #ANDROID} is {@code os.version} starting with {@code Android-}
 * (case-insensitive), same rule as {@link AndroidPlatform}. {@link #IOS} is
 * reserved; no launcher convention is implemented yet, so {@link #detect}
 * never returns it.</p>
 */
public enum HostPlatform {

    /** Desktop JVM (Windows / macOS / Linux). */
    DESKTOP,

    /** Android Minecraft launcher game JVM. */
    ANDROID,

    /** iOS Minecraft launcher game JVM (reserved, not detected yet). */
    IOS;

    private static final HostPlatform CURRENT = detect(
            System.getProperty("os.version", ""));

    /**
     * Cached host for this process, read from {@code os.version} at class load.
     */
    public static HostPlatform current() {
        return CURRENT;
    }

    /**
     * Resolves the host from an explicit {@code os.version}. Package-private
     * so tests can drive it without touching process properties.
     */
    static HostPlatform detect(String osVersion) {
        if (AndroidPlatform.detect(osVersion)) {
            return ANDROID;
        }
        return DESKTOP;
    }

    public boolean isDesktop() {
        return this == DESKTOP;
    }

    public boolean isAndroid() {
        return this == ANDROID;
    }

    public boolean isIos() {
        return this == IOS;
    }
}
