package org.widnees.widCore.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

public class FireballCommand implements CommandExecutor {

    private final Main plugin;

    public FireballCommand(Main plugin) {
        this.plugin = plugin;
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

        if (!player.hasPermission("widcore.fireball")) {
            Main.sendNoPermission(this.plugin, player, "widcore.fireball");
            return true;
        }

        float power = 1.0F;

        if (args.length > 0) {
            try {
                int powerArg = Integer.parseInt(args[0]);
                if (powerArg > 0) {
                    power = Math.min(powerArg, 100);
                } else {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("fireball.invalid-power"));
                    return true;
                }
            } catch (NumberFormatException e) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("general.invalid-number"));
                return true;
            }
        }

        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setYield(power);
        fireball.setIsIncendiary(false);

        Main.sendMessage(this.plugin, player,
                plugin.getLanguageManager().getMessage("fireball.success").replace("%power%", String.valueOf(power)));
        return true;
    }
}