package com.zhongbai233.bench.gradle;

import java.util.Locale;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

/** One validated selection shared by client launches and the paired coordinator. */
final class ClientGraphicsBackend {
    static final String PROPERTY = "modBench.client.graphicsBackend";

    private ClientGraphicsBackend() {}

    static Provider<String> requested(Project project, ModBenchExtension extension) {
        return project.getProviders().gradleProperty(PROPERTY)
                .orElse(extension.getClientGraphicsBackend())
                .map(ClientGraphicsBackend::normalize);
    }

    static String normalize(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (!normalized.equals("opengl") && !normalized.equals("vulkan")) {
            throw new GradleException("Invalid " + PROPERTY + " '" + value
                    + "'; expected opengl or vulkan (modBench.clientGraphicsBackend in the DSL)");
        }
        return normalized;
    }
}
