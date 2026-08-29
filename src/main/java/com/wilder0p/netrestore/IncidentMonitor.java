package com.wilder0p.netrestore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class IncidentMonitor {

    enum State { IDLE, INCIDENT, RECOVERY }

    private final NetRestore plugin;
    private final ProbeService probe = new ProbeService();
    private final Map<UUID, Double> pingEwma = new ConcurrentHashMap<>();
    private final Deque<Long> quitTimes = new ArrayDeque<>();
    private final Deque<Double> tickMs = new ArrayDeque<>();
    private final AtomicLong incidentId = new AtomicLong();
    private final AtomicInteger lastSpiked = new AtomicInteger();

    private volatile State state = State.IDLE;
    private volatile long currentIncidentId;
    private volatile long hotUntil;
    private volatile String lastReason = "idle";

    private BukkitTask probeTask;
    private BukkitTask pingTask;
    private BukkitTask tickTask;

    IncidentMonitor(NetRestore plugin) {
        this.plugin = plugin;
        reload();
    }

    void reload() {
        var cfg = plugin.getConfig();
        probe.configure(
                cfg.getStringList("probe.targets"),
                cfg.getInt("probe.timeout-ms", 1000),
                cfg.getDouble("probe.spike-multiplier", 3.0),
                cfg.getLong("probe.absolute-ms", 250),
                cfg.getInt("probe.fail-streak", 2),
                cfg.getInt("probe.baseline-samples", 40)
        );
    }

    void start() {
        stop();
        long probeTicks = Math.max(20L, plugin.getConfig().getLong("probe.interval-seconds", 3) * 20L);
        long pingTicks = Math.max(20L, plugin.getConfig().getLong("crowd-ping.interval-seconds", 1) * 20L);

        if (plugin.getConfig().getBoolean("probe.enabled", true)) {
            probeTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, probe::probeOnce, 40L, probeTicks);
        }
        pingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCrowdAndState, 40L, pingTicks);
        if (plugin.getConfig().getBoolean("mspt.enabled", true)) {
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sampleMspt, 1L, 1L);
        }
    }

    void stop() {
        if (probeTask != null) {
            probeTask.cancel();
            probeTask = null;
        }
        if (pingTask != null) {
            pingTask.cancel();
            pingTask = null;
        }
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    void recordQuit() {
        long now = System.currentTimeMillis();
        synchronized (quitTimes) {
            quitTimes.addLast(now);
            long window = plugin.getConfig().getLong("disconnects.window-seconds", 8) * 1000L;
            while (!quitTimes.isEmpty() && now - quitTimes.peekFirst() > window) {
                quitTimes.removeFirst();
            }
        }
    }

    boolean isHot() {
        return state != State.IDLE || System.currentTimeMillis() < hotUntil;
    }

    long currentIncidentId() {
        return currentIncidentId;
    }

    State state() {
        return state;
    }

    String lastReason() {
        return lastReason;
    }

    long lastProbeRtt() {
        return probe.lastRtt();
    }

    long probeBaseline() {
        return probe.baseline();
    }

    int lastSpikedCount() {
        return lastSpiked.get();
    }

    private void tickCrowdAndState() {
        boolean crowd = crowdPingBad();
        boolean disc = disconnectBad();
        boolean probeBad = plugin.getConfig().getBoolean("probe.enabled", true) && probe.isBad();
        boolean msptBad = plugin.getConfig().getBoolean("mspt.enabled", true) && msptBad();

        int signals = 0;
        StringBuilder reason = new StringBuilder();
        if (probeBad) {
            signals++;
            reason.append("probe=").append(probe.lastRtt()).append("ms ");
        }
        if (crowd) {
            signals++;
            reason.append("crowd-ping=").append(lastSpiked.get()).append(" ");
        }
        if (disc) {
            signals++;
            reason.append("disconnect-burst ");
        }
        if (msptBad) {
            signals++;
            reason.append("mspt ");
        }

        boolean twoSignals = signals >= 2;
        long graceMs = plugin.getConfig().getLong("incident.grace-seconds", 12) * 1000L;
        long now = System.currentTimeMillis();

        if (twoSignals) {
            if (state != State.INCIDENT) {
                currentIncidentId = incidentId.incrementAndGet();
                lastReason = reason.toString().trim();
                plugin.audit().log("INCIDENT #" + currentIncidentId + " start: " + lastReason);
                alertStaff("Network incident #" + currentIncidentId + " started (" + lastReason + ")");
            }
            state = State.INCIDENT;
            hotUntil = now + graceMs;
        } else if (state == State.INCIDENT) {
            state = State.RECOVERY;
            hotUntil = now + graceMs;
            lastReason = "recovery " + reason;
            plugin.audit().log("INCIDENT #" + currentIncidentId + " recovery");
        } else if (state == State.RECOVERY && now >= hotUntil) {
            plugin.audit().log("INCIDENT #" + currentIncidentId + " idle");
            state = State.IDLE;
            lastReason = "idle";
        }
    }

    private boolean crowdPingBad() {
        if (!plugin.getConfig().getBoolean("crowd-ping.enabled", true)) {
            return false;
        }
        var online = Bukkit.getOnlinePlayers();
        int minOnline = plugin.getConfig().getInt("incident.min-online-for-crowd", 3);
        if (online.size() < minOnline) {
            lastSpiked.set(0);
            return false;
        }
        double delta = plugin.getConfig().getDouble("crowd-ping.spike-delta-ms", 180);
        double soloHigh = plugin.getConfig().getDouble("crowd-ping.solo-high-ms", 250);
        int spiked = 0;
        int considered = 0;
        for (Player player : online) {
            int ping = player.getPing();
            Double prev = pingEwma.get(player.getUniqueId());
            if (prev == null) {
                pingEwma.put(player.getUniqueId(), (double) ping);
                continue;
            }
            considered++;
            double ewma = prev * 0.8 + ping * 0.2;
            pingEwma.put(player.getUniqueId(), ewma);
            boolean jumped = ping >= prev + delta && ping >= soloHigh;
            if (jumped) {
                spiked++;
            }
        }
        lastSpiked.set(spiked);
        if (considered == 0) {
            return false;
        }
        int minCount = plugin.getConfig().getInt("crowd-ping.min-spiked-players", 3);
        double minPct = plugin.getConfig().getDouble("crowd-ping.min-spiked-percent", 35) / 100.0;
        return spiked >= minCount && spiked >= Math.ceil(online.size() * minPct);
    }

    private boolean disconnectBad() {
        if (!plugin.getConfig().getBoolean("disconnects.enabled", true)) {
            return false;
        }
        int min = plugin.getConfig().getInt("disconnects.min-quits", 3);
        synchronized (quitTimes) {
            long now = System.currentTimeMillis();
            long window = plugin.getConfig().getLong("disconnects.window-seconds", 8) * 1000L;
            while (!quitTimes.isEmpty() && now - quitTimes.peekFirst() > window) {
                quitTimes.removeFirst();
            }
            return quitTimes.size() >= min;
        }
    }

    private void sampleMspt() {
        try {
            long[] times = Bukkit.getServer().getTickTimes();
            if (times.length == 0) {
                return;
            }
            double last = times[times.length - 1] / 1_000_000.0;
            tickMs.addLast(last);
            int window = plugin.getConfig().getInt("mspt.window-ticks", 40);
            while (tickMs.size() > window) {
                tickMs.removeFirst();
            }
        } catch (NoSuchMethodError ignored) {
            // Older API fallback: ignore MSPT sampling
        }
    }

    private boolean msptBad() {
        if (tickMs.isEmpty()) {
            try {
                return Bukkit.getAverageTickTime() >= plugin.getConfig().getDouble("mspt.average-ms", 70);
            } catch (NoSuchMethodError ignored) {
                return false;
            }
        }
        double last = tickMs.peekLast();
        double avg = 0;
        for (double v : tickMs) {
            avg += v;
        }
        avg /= tickMs.size();
        return last >= plugin.getConfig().getDouble("mspt.spike-ms", 150)
                || avg >= plugin.getConfig().getDouble("mspt.average-ms", 70);
    }

    private void alertStaff(String message) {
        if (!plugin.getConfig().getBoolean("staff-alerts", true)) {
            return;
        }
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("netrestore.admin"))
                .forEach(p -> p.sendMessage(Messages.prefix().append(net.kyori.adventure.text.Component.text(message))));
    }

    void clearPlayer(UUID id) {
        pingEwma.remove(id);
    }
}
