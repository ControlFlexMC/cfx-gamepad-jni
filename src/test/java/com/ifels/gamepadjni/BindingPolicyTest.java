package com.ifels.gamepadjni;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingPolicyTest {

    @Test
    void parsesKnownValues() {
        assertEquals(BindingMode.AUTO, BindingPolicy.parse(null));
        assertEquals(BindingMode.AUTO, BindingPolicy.parse(""));
        assertEquals(BindingMode.AUTO, BindingPolicy.parse("auto"));
        assertEquals(BindingMode.JNI_ONLY, BindingPolicy.parse("jni"));
        assertEquals(BindingMode.JNA_ONLY, BindingPolicy.parse("jna"));
    }

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(BindingMode.JNI_ONLY, BindingPolicy.parse("JNI"));
        assertEquals(BindingMode.JNA_ONLY, BindingPolicy.parse("Jna"));
        assertEquals(BindingMode.AUTO, BindingPolicy.parse("AUTO"));
    }

    @Test
    void unknownValueFallsBackToAuto() {
        assertEquals(BindingMode.AUTO, BindingPolicy.parse("garbage"));
        assertFalse(BindingPolicy.isKnown("garbage"));
        assertTrue(BindingPolicy.isKnown("jna"));
        assertTrue(BindingPolicy.isKnown(null));
    }

    @Test
    void propertyWinsOverEnv() {
        assertEquals(BindingMode.JNI_ONLY, BindingPolicy.mode("jni", "jna"));
        assertEquals(BindingMode.JNA_ONLY, BindingPolicy.mode(null, "jna"));
        assertEquals(BindingMode.JNA_ONLY, BindingPolicy.mode("", "jna"));
        assertEquals(BindingMode.AUTO, BindingPolicy.mode(null, null));
    }

    @Test
    void invalidPropertyDoesNotFallThroughToEnv() {
        assertEquals(BindingMode.AUTO, BindingPolicy.mode("garbage", "jna"));
    }

    @Test
    void jnaPresentUsesGivenLoader() {
        assertTrue(BindingPolicy.jnaPresent(BindingPolicyTest.class.getClassLoader()));
        ClassLoader isolated = new ClassLoader(null) { };
        assertFalse(BindingPolicy.jnaPresent(isolated));
    }
}
