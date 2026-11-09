package com.zhongbai233.bench.runtime.client;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;

/** Early run marker, available before the first window/world/GUI transition or client tick. */
public final class BenchInputIsolation {
    private static final CursorModeAccess NATIVE_CURSOR = new CursorModeAccess() {
        @Override
        public int mode(long handle) {
            return GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR);
        }

        @Override
        public void release(long handle) {
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    };

    private BenchInputIsolation() {}

    /** Ordinary game runs retain their original native input behavior. */
    public static boolean active() {
        String results = System.getProperty("modBench.resultDirectory");
        return results != null && !results.isBlank();
    }

    /**
     * Release a cursor that was captured before benchmark isolation became active, without
     * moving it to the centre of the game window as vanilla's release helper does.
     */
    public static void ensureCursorReleased(Window window) {
        if (!active() || window == null) return;
        ensureCursorReleased(window.handle(), NATIVE_CURSOR);
    }

    static void ensureCursorReleased(long handle, CursorModeAccess cursor) {
        if (handle == 0L) return;
        if (cursor.mode(handle) != GLFW.GLFW_CURSOR_NORMAL) cursor.release(handle);
    }

    interface CursorModeAccess {
        int mode(long handle);

        void release(long handle);
    }
}
