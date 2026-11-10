package com.ifels.gamepadjni;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JnaBindingShapeTest {

    @Test
    void jnaBindingImplementsEverySlot() {
        long count = Arrays.stream(JnaBinding.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .count();
        assertEquals(76, count, "JnaBinding must implement 75 SDL methods + showCursor");
    }

    @Test
    void everyBooleanReturningNativeIsByte() {
        // JNA maps Java boolean to a 4-byte int; SDL's bool is 1 byte. Reading four
        // bytes where the callee wrote one yields garbage in the upper bits.
        for (Method m : JnaGamepad.class.getDeclaredMethods()) {
            if (!Modifier.isNative(m.getModifiers())) {
                continue;
            }
            assertNotEquals(boolean.class, m.getReturnType(),
                    m.getName() + " must return byte, not boolean");
        }
    }

    @Test
    void noStringTypesInTheNativeSurface() {
        for (Method m : JnaGamepad.class.getDeclaredMethods()) {
            if (!Modifier.isNative(m.getModifiers())) {
                continue;
            }
            assertNotEquals(String.class, m.getReturnType(), m.getName());
            for (Class<?> p : m.getParameterTypes()) {
                assertNotEquals(String.class, p, m.getName());
            }
        }
    }

    @Test
    void sdlHandleCarriesEitherFileOrName() {
        SdlHandle byName = SdlHandle.ofName("SDL3");
        assertNull(byName.file);
        assertEquals("SDL3", byName.name);
        assertEquals("SDL3", byName.target());

        SdlHandle byFile = SdlHandle.ofFile(null, Paths.get("C:/x/SDL3.dll"));
        assertEquals("C:/x/SDL3.dll", byFile.file.toString());
        assertNull(byFile.name);
        assertTrue(byFile.dir == null);
    }
}
