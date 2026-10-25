/**
 * gamepad_jni.c - JNI wrapper for SDL3 Gamepad API
 *
 * This file implements the native methods declared in
 * com.ifels.gamepadjni.GamepadJNI.
 *
 * Build: see ../../CMakeLists.txt and build script.
 */

/* The system-cursor entry point at the bottom needs <windows.h>, and the lean /
 * no-minmax macros must be in place before ANY header pulls it in (SDL3 does on
 * Windows), so they are set ahead of every include. */
#ifdef _WIN32
#  define WIN32_LEAN_AND_MEAN
#  define NOMINMAX
#endif

#include <jni.h>
#include <SDL3/SDL.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#  include <windows.h>
#endif

/* ═══════════════════════════════════════════
 * JNI_OnLoad
 *
 * This must be defined, even though we register nothing: without it the JVM's
 * lookup of "JNI_OnLoad" on OUR library handle falls through to our dependencies
 * (POSIX dlsym searches the library and its dependency chain), and on Android
 * libSDL3.so exports one. SDL's Android JNI_OnLoad calls register_methods() for
 * org.libsdl.app.*, whose FindClass leaves a pending NoClassDefFoundError when
 * those classes live in the launcher's ART runtime rather than in the game JVM.
 * SDL never clears that exception, so it surfaces out of System.load() and, being
 * an Error rather than an UnsatisfiedLinkError, used to crash the game at
 * entrypoint init. Verified on device: dlsym(jniHandle, "JNI_OnLoad") returned
 * the exact same address as dlsym(sdlHandle, "JNI_OnLoad").
 *
 * Desktop SDL builds have no JNI_OnLoad (SDL_android.c is Android-only), which is
 * why this only ever broke on Android.
 * ═══════════════════════════════════════════ */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)vm;
    (void)reserved;
    /* 1_6, not 1_8: the NDK's jni.h does not define JNI_VERSION_1_8, and 1.6 is
     * what ART and every supported HotSpot accept. We register nothing here. */
    return JNI_VERSION_1_6;
}

/* ═══════════════════════════════════════════
 * Helper: throw a RuntimeException from JNI
 * ═══════════════════════════════════════════ */
static void throwRuntimeException(JNIEnv *env, const char *msg) {
    jclass cls = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (cls) {
        (*env)->ThrowNew(env, cls, msg);
    }
}

/* ═══════════════════════════════════════════
 * Helper: convert SDL_Gamepad* to jlong
 * ═══════════════════════════════════════════ */
static jlong ptr_to_jlong(void *ptr) {
    return (jlong)(intptr_t)ptr;
}

static void *jlong_to_ptr(jlong handle) {
    return (void *)(intptr_t)handle;
}

/* ═══════════════════════════════════════════
 * SDL log capture
 *
 * SDL's default filter is app=info, assert=warn, test=verbose, *=error. The
 * input category is therefore ERROR-only, which hides precisely the messages
 * that explain a silently degraded backend: "Using udev for HIDAPI joystick
 * device discovery", "Couldn't load libusb", "udev init failed, disabling udev
 * integration" are all DEBUG.
 *
 * This installs a public-API log output function (SDL_SetLogOutputFunction,
 * SDL 3.2+) so SDL itself is untouched, lowers only the input category to
 * DEBUG, and copies each line into a fixed ring buffer. The callback runs on
 * whichever thread logged, sometimes under SDL's internal log mutex, so it
 * never calls into the JVM -- Java drains the ring from a thread of its own
 * choosing.
 *
 * Declared in SdlLogJNI, NOT GamepadJNI: verifyNativeSymbols requires every
 * GamepadJNI entry point in EVERY packaged binary, and only the Linux natives
 * are rebuilt for 0.9.3.2. Callers catch UnsatisfiedLinkError on the platforms
 * still carrying older binaries.
 * ═══════════════════════════════════════════ */

#define SDL_LOG_CAPTURE_CAPACITY 64
#define SDL_LOG_CAPTURE_LINE     1024

typedef struct {
    int category;
    int priority;
    char message[SDL_LOG_CAPTURE_LINE];
} SdlLogCaptureEntry;

