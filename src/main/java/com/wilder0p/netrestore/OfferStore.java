package com.wilder0p.netrestore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class OfferStore {

    private final NetRestore plugin;
    private final Map<UUID, RestoreOffer> offers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRestore = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastIncidentOffer = new ConcurrentHashMap<>();
    private final File cooldownFile;

    OfferStore(NetRestore plugin) {
        this.plugin = plugin;
        this.cooldownFile = new File(plugin.getDataFolder(), "cooldowns.yml");
        loadCooldowns();
    }

    RestoreOffer get(UUID id) {
        RestoreOffer offer = offers.get(id);
        if (offer != null && offer.expired()) {
            offers.remove(id);
            return null;
        }
        return offer;
    }

    void put(RestoreOffer offer) {
        offers.put(offer.playerId, offer);
        lastIncidentOffer.put(offer.playerId, offer.incidentId);
    }

    void remove(UUID id) {
        offers.remove(id);
    }

    boolean alreadyOfferedThisIncident(UUID id, long incidentId) {
        return Long.valueOf(incidentId).equals(lastIncidentOffer.get(id));
    }

    boolean onCooldown(Player player) {
        if (player.hasPermission("netrestore.bypass-cooldown")) {
            return false;
        }
        Long last = lastRestore.get(player.getUniqueId());
        if (last == null) {
            return false;
        }
        long cd = plugin.getConfig().getLong("offers.cooldown-seconds", 21600) * 1000L;
        return System.currentTimeMillis() - last < cd;
    }

    long cooldownLeftSeconds(Player player) {
        Long last = lastRestore.get(player.getUniqueId());
        if (last == null) {
            return 0;
        }
        long cd = plugin.getConfig().getLong("offers.cooldown-seconds", 21600) * 1000L;
        return Math.max(0, (cd - (System.currentTimeMillis() - last)) / 1000L);
    }

    void markUsed(UUID id) {
        RestoreOffer offer = offers.get(id);
        if (offer != null) {
            offer.used = true;
        }
        lastRestore.put(id, System.currentTimeMillis());
        offers.remove(id);
        saveCooldowns();
    }

    void deny(UUID id) {
        RestoreOffer offer = offers.get(id);
        if (offer != null) {
            offer.denied = true;
        }
        offers.remove(id);
    }

    void purgeExpired() {
        offers.entrySet().removeIf(e -> e.getValue().expired());
    }

    Map<UUID, RestoreOffer> snapshot() {
        purgeExpired();
        return Map.copyOf(offers);
    }

    private void loadCooldowns() {
        if (!cooldownFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(cooldownFile);
        for (String key : yaml.getKeys(false)) {
            try {
                lastRestore.put(UUID.fromString(key), yaml.getLong(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveCooldowns() {
        YamlConfiguration yaml = new YamlConfiguration();
        lastRestore.forEach((id, time) -> yaml.set(id.toString(), time));
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(cooldownFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save cooldowns: " + ex.getMessage());
        }
    }
}
