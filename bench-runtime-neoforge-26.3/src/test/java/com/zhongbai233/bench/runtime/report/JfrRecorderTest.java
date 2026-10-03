package com.zhongbai233.bench.runtime.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JfrRecorderTest {
    @Test
    void recordsAndDumpsAFlightRecorderFile(@TempDir Path directory) throws Exception {
        JfrRecorder recorder = new JfrRecorder();
        assertTrue(recorder.start(), "Flight Recorder must be available on the test JVM");
        assertTrue(recorder.isRecording());

        Path target = directory.resolve("artifacts/jfr/recording.jfr");
        recorder.dump(target);

        assertFalse(recorder.isRecording());
        assertTrue(Files.size(target) > 0);
        try (InputStream stream = Files.newInputStream(target)) {
            assertArrayEquals(new byte[] {'F', 'L', 'R'}, stream.readNBytes(3), "JFR magic header");
        }

        recorder.dump(target);
    }

    @Test
    void supportsSequentialScenarioRecordings(@TempDir Path directory) throws Exception {
        JfrRecorder recorder = new JfrRecorder();
        assertTrue(recorder.start("modbench:first"));
        assertFalse(recorder.start("modbench:overlap"));
        recorder.dump(directory.resolve("first.jfr"));

        assertTrue(recorder.start("modbench:second"));
        recorder.dump(directory.resolve("second.jfr"));
        assertTrue(Files.size(directory.resolve("first.jfr")) > 0);
        assertTrue(Files.size(directory.resolve("second.jfr")) > 0);
    }

    @Test
    void createsSafeStableScenarioFileNames() {
        assertEquals("super_lead.redstone-network-load.jfr",
                JfrRecorder.scenarioFileName("super_lead.redstone-network-load"));
        assertEquals("unsafe_scenario_.jfr", JfrRecorder.scenarioFileName("unsafe/scenario?"));
        assertEquals("scenario.jfr", JfrRecorder.scenarioFileName(" "));
    }
}
