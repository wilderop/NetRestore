package com.wilder0p.netrestore;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

final class InventorySnapshot {

    final byte[][] contents;
    final byte[][] armor;
    final byte[] extra;
    final int level;
    final float exp;
    final int totalExp;
    final int itemCount;
    final Map<String, Integer> counts;

    private InventorySnapshot(byte[][] contents, byte[][] armor, byte[] extra, int level, float exp, int totalExp,
                              int itemCount, Map<String, Integer> counts) {
        this.contents = contents;
        this.armor = armor;
        this.extra = extra;
        this.level = level;
        this.exp = exp;
        this.totalExp = totalExp;
        this.itemCount = itemCount;
        this.counts = counts;
    }

    static InventorySnapshot capture(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] rawContents = inv.getStorageContents();
        ItemStack[] rawArmor = inv.getArmorContents();
        ItemStack extraItem = inv.getItemInOffHand();

        Map<String, Integer> counts = new HashMap<>();
        int items = 0;
        byte[][] contents = encode(rawContents, counts);
        items += count(rawContents);
        byte[][] armor = encode(rawArmor, counts);
        items += count(rawArmor);
        byte[] extra = encodeOne(extraItem, counts);
        if (notEmpty(extraItem)) {
            items++;
        }
        return new InventorySnapshot(contents, armor, extra, player.getLevel(), player.getExp(),
                player.getTotalExperience(), items, counts);
    }

    void apply(Player player, boolean xp) {
        PlayerInventory inv = player.getInventory();
        inv.setStorageContents(decode(contents));
        inv.setArmorContents(decode(armor));
        inv.setItemInOffHand(decodeOne(extra));
        if (xp) {
            player.setTotalExperience(0);
            player.setLevel(0);
            player.setExp(0f);
            player.setTotalExperience(totalExp);
            player.setLevel(level);
            player.setExp(exp);
        }
    }

    double overlapPercent(Player player) {
        if (counts.isEmpty()) {
            return 0;
        }
        Map<String, Integer> now = new HashMap<>();
        tally(player.getInventory().getStorageContents(), now);
        tally(player.getInventory().getArmorContents(), now);
        tally(new ItemStack[]{player.getInventory().getItemInOffHand()}, now);
        int matched = 0;
        int total = 0;
        for (var e : counts.entrySet()) {
            total += e.getValue();
            matched += Math.min(e.getValue(), now.getOrDefault(e.getKey(), 0));
        }
        if (total == 0) {
            return 0;
        }
        return (matched * 100.0) / total;
    }

    static String encodeB64(byte[] data) {
        return data == null ? "" : Base64.getEncoder().encodeToString(data);
    }

    static byte[] decodeB64(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        return Base64.getDecoder().decode(data);
    }

    private static byte[][] encode(ItemStack[] items, Map<String, Integer> counts) {
        byte[][] out = new byte[items.length][];
        for (int i = 0; i < items.length; i++) {
            out[i] = encodeOne(items[i], counts);
        }
        return out;
    }

    private static byte[] encodeOne(ItemStack item, Map<String, Integer> counts) {
        if (!notEmpty(item)) {
            return null;
        }
        tally(item, counts);
        return item.serializeAsBytes();
    }

    private static ItemStack[] decode(byte[][] data) {
        ItemStack[] items = new ItemStack[data.length];
        for (int i = 0; i < data.length; i++) {
            items[i] = decodeOne(data[i]);
        }
        return items;
    }

    private static ItemStack decodeOne(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        return ItemStack.deserializeBytes(data);
    }

    private static void tally(ItemStack[] items, Map<String, Integer> counts) {
        if (items == null) {
            return;
        }
        for (ItemStack item : items) {
            tally(item, counts);
        }
    }

    private static void tally(ItemStack item, Map<String, Integer> counts) {
        if (!notEmpty(item)) {
            return;
        }
        String key = item.getType().name();
        counts.merge(key, item.getAmount(), Integer::sum);
    }

    private static int count(ItemStack[] items) {
        int n = 0;
        if (items == null) {
            return 0;
        }
        for (ItemStack item : items) {
            if (notEmpty(item)) {
                n++;
            }
        }
        return n;
    }

    private static boolean notEmpty(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }
}
