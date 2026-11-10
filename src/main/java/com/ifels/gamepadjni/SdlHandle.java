package com.ifels.gamepadjni;

import java.nio.file.Path;

/**
 * How SDL3 was loaded, in the form the JNA backend needs.
 *
 * <p>The JNI backend binds by module name and needs nothing here, but JNA has to
 * be told where to look: an absolute path when we extracted or adopted a file,
 * or a bare library name when the platform resolved it through
 * {@code System.loadLibrary}.</p>
 *
 * <p>{@link #dir} is the temp directory the platform natives were extracted to,
 * when there is one. It is a hint for {@link NativeLibraryLoader} so the bundled
 * case does not extract {@code gamepadjni.dll} a second time.</p>
 */
final class SdlHandle {

    /** Extraction directory, or {@code null} when nothing was extracted. */
    final Path dir;

    /** Absolute path of the SDL3 library, or {@code null} when only a name is known. */
    final Path file;

    /** Library name for {@code NativeLibrary.getInstance}, or {@code null} when a path is known. */
    final String name;

    private SdlHandle(Path dir, Path file, String name) {
        this.dir = dir;
        this.file = file;
        this.name = name;
    }

    static SdlHandle ofFile(Path dir, Path file) {
        return new SdlHandle(dir, file, null);
    }

    static SdlHandle ofName(String name) {
        return new SdlHandle(null, null, name);
    }

    /** The argument to hand to {@code NativeLibrary.getInstance}. */
    String target() {
        return file != null ? file.toString() : name;
    }

    @Override
    public String toString() {
        return file != null ? file.toString() : String.valueOf(name);
    }
}
