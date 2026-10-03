package com.zhongbai233.bench.runtime.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

/** Tests scoped modifier semantics. Actual client Mixin application requires a client launch. */
class LegacyInputModifiersTest {
    @Test
    void mixinIsClientOnlyAndTargetsExistingStaticBooleanHelpers() throws Exception {
        try (var input = LegacyInputModifiers.class.getResourceAsStream("/modbench.client.mixins.json")) {
            assertNotNull(input);
            var config = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(config.has("mixins"), "production mixin must never target a dedicated server");
            assertEquals("ScreenInputModifiersMixin", config.getAsJsonArray("client").get(0).getAsString());
        }
        for (String name : java.util.List.of("hasShiftDown", "hasControlDown", "hasAltDown")) {
            var method = net.minecraft.client.gui.screens.Screen.class.getDeclaredMethod(name);
            assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
            assertEquals(boolean.class, method.getReturnType());
        }
        assertNull(LegacyInputModifiers.class.getResourceAsStream("/modbench.test-client.mixins.json"));
    }

    @Test
    void scopeExposesAllSyntheticModifiers() {
        int control = GLFW.GLFW_MOD_CONTROL;
        try (var ignored = LegacyInputModifiers.enter(GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT | control)) {
            assertTrue(LegacyInputModifiers.keyDown(GLFW.GLFW_MOD_SHIFT));
            assertTrue(LegacyInputModifiers.keyDown(GLFW.GLFW_MOD_ALT));
            assertTrue(LegacyInputModifiers.keyDown(GLFW.GLFW_MOD_CONTROL));
        }
        assertNull(LegacyInputModifiers.current());
    }

    @Test
    void zeroMaskAndNestedCallbacksRestoreThePreviousState() {
        try (var outer = LegacyInputModifiers.enter(GLFW.GLFW_MOD_SHIFT)) {
            assertTrue(LegacyInputModifiers.keyDown(GLFW.GLFW_MOD_SHIFT));
            try (var inner = LegacyInputModifiers.enter(0)) {
                assertFalse(LegacyInputModifiers.keyDown(GLFW.GLFW_MOD_SHIFT));
                assertFalse(LegacyInputModifiers.keyDown(GLFW.GLFW_MOD_ALT));
                assertFalse(LegacyInputModifiers.keyDown(GLFW.GLFW_MOD_CONTROL));
            }
            assertTrue(LegacyInputModifiers.keyDown(GLFW.GLFW_MOD_SHIFT));
        }
        assertNull(LegacyInputModifiers.current());
    }

    @Test
    void controlAndSuperMasksStayDistinctForPlatformSpecificShortcuts() {
        try (var ignored = LegacyInputModifiers.enter(GLFW.GLFW_MOD_SUPER)) {
            assertTrue(LegacyInputModifiers.keyDown(GLFW.GLFW_MOD_SUPER));
            assertFalse(LegacyInputModifiers.keyDown(GLFW.GLFW_MOD_CONTROL));
        }
        assertNull(LegacyInputModifiers.keyDown(GLFW.GLFW_MOD_CONTROL));
    }

    @Test
    void callbackFailureDoesNotLeakSyntheticState() {
        assertThrows(IllegalStateException.class, () -> {
            try (var ignored = LegacyInputModifiers.enter(GLFW.GLFW_MOD_ALT)) {
                assertTrue(LegacyInputModifiers.keyDown(GLFW.GLFW_MOD_ALT));
                throw new IllegalStateException("callback failed");
            }
        });
        assertNull(LegacyInputModifiers.current());
    }

    @Test
    void modifierScopeDoesNotCrossThreads() throws Exception {
        try (var ignored = LegacyInputModifiers.enter(GLFW.GLFW_MOD_SHIFT)) {
            var otherThread = java.util.concurrent.CompletableFuture.supplyAsync(LegacyInputModifiers::current);
            assertNull(otherThread.get());
            assertTrue(LegacyInputModifiers.keyDown(GLFW.GLFW_MOD_SHIFT));
        }
    }
}
