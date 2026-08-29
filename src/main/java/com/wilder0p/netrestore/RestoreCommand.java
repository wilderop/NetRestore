package com.wilder0p.netrestore;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

final class RestoreCommand implements CommandExecutor, TabCompleter {

    private final NetRestore plugin;

    RestoreCommand(NetRestore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        RestoreOffer offer = plugin.offers().get(player.getUniqueId());
        if (offer == null) {
            player.sendMessage(Messages.err("No lag-death restore is available."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            player.sendMessage(Messages.offer(offer));
            return true;
        }
        if (!args[0].equalsIgnoreCase("confirm")) {
            player.sendMessage(Messages.info("Use /restore confirm to recover the saved inventory."));
            return true;
        }
        if (plugin.getConfig().getBoolean("offers.require-confirm", true) && args.length == 0) {
            player.sendMessage(Messages.offer(offer));
            return true;
        }

        double overlap = offer.snapshot.overlapPercent(player);
        double maxOverlap = plugin.getConfig().getDouble("offers.deny-if-overlap-percent", 70);
        if (overlap >= maxOverlap) {
            plugin.offers().deny(player.getUniqueId());
            plugin.audit().log("DENY overlap " + player.getName() + " " + (int) overlap + "%");
            player.sendMessage(Messages.err("Restore denied — your inventory already looks recovered (" + (int) overlap + "% overlap)."));
            return true;
        }

        offer.snapshot.apply(player, plugin.getConfig().getBoolean("offers.restore-xp", true));
        plugin.offers().markUsed(player.getUniqueId());
        plugin.audit().log("RESTORE " + player.getName()
                + " incident=#" + offer.incidentId
                + " items=" + offer.snapshot.itemCount
                + " overlap=" + (int) overlap + "%");
        player.sendMessage(Messages.ok("Inventory restored from network-lag death (" + offer.snapshot.itemCount + " stacks)."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("confirm", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
