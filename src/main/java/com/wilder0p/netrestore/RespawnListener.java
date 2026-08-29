package com.wilder0p.netrestore;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

final class RespawnListener implements Listener {

    private final NetRestore plugin;

    RespawnListener(NetRestore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        RestoreOffer offer = plugin.offers().get(player.getUniqueId());
        if (offer == null) {
            return;
        }
        player.sendMessage(Messages.offer(offer));
    }
}
