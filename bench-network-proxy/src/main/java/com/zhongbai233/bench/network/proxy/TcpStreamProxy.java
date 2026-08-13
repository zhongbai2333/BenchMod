package com.zhongbai233.bench.network.proxy;

import com.zhongbai233.bench.network.NetworkBackendCapabilities;
import com.zhongbai233.bench.network.NetworkDirection;
import com.zhongbai233.bench.network.NetworkDirectionProfile;
import com.zhongbai233.bench.network.NetworkFault;
import com.zhongbai233.bench.network.NetworkFaultKind;
import com.zhongbai233.bench.network.NetworkSeedDerivation;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Cross-platform TCP byte-stream proxy with deterministic, direction-aware traffic shaping.
 * It never drops, duplicates, or reorders bytes while a connection remains open.
 */
public final class TcpStreamProxy implements AutoCloseable {
    private static final int STREAM_IDLE_FLUSH_MILLIS = 5;
    private final TcpStreamProxyConfig config;
    private final ProxyMetrics metrics = new ProxyMetrics();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean eventSinkFailed = new AtomicBoolean();
    private final AtomicLong connectionIds = new AtomicLong();
    private final Set<ProxyConnection> connections = ConcurrentHashMap.newKeySet();
    private final Set<Socket> pendingSockets = ConcurrentHashMap.newKeySet();
    private volatile ServerSocket listener;
    private volatile Thread acceptThread;
    private volatile Throwable failure;
    private volatile long activatedNanos;

    public TcpStreamProxy(TcpStreamProxyConfig config) {
        this.config = config;
        config.profile().requireSupportedBy(NetworkBackendCapabilities.tcpStreamProxy());
    }

    /** Binds the listener and starts accepting connections. Calling twice is an error. */
    public synchronized TcpStreamProxy start() throws IOException {
        if (!running.compareAndSet(false, true)) throw new IllegalStateException("Proxy is already running");
        try {
            activatedNanos = System.nanoTime();
            listener = new ServerSocket();
            listener.bind(new InetSocketAddress(config.listenAddress(), config.listenPort()));
            emit(0, null, "PROXY_STARTED", 0, 0, listenAddress().toString());
            acceptThread = Thread.ofVirtual().name("modbench-proxy-accept").start(this::acceptLoop);
            return this;
        } catch (IOException | RuntimeException exception) {
            running.set(false);
            closeListener();
            throw exception;
        }
    }

    public InetSocketAddress listenAddress() {
        ServerSocket socket = listener;
        if (socket == null || !socket.isBound()) throw new IllegalStateException("Proxy is not started");
        return (InetSocketAddress) socket.getLocalSocketAddress();
    }

    public boolean isRunning() { return running.get(); }
    public Throwable failure() { return failure; }
    public ProxyMetricsSnapshot snapshot() { return metrics.snapshot(); }

    private void acceptLoop() {
        try {
            while (running.get()) {
                Socket client = listener.accept();
                client.setTcpNoDelay(true);
                pendingSockets.add(client);
                if (!running.get()) {
                    closeSocket(client);
                    pendingSockets.remove(client);
                    return;
                }
                long id = connectionIds.incrementAndGet();
                Socket upstream = new Socket();
                pendingSockets.add(upstream);
                try {
                    if (!running.get()) throw new SocketException("Proxy is stopping");
                    upstream.setTcpNoDelay(true);
                    upstream.connect(config.upstreamAddress(), Math.toIntExact(config.connectTimeout().toMillis()));
                } catch (IOException exception) {
                    closeSocket(upstream);
                    closeSocket(client);
                    if (running.get()) {
                        metrics.failures.incrementAndGet();
                        emit(id, null, "CONNECTION_FAILED", 0, metrics.queued.get(), exception.toString());
                    }
                    pendingSockets.remove(upstream);
                    pendingSockets.remove(client);
                    continue;
                }
                pendingSockets.remove(upstream);
                pendingSockets.remove(client);
                if (!running.get()) {
                    closeSocket(upstream);
                    closeSocket(client);
                    return;
                }
                ProxyConnection connection = new ProxyConnection(id, client, upstream);
                if (!register(connection)) return;
            }
        } catch (SocketException exception) {
            if (running.get()) fail(exception);
        } catch (Throwable exception) {
            fail(exception);
        }
    }

