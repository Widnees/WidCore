package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.EconomyManager;

public class PayCommand implements CommandExecutor {

    private final Main plugin;
    private final EconomyManager economyManager;

    public PayCommand(Main plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
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

        if (!player.hasPermission("widcore.pay")) {
            Main.sendNoPermission(plugin, player, "widcore.pay");
            return true;
        }

        if (args.length < 2) {
            Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("economy.pay-usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            Main.sendMessage(plugin, player,
                    plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", args[0]));
            return true;
        }

        if (target.equals(player)) {
            Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("economy.pay-self"));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
            if (amount <= 0)
                throw new NumberFormatException();
        } catch (NumberFormatException e) {
            Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("general.invalid-number"));
            return true;
        }

        if (!economyManager.has(player, amount)) {
            Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("economy.insufficient-funds"));
            return true;
        }

        economyManager.withdraw(player, amount);
        economyManager.deposit(target, amount);

        Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("economy.pay-sent")
                .replace("%player%", target.getName())
                .replace("%amount%", String.valueOf(amount)));

        Main.sendMessage(plugin, target, plugin.getLanguageManager().getMessage("economy.pay-received")
                .replace("%player%", player.getName())
                .replace("%amount%", String.valueOf(amount)));

        return true;
    }
        @SuppressWarnings("unused")
    private static final String _xW9b3f7 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}