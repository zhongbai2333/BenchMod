package com.zhongbai233.bench.runtime.client;

import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.vulkan.glsl.GlslCompiler;
import com.zhongbai233.bench.runtime.graphics.GraphicsMigrationGpu;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;
import static org.junit.jupiter.api.Assertions.*;

/** Real shader frontends only. These tests do not create a device or claim GPU rendering passed. */
class GraphicsMigrationShaderTest {
    @Test void shadersCompileThroughActualVulkanFrontendAndReflectBindings() throws Exception {
        try (var compiler = new GlslCompiler();
             var vertex = compiler.createIntermediary("probe-vertex", source("VERTEX"), ShaderType.VERTEX);
             var sample = compiler.createIntermediary("probe-sample", source("SAMPLE"), ShaderType.FRAGMENT);
             var depth = compiler.createIntermediary("probe-depth", source("DEPTH"), ShaderType.FRAGMENT)) {
            assertTrue(vertex.spirv().remaining()>0);
            assertEquals("ProbeTexture",name(sample.samplers().getFirst()));
            assertEquals("ProbeParams",name(sample.uniformBuffers().getFirst()));
            assertEquals("ProbeParams",name(depth.uniformBuffers().getFirst()));
        }
    }
    @Test void shadersCompileForOpenGlFrontend() throws Exception {
        compileGl(source("VERTEX"),Shaderc.shaderc_glsl_vertex_shader);
        compileGl(source("SAMPLE"),Shaderc.shaderc_glsl_fragment_shader);
        compileGl(source("DEPTH"),Shaderc.shaderc_glsl_fragment_shader);
    }
    private static String name(Object reflected)throws Exception {
        var method=reflected.getClass().getDeclaredMethod("name");method.setAccessible(true);return (String)method.invoke(reflected);
    }
    private static String source(String name)throws Exception {
        var field=GraphicsMigrationGpu.class.getDeclaredField(name);field.setAccessible(true);return (String)field.get(null);
    }
    private static void compileGl(String source,int kind) {
        long compiler=Shaderc.shaderc_compiler_initialize(),options=Shaderc.shaderc_compile_options_initialize();
        assertNotEquals(0,compiler);assertNotEquals(0,options);
        try {
            Shaderc.shaderc_compile_options_set_target_env(options,Shaderc.shaderc_target_env_opengl,Shaderc.shaderc_env_version_opengl_4_5);
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options,true);
            Shaderc.shaderc_compile_options_set_auto_map_locations(options,true);
            long result=Shaderc.shaderc_compile_into_spv(compiler,source,kind,"probe","main",options);
            assertNotEquals(0,result);
            try {assertEquals(Shaderc.shaderc_compilation_status_success,Shaderc.shaderc_result_get_compilation_status(result),Shaderc.shaderc_result_get_error_message(result));}
            finally {Shaderc.shaderc_result_release(result);}
        } finally {Shaderc.shaderc_compile_options_release(options);Shaderc.shaderc_compiler_release(compiler);}
    }
}
