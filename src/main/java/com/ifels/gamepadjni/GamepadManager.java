package com.ifels.gamepadjni;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleton manager for SDL3 gamepad lifecycle and device management.
 *
 * <p>This is the main entry point for the gamepad-jni library.
 * It handles SDL initialization/shutdown, gamepad discovery, hotplug
 * detection, and provides access to connected gamepads.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * GamepadManager mgr = GamepadManager.getInstance();
 *
 * // Initialize SDL and load native libraries
 * if (!mgr.initialize()) {
 *     System.err.println("Failed to initialize SDL3");
 *     return;
 * }
 *
 * // Start background polling for gamepad state updates
 * mgr.startPollThread();
 *
 * // Open a gamepad
 * Gamepad gamepad = mgr.openGamepad(instanceId);
 *
 * // ... use gamepad ...
 *
 * // Cleanup
 * mgr.stopPollThread();
 * mgr.shutdown();
 * }</pre>
 */
public class GamepadManager {

    /** SDL initialization flags for gamepad support. */
    private static final int SDL_INIT_GAMEPAD = 0x00002000;
    private static final int SDL_INIT_JOYSTICK = 0x00000200;

    /** Cached OS check to avoid repeated string operations. */
    private static final boolean IS_MAC =
            System.getProperty("os.name", "").toLowerCase().contains("mac");

    private static final Object LOCK = new Object();
    private static volatile GamepadManager instance;

    private volatile boolean initialized = false;
    private volatile boolean pollThreadRunning = false;
    private Thread pollThread;

    /** Connected gamepads: instanceId -> Gamepad. */
    private final ConcurrentHashMap<Integer, Gamepad> gamepads = new ConcurrentHashMap<>();

    /** Ordered list of connected gamepad instance IDs. */
    private final CopyOnWriteArrayList<Integer> gamepadOrder = new CopyOnWriteArrayList<>();

    /** Gamepad connection listeners. */
    private final CopyOnWriteArrayList<GamepadListener> listeners = new CopyOnWriteArrayList<>();

    private int updateTickCounter = 0;

    private GamepadManager() {
    }

