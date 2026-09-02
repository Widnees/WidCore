package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.MenuManager;
import org.widnees.widCore.manager.PlayerNameCache;

import java.util.Collections;
import java.util.List;

public class InventoryRollbackCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final MenuManager menuManager;

    public InventoryRollbackCommand(Main plugin, MenuManager menuManager) {
        this.plugin = plugin;
        this.menuManager = menuManager;
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
        if (!player.hasPermission("widcore.irp")) {
            Main.sendNoPermission(this.plugin, player, "widcore.irp");
            return true;
        }

        if (args.length != 1) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("inventory_rollback.usage"));
            return true;
        }

        OfflinePlayer target = resolveTarget(args[0]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            Main.sendMessage(this.plugin, player,
                    plugin.getLanguageManager().getMessage("inventory_rollback.never-played"));
            return true;
        }

        menuManager.openBackupTypeMenu(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("widcore.irp")) {
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
    private static final String __wN7e3x9 = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}