package com.zhongbai233.bench.api.discovery;

import com.zhongbai233.bench.api.BenchProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/** Deterministic, defensive ServiceLoader discovery. */
public final class ProviderDiscovery {
    private ProviderDiscovery() {}

    public static List<BenchProvider> discover(ClassLoader gameClassLoader, int expectedProviderCount) {
        if (expectedProviderCount < 0) {
            throw new IllegalArgumentException("expectedProviderCount must be non-negative");
        }

        List<BenchProvider> providers = new ArrayList<>();
        try {
            ServiceLoader.load(BenchProvider.class, gameClassLoader).forEach(providers::add);
        } catch (ServiceConfigurationError error) {
            throw new ProviderDiscoveryException("Failed to instantiate a BenchProvider", error);
        }

        var metadata = providers.stream()
                .map(provider -> new ProviderMetadata(provider, provider.id(), provider.compatibility()))
                .toList();
        for (var entry : metadata) {
            if (entry.id() == null || entry.id().isBlank()) {
                throw new ProviderDiscoveryException("Provider " + entry.provider().getClass().getName() + " has a blank id");
            }
            if (entry.compatibility() == null) {
                throw new ProviderDiscoveryException("Provider " + entry.id() + " has no API compatibility declaration");
            }
        }
        metadata = metadata.stream().sorted(Comparator.comparing(ProviderMetadata::id)).toList();
        Set<String> ids = new HashSet<>();
        for (var entry : metadata) {
            if (!ids.add(entry.id())) {
                throw new ProviderDiscoveryException("Duplicate BenchProvider id: " + entry.id());
            }
        }
        if (metadata.size() != expectedProviderCount) {
            throw new ProviderDiscoveryException(
                    "Expected " + expectedProviderCount + " BenchProvider(s), discovered " + metadata.size());
        }
        return metadata.stream().map(ProviderMetadata::provider).toList();
    }

    private record ProviderMetadata(BenchProvider provider, String id,
                                    com.zhongbai233.bench.api.BenchCompatibility compatibility) {}
}