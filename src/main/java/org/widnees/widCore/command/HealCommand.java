package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

public class HealCommand implements CommandExecutor {

    private final Main plugin;

    public HealCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("widcore.heal")) {
                Main.sendNoPermission(this.plugin, player, "widcore.heal");
                return true;
            }

            player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("heal.success-self"));
            return true;

        } else if (args.length == 1) {
            if (!sender.hasPermission("widcore.heal.other")) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("heal.no-perm-other"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.player-not-found")
                        .replace("%player%", args[0]));
                return true;
            }

            target.setHealth(target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("heal.success-other").replace("%player%", target.getName()));
            Main.sendMessage(plugin, target, plugin.getLanguageManager().getMessage("heal.success-target"));
            return true;

        } else {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("heal.usage"));
            return true;
        }
    }
}