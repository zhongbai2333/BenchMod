package com.zhongbai233.bench.runtime.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.zhongbai233.bench.api.graphics.GraphicsMigrationFixtures;
import com.zhongbai233.bench.api.graphics.GraphicsMigrationScene;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

/** Actual Renderpearl probes. No OpenGL handles, global render-target overrides, or CPU substitutes. */
public final class GraphicsMigrationGpu {
    private GraphicsMigrationGpu() {}

    public static Map<String, String> device() {
        var info = RenderSystem.getDevice().getDeviceInfo();
        return Map.of("actualBackend", info.backendName().toLowerCase(java.util.Locale.ROOT),
                "rawBackend", info.backendName(), "deviceName", info.name(),
                "vendor", info.vendorName(), "driver", info.driverInfo());
    }

    public static Probe start(GraphicsMigrationScene scene, long seed) {
        RenderSystem.assertOnRenderThread();
        return new ActualProbe(scene, seed);
    }

    /** Poll between frames; read only after ready. Closure never frees an in-flight readback. */
    public interface Probe extends AutoCloseable {
        boolean ready();
        byte[] read();
        Map<String, String> checks();
        @Override void close();
    }

    private static final class ActualProbe implements Probe {
        private final GraphicsMigrationScene scene;
        private final long seed;
        private final GpuDevice device = RenderSystem.getDevice();
        private final int width;
        private final int height;
        private final int cycles;
        private final List<AutoCloseable> owned = new ArrayList<>();
        private final Map<String, String> checks = new LinkedHashMap<>();
        private int acquired;
        private int released;
        private int completed;
        private int writes;
        private int readbacks;
        private ByteBuffer staging;
        private GpuTexture input;
        private GpuTexture output;
        private GpuBuffer readback;
        private GpuBuffer reusableBuffer;
        private byte[] pixels;
        private Throwable readFailure;
        private boolean pending;
        private boolean completedReadback;
        private boolean closed;
        private boolean ready;
        private byte[] expectedCycle;

