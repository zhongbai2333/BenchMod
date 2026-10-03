package com.zhongbai233.bench.runtime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class GraphicsBackendValidationTest {
    @Test
    void acceptsTheActualRequestedBackendIgnoringPresentationCase() {
        assertEquals("", GraphicsBackendValidation.mismatch("opengl", "OpenGL"));
        assertEquals("", GraphicsBackendValidation.mismatch("vulkan", "Vulkan"));
    }

    @Test
    void rejectsFallbackAndUnknownDeviceBackends() {
        assertEquals("client.graphics.backend_mismatch=requested:vulkan,actual:OpenGL",
                GraphicsBackendValidation.mismatch("vulkan", "OpenGL"));
        assertFalse(GraphicsBackendValidation.mismatch("opengl", "unknown").isEmpty());
        assertFalse(GraphicsBackendValidation.mismatch("vulkan", "").isEmpty());
    }
}
