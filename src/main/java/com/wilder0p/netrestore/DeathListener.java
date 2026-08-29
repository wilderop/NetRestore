package com.wilder0p.netrestore;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

final class DeathListener implements Listener {

    private final NetRestore plugin;

    DeathListener(NetRestore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        String deny = eligibility(player, event);
        if (deny != null) {
            plugin.audit().log(player.getName() + " death not offered: " + deny
                    + " cause=" + causeName(event) + " hot=" + plugin.incidents().isHot());
            return;
        }

        InventorySnapshot snapshot = InventorySnapshot.capture(player);
        long now = System.currentTimeMillis();
        long expire = now + plugin.getConfig().getLong("offers.expire-seconds", 300) * 1000L;
        String killer = player.getKiller() != null ? player.getKiller().getName() : "-";

        RestoreOffer offer = new RestoreOffer(
                player.getUniqueId(),
                player.getName(),
                plugin.incidents().currentIncidentId(),
                now,
                expire,
                player.getLocation(),
                causeName(event),
                killer,
                plugin.incidents().lastProbeRtt(),
                plugin.incidents().lastSpikedCount(),
                snapshot
        );
        plugin.offers().put(offer);

        event.getDrops().clear();
        event.setKeepLevel(plugin.getConfig().getBoolean("offers.restore-xp", true));
        event.setDroppedExp(0);

        plugin.audit().log("OFFER " + player.getName()
                + " incident=#" + offer.incidentId
                + " items=" + snapshot.itemCount
                + " cause=" + offer.cause
                + " loc=" + offer.shortLoc()
                + " probe=" + offer.probeRtt + "ms");
        alertStaff(player.getName() + " died during incident #" + offer.incidentId
                + " (" + offer.cause + ", " + snapshot.itemCount + " stacks). Offer issued.");
    }

    String eligibility(Player player, PlayerDeathEvent event) {
        if (!plugin.incidents().isHot()) {
            return "no-incident";
        }
        long incidentId = plugin.incidents().currentIncidentId();
        if (plugin.offers().alreadyOfferedThisIncident(player.getUniqueId(), incidentId)) {
            return "already-offered-this-incident";
        }
        if (plugin.offers().onCooldown(player)) {
            return "cooldown";
        }
        long minSession = plugin.getConfig().getLong("offers.min-session-seconds", 45);
        if (plugin.sessions().sessionSeconds(player) < minSession) {
            return "session-too-short";
        }

        String cause = causeName(event);
        if (plugin.getConfig().getStringList("denied-causes").contains(cause)) {
            return "denied-cause";
        }
        var allowed = plugin.getConfig().getStringList("allowed-causes");
        if (!allowed.isEmpty() && !allowed.contains(cause)) {
            return "cause-not-allowed";
        }

        if (plugin.getConfig().getBoolean("offers.deny-pvp", true)) {
            if (player.getKiller() != null && !player.getKiller().getUniqueId().equals(player.getUniqueId())) {
                return "pvp-killer";
            }
            if (plugin.combat().tagged(player)) {
                return "combat-tagged";
            }
        }

        if (plugin.getConfig().getBoolean("offers.deny-if-contested", true)) {
            double radius = plugin.getConfig().getDouble("offers.contested-radius", 8.0);
            for (Player other : player.getWorld().getPlayers()) {
                if (other.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                if (other.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                    return "contested";
                }
            }
        }
        return null;
    }

    private static String causeName(PlayerDeathEvent event) {
        EntityDamageEvent last = event.getEntity().getLastDamageCause();
        return last == null ? "UNKNOWN" : last.getCause().name();
    }

    private void alertStaff(String message) {
        if (!plugin.getConfig().getBoolean("staff-alerts", true)) {
            return;
        }
        Component text = Messages.prefix().append(Component.text(message));
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("netrestore.admin"))
                .forEach(p -> p.sendMessage(text));
    }
}