        ActualProbe(GraphicsMigrationScene scene, long seed) {
            this.scene = java.util.Objects.requireNonNull(scene, "scene");
            this.seed = seed;
            width = scene.width();
            height = scene.height();
            cycles = switch (scene) {
                case BUFFER_REUSE -> 3;
                case RESOURCE_RECREATE -> 16;
                default -> 1;
            };
            checks.put("adapter", "renderpearl-26.3");
            checks.put("transfer", "CommandEncoder.writeToTexture/copyTextureToBuffer");
            checks.put("submission", "minecraft-owned-frame-boundary");
            checks.put("mainTargetModified", "false");
            checks.put("shaderUniform", "std140-vec4/16-byte-buffer/(2,4,8,16)-normalized");
            try {
                startCycle();
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private void startCycle() {
            expectedCycle = GraphicsMigrationFixtures.expected(scene, cycleSeed());
            CommandEncoder encoder = device.createCommandEncoder();
            if (scene == GraphicsMigrationScene.BUFFER_REUSE) {
                if (readback == null) {
                    readback = own(device.createBuffer(() -> "ModBench migration buffer readback",
                            GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) width * height * 4));
                    reusableBuffer = own(device.createBuffer(() -> "ModBench migration reusable upload",
                            GpuBuffer.USAGE_COPY_SRC | GpuBuffer.USAGE_COPY_DST, (long) width * height * 4));
                }
                ByteBuffer upload = MemoryUtil.memAlloc(expectedCycle.length);
                try {
                    upload.put(expectedCycle).flip();
                    encoder.writeToBuffer(reusableBuffer.slice(), upload);
                    encoder.copyToBuffer(reusableBuffer.slice(), readback.slice());
                    writes++;
                } finally { MemoryUtil.memFree(upload); }
                pending = true;
                completedReadback = false;
                RenderSystem.queueFencedTask(this::completeReadback);
                checks.put("transfer", "CommandEncoder.writeToBuffer/copyToBuffer");
                return;
            } else {
                switch (scene) {
                    case RGBA_STRIDE_ORIENTATION -> {
                        output = colorTexture("stride", GpuFormat.RGBA8_UNORM, false);
                        int stride = width * 4 + 7;
                        ByteBuffer padded = ByteBuffer.allocate(5 + stride * height);
                        padded.position(5);
                        for (int y = 0; y < height; y++) {
                            padded.position(5 + y * stride);
                            padded.put(expectedCycle, (height - 1 - y) * width * 4, width * 4);
                        }
                        padded.position(5).limit(5 + stride * height);
                        byte[] packed = GraphicsMigrationFixtures.packRows(padded, width, height, 4, stride, true);
                        if (padded.position() != 5) throw new AssertionError("Packer changed source position");
                        upload(encoder, output, packed);
                        checks.put("sourceRowStride", Integer.toString(stride));
                        checks.put("sourcePosition", "5");
                        checks.put("sourceOrientation", "bottom-up/repacked");
                    }
                    case RG8_UV_CHANNELS -> {
                        input = colorTexture("uv", GpuFormat.RG8_UNORM, false);
                        upload(encoder, input, GraphicsMigrationFixtures.rg8(width, height, cycleSeed()));
                        output = colorTexture("uv-sampled", GpuFormat.RGBA8_UNORM, true);
                        renderTexture(encoder, input, output, true);
                        checks.put("nativeFormat", "RG8_UNORM");
                        checks.put("sampling", "real-fragment-shader-rg-to-rgba");
                    }
                    case SHADER_DEPTH_BLEND -> renderDepthBlend(encoder);
                    case OFFSCREEN_COPY -> {
                        input = colorTexture("offscreen-source", GpuFormat.RGBA8_UNORM, false);
                        upload(encoder, input, expectedCycle);
                        GpuTexture rendered = colorTexture("offscreen-rendered", GpuFormat.RGBA8_UNORM, true);
                        renderTexture(encoder, input, rendered, false);
                        output = colorTexture("offscreen-copy", GpuFormat.RGBA8_UNORM, true);
                        encoder.clearColorTexture(output, new Vector4f(1, 0, 1, 1));
                        encoder.copyTextureToTexture(rendered, output, 0, 0, 0, 0, 0, width, height);
                        checks.put("attachment", "explicit-color-view");
                        checks.put("copy", "offscreen-render-to-separate-texture");
                    }
                    case BUFFER_REUSE, RESOURCE_RECREATE -> {
                        output = colorTexture("lifecycle-" + completed, GpuFormat.RGBA8_UNORM, false);
                        upload(encoder, output, expectedCycle);
                    }
                }
            }
            if (readback == null) {
                readback = own(device.createBuffer(() -> "ModBench migration readback",
                        GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) width * height * 4));
            }
            pending = true;
            completedReadback = false;
            try {
                encoder.copyTextureToBuffer(output, readback, 0, this::completeReadback, 0);
            } catch (RuntimeException | Error failure) {
                pending = false;
                throw failure;
            }
        }

        private void completeReadback() {
            try {
                if (!closed) {
                    try (var mapped = readback.map(true, false)) {
                        byte[] result = new byte[width * height * 4];
                        mapped.data().get(result);
                        pixels = result;
                    }
                    readbacks++;
                }
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable failure) {
                readFailure = failure;
            } finally {
                pending = false;
                completedReadback = true;
                if (closed) releaseAfterFence();
            }
        }

        private long cycleSeed() {
            return completed == cycles - 1 ? seed : seed + 101L * (completed + 1);
        }

        private GpuTexture colorTexture(String label, GpuFormat format, boolean attachment) {
            int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING;
            if (attachment) usage |= GpuTexture.USAGE_RENDER_ATTACHMENT;
            return own(device.createTexture("ModBench migration " + label, usage, format, width, height, 1, 1));
        }

