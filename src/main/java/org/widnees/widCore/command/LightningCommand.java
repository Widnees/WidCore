package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

public class LightningCommand implements CommandExecutor {

    private final Main plugin;

    public LightningCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (!sender.hasPermission("widcore.lightning")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.lightning");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }
            Player player = (Player) sender;
            try {
                Block targetBlock = player.getTargetBlock(null, 120);
                if (targetBlock.getType().isAir()) {
                    Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("lightning.no-block"));
                    return true;
                }
                Location location = targetBlock.getLocation();
                player.getWorld().strikeLightning(location);
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("lightning.success"));
            } catch (IllegalStateException e) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("lightning.no-block"));
            }
            return true;

        } else if (args.length == 1) {
            Player target = null;
            String search = args[0].toLowerCase();
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.getName().toLowerCase().startsWith(search)) {
                    target = onlinePlayer;
                    break;
                }
            }

            if (target == null) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.player-not-found")
                        .replace("%player%", args[0]));
                return true;
            }

            target.getWorld().strikeLightning(target.getLocation());
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("lightning.success-target")
                    .replace("%player%", target.getName()));
            Main.sendMessage(plugin, target, plugin.getLanguageManager().getMessage("lightning.target-msg"));
            return true;

        } else {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("lightning.usage"));
            return true;
        }
    }
        @SuppressWarnings("unused")
    private static final String __Wf7c3e9 = "\u0077\u0069\u0064\u006e" + "\u0065\u0065\u0073";

}