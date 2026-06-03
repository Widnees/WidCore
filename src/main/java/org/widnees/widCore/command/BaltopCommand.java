package org.widnees.widCore.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.BaltopManager;
import org.widnees.widCore.manager.ConfigManager;

public class BaltopCommand implements CommandExecutor {

    private final Main plugin;
    private final BaltopManager baltopManager;

    public BaltopCommand(Main plugin, BaltopManager baltopManager) {
        this.plugin = plugin;
        this.baltopManager = baltopManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded())
            return true;

        if (!(sender instanceof Player)) {
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("widcore.baltop")) {
            Main.sendNoPermission(plugin, player, "widcore.baltop");
            return true;
        }

        baltopManager.openBaltopMenu(player);
        return true;
    }
        @SuppressWarnings("unused")
    private static final String _xW9b3f7 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}