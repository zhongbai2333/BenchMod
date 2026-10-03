package com.zhongbai233.bench.runtime.graphics;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.textures.*;
import com.zhongbai233.bench.api.graphics.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.joml.Vector4f;
import net.minecraft.resources.Identifier;

/** Minecraft 26.2 public Blaze3D implementation. No OpenGL handles, PBO assumptions or backend casts. */
public final class GraphicsMigrationGpu {
    private GraphicsMigrationGpu() {}
    public interface Probe extends AutoCloseable {
        boolean ready();
        byte[] read();
        Map<String, String> checks();
        @Override void close();
    }

    public static Map<String, String> device() {
        var info = RenderSystem.getDevice().getDeviceInfo();
        String raw = info.backendName();
        String normalized = raw.toLowerCase(Locale.ROOT);
        if (normalized.contains("vulkan")) normalized = "vulkan";
        else if (normalized.contains("opengl")) normalized = "opengl";
        return Map.of("actualBackend", normalized, "rawBackend", raw, "deviceName", info.name(),
                "vendor", info.vendorName(), "driver", info.driverInfo());
    }

    public static Probe start(GraphicsMigrationScene scene, long seed) {
        RenderSystem.assertOnRenderThread();
        Task task = new Task(scene, seed);
        try { task.submit(); return task; }
        catch (RuntimeException | AssertionError failure) { task.close(); throw failure; }
    }

    private static final String VERTEX = """
            #version 330
            out vec2 texCoord;
            void main() {
                vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
                texCoord = uv;
            }
            """;
    private static final String SAMPLE = """
            #version 330
            uniform sampler2D ProbeTexture;
            layout(std140) uniform ProbeParams { vec4 Tint; vec4 Options; };
            in vec2 texCoord;
            out vec4 fragColor;
            void main() {
                vec4 value = texture(ProbeTexture, texCoord);
                fragColor = Options.x > 0.5 ? vec4(value.rg, 0.0, 1.0) * Tint : value * Tint;
            }
            """;
    private static final String DEPTH = """
            #version 330
            layout(std140) uniform ProbeParams { vec4 Tint; vec4 Options; };
            out vec4 fragColor;
            void main() { fragColor = Tint; gl_FragDepth = Options.x; }
            """;
    private static final RenderPipeline SAMPLE_PIPELINE = pipeline(false);
    private static final RenderPipeline DEPTH_PIPELINE = pipeline(true);

