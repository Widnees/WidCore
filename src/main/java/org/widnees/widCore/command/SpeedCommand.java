package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

public class SpeedCommand implements CommandExecutor {

    private final Main plugin;

    public SpeedCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }

            Player player = (Player) sender;
            if (!player.hasPermission("widcore.speed")) {
                Main.sendNoPermission(this.plugin, player, "widcore.speed");
                return true;
            }

            try {
                float speed = Float.parseFloat(args[0]);
                if (player.isFlying()) {
                    setSpeed(player, "fly", speed);
                } else {
                    setSpeed(player, "walk", speed);
                }
            } catch (NumberFormatException e) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("speed.invalid-value"));
            }
            return true;
        }
        if (args.length == 3) {
            if (!sender.hasPermission("widcore.speed.other")) {
                Main.sendNoPermission(this.plugin, sender, "widcore.speed.other");
                return true;
            }

            String type = args[0].toLowerCase();
            if (!type.equals("walk") && !type.equals("fly")) {
                sendUsage(sender);
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.player-not-found")
                        .replace("%player%", args[1]));
                return true;
            }

            try {
                float speed = Float.parseFloat(args[2]);
                setSpeed(target, type, speed);

                String typeMsg = type.equals("fly")
                        ? plugin.getLanguageManager().getMessage("speed.type-fly")
                        : plugin.getLanguageManager().getMessage("speed.type-walk");

                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("speed.set-other")
                        .replace("%player%", target.getName())
                        .replace("%type%", typeMsg)
                        .replace("%speed%", String.valueOf(speed)));

                if (!(sender instanceof Player) || !((Player) sender).equals(target)) {
                    Main.sendMessage(plugin, target, plugin.getLanguageManager().getMessage("speed.set-by-other")
                            .replace("%player%", sender.getName())
                            .replace("%type%", typeMsg)
                            .replace("%speed%", String.valueOf(speed)));
                }
            } catch (NumberFormatException e) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("speed.invalid-value"));
            }
            return true;
        }

        sendUsage(sender);
        return true;
    }

    private void setSpeed(Player player, String type, float speed) {
        if (speed < 0.0f || speed > 10.0f) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("speed.invalid-value"));
            return;
        }

        float finalSpeed;
        if (type.equals("fly")) {
            finalSpeed = speed / 10.0f;
            if (speed == 0.0f)
                finalSpeed = 0.0001f;
            player.setFlySpeed(finalSpeed);
            Main.sendMessage(this.plugin, player,
                    plugin.getLanguageManager().getMessage("speed.set-fly").replace("%speed%", String.valueOf(speed)));
        } else {
            finalSpeed = speed / 5.0f;
            if (speed > 5)
                finalSpeed = 1.0f;
            player.setWalkSpeed(finalSpeed);
            Main.sendMessage(this.plugin, player,
                    plugin.getLanguageManager().getMessage("speed.set-walk").replace("%speed%", String.valueOf(speed)));
        }
    }

    private void sendUsage(CommandSender sender) {
        Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("speed.usage"));
    }
}