    /** Get the singleton instance. */
    public static GamepadManager getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new GamepadManager();
                }
            }
        }
        return instance;
    }

    // ═══════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════

    /**
     * Initialize SDL3 and load native libraries.
     *
     * <p>This must be called before any other gamepad operations.
     * It loads the JNI native library and SDL3 shared library,
     * then initializes the SDL3 gamepad subsystem.</p>
     *
     * @return true on success, false on failure
     */
    public boolean initialize() {
        if (initialized) {
            return true;
        }

        try {
            // Load the JNI native library
            NativeLibraryLoader.load();

            // macOS driver strategy:
            // - MFI (GCController): ENABLED - only way to discover Bluetooth gamepads
            // - IOKit: ENABLED - handles USB/wired gamepads
            // - HIDAPI: DISABLED - its IOHIDManager conflicts with GLFW's RunLoop
            //   on the render thread, causing EXC_BREAKPOINT (PAC signature failure)
            //   during repeated connect/disconnect cycles.
            // Disconnect detection is handled at Java layer via checkMacDisconnect()
            // which directly queries [GCController controllers].count.
            if (IS_MAC) {
                GamepadJNI.SDL_SetHintWithPriority(
                        "SDL_JOYSTICK_HIDAPI", "0", 2 /* SDL_HINT_OVERRIDE */);
                GamepadLog.info("macOS: disabled HIDAPI driver (using MFI + IOKit)");
            }

            // Initialize SDL3 with gamepad support.
            //
            // Android must call SDL_InitSubSystem rather than SDL_Init: the launchers'
            // bytehook patches the SDL_InitSubSystem symbol, and only when that hook fires
            // do they System.loadLibrary("SDL3") + SDL.setupJNI() on the ART side, which is
            // what installs the Java glue SDL's Android joystick driver needs. Calling
            // SDL_Init would only reach the hook if the library happens to forward to
            // SDL_InitSubSystem through the PLT, which is not something to rely on.
            // The two are semantically identical in SDL3 (SDL_Init(flags) *is*
            // SDL_InitSubSystem(flags)).
            boolean result = AndroidPlatform.isAndroid()
                    ? GamepadJNI.SDL_InitSubSystem(SDL_INIT_GAMEPAD | SDL_INIT_JOYSTICK)
                    : GamepadJNI.SDL_Init(SDL_INIT_GAMEPAD | SDL_INIT_JOYSTICK);
            if (!result) {
                String error = GamepadJNI.SDL_GetError();
                GamepadLog.error("[gamepad-jni] SDL_Init failed: {}", error);
                return false;
            }

            initialized = true;
            GamepadLog.info("[gamepad-jni] SDL3 initialized successfully");

            if (AndroidPlatform.isAndroid()) {
                logAndroidSdlState();
            }

            // Detect currently connected gamepads
            refreshGamepads();

            if (AndroidPlatform.isAndroid() && getGamepadCount() == 0) {
                GamepadLog.warn("[gamepad-jni] {}", describeNoGamepadHint());
            }

            return true;
        } catch (UnsatisfiedLinkError e) {
            GamepadLog.error("[gamepad-jni] Failed to load native library: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            GamepadLog.error("[gamepad-jni] Initialization failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Android-only diagnostics: SDL runtime version, whether we are the first user of
     * the joystick subsystem, and whether the launcher glue is in place.
     *
     * <p>Every call is guarded because the desktop natives already committed to this
     * repository predate these three bindings; a missing symbol must only skip the
     * diagnostic, never affect functionality.</p>
     */
    private void logAndroidSdlState() {
        try {
            int v = GamepadJNI.SDL_GetVersion();
            int major = v / 1000000;
            int minor = v / 1000 % 1000;
            int patch = v % 1000;
            GamepadLog.info("[gamepad-jni] Android: SDL3 runtime version {}.{}.{}", major, minor, patch);
            if (major != 3 || minor < 2) {
                GamepadLog.warn("[gamepad-jni] Android: SDL3 {}.{}.{} is outside the verified range "
                        + "(>= 3.2.0); behaviour is best-effort", major, minor, patch);
            }
        } catch (UnsatisfiedLinkError e) {
            GamepadLog.warn("[gamepad-jni] Android: SDL_GetVersion unavailable in this build");
        }
        try {
            int joystick = GamepadJNI.SDL_WasInit(SDL_INIT_JOYSTICK);
            GamepadLog.info("[gamepad-jni] Android: joystick subsystem was {} before our init",
                    joystick != 0 ? "already initialized (we are a guest)" : "not initialized");
        } catch (UnsatisfiedLinkError e) {
            GamepadLog.warn("[gamepad-jni] Android: SDL_WasInit unavailable in this build");
        }
    }

    /**
     * What to tell the player when SDL is ready but no gamepad was found.
     *
     * <p>On Android this is usually not a fault: the launchers make "map the gamepad"
     * and "pass it through to SDL" mutually exclusive paths.</p>
     */
    private static String describeNoGamepadHint() {
        return "SDL3 is ready but no gamepad was found. On Android this usually means the launcher "
                + "is not routing gamepad input to SDL. Check: (1) the launcher's gamepad control "
                + "switch is enabled; (2) the gamepad input mode is SDL passthrough / SDL_DIRECT "
                + "rather than mapped / MAPPED (FCL: enable 'gamepad control' and pick SDL direct; "
                + "Amethyst: enable gamepadPassthruForced). In MAPPED mode the launcher consumes "
                + "gamepad input itself, so ControlFlex cannot see it - that is by design.";
    }

    /**
     * Shutdown SDL3 and release all resources.
     *
     * <p>Closes all opened gamepads and quits SDL3.</p>
     */
    public void shutdown() {
        if (!initialized) return;

        stopPollThread();

        // Close all gamepads
        for (Gamepad gamepad : gamepads.values()) {
            try {
                gamepad.close();
            } catch (Exception ignored) {
            }
        }
        gamepads.clear();
        gamepadOrder.clear();

        // Android: we are a guest in the launcher's SDL instance, and SDL_Quit() is a
        // process-wide teardown that would also destroy VIDEO/EVENTS the launcher (or,
        // from MC 26.3 on, the game itself) owns. Release only what we asked for.
        // Desktop keeps SDL_Quit() so its behaviour is unchanged.
        if (AndroidPlatform.isAndroid()) {
            try {
                GamepadJNI.SDL_QuitSubSystem(SDL_INIT_GAMEPAD | SDL_INIT_JOYSTICK);
            } catch (UnsatisfiedLinkError e) {
                GamepadLog.warn("[gamepad-jni] Android: SDL_QuitSubSystem unavailable, skipping");
            }
        } else {
            GamepadJNI.SDL_Quit();
        }
        initialized = false;
        GamepadLog.info("[gamepad-jni] SDL3 shutdown complete");
    }

    /** Check if SDL3 has been initialized. */
    public boolean isInitialized() {
        return initialized;
    }

    // ═══════════════════════════════════════════
    // Gamepad Discovery & Management
    // ═══════════════════════════════════════════

    /**
     * Refresh the list of connected gamepads from SDL3.
     *
     * <p>Closes stale gamepads and opens newly connected ones.</p>
     */
    public void refreshGamepads() {
        if (!initialized) return;

        int[] ids = GamepadJNI.SDL_GetGamepads();
        if (ids == null) return;

        // Determine which gamepads are new and which are stale
        List<Integer> currentIds = new ArrayList<>();
        for (int id : ids) {
            currentIds.add(id);
        }

        // Close gamepads that are no longer connected
        for (Integer instanceId : new ArrayList<>(gamepads.keySet())) {
            if (!currentIds.contains(instanceId)) {
                Gamepad gamepad = gamepads.remove(instanceId);
                gamepadOrder.remove(instanceId);
                if (gamepad != null) {
                    gamepad.close();
                    notifyGamepadRemoved(gamepad);
                }
            }
        }

        // Open newly connected gamepads
        for (Integer instanceId : currentIds) {
            if (!gamepads.containsKey(instanceId)) {
                Gamepad gamepad = openGamepadInternal(instanceId);
                if (gamepad != null) {
                    gamepads.put(instanceId, gamepad);
                    gamepadOrder.add(instanceId);
                    notifyGamepadAdded(gamepad);
                }
            }
        }
    }

    /**
     * Open a gamepad by instance ID.
     *
     * @param instanceId the joystick instance ID
     * @return Gamepad instance, or null on failure
     */
    public Gamepad openGamepad(int instanceId) {
        if (!initialized) return null;

        // Return existing gamepad if already opened
        Gamepad existing = gamepads.get(instanceId);
        if (existing != null && existing.isConnected()) {
            return existing;
        }

        Gamepad gamepad = openGamepadInternal(instanceId);
        if (gamepad != null) {
            gamepads.put(instanceId, gamepad);
            if (!gamepadOrder.contains(instanceId)) {
                gamepadOrder.add(instanceId);
            }
        }
        return gamepad;
    }

    private Gamepad openGamepadInternal(int instanceId) {
        long handle = GamepadJNI.SDL_OpenGamepad(instanceId);
        if (handle == 0) {
            return null;
        }
        return new Gamepad(handle, instanceId);
    }

    /**
     * Close a gamepad by instance ID.
     */
    public void closeGamepad(int instanceId) {
        Gamepad gamepad = gamepads.remove(instanceId);
        gamepadOrder.remove(Integer.valueOf(instanceId));
        if (gamepad != null) {
            gamepad.close();
            notifyGamepadRemoved(gamepad);
        }
    }

    /**
     * Get a gamepad by instance ID.
     *
     * @return Gamepad instance, or null if not opened
     */
    public Gamepad getGamepad(int instanceId) {
        return gamepads.get(instanceId);
    }

    /**
     * Get a gamepad by index (0-based, order of connection).
     *
     * @param index gamepad index
     * @return Gamepad instance, or null
     */
    public Gamepad getGamepadByIndex(int index) {
        if (index < 0 || index >= gamepadOrder.size()) return null;
        Integer instanceId = gamepadOrder.get(index);
        return instanceId != null ? gamepads.get(instanceId) : null;
    }

    /** Get all currently opened gamepads. */
    public List<Gamepad> getGamepads() {
        List<Gamepad> result = new ArrayList<>();
        for (Integer id : gamepadOrder) {
            Gamepad gamepad = gamepads.get(id);
            if (gamepad != null && gamepad.isConnected()) {
                result.add(gamepad);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Get the number of connected gamepads. */
    public int getGamepadCount() {
        return gamepads.size();
    }

    /**
     * Get the gamepad name by index.
     *
     * @param index gamepad index
     * @return name string
     */
    public String getGamepadName(int index) {
        Gamepad gamepad = getGamepadByIndex(index);
        return gamepad != null ? gamepad.getName() : "Unknown";
    }

    // ═══════════════════════════════════════════
    // Input Polling
    // ═══════════════════════════════════════════

    /**
     * Start a background polling thread that calls SDL_UpdateGamepads().
     *
     * <p>This is necessary if gamepad events are enabled (default).
     * The polling thread runs at ~60Hz and also performs periodic
     * hotplug detection.</p>
     */
    public void startPollThread() {
        if (pollThreadRunning) return;

        pollThreadRunning = true;
        pollThread = new Thread(() -> {
            int tickCounter = 0;
            while (pollThreadRunning && initialized) {
                try {
                    if (!IS_MAC) {
                        // Windows/Linux: safe to call from background thread
                        GamepadJNI.SDL_UpdateGamepads();

                        tickCounter++;
                        if (tickCounter >= 120) { // ~2 seconds at 60Hz
                            tickCounter = 0;
                            refreshGamepads();
                        }
                    }

                    // macOS: all SDL calls (SDL_UpdateGamepads + refreshGamepads)
                    // happen on the render thread via updateGamepads().
                    // Poll thread does nothing on macOS to avoid thread-safety issues
                    // with GCController/IOKit callbacks bound to CFRunLoop.

                    Thread.sleep(16); // ~60Hz
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {
                }
            }
        }, "gamepad-jni-poll");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    /** Stop the background polling thread. */
    public void stopPollThread() {
        pollThreadRunning = false;
        if (pollThread != null) {
            pollThread.interrupt();
            pollThread = null;
        }
    }

    /** Check if the poll thread is running. */
    public boolean isPollThreadRunning() {
        return pollThreadRunning;
    }

    /**
     * Update gamepad state from the render thread.
     *
     * <p>On macOS, this is the ONLY place that calls SDL APIs for polling,
     * ensuring thread-safety with GCController/IOKit callbacks
     * bound to the main thread's CFRunLoop.</p>
     *
     * <p>On Windows/Linux, SDL polling runs on the background poll thread;
     * this method does nothing there to avoid duplicate SDL calls.</p>
     */
    public void updateGamepads() {
        if (!initialized) return;

        if (!IS_MAC) {
            return;
        }

        GamepadJNI.SDL_UpdateGamepads();

        // Periodic device list refresh for hotplug detection
        updateTickCounter++;
        if (updateTickCounter >= 60) { // ~1 second at 60 FPS
            updateTickCounter = 0;
            refreshGamepads();

            checkMacDisconnect();
        }
    }

    /**
     * Check for macOS gamepad disconnects by querying GCController directly.
     *
     * <p>SDL's MFI driver relies on GCControllerDidDisconnectNotification,
     * but in Minecraft/LWJGL the notification never arrives because SDL_Init
     * runs on a non-main thread. We bypass this by directly querying
     * [GCController controllers].count via JNI.</p>
     */
    private void checkMacDisconnect() {
        int systemCount = GamepadJNI.getGCControllerCount();
        int sdlCount = gamepads.size();

        if (systemCount < 0) return; // Not supported on this macOS version

        if (systemCount < sdlCount) {
            GamepadLog.info("macOS disconnect detected: system={}, SDL={}",
                    systemCount, sdlCount);

            // Force-close all stale gamepads; refreshGamepads() will re-discover
            // any that are still connected.
            for (Integer instanceId : new ArrayList<>(gamepads.keySet())) {
                Gamepad gamepad = gamepads.remove(instanceId);
                gamepadOrder.remove(instanceId);
                if (gamepad != null) {
                    notifyGamepadRemoved(gamepad);
                    gamepad.close();
                }
            }
            refreshGamepads();
        }
    }

    // ═══════════════════════════════════════════
    // Gamepad Mappings
    // ═══════════════════════════════════════════

    /** Add a gamepad mapping string. */
    public int addGamepadMapping(String mapping) {
        return GamepadJNI.SDL_AddGamepadMapping(mapping);
    }

    /** Load gamepad mappings from a file. */
    public int addGamepadMappingsFromFile(String file) {
        return GamepadJNI.SDL_AddGamepadMappingsFromFile(file);
    }

    // ═══════════════════════════════════════════
    // Listeners
    // ═══════════════════════════════════════════

    /**
     * Listener for gamepad connection/disconnection events.
     */
    public interface GamepadListener {
        /** Called when a gamepad is connected. */
        void onGamepadAdded(Gamepad gamepad);

        /** Called when a gamepad is disconnected. */
        void onGamepadRemoved(Gamepad gamepad);
    }

    /** Add a gamepad listener. */
    public void addListener(GamepadListener listener) {
        listeners.add(listener);
    }

    /** Remove a gamepad listener. */
    public void removeListener(GamepadListener listener) {
        listeners.remove(listener);
    }

    private void notifyGamepadAdded(Gamepad gamepad) {
        for (GamepadListener listener : listeners) {
            try {
                listener.onGamepadAdded(gamepad);
            } catch (Exception ignored) {
            }
        }
    }

    private void notifyGamepadRemoved(Gamepad gamepad) {
        for (GamepadListener listener : listeners) {
            try {
                listener.onGamepadRemoved(gamepad);
            } catch (Exception ignored) {
            }
        }
    }
}
