package com.zhongbai233.bench.network.proxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhongbai233.bench.network.NetworkDirection;
import com.zhongbai233.bench.network.NetworkDirectionProfile;
import com.zhongbai233.bench.network.NetworkFault;
import com.zhongbai233.bench.network.NetworkFaultKind;
import com.zhongbai233.bench.network.NetworkProfile;
import com.zhongbai233.bench.network.NetworkSemanticLayer;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 15, unit = TimeUnit.SECONDS)
class TcpStreamProxyTest {
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    @Test
    void preservesLargeBidirectionalByteStreamsAndMetrics() throws Exception {
        byte[] payload = payload(70_000);
        CopyOnWriteArrayList<NetworkEvent> events = new CopyOnWriteArrayList<>();
        try (EchoServer upstream = new EchoServer();
             TcpStreamProxy proxy = proxy(upstream.address(), passthrough(), events).start();
             Socket client = new Socket()) {
            client.connect(proxy.listenAddress());
            client.getOutputStream().write(payload);
            client.shutdownOutput();

            assertArrayEquals(payload, client.getInputStream().readAllBytes());
            waitForNoConnections(proxy);
            ProxyMetricsSnapshot snapshot = proxy.snapshot();
            assertEquals(payload.length, snapshot.clientToServerBytesRead());
            assertEquals(payload.length, snapshot.clientToServerBytesForwarded());
            assertEquals(payload.length, snapshot.serverToClientBytesRead());
            assertEquals(payload.length, snapshot.serverToClientBytesForwarded());
            assertEquals(0, snapshot.queuedBytes());
            assertTrue(snapshot.forwardedQuanta() >= 10);
            assertTrue(events.stream().anyMatch(event -> event.type().equals("CONNECTION_OPENED")));
            assertTrue(events.stream().anyMatch(event -> event.type().equals("CONNECTION_CLOSED")));
        }
    }

    @Test
    void forwardsSmallInteractiveMessagesWithoutWaitingForFinOrAFullQuantum() throws Exception {
        try (InteractiveEchoServer upstream = new InteractiveEchoServer();
             TcpStreamProxy proxy = proxy(upstream.address(), passthrough(), new CopyOnWriteArrayList<>()).start();
             Socket client = new Socket()) {
            client.connect(proxy.listenAddress());
            client.setSoTimeout(1_000);
            client.getOutputStream().write(0x5a);
            client.getOutputStream().flush();
            assertEquals(0x5a, client.getInputStream().read());
        }
    }

    @Test
    void appliesDirectionalLatencyWithoutSeriallyAccumulatingPerQuantum() throws Exception {
        NetworkDirectionProfile delayed = new NetworkDirectionProfile(80, 0, 0, 0, 65_536, 4_096);
        NetworkProfile profile = new NetworkProfile(1, "latency", 9, delayed,
                NetworkDirectionProfile.PASSTHROUGH, List.of());
        byte[] payload = payload(32_768);
        try (EchoServer upstream = new EchoServer();
             TcpStreamProxy proxy = proxy(upstream.address(), profile, new CopyOnWriteArrayList<>()).start();
             Socket client = new Socket()) {
            client.connect(proxy.listenAddress());
            long started = System.nanoTime();
            client.getOutputStream().write(payload);
            client.shutdownOutput();
            assertArrayEquals(payload, client.getInputStream().readAllBytes());
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertTrue(elapsedMillis >= 65, "latency was not applied: " + elapsedMillis + "ms");
            assertTrue(elapsedMillis < 550, "latency accumulated once per quantum: " + elapsedMillis + "ms");
        }
    }

    @Test
    void scheduledPauseDefersForwardingWithoutDroppingBytes() throws Exception {
        NetworkFault pause = new NetworkFault("pause", NetworkDirection.CLIENT_TO_SERVER,
                NetworkFaultKind.FORWARDING_PAUSE, NetworkSemanticLayer.TCP_STREAM, 0, 180, 1);
        NetworkProfile profile = new NetworkProfile(1, "pause", 5,
                NetworkDirectionProfile.PASSTHROUGH, NetworkDirectionProfile.PASSTHROUGH, List.of(pause));
        byte[] payload = payload(4_000);
        try (EchoServer upstream = new EchoServer();
             TcpStreamProxy proxy = proxy(upstream.address(), profile, new CopyOnWriteArrayList<>()).start();
             Socket client = new Socket()) {
            client.connect(proxy.listenAddress());
            long started = System.nanoTime();
            client.getOutputStream().write(payload);
            client.shutdownOutput();
            assertArrayEquals(payload, client.getInputStream().readAllBytes());
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertTrue(elapsedMillis >= 140, "pause was not applied: " + elapsedMillis + "ms");
        }
    }

