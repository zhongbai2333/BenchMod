package com.zhongbai233.bench.runtime.client;

import com.zhongbai233.bench.api.client.BenchCameraFramer;
import com.zhongbai233.bench.api.client.BenchCameraFraming;
import com.zhongbai233.bench.api.client.gui.BenchScreenSnapshot;
import com.zhongbai233.bench.api.client.gui.BenchGuiCaptureOptions;
import com.zhongbai233.bench.api.client.gui.BenchGuiSelector;
import com.zhongbai233.bench.api.client.gui.BenchGuiSelectors;
import com.zhongbai233.bench.api.neoforge.client.BenchCameraPath;
import com.zhongbai233.bench.api.neoforge.client.BenchCameraPlayback;
import com.zhongbai233.bench.api.neoforge.client.BenchCaptureOptions;
import com.zhongbai233.bench.api.neoforge.client.BenchClientAutomation;
import com.zhongbai233.bench.api.neoforge.client.BenchClientPose;
import com.zhongbai233.bench.api.neoforge.client.BenchGuiSession;
import com.zhongbai233.bench.api.neoforge.client.BenchPoseHold;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/** Implements deterministic local-player control and render-thread framebuffer capture. */
final class ClientAutomationController implements BenchClientAutomation {
    private final Minecraft minecraft;
    private final Path resultDirectory;
    private final ClientEnvironmentGuard environment;
    private final ClientGuiSnapshotter guiSnapshotter;
    /** Frames a gated capture may wait before it is taken anyway and reported as ungated. */
    private final int captureGateFrameBudget;
    private final ConcurrentLinkedQueue<ScreenshotRequest> pendingScreenshots = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<GuiCaptureRequest> pendingGuiCaptures = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CapturedArtifact> completedArtifacts = new ConcurrentLinkedQueue<>();
    private final AtomicInteger writesInFlight = new AtomicInteger();
    private boolean hudHiddenForCapture;
    private boolean hudHiddenBeforeCapture;
    private boolean preferGuiCapture;
    private long holdGeneration;
    private HeldPose activeHold;
    private long guiGeneration;
    private ActiveGuiSession activeGuiSession;

