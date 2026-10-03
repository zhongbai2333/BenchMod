package com.zhongbai233.bench.api.graphics;

/** Versioned deterministic GPU probes. Their identities and thresholds are comparison inputs. */
public enum GraphicsMigrationScene {
    RGBA_STRIDE_ORIENTATION("rgba-stride-orientation", 17, 9, 0),
    RG8_UV_CHANNELS("rg8-uv-channels", 17, 9, 2),
    BUFFER_REUSE("buffer-reuse", 8, 8, 0),
    SHADER_DEPTH_BLEND("shader-depth-blend", 8, 8, 2),
    OFFSCREEN_COPY("offscreen-copy", 17, 9, 0),
    RESOURCE_RECREATE("resource-recreate", 17, 9, 0);

    private final String id;
    private final int width, height, tolerance;
    GraphicsMigrationScene(String id, int width, int height, int tolerance) {
        this.id = id; this.width = width; this.height = height; this.tolerance = tolerance;
    }
    public String id() { return id; }
    public int width() { return width; }
    public int height() { return height; }
    public int tolerance() { return tolerance; }
    public int revision() { return 1; }
}
