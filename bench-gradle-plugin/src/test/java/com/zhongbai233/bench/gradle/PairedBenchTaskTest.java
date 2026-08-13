package com.zhongbai233.bench.gradle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PairedBenchTaskTest {
    @Test
    void recognizesOnlyGameJvmMainThreadLogRecords() {
        assertFalse(PairedBenchTask.clientJvmStarted(null));
        assertFalse(PairedBenchTask.clientJvmStarted("> Task :runBenchRemoteClient\n"));
        assertFalse(PairedBenchTask.clientJvmStarted("[Gradle worker/INFO] configuring run\n"));
        assertTrue(PairedBenchTask.clientJvmStarted("[07:09:11] [main/INFO] [FMLLoader/]: Loading\n"));
        assertTrue(PairedBenchTask.clientJvmStarted("[07:09:11] [main/DEBUG] [FMLPaths/]: Path\n"));
    }
}