    ClientAutomationController(Minecraft minecraft, Path resultDirectory, ClientEnvironmentGuard environment,
                               int captureGateFrameBudget) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.resultDirectory = Objects.requireNonNull(resultDirectory, "resultDirectory");
        this.environment = Objects.requireNonNull(environment, "environment");
        guiSnapshotter = new ClientGuiSnapshotter(minecraft);
        if (captureGateFrameBudget < 1) throw new IllegalArgumentException("captureGateFrameBudget must be positive");
        this.captureGateFrameBudget = captureGateFrameBudget;
    }

    @Override
    public void setPose(BenchClientPose pose) {
        claimCamera();
        applyPose(pose, false);
    }

    @Override
    public void movePose(BenchClientPose pose) {
        claimCamera();
        applyPose(pose, true);
    }

    @Override
    public void lookAt(double x, double y, double z) {
        claimCamera();
        LocalPlayer player = requirePlayer();
        double dx = x - player.getX();
        double dy = y - player.getEyeY();
        double dz = z - player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        player.absSnapRotationTo(yaw, clampPitch(pitch));
    }

    @Override
    public void stopMovement() {
        LocalPlayer player = requirePlayer();
        player.input = new ClientInput();
        player.setDeltaMovement(Vec3.ZERO);
        player.setSprinting(false);
    }

    @Override
    public BenchClientPose pose() {
        LocalPlayer player = requirePlayer();
        return new BenchClientPose(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    @Override
    public BenchClientPose frameTarget(BenchCameraFraming framing) {
        claimCamera();
        BenchClientPose pose = framedPose(framing);
        applyPose(pose, false);
        return pose;
    }

    @Override
    public BenchPoseHold holdPose(BenchClientPose pose) {
        Objects.requireNonNull(pose, "pose");
        long generation = claimCamera();
        HeldPose hold = new HeldPose(generation, pose);
        activeHold = hold;
        applyPose(pose, false);
        return hold;
    }

    @Override
    public BenchPoseHold holdFramedTarget(BenchCameraFraming framing) {
        return holdPose(framedPose(framing));
    }

    @Override
    public BenchCameraPlayback playPath(BenchCameraPath path) {
        return playPath(path, BenchCaptureOptions.defaults());
    }

    @Override
    public BenchCameraPlayback playPath(BenchCameraPath path, BenchCaptureOptions captureOptions) {
        long generation = claimCamera();
        CameraPathPlayer playback = new CameraPathPlayer(
            path, this::applyPose, captureOptions, this::captureScreenshot,
            () -> holdGeneration == generation);
        return playback;
    }

    @Override
    public CompletableFuture<Path> captureScreenshot(String name) {
        return captureScreenshot(name, BenchCaptureOptions.defaults());
    }

    @Override
    public CompletableFuture<Path> captureScreenshot(String name, BenchCaptureOptions options) {
        Objects.requireNonNull(options, "options");
        String filename = normalizeScreenshotName(name);
        CompletableFuture<Path> future = new CompletableFuture<>();
        pendingScreenshots.add(new ScreenshotRequest(filename, options, future));
        return future;
    }

    @Override
    public BenchGuiSession beginGuiSession(Class<? extends Screen> expectedScreen) {
        requireClientThread();
        Objects.requireNonNull(expectedScreen, "expectedScreen");
        if (minecraft.screen == null || !expectedScreen.isInstance(minecraft.screen)) {
            throw new IllegalStateException("Expected Screen " + expectedScreen.getName() + " is not currently open");
        }
        if (activeGuiSession != null) activeGuiSession.close();
        long generation = ++guiGeneration;
        long environmentGeneration = environment.beginExpectedScreen(expectedScreen);
        ActiveGuiSession session = new ActiveGuiSession(
                generation, environmentGeneration, expectedScreen, new IdentityHashMap<>());
        activeGuiSession = session;
        return session;
    }

    @Override
    public void setHudHidden(boolean hidden) {
        minecraft.options.hideGui = hidden;
    }

    void applyPose(BenchClientPose pose, boolean smooth) {
        Objects.requireNonNull(pose, "pose");
        LocalPlayer player = requirePlayer();
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        player.setDeltaMovement(Vec3.ZERO);
        float pitch = clampPitch(pose.pitch());
        if (smooth) {
            player.setPos(pose.x(), pose.y(), pose.z());
            player.setYRot(pose.yaw());
            player.setXRot(pitch);
        } else {
            player.absSnapTo(pose.x(), pose.y(), pose.z(), pose.yaw(), pitch);
        }
    }

    void maintainHeldPose() {
        if (activeHold != null) applyPose(activeHold.pose(), false);
    }

    void releaseHeldPose() {
        if (activeHold != null) activeHold.release();
    }

    void releaseGuiSession() {
        ActiveGuiSession session = activeGuiSession;
        if (session != null) session.close();
        else environment.clearExpectedScreen();
    }

    private long claimCamera() {
        activeHold = null;
        return ++holdGeneration;
    }

    private BenchClientPose framedPose(BenchCameraFraming framing) {
        Objects.requireNonNull(framing, "framing");
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width < 1 || height < 1) throw new IllegalStateException("Client viewport is unavailable");
        double verticalFov = minecraft.gameRenderer.getMainCamera().getFov();
        BenchCameraFramer.Solution solution = BenchCameraFramer.solve(
                framing, verticalFov, (double) width / height);
        LocalPlayer player = requirePlayer();
        double feetY = solution.eyeY() - player.getEyeHeight();
        return new BenchClientPose(solution.eyeX(), feetY, solution.eyeZ(), solution.yaw(), solution.pitch());
    }

    /**
     * Runs on the render thread after a completed frame. A gated request first waits for the render
     * pipeline and frame pacing, then spends one extra frame with the HUD hidden when asked.
     */
    void capturePendingScreenshot() {
        GuiCaptureRequest guiRequest = pendingGuiCaptures.peek();
        ScreenshotRequest screenshotRequest = pendingScreenshots.peek();
        if (guiRequest != null && (screenshotRequest == null || preferGuiCapture)) {
            preferGuiCapture = false;
            capturePendingGui(guiRequest);
            return;
        }
        ScreenshotRequest request = screenshotRequest;
        if (request == null) return;
        preferGuiCapture = true;
        boolean gateOpen = isGateOpen(request);
        boolean budgetExhausted = request.waitedFrames() >= captureGateFrameBudget;
        if (!gateOpen && !budgetExhausted) {
            request.awaitFrame();
            return;
        }
        if (request.options().hideHud() && !hudHiddenForCapture) {
            hudHiddenForCapture = true;
            hudHiddenBeforeCapture = minecraft.options.hideGui;
            minecraft.options.hideGui = true;
            return;
        }
        pendingScreenshots.poll();
        writeScreenshot(request, gateOpen, request.waitedFrames());
        if (hudHiddenForCapture) {
            hudHiddenForCapture = false;
            minecraft.options.hideGui = hudHiddenBeforeCapture;
        }
    }

    boolean hasPendingScreenshots() {
        return !pendingScreenshots.isEmpty() || !pendingGuiCaptures.isEmpty() || writesInFlight.get() > 0;
    }

    CapturedArtifact pollCompletedArtifact() {
        return completedArtifacts.poll();
    }

    void failPending(Throwable failure) {
        ScreenshotRequest request;
        while ((request = pendingScreenshots.poll()) != null) request.future().completeExceptionally(failure);
        GuiCaptureRequest guiRequest;
        while ((guiRequest = pendingGuiCaptures.poll()) != null) guiRequest.future().completeExceptionally(failure);
        if (hudHiddenForCapture) {
            hudHiddenForCapture = false;
            minecraft.options.hideGui = hudHiddenBeforeCapture;
        }
    }

    static String normalizeScreenshotName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Screenshot name must not be blank");
        String normalized = name.endsWith(".png") ? name : name + ".png";
        if (!Path.of(normalized).getFileName().toString().equals(normalized) || normalized.contains("..")) {
            throw new IllegalArgumentException("Screenshot name must be a plain filename");
        }
        return normalized;
    }

    private boolean isGateOpen(ScreenshotRequest request) {
        BenchCaptureOptions options = request.options();
        if (options.requireRenderReady() && !environment.readiness().ready()) return false;
        return environment.isFrameStable(options.stableFrames());
    }

    private void writeScreenshot(ScreenshotRequest request, boolean gateSatisfied, int waitedFrames) {
        Path directory = resultDirectory.resolve("artifacts").resolve("screenshots");
        Path target = directory.resolve(request.filename());
        writesInFlight.incrementAndGet();
        try {
            Files.createDirectories(directory);
            Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
                try (image) {
                    image.writeToFile(target);
                        completedArtifacts.add(new CapturedArtifact("screenshot", target, sha256(target), Files.size(target),
                            gateSatisfied, waitedFrames, request.options().hideHud(), ""));
                    request.future().complete(target);
                } catch (Exception exception) {
                    request.future().completeExceptionally(exception);
                } finally {
                    writesInFlight.decrementAndGet();
                }
            }));
        } catch (Exception exception) {
            writesInFlight.decrementAndGet();
            request.future().completeExceptionally(exception);
        }
    }

    private static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (Exception exception) {
            return "";
        }
    }

    private void capturePendingGui(GuiCaptureRequest request) {
        if (!environment.isFrameStable(request.options().stableFrames())) {
            request.awaitFrame();
            if (request.waitedFrames() < captureGateFrameBudget) return;
        }
        ActiveGuiSession session = request.session();
        try {
            session.ensureActive();
            BenchScreenSnapshot snapshot = session.snapshot();
            var node = BenchGuiSelectors.select(snapshot, request.selector()).requireMatch();
            GuiCoordinateMapper.Mapping mapping = GuiCoordinateMapper.map(node.bounds(), request.options().padding(),
                    snapshot.guiWidth(), snapshot.guiHeight(), snapshot.framebufferWidth(), snapshot.framebufferHeight());
            if (request.options().failOnClippedBounds() && mapping.clipped()) {
                throw new IllegalStateException("GUI capture bounds were clipped to the viewport: " + node.path());
            }
            pendingGuiCaptures.poll();
            writeGuiCapture(request, snapshot, node.path(), mapping,
                    request.waitedFrames() < captureGateFrameBudget, request.waitedFrames());
        } catch (RuntimeException exception) {
            pendingGuiCaptures.poll();
            request.future().completeExceptionally(exception);
        }
    }

    private void writeGuiCapture(GuiCaptureRequest request, BenchScreenSnapshot snapshot, String nodePath,
                                 GuiCoordinateMapper.Mapping mapping, boolean gateSatisfied, int waitedFrames) {
        Path directory = resultDirectory.resolve("artifacts").resolve("gui");
        Path target = directory.resolve(request.filename());
        writesInFlight.incrementAndGet();
        try {
            Files.createDirectories(directory);
            Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
                try (image; NativeImage cropped = crop(image, mapping.framebuffer())) {
                    cropped.writeToFile(target);
                    String detail = "screen=" + snapshot.screenClassName() + ";node=" + nodePath
                            + ";gui=" + mapping.clippedLogical() + ";framebuffer=" + mapping.framebuffer()
                            + ";clipped=" + mapping.clipped();
                    completedArtifacts.add(new CapturedArtifact("gui-widget-screenshot", target,
                            sha256(target), Files.size(target), gateSatisfied, waitedFrames, false, detail));
                    request.future().complete(target);
                } catch (Exception exception) {
                    request.future().completeExceptionally(exception);
                } finally {
                    writesInFlight.decrementAndGet();
                }
            }));
        } catch (Exception exception) {
            writesInFlight.decrementAndGet();
            request.future().completeExceptionally(exception);
        }
    }

    private static NativeImage crop(NativeImage source, com.zhongbai233.bench.api.client.gui.BenchGuiRectangle area) {
        NativeImage result = new NativeImage(area.width(), area.height(), false);
        boolean complete = false;
        try {
            for (int y = 0; y < area.height(); y++) {
                for (int x = 0; x < area.width(); x++) {
                    result.setPixel(x, y, source.getPixel(area.x() + x, area.y() + y));
                }
            }
            complete = true;
            return result;
        } finally {
            if (!complete) result.close();
        }
    }

    private LocalPlayer requirePlayer() {
        if (minecraft.player == null) throw new IllegalStateException("Client player is unavailable");
        return minecraft.player;
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90.0F, Math.min(90.0F, pitch));
    }

    private final class HeldPose implements BenchPoseHold {
        private final long generation;
        private final BenchClientPose pose;

        private HeldPose(long generation, BenchClientPose pose) {
            this.generation = generation;
            this.pose = pose;
        }

        @Override public BenchClientPose pose() { return pose; }
        @Override public boolean active() { return activeHold == this; }
        @Override public void release() {
            if (activeHold == this && holdGeneration == generation) activeHold = null;
        }
    }

    private final class ActiveGuiSession implements BenchGuiSession {
        private final long generation;
        private final long environmentGeneration;
        private final Class<? extends Screen> expectedScreen;
        private final Map<GuiEventListener, String> semanticNames;

        private ActiveGuiSession(long generation, long environmentGeneration,
                                 Class<? extends Screen> expectedScreen,
                                 Map<GuiEventListener, String> semanticNames) {
            this.generation = generation;
            this.environmentGeneration = environmentGeneration;
            this.expectedScreen = expectedScreen;
            this.semanticNames = semanticNames;
        }

        @Override
        public BenchGuiSession name(GuiEventListener listener, String semanticName) {
            requireClientThread();
            ensureActive();
            Objects.requireNonNull(listener, "listener");
            if (semanticName == null || semanticName.isBlank()) {
                throw new IllegalArgumentException("GUI semantic name must not be blank");
            }
            String normalized = semanticName.trim();
            if (semanticNames.containsValue(normalized)) {
                throw new IllegalArgumentException("GUI semantic name is already registered: " + normalized);
            }
            semanticNames.put(listener, normalized);
            return this;
        }

        @Override
        public BenchScreenSnapshot snapshot() {
            requireClientThread();
            ensureActive();
            return guiSnapshotter.snapshot(expectedScreen, listener -> semanticNames.getOrDefault(listener, ""));
        }

        @Override
        public CompletableFuture<Path> captureWidget(String name, BenchGuiSelector selector,
                                                     BenchGuiCaptureOptions options) {
            requireClientThread();
            ensureActive();
            Objects.requireNonNull(selector, "selector");
            Objects.requireNonNull(options, "options");
            String filename = normalizeScreenshotName(name);
            CompletableFuture<Path> future = new CompletableFuture<>();
            pendingGuiCaptures.add(new GuiCaptureRequest(filename, selector, options, this, future));
            return future;
        }

        @Override
        public boolean active() {
            return activeGuiSession == this && guiGeneration == generation;
        }

        @Override
        public void close() {
            if (!active()) return;
            failGuiRequests(this, new IllegalStateException("GUI debugging session was closed"));
            semanticNames.clear();
            activeGuiSession = null;
            environment.endExpectedScreen(environmentGeneration);
        }

        private void ensureActive() {
            if (!active()) throw new IllegalStateException("GUI debugging session is no longer active");
        }
    }

    private void failGuiRequests(ActiveGuiSession session, Throwable failure) {
        pendingGuiCaptures.removeIf(request -> {
            if (request.session() != session) return false;
            request.future().completeExceptionally(failure);
            return true;
        });
    }

    private void requireClientThread() {
        if (!minecraft.isSameThread()) {
            throw new IllegalStateException("GUI debugging APIs must be called from the Minecraft client thread");
        }
    }

    /**
     * A written screenshot plus the metadata the report needs to compare runs.
     *
     * @param gateSatisfied {@code false} when the readiness or stability gate never opened and the
     *                      frame was captured only because the wait budget ran out
     */
        record CapturedArtifact(
            String type, Path path, String sha256, long bytes, boolean gateSatisfied,
            int waitedFrames, boolean hudHidden, String detail) {}

    private static final class ScreenshotRequest {
        private final String filename;
        private final BenchCaptureOptions options;
        private final CompletableFuture<Path> future;
        private int waitedFrames;

        ScreenshotRequest(String filename, BenchCaptureOptions options, CompletableFuture<Path> future) {
            this.filename = filename;
            this.options = options;
            this.future = future;
        }

        String filename() { return filename; }
        BenchCaptureOptions options() { return options; }
        CompletableFuture<Path> future() { return future; }
        int waitedFrames() { return waitedFrames; }
        void awaitFrame() { waitedFrames++; }
    }

    private static final class GuiCaptureRequest {
        private final String filename;
        private final BenchGuiSelector selector;
        private final BenchGuiCaptureOptions options;
        private final ActiveGuiSession session;
        private final CompletableFuture<Path> future;
        private int waitedFrames;

        GuiCaptureRequest(String filename, BenchGuiSelector selector, BenchGuiCaptureOptions options,
                          ActiveGuiSession session, CompletableFuture<Path> future) {
            this.filename = filename;
            this.selector = selector;
            this.options = options;
            this.session = session;
            this.future = future;
        }

        String filename() { return filename; }
        BenchGuiSelector selector() { return selector; }
        BenchGuiCaptureOptions options() { return options; }
        ActiveGuiSession session() { return session; }
        CompletableFuture<Path> future() { return future; }
        int waitedFrames() { return waitedFrames; }
        void awaitFrame() { waitedFrames++; }
    }
}