static SdlLogCaptureEntry sdl_log_entries[SDL_LOG_CAPTURE_CAPACITY];
static int sdl_log_head;                      /* next slot to write */
static int sdl_log_tail;                      /* next slot to read */
static unsigned int sdl_log_dropped;          /* lines lost to a full ring */
static SDL_Mutex *sdl_log_mutex;              /* guards head/tail/entries */
static SDL_LogOutputFunction sdl_log_previous;
static void *sdl_log_previous_userdata;
static SDL_LogPriority sdl_log_previous_input_priority = SDL_LOG_PRIORITY_INVALID;
static int sdl_log_capture_installed;

static const char *sdl_log_category_name(int category) {
    switch (category) {
        case SDL_LOG_CATEGORY_APPLICATION: return "app";
        case SDL_LOG_CATEGORY_ERROR:       return "error";
        case SDL_LOG_CATEGORY_ASSERT:      return "assert";
        case SDL_LOG_CATEGORY_SYSTEM:      return "system";
        case SDL_LOG_CATEGORY_AUDIO:       return "audio";
        case SDL_LOG_CATEGORY_VIDEO:       return "video";
        case SDL_LOG_CATEGORY_RENDER:      return "render";
        case SDL_LOG_CATEGORY_INPUT:       return "input";
        case SDL_LOG_CATEGORY_TEST:        return "test";
        case SDL_LOG_CATEGORY_GPU:         return "gpu";
        default:                           return NULL;
    }
}

static const char *sdl_log_priority_name(int priority) {
    switch (priority) {
        case SDL_LOG_PRIORITY_TRACE:    return "TRACE";
        case SDL_LOG_PRIORITY_VERBOSE:  return "VERBOSE";
        case SDL_LOG_PRIORITY_DEBUG:    return "DEBUG";
        case SDL_LOG_PRIORITY_INFO:     return "INFO";
        case SDL_LOG_PRIORITY_WARN:     return "WARN";
        case SDL_LOG_PRIORITY_ERROR:    return "ERROR";
        case SDL_LOG_PRIORITY_CRITICAL: return "CRITICAL";
        default:                        return "INVALID";
    }
}

