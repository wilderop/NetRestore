package com.wilder0p.netrestore;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class SessionTracker implements Listener {

    private final NetRestore plugin;
    private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();

    SessionTracker(NetRestore plugin) {
        this.plugin = plugin;
    }

    void seedOnline() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            joinedAt.putIfAbsent(player.getUniqueId(), now);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        joinedAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        joinedAt.remove(player.getUniqueId());
        plugin.incidents().recordQuit();
        plugin.incidents().clearPlayer(player.getUniqueId());
        plugin.combat().clear(player.getUniqueId());
    }

    long sessionSeconds(Player player) {
        Long start = joinedAt.get(player.getUniqueId());
        if (start == null) {
            return 0;
        }
        return Math.max(0, (System.currentTimeMillis() - start) / 1000L);
    }
}
