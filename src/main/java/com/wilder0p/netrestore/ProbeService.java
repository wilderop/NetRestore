package com.wilder0p.netrestore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class ProbeService {

    record Target(String host, int port) {}

    private final Deque<Long> samples = new ArrayDeque<>();
    private final AtomicLong lastRtt = new AtomicLong(-1);
    private final AtomicInteger failStreak = new AtomicInteger();
    private volatile boolean bad;
    private List<Target> targets = List.of();
    private int timeoutMs = 1000;
    private double multiplier = 3.0;
    private long absoluteMs = 250;
    private int failNeed = 2;
    private int baselineSamples = 40;
    private int targetIndex;

    void configure(List<String> rawTargets, int timeoutMs, double multiplier, long absoluteMs, int failNeed, int baselineSamples) {
        List<Target> parsed = new ArrayList<>();
        for (String raw : rawTargets) {
            Target t = parse(raw);
            if (t != null) {
                parsed.add(t);
            }
        }
        this.targets = parsed;
        this.timeoutMs = Math.max(200, timeoutMs);
        this.multiplier = Math.max(1.5, multiplier);
        this.absoluteMs = Math.max(80, absoluteMs);
        this.failNeed = Math.max(1, failNeed);
        this.baselineSamples = Math.max(10, baselineSamples);
    }

    long probeOnce() {
        if (targets.isEmpty()) {
            bad = false;
            return -1;
        }
        Target target = targets.get(Math.floorMod(targetIndex++, targets.size()));
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target.host(), target.port()), timeoutMs);
            long rtt = Math.max(1, (System.nanoTime() - start) / 1_000_000L);
            recordSuccess(rtt);
            return rtt;
        } catch (IOException ex) {
            recordFailure();
            return -1;
        }
    }

    private synchronized void recordSuccess(long rtt) {
        lastRtt.set(rtt);
        failStreak.set(0);
        samples.addLast(rtt);
        while (samples.size() > baselineSamples) {
            samples.removeFirst();
        }
        long baseline = baseline();
        long threshold = Math.max(absoluteMs, (long) (baseline * multiplier));
        bad = rtt >= threshold && samples.size() >= Math.min(8, baselineSamples / 2);
    }

    private synchronized void recordFailure() {
        lastRtt.set(-1);
        int streak = failStreak.incrementAndGet();
        bad = streak >= failNeed;
    }

    synchronized boolean isBad() {
        return bad;
    }

    long lastRtt() {
        return lastRtt.get();
    }

    synchronized long baseline() {
        if (samples.isEmpty()) {
            return absoluteMs;
        }
        long sum = 0;
        for (long sample : samples) {
            sum += sample;
        }
        return Math.max(1, sum / samples.size());
    }

    private static Target parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("127.") || value.startsWith("localhost") || value.startsWith("0.0.0.0")) {
            return null;
        }
        String host = value;
        int port = 443;
        int colon = value.lastIndexOf(':');
        if (colon > 0) {
            try {
                port = Integer.parseInt(value.substring(colon + 1));
                host = value.substring(0, colon);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return new Target(host, port);
    }
}
