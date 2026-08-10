package com.zhongbai233.bench.gradle;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Supervises the first passthrough paired vertical slice using public ModDev run tasks. */
@DisableCachingByDefault(because = "Launches two long-lived Minecraft participant processes")
public abstract class PairedBenchTask extends DefaultTask {
    @Internal public abstract DirectoryProperty getProjectDirectory();
    @OutputDirectory public abstract DirectoryProperty getOutputDirectory();
    @Input public abstract Property<String> getHost();
    @Input public abstract Property<Integer> getConfiguredPort();
    @Input public abstract Property<Integer> getStartupTimeoutSeconds();
    @Input public abstract Property<Integer> getClientTimeoutSeconds();
    @Input public abstract Property<String> getScenarioFilter();
    @Input public abstract Property<String> getServerScenarioFilter();
    @Input public abstract Property<String> getClientScenarioFilter();

    @TaskAction
    public void runPaired() throws Exception {
        String host = getHost().get();
        if (!host.equals("127.0.0.1") && !host.equals("localhost")) {
            throw new GradleException("Paired passthrough MVP only supports a loopback host");
        }
        int port = getConfiguredPort().get() == 0 ? freePort() : getConfiguredPort().get();
        String session = UUID.randomUUID().toString();
        Path output = getOutputDirectory().get().getAsFile().toPath();
        Files.createDirectories(output);
        Path serverLog = output.resolve("server-launch.log");
        Path clientLog = output.resolve("client-launch.log");
        Instant started = Instant.now();
        Process server = null;
        Process client = null;
        int clientExit = -1;
        String status = "FAILED";
        try {
            cleanupParticipantResults();
            getLogger().lifecycle("MODBENCH paired session={} stage=starting-server port={}", session, port);
            server = launch("runBenchPairedServer", port, session, serverLog, participantFilter(true));
            waitForPort(host, port, server, Duration.ofSeconds(getStartupTimeoutSeconds().get()));
            getLogger().lifecycle("MODBENCH paired session={} stage=server-ready", session);
            client = launch("runBenchRemoteClient", port, session, clientLog, participantFilter(false));
            getLogger().lifecycle("MODBENCH paired session={} stage=client-running", session);
            Path serverReport = participantReport("paired-server");
            Path clientReport = participantReport("remote-client");
            waitForReport(clientReport, client, "remote client", Duration.ofSeconds(getClientTimeoutSeconds().get()));
            requirePassedReport(clientReport, "remote client");
            getLogger().lifecycle("MODBENCH paired session={} stage=client-passed", session);
            waitForReport(serverReport, server, "paired server", Duration.ofSeconds(getClientTimeoutSeconds().get()));
            requirePassedReport(serverReport, "paired server");
            getLogger().lifecycle("MODBENCH paired session={} stage=server-passed", session);
            if (client.isAlive()) {
                clientExit = -1;
            } else {
                clientExit = client.exitValue();
            }
                status = participantStatus(serverReport).equals("PASSED")
                    && participantStatus(clientReport).equals("PASSED") ? "PASSED" : "INCONCLUSIVE";
        } finally {
            destroyTree(client);
            destroyTree(server);
            writeSummary(output.resolve("summary.json"), session, host, port, started,
                    Instant.now(), status, clientExit, serverLog, clientLog);
        }
        if (!status.equals("PASSED")) {
            throw new GradleException("Paired benchmark failed; see " + output.resolve("summary.json"));
        }
    }

    private Process launch(String task, int port, String session, Path log, String filter) throws IOException {
        Path project = getProjectDirectory().get().getAsFile().toPath();
        Path wrapper = findWrapper(project);
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.add("cmd.exe");
            command.add("/d");
            command.add("/c");
        }
        command.add(wrapper.toString());
        command.add("--no-daemon");
        command.add(task);
        command.add("-PmodBench.internal.pairedPort=" + port);
        command.add("-PmodBench.internal.sessionId=" + session);
        if (!filter.isBlank()) {
            command.add("-PmodBench.scenarios=" + filter);
        }
        return new ProcessBuilder(command)
                .directory(project.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
    }