        private void upload(CommandEncoder encoder, GpuTexture texture, byte[] bytes) {
            if (staging == null || staging.capacity() < bytes.length + 3) {
                if (staging != null) MemoryUtil.memFree(staging);
                staging = MemoryUtil.memAlloc(bytes.length + 3);
            }
            // Nonzero position catches accidental absolute-address/whole-capacity uploads.
            staging.clear().position(3);
            staging.put(bytes).flip().position(3);
            encoder.writeToTexture(texture, staging, 0, 0, 0, 0, width, height);
            writes++;
        }

        private void renderTexture(CommandEncoder encoder, GpuTexture source, GpuTexture target, boolean rgOnly) {
            VertexFormat format = VertexFormat.builder(0)
                    .addAttribute("Position", GpuFormat.RGB32_FLOAT)
                    .addAttribute("UV0", GpuFormat.RG32_FLOAT).build();
            CompiledRenderPipeline pipeline = compile(format, textureVertex(), textureFragment(rgOnly), false);
            GpuBuffer vertices = vertexBuffer(new float[]{-1, -1, 0, 0, 0, 3, -1, 0, 2, 0, -1, 3, 0, 0, 2});
            GpuBuffer parameters = vertexBufferWithUsage(new float[]{2, 4, 8, 16}, GpuBuffer.USAGE_UNIFORM);
            GpuTextureView sourceView = own(device.createTextureView(source));
            GpuTextureView targetView = own(device.createTextureView(target));
            try (RenderPass pass = encoder.createRenderPass(() -> "ModBench texture probe", targetView,
                    Optional.of(new Vector4f(1, 0, 1, 1)))) {
                pass.setPipeline(pipeline);
                pass.setUniform("ProbeParams", parameters);
                pass.setUniform("Sampler0", sourceView,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.setVertexBuffer(0, vertices.slice());
                pass.draw(3, 1, 0, 0);
            }
        }

        private void renderDepthBlend(CommandEncoder encoder) {
            output = colorTexture("depth-blend", GpuFormat.RGBA8_UNORM, true);
            GpuTexture depth = own(device.createTexture("ModBench migration depth", GpuTexture.USAGE_RENDER_ATTACHMENT,
                    GpuFormat.D32_FLOAT, width, height, 1, 1));
            GpuTextureView colorView = own(device.createTextureView(output));
            GpuTextureView depthView = own(device.createTextureView(depth));
            VertexFormat format = VertexFormat.builder(0)
                    .addAttribute("Position", GpuFormat.RGB32_FLOAT)
                    .addAttribute("Color", GpuFormat.RGBA32_FLOAT).build();
            CompiledRenderPipeline pipeline = compile(format, colorVertex(), colorFragment(), true);
            GpuBuffer parameters = vertexBufferWithUsage(new float[]{2, 4, 8, 16}, GpuBuffer.USAGE_UNIFORM);
            GpuBuffer blue = colorTriangle(.75F, 0, 0, 1, 1);
            GpuBuffer red = colorTriangle(.25F, 1, 0, 0, .5F);
            GpuBuffer green = colorTriangle(.5F, 0, 1, 0, 1);
            try (RenderPass pass = encoder.createRenderPass(() -> "ModBench depth blend probe", colorView,
                    Optional.of(new Vector4f(0, 0, 0, 0)), depthView, OptionalDouble.of(1))) {
                pass.setPipeline(pipeline);
                pass.setUniform("ProbeParams", parameters);
                for (GpuBuffer vertices : List.of(blue, red, green)) {
                    pass.setVertexBuffer(0, vertices.slice());
                    pass.draw(3, 1, 0, 0);
                }
            }
            checks.put("depthConvention", "explicit-forward-less-or-equal/clear-one");
            checks.put("depthSequence", "blue=.75;red=.25;green=.5-rejected");
            checks.put("blend", "SRC_ALPHA/ONE_MINUS_SRC_ALPHA;alpha=ONE/ONE_MINUS_SRC_ALPHA");
            checks.put("colorPipeline", "compiled-custom-shader");
        }

        private GpuBuffer colorTriangle(float depth, float r, float g, float b, float a) {
            float z = device.getDeviceInfo().isZZeroToOne() ? depth : 2 * depth - 1;
            return vertexBuffer(new float[]{-1, -1, z, r, g, b, a, 3, -1, z, r, g, b, a, -1, 3, z, r, g, b, a});
        }

        private GpuBuffer vertexBuffer(float[] values) {
            return vertexBufferWithUsage(values, GpuBuffer.USAGE_VERTEX);
        }

        private GpuBuffer vertexBufferWithUsage(float[] values, int usage) {
            ByteBuffer bytes = MemoryUtil.memAlloc(values.length * Float.BYTES).order(ByteOrder.nativeOrder());
            try {
                for (float value : values) bytes.putFloat(value);
                bytes.flip();
                return own(device.createBuffer(() -> "ModBench migration vertices", usage, bytes));
            } finally {
                MemoryUtil.memFree(bytes);
            }
        }

        private CompiledRenderPipeline compile(VertexFormat format, String vertex, String fragment, boolean depth) {
            Identifier id = Identifier.fromNamespaceAndPath("modbench", "graphics_migration/" + scene.id());
            var builder = RenderPipeline.builder().withLocation(id).withVertexShader(id).withFragmentShader(id)
                    .withVertexBinding(0, format).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
                    .withBindGroupLayout(BindGroupLayout.builder()
                            .withUniform("ProbeParams", UniformType.UNIFORM_BUFFER).build())
                    .withColorTargetState(depth ? new ColorTargetState(BlendFunction.TRANSLUCENT) : ColorTargetState.DEFAULT);
            if (depth) builder.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true));
            else builder.withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER).build());
            try (ShaderSource source = new InlineShaderSource(vertex, fragment)) {
                CompiledRenderPipeline result = device.compilePipeline(builder.build(), source, Runnable::run).join().finishCompile();
                if (result == null) throw new IllegalStateException("Required migration shader did not compile: " + scene.id());
                return own(result);
            }
        }

        private <T extends AutoCloseable> T own(T resource) {
            owned.add(resource);
            acquired++;
            return resource;
        }

        @Override public boolean ready() {
            RenderSystem.assertOnRenderThread();
            if (closed) throw new IllegalStateException("Probe is closed");
            if (ready) return true;
            if (!completedReadback) return false;
            if (readFailure != null) throw new IllegalStateException("GPU readback failed", readFailure);
            if (completed < cycles - 1 && !Arrays.equals(expectedCycle, pixels)) {
                throw new AssertionError("Intermediate fenced upload " + completed + " did not match fixture");
            }
            completed++;
            if (completed < cycles) {
                if (scene == GraphicsMigrationScene.RESOURCE_RECREATE) releaseResources();
                startCycle();
                return false;
            }
            ready = true;
            // The callback proves GPU completion: now all explicitly owned handles can be closed.
            releaseResources();
            return true;
        }

        @Override public byte[] read() {
            if (!ready || closed) throw new IllegalStateException("Read requires a completed, open probe");
            return pixels.clone();
        }

        @Override public Map<String, String> checks() {
            var result = new LinkedHashMap<>(checks);
            result.put("fencedReadbacks", Integer.toString(readbacks));
            result.put("uploadWrites", Integer.toString(writes));
            result.put("completedCycles", Integer.toString(completed));
            result.put("ownedHandlesAcquired", Integer.toString(acquired));
            result.put("ownedHandlesReleased", Integer.toString(released));
            result.put("ownedHandlesRemaining", Integer.toString(acquired - released));
            result.put("engineResourceReload", "not-tested;close-recreate-only");
            return Map.copyOf(result);
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (ready) releaseResources();
            else RenderSystem.queueFencedTask(this::releaseAfterFence);
            // Both cancellation and partial submission failure defer destruction past GPU work.
        }

        private void releaseAfterFence() {
            try { releaseResources(); }
            catch (RuntimeException | AssertionError failure) {
                readFailure = failure;
                System.getLogger(GraphicsMigrationGpu.class.getName()).log(System.Logger.Level.ERROR,
                        "Deferred migration-probe cleanup failed; no successful hardware claim is valid", failure);
            }
        }

        private void releaseResources() {
            RuntimeException first = null;
            for (int i = owned.size() - 1; i >= 0; i--) {
                try {
                    AutoCloseable resource = owned.get(i);
                    resource.close();
                    if (resource instanceof GpuTexture texture && !texture.isClosed()
                            || resource instanceof GpuTextureView view && !view.isClosed()
                            || resource instanceof GpuBuffer buffer && !buffer.isClosed()
                            || resource instanceof CompiledRenderPipeline pipeline && !pipeline.isClosed()) {
                        throw new IllegalStateException("GPU resource remained open after close");
                    }
                    released++;
                }
                catch (Exception failure) {
                    if (first == null) first = new IllegalStateException("Migration resource close failed", failure);
                    else first.addSuppressed(failure);
                }
            }
            owned.clear();
            if (staging != null) { MemoryUtil.memFree(staging); staging = null; }
            input = null;
            output = null;
            readback = null;
            reusableBuffer = null;
            if (first != null) throw first;
        }
    }

    private record InlineShaderSource(String vertex, String fragment) implements ShaderSource {
        @Override public String getShader(Identifier id, ShaderType type) {
            return switch (type) { case VERTEX -> vertex; case FRAGMENT -> fragment; };
        }
        @Override public CachedIncludeSource getInclude(Identifier id) {
            throw new IllegalArgumentException("Probe shaders have no external includes: " + id);
        }
        @Override public void close() {}
    }

    static String textureVertex() {
        return """
                #version 330
                #extension GL_ARB_separate_shader_objects : require
                layout(location=0) in vec3 Position;
                layout(location=1) in vec2 UV0;
                layout(location=0) out vec2 texCoord;
                #ifdef RENDERPEARL_EXPLICIT_DEPTH_INVARIANCE
                invariant gl_Position;
                #endif
                void main() { gl_Position = vec4(Position, 1.0); texCoord = UV0; }
                """;
    }

    static String textureFragment(boolean rgOnly) {
        return """
                #version 330
                #extension GL_ARB_separate_shader_objects : require
                uniform sampler2D Sampler0;
                layout(std140) uniform ProbeParams { vec4 ProbeValues; };
                layout(location=0) in vec2 texCoord;
                layout(location=0) out vec4 fragColor;
                void main() {
                """ + (rgOnly ? "fragColor = vec4(texture(Sampler0, texCoord).rg, 0.0, 1.0) * (ProbeValues / vec4(2,4,8,16));"
                        : "fragColor = texture(Sampler0, texCoord) * (ProbeValues / vec4(2,4,8,16));") + "\n}\n";
    }

    static String colorVertex() {
        return """
                #version 330
                #extension GL_ARB_separate_shader_objects : require
                layout(location=0) in vec3 Position;
                layout(location=1) in vec4 Color;
                layout(location=0) out vec4 vertexColor;
                #ifdef RENDERPEARL_EXPLICIT_DEPTH_INVARIANCE
                invariant gl_Position;
                #endif
                void main() { gl_Position = vec4(Position, 1.0); vertexColor = Color; }
                """;
    }

    static String colorFragment() {
        return """
                #version 330
                #extension GL_ARB_separate_shader_objects : require
                layout(location=0) in vec4 vertexColor;
                layout(location=0) out vec4 fragColor;
                layout(std140) uniform ProbeParams { vec4 ProbeValues; };
                void main() { fragColor = vertexColor * (ProbeValues / vec4(2,4,8,16)); }
                """;
    }
}
