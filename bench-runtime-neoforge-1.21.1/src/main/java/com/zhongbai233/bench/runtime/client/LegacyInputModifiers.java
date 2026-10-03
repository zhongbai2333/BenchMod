package com.zhongbai233.bench.runtime.client;

/**
 * Lexically scoped synthetic key state for legacy Screen modifier helpers. It never changes
 * physical GLFW input and is only active on the thread dispatching an automation callback.
 */
public final class LegacyInputModifiers {
    private static final ThreadLocal<Integer> CURRENT = new ThreadLocal<>();

    private LegacyInputModifiers() {}

    public static Integer current() { return CURRENT.get(); }

    /** Null means delegate to the physical-input implementation, including outside callbacks. */
    public static Boolean keyDown(int mask) {
        Integer modifiers = CURRENT.get();
        return modifiers == null ? null : (modifiers & mask) != 0;
    }

    public static Scope enter(int modifiers) {
        Integer previous = CURRENT.get();
        CURRENT.set(modifiers);
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {
        private final Integer previous;
        private boolean closed;

        private Scope(Integer previous) { this.previous = previous; }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