    private void fail(Throwable exception) {
        failure = exception;
        metrics.failures.incrementAndGet();
        emit(0, null, "PROXY_FAILED", 0, metrics.queued.get(), exception.toString());
        close();
    }

    private synchronized boolean register(ProxyConnection connection) throws IOException {
        if (!running.get()) {
            closeSocket(connection.client);
            closeSocket(connection.upstream);
            return false;
        }
        connections.add(connection);
        metrics.accepted.incrementAndGet();
        metrics.active.incrementAndGet();
        emit(connection.id, null, "CONNECTION_OPENED", 0, 0, config.upstreamAddress().toString());
        try {
            connection.start();
            return true;
        } catch (IOException | RuntimeException exception) {
            connection.close(CloseReason.ABORTED, "connection startup failure");
            throw exception;
        }
    }

    @Override
    public synchronized void close() {
        if (!running.getAndSet(false) && listener == null) return;
        closeListener();
        pendingSockets.forEach(TcpStreamProxy::closeSocket);
        pendingSockets.clear();
        interrupt(acceptThread);
        List<ProxyConnection> closing = List.copyOf(connections);
        closing.forEach(connection -> connection.close(CloseReason.SHUTDOWN, "proxy shutdown"));
        long deadline = System.nanoTime() + config.shutdownTimeout().toNanos();
        closing.forEach(connection -> connection.joinThreads(deadline));
        connections.clear();
        joinUntil(acceptThread, deadline);
        boolean terminated = !alive(acceptThread) && closing.stream().allMatch(ProxyConnection::threadsTerminated);
        if (terminated) {
            emit(0, null, "PROXY_STOPPED", 0, metrics.queued.get(),
                    "profileSha256=" + config.profile().sha256());
        } else {
            IllegalStateException exception = new IllegalStateException(
                    "TCP proxy shutdown deadline elapsed with background threads still running");
            metrics.failures.incrementAndGet();
            if (failure == null) failure = exception;
        }
        try {
            config.eventSink().close();
        } catch (Exception exception) {
            metrics.failures.incrementAndGet();
            if (failure == null) failure = exception;
        }
        listener = null;
    }

    private void closeListener() {
        try {
            if (listener != null) listener.close();
        } catch (IOException ignored) {}
    }

    private final class ProxyConnection {
        private final long id;
        private final Socket client;
        private final Socket upstream;
        private final long createdNanos = System.nanoTime();
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicLong livePumps = new AtomicLong(2);
        private volatile Thread c2sThread;
        private volatile Thread s2cThread;
        private volatile Thread faultThread;
        private volatile DirectionPump c2sPump;
        private volatile DirectionPump s2cPump;

        private ProxyConnection(long id, Socket client, Socket upstream) {
            this.id = id;
            this.client = client;
            this.upstream = upstream;
        }

        void start() throws IOException {
            c2sPump = new DirectionPump(this, id, NetworkDirection.CLIENT_TO_SERVER,
                client, client.getInputStream(), upstream.getOutputStream(), upstream,
                    config.profile().clientToServer());
            s2cPump = new DirectionPump(this, id, NetworkDirection.SERVER_TO_CLIENT,
                upstream, upstream.getInputStream(), client.getOutputStream(), client,
                    config.profile().serverToClient());
            c2sThread = Thread.ofVirtual().name("modbench-proxy-c2s-" + id).start(() -> runPump(c2sPump));
            s2cThread = Thread.ofVirtual().name("modbench-proxy-s2c-" + id).start(() -> runPump(s2cPump));
            faultThread = Thread.ofVirtual().name("modbench-proxy-fault-" + id).start(this::runFaults);
        }

        private void runPump(DirectionPump pump) {
            try {
                pump.run();
            } catch (Throwable exception) {
                if (open.get() && !(exception instanceof SocketException)) directionFailed(pump.direction, exception);
            } finally {
                if (livePumps.decrementAndGet() == 0) close(CloseReason.COMPLETED, "streams completed");
            }
        }

