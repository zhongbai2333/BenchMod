package com.zhongbai233.bench.api.neoforge.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An immutable, deterministic keyframe timeline for the benchmark camera.
 *
 * <p>Sampling is a pure function of the elapsed client tick, so a path produces the same poses on
 * every machine and can be unit tested without a running game.
 */
public final class BenchCameraPath {
    /** Client ticks per second, the unit every keyframe duration is expressed in. */
    public static final int TICKS_PER_SECOND = 20;

    private final List<BenchCameraKeyframe> keyframes;
    private final BenchCameraPathMode mode;
    private final boolean smooth;
    private final int totalDurationTicks;

    private BenchCameraPath(List<BenchCameraKeyframe> keyframes, BenchCameraPathMode mode, boolean smooth) {
        this.keyframes = List.copyOf(keyframes);
        this.mode = mode;
        this.smooth = smooth;
        int total = 0;
        for (BenchCameraKeyframe keyframe : this.keyframes) total += keyframe.durationTicks();
        totalDurationTicks = total;
    }

    /** Starts a path at an absolute pose. The start pose is keyframe {@code 0} with duration {@code 0}. */
    public static Builder from(BenchClientPose start) {
        return new Builder(start);
    }

    public List<BenchCameraKeyframe> keyframes() {
        return keyframes;
    }

    public BenchCameraPathMode mode() {
        return mode;
    }

    /**
     * Returns {@code true} when the Runtime should let Minecraft interpolate between ticks, which
     * yields a continuous camera. A snapped path pins the camera to exactly one pose per tick.
     */
    public boolean smooth() {
        return smooth;
    }

    /** Total ticks of a single forward pass over the timeline. */
    public int totalDurationTicks() {
        return totalDurationTicks;
    }

    public boolean isFinished(long elapsedTicks) {
        return mode == BenchCameraPathMode.ONCE && elapsedTicks >= totalDurationTicks;
    }

    /**
     * Maps an elapsed tick count onto a position inside a single forward pass, honouring the loop
     * mode. The result is always within {@code [0, totalDurationTicks]}.
     */
    public double timelinePosition(double elapsedTicks) {
        double elapsed = Math.max(0.0, elapsedTicks);
        if (totalDurationTicks == 0) return 0.0;
        return switch (mode) {
            case ONCE -> Math.min(elapsed, totalDurationTicks);
            case LOOP -> elapsed % totalDurationTicks;
            case PING_PONG -> {
                double cycle = 2.0 * totalDurationTicks;
                double position = elapsed % cycle;
                yield position <= totalDurationTicks ? position : cycle - position;
            }
        };
    }

    /** Samples the pose at an elapsed tick offset. Pure and side-effect free. */
    public BenchClientPose sampleAt(double elapsedTicks) {
        double position = timelinePosition(elapsedTicks);
        double consumed = 0.0;
        for (int i = 1; i < keyframes.size(); i++) {
            BenchCameraKeyframe keyframe = keyframes.get(i);
            double next = consumed + keyframe.durationTicks();
            if (position <= next || i == keyframes.size() - 1) {
                double progress = keyframe.durationTicks() == 0 ? 1.0 : (position - consumed) / keyframe.durationTicks();
                return interpolate(keyframes.get(i - 1).pose(), keyframe.pose(), keyframe.easing().apply(progress));
            }
            consumed = next;
        }
        return keyframes.get(0).pose();
    }

    /**
     * Returns the keyframe whose timeline offset is exactly {@code timelineTicks}, or {@code null}.
     * Keyframe offsets are strictly increasing, so at most one keyframe can match.
     */
    public BenchCameraKeyframe keyframeAt(long timelineTicks) {
        long consumed = 0;
        for (BenchCameraKeyframe keyframe : keyframes) {
            consumed += keyframe.durationTicks();
            if (consumed == timelineTicks) return keyframe;
            if (consumed > timelineTicks) return null;
        }
        return null;
    }

    static BenchClientPose interpolate(BenchClientPose from, BenchClientPose to, double t) {
        return new BenchClientPose(
                from.x() + (to.x() - from.x()) * t,
                from.y() + (to.y() - from.y()) * t,
                from.z() + (to.z() - from.z()) * t,
                (float) (from.yaw() + wrapDegrees(to.yaw() - from.yaw()) * t),
                (float) (from.pitch() + (to.pitch() - from.pitch()) * t));
    }

    /** Normalizes a degree delta into {@code [-180, 180)} so rotations take the shortest arc. */
    static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    static double distance(BenchClientPose from, BenchClientPose to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Fluent builder producing an immutable path. */
    public static final class Builder {
        private final List<BenchCameraKeyframe> keyframes = new ArrayList<>();
        private BenchCameraPathMode mode = BenchCameraPathMode.ONCE;
        private boolean smooth = true;

        private Builder(BenchClientPose start) {
            keyframes.add(new BenchCameraKeyframe(Objects.requireNonNull(start, "start"), 0, BenchEasing.LINEAR, null));
        }

        public Builder to(BenchClientPose pose, int durationTicks) {
            return to(pose, durationTicks, BenchEasing.LINEAR);
        }

        public Builder to(BenchClientPose pose, int durationTicks, BenchEasing easing) {
            if (durationTicks < 1) throw new IllegalArgumentException("durationTicks must be positive");
            keyframes.add(new BenchCameraKeyframe(pose, durationTicks, easing, null));
            return this;
        }

        /** Adds a keyframe whose duration comes from a constant camera speed in blocks per second. */
        public Builder toAtSpeed(BenchClientPose pose, double blocksPerSecond) {
            return toAtSpeed(pose, blocksPerSecond, BenchEasing.LINEAR);
        }

        public Builder toAtSpeed(BenchClientPose pose, double blocksPerSecond, BenchEasing easing) {
            if (!(blocksPerSecond > 0.0)) throw new IllegalArgumentException("blocksPerSecond must be positive");
            Objects.requireNonNull(pose, "pose");
            double travel = distance(last().pose(), pose);
            int ticks = (int) Math.max(1, Math.ceil(travel / blocksPerSecond * TICKS_PER_SECOND));
            return to(pose, ticks, easing);
        }

        /** Holds the previous pose, which lets the picture settle before a capture. */
        public Builder hold(int durationTicks) {
            return to(last().pose(), durationTicks, BenchEasing.LINEAR);
        }

        /** Requests a screenshot when the most recently added keyframe is reached. */
        public Builder capture(String name) {
            BenchCameraKeyframe keyframe = last();
            keyframes.set(keyframes.size() - 1,
                    new BenchCameraKeyframe(keyframe.pose(), keyframe.durationTicks(), keyframe.easing(), name));
            return this;
        }

        public Builder mode(BenchCameraPathMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        /** Pins the camera to one pose per tick instead of letting Minecraft interpolate frames. */
        public Builder snapped() {
            smooth = false;
            return this;
        }

        public BenchCameraPath build() {
            if (keyframes.size() < 2) {
                throw new IllegalStateException("A camera path needs at least one destination keyframe");
            }
            return new BenchCameraPath(keyframes, mode, smooth);
        }

        private BenchCameraKeyframe last() {
            return keyframes.get(keyframes.size() - 1);
        }
    }
}