static void SDLCALL gamepad_jni_log_capture(void *userdata, int category,
                                            SDL_LogPriority priority,
                                            const char *message) {
    int next;

    (void)userdata;
    if (!sdl_log_mutex || !message) {
        return;
    }

    SDL_LockMutex(sdl_log_mutex);
    next = (sdl_log_head + 1) % SDL_LOG_CAPTURE_CAPACITY;
    if (next == sdl_log_tail) {
        ++sdl_log_dropped;
    } else {
        SdlLogCaptureEntry *entry = &sdl_log_entries[sdl_log_head];
        entry->category = category;
        entry->priority = (int)priority;
        SDL_strlcpy(entry->message, message, sizeof(entry->message));
        sdl_log_head = next;
    }
    SDL_UnlockMutex(sdl_log_mutex);
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_SdlLogJNI_installLogCapture(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;

    if (sdl_log_capture_installed) {
        return JNI_TRUE;
    }
    if (!sdl_log_mutex) {
        sdl_log_mutex = SDL_CreateMutex();
        if (!sdl_log_mutex) {
            return JNI_FALSE;
        }
    }

    SDL_GetLogOutputFunction(&sdl_log_previous, &sdl_log_previous_userdata);
    sdl_log_previous_input_priority = SDL_GetLogPriority(SDL_LOG_CATEGORY_INPUT);
    SDL_SetLogPriority(SDL_LOG_CATEGORY_INPUT, SDL_LOG_PRIORITY_DEBUG);
    SDL_SetLogOutputFunction(gamepad_jni_log_capture, NULL);
    sdl_log_capture_installed = 1;
    return JNI_TRUE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_ifels_gamepadjni_SdlLogJNI_drainLogs(JNIEnv *env, jclass cls, jint maxLines) {
    int capacity = SDL_LOG_CAPTURE_CAPACITY;
    int available, count, dropped, total, i;
    SdlLogCaptureEntry *batch;
    jobjectArray result;
    jclass stringClass;

    (void)cls;

    if (!sdl_log_mutex || maxLines <= 0) {
        return NULL;
    }

    SDL_LockMutex(sdl_log_mutex);
    available = (sdl_log_head - sdl_log_tail + capacity) % capacity;
    if (available <= 0) {
        SDL_UnlockMutex(sdl_log_mutex);
        return NULL;
    }
    count = available < (int)maxLines ? available : (int)maxLines;
    batch = (SdlLogCaptureEntry *)malloc(sizeof(SdlLogCaptureEntry) * (size_t)count);
    if (!batch) {
        SDL_UnlockMutex(sdl_log_mutex);
        return NULL;
    }
    for (i = 0; i < count; ++i) {
        batch[i] = sdl_log_entries[sdl_log_tail];
        sdl_log_tail = (sdl_log_tail + 1) % capacity;
    }
    /* Only report drops once the ring is empty, so the count lines up with
     * what the reader actually lost. */
    dropped = 0;
    total = count;
    if (count == available && sdl_log_dropped > 0) {
        dropped = (int)sdl_log_dropped;
        sdl_log_dropped = 0;
        ++total;
    }
    SDL_UnlockMutex(sdl_log_mutex);

    stringClass = (*env)->FindClass(env, "java/lang/String");
    if (!stringClass) {
        free(batch);
        return NULL;
    }
    result = (*env)->NewObjectArray(env, (jsize)total, stringClass, NULL);
    if (!result) {
        free(batch);
        return NULL;
    }

    for (i = 0; i < count; ++i) {
        const char *category = sdl_log_category_name(batch[i].category);
        const char *priority = sdl_log_priority_name(batch[i].priority);
        char prefix[64];
        size_t length;
        char *line;
        jstring string;

        if (category) {
            SDL_snprintf(prefix, sizeof(prefix), "[SDL][%s][%s] ", category, priority);
        } else {
            SDL_snprintf(prefix, sizeof(prefix), "[SDL][cat%d][%s] ",
                         batch[i].category, priority);
        }
        length = SDL_strlen(prefix) + SDL_strlen(batch[i].message) + 1;
        line = (char *)malloc(length);
        if (!line) {
            continue;
        }
        SDL_snprintf(line, length, "%s%s", prefix, batch[i].message);
        string = (*env)->NewStringUTF(env, line);
        free(line);
        if (string) {
            (*env)->SetObjectArrayElement(env, result, (jsize)i, string);
            (*env)->DeleteLocalRef(env, string);
        }
    }
    if (dropped > 0) {
        char summary[96];
        jstring string;
        SDL_snprintf(summary, sizeof(summary),
                     "[SDL] %d message(s) dropped (capture buffer full)", dropped);
        string = (*env)->NewStringUTF(env, summary);
        if (string) {
            (*env)->SetObjectArrayElement(env, result, (jsize)count, string);
            (*env)->DeleteLocalRef(env, string);
        }
    }

    free(batch);
    return result;
}

JNIEXPORT void JNICALL
Java_com_ifels_gamepadjni_SdlLogJNI_uninstallLogCapture(JNIEnv *env, jclass cls) {
    SDL_LogOutputFunction current = NULL;
    void *current_userdata = NULL;

    (void)env; (void)cls;

    if (!sdl_log_capture_installed) {
        return;
    }

    /* Leave someone else's callback alone if the host replaced ours. */
    SDL_GetLogOutputFunction(&current, &current_userdata);
    if (current == gamepad_jni_log_capture) {
        if (sdl_log_previous) {
            SDL_SetLogOutputFunction(sdl_log_previous, sdl_log_previous_userdata);
        } else {
            SDL_SetLogOutputFunction(SDL_GetDefaultLogOutputFunction(), NULL);
        }
    }
    if (sdl_log_previous_input_priority != SDL_LOG_PRIORITY_INVALID) {
        SDL_SetLogPriority(SDL_LOG_CATEGORY_INPUT, sdl_log_previous_input_priority);
    }
    sdl_log_capture_installed = 0;

    /* The mutex stays allocated: a callback already in flight on another
     * thread may still be about to lock it, and one SDL_Mutex is cheaper than
     * the lifetime dance needed to free it safely. */
}

/* ═══════════════════════════════════════════
 * SDL Init / Quit
 * ═══════════════════════════════════════════ */

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1Init(JNIEnv *env, jclass cls, jint flags) {
    (void)env; (void)cls;
    return SDL_Init((SDL_InitFlags)flags) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1InitSubSystem(JNIEnv *env, jclass cls, jint flags) {
    (void)env; (void)cls;
    return SDL_InitSubSystem((SDL_InitFlags)flags) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1Quit(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    SDL_Quit();
}

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetVersion(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    return (jint)SDL_GetVersion();
}

JNIEXPORT void JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1QuitSubSystem(JNIEnv *env, jclass cls, jint flags) {
    (void)env; (void)cls;
    SDL_QuitSubSystem((SDL_InitFlags)flags);
}

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1WasInit(JNIEnv *env, jclass cls, jint flags) {
    (void)env; (void)cls;
    return (jint)SDL_WasInit((SDL_InitFlags)flags);
}

JNIEXPORT jstring JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetError(JNIEnv *env, jclass cls) {
    (void)cls;
    const char *err = SDL_GetError();
    if (!err) return NULL;
    return (*env)->NewStringUTF(env, err);
}

JNIEXPORT void JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1ClearError(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    SDL_ClearError();
}

/* ═══════════════════════════════════════════
 * SDL Hints
 * ═══════════════════════════════════════════ */

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1SetHintWithPriority(JNIEnv *env, jclass cls,
        jstring name, jstring value, jint priority) {
    (void)cls;
    const char *cname = (*env)->GetStringUTFChars(env, name, NULL);
    const char *cvalue = (*env)->GetStringUTFChars(env, value, NULL);
    jboolean result = SDL_SetHintWithPriority(cname, cvalue, (SDL_HintPriority)priority) ? JNI_TRUE : JNI_FALSE;
    (*env)->ReleaseStringUTFChars(env, name, cname);
    (*env)->ReleaseStringUTFChars(env, value, cvalue);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1SetHint(JNIEnv *env, jclass cls,
        jstring name, jstring value) {
    (void)cls;
    const char *cname = (*env)->GetStringUTFChars(env, name, NULL);
    const char *cvalue = (*env)->GetStringUTFChars(env, value, NULL);
    jboolean result = SDL_SetHint(cname, cvalue) ? JNI_TRUE : JNI_FALSE;
    (*env)->ReleaseStringUTFChars(env, name, cname);
    (*env)->ReleaseStringUTFChars(env, value, cvalue);
    return result;
}

/* ═══════════════════════════════════════════
 * Gamepad Mapping
 * ═══════════════════════════════════════════ */

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1AddGamepadMapping(JNIEnv *env, jclass cls, jstring mapping) {
    (void)cls;
    const char *cmapping = (*env)->GetStringUTFChars(env, mapping, NULL);
    jint result = (jint)SDL_AddGamepadMapping(cmapping);
    (*env)->ReleaseStringUTFChars(env, mapping, cmapping);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1AddGamepadMappingsFromFile(JNIEnv *env, jclass cls, jstring file) {
    (void)cls;
    const char *cfile = (*env)->GetStringUTFChars(env, file, NULL);
    jint result = (jint)SDL_AddGamepadMappingsFromFile(cfile);
    (*env)->ReleaseStringUTFChars(env, file, cfile);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1ReloadGamepadMappings(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    return SDL_ReloadGamepadMappings() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadMappingForID(JNIEnv *env, jclass cls, jint instanceId) {
    (void)cls;
    char *mapping = SDL_GetGamepadMappingForID((SDL_JoystickID)instanceId);
    if (!mapping) return NULL;
    jstring result = (*env)->NewStringUTF(env, mapping);
    SDL_free(mapping);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadMapping(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return NULL;
    char *mapping = SDL_GetGamepadMapping(gp);
    if (!mapping) return NULL;
    jstring result = (*env)->NewStringUTF(env, mapping);
    SDL_free(mapping);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1SetGamepadMapping(JNIEnv *env, jclass cls,
        jint instanceId, jstring mapping) {
    (void)cls;
    const char *cmapping = mapping ? (*env)->GetStringUTFChars(env, mapping, NULL) : NULL;
    jboolean result = SDL_SetGamepadMapping((SDL_JoystickID)instanceId, cmapping) ? JNI_TRUE : JNI_FALSE;
    if (cmapping) (*env)->ReleaseStringUTFChars(env, mapping, cmapping);
    return result;
}

/* ═══════════════════════════════════════════
 * Gamepad Discovery
 * ═══════════════════════════════════════════ */

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1HasGamepad(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    return SDL_HasGamepad() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jintArray JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepads(JNIEnv *env, jclass cls) {
    (void)cls;
    int count = 0;
    SDL_JoystickID *ids = SDL_GetGamepads(&count);
    if (!ids || count <= 0) {
        if (ids) SDL_free(ids);
        return NULL;
    }

    jintArray result = (*env)->NewIntArray(env, count);
    if (!result) {
        SDL_free(ids);
        return NULL;
    }

    /* SDL_JoystickID is Uint32, which fits in jint */
    jint *buf = (*env)->GetIntArrayElements(env, result, NULL);
    for (int i = 0; i < count; i++) {
        buf[i] = (jint)ids[i];
    }
    (*env)->ReleaseIntArrayElements(env, result, buf, 0);
    SDL_free(ids);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1IsGamepad(JNIEnv *env, jclass cls, jint instanceId) {
    (void)env; (void)cls;
    return SDL_IsGamepad((SDL_JoystickID)instanceId) ? JNI_TRUE : JNI_FALSE;
}

/* ═══════════════════════════════════════════
 * Gamepad Info by Instance ID
 * ═══════════════════════════════════════════ */

JNIEXPORT jstring JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadNameForID(JNIEnv *env, jclass cls, jint instanceId) {
    (void)cls;
    const char *name = SDL_GetGamepadNameForID((SDL_JoystickID)instanceId);
    return name ? (*env)->NewStringUTF(env, name) : NULL;
}

JNIEXPORT jstring JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadPathForID(JNIEnv *env, jclass cls, jint instanceId) {
    (void)cls;
    const char *path = SDL_GetGamepadPathForID((SDL_JoystickID)instanceId);
    return path ? (*env)->NewStringUTF(env, path) : NULL;
}

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadPlayerIndexForID(JNIEnv *env, jclass cls, jint instanceId) {
    (void)env; (void)cls;
    return (jint)SDL_GetGamepadPlayerIndexForID((SDL_JoystickID)instanceId);
}

JNIEXPORT jstring JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadGUIDForID(JNIEnv *env, jclass cls, jint instanceId) {
    (void)cls;
    SDL_GUID guid = SDL_GetGamepadGUIDForID((SDL_JoystickID)instanceId);
    char guidStr[33];
    SDL_GUIDToString(guid, guidStr, sizeof(guidStr));
    return (*env)->NewStringUTF(env, guidStr);
}

JNIEXPORT jshort JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadVendorForID(JNIEnv *env, jclass cls, jint instanceId) {
    (void)env; (void)cls;
    return (jshort)SDL_GetGamepadVendorForID((SDL_JoystickID)instanceId);
}

JNIEXPORT jshort JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadProductForID(JNIEnv *env, jclass cls, jint instanceId) {
    (void)env; (void)cls;
    return (jshort)SDL_GetGamepadProductForID((SDL_JoystickID)instanceId);
}

JNIEXPORT jshort JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadProductVersionForID(JNIEnv *env, jclass cls, jint instanceId) {
    (void)env; (void)cls;
    return (jshort)SDL_GetGamepadProductVersionForID((SDL_JoystickID)instanceId);
}

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadTypeForID(JNIEnv *env, jclass cls, jint instanceId) {
    (void)env; (void)cls;
    return (jint)SDL_GetGamepadTypeForID((SDL_JoystickID)instanceId);
}

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetRealGamepadTypeForID(JNIEnv *env, jclass cls, jint instanceId) {
    (void)env; (void)cls;
    return (jint)SDL_GetRealGamepadTypeForID((SDL_JoystickID)instanceId);
}

/* ═══════════════════════════════════════════
 * Gamepad Open / Close
 * ═══════════════════════════════════════════ */

JNIEXPORT jlong JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1OpenGamepad(JNIEnv *env, jclass cls, jint instanceId) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = SDL_OpenGamepad((SDL_JoystickID)instanceId);
    return ptr_to_jlong(gp);
}

JNIEXPORT jlong JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadFromID(JNIEnv *env, jclass cls, jint instanceId) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = SDL_GetGamepadFromID((SDL_JoystickID)instanceId);
    return ptr_to_jlong(gp);
}

JNIEXPORT jlong JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadFromPlayerIndex(JNIEnv *env, jclass cls, jint playerIndex) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = SDL_GetGamepadFromPlayerIndex((int)playerIndex);
    return ptr_to_jlong(gp);
}

JNIEXPORT void JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1CloseGamepad(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (gp) SDL_CloseGamepad(gp);
}

/* ═══════════════════════════════════════════
 * Gamepad Properties (opened gamepad)
 * ═══════════════════════════════════════════ */

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadID(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return 0;
    return (jint)SDL_GetGamepadID(gp);
}

JNIEXPORT jstring JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadName(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return NULL;
    const char *name = SDL_GetGamepadName(gp);
    return name ? (*env)->NewStringUTF(env, name) : NULL;
}

JNIEXPORT jstring JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadPath(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return NULL;
    const char *path = SDL_GetGamepadPath(gp);
    return path ? (*env)->NewStringUTF(env, path) : NULL;
}

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadType(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return SDL_GAMEPAD_TYPE_UNKNOWN;
    return (jint)SDL_GetGamepadType(gp);
}

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetRealGamepadType(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return SDL_GAMEPAD_TYPE_UNKNOWN;
    return (jint)SDL_GetRealGamepadType(gp);
}

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadPlayerIndex(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return -1;
    return (jint)SDL_GetGamepadPlayerIndex(gp);
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1SetGamepadPlayerIndex(JNIEnv *env, jclass cls,
        jlong gamepad, jint playerIndex) {
    (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return JNI_FALSE;
    return SDL_SetGamepadPlayerIndex(gp, (int)playerIndex) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jshort JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadVendor(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return 0;
    return (jshort)SDL_GetGamepadVendor(gp);
}

JNIEXPORT jshort JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadProduct(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return 0;
    return (jshort)SDL_GetGamepadProduct(gp);
}

JNIEXPORT jshort JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadProductVersion(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return 0;
    return (jshort)SDL_GetGamepadProductVersion(gp);
}

JNIEXPORT jshort JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadFirmwareVersion(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return 0;
    return (jshort)SDL_GetGamepadFirmwareVersion(gp);
}

JNIEXPORT jstring JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadSerial(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return NULL;
    const char *serial = SDL_GetGamepadSerial(gp);
    return serial ? (*env)->NewStringUTF(env, serial) : NULL;
}

JNIEXPORT jlong JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadSteamHandle(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return 0;
    return (jlong)SDL_GetGamepadSteamHandle(gp);
}

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadConnectionState(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return SDL_JOYSTICK_CONNECTION_INVALID;
    return (jint)SDL_GetGamepadConnectionState(gp);
}

JNIEXPORT jintArray JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadPowerInfo(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return NULL;

    int percent = -1;
    SDL_PowerState state = SDL_GetGamepadPowerInfo(gp, &percent);

    jintArray result = (*env)->NewIntArray(env, 2);
    if (!result) return NULL;
    jint buf[2] = { (jint)state, (jint)percent };
    (*env)->SetIntArrayRegion(env, result, 0, 2, buf);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GamepadConnected(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return JNI_FALSE;
    return SDL_GamepadConnected(gp) ? JNI_TRUE : JNI_FALSE;
}

/* ═══════════════════════════════════════════
 * Gamepad Input State
 * ═══════════════════════════════════════════ */

JNIEXPORT void JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1UpdateGamepads(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    SDL_UpdateGamepads();
}

JNIEXPORT void JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1SetGamepadEventsEnabled(JNIEnv *env, jclass cls, jboolean enabled) {
    (void)env; (void)cls;
    SDL_SetGamepadEventsEnabled(enabled == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GamepadEventsEnabled(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    return SDL_GamepadEventsEnabled() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GamepadHasAxis(JNIEnv *env, jclass cls, jlong gamepad, jint axis) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return JNI_FALSE;
    return SDL_GamepadHasAxis(gp, (SDL_GamepadAxis)axis) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jshort JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadAxis(JNIEnv *env, jclass cls, jlong gamepad, jint axis) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return 0;
    return (jshort)SDL_GetGamepadAxis(gp, (SDL_GamepadAxis)axis);
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GamepadHasButton(JNIEnv *env, jclass cls, jlong gamepad, jint button) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return JNI_FALSE;
    return SDL_GamepadHasButton(gp, (SDL_GamepadButton)button) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadButton(JNIEnv *env, jclass cls, jlong gamepad, jint button) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return JNI_FALSE;
    return SDL_GetGamepadButton(gp, (SDL_GamepadButton)button) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadButtonLabelForType(JNIEnv *env, jclass cls,
        jint type, jint button) {
    (void)env; (void)cls;
    return (jint)SDL_GetGamepadButtonLabelForType((SDL_GamepadType)type, (SDL_GamepadButton)button);
}

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadButtonLabel(JNIEnv *env, jclass cls,
        jlong gamepad, jint button) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return SDL_GAMEPAD_BUTTON_LABEL_UNKNOWN;
    return (jint)SDL_GetGamepadButtonLabel(gp, (SDL_GamepadButton)button);
}

/* ═══════════════════════════════════════════
 * String <-> Enum Conversion
 * ═══════════════════════════════════════════ */

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadTypeFromString(JNIEnv *env, jclass cls, jstring str) {
    (void)cls;
    const char *cstr = (*env)->GetStringUTFChars(env, str, NULL);
    jint result = (jint)SDL_GetGamepadTypeFromString(cstr);
    (*env)->ReleaseStringUTFChars(env, str, cstr);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadStringForType(JNIEnv *env, jclass cls, jint type) {
    (void)env; (void)cls;
    const char *str = SDL_GetGamepadStringForType((SDL_GamepadType)type);
    return str ? (*env)->NewStringUTF(env, str) : NULL;
}

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadAxisFromString(JNIEnv *env, jclass cls, jstring str) {
    (void)cls;
    const char *cstr = (*env)->GetStringUTFChars(env, str, NULL);
    jint result = (jint)SDL_GetGamepadAxisFromString(cstr);
    (*env)->ReleaseStringUTFChars(env, str, cstr);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadStringForAxis(JNIEnv *env, jclass cls, jint axis) {
    (void)env; (void)cls;
    const char *str = SDL_GetGamepadStringForAxis((SDL_GamepadAxis)axis);
    return str ? (*env)->NewStringUTF(env, str) : NULL;
}

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadButtonFromString(JNIEnv *env, jclass cls, jstring str) {
    (void)cls;
    const char *cstr = (*env)->GetStringUTFChars(env, str, NULL);
    jint result = (jint)SDL_GetGamepadButtonFromString(cstr);
    (*env)->ReleaseStringUTFChars(env, str, cstr);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadStringForButton(JNIEnv *env, jclass cls, jint button) {
    (void)env; (void)cls;
    const char *str = SDL_GetGamepadStringForButton((SDL_GamepadButton)button);
    return str ? (*env)->NewStringUTF(env, str) : NULL;
}

/* ═══════════════════════════════════════════
 * Rumble & LED
 * ═══════════════════════════════════════════ */

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1RumbleGamepad(JNIEnv *env, jclass cls,
        jlong gamepad, jint lowFrequencyRumble, jint highFrequencyRumble, jint durationMs) {
    (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return JNI_FALSE;
    return SDL_RumbleGamepad(gp, (Uint16)lowFrequencyRumble,
                             (Uint16)highFrequencyRumble, (Uint32)durationMs) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1RumbleGamepadTriggers(JNIEnv *env, jclass cls,
        jlong gamepad, jint leftRumble, jint rightRumble, jint durationMs) {
    (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return JNI_FALSE;
    return SDL_RumbleGamepadTriggers(gp, (Uint16)leftRumble,
                                     (Uint16)rightRumble, (Uint32)durationMs) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1SetGamepadLED(JNIEnv *env, jclass cls,
        jlong gamepad, jbyte red, jbyte green, jbyte blue) {
    (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return JNI_FALSE;
    return SDL_SetGamepadLED(gp, (Uint8)red, (Uint8)green, (Uint8)blue) ? JNI_TRUE : JNI_FALSE;
}

/* ═══════════════════════════════════════════
 * Touchpad
 * ═══════════════════════════════════════════ */

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetNumGamepadTouchpads(JNIEnv *env, jclass cls, jlong gamepad) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return 0;
    return (jint)SDL_GetNumGamepadTouchpads(gp);
}

JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetNumGamepadTouchpadFingers(JNIEnv *env, jclass cls,
        jlong gamepad, jint touchpad) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return 0;
    return (jint)SDL_GetNumGamepadTouchpadFingers(gp, (int)touchpad);
}

JNIEXPORT jfloatArray JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadTouchpadFinger(JNIEnv *env, jclass cls,
        jlong gamepad, jint touchpad, jint finger) {
    (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return NULL;

    bool down = false;
    float x = 0.0f, y = 0.0f, pressure = 0.0f;

    if (!SDL_GetGamepadTouchpadFinger(gp, (int)touchpad, (int)finger, &down, &x, &y, &pressure)) {
        return NULL;
    }

    jfloatArray result = (*env)->NewFloatArray(env, 4);
    if (!result) return NULL;
    jfloat buf[4] = { down ? 1.0f : 0.0f, x, y, pressure };
    (*env)->SetFloatArrayRegion(env, result, 0, 4, buf);
    return result;
}

/* ═══════════════════════════════════════════
 * Sensor
 * ═══════════════════════════════════════════ */

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GamepadHasSensor(JNIEnv *env, jclass cls,
        jlong gamepad, jint type) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return JNI_FALSE;
    return SDL_GamepadHasSensor(gp, (SDL_SensorType)type) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1SetGamepadSensorEnabled(JNIEnv *env, jclass cls,
        jlong gamepad, jint type, jboolean enabled) {
    (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return JNI_FALSE;
    return SDL_SetGamepadSensorEnabled(gp, (SDL_SensorType)type, enabled == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GamepadSensorEnabled(JNIEnv *env, jclass cls,
        jlong gamepad, jint type) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return JNI_FALSE;
    return SDL_GamepadSensorEnabled(gp, (SDL_SensorType)type) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloat JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadSensorDataRate(JNIEnv *env, jclass cls,
        jlong gamepad, jint type) {
    (void)env; (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return 0.0f;
    return (jfloat)SDL_GetGamepadSensorDataRate(gp, (SDL_SensorType)type);
}

JNIEXPORT jfloatArray JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_SDL_1GetGamepadSensorData(JNIEnv *env, jclass cls,
        jlong gamepad, jint type, jint numValues) {
    (void)cls;
    SDL_Gamepad *gp = (SDL_Gamepad *)jlong_to_ptr(gamepad);
    if (!gp) return NULL;

    jfloatArray result = (*env)->NewFloatArray(env, numValues);
    if (!result) return NULL;

    jfloat *buf = (*env)->GetFloatArrayElements(env, result, NULL);
    if (!buf) return NULL;

    if (!SDL_GetGamepadSensorData(gp, (SDL_SensorType)type, (float *)buf, (int)numValues)) {
        (*env)->ReleaseFloatArrayElements(env, result, buf, 0);
        return NULL;
    }

    (*env)->ReleaseFloatArrayElements(env, result, buf, 0);
    return result;
}

/* ═══════════════════════════════════════════
 * System cursor visibility (Windows only)
 *
 * Why this exists: the 1.7.10 client ships NO JNA on its launcher classpath
 * (1.8.9 has jna 3.4.0 + platform 3.4.0, 1.12.2 has 4.4.0, 1.7.10 has none --
 * measured from both version jsons), so the mod's GLFW_CURSOR_HIDDEN stand-in
 * used to degrade to "the system cursor stays visible next to the drawn one".
 * Carrying a JNA copy just for ShowCursor costs ~1.8 MB in every 1.7.10 build;
 * this library is bundled by the mod anyway, so one entry point here replaces
 * it at zero dependency cost.
 *
 * ShowCursor moves a display COUNTER. The caller is responsible for transition
 * discipline -- compat/CursorVisibility calls this only when the desired state
 * actually changes, because hideSystemCursor runs every frame while the pointer
 * settles. This wrapper is deliberately dumb: forward the flag, return the new
 * counter so the caller can log it.
 *
 * Declared in SystemCursorJNI, NOT GamepadJNI: verifyNativeSymbols requires
 * every GamepadJNI method to be exported by EVERY packaged binary, and this one
 * only exists on Windows.
 * ═══════════════════════════════════════════ */
JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_SystemCursorJNI_showCursor(JNIEnv *env, jclass cls, jboolean hide) {
    (void)env; (void)cls;
#ifdef _WIN32
    return (jint)ShowCursor(hide ? FALSE : TRUE);
#else
    /* No implementation on macOS/Linux: the port has no cursor API there and the
     * mod never asks for it (SystemCursor.isSupported() is Windows-only). Keeping
     * the symbol defined on every platform means a future rebuild of any target
     * still resolves it instead of throwing UnsatisfiedLinkError if something
     * does call. */
    return 0;
#endif
}