        private void directionFailed(NetworkDirection direction, Throwable exception) {
            if (!open.get()) return;
            metrics.failures.incrementAndGet();
            emit(id, direction, "DIRECTION_FAILED", 0, metrics.queued.get(), exception.toString());
            close(CloseReason.ABORTED, "direction failure");
        }

        private void runFaults() {
            for (NetworkFault fault : config.profile().scheduledFaults()) {
                if (fault.kind() == NetworkFaultKind.CONNECT_REFUSE || !selected(fault, id)) continue;
                long faultNanos = activatedNanos + fault.startOffsetMillis() * 1_000_000;
                if (createdNanos >= faultNanos) continue;
                if (System.nanoTime() < faultNanos && !parkUntil(faultNanos)) return;
                if (!open.get()) return;
                if (fault.kind() == NetworkFaultKind.CONNECTION_ABORT) {
                    emit(id, fault.direction(), "CONNECTION_ABORT", 0, metrics.queued.get(), fault.id());
                    close(CloseReason.ABORTED, fault.id());
                    return;
                }
            }
        }

        void close(CloseReason closeReason, String reason) {
            if (!open.compareAndSet(true, false)) return;
            if (closeReason == CloseReason.ABORTED) {
                setAbortive(client);
                setAbortive(upstream);
            }
            try { client.close(); } catch (IOException ignored) {}
            try { upstream.close(); } catch (IOException ignored) {}
            DirectionPump first = c2sPump;
            DirectionPump second = s2cPump;
            if (first != null) first.cancel();
            if (second != null) second.cancel();
            interrupt(c2sThread);
            interrupt(s2cThread);
            interrupt(faultThread);
            connections.remove(this);
            metrics.active.decrementAndGet();
            if (closeReason == CloseReason.ABORTED) metrics.aborted.incrementAndGet();
            else if (closeReason == CloseReason.COMPLETED) metrics.completed.incrementAndGet();
            emit(id, null, closeReason == CloseReason.ABORTED ? "CONNECTION_ABORTED"
                            : closeReason == CloseReason.COMPLETED ? "CONNECTION_CLOSED" : "CONNECTION_CANCELLED",
                    0, metrics.queued.get(), reason);
        }

        void joinThreads(long deadlineNanos) {
            joinUntil(c2sThread, deadlineNanos);
            joinUntil(s2cThread, deadlineNanos);
            joinUntil(faultThread, deadlineNanos);
            DirectionPump first = c2sPump;
            DirectionPump second = s2cPump;
            if (first != null) first.joinWriter(deadlineNanos);
            if (second != null) second.joinWriter(deadlineNanos);
        }

        boolean threadsTerminated() {
            DirectionPump first = c2sPump;
            DirectionPump second = s2cPump;
            return !alive(c2sThread) && !alive(s2cThread) && !alive(faultThread)
                && (first == null || first.writerTerminated())
                && (second == null || second.writerTerminated());
        }
    }

    private enum CloseReason { COMPLETED, ABORTED, SHUTDOWN }

    private final class DirectionPump {
        private static final QueuedChunk EOF = new QueuedChunk(new byte[0], 0, 0, 0);
        private final long connectionId;
        private final ProxyConnection connection;
        private final NetworkDirection direction;
        private final Socket sourceSocket;
        private final InputStream source;
        private final OutputStream target;
        private final Socket targetSocket;
        private final NetworkDirectionProfile shaping;
        private final LinkedBlockingQueue<QueuedChunk> queue;
        private final long jitterSeed;
        private volatile Thread writer;

        private DirectionPump(ProxyConnection connection, long connectionId, NetworkDirection direction,
                      Socket sourceSocket, InputStream source,
                      OutputStream target, Socket targetSocket, NetworkDirectionProfile shaping) {
            this.connection = connection;
            this.connectionId = connectionId;
            this.direction = direction;
            this.sourceSocket = sourceSocket;
            this.source = source;
            this.target = target;
            this.targetSocket = targetSocket;
            this.shaping = shaping;
            int capacity = Math.toIntExact(shaping.maxQueueBytes() / shaping.streamQuantumBytes());
            queue = new LinkedBlockingQueue<>(capacity);
            jitterSeed = NetworkSeedDerivation.derive(config.profile(), direction, config.phase(), "jitter");
        }

