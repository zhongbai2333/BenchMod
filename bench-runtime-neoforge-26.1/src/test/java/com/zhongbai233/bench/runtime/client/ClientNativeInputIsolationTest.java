package com.zhongbai233.bench.runtime.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.TextInputManager;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.Window;
import sun.misc.Unsafe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

/** Null native context is a tripwire: a cursor/IME entry must be cancelled before touching it. */
@ResourceLock("SYSTEM_PROPERTIES")
class ClientNativeInputIsolationTest {
    private String previous;
    @BeforeEach void activate() {
        previous = System.getProperty("modBench.resultDirectory");
        System.setProperty("modBench.resultDirectory", "unit-test-owned-results");
    }
    @AfterEach void restore() {
        if (previous == null) System.clearProperty("modBench.resultDirectory");
        else System.setProperty("modBench.resultDirectory", previous);
    }
    @Test void repeatedGrabsKeepGuiCloseSemanticsWithoutMovingOrCapturingCursor() throws Exception {
        StubMinecraft minecraft = allocate(StubMinecraft.class);
        minecraft.window = allocate(Window.class);
        minecraft.active = true;
        set(minecraft.window, "width", 1280); set(minecraft.window, "height", 720);
        MouseHandler mouse = new MouseHandler(minecraft);
        minecraft.mouse = mouse;
        set(mouse, "xpos", 23.5); set(mouse, "ypos", 93.0);
        for (int index = 0; index < 8; index++) {
            minecraft.guiOpen = true;
            assertDoesNotThrow(mouse::grabMouse);
            assertFalse(mouse.isMouseGrabbed());
            assertFalse(minecraft.guiOpen, "grab-only GUI closing callbacks must still work");
            assertEquals(index + 1, minecraft.closes, "setScreen's recursive grab must terminate");
            assertEquals(10000, minecraft.missTimeForTest());
            assertTrue((boolean) get(mouse, "ignoreFirstMove"));
            assertEquals(23.5, mouse.xpos()); assertEquals(93.0, mouse.ypos());
            assertDoesNotThrow(mouse::releaseMouse);
            assertEquals(23.5, mouse.xpos()); assertEquals(93.0, mouse.ypos());
        }
        minecraft.active = false; minecraft.guiOpen = true;
        mouse.grabMouse();
        assertTrue(minecraft.guiOpen, "inactive windows must not close the GUI");
        assertEquals(8, minecraft.closes);
    }
    @Test void releaseDoesNotRecenterEvenWhenLogicalGrabFlagWasAlreadySet() throws Exception {
        MouseHandler mouse = new MouseHandler(null);
        set(mouse, "mouseGrabbed", true); set(mouse, "xpos", 23.5); set(mouse, "ypos", 93.0);
        assertDoesNotThrow(mouse::releaseMouse);
        assertFalse(mouse.isMouseGrabbed());
        assertEquals(23.5, mouse.xpos()); assertEquals(93.0, mouse.ypos());
    }
    @Test void lowLevelGrabAndReleaseAreBlockedBeforeFirstCursorPositionOrModeCall() {
        assertDoesNotThrow(() -> InputConstants.grabOrReleaseMouse(null, 212995, 640, 360));
        assertDoesNotThrow(() -> InputConstants.grabOrReleaseMouse(null, 212993, 640, 360));
    }
    @Test void imeModeWritesAreBlockedInBothDirectionsWhileTextLogicStillWorks() throws Exception {
        TextInputManager manager = new TextInputManager(null);
        Method nativeWrite = TextInputManager.class.getDeclaredMethod("setIMEInputMode", boolean.class);
        nativeWrite.setAccessible(true);
        for (int index = 0; index < 16; index++) {
            assertDoesNotThrow(() -> nativeWrite.invoke(manager, true));
            assertDoesNotThrow(() -> nativeWrite.invoke(manager, false));
            set(manager, "imeRequested", true);
            assertDoesNotThrow(manager::startTextInput);
            assertTrue((boolean) get(manager, "textInputEnabled"));
            manager.stopTextInput();
            assertFalse((boolean) get(manager, "textInputEnabled"));
        }
    }
    @Test void ordinaryAndBlankMarkerRunsStillEnterOriginalMouseAndImeImplementations() throws Exception {
        for (String marker : new String[]{null, "", "  "}) {
            if (marker == null) System.clearProperty("modBench.resultDirectory");
            else System.setProperty("modBench.resultDirectory", marker);
            assertFalse(BenchInputIsolation.active());
            assertThrows(NullPointerException.class, () -> new MouseHandler(null).grabMouse());
            assertThrows(NullPointerException.class, () -> InputConstants.grabOrReleaseMouse(null, 212995, 640, 360));
            TextInputManager manager = new TextInputManager(null);
            Method nativeWrite = TextInputManager.class.getDeclaredMethod("setIMEInputMode", boolean.class);
            nativeWrite.setAccessible(true);
            InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> nativeWrite.invoke(manager, true));
            assertInstanceOf(NullPointerException.class, error.getCause());
        }
    }
    /** Constructor-free fixtures retain actual MouseHandler bytecode, with no GLFW window creation. */
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }
    private static final class StubMinecraft extends Minecraft {
        Window window;
        MouseHandler mouse;
        boolean active;
        boolean guiOpen;
        int closes;
        private StubMinecraft() { super(null); }
        int missTimeForTest() { return missTime; }
        @Override public boolean isWindowActive() { return active; }
        @Override public Window getWindow() { return window; }
        @Override public void setScreen(Screen screen) {
            assertNull(screen); guiOpen = false; closes++;
            // Actual setScreen(null) calls grabMouse again. Its temporary grabbed flag is essential.
            mouse.grabMouse();
        }
    }
    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
}