    private static Path findWrapper(Path project) {
        String name = isWindows() ? "gradlew.bat" : "gradlew";
        Path directory = project.toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(name);
            if (Files.isRegularFile(candidate)) return candidate;
            directory = directory.getParent();
        }
        throw new GradleException("No Gradle wrapper found at or above " + project);
    }

    private String participantFilter(boolean server) {
        String specific = server ? getServerScenarioFilter().get() : getClientScenarioFilter().get();
        return specific.isBlank() ? getScenarioFilter().get() : specific;
    }

    private Path participantReport(String runType) {
        Path root = getProjectDirectory().get().getAsFile().toPath().resolve("build/modBench/raw-results/default");
        return root.resolve(runType + "/summary.json");
    }

    private void cleanupParticipantResults() throws IOException {
        deleteRecursively(participantReport("paired-server").getParent());
        deleteRecursively(participantReport("remote-client").getParent());
    }

    private static void waitForReport(Path report, Process process, String participant, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(report)) return;
            if (!process.isAlive()) {
                // The game writes the report before the ModDev/Gradle wrapper fully exits, but
                // the filesystem timestamp can trail process termination by a few ticks.
                long graceDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (System.nanoTime() < graceDeadline && !Files.isRegularFile(report) ) {
                    Thread.sleep(50L);
                }
                if (Files.isRegularFile(report)) return;
                throw new GradleException(participant + " exited before writing " + report);
            }
            Thread.sleep(100L);
        }
        throw new GradleException("Timed out waiting for paired server report " + report);
    }

    private static void requirePassedReport(Path report, String participant) throws IOException {
        if (!Files.isRegularFile(report)) {
            throw new GradleException("Missing " + participant + " report: " + report);
        }
        JsonNode root = new ObjectMapper().readTree(Files.readString(report, StandardCharsets.UTF_8));
        String status = participantStatus(root);
        if ("FAILED".equals(status) || "ABORTED".equals(status)) {
            throw new GradleException(participant + " report is " + status + ": " + report);
        }
    }

    private static String participantStatus(Path report) throws IOException {
        return participantStatus(new ObjectMapper().readTree(Files.readString(report, StandardCharsets.UTF_8)));
    }

    private static String participantStatus(JsonNode root) {
        return root.path("summary").path("status").asText("UNKNOWN");
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void waitForPort(String host, int port, Process process, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new GradleException("Paired server exited before opening port " + port);
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 250);
                return;
            } catch (IOException ignored) {
                Thread.sleep(100L);
            }
        }
        throw new GradleException("Timed out waiting for paired server port " + port);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static void destroyTree(Process process) {
        if (process == null) return;
        List<ProcessHandle> handles = process.toHandle().descendants()
                .sorted(Comparator.comparingLong(ProcessHandle::pid).reversed()).toList();
        handles.forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                handles.forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static void writeSummary(Path file, String session, String host, int port,
            Instant started, Instant finished, String status, int clientExit,
            Path serverLog, Path clientLog) throws IOException {
        String json = """
                {
                  "schema": "modbench-paired-mvp-1",
                  "sessionId": "%s",
                  "status": "%s",
                  "startedAt": "%s",
                  "finishedAt": "%s",
                  "transport": {"backend": "PASSTHROUGH_TCP", "host": "%s", "port": %d},
                  "capabilities": {"phaseBarrier": false, "nonceHandshake": false, "networkProfile": false},
                  "participants": {
                    "serverReport": "../raw-results/default/paired-server/summary.json",
                    "clientReport": "../raw-results/default/remote-client/summary.json",
                    "clientExitCode": %d
                  },
                  "logs": {"server": "%s", "client": "%s"}
                }
                """.formatted(session, status, started, finished, host, port, clientExit,
                    escape(serverLog.toString()), escape(clientLog.toString()));
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}