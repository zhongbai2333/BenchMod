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
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Supervises a dedicated server and one or more isolated physical clients. */
@DisableCachingByDefault(because = "Launches multiple long-lived Minecraft participant processes")
public abstract class PairedBenchTask extends DefaultTask {
    /** @return consumer project directory containing the Gradle wrapper */
    @Internal public abstract DirectoryProperty getProjectDirectory();
    /** @return consumer build directory containing participant results */
    @Internal public abstract DirectoryProperty getBuildDirectory();
    /** @return directory that receives the paired summary and participant logs */
    @OutputDirectory public abstract DirectoryProperty getOutputDirectory();
    /** @return loopback host exposed to clients */
    @Input public abstract Property<String> getHost();
    /** @return fixed server port, or zero to allocate a free port */
    @Input public abstract Property<Integer> getConfiguredPort();
    /** @return seconds allowed for participant JVM startup */
    @Input public abstract Property<Integer> getStartupTimeoutSeconds();
    /** @return seconds allowed for participant report completion */
    @Input public abstract Property<Integer> getClientTimeoutSeconds();
    /** @return number of separately launched physical clients */
    @Input public abstract Property<Integer> getClientCount();
    /** @return shared scenario filter fallback */
    @Input public abstract Property<String> getScenarioFilter();
    /** @return optional paired-server scenario filter */
    @Input public abstract Property<String> getServerScenarioFilter();
    /** @return optional scenario filter applied to every paired client */
    @Input public abstract Property<String> getClientScenarioFilter();
    /** @return explicit non-secret project properties forwarded to participant builds */
    @Input public abstract MapProperty<String, String> getParticipantProjectProperties();

    /** Launches, supervises, verifies, and terminates all paired participants. */
    @TaskAction
    public void runPaired() throws Exception {
        String host = getHost().get();
        if (!host.equals("127.0.0.1") && !host.equals("localhost")) {
            throw new GradleException("Paired passthrough MVP only supports a loopback host");
        }
        int port = getConfiguredPort().get() == 0 ? freePort() : getConfiguredPort().get();
        int clientCount = getClientCount().get();
        if (clientCount < 1 || clientCount > 8) {
            throw new GradleException("pairedClientCount must be between 1 and 8, got " + clientCount);
        }
        String session = UUID.randomUUID().toString();
        Path output = getOutputDirectory().get().getAsFile().toPath();
        Files.createDirectories(output);
        Path serverLog = output.resolve("server-launch.log");
        List<Path> clientLogs = new ArrayList<>();
        for (int index = 0; index < clientCount; index++) {
            clientLogs.add(output.resolve(clientCount == 1
                    ? "client-launch.log" : "client-" + index + "-launch.log"));
        }
        Instant started = Instant.now();
        Process server = null;
        List<Process> clients = new ArrayList<>();
        List<Integer> clientExits = new ArrayList<>();
        String status = "FAILED";
        try {
            cleanupParticipantResults(clientCount);
            getLogger().lifecycle("MODBENCH paired session={} stage=starting-server port={} clients={}",
                    session, port, clientCount);
            server = launch("runBenchPairedServer", port, session, serverLog,
                    participantFilter(true), null, clientCount);
            waitForPort(host, port, server, Duration.ofSeconds(getStartupTimeoutSeconds().get()));
            getLogger().lifecycle("MODBENCH paired session={} stage=server-ready", session);
            for (int index = 0; index < clientCount; index++) {
                Process client = launch("runBenchRemoteClient", port, session, clientLogs.get(index),
                        participantFilter(false), index, clientCount);
                clients.add(client);
                // ModDev materializes one task-scoped @argfile for the remote-client run.
                // Concurrent child builds can overwrite it before the first Java process
                // consumes its username/game-directory arguments.  Once the game logger
                // has emitted a main-thread record, that JVM already owns an immutable
                // command line and the next participant may safely materialize its args.
                waitForClientJvmStart(clientLogs.get(index), client, index,
                        Duration.ofSeconds(getStartupTimeoutSeconds().get()));
                getLogger().lifecycle("MODBENCH paired session={} stage=client-jvm-started index={}",
                        session, index);
            }
            getLogger().lifecycle("MODBENCH paired session={} stage=clients-running count={}",
                    session, clientCount);
            Path serverReport = participantReport("paired-server");
            List<Path> clientReports = new ArrayList<>();
            for (int index = 0; index < clientCount; index++) {
                Path report = participantReport(PairedParticipantLayout.remoteClientRunType(index, clientCount));
                clientReports.add(report);
                waitForReport(report, clients.get(index), "remote client " + index,
                        Duration.ofSeconds(getClientTimeoutSeconds().get()));
                requirePassedReport(report, "remote client " + index);
                getLogger().lifecycle("MODBENCH paired session={} stage=client-passed index={}", session, index);
            }
            waitForReport(serverReport, server, "paired server", Duration.ofSeconds(getClientTimeoutSeconds().get()));
            requirePassedReport(serverReport, "paired server");
            getLogger().lifecycle("MODBENCH paired session={} stage=server-passed", session);
            for (Process client : clients) {
                clientExits.add(client.isAlive() ? -1 : client.exitValue());
            }
            boolean clientsPassed = true;
            for (Path report : clientReports) {
                clientsPassed &= participantStatus(report).equals("PASSED");
            }
            status = participantStatus(serverReport).equals("PASSED") && clientsPassed
                    ? "PASSED" : "INCONCLUSIVE";
        } finally {
            for (Process client : clients) {
                destroyTree(client);
            }
            destroyTree(server);
            writeSummary(output.resolve("summary.json"), session, host, port, started,
                    Instant.now(), status, clientCount, clientExits, serverLog, clientLogs);
        }
        if (!status.equals("PASSED")) {
            throw new GradleException("Paired benchmark failed; see " + output.resolve("summary.json"));
        }
    }

