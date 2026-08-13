package com.zhongbai233.bench.runtime.client;

import com.zhongbai233.bench.api.BenchMetricDescriptor;
import com.zhongbai233.bench.api.BenchPhase;
import com.zhongbai233.bench.api.BenchProvider;
import com.zhongbai233.bench.api.BenchStatus;
import com.zhongbai233.bench.api.MetricDirection;
import com.zhongbai233.bench.api.discovery.ProviderDiscovery;
import com.zhongbai233.bench.api.neoforge.client.BenchCaptureOptions;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContextAdapter;
import com.zhongbai233.bench.api.neoforge.client.BenchClientReadiness;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScheduler;
import com.zhongbai233.bench.api.neoforge.server.BenchCancellationToken;
import com.zhongbai233.bench.api.neoforge.server.BenchMetricRecorder;
import com.zhongbai233.bench.runtime.ModBenchRuntimeMod;
import com.zhongbai233.bench.runtime.RuntimeConfiguration;
import com.zhongbai233.bench.runtime.ScenarioFilter;
import com.zhongbai233.bench.runtime.ScenarioReportAssembler;
import com.zhongbai233.bench.runtime.ScenarioTimeouts;
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
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns one integrated-client benchmark after a client world and player are ready. */
public final class ClientBenchEngine implements BenchClientScheduler, BenchCancellationToken, BenchMetricRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientBenchEngine.class);
    private static final int DEFAULT_FRAME_CAPACITY = 1_000_000;
    /** Client ticks a scenario boundary waits for outstanding screenshots before giving up. */
    private static final int CAPTURE_DRAIN_TICKS = 200;
    static final BenchMetricDescriptor FRAME_INTERVAL =
            new BenchMetricDescriptor("client.frame.interval", "ns", MetricDirection.LOWER_IS_BETTER);

    private final Minecraft minecraft;
    private final RuntimeConfiguration configuration;
    private final List<ClientScenarioRegistry.Registration> registrations;
    private final BenchReportWriter report;
    private final FrameIntervalSampler frames = new FrameIntervalSampler(DEFAULT_FRAME_CAPACITY);
    private final ClientEnvironmentGuard environment;
    private final ClientAutomationController automation;
    private final JvmMetricSampler jvmMetrics = new JvmMetricSampler();
    private final JfrRecorder jfr = new JfrRecorder();
    private ScenarioArtifactWriter artifacts;
    private ScenarioFilter filter = ScenarioFilter.parse("");
    private int executedScenarios;
    private final ScenarioMetricCollector metrics = new ScenarioMetricCollector();
    private long previousFrameNanos = -1;
    private ClientScenarioRunner runner;
    private int scenarioIndex;
    private long currentTick;
    private boolean complete;
    private boolean cancelled;
    private boolean awaitingCaptureDrain;
    private int drainTicks;
    private boolean drainTimeoutReported;
    private String cancellationReason = "";
    private BenchStatus status = BenchStatus.PASSED;

    private ClientBenchEngine(Minecraft minecraft, RuntimeConfiguration configuration,
                              List<ClientScenarioRegistry.Registration> registrations) {
        this.minecraft = minecraft;
        this.configuration = configuration;
        this.registrations = registrations;
        report = new BenchReportWriter(configuration.resultDirectory(), configuration.targetMod(),
            configuration.seed(), configuration.remoteClient() ? configuration.participantRunType() : "client",
            configuration.remoteClient() ? "REMOTE_CLIENT" : "INTEGRATED_CLIENT");
        environment = new ClientEnvironmentGuard(minecraft, configuration.clientRequireWindowFocus(),
                configuration.clientStableFrameRatio());
        automation = new ClientAutomationController(minecraft, configuration.resultDirectory(), environment,
                configuration.clientCaptureGateFrameBudget());
    }

    public static ClientBenchEngine start(Minecraft minecraft, RuntimeConfiguration configuration) throws IOException {
        if (minecraft.level == null || minecraft.player == null) {
            throw new IllegalStateException("Client world and player must be ready before starting a benchmark");
        }
        List<BenchProvider> providers = ProviderDiscovery.discover(
                ModBenchRuntimeMod.class.getClassLoader(), configuration.expectedProviderCount());
        ClientScenarioRegistry registry = new ClientScenarioRegistry();
        registry.registerProviders(providers);
        ClientBenchEngine engine = new ClientBenchEngine(minecraft, configuration, registry.registrations());
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

    private void captureEnvironment() {
        try {
            report.setEnvironment(FmlEnvironment.capture());
        } catch (Throwable exception) {
            report.addDiagnostic("environment.capture_failed=" + exception.getClass().getName());
        }
        report.addRunParameter("phaseTimeoutTicks", String.valueOf(configuration.phaseTimeoutTicks()));
        report.addRunParameter("expectedProviderCount", String.valueOf(configuration.expectedProviderCount()));
        report.addRunParameter("clientWindowWidth", String.valueOf(configuration.clientWindowWidth()));
        report.addRunParameter("clientWindowHeight", String.valueOf(configuration.clientWindowHeight()));
        report.addRunParameter("clientVsync", String.valueOf(configuration.clientVsync()));
        report.addRunParameter("clientFpsLimit", String.valueOf(configuration.clientFpsLimit()));
        report.addRunParameter("clientRenderDistance", String.valueOf(configuration.clientRenderDistance()));
        report.addRunParameter("clientSimulationDistance", String.valueOf(configuration.clientSimulationDistance()));
        report.addRunParameter("clientRequireWindowFocus", String.valueOf(configuration.clientRequireWindowFocus()));
        report.addRunParameter("clientStableFrameRatio", String.valueOf(configuration.clientStableFrameRatio()));
        report.addRunParameter("clientCaptureGateFrameBudget",
                String.valueOf(configuration.clientCaptureGateFrameBudget()));
        report.addRunParameter("clientWorldPreset", configuration.clientWorldPreset());
        report.addRunParameter("clientDimension", configuration.clientDimension());
        report.addRunParameter("jfrEnabled", String.valueOf(configuration.jfrEnabled()));
        report.addRunParameter("participantMode", configuration.participantMode());
        if (!configuration.pairedSessionId().isBlank()) {
            report.addRunParameter("pairedSessionId", configuration.pairedSessionId());
            report.addRunParameter("pairedClientIndex", String.valueOf(configuration.pairedClientIndex()));
            report.addRunParameter("pairedClientCount", String.valueOf(configuration.pairedClientCount()));
        }
        if (minecraft.level != null) {
            report.addDiagnostic("client.world.dimension=" + minecraft.level.dimension().identifier());
        }
    }

    public void tick() {
        collectArtifacts();
        currentTick++;
        if (complete) return;
        if (cancelled) {
            if (runner != null && runner.status() == null) {
                runner.tick();
                if (runner.status() != null) completeScenario();
            } else if (awaitingCaptureDrain) {
                drainCaptures();
            } else {
                finish();
            }
            return;
        }
        automation.maintainHeldPose();
        environment.sample();
        if (awaitingCaptureDrain) {
            drainCaptures();
            return;
        }
        if (runner == null) return;
        runner.tick();
        if (runner.status() != null) completeScenario();
    }

    public void recordFrame(long timestampNanos) {
        frames.recordFrame(timestampNanos);
        environment.recordFrame(timestampNanos);
        if (previousFrameNanos >= 0 && runner != null && runner.status() == null
                && isTickDriven(runner.phase())) {
            long interval = timestampNanos - previousFrameNanos;
            if (interval > 0) metrics.record(FRAME_INTERVAL, runner.phase(), interval);
        }
        previousFrameNanos = timestampNanos;
    }

    private static boolean isTickDriven(BenchPhase phase) {
        return phase == BenchPhase.STABILIZE || phase == BenchPhase.WARMUP || phase == BenchPhase.MEASURE;
    }

    public void capturePendingScreenshot() { automation.capturePendingScreenshot(); }
    public boolean hasPendingScreenshots() { return automation.hasPendingScreenshots(); }
    public boolean isComplete() { return complete; }
    public BenchStatus status() { return status; }

    void beginExpectedReconnect() {
        environment.beginExpectedReconnect();
    }

    public void recordGraphicsSnapshot(String stage, ClientGraphicsController.Snapshot snapshot) {
        report.addDiagnostic("client.graphics." + stage + ".window=" + snapshot.width() + "x" + snapshot.height());
        report.addDiagnostic("client.graphics." + stage + ".fullscreen=" + snapshot.fullscreen());
        report.addDiagnostic("client.graphics." + stage + ".active=" + snapshot.active());
        report.addDiagnostic("client.graphics." + stage + ".paused=" + snapshot.paused());
        report.addDiagnostic("client.graphics." + stage + ".screen_open=" + snapshot.screenOpen());
        report.addDiagnostic("client.graphics." + stage + ".vsync=" + snapshot.vsync());
        report.addDiagnostic("client.graphics." + stage + ".fps_limit=" + snapshot.fpsLimit());
        report.addDiagnostic("client.graphics." + stage + ".render_distance=" + snapshot.renderDistance());
        report.addDiagnostic("client.graphics." + stage + ".simulation_distance=" + snapshot.simulationDistance());
        report.addDiagnostic("client.graphics." + stage + ".inactivity_fps_limit=" + snapshot.inactivityFpsLimit());
        report.addDiagnostic("client.graphics." + stage + ".mouse_grabbed=" + snapshot.mouseGrabbed());
    }

    public void abort(String reason) {
        if (cancelled || complete) return;
        cancelled = true;
        cancellationReason = reason;
        status = BenchStatus.ABORTED;
        report.addDiagnostic(reason);
    }

    private void completeScenario() {
        ClientScenarioRegistry.Registration registration = registrations.get(scenarioIndex);
        BenchStatus scenarioStatus = runner.status();
        report.addScenario(registration.descriptor().id(), registration.providerId(), scenarioStatus,
                runner.failure(), ScenarioReportAssembler.phases(runner.phaseRecords()),
                ScenarioReportAssembler.metrics(metrics, FRAME_INTERVAL.name()));
        dumpScenarioJfrIfRecording(registration.descriptor().id());
        writeSampleLog(registration.descriptor().id());
        metrics.reset();
        if (scenarioStatus == BenchStatus.TIMED_OUT) writeThreadDump(registration.descriptor().id());
        if (scenarioStatus != BenchStatus.PASSED && !cancelled) {
            status = BenchStatus.FAILED;
            captureFailureFrame(registration.descriptor().id());
        }
        flushPartial();
        runner = null;
        scenarioIndex++;
        awaitingCaptureDrain = true;
        drainTicks = 0;
        drainTimeoutReported = false;
    }

    /** Keeps the client ticking until outstanding screenshots have been written to disk. */
    private void drainCaptures() {
        if (automation.hasPendingScreenshots()) {
            drainTicks++;
            if (drainTicks < CAPTURE_DRAIN_TICKS) return;
            if (!drainTimeoutReported) {
                drainTimeoutReported = true;
                report.addDiagnostic("client.screenshot.drain_timeout_ticks=" + drainTicks);
                automation.failPending(new IllegalStateException(
                        "Screenshot capture did not finish within the drain budget"));
                status = BenchStatus.FAILED;
            }
            if (automation.hasPendingScreenshots()) return;
        }
        awaitingCaptureDrain = false;
        drainTicks = 0;
        drainTimeoutReported = false;
        collectArtifacts();
        if (cancelled) finish();
        else startNextScenario();
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
            report.addDiagnostic("client.thread_dump=" + relative);
        } catch (Exception exception) {
            report.addDiagnostic("client.thread_dump.failed=" + exception.getClass().getName());
        }
    }

    private void captureFailureFrame(String scenarioId) {
        try {
            automation.captureScreenshot("failure-" + scenarioId.replace('.', '-'), BenchCaptureOptions.immediate());
            report.addDiagnostic("client.failure_screenshot.scenario=" + scenarioId);
        } catch (Exception exception) {
            report.addDiagnostic("client.failure_screenshot.failed=" + exception.getMessage());
        }
    }

    private void startNextScenario() {
        // Keep a held viewpoint through screenshot draining, then release it before the next setup.
        automation.releaseHeldPose();
        automation.releaseGuiSession();
        while (scenarioIndex < registrations.size()
                && !filter.matches(registrations.get(scenarioIndex).descriptor().id())) {
            ClientScenarioRegistry.Registration skipped = registrations.get(scenarioIndex);
            report.addScenario(skipped.descriptor().id(), skipped.providerId(), BenchStatus.SKIPPED, null);
            report.addDiagnostic("scenario.skipped_by_filter=" + skipped.descriptor().id());
            scenarioIndex++;
        }
        if (scenarioIndex >= registrations.size()) {
            finish();
            return;
        }
        ClientScenarioRegistry.Registration registration = registrations.get(scenarioIndex);
        String scenarioId = registration.descriptor().id();
        try {
            LOGGER.info("MODBENCH event=phase_start scenario={} phase=setup", scenarioId);
            startScenarioJfrIfEnabled(scenarioId);
            if (minecraft.level == null || minecraft.player == null) {
                throw new IllegalStateException("Client left the world before scenario setup");
            }
            var context = new BenchClientContextAdapter(
                    minecraft, minecraft.level, minecraft.player, this, this, frames, automation, artifacts,
                    environment, this, configuration.resultDirectory(), configuration.seed());
            executedScenarios++;
            runner = new ClientScenarioRunner(context, registration.factory().create(context),
                    ScenarioTimeouts.effectivePhaseTicks(
                            configuration.phaseTimeoutTicks(), registration.descriptor().phaseTimeout()));
            runner.start();
        } catch (Exception | AssertionError exception) {
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
        automation.releaseHeldPose();
        automation.releaseGuiSession();
        applyFilterVerdict();
        if (jfr.isRecording()) dumpScenarioJfrIfRecording("unfinished-run");
        collectArtifacts();
        automation.failPending(new IllegalStateException("Benchmark completed before screenshot capture"));
        var snapshot = jvmMetrics.snapshot();
        report.addDiagnostic("client.frame.interval.samples=" + frames.sampleCount());
        report.addDiagnostic("client.frame.interval.dropped=" + frames.droppedSampleCount());
        report.addDiagnostic("client.frame.interval.mean_ns=" + frames.meanIntervalNanos());
        report.addDiagnostic("client.frame.interval.p95_ns=" + frames.percentileIntervalNanos(95));
        report.addDiagnostic("client.frame.interval.p99_ns=" + frames.percentileIntervalNanos(99));
        report.addDiagnostic("client.frame.interval.max_ns=" + frames.maxIntervalNanos());
        report.addDiagnostic("jvm.heap.used_bytes=" + snapshot.heapUsedBytes());
        report.addDiagnostic("jvm.gc.count=" + snapshot.gcCount());
        recordEnvironmentDiagnostics();
        applyEnvironmentVerdict();
        try {
            report.writeFinal(status);
        } catch (IOException exception) {
            status = BenchStatus.FAILED;
            LOGGER.error("MODBENCH event=run_failed phase=finalize", exception);
        }
        complete = true;
    }

    private void recordEnvironmentDiagnostics() {
        BenchClientReadiness readiness = environment.readiness();
        report.addDiagnostic("client.environment.require_window_focus=" + environment.requiresWindowFocus());
        report.addDiagnostic("client.environment.render_ready_reached=" + environment.isArmed());
        report.addDiagnostic("client.environment.ticks_before_render_ready=" + environment.ticksBeforeArmed());
        report.addDiagnostic("client.environment.expected_reconnects=" + environment.expectedReconnectCount());
        report.addDiagnostic("client.environment.valid=" + environment.isValid());
        environment.invalidations().forEach(reason -> report.addDiagnostic("client.environment.invalidation=" + reason));
        report.addDiagnostic("client.readiness.ready=" + readiness.ready());
        report.addDiagnostic("client.readiness.pending_reason=" + readiness.pendingReason());
        report.addDiagnostic("client.readiness.loaded_chunks=" + readiness.loadedChunks());
        report.addDiagnostic("client.readiness.rendered_sections=" + readiness.renderedSections());
        report.addDiagnostic("client.readiness.total_sections=" + readiness.totalSections());
    }

    /** A restricted filter that ran nothing must fail loudly instead of passing an empty run. */
    private void applyFilterVerdict() {
        if (!filter.isRestricted() || executedScenarios > 0 || registrations.isEmpty()) return;
        if (status == BenchStatus.PASSED) status = BenchStatus.FAILED;
        report.addDiagnostic("scenario.filter.matched_nothing=" + configuration.scenarioFilter());
        registrations.forEach(registration ->
                report.addDiagnostic("scenario.available=" + registration.descriptor().id()));
    }

    /** A degraded environment must never be reported as a comparable, successful measurement. */
    private void applyEnvironmentVerdict() {
        if (status != BenchStatus.PASSED) return;
        if (!environment.isArmed()) {
            report.addDiagnostic("client.environment.verdict=render_pipeline_never_ready");
            status = BenchStatus.INCONCLUSIVE;
        } else if (!environment.isValid()) {
            report.addDiagnostic("client.environment.verdict=invalidated");
            status = BenchStatus.INCONCLUSIVE;
        }
    }

    private void collectArtifacts() {
        ClientAutomationController.CapturedArtifact artifact;
        while ((artifact = automation.pollCompletedArtifact()) != null) {
            String relative = configuration.resultDirectory().relativize(artifact.path()).toString().replace('\\', '/');
            report.addArtifact(artifact.type(), relative, "image/png", artifact.sha256(), artifact.bytes());
            report.addDiagnostic("client.capture=" + relative);
            report.addDiagnostic("client.capture.type=" + artifact.type());
            report.addDiagnostic("client.screenshot.sha256=" + artifact.sha256());
            report.addDiagnostic("client.screenshot.gate_satisfied=" + artifact.gateSatisfied());
            report.addDiagnostic("client.screenshot.waited_frames=" + artifact.waitedFrames());
            report.addDiagnostic("client.screenshot.hud_hidden=" + artifact.hudHidden());
            if (!artifact.detail().isBlank()) report.addDiagnostic("client.capture.detail=" + artifact.detail());
            if (!artifact.gateSatisfied()) {
                environment.invalidate("client.screenshot.capture_gate_timeout=" + relative);
            }
        }
    }

    private void flushPartial() {
        try {
            report.writePartial();
        } catch (IOException exception) {
            status = BenchStatus.FAILED;
            LOGGER.error("MODBENCH event=run_failed phase=partial_flush", exception);
        }
    }

    @Override public void execute(Runnable action) { minecraft.execute(action); }
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
