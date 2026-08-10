package com.zhongbai233.bench.runtime.report;

import java.io.IOException;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Writes a full jstack-style thread dump so a timed-out scenario shows what every thread was
 * doing, including monitors, ownable synchronizers, and detected deadlocks.
 */
public final class ThreadDumpWriter {
    private ThreadDumpWriter() {}

    public static void write(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.writeString(target, dump(), StandardCharsets.UTF_8);
    }

    static String dump() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        StringBuilder out = new StringBuilder(1 << 16);
        out.append("ModBench thread dump taken at ").append(Instant.now()).append('\n');
        long[] deadlocked = threads.findDeadlockedThreads();
        if (deadlocked != null && deadlocked.length > 0) {
            out.append("!!! DEADLOCK DETECTED between ").append(deadlocked.length).append(" thread(s)\n");
        }
        out.append('\n');
        for (ThreadInfo info : threads.dumpAllThreads(true, true)) {
            if (info != null) appendThread(out, info);
        }
        return out.toString();
    }

    private static void appendThread(StringBuilder out, ThreadInfo info) {
        out.append('"').append(info.getThreadName()).append("\" #").append(info.getThreadId())
                .append(info.isDaemon() ? " daemon" : "")
                .append(" java.lang.Thread.State: ").append(info.getThreadState());
        if (info.getLockName() != null) {
            out.append(" (on ").append(info.getLockName());
            if (info.getLockOwnerName() != null) {
                out.append(" owned by \"").append(info.getLockOwnerName())
                        .append("\" #").append(info.getLockOwnerId());
            }
            out.append(')');
        }
        out.append('\n');
        StackTraceElement[] stack = info.getStackTrace();
        MonitorInfo[] monitors = info.getLockedMonitors();
        for (int depth = 0; depth < stack.length; depth++) {
            out.append("    at ").append(stack[depth]).append('\n');
            for (MonitorInfo monitor : monitors) {
                if (monitor.getLockedStackDepth() == depth) {
                    out.append("    - locked ").append(monitor).append('\n');
                }
            }
        }
        LockInfo[] synchronizers = info.getLockedSynchronizers();
        if (synchronizers.length > 0) {
            out.append("    Locked ownable synchronizers:\n");
            for (LockInfo lock : synchronizers) out.append("    - ").append(lock).append('\n');
        }
        out.append('\n');
    }
}