        @Test
        void probabilityZeroPauseIsNotApplied() throws Exception {
        NetworkFault pause = new NetworkFault("disabled", NetworkDirection.CLIENT_TO_SERVER,
            NetworkFaultKind.FORWARDING_PAUSE, NetworkSemanticLayer.TCP_STREAM, 0, 400, 0);
        NetworkProfile profile = new NetworkProfile(1, "disabled-pause", 5,
            NetworkDirectionProfile.PASSTHROUGH, NetworkDirectionProfile.PASSTHROUGH, List.of(pause));
        try (InteractiveEchoServer upstream = new InteractiveEchoServer();
             TcpStreamProxy proxy = proxy(upstream.address(), profile, new CopyOnWriteArrayList<>()).start();
             Socket client = new Socket()) {
            client.connect(proxy.listenAddress());
            client.setSoTimeout(1_000);
            long started = System.nanoTime();
            client.getOutputStream().write(7);
            assertEquals(7, client.getInputStream().read());
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertTrue(elapsedMillis < 250, "probability-zero pause was applied: " + elapsedMillis + "ms");
        }
        }

        @Test
        void overlappingPausesExtendToTheEndOfTheirUnion() throws Exception {
        NetworkFault first = new NetworkFault("first", NetworkDirection.CLIENT_TO_SERVER,
            NetworkFaultKind.FORWARDING_PAUSE, NetworkSemanticLayer.TCP_STREAM, 0, 120, 1);
        NetworkFault second = new NetworkFault("second", NetworkDirection.CLIENT_TO_SERVER,
            NetworkFaultKind.FORWARDING_PAUSE, NetworkSemanticLayer.TCP_STREAM, 80, 160, 1);
        NetworkProfile profile = new NetworkProfile(1, "overlap", 5,
            NetworkDirectionProfile.PASSTHROUGH, NetworkDirectionProfile.PASSTHROUGH, List.of(first, second));
        try (InteractiveEchoServer upstream = new InteractiveEchoServer();
             TcpStreamProxy proxy = proxy(upstream.address(), profile, new CopyOnWriteArrayList<>()).start();
             Socket client = new Socket()) {
            client.connect(proxy.listenAddress());
            client.setSoTimeout(1_000);
            long started = System.nanoTime();
            client.getOutputStream().write(9);
            assertEquals(9, client.getInputStream().read());
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertTrue(elapsedMillis >= 180, "overlap was not closed transitively: " + elapsedMillis + "ms");
        }
        }

    @Test
    void bandwidthLimitThrottlesButPreservesTheStream() throws Exception {
        // 64 KiB/s with an 8 KiB burst: 32 KiB needs roughly 375 ms after the initial burst.
        NetworkDirectionProfile limited = new NetworkDirectionProfile(
                0, 0, 64 * 1024 * 8L, 8 * 1024, 64 * 1024, 4 * 1024);
        NetworkProfile profile = new NetworkProfile(1, "bandwidth", 3, limited,
                NetworkDirectionProfile.PASSTHROUGH, List.of());
        byte[] payload = payload(32 * 1024);
        try (EchoServer upstream = new EchoServer();
             TcpStreamProxy proxy = proxy(upstream.address(), profile, new CopyOnWriteArrayList<>()).start();
             Socket client = new Socket()) {
            client.connect(proxy.listenAddress());
            long started = System.nanoTime();
            client.getOutputStream().write(payload);
            client.shutdownOutput();
            assertArrayEquals(payload, client.getInputStream().readAllBytes());
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertTrue(elapsedMillis >= 250, "bandwidth limit was not applied: " + elapsedMillis + "ms");
        }
    }

    @Test
    void normalFinDrainsShapingEvenWhenItExceedsShutdownTimeout() throws Exception {
        NetworkDirectionProfile limited = new NetworkDirectionProfile(
                0, 0, 64 * 1024L, 4 * 1024, 32 * 1024, 4 * 1024);
        NetworkProfile profile = new NetworkProfile(1, "slow-fin", 3, limited,
                NetworkDirectionProfile.PASSTHROUGH, List.of());
        byte[] payload = payload(8 * 1024);
        try (EchoServer upstream = new EchoServer();
             TcpStreamProxy proxy = proxy(upstream.address(), profile, new CopyOnWriteArrayList<>(),
                     Duration.ofMillis(50)).start();
             Socket client = new Socket()) {
            client.connect(proxy.listenAddress());
            client.setSoTimeout(2_000);
            client.getOutputStream().write(payload);
            client.shutdownOutput();
            assertArrayEquals(payload, client.getInputStream().readAllBytes());
            waitForNoConnections(proxy);
            assertEquals(0, proxy.snapshot().queuedBytes());
        }
    }

