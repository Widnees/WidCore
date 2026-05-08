package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

public class GodCommand implements CommandExecutor {
    private final Main plugin;

    public GodCommand(Main plugin) {
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
            if (!player.hasPermission("widcore.god")) {
                Main.sendNoPermission(this.plugin, player, "widcore.god");
                return true;
            }
            toggleGodMode(player);
        } else {
            if (!sender.hasPermission("widcore.god.other")) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("god.no-perm-other"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.player-not-found")
                        .replace("%player%", args[0]));
                return true;
            }
            toggleGodMode(target);

            String status = plugin.getGodModePlayers().contains(target.getUniqueId())
                    ? plugin.getLanguageManager().getMessage("god.status-active")
                    : plugin.getLanguageManager().getMessage("god.status-inactive");

            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("god.other-toggle")
                    .replace("%player%", target.getName())
                    .replace("%status%", status));
        }
        return true;
    }

    private void toggleGodMode(Player target) {
        if (plugin.getGodModePlayers().contains(target.getUniqueId())) {
            plugin.getGodModePlayers().remove(target.getUniqueId());
            target.setInvulnerable(false);
            Main.sendMessage(plugin, target, plugin.getLanguageManager().getMessage("god.toggle-off"));
        } else {
            plugin.getGodModePlayers().add(target.getUniqueId());
            target.setInvulnerable(true);
            target.setHealth(target.getMaxHealth());
            target.setFoodLevel(20);
            target.setSaturation(20F);
            Main.sendMessage(plugin, target, plugin.getLanguageManager().getMessage("god.toggle-on"));
        }
    }
}