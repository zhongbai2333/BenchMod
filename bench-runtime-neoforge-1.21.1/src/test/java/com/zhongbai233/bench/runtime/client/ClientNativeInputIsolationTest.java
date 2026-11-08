package com.zhongbai233.bench.runtime.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.MouseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the legacy client contract without loading client Mixins in NeoForge's server-only
 * 1.21.1 JUnit launcher. Actual transformation remains scoped to a real client launch.
 */
@ResourceLock("SYSTEM_PROPERTIES")
class ClientNativeInputIsolationTest {
    private final String previous = System.getProperty("modBench.resultDirectory");

    @AfterEach
    void restore() {
        if (previous == null) System.clearProperty("modBench.resultDirectory");
        else System.setProperty("modBench.resultDirectory", previous);
    }

    @Test
    void runMarkerOnlyActivatesForOwnedNonBlankResultDirectories() {
        for (String value : new String[]{"", "  "}) {
            System.setProperty("modBench.resultDirectory", value);
            assertFalse(BenchInputIsolation.active());
        }
        System.clearProperty("modBench.resultDirectory");
        assertFalse(BenchInputIsolation.active());
        System.setProperty("modBench.resultDirectory", "unit-test-owned-results");
        assertTrue(BenchInputIsolation.active());
    }

    @Test
    void productionConfigKeepsNativeIsolationClientOnly() throws Exception {
        try (var input = BenchInputIsolation.class.getResourceAsStream("/modbench.client.mixins.json")) {
            assertNotNull(input);
            var config = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertFalse(config.has("mixins"), "native input Mixins must never target a dedicated server");
            var client = config.getAsJsonArray("client");
            List<String> entries = new java.util.ArrayList<>();
            client.forEach(element -> entries.add(element.getAsString()));
            assertTrue(entries.contains("client.MouseHandlerMixin"));
            assertTrue(entries.contains("client.InputConstantsMixin"));
        }
        assertNull(BenchInputIsolation.class.getResourceAsStream(
                "/modbench_runtime.input-isolation-test.mixins.json"));
    }

    @Test
    void mixinTargetsStillMatchLegacyMinecraftSignatures() throws Exception {
        var nativeBoundary = InputConstants.class.getDeclaredMethod(
                "grabOrReleaseMouse", long.class, int.class, double.class, double.class);
        assertTrue(Modifier.isStatic(nativeBoundary.getModifiers()));
        assertEquals(void.class, nativeBoundary.getReturnType());
        assertEquals(void.class, MouseHandler.class.getDeclaredMethod("grabMouse").getReturnType());
        assertEquals(void.class, MouseHandler.class.getDeclaredMethod("releaseMouse").getReturnType());
        assertEquals(boolean.class, MouseHandler.class.getDeclaredMethod("isMouseGrabbed").getReturnType());
        assertEquals(double.class, MouseHandler.class.getDeclaredField("xpos").getType());
        assertEquals(double.class, MouseHandler.class.getDeclaredField("ypos").getType());
    }
}
