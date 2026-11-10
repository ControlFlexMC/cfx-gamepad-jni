package com.ifels.gamepadjni;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingsTest {

    @BeforeEach
    @AfterEach
    void reset() {
        Bindings.clear();
    }

    @Test
    void unsetBindingThrowsUnsatisfiedLinkErrorNotNpe() {
        UnsatisfiedLinkError e = assertThrows(UnsatisfiedLinkError.class, Bindings::b);
        assertTrue(e.getMessage().contains("no binding installed"), e.getMessage());
    }

    @Test
    void reportsJniAndJna() {
        assertEquals("none", Bindings.describe());
        Bindings.set(JniBinding.INSTANCE);
        assertTrue(Bindings.isJni());
        assertEquals("jni", Bindings.describe());
    }
}