    @Test
    void closeCancelsAWriterInsideALongPauseWithoutLateEvents() throws Exception {
        NetworkFault pause = new NetworkFault("long", NetworkDirection.CLIENT_TO_SERVER,
                NetworkFaultKind.FORWARDING_PAUSE, NetworkSemanticLayer.TCP_STREAM, 0, 120_000, 1);
        NetworkProfile profile = new NetworkProfile(1, "long-pause", 3,
                NetworkDirectionProfile.PASSTHROUGH, NetworkDirectionProfile.PASSTHROUGH, List.of(pause));
        CopyOnWriteArrayList<NetworkEvent> events = new CopyOnWriteArrayList<>();
        try (InteractiveEchoServer upstream = new InteractiveEchoServer();
             TcpStreamProxy proxy = proxy(upstream.address(), profile, events, Duration.ofMillis(150)).start();
             Socket client = new Socket()) {
            client.connect(proxy.listenAddress());
            client.getOutputStream().write(1);
            waitForEvent(events, "QUANTUM_QUEUED");
            long started = System.nanoTime();
            proxy.close();
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertTrue(elapsedMillis < 1_000, "close waited for the pause: " + elapsedMillis + "ms");
            assertEquals(0, proxy.snapshot().activeConnections());
            assertEquals(0, proxy.snapshot().queuedBytes());
            int stopped = events.size();
            Thread.onSpinWait();
            assertEquals(stopped, events.size());
            assertEquals("PROXY_STOPPED", events.getLast().type());
        }
    }

    @Test
    void eventSinkFailureDoesNotBlockForwardingOrCleanup() throws Exception {
        AtomicBoolean sinkClosed = new AtomicBoolean();
        NetworkEventSink failingSink = new NetworkEventSink() {
            @Override public void record(NetworkEvent event) { throw new IllegalStateException("disk full"); }
            @Override public void close() { sinkClosed.set(true); }
        };
        try (InteractiveEchoServer upstream = new InteractiveEchoServer();
             TcpStreamProxy proxy = new TcpStreamProxy(new TcpStreamProxyConfig(
                     LOOPBACK, 0, upstream.address(), passthrough(), "MEASURE",
                     Duration.ofSeconds(2), Duration.ofMillis(200), failingSink)).start();
             Socket client = new Socket()) {
            client.connect(proxy.listenAddress());
            client.setSoTimeout(1_000);
            client.getOutputStream().write(33);
            assertEquals(33, client.getInputStream().read());
            proxy.close();
            assertTrue(sinkClosed.get());
            assertEquals(0, proxy.snapshot().activeConnections());
            assertTrue(proxy.snapshot().failures() >= 1);
            assertTrue(proxy.failure() instanceof IllegalStateException);
        }
    }

    @Test
    void scheduledAbortClosesAnExistingConnectionAndRecordsIt() throws Exception {
        NetworkFault abort = new NetworkFault("abort", NetworkDirection.CLIENT_TO_SERVER,
                NetworkFaultKind.CONNECTION_ABORT, NetworkSemanticLayer.TCP_STREAM, 80, 0, 1);
        NetworkProfile profile = new NetworkProfile(1, "abort", 4,
                NetworkDirectionProfile.PASSTHROUGH, NetworkDirectionProfile.PASSTHROUGH, List.of(abort));
        CopyOnWriteArrayList<NetworkEvent> events = new CopyOnWriteArrayList<>();
        try (EchoServer upstream = new EchoServer();
             TcpStreamProxy proxy = proxy(upstream.address(), profile, events).start();
             Socket client = new Socket()) {
            client.connect(proxy.listenAddress());
            client.getOutputStream().write(payload(1_000));
            waitForEvent(events, "CONNECTION_ABORTED");
            assertEquals(0, proxy.snapshot().activeConnections());
            assertEquals(1, proxy.snapshot().abortedConnections());
            assertTrue(events.stream().anyMatch(event -> event.type().equals("CONNECTION_ABORT")));
        }
    }

