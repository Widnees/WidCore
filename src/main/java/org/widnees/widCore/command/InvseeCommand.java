package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.widnees.widCore.Main;
import org.widnees.widCore.database.BinaryDataManager;
import org.widnees.widCore.manager.ConfigManager;

public class InvseeCommand implements CommandExecutor {

    private final Main plugin;

    public InvseeCommand(Main plugin) {
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

        if (!player.hasPermission("widcore.inv")) {
            Main.sendNoPermission(this.plugin, player, "widcore.inv");
            return true;
        }

        if (args.length != 1) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("invsee.usage"));
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            Main.sendMessage(this.plugin, player,
                    plugin.getLanguageManager().getMessage("invsee.never-played").replace("%player%", args[0]));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("general.self-interaction"));
            return true;
        }

        if (target.isOnline()) {
            Player targetOnline = target.getPlayer();
            Inventory virtualInv = Bukkit.createInventory(player, 54, targetOnline.getName() + "'s Inventory");

            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!targetOnline.isOnline() || !player.getOpenInventory().getTopInventory().equals(virtualInv)) {
                        this.cancel();
                        return;
                    }

                    ItemStack[] storageContents = targetOnline.getInventory().getStorageContents();
                    for (int i = 0; i < storageContents.length; i++) {
                        virtualInv.setItem(i, storageContents[i]);
                    }

                    virtualInv.setItem(45, targetOnline.getInventory().getHelmet());
                    virtualInv.setItem(46, targetOnline.getInventory().getChestplate());
                    virtualInv.setItem(47, targetOnline.getInventory().getLeggings());
                    virtualInv.setItem(48, targetOnline.getInventory().getBoots());
                    virtualInv.setItem(53, targetOnline.getInventory().getItemInOffHand());

                    BinaryDataManager.fillPlaceholders(virtualInv);
                }
            }.runTaskTimer(plugin, 0L, 10L);

            plugin.getActiveInvseeTasks().put(player.getUniqueId(), task);
            plugin.getOpenInvseeInventories().put(player.getUniqueId(), target.getUniqueId());
            player.openInventory(virtualInv);

        } else {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("invsee.opening-offline")
                    .replace("%player%", target.getName()));
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("invsee.offline-save-note"));

            plugin.getDataManager().getOfflinePlayerInventory(target, virtualInv -> {
                plugin.getOpenInvseeInventories().put(player.getUniqueId(), target.getUniqueId());
                player.openInventory(virtualInv);
            });
        }

        return true;
    }
}