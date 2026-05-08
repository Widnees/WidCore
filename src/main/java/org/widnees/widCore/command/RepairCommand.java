package org.widnees.widCore.command;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

public class RepairCommand implements CommandExecutor {

    private final Main plugin;

    public RepairCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (!(sender instanceof Player)) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("all")) {
            if (!player.hasPermission("widcore.repair.all")) {
                Main.sendNoPermission(this.plugin, player, "widcore.repair.all");
                return true;
            }
            repairAllItems(player);
        } else {
            if (!player.hasPermission("widcore.repair")) {
                Main.sendNoPermission(this.plugin, player, "widcore.repair");
                return true;
            }
            repairItemInHand(player);
        }

        return true;
    }

    private void repairItemInHand(Player player) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("repair.no-item"));
            return;
        }

        ItemMeta meta = itemInHand.getItemMeta();
        if (!(meta instanceof Damageable)) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("repair.not-repairable"));
            return;
        }

        Damageable damageable = (Damageable) meta;
        if (damageable.getDamage() == 0) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("repair.no-need"));
            return;
        }

        damageable.setDamage(0);
        itemInHand.setItemMeta(meta);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("repair.success"));
    }

    private void repairAllItems(Player player) {
        int repairedCount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (repairItem(item)) {
                repairedCount++;
            }
        }
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (repairItem(armor)) {
                repairedCount++;
            }
        }
        if (repairItem(player.getInventory().getItemInOffHand())) {
            repairedCount++;
        }

        if (repairedCount > 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("repair.success-all")
                    .replace("%count%", String.valueOf(repairedCount)));
        } else {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("repair.no-need-all"));
        }
    }

    private boolean repairItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable)) {
            return false;
        }

        Damageable damageable = (Damageable) meta;
        if (damageable.getDamage() > 0) {
            damageable.setDamage(0);
            item.setItemMeta(meta);
            return true;
        }

        return false;
    }
}