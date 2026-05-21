package org.widnees.widCore.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.PunishmentMenuManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PunishmentListCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final PunishmentMenuManager menuManager;

    public PunishmentListCommand(Main plugin, PunishmentMenuManager menuManager) {
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

        String commandKey = plugin.getAliasManager().lookupKey(command.getName());
        boolean isBanList = commandKey.equals("banlist");

        String permission = isBanList ? "widcore.banlist" : "widcore.mutelist";
        if (!player.hasPermission(permission)) {
            Main.sendNoPermission(this.plugin, player, permission);
            return true;
        }

        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
            }
        }

        menuManager.openPunishmentListMenu(player, page, isBanList);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {

            List<String> completions = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                completions.add(String.valueOf(i));
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
