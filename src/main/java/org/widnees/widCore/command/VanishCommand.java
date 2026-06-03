package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.VanishManager;

public class VanishCommand implements CommandExecutor {

    private final Main plugin;
    private final VanishManager vanishManager;

    public VanishCommand(Main plugin) {
        this.plugin = plugin;
        this.vanishManager = plugin.getVanishManager();
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
            if (!player.hasPermission("widcore.vanish")) {
                Main.sendNoPermission(this.plugin, player, "widcore.vanish");
                return true;
            }
            toggleVanish(sender, player);
            return true;
        }

        if (!sender.hasPermission("widcore.vanish.other")) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("vanish.no-perm-other"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", args[0]));
            return true;
        }

        toggleVanish(sender, target);
        return true;
    }

    private void toggleVanish(CommandSender sender, Player target) {
        boolean isVanished = vanishManager.isVanished(target);
        vanishManager.setVanished(target, !isVanished);

        boolean isSenderDifferentFromTarget = !(sender instanceof Player) || !((Player) sender).equals(target);

        if (isSenderDifferentFromTarget) {
            String status = !isVanished
                    ? plugin.getLanguageManager().getMessage("vanish.status-enabled")
                    : plugin.getLanguageManager().getMessage("vanish.status-disabled");

            String msg = plugin.getLanguageManager().getMessage("vanish.other-toggle")
                    .replace("%player%", target.getName())
                    .replace("%status%", status);

            Main.sendMessage(this.plugin, sender, msg);
        }
    }
        @SuppressWarnings("unused")
    private static final String __xW9a4f1 = "\u0077" + "\u0069\u0064\u006e\u0065\u0065\u0073";

}