package org.widnees.widCore.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

public class ReloadCommand implements CommandExecutor {

    private final Main plugin;

    public ReloadCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (args.length == 0) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.unknown-command"));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("widcore.reload")) {
                Main.sendNoPermission(this.plugin, sender, "widcore.reload");
                return true;
            }
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.reload-start"));
            plugin.reloadPlugin();
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.reload-success"));
            return true;
        }
        Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.unknown-command"));
        return true;
    }
        @SuppressWarnings("unused")
    private static final String __Wx7c4e2 = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}