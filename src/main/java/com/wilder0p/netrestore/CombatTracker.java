package com.wilder0p.netrestore;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class CombatTracker implements Listener {

    private final NetRestore plugin;
    private final Map<UUID, Long> lastPlayerHit = new ConcurrentHashMap<>();

    CombatTracker(NetRestore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        long now = System.currentTimeMillis();
        lastPlayerHit.put(victim.getUniqueId(), now);
        lastPlayerHit.put(attacker.getUniqueId(), now);
    }

    boolean tagged(Player player) {
        Long last = lastPlayerHit.get(player.getUniqueId());
        if (last == null) {
            return false;
        }
        long tagMs = plugin.getConfig().getLong("offers.combat-tag-seconds", 20) * 1000L;
        return System.currentTimeMillis() - last <= tagMs;
    }

    void clear(UUID id) {
        lastPlayerHit.remove(id);
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