    private static RenderPipeline pipeline(boolean depth) {
        var layout = BindGroupLayout.builder().withUniform("ProbeParams", UniformType.UNIFORM_BUFFER);
        if (!depth) layout.withSampler("ProbeTexture");
        var builder = RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("modbench", "graphics_migration/" + (depth ? "depth" : "sample")))
                .withVertexShader(Identifier.fromNamespaceAndPath("modbench", "graphics_migration/fullscreen"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("modbench", "graphics_migration/" + (depth ? "depth" : "sample")))
                .withBindGroupLayout(layout.build()).withCull(false).withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(depth ? new ColorTargetState(BlendFunction.TRANSLUCENT) : ColorTargetState.DEFAULT);
        if (depth) builder.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN, true));
        else builder.withDepthStencilState(Optional.empty());
        return builder.build();
    }

    private static final class Task implements Probe {
        private final GraphicsMigrationScene scene;
        private final long seed;
        private final GpuDevice device = RenderSystem.getDevice();
        private final List<AutoCloseable> owned = new ArrayList<>();
        private final Map<String, String> checks = new LinkedHashMap<>();
        private final AtomicBoolean copied = new AtomicBoolean();
        private GpuBuffer readback, reusable;
        private GpuFence fence;
        private int iteration, created, closed;
        private boolean finished, released;

        Task(GraphicsMigrationScene scene, long seed) {
            this.scene = scene; this.seed = seed;
            checks.put("submission", "Minecraft-owned frame boundary");
            checks.put("api", "public-blaze3d"); checks.put("readback", "GPU-to-CPU");
            checks.put("rowConvention", "raw GPU row zero; no backend-specific correction");
            checks.put("globalDriverLeakClaim", "none; counts cover only suite-owned handles");
        }
        private <T extends AutoCloseable> T own(T value) { owned.add(value); created++; return value; }
        private GpuTexture texture(String label, GpuFormat format, int usage) {
            return own(device.createTexture("modbench:" + label, usage, format, scene.width(), scene.height(), 1, 1));
        }
        private GpuBuffer buffer(String label, int usage, long size) { return own(device.createBuffer(() -> "modbench:"+label, usage, size)); }
        private ByteBuffer direct(byte[] bytes) { return ByteBuffer.allocateDirect(bytes.length).put(bytes).flip(); }
        private GpuBuffer uniform(float r, float g, float b, float a, float option) {
            ByteBuffer bytes = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
            bytes.putFloat(r).putFloat(g).putFloat(b).putFloat(a).putFloat(option).putFloat(0).putFloat(0).putFloat(0).flip();
            return own(device.createBuffer(() -> "modbench:probe-params", GpuBuffer.USAGE_UNIFORM, bytes));
        }
        void submit() {
            if (scene == GraphicsMigrationScene.BUFFER_REUSE) {
                readback = buffer("buffer-readback", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, scene.width()*scene.height()*4L);
                reusable = buffer("reusable-upload", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC, readback.size());
                submitBufferCycle(); return;
            }
            submitTextureCycle();
        }
        private void submitBufferCycle() {
            CommandEncoder encoder = device.createCommandEncoder();
            encoder.writeToBuffer(reusable.slice(), direct(GraphicsMigrationFixtures.rgba(scene.width(), scene.height(), cycleSeed())));
            encoder.copyToBuffer(reusable.slice(), readback.slice());
            fence = own(encoder.createFence());
        }
        private long cycleSeed() { return iteration == cycles()-1 ? seed : seed + iteration + 1; }
        private int cycles() { return scene == GraphicsMigrationScene.BUFFER_REUSE ? 3 : scene == GraphicsMigrationScene.RESOURCE_RECREATE ? 16 : 1; }

        private void submitTextureCycle() {
            copied.set(false);
            CommandEncoder encoder = device.createCommandEncoder();
            GpuTexture output;
            int copyUsage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC;
            if (scene == GraphicsMigrationScene.SHADER_DEPTH_BLEND) {
                output = texture("depth-color", GpuFormat.RGBA8_UNORM, copyUsage | GpuTexture.USAGE_RENDER_ATTACHMENT);
                GpuTexture depth = texture("depth-attachment", GpuFormat.D32_FLOAT, GpuTexture.USAGE_RENDER_ATTACHMENT);
                var colorView = own(device.createTextureView(output)); var depthView = own(device.createTextureView(depth));
                compile(DEPTH_PIPELINE, DEPTH);
                GpuBuffer blue = uniform(0,0,1,1,.75f), red = uniform(1,0,0,.5f,.25f), green = uniform(0,1,0,1,.5f);
                try (RenderPass pass = encoder.createRenderPass(() -> "modbench:depth-blend", colorView,
                        Optional.of(new Vector4f(0,0,0,1)), depthView, OptionalDouble.of(1))) {
                    pass.setPipeline(DEPTH_PIPELINE);
                    for (GpuBuffer params : List.of(blue, red, green)) { pass.setUniform("ProbeParams", params); pass.draw(3,1,0,0); }
                }
                checks.put("drawOrder", "blue@0.75; red(alpha=0.5)@0.25; green@0.5 rejected");
                checks.put("depthWrite", "true"); checks.put("blend", "src-alpha over destination; alpha preserved");
            } else {
                boolean rg8 = scene == GraphicsMigrationScene.RG8_UV_CHANNELS;
                GpuTexture input = texture("upload", rg8 ? GpuFormat.RG8_UNORM : GpuFormat.RGBA8_UNORM,
                        copyUsage | GpuTexture.USAGE_TEXTURE_BINDING);
                byte[] pixels = rg8 ? GraphicsMigrationFixtures.rg8(scene.width(),scene.height(),seed)
                        : GraphicsMigrationFixtures.rgba(scene.width(),scene.height(),cycleSeed());
                if (scene == GraphicsMigrationScene.RGBA_STRIDE_ORIENTATION) pixels = paddedBottomUp(pixels);
                encoder.writeToTexture(input, direct(pixels),0,0,0,0,scene.width(),scene.height());
                output = input;
                if (rg8 || scene == GraphicsMigrationScene.OFFSCREEN_COPY) {
                    GpuTexture attachment = texture("offscreen-color",GpuFormat.RGBA8_UNORM,
                            copyUsage | GpuTexture.USAGE_RENDER_ATTACHMENT);
                    var inputView = own(device.createTextureView(input)); var targetView = own(device.createTextureView(attachment));
                    var sampler = own(device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                            FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.of(0)));
                    GpuBuffer params = uniform(1,1,1,1,rg8 ? 1 : 0);
                    compile(SAMPLE_PIPELINE,SAMPLE);
                    try (RenderPass pass=encoder.createRenderPass(() -> "modbench:sample-offscreen",targetView,Optional.of(new Vector4f(1,0,1,0)))) {
                        pass.setPipeline(SAMPLE_PIPELINE); pass.setUniform("ProbeParams",params); pass.bindTexture("ProbeTexture",inputView,sampler); pass.draw(3,1,0,0);
                    }
                    output=attachment;
                    checks.put("bindings", "ProbeTexture + std140 ProbeParams; nearest sampling");
                    checks.put("inputFormat", rg8 ? "RG8_UNORM" : "RGBA8_UNORM");
                    if (!rg8) {
                        output=texture("copied-offscreen",GpuFormat.RGBA8_UNORM,copyUsage);
                        encoder.copyTextureToTexture(attachment,output,0,0,0,0,0,scene.width(),scene.height());
                        checks.put("attachments", "explicit offscreen RGBA8 color; copied after render pass closes");
                    }
                }
            }
            readback=buffer("texture-readback",GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,scene.width()*scene.height()*4L);
            encoder.copyTextureToBuffer(output,readback,0,() -> copied.set(true),0);
        }
        private byte[] paddedBottomUp(byte[] pixels) {
            int row=scene.width()*4, stride=row+12, offset=7;
            ByteBuffer padded=ByteBuffer.allocate(offset+stride*scene.height());
            Arrays.fill(padded.array(),(byte)0xCD);
            for (int y=0;y<scene.height();y++) padded.put(offset+(scene.height()-1-y)*stride,pixels,y*row,row);
            padded.position(offset);
            checks.put("sourceRowStride",Integer.toString(stride)); checks.put("sourceOffset",Integer.toString(offset));
            checks.put("sourceOrientation","bottom-up; repacked through shared fixture utility");
            return GraphicsMigrationFixtures.packRows(padded,scene.width(),scene.height(),4,stride,true);
        }
        private void compile(RenderPipeline pipeline,String fragment) {
            if (!device.precompilePipeline(pipeline,(name,type) -> type == ShaderType.VERTEX ? VERTEX : fragment).isValid())
                throw new IllegalStateException("Probe shader compilation failed: " + device.getLastDebugMessages());
        }
        @Override public boolean ready() {
            if (released) throw new IllegalStateException("Probe closed");
            if (finished) return true;
            boolean completed=scene == GraphicsMigrationScene.BUFFER_REUSE ? fence.awaitCompletion(0) : copied.get();
            if (!completed) return false;
            if (iteration < cycles()-1) {
                byte[] expected=GraphicsMigrationFixtures.rgba(scene.width(),scene.height(),cycleSeed());
                if (!GraphicsMigrationOracle.compare(expected,readBytes(),0).passed()) throw new AssertionError("Stale/corrupt cycle " + iteration);
                iteration++;
                if (scene == GraphicsMigrationScene.BUFFER_REUSE) { release(fence); fence=null; submitBufferCycle(); }
                else { releaseAll(); submitTextureCycle(); }
                return false;
            }
            finished=true; checks.put("verifiedCycles",Integer.toString(cycles())); return true;
        }
        private byte[] readBytes() {
            byte[] bytes=new byte[Math.toIntExact(readback.size())];
            try (var mapped=readback.map(true,false)) { mapped.data().get(bytes); }
            return bytes;
        }
        @Override public byte[] read() { if (!finished) throw new IllegalStateException("GPU is not complete"); return readBytes(); }
        @Override public Map<String,String> checks() {
            checks.put("ownedHandlesCreated",Integer.toString(created)); checks.put("ownedHandlesClosed",Integer.toString(closed));
            checks.put("ownedHandlesRemaining",Integer.toString(owned.size()));
            checks.put("pipelineOwnership","two reusable device-cached pipelines; global cache not cleared");
            return Map.copyOf(checks);
        }
        private void release(AutoCloseable resource) {
            try { resource.close(); }
            catch (RuntimeException failure) { throw failure; }
            catch (Exception failure) { throw new IllegalStateException("GPU resource close failed",failure); }
            if (resource instanceof GpuTexture texture && !texture.isClosed()) throw new AssertionError("Texture close did not take effect");
            if (resource instanceof GpuTextureView view && !view.isClosed()) throw new AssertionError("Texture view close did not take effect");
            if (resource instanceof GpuBuffer buffer && !buffer.isClosed()) throw new AssertionError("Buffer close did not take effect");
            owned.remove(resource); closed++;
        }
        private void releaseAll() {
            RuntimeException failure=null;
            for (AutoCloseable resource : new ArrayList<>(owned).reversed()) {
                try { release(resource); }
                catch (RuntimeException | AssertionError error) {
                    if(failure==null) failure=new IllegalStateException("GPU cleanup failed",error);
                    else failure.addSuppressed(error);
                }
            }
            if (failure != null) throw failure;
        }
        @Override public void close() {
            if (released) return;
            released=true;
            // Engine owns submission. Timeout/cancellation must not free resources still in flight.
            if (finished) releaseAll();
            else RenderSystem.queueFencedTask(() -> {
                try { releaseAll(); }
                catch (RuntimeException | AssertionError failure) {
                    // A cancelled/blocked probe must never poison Minecraft's global fenced-task queue.
                    System.getLogger(GraphicsMigrationGpu.class.getName()).log(System.Logger.Level.ERROR,
                            "Cancelled graphics probe cleanup failed for " + scene.id(), failure);
                }
            });
        }
    }
}
