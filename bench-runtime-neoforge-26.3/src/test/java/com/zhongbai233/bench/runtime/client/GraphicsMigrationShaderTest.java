package com.zhongbai233.bench.runtime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.zhongbai233.bench.runtime.graphics.GraphicsMigrationGpu;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;

/** Real ShaderC frontend checks, not a GPU rendering test. */
class GraphicsMigrationShaderTest {
    @Test
    void allProbeShadersCompileForBothBackendTargets() throws Exception {
        compile(source("textureVertex"), Shaderc.shaderc_glsl_vertex_shader);
        compile(source("textureFragment", true), Shaderc.shaderc_glsl_fragment_shader);
        compile(source("textureFragment", false), Shaderc.shaderc_glsl_fragment_shader);
        compile(source("colorVertex"), Shaderc.shaderc_glsl_vertex_shader);
        compile(source("colorFragment"), Shaderc.shaderc_glsl_fragment_shader);
    }

    private static String source(String name, Object... arguments) throws Exception {
        Method method = arguments.length == 0 ? GraphicsMigrationGpu.class.getDeclaredMethod(name)
                : GraphicsMigrationGpu.class.getDeclaredMethod(name, boolean.class);
        method.setAccessible(true);
        return (String) method.invoke(null, arguments);
    }

    private static void compile(String source, int kind) {
        long compiler = Shaderc.shaderc_compiler_initialize();
        assertNotEquals(0, compiler, "ShaderC compiler unavailable");
        try {
            for (int target : new int[]{Shaderc.shaderc_target_env_opengl, Shaderc.shaderc_target_env_vulkan}) {
                long options = Shaderc.shaderc_compile_options_initialize();
                try {
                    Shaderc.shaderc_compile_options_set_target_env(options, target,
                            target == Shaderc.shaderc_target_env_opengl ? Shaderc.shaderc_env_version_opengl_4_5
                                    : Shaderc.shaderc_env_version_vulkan_1_2);
                    Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
                    long result = Shaderc.shaderc_compile_into_spv(compiler, source, kind,
                            "modbench-migration-probe", "main", options);
                    assertNotEquals(0, result);
                    try {
                        assertEquals(Shaderc.shaderc_compilation_status_success,
                                Shaderc.shaderc_result_get_compilation_status(result),
                                "target=" + target + ": " + Shaderc.shaderc_result_get_error_message(result));
                    } finally {
                        Shaderc.shaderc_result_release(result);
                    }
                } finally {
                    Shaderc.shaderc_compile_options_release(options);
                }
            }
        } finally {
            Shaderc.shaderc_compiler_release(compiler);
        }
    }
}
