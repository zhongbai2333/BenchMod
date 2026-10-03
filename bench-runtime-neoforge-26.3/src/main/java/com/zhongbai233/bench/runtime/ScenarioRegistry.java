package com.zhongbai233.bench.runtime;

import com.zhongbai233.bench.api.BenchApiVersion;
import com.zhongbai233.bench.api.BenchProvider;
import com.zhongbai233.bench.api.ScenarioDescriptor;
import com.zhongbai233.bench.api.neoforge.server.BenchServerProvider;
import com.zhongbai233.bench.api.neoforge.server.BenchServerScenarioFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ScenarioRegistry {
    private final List<Registration> registrations = new ArrayList<>();
    private final Set<String> scenarioIds = new HashSet<>();

    public void registerProviders(List<BenchProvider> providers) {
        for (BenchProvider provider : providers) {
            if (!provider.compatibility().supports(BenchApiVersion.MAJOR, BenchApiVersion.MINOR)) {
                throw new IllegalStateException("Provider " + provider.id() + " is incompatible with Core API "
                        + BenchApiVersion.MAJOR + "." + BenchApiVersion.MINOR);
            }
            if (!(provider instanceof BenchServerProvider serverProvider)) {
                throw new IllegalStateException("Provider " + provider.id() + " has no dedicated server scenarios");
            }
            serverProvider.registerServer((descriptor, factory) -> register(provider.id(), descriptor, factory));
        }
        if (registrations.isEmpty()) {
            throw new IllegalStateException("No dedicated server scenarios were registered");
        }
    }

    public List<Registration> registrations() {
        return List.copyOf(registrations);
    }

    private void register(String providerId, ScenarioDescriptor descriptor, BenchServerScenarioFactory factory) {
        if (descriptor == null || factory == null) {
            throw new IllegalArgumentException("Scenario descriptor and factory are required");
        }
        if (!scenarioIds.add(descriptor.id())) {
            throw new IllegalStateException("Duplicate scenario id: " + descriptor.id());
        }
        registrations.add(new Registration(providerId, descriptor, factory));
    }

    public record Registration(String providerId, ScenarioDescriptor descriptor, BenchServerScenarioFactory factory) {}
}