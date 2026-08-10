package com.zhongbai233.bench.api.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zhongbai233.bench.api.BenchCompatibility;
import com.zhongbai233.bench.api.BenchProvider;
import com.zhongbai233.bench.api.BenchRegistrar;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProviderDiscoveryTest {
    @TempDir Path tempDirectory;

    @Test
    void discoversProvidersFromExplicitClassLoader() throws Exception {
        ClassLoader loader = serviceClassLoader(List.of(ExampleProvider.class.getName()));

        var providers = ProviderDiscovery.discover(loader, 1);

        assertEquals(List.of("example"), providers.stream().map(BenchProvider::id).toList());
    }

    @Test
    void failsWhenExpectedCountDoesNotMatch() throws Exception {
        ClassLoader loader = serviceClassLoader(List.of());

        assertThrows(ProviderDiscoveryException.class, () -> ProviderDiscovery.discover(loader, 1));
    }

    @Test
    void reportsNullIdAsProviderDiscoveryFailure() throws Exception {
        ClassLoader loader = serviceClassLoader(List.of(NullIdProvider.class.getName()));

        assertThrows(ProviderDiscoveryException.class, () -> ProviderDiscovery.discover(loader, 1));
    }

    private ClassLoader serviceClassLoader(List<String> implementations) throws Exception {
        Path serviceFile = tempDirectory.resolve("META-INF/services/" + BenchProvider.class.getName());
        Files.createDirectories(serviceFile.getParent());
        Files.write(serviceFile, implementations);
        return new URLClassLoader(new URL[] { tempDirectory.toUri().toURL() }, getClass().getClassLoader());
    }

    public static final class ExampleProvider implements BenchProvider {
        public ExampleProvider() {}

        @Override public String id() { return "example"; }
        @Override public BenchCompatibility compatibility() { return BenchCompatibility.exactly(0, 1); }
        @Override public void register(BenchRegistrar registrar) {}
    }

    public static final class NullIdProvider implements BenchProvider {
        public NullIdProvider() {}
        @Override public String id() { return null; }
        @Override public BenchCompatibility compatibility() { return BenchCompatibility.exactly(0, 1); }
        @Override public void register(BenchRegistrar registrar) {}
    }
}