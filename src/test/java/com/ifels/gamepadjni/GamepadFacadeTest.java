package com.ifels.gamepadjni;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GamepadFacadeTest {

    private static Map<String, String> signatureOf(Class<?> c) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Method m : c.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic()) {
                continue;
            }
            map.put(m.getName(), Arrays.toString(m.getParameterTypes())
                    + "->" + m.getReturnType().getName());
        }
        return map;
    }

    @Test
    void facadeMirrorsTheBindingInterface() {
        Map<String, String> api = signatureOf(GamepadBinding.class);
        Map<String, String> facade = signatureOf(GamepadJNI.class);
        assertEquals(76, api.size(), "GamepadBinding must declare 75 SDL methods + showCursor");
        assertEquals(75, facade.size(), "GamepadJNI must keep its 75 public methods");
        assertTrue(api.keySet().containsAll(facade.keySet()), "facade has methods the interface lacks");
        for (Map.Entry<String, String> e : facade.entrySet()) {
            assertEquals(e.getValue(), api.get(e.getKey()), "signature drift on " + e.getKey());
        }
    }

    @Test
    void nativeDeclarationsMovedToJniGamepad() {
        long natives = Arrays.stream(JniGamepad.class.getDeclaredMethods())
                .filter(m -> Modifier.isNative(m.getModifiers())).count();
        assertEquals(75, natives);
        assertFalse(Arrays.stream(GamepadJNI.class.getDeclaredMethods())
                .anyMatch(m -> Modifier.isNative(m.getModifiers())),
                "GamepadJNI must not declare natives anymore");
    }
}
