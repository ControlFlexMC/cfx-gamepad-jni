/**
 * gamepad_jni.c - JNI wrapper for SDL3 Gamepad API
 *
 * This file implements the native methods declared in
 * com.ifels.gamepadjni.GamepadJNI.
 *
 * Build: see ../../CMakeLists.txt and build script.
 */

#include <jni.h>
#include <SDL3/SDL.h>
#include <stdlib.h>
#include <string.h>

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
