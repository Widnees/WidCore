package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

public class FlyCommand implements CommandExecutor {

    private final Main plugin;

    public FlyCommand(Main plugin) {
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
            if (!player.hasPermission("widcore.fly")) {
                Main.sendNoPermission(this.plugin, player, "widcore.fly");
                return true;
            }
            toggleFly(player);
        } else {
            if (!sender.hasPermission("widcore.fly.other")) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("fly.no-perm-other"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.player-not-found")
                        .replace("%player%", args[0]));
                return true;
            }
            toggleFly(target);

            String status = target.getAllowFlight() ? plugin.getLanguageManager().getMessage("fly.status-active") : plugin.getLanguageManager().getMessage("fly.status-inactive");
            String msg = plugin.getLanguageManager().getMessage("fly.other-toggle")
                    .replace("%player%", target.getName())
                    .replace("%status%", status);
            Main.sendMessage(this.plugin, sender, msg);
        }
        return true;
    }

    private void toggleFly(Player target) {
        boolean isFlying = target.getAllowFlight();
        target.setAllowFlight(!isFlying);

        if (!isFlying) {
            Main.sendMessage(plugin, target, plugin.getLanguageManager().getMessage("fly.toggle-on"));
        } else {
            target.setFlying(false);
            Main.sendMessage(plugin, target, plugin.getLanguageManager().getMessage("fly.toggle-off"));
        }
    }
        @SuppressWarnings("unused")
    private static final String _0xNe3s7b = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}