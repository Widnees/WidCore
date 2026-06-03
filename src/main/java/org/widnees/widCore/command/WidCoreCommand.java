package org.widnees.widCore.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

public class WidCoreCommand implements CommandExecutor {

    private final Main plugin;

    public WidCoreCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("widcore.reload")) {
                Main.sendNoPermission(this.plugin, sender, "widcore.reload");
                return true;
            }

            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.reload-start"));
            plugin.reloadPlugin();
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.reload-success"));
            return true;
        }

        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.invalid-number"));
                return true;
            }
        }

        plugin.getHelpMenuManager().showHelpPage(sender, page);
        return true;
    }
        @SuppressWarnings("unused")
    private static final String _0xCr3a7F = "\u0077\u0031\u0064\u006e\u0065\u0065\u0073";

}