package com.zhongbai233.bench.runtime.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;

/**
 * Owns one Flight Recorder recording per benchmark run.
 *
 * <p>Uses the JDK's low-overhead {@code default} configuration (about 1% overhead) so the
 * recording itself does not distort the measurement it documents. The dump happens in
 * {@code finish()} paths, so failed runs still keep their recording.
 */
public final class JfrRecorder {
    private Recording recording;

    /** Starts recording; returns {@code false} (and stays inert) when Flight Recorder is unavailable. */
    public boolean start() {
        return start("modbench");
    }

    /** Starts one named recording. A recorder cannot own two active recordings. */
    public boolean start(String name) {
        if (recording != null) return false;
        try {
            Recording started = new Recording(Configuration.getConfiguration("default"));
            started.setName(name == null || name.isBlank() ? "modbench" : name);
            started.start();
            recording = started;
            return true;
        } catch (Exception exception) {
            recording = null;
            return false;
        }
    }

    public boolean isRecording() {
        return recording != null;
    }

    /** Stable cross-platform file name for one scenario recording. */
    public static String scenarioFileName(String scenarioId) {
        String source = scenarioId == null ? "scenario" : scenarioId.trim();
        StringBuilder safe = new StringBuilder(Math.max(8, source.length()));
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            safe.append(Character.isLetterOrDigit(ch) || ch == '.' || ch == '-' || ch == '_' ? ch : '_');
        }
        if (safe.isEmpty()) safe.append("scenario");
        return safe.append(".jfr").toString();
    }

    /** Dumps the recording to {@code target} and closes it. Idempotent once dumped. */
    public void dump(Path target) throws IOException {
        if (recording == null) return;
        try {
            Files.createDirectories(target.getParent());
            recording.dump(target);
        } finally {
            recording.close();
            recording = null;
        }
    }
}
