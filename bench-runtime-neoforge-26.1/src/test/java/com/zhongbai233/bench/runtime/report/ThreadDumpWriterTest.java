package com.zhongbai233.bench.runtime.report;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThreadDumpWriterTest {
    @Test
    void dumpContainsTheCurrentThreadWithItsStack() {
        String dump = ThreadDumpWriter.dump();

        assertTrue(dump.contains('"' + Thread.currentThread().getName() + '"'));
        assertTrue(dump.contains("java.lang.Thread.State: "));
        // Frames may carry a module-loader prefix (e.g. TRANSFORMER/...), so match the suffix only.
        assertTrue(dump.contains(ThreadDumpWriter.class.getName() + ".dump"));
    }

    @Test
    void writeCreatesParentDirectories(@TempDir Path directory) throws Exception {
        Path target = directory.resolve("artifacts/thread-dumps/scenario.txt");
        ThreadDumpWriter.write(target);

        assertTrue(Files.isRegularFile(target));
        assertTrue(Files.size(target) > 0);
    }
}