        void run() throws Exception {
            sourceSocket.setSoTimeout(STREAM_IDLE_FLUSH_MILLIS);
            writer = Thread.ofVirtual().name("modbench-proxy-writer-" + connectionId + "-" + direction)
                    .start(() -> {
                        try {
                            writeLoop();
                        } catch (Throwable exception) {
                            if (connection.open.get()) connection.directionFailed(direction, exception);
                        }
                    });
            long offset = 0;
            byte[] buffer = new byte[shaping.streamQuantumBytes()];
            int buffered = 0;
            try {
                while (running.get() && connection.open.get()) {
                    int read;
                    try {
                        read = source.read(buffer, buffered, buffer.length - buffered);
                    } catch (SocketTimeoutException timeout) {
                        if (buffered == 0) continue;
                        offset = queueQuantum(buffer, buffered, offset);
                        buffer = new byte[shaping.streamQuantumBytes()];
                        buffered = 0;
                        continue;
                    }
                    if (read < 0) break;
                    if (read == 0) continue;
                    readCounter().addAndGet(read);
                    buffered += read;
                    if (buffered < buffer.length) continue;
                    offset = queueQuantum(buffer, buffered, offset);
                    buffer = new byte[shaping.streamQuantumBytes()];
                    buffered = 0;
                }
                if (buffered > 0) {
                    offset = queueQuantum(buffer, buffered, offset);
                }
            } finally {
                if (connection.open.get()) {
                    queue.put(EOF);
                    writer.join();
                } else {
                    cancel();
                    joinUntil(writer, System.nanoTime() + config.shutdownTimeout().toNanos());
                }
            }
        }

        void cancel() {
            interrupt(writer);
            discardQueued();
        }

        boolean writerTerminated() {
            return !alive(writer);
        }

        void joinWriter(long deadlineNanos) {
            joinUntil(writer, deadlineNanos);
        }

        private long queueQuantum(byte[] buffer, int length, long offset) throws InterruptedException {
                    byte[] bytes = java.util.Arrays.copyOf(buffer, length);
                    long sequence = offset / shaping.streamQuantumBytes();
                    long ingress = System.nanoTime();
                    long jitter = DeterministicJitter.millis(jitterSeed, sequence, shaping.jitterMillis());
                    long delayMillis = Math.max(0, shaping.baseLatencyMillis() + jitter);
                    metrics.queue(length);
                    boolean inserted = false;
                    try {
                        queue.put(new QueuedChunk(bytes, offset, ingress, ingress + delayMillis * 1_000_000));
                        inserted = true;
                    } finally {
                        if (!inserted) metrics.dequeue(length);
                    }
                    emit(connectionId, direction, "QUANTUM_QUEUED", offset, metrics.queued.get(),
                            "delayMillis=" + delayMillis);
                    return offset + length;
        }

        private void writeLoop() throws IOException, InterruptedException {
            BandwidthGate bandwidth = new BandwidthGate(
                    shaping.bandwidthBitsPerSecond(), shaping.burstBytes(), System.nanoTime());
            while (true) {
                QueuedChunk chunk = queue.take();
                if (chunk == EOF) {
                    target.flush();
                    if (!targetSocket.isOutputShutdown()) targetSocket.shutdownOutput();
                    return;
                }
                metrics.dequeue(chunk.bytes.length);
                long eligible = Math.max(chunk.deadlineNanos,
                        bandwidth.reserve(chunk.bytes.length, chunk.deadlineNanos));
                while (true) {
                    eligible = Math.max(eligible, pauseEndNanos(direction, connectionId, eligible));
                    if (!parkUntil(eligible)) throw new InterruptedException("Direction writer cancelled");
                    long now = System.nanoTime();
                    long revised = pauseEndNanos(direction, connectionId, now);
                    if (revised <= now) break;
                    eligible = revised;
                }
                target.write(chunk.bytes);
                target.flush();
                forwardedCounter().addAndGet(chunk.bytes.length);
                metrics.quanta.incrementAndGet();
                emit(connectionId, direction, "QUANTUM_FORWARDED", chunk.streamOffset,
                        metrics.queued.get(), "delayNanos=" + (System.nanoTime() - chunk.ingressNanos));
            }
        }

