package com.ifels.gamepadjni;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class GamepadLog {
    private static final Logger LOGGER = LogManager.getLogger("gamepad-jni");

    private GamepadLog() {
    }

    public static void info(String msg, Object... args) {
        LOGGER.info(msg, args);
    }

    public static void error(String msg, Object... args) {
        LOGGER.error(msg, args);
    }

    public static void warn(String msg, Object... args) {
        LOGGER.warn(msg, args);
    }

    public static void debug(String msg, Object... args) {
        LOGGER.debug(msg, args);
    }
}
