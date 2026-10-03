package com.zhongbai233.bench.runtime;

import com.zhongbai233.bench.api.BenchMetricDescriptor;
import com.zhongbai233.bench.api.BenchPhase;
import com.zhongbai233.bench.api.BenchProvider;
import com.zhongbai233.bench.api.BenchStatus;
import com.zhongbai233.bench.api.MetricDirection;
import com.zhongbai233.bench.api.discovery.ProviderDiscovery;
import com.zhongbai233.bench.api.neoforge.server.BenchCancellationToken;
import com.zhongbai233.bench.api.neoforge.server.BenchMetricRecorder;
import com.zhongbai233.bench.api.neoforge.server.BenchScheduler;
import com.zhongbai233.bench.api.neoforge.server.BenchServerContextAdapter;
import com.zhongbai233.bench.runtime.metrics.JvmMetricSampler;
import com.zhongbai233.bench.runtime.metrics.ScenarioMetricCollector;
import com.zhongbai233.bench.runtime.report.BenchReportWriter;
import com.zhongbai233.bench.runtime.report.FmlEnvironment;
import com.zhongbai233.bench.runtime.report.JfrRecorder;
import com.zhongbai233.bench.runtime.report.SampleLogWriter;
import com.zhongbai233.bench.runtime.report.ScenarioArtifactWriter;
import com.zhongbai233.bench.runtime.report.ThreadDumpWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns one dedicated server benchmark run and executes registered scenarios sequentially. */
public final class ServerBenchEngine implements BenchScheduler, BenchCancellationToken, BenchMetricRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerBenchEngine.class);
    static final BenchMetricDescriptor TICK_DURATION =
            new BenchMetricDescriptor("server.tick.duration", "ns", MetricDirection.LOWER_IS_BETTER);

    private final MinecraftServer server;
    private final RuntimeConfiguration configuration;
    private final List<ScenarioRegistry.Registration> registrations;
    private final BenchReportWriter report;
    private final JvmMetricSampler jvmMetrics = new JvmMetricSampler();
    private final JfrRecorder jfr = new JfrRecorder();
    private ScenarioArtifactWriter artifacts;
    private ScenarioFilter filter = ScenarioFilter.parse("");
    private int executedScenarios;
    private final ScenarioMetricCollector metrics = new ScenarioMetricCollector();
    private ServerScenarioRunner runner;
    private int scenarioIndex;
    private long currentTick;
    private long tickDurationTotalNanos;
    private long tickDurationSamples;
    private boolean complete;
    private boolean cancelled;
    private String cancellationReason = "";
    private BenchStatus status = BenchStatus.PASSED;

    private ServerBenchEngine(MinecraftServer server, RuntimeConfiguration configuration,
                              List<ScenarioRegistry.Registration> registrations) {
        this.server = server;
        this.configuration = configuration;
        this.registrations = registrations;
        this.report = configuration.pairedServer()
            ? new BenchReportWriter(configuration.resultDirectory(), configuration.targetMod(),
                configuration.seed(), "paired-server", "PAIRED_SERVER")
            : new BenchReportWriter(configuration.resultDirectory(), configuration.targetMod(), configuration.seed());
    }

    public static ServerBenchEngine start(MinecraftServer server, RuntimeConfiguration configuration) throws IOException {
        ClassLoader gameClassLoader = ModBenchRuntimeMod.class.getClassLoader();
        List<BenchProvider> providers = ProviderDiscovery.discover(gameClassLoader, configuration.expectedProviderCount());
        ScenarioRegistry registry = new ScenarioRegistry();
        registry.registerProviders(providers);
        ServerBenchEngine engine = new ServerBenchEngine(server, configuration, registry.registrations());
        engine.artifacts = new ScenarioArtifactWriter(configuration.resultDirectory(), engine.report);
        engine.filter = ScenarioFilter.parse(configuration.scenarioFilter());
        if (engine.filter.isRestricted()) {
            engine.report.addDiagnostic("scenario.filter=" + configuration.scenarioFilter());
        }
        providers.forEach(engine.report::addProvider);
        engine.captureEnvironment();
        engine.report.writePartial();
        engine.startNextScenario();
        return engine;
    }

    private void captureEnvironment() {
        try {
            report.setEnvironment(FmlEnvironment.capture());
        } catch (Throwable exception) {
            report.addDiagnostic("environment.capture_failed=" + exception.getClass().getName());
        }
        report.addRunParameter("phaseTimeoutTicks", String.valueOf(configuration.phaseTimeoutTicks()));
        report.addRunParameter("expectedProviderCount", String.valueOf(configuration.expectedProviderCount()));
        report.addRunParameter("serverLevelType", configuration.serverLevelType());
        if (!configuration.serverGeneratorSettings().isBlank()) {
            report.addRunParameter("serverGeneratorSettings", configuration.serverGeneratorSettings());
        }
        report.addRunParameter("jfrEnabled", String.valueOf(configuration.jfrEnabled()));
        report.addRunParameter("participantMode", configuration.participantMode());
        if (!configuration.pairedSessionId().isBlank()) {
            report.addRunParameter("pairedSessionId", configuration.pairedSessionId());
        }
    }

    private void startScenarioJfrIfEnabled(String scenarioId) {
        if (!configuration.jfrEnabled()) return;
        if (!jfr.start("modbench:" + scenarioId)) {
            report.addDiagnostic("jfr.start_failed=" + scenarioId + ":flight_recorder_unavailable");
        }
    }

    /** Dumps the active scenario recording, including failed and timed-out scenarios. */
    private void dumpScenarioJfrIfRecording(String scenarioId) {
        if (!jfr.isRecording()) return;
        try {
            Path target = configuration.resultDirectory().resolve("artifacts").resolve("jfr")
                    .resolve(JfrRecorder.scenarioFileName(scenarioId));
            jfr.dump(target);
            String relative = configuration.resultDirectory().relativize(target).toString().replace('\\', '/');
            report.addArtifact("jfr", relative, "application/octet-stream");
            report.addDiagnostic("jfr.recording." + scenarioId + "=" + relative);
        } catch (Exception exception) {
            report.addDiagnostic("jfr.dump_failed." + scenarioId + "=" + exception.getClass().getName());
        }
    }

    public void tick() {
        currentTick++;
        if (complete || runner == null) {
            return;
        }
        runner.tick();
        if (runner.status() != null) {
            recordCompletedScenario();
            startNextScenario();
        }
    }

    private void recordCompletedScenario() {
        ScenarioRegistry.Registration registration = registrations.get(scenarioIndex);
        report.addScenario(registration.descriptor().id(), registration.providerId(), runner.status(),
                runner.failure(), ScenarioReportAssembler.phases(runner.phaseRecords()),
                ScenarioReportAssembler.metrics(metrics, null));
        dumpScenarioJfrIfRecording(registration.descriptor().id());
        writeSampleLog(registration.descriptor().id());
        metrics.reset();
        if (runner.status() == BenchStatus.TIMED_OUT) writeThreadDump(registration.descriptor().id());
        if (!cancelled && runner.status() != BenchStatus.PASSED) {
            status = BenchStatus.FAILED;
        }
        flushPartial();
        scenarioIndex++;
        runner = null;
    }

    public void recordServerTick(long durationNanos) {
        if (durationNanos >= 0) {
            tickDurationTotalNanos += durationNanos;
            tickDurationSamples++;
            if (runner != null && runner.status() == null && isTickDriven(runner.phase())) {
                metrics.record(TICK_DURATION, runner.phase(), durationNanos);
            }
        }
    }

    private static boolean isTickDriven(BenchPhase phase) {
        return phase == BenchPhase.STABILIZE || phase == BenchPhase.WARMUP || phase == BenchPhase.MEASURE;
    }

    /** Raw metric samples land next to the report so tooling can recompute distributions. */
    private void writeSampleLog(String scenarioId) {
        try {
            List<SampleLogWriter.MetricSamples> samples = ScenarioReportAssembler.samples(metrics);
            if (samples.isEmpty()) return;
            Path target = configuration.resultDirectory()
                    .resolve("artifacts").resolve("samples").resolve(scenarioId + ".jsonl");
            SampleLogWriter.write(target, samples);
            String relative = configuration.resultDirectory().relativize(target).toString().replace('\\', '/');
            report.addArtifact("samples", relative, "application/x-ndjson");
        } catch (Exception exception) {
            report.addDiagnostic("samples.write_failed=" + exception.getClass().getName());
        }
    }

    /** A timed-out scenario captures every thread so the hang can be diagnosed offline. */
    private void writeThreadDump(String scenarioId) {
        try {
            Path target = configuration.resultDirectory()
                    .resolve("artifacts").resolve("thread-dumps").resolve(scenarioId + ".txt");
            ThreadDumpWriter.write(target);
            String relative = configuration.resultDirectory().relativize(target).toString().replace('\\', '/');
            report.addArtifact("thread-dump", relative, "text/plain");
            report.addDiagnostic("server.thread_dump=" + relative);
        } catch (Exception exception) {
            report.addDiagnostic("server.thread_dump.failed=" + exception.getClass().getName());
        }
    }

    public boolean isComplete() {
        return complete;
    }

    public BenchStatus status() {
        return status;
    }

    public void abort(String reason) {
        if (complete) return;
        cancelled = true;
        cancellationReason = reason;
        status = BenchStatus.ABORTED;
        report.addDiagnostic(reason);
        if (runner != null && runner.status() == null) {
            // Drive cancellation synchronously while the server thread is still available so
            // the active scenario receives its teardown exactly once before finalization.
            runner.tick();
            if (runner.status() != null) recordCompletedScenario();
        }
        finish();
    }

    private void startNextScenario() {
        while (scenarioIndex < registrations.size()
                && !filter.matches(registrations.get(scenarioIndex).descriptor().id())) {
            ScenarioRegistry.Registration skipped = registrations.get(scenarioIndex);
            report.addScenario(skipped.descriptor().id(), skipped.providerId(), BenchStatus.SKIPPED, null);
            report.addDiagnostic("scenario.skipped_by_filter=" + skipped.descriptor().id());
            scenarioIndex++;
        }
        if (scenarioIndex >= registrations.size()) {
            finish();
            return;
        }
        ScenarioRegistry.Registration registration = registrations.get(scenarioIndex);
        String scenarioId = registration.descriptor().id();
        try {
            LOGGER.info("MODBENCH event=phase_start scenario={} phase=setup", scenarioId);
            startScenarioJfrIfEnabled(scenarioId);
            var context = new BenchServerContextAdapter(
                    server, server.overworld(), this, this, artifacts, this,
                    configuration.resultDirectory(), configuration.seed());
            executedScenarios++;
            runner = new ServerScenarioRunner(context, registration.factory().create(context),
                    ScenarioTimeouts.effectivePhaseTicks(
                            configuration.phaseTimeoutTicks(), registration.descriptor().phaseTimeout()));
            runner.start();
        } catch (Exception exception) {
            dumpScenarioJfrIfRecording(scenarioId);
            report.addScenario(scenarioId, registration.providerId(), BenchStatus.FAILED,
                    exception.getClass().getName() + ": " + exception.getMessage());
            metrics.reset();
            status = BenchStatus.FAILED;
            scenarioIndex++;
            flushPartial();
            startNextScenario();
        }
    }

    private void finish() {
        if (complete) return;
        applyFilterVerdict();
        if (jfr.isRecording()) dumpScenarioJfrIfRecording("unfinished-run");
        var snapshot = jvmMetrics.snapshot();
        long tickMean = tickDurationSamples == 0 ? 0 : tickDurationTotalNanos / tickDurationSamples;
        report.addDiagnostic("server.tick.duration.mean_ns=" + tickMean);
        report.addDiagnostic("jvm.heap.used_bytes=" + snapshot.heapUsedBytes());
        report.addDiagnostic("jvm.gc.count=" + snapshot.gcCount());
        try {
            report.writeFinal(status);
        } catch (IOException exception) {
            status = BenchStatus.FAILED;
            LOGGER.error("MODBENCH event=run_failed phase=finalize", exception);
        }
        complete = true;
    }

    /** A restricted filter that ran nothing must fail loudly instead of passing an empty run. */
    private void applyFilterVerdict() {
        if (!filter.isRestricted() || executedScenarios > 0 || registrations.isEmpty()) return;
        if (status == BenchStatus.PASSED) status = BenchStatus.FAILED;
        report.addDiagnostic("scenario.filter.matched_nothing=" + configuration.scenarioFilter());
        registrations.forEach(registration ->
                report.addDiagnostic("scenario.available=" + registration.descriptor().id()));
    }

    private void flushPartial() {
        try {
            report.writePartial();
        } catch (IOException exception) {
            status = BenchStatus.FAILED;
            LOGGER.error("MODBENCH event=run_failed phase=partial_flush", exception);
        }
    }

    @Override public void execute(Runnable action) { server.execute(action); }
    @Override public long currentTick() { return currentTick; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public String reason() { return cancellationReason; }
    @Override public void record(BenchMetricDescriptor descriptor, long value) {
        record(descriptor, (double) value);
    }
    @Override public void record(BenchMetricDescriptor descriptor, double value) {
        if (runner != null && runner.status() == null) {
            metrics.record(descriptor, runner.phase(), value);
        } else {
            report.addDiagnostic(descriptor.name() + "=" + value + descriptor.unit());
        }
    }
}