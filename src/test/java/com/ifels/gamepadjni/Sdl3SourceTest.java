package com.ifels.gamepadjni;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Desktop callers pick HOST vs BUNDLED; Android always uses the launcher copy.
 */
class Sdl3SourceTest {

    @Test
    void androidAlwaysUsesLauncherEvenIfHostRequested() {
        assertEquals(Sdl3Source.LoadKind.LAUNCHER,
                Sdl3Source.resolveLoadKind(true, Sdl3Source.HOST));
    }

    @Test
    void androidAlwaysUsesLauncherEvenIfBundledRequested() {
        assertEquals(Sdl3Source.LoadKind.LAUNCHER,
                Sdl3Source.resolveLoadKind(true, Sdl3Source.BUNDLED));
    }

    @Test
    void desktopHostIsExclusive() {
        assertEquals(Sdl3Source.LoadKind.HOST,
                Sdl3Source.resolveLoadKind(false, Sdl3Source.HOST));
    }

    @Test
    void desktopBundledDoesNotAdoptHost() {
        assertEquals(Sdl3Source.LoadKind.BUNDLED,
                Sdl3Source.resolveLoadKind(false, Sdl3Source.BUNDLED));
    }

    @Test
    void nullSourceIsRejected() {
        assertThrows(NullPointerException.class,
                () -> Sdl3Source.resolveLoadKind(false, null));
    }
}
