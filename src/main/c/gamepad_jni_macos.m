/**
 * gamepad_jni_macos.m - macOS-specific JNI helpers (Objective-C)
 *
 * Provides direct GCController queries to work around the fact that
 * GCControllerDidDisconnectNotification is never delivered when SDL
 * is initialized on a non-main thread (e.g. modloading-worker) inside
 * a host application like Minecraft/LWJGL that owns the main RunLoop.
 */

#ifdef __APPLE__

#import <Foundation/Foundation.h>
#import <GameController/GameController.h>
#import <jni.h>

/**
 * Return the number of GCControllers currently known to the system.
 * This is a 'direct query' - it does NOT depend on NSNotification delivery.
 */
JNIEXPORT jint JNICALL
Java_com_ifels_gamepadjni_GamepadJNI_getGCControllerCount(JNIEnv *env, jclass cls) {
    (void)env;
    (void)cls;
    @autoreleasepool {
        if (@available(macOS 11.0, *)) {
            return (jint)[[GCController controllers] count];
        }
        return -1; /* Not supported on older macOS */
    }
}

#endif /* __APPLE__ */
