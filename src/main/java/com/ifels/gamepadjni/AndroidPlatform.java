package com.ifels.gamepadjni;

import java.io.File;

/**
 * Android 平台判定与打包契约映射。
 *
 * <p>Android 版 Minecraft Java 启动器（Amethyst / Zalith Launcher 2 /
 * FoldCraftLauncher / PojavLauncher）会把游戏跑在同一个进程里的第二个 JVM 中，
 * 并强制设置 {@code os.name=Linux}，因此不能靠 {@code os.name} 判平台。</p>
 *
 * <p>本类刻意不引用日志，也不做任何 IO 之外的副作用，以便单元测试直接用显式入参
 * 驱动 {@link #detect}。</p>
 */
public final class AndroidPlatform {

    /** Android 上 SDL3 由启动器 APK 提供，位于其 native library 目录。 */
    public static final String SDL3_LIBRARY_NAME = "libSDL3.so";

    private static final boolean ANDROID = detect(
            System.getProperty("os.name", ""),
            System.getProperty("os.version", ""),
            System.getProperty("java.vm.name", ""),
            System.getenv("POJAV_NATIVEDIR"),
            new File("/system/build.prop").exists());

    private AndroidPlatform() {
    }

    /** 当前进程是否运行在 Android 启动器提供的游戏 JVM 中。 */
    public static boolean isAndroid() {
        return ANDROID;
    }

    /**
     * 多信号冗余判定。任一条命中即认为是 Android。
     *
     * @param osName                 os.name
     * @param osVersion              os.version；启动器会设为 {@code Android-<release>}
     * @param vmName                 java.vm.name；ART 上是 dalvik
     * @param pojavNativeDir         POJAV_NATIVEDIR 环境变量
     * @param systemBuildPropExists  /system/build.prop 是否存在
     */
    static boolean detect(String osName, String osVersion, String vmName,
                          String pojavNativeDir, boolean systemBuildPropExists) {
        // ① 主判据：Pojav 家族启动器的标记（Controlify / Legacy4J 同款）
        if (pojavNativeDir != null && !pojavNativeDir.isEmpty()) {
            return true;
        }
        // ② 启动器设置的 -Dos.version=Android-<release>
        if (osVersion != null && osVersion.startsWith("Android")) {
            return true;
        }
        // ③ 与启动器无关的 OS 级信号，兜住 FCL 这类非 Pojav 分支启动器
        if (osName != null && osName.toLowerCase().contains("linux")
                && systemBuildPropExists) {
            return true;
        }
        // ④ 最后兜底
        return vmName != null && "dalvik".equalsIgnoreCase(vmName);
    }

    /**
     * jar 内的平台目录名，与 {@code build.gradle} 的打包白名单是一份契约。
     *
     * @return 形如 {@code "android-arm64-v8a"}；不支持的 ABI 返回 {@code null}
     */
    public static String nativePlatformDir() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "android-arm64-v8a";
        }
        if (arch.equals("arm") || arch.equals("armv7l") || arch.equals("armv8l")) {
            return "android-armeabi-v7a";
        }
        if (arch.equals("x86_64") || arch.equals("amd64")) {
            return "android-x86_64";
        }
        return null;
    }

    /**
     * 启动器自带的 SDL3 绝对路径。
     *
     * @return 形如 {@code "<POJAV_NATIVEDIR>/libSDL3.so"}；无法构造时返回 {@code null}
     */
    public static String launcherSdlPath(String pojavNativeDir) {
        if (pojavNativeDir == null || pojavNativeDir.isEmpty()) {
            return null;
        }
        return pojavNativeDir + "/" + SDL3_LIBRARY_NAME;
    }
}
