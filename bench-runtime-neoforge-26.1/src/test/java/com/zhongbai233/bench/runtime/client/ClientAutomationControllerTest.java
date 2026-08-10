package com.zhongbai233.bench.runtime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ClientAutomationControllerTest {
    @Test
    void normalizesScreenshotNames() {
        assertEquals("view.png", ClientAutomationController.normalizeScreenshotName("view"));
        assertEquals("view.png", ClientAutomationController.normalizeScreenshotName("view.png"));
    }

    @Test
    void rejectsUnsafeScreenshotNames() {
        assertThrows(IllegalArgumentException.class,
                () -> ClientAutomationController.normalizeScreenshotName("../outside"));
        assertThrows(IllegalArgumentException.class,
                () -> ClientAutomationController.normalizeScreenshotName("nested/view"));
        assertThrows(IllegalArgumentException.class,
                () -> ClientAutomationController.normalizeScreenshotName(" "));
    }
}