    @Test
    void rejectsConnectRefuseBecauseAUserSpaceProxyCannotRefuseTheTcpHandshake() throws Exception {
        NetworkFault refuse = new NetworkFault("refuse", NetworkDirection.CLIENT_TO_SERVER,
                NetworkFaultKind.CONNECT_REFUSE, NetworkSemanticLayer.TCP_STREAM, 0, 2_000, 1);
        NetworkProfile profile = new NetworkProfile(1, "refuse", 7,
                NetworkDirectionProfile.PASSTHROUGH, NetworkDirectionProfile.PASSTHROUGH, List.of(refuse));
        try (EchoServer upstream = new EchoServer()) {
            boolean rejected = false;
            try {
                proxy(upstream.address(), profile, new CopyOnWriteArrayList<>());
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            assertTrue(rejected);
        }
    }

    @Test
    void closeIsIdempotentAndStopsAcceptingConnections() throws Exception {
        try (EchoServer upstream = new EchoServer()) {
            TcpStreamProxy proxy = proxy(upstream.address(), passthrough(), new CopyOnWriteArrayList<>()).start();
            InetSocketAddress address = proxy.listenAddress();
            proxy.close();
            proxy.close();
            assertFalse(proxy.isRunning());
            assertEquals(0, proxy.snapshot().activeConnections());
            try (Socket client = new Socket()) {
                boolean refused = false;
                try {
                    client.connect(address, 200);
                } catch (Exception expected) {
                    refused = true;
                }
                assertTrue(refused);
            }
        }
    }

    @Test
    void upstreamFailureClosesOnlyThatClientAndKeepsTheListenerAlive() throws Exception {
        InetSocketAddress unavailable;
        try (ServerSocket reservation = new ServerSocket(0, 1, LOOPBACK)) {
            unavailable = (InetSocketAddress) reservation.getLocalSocketAddress();
        }
        CopyOnWriteArrayList<NetworkEvent> events = new CopyOnWriteArrayList<>();
        try (TcpStreamProxy proxy = proxy(unavailable, passthrough(), events).start();
             Socket client = new Socket()) {
            client.connect(proxy.listenAddress());
            waitForEvent(events, "CONNECTION_FAILED");
            assertTrue(proxy.isRunning());
            assertEquals(1, proxy.snapshot().failures());
            assertEquals(0, proxy.snapshot().activeConnections());
        }
    }

    private static TcpStreamProxy proxy(InetSocketAddress upstream, NetworkProfile profile,
                                        CopyOnWriteArrayList<NetworkEvent> events) {
        return proxy(upstream, profile, events, Duration.ofSeconds(2));
        }

        private static TcpStreamProxy proxy(InetSocketAddress upstream, NetworkProfile profile,
                        CopyOnWriteArrayList<NetworkEvent> events, Duration shutdownTimeout) {
        return new TcpStreamProxy(new TcpStreamProxyConfig(
            LOOPBACK, 0, upstream, profile, "MEASURE", Duration.ofSeconds(2), shutdownTimeout, events::add));
    }

    private static NetworkProfile passthrough() {
        return new NetworkProfile(1, "passthrough", 1,
                NetworkDirectionProfile.PASSTHROUGH, NetworkDirectionProfile.PASSTHROUGH, List.of());
    }

    private static byte[] payload(int size) {
        byte[] bytes = new byte[size];
        for (int index = 0; index < bytes.length; index++) bytes[index] = (byte) (index * 31 + 7);
        return bytes;
    }

    private static void waitForNoConnections(TcpStreamProxy proxy) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (proxy.snapshot().activeConnections() != 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(0, proxy.snapshot().activeConnections());
    }

    private static void waitForEvent(List<NetworkEvent> events, String type) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (events.stream().noneMatch(event -> event.type().equals(type)) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(events.stream().anyMatch(event -> event.type().equals(type)), "missing event " + type);
    }

    private static final class EchoServer implements AutoCloseable {
        private final ServerSocket server;
        private final Thread thread;

        private EchoServer() throws Exception {
            server = new ServerSocket(0, 16, LOOPBACK);
            thread = Thread.ofVirtual().name("modbench-test-echo").start(this::run);
        }

        InetSocketAddress address() {
            return (InetSocketAddress) server.getLocalSocketAddress();
        }

        private void run() {
            try {
                while (!server.isClosed()) {
                    Socket socket = server.accept();
                    Thread.ofVirtual().start(() -> echo(socket));
                }
            } catch (Exception ignored) {}
        }

        private static void echo(Socket socket) {
            try (socket; ByteArrayOutputStream received = new ByteArrayOutputStream()) {
                socket.getInputStream().transferTo(received);
                socket.getOutputStream().write(received.toByteArray());
            } catch (Exception ignored) {}
        }

        @Override
        public void close() throws Exception {
            server.close();
            thread.join(1_000);
        }
    }

    private static final class InteractiveEchoServer implements AutoCloseable {
        private final ServerSocket server;
        private final Thread thread;

        private InteractiveEchoServer() throws Exception {
            server = new ServerSocket(0, 16, LOOPBACK);
            thread = Thread.ofVirtual().name("modbench-test-interactive-echo").start(this::run);
        }

        InetSocketAddress address() {
            return (InetSocketAddress) server.getLocalSocketAddress();
        }

        private void run() {
            try (Socket socket = server.accept()) {
                int value;
                while ((value = socket.getInputStream().read()) >= 0) {
                    socket.getOutputStream().write(value);
                    socket.getOutputStream().flush();
                }
            } catch (Exception ignored) {}
        }

        @Override
        public void close() throws Exception {
            server.close();
            thread.join(1_000);
        }
    }
}