        private void discardQueued() {
            QueuedChunk chunk;
            while ((chunk = queue.poll()) != null) {
                if (chunk != EOF) metrics.dequeue(chunk.bytes.length);
            }
        }

        private AtomicLong readCounter() {
            return direction == NetworkDirection.CLIENT_TO_SERVER ? metrics.c2sRead : metrics.s2cRead;
        }

        private AtomicLong forwardedCounter() {
            return direction == NetworkDirection.CLIENT_TO_SERVER ? metrics.c2sForwarded : metrics.s2cForwarded;
        }
    }

    private record QueuedChunk(byte[] bytes, long streamOffset, long ingressNanos, long deadlineNanos) {}

    private long pauseEndNanos(NetworkDirection direction, long connectionId, long candidateNanos) {
        long endNanos = candidateNanos;
        boolean extended;
        do {
            extended = false;
            long candidateMillis = Math.max(0, (endNanos - activatedNanos) / 1_000_000);
            for (NetworkFault fault : selectedFaults(NetworkFaultKind.FORWARDING_PAUSE, direction)) {
                if (!selected(fault, connectionId)) continue;
                long faultEndMillis = fault.startOffsetMillis() + fault.durationMillis();
                if (candidateMillis >= fault.startOffsetMillis() && candidateMillis < faultEndMillis) {
                    long faultEndNanos = activatedNanos + faultEndMillis * 1_000_000;
                    if (faultEndNanos > endNanos) {
                        endNanos = faultEndNanos;
                        extended = true;
                    }
                }
            }
        } while (extended);
        return endNanos;
    }

    private List<NetworkFault> selectedFaults(NetworkFaultKind kind, NetworkDirection direction) {
        return config.profile().scheduledFaults().stream()
                .filter(fault -> fault.kind() == kind && (direction == null || fault.direction() == direction))
                .toList();
    }

    private boolean selected(NetworkFault fault, long connectionId) {
        long seed = NetworkSeedDerivation.derive(config.profile(), fault.direction(), config.phase(),
                fault.id() + "/connection/" + connectionId);
        return DeterministicJitter.selected(seed, fault.probability());
    }

    private long elapsedNanos() { return Math.max(0, System.nanoTime() - activatedNanos); }

    private void emit(long connectionId, NetworkDirection direction, String type,
                      long streamOffset, long queueBytes, String detail) {
        try {
            config.eventSink().record(new NetworkEvent(
                    elapsedNanos(), connectionId, direction, type, streamOffset, queueBytes, detail));
        } catch (RuntimeException exception) {
            if (eventSinkFailed.compareAndSet(false, true)) {
                metrics.failures.incrementAndGet();
                if (failure == null) failure = exception;
            }
        }
    }

    private static boolean parkUntil(long deadlineNanos) {
        while (true) {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) return true;
            LockSupport.parkNanos(remaining);
            if (Thread.interrupted()) return false;
        }
    }

    private static void closeSocket(Socket socket) {
        try { socket.close(); } catch (IOException ignored) {}
    }

    private static void setAbortive(Socket socket) {
        try { socket.setSoLinger(true, 0); } catch (SocketException ignored) {}
    }

    private static void interrupt(Thread thread) {
        if (thread != null && thread != Thread.currentThread()) thread.interrupt();
    }

    private static boolean alive(Thread thread) {
        return thread != null && thread.isAlive();
    }

    private static void joinUntil(Thread thread, long deadlineNanos) {
        if (thread == null || thread == Thread.currentThread()) return;
        try {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining > 0) thread.join(Duration.ofNanos(remaining));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
