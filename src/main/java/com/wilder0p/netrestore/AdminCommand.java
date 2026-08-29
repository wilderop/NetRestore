package com.wilder0p.netrestore;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

final class AdminCommand implements CommandExecutor, TabCompleter {

    private final NetRestore plugin;

    AdminCommand(NetRestore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("netrestore.admin")) {
            sender.sendMessage(Messages.err("No permission."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            IncidentMonitor mon = plugin.incidents();
            sender.sendMessage(Messages.info("state=" + mon.state()
                    + " incident=#" + mon.currentIncidentId()
                    + " hot=" + mon.isHot()
                    + " probe=" + mon.lastProbeRtt() + "ms (base " + mon.probeBaseline() + "ms)"
                    + " spiked=" + mon.lastSpikedCount()
                    + " reason=" + mon.lastReason()
                    + " offers=" + plugin.offers().snapshot().size()));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            sender.sendMessage(Messages.ok("Config reloaded."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.info("/netrestore <status|reload|preview|deny|force> [player]"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Messages.err("Player not found."));
            return true;
        }
        RestoreOffer offer = plugin.offers().get(target.getUniqueId());
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "preview" -> {
                if (offer == null) {
                    sender.sendMessage(Messages.err("No active offer for " + target.getName()));
                    return true;
                }
                sender.sendMessage(Messages.info(target.getName()
                        + " #" + offer.incidentId
                        + " " + offer.cause
                        + " " + offer.shortLoc()
                        + " items=" + offer.snapshot.itemCount
                        + " left=" + offer.secondsLeft() + "s"
                        + " killer=" + offer.killer));
            }
            case "deny" -> {
                if (offer == null) {
                    sender.sendMessage(Messages.err("No active offer for " + target.getName()));
                    return true;
                }
                plugin.offers().deny(target.getUniqueId());
                plugin.audit().log("ADMIN DENY " + target.getName() + " by " + sender.getName());
                target.sendMessage(Messages.err("Your lag-death restore was denied by staff."));
                sender.sendMessage(Messages.ok("Denied restore for " + target.getName()));
            }
            case "force" -> {
                InventorySnapshot snap = offer == null ? InventorySnapshot.capture(target) : offer.snapshot;
                if (offer == null) {
                    sender.sendMessage(Messages.err("No offer on file; refusing to invent items. Capture happens only on eligible lag deaths."));
                    return true;
                }
                snap.apply(target, plugin.getConfig().getBoolean("offers.restore-xp", true));
                plugin.offers().markUsed(target.getUniqueId());
                plugin.audit().log("ADMIN FORCE " + target.getName() + " by " + sender.getName()
                        + " incident=#" + offer.incidentId);
                target.sendMessage(Messages.ok("Staff restored your lag-death inventory."));
                sender.sendMessage(Messages.ok("Forced restore for " + target.getName()));
            }
            default -> sender.sendMessage(Messages.info("/netrestore <status|reload|preview|deny|force> [player]"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("netrestore.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return Stream.of("status", "reload", "preview", "deny", "force")
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
