package com.zhongbai233.bench.runtime.client;

import com.zhongbai233.bench.api.neoforge.client.BenchGuiSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhongbai233.bench.api.client.gui.BenchGuiSelector;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BenchGuiSessionTest {
    @Test
    void defaultClicksAndDragsForwardTheSdlPrimaryButton() {
        AtomicInteger button = new AtomicInteger(-1);
        BenchGuiSession session = (BenchGuiSession) Proxy.newProxyInstance(
                BenchGuiSession.class.getClassLoader(), new Class<?>[] { BenchGuiSession.class },
                (proxy, method, args) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    if (method.getName().equals("click")) button.set((Integer) args[1]);
                    else if (method.getName().equals("drag")) button.set((Integer) args[3]);
                    else throw new UnsupportedOperationException(method.getName());
                    return true;
                });
        var selector = BenchGuiSelector.semanticName("target");
        assertTrue(session.click(selector));
        assertEquals(1, button.get(), "26.3 uses SDL's one-based primary button");
        button.set(-1);
        assertTrue(session.drag(selector, 20, 10));
        assertEquals(1, button.get(), "drag must not retain GLFW's zero-based button");
    }
}
