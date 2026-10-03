package com.zhongbai233.bench.runtime.client;

import com.zhongbai233.bench.runtime.graphics.GraphicsMigrationGpu;

import com.google.gson.JsonParser;
import com.zhongbai233.bench.api.graphics.*;
import com.zhongbai233.bench.api.neoforge.client.*;
import com.zhongbai233.bench.api.neoforge.server.BenchArtifactWriter;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsMigrationSuiteTest {
    static final Map<String,String> DEVICE=Map.of("actualBackend","opengl","deviceName","GPU","vendor","Vendor","driver","test");
    @Test void sixActualResultsAndExplicitOptionalSkipAreRegistered() throws Exception {
        Harness h=new Harness(); AtomicInteger closes=new AtomicInteger();
        var runner=h.runner((s,seed)->probe(s,seed,2,closes));
        runner.setup(h.context); int ticks=run(runner,h); runner.verify(h.context); runner.teardown(h.context);
        var report=h.report(); assertEquals("PASS",report.get("status").getAsString());
        assertEquals(7,report.getAsJsonArray("scenes").size()); assertEquals(6,closes.get()); assertTrue(ticks>=18);
        assertEquals("SKIP",report.getAsJsonArray("scenes").get(6).getAsJsonObject().get("status").getAsString());
        assertEquals(31,h.files.size()); // 6 x (raw expected/actual + expected/actual/diff PNG), plus JSON
        assertTrue(h.files.get("rgba-stride-orientation-actual.png").length>0);
    }
    @Test void backendFallbackBlocksWithoutSubmittingGpuWork() throws Exception {
        Harness h=new Harness();
        var runner=runner(()->Map.of("actualBackend","vulkan","deviceName","GPU","vendor","V","driver","D"),
                (s,seed)->{ throw new AssertionError("Must not execute mismatched backend"); },()->"test");
        runner.setup(h.context);run(runner,h);runner.verify(h.context);runner.teardown(h.context);
        assertEquals("BLOCKED",h.report().get("status").getAsString()); assertFalse(h.invalidations.isEmpty());
    }
    @Test void corruptReadbackFailsButDoesNotPreventRemainingProbes() throws Exception {
        Harness h=new Harness(); AtomicInteger starts=new AtomicInteger();
        var runner=h.runner((s,seed)->{ starts.incrementAndGet(); return new GraphicsMigrationGpu.Probe() {
            public boolean ready(){return true;} public byte[] read(){byte[] v=GraphicsMigrationFixtures.expected(s,seed);v[0]^=127;return v;}
            public Map<String,String> checks(){return Map.of();} public void close(){}
        }; });
        runner.setup(h.context);run(runner,h);assertThrows(AssertionError.class,()->runner.verify(h.context));runner.teardown(h.context);
        assertEquals(6,starts.get());assertEquals("FAIL",h.report().get("status").getAsString());
    }
    @Test void unsupportedRequiredProbeIsBlockedAndNeverSkip() throws Exception {
        Harness h=new Harness();var runner=h.runner((s,seed)->{throw new UnsupportedOperationException("feature");});
        runner.setup(h.context);run(runner,h);runner.verify(h.context);runner.teardown(h.context);
        assertEquals("BLOCKED",h.report().getAsJsonArray("scenes").get(0).getAsJsonObject().get("status").getAsString());
        assertEquals("BLOCKED",h.report().get("status").getAsString());
    }
    @Test void cancellationClosesPendingAndPersistsUnrunBlockedEvidence() throws Exception {
        Harness h=new Harness();AtomicInteger closed=new AtomicInteger();var runner=h.runner((s,seed)->probe(s,seed,Integer.MAX_VALUE,closed));
        runner.setup(h.context);runner.measure(h.context);runner.teardown(h.context);
        assertEquals(1,closed.get());assertEquals("BLOCKED",h.report().get("status").getAsString());assertEquals(7,h.report().getAsJsonArray("scenes").size());
    }
    @Test void absentDeviceRemainsBlockedThroughVerifyAndTeardown() throws Exception {
        Harness h=new Harness();var runner=runner(()->{throw new IllegalStateException("missing");},(s,seed)->null,()->"test");
        runner.setup(h.context);run(runner,h);runner.verify(h.context);runner.teardown(h.context);
        assertEquals("BLOCKED",h.report().get("status").getAsString());
    }
    @Test void noCompletionTimesOutWithoutBlockingClientTicks() throws Exception {
        Harness h=new Harness();AtomicInteger closed=new AtomicInteger();var runner=h.runner((s,seed)->probe(s,seed,Integer.MAX_VALUE,closed));
        runner.setup(h.context);for(int i=0;i<400;i++) runner.measure(h.context);runner.teardown(h.context);
        assertEquals(1,closed.get());assertTrue(h.report().getAsJsonArray("scenes").get(0).getAsJsonObject().get("reason").getAsString().contains("400"));
    }
    private static BenchClientScenario runner(java.util.function.Supplier<Map<String,String>> device,
            BiFunction<GraphicsMigrationScene,Long,GraphicsMigrationGpu.Probe> start,java.util.function.Supplier<String> version) {
        try {
            var constructor=Class.forName("com.zhongbai233.bench.runtime.graphics.GraphicsMigrationRuntime$Runner")
                    .getDeclaredConstructor(java.util.function.Supplier.class,BiFunction.class,java.util.function.Supplier.class);
            constructor.setAccessible(true); return (BenchClientScenario)constructor.newInstance(device,start,version);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static int run(BenchClientScenario runner,Harness h)throws Exception{
        int ticks=0;while(runner.measure(h.context)!=BenchClientStepResult.COMPLETE){if(++ticks>3000)throw new AssertionError("did not complete");}return ticks+1;
    }
    private static GraphicsMigrationGpu.Probe probe(GraphicsMigrationScene scene,long seed,int delay,AtomicInteger closes){
        return new GraphicsMigrationGpu.Probe(){int remaining=delay;boolean closed;
            public boolean ready(){return remaining--<=0;} public byte[] read(){return GraphicsMigrationFixtures.expected(scene,seed);}
            public Map<String,String> checks(){return Map.of("ownedHandlesRemaining",closed?"0":"1");}
            public void close(){if(!closed){closed=true;closes.incrementAndGet();}}
        };
    }
    private static class Harness {
        final Map<String,byte[]> files=new HashMap<>(); final List<String> invalidations=new ArrayList<>();
        final BenchClientEnvironment environment=new BenchClientEnvironment(){
            public boolean isValid(){return invalidations.isEmpty();}public List<String> invalidations(){return List.copyOf(invalidations);}
            public void invalidate(String reason){invalidations.add(reason);}public BenchClientReadiness readiness(){return null;}public boolean isFrameStable(int n){return true;}
        };
        final BenchArtifactWriter artifacts=new BenchArtifactWriter(){
            public Path write(String name,String type,String text){return write(name,type,text.getBytes(StandardCharsets.UTF_8));}
            public Path write(String name,String type,byte[] bytes){files.put(name,bytes);return Path.of(name);}public void register(Path file,String type){}
        };
        final BenchClientContext context=new GraphicsMigrationTestContext(environment,artifacts);
        BenchClientScenario runner(BiFunction<GraphicsMigrationScene,Long,GraphicsMigrationGpu.Probe> start){return GraphicsMigrationSuiteTest.runner(()->DEVICE,start,()->"test");}
        com.google.gson.JsonObject report(){return JsonParser.parseString(new String(files.get("graphics-migration.json"),StandardCharsets.UTF_8)).getAsJsonObject();}
    }
}
