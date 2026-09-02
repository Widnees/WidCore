package org.widnees.widCore.command;

import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.PlayerNameCache;

public class EnderChestCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public EnderChestCommand(Main plugin) {
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
        if (args.length == 0) {
            if (!player.hasPermission("widcore.ec")) {
                Main.sendNoPermission(this.plugin, player, "widcore.ec");
                return true;
            }
            player.openInventory(player.getEnderChest());
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("enderchest.open-self"));
            return true;
        }
        if (args.length == 1) {
            if (!player.hasPermission("widcore.ec.other")) {
                Main.sendMessage(this.plugin, player,
                        plugin.getLanguageManager().getMessage("enderchest.no-perm-other"));
                return true;
            }
            OfflinePlayer target = resolveTarget(args[0]);
            if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                Main.sendMessage(this.plugin, player,
                        plugin.getLanguageManager().getMessage("enderchest.never-played").replace("%player%", args[0]));
                return true;
            }
            if (target.isOnline()) {
                player.openInventory(target.getPlayer().getEnderChest());
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("enderchest.open-other")
                        .replace("%player%", target.getName()));
            } else {
                plugin.getDataManager().getOfflineEnderChest(target, offlineEnderChest -> {
                    player.openInventory(offlineEnderChest);
                    plugin.getOpenOfflineInventories().put(player.getUniqueId(), target.getUniqueId());
                    Main.sendMessage(this.plugin, player, plugin.getLanguageManager()
                            .getMessage("enderchest.open-offline").replace("%player%", target.getName()));
                });
            }
            return true;
        }
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("enderchest.usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("widcore.ec.other")) {
            return Collections.emptyList();
        }
        PlayerNameCache cache = plugin.getPlayerNameCache();
        if (cache == null) {
            return Collections.emptyList();
        }
        return cache.complete(sender, args[0], true);
    }

    private OfflinePlayer resolveTarget(String name) {
        PlayerNameCache cache = plugin.getPlayerNameCache();
        if (cache != null) {
            return cache.resolveKnown(name);
        }
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : null;
    }

    @SuppressWarnings("unused")
    private static final String _0xWb8d2e = "\u0077\u0069\u0064" + "\u006e\u0065" + "\u0065\u0073";

}
