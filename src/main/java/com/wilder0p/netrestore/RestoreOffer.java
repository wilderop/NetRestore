package com.wilder0p.netrestore;

import org.bukkit.Location;

import java.util.UUID;

final class RestoreOffer {

    final UUID playerId;
    final String playerName;
    final long incidentId;
    final long createdAt;
    final long expiresAt;
    final String world;
    final double x;
    final double y;
    final double z;
    final String cause;
    final String killer;
    final long probeRtt;
    final int spikedPlayers;
    final InventorySnapshot snapshot;
    boolean used;
    boolean denied;

    RestoreOffer(UUID playerId, String playerName, long incidentId, long createdAt, long expiresAt,
                 Location loc, String cause, String killer, long probeRtt, int spikedPlayers,
                 InventorySnapshot snapshot) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.incidentId = incidentId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
        this.cause = cause;
        this.killer = killer;
        this.probeRtt = probeRtt;
        this.spikedPlayers = spikedPlayers;
        this.snapshot = snapshot;
    }

    boolean expired() {
        return used || denied || System.currentTimeMillis() > expiresAt;
    }

    long secondsLeft() {
        return Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000L);
    }

    String shortLoc() {
        return world + " " + (int) x + " " + (int) y + " " + (int) z;
    }
}
