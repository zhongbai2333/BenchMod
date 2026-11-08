package com.zhongbai233.bench.runtime.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.TextInputManager;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.Window;
import sun.misc.Unsafe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Null native context is a tripwire: a cursor/IME entry must be cancelled before touching it. */
@ResourceLock("SYSTEM_PROPERTIES")
@ResourceLock("MINECRAFT_CLIENT_GLOBALS")
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
        repeatedGrabsWithScopedClient(InputQuirks.RESTORE_KEY_STATE_AFTER_MOUSE_GRAB);
    }
    @Test void nonMacKeyStateRestoreBranchUsesScopedSingletonWithoutNativeKeyPolling() throws Exception {
        repeatedGrabsWithScopedClient(true);
    }
    @Test void macKeyStateSkipBranchKeepsOriginalCursorAndRecursiveGuiAssertions() throws Exception {
        repeatedGrabsWithScopedClient(false);
    }
    @SuppressWarnings("unchecked")
    private void repeatedGrabsWithScopedClient(boolean restoreKeys) throws Exception {
        StubMinecraft minecraft = allocate(StubMinecraft.class);
        minecraft.window = allocate(Window.class);
        minecraft.active = true;
        set(minecraft.window, "width", 1280); set(minecraft.window, "height", 720);
        assertEquals(0L, minecraft.window.handle(), "fixture must never own a native SDL window");
        MouseHandler mouse = new MouseHandler(minecraft);
        minecraft.mouse = mouse;
        StubGui gui = allocate(StubGui.class);
        gui.client = minecraft;
        Field guiField = Minecraft.class.getDeclaredField("gui");
        guiField.setAccessible(true);
        guiField.set(minecraft, gui);
        set(mouse, "xpos", 23.5); set(mouse, "ypos", 93.0);
        Field singleton = Minecraft.class.getDeclaredField("instance"); singleton.setAccessible(true);
        Object previousClient = singleton.get(null);
        Field mappingsField = KeyMapping.class.getDeclaredField("ALL"); mappingsField.setAccessible(true);
        Map<String, KeyMapping> mappings = (Map<String, KeyMapping>) mappingsField.get(null);
        Map<String, KeyMapping> previousMappings = new LinkedHashMap<>(mappings);
        FocusProbeKeyMapping probe = allocate(FocusProbeKeyMapping.class);
        probe.client = minecraft;
        // This non-constant final is platform-dependent. The scoped fixture exercises both
        // actual MouseHandler branches on this host; it does not claim a native Linux GUI.
        Class.forName(InputQuirks.class.getName(), true, InputQuirks.class.getClassLoader());
        Field quirk = InputQuirks.class.getDeclaredField("RESTORE_KEY_STATE_AFTER_MOUSE_GRAB");
        Unsafe unsafe = unsafe();
        Object quirkBase = unsafe.staticFieldBase(quirk);
        long quirkOffset = unsafe.staticFieldOffset(quirk);
        boolean previousQuirk = unsafe.getBooleanVolatile(quirkBase, quirkOffset);
        try {
            singleton.set(null, minecraft);
            mappings.clear(); mappings.put("modbench.test.owned-focus-probe", probe);
            unsafe.putBooleanVolatile(quirkBase, quirkOffset, restoreKeys);
            assertSame(minecraft, Minecraft.getInstance());
            assertEquals(restoreKeys, unsafe.getBooleanVolatile(quirkBase, quirkOffset));
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
            assertEquals(restoreKeys ? 8 : 0, probe.focusChecks,
                    "only the non-Mac branch must traverse actual KeyMapping.setAll once per grab");
            assertEquals(Map.of("modbench.test.owned-focus-probe", probe), mappings,
                    "the fixture may not acquire unrelated registered mappings");
        } finally {
            // ALL is isolated without touching MAP or any original mapping's key/down/click state.
            mappings.clear(); mappings.putAll(previousMappings);
            unsafe.putBooleanVolatile(quirkBase, quirkOffset, previousQuirk);
            singleton.set(null, previousClient);
            assertEquals(previousMappings, mappings);
            assertEquals(previousQuirk, unsafe.getBooleanVolatile(quirkBase, quirkOffset));
            assertSame(previousClient, Minecraft.getInstance());
        }
    }
    @Test void releaseDoesNotRecenterEvenWhenLogicalGrabFlagWasAlreadySet() throws Exception {
        MouseHandler mouse = new MouseHandler(null);
        set(mouse, "mouseGrabbed", true); set(mouse, "xpos", 23.5); set(mouse, "ypos", 93.0);
        assertDoesNotThrow(mouse::releaseMouse);
        assertFalse(mouse.isMouseGrabbed());
        assertEquals(23.5, mouse.xpos()); assertEquals(93.0, mouse.ypos());
    }
    @Test void lowLevelGrabAndReleaseAreBlockedBeforeFirstCursorPositionOrModeCall() {
        assertDoesNotThrow(() -> InputConstants.grabMouse(null, 640, 360));
        assertDoesNotThrow(() -> InputConstants.releaseMouse(null, 640, 360));
    }
    @Test void sdlTextInputWritesAreBlockedInBothDirectionsWhileTextLogicStillWorks() throws Exception {
        Window window = allocate(Window.class);
        TextInputManager manager = new TextInputManager(window);
        Object owner = new Object();
        for (int index = 0; index < 16; index++) {
            assertDoesNotThrow(() -> manager.startTextInput(owner));
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
            assertThrows(NullPointerException.class, () -> InputConstants.grabMouse(null, 640, 360));
            assertThrows(NullPointerException.class, () -> InputConstants.releaseMouse(null, 640, 360));
            TextInputManager manager = new TextInputManager(null);
            assertThrows(NullPointerException.class, () -> manager.startTextInput(new Object()));
        }
    }
    /** Constructor-free fixtures retain actual MouseHandler bytecode, with no SDL window creation. */
    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(unsafe().allocateInstance(type));
    }
    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
    private static final class FocusProbeKeyMapping extends KeyMapping {
        StubMinecraft client;
        int focusChecks;
        private FocusProbeKeyMapping() { super("modbench.test.owned-focus-probe", -1, null); }
        @Override protected boolean shouldSetOnIngameFocus() {
            assertSame(client, Minecraft.getInstance());
            focusChecks++;
            // setAll still executes; the test-owned mapping must not poll SDL on handle zero.
            return false;
        }
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
    }
    private static final class StubGui extends Gui {
        StubMinecraft client;
        private StubGui() { super(null, null, null); }
        @Override public void setScreen(Screen screen) {
            assertNull(screen);
            // Actual setScreen(null) calls grabMouse again. Its temporary grabbed flag is essential.
            client.guiOpen = false;
            client.closes++;
            client.mouse.grabMouse();
        }
    }
    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
}