    private Process launch(String task, int port, String session, Path log, String filter,
            Integer clientIndex, int clientCount) throws IOException {
        Path project = getProjectDirectory().get().getAsFile().toPath();
        Path wrapper = findWrapper(project);
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.add("cmd.exe");
            command.add("/d");
            command.add("/c");
        } else if (!Files.isExecutable(wrapper)) {
            command.add("bash");
        }
        command.add(wrapper.toString());
        command.add("--no-daemon");
        // Participant builds run concurrently with different client-index
        // properties. A shared configuration-cache entry can otherwise publish
        // one participant's username, game directory and result directory into
        // another process before Gradle notices the property mismatch.
        command.add("--no-configuration-cache");
        command.add(task);
        command.add("-PmodBench.internal.pairedPort=" + port);
        command.add("-PmodBench.internal.sessionId=" + session);
        command.add("-PmodBench.internal.clientCount=" + clientCount);
        if (clientIndex != null) {
            command.add("-PmodBench.internal.clientIndex=" + clientIndex);
        }
        if (!filter.isBlank()) {
            command.add("-PmodBench.scenarios=" + filter);
        }
        getParticipantProjectProperties().get().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (entry.getKey().isBlank() || entry.getKey().startsWith("modBench.internal.")) {
                        throw new GradleException("Unsafe paired project property name: " + entry.getKey());
                    }
                    command.add("-P" + entry.getKey() + "=" + entry.getValue());
                });
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
        Path root = getBuildDirectory().get().getAsFile().toPath().resolve("modBench/raw-results/default");
        return root.resolve(runType + "/summary.json");
    }

    private Path participantRunDirectory(String runType) {
        Path root = getBuildDirectory().get().getAsFile().toPath().resolve("modBench/runs/default");
        return root.resolve(runType);
    }

    private void cleanupParticipantResults(int clientCount) throws IOException {
        deleteRecursively(participantReport("paired-server").getParent());
        deleteRecursively(participantRunDirectory("paired-server"));
        for (int index = 0; index < clientCount; index++) {
            String runType = PairedParticipantLayout.remoteClientRunType(index, clientCount);
            deleteRecursively(participantReport(runType).getParent());
            deleteRecursively(participantRunDirectory(runType));
        }
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
        throw new GradleException("Timed out waiting for " + participant + " report " + report);
    }

    private static void waitForClientJvmStart(Path log, Process process, int clientIndex, Duration timeout)
            throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(log)
                    && clientJvmStarted(Files.readString(log, StandardCharsets.UTF_8))) {
                return;
            }
            if (!process.isAlive()) {
                throw new GradleException("remote client " + clientIndex
                        + " exited before its game JVM consumed the ModDev argument file; see " + log);
            }
            Thread.sleep(100L);
        }
        throw new GradleException("Timed out waiting for remote client " + clientIndex
                + " game JVM startup marker in " + log);
    }

    static boolean clientJvmStarted(CharSequence log) {
        if (log == null) return false;
        String text = log.toString();
        return text.contains("[main/INFO]") || text.contains("[main/DEBUG]")
                || text.contains("[main/WARN]") || text.contains("[main/ERROR]");
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
            Instant started, Instant finished, String status, int clientCount, List<Integer> clientExits,
            Path serverLog, List<Path> clientLogs) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        root.put("schema", "modbench-paired-multi-client-1");
        root.put("sessionId", session);
        root.put("status", status);
        root.put("startedAt", started.toString());
        root.put("finishedAt", finished.toString());
        var transport = root.putObject("transport");
        transport.put("backend", "PASSTHROUGH_TCP");
        transport.put("host", host);
        transport.put("port", port);
        var capabilities = root.putObject("capabilities");
        capabilities.put("physicalClientCount", clientCount);
        capabilities.put("isolatedClientDirectories", true);
        capabilities.put("phaseBarrier", false);
        capabilities.put("nonceHandshake", false);
        capabilities.put("networkProfile", false);
        var participants = root.putObject("participants");
        participants.put("serverReport", "../raw-results/default/paired-server/summary.json");
        var clientArray = participants.putArray("clients");
        for (int index = 0; index < clientCount; index++) {
            var client = clientArray.addObject();
            client.put("index", index);
            client.put("report", "../raw-results/default/"
                    + PairedParticipantLayout.remoteClientRunType(index, clientCount)
                    + "/summary.json");
            client.put("exitCode", index < clientExits.size() ? clientExits.get(index) : -1);
        }
        var logs = root.putObject("logs");
        logs.put("server", serverLog.toString());
        var clientLogArray = logs.putArray("clients");
        clientLogs.forEach(path -> clientLogArray.add(path.toString()));
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
