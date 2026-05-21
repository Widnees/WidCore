package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.AliasManager;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.EconomyManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class EconomyCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final EconomyManager economyManager;

    private List<String> giveAliases;
    private List<String> takeAliases;
    private List<String> setAliases;
    private List<String> allSubCommands;

    public EconomyCommand(Main plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        loadAliases();
    }

    private void loadAliases() {
        AliasManager aliasManager = plugin.getAliasManager();
        this.giveAliases = aliasManager.getSubcommandAliases("economy", "give");
        this.takeAliases = aliasManager.getSubcommandAliases("economy", "take");
        this.setAliases = aliasManager.getSubcommandAliases("economy", "set");

        if (giveAliases.isEmpty())
            giveAliases = Arrays.asList("give", "add", "deposit");
        if (takeAliases.isEmpty())
            takeAliases = Arrays.asList("take", "remove", "withdraw");
        if (setAliases.isEmpty())
            setAliases = Arrays.asList("set", "define");

        this.allSubCommands = new ArrayList<>();
        this.allSubCommands.addAll(giveAliases);
        this.allSubCommands.addAll(takeAliases);
        this.allSubCommands.addAll(setAliases);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded())
            return true;

        if (!sender.hasPermission("widcore.eco.admin")) {
            Main.sendNoPermission(plugin, sender, "widcore.eco.admin");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                double balance = economyManager.getBalance(player);
                Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("showitem.economy-balance")
                        .replace("%amount%", String.valueOf(balance)));
            } else {
                Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
            }
            return true;
        }

        String arg0 = args[0].toLowerCase();

        if (!allSubCommands.contains(arg0)) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

            if (!target.hasPlayedBefore() && !target.isOnline()) {
                Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("general.player-not-found")
                        .replace("%player%", args[0]));
                return true;
            }

            double balance = economyManager.getBalance(target);
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("showitem.economy-balance-target")
                    .replace("%player%", target.getName())
                    .replace("%amount%", String.valueOf(balance)));
            return true;
        }

        if (args.length < 3) {
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("economy.eco-usage"));
            return true;
        }

        double amount;

        try {
            amount = Double.parseDouble(args[2]);
            if (amount < 0)
                throw new NumberFormatException();
        } catch (NumberFormatException e) {
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("general.invalid-number"));
            return true;
        }

        if (args[1].equals("**")) {
            if (giveAliases.contains(arg0)) {
                if (!sender.hasPermission("widcore.eco.give.all")) {
                    Main.sendNoPermission(plugin, sender, "widcore.eco.give.all");
                    return true;
                }
                int count = 0;
                for (Player online : Bukkit.getOnlinePlayers()) {
                    economyManager.deposit(online, amount);
                    Main.sendMessage(plugin, online, plugin.getLanguageManager().getMessage("economy.given-target")
                            .replace("%amount%", String.valueOf(amount)));
                    count++;
                }
                Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("economy.given-all")
                        .replace("%amount%", String.valueOf(amount))
                        .replace("%count%", String.valueOf(count)));
            } else if (setAliases.contains(arg0)) {
                if (!sender.hasPermission("widcore.eco.set.all")) {
                    Main.sendNoPermission(plugin, sender, "widcore.eco.set.all");
                    return true;
                }
                int count = 0;
                for (Player online : Bukkit.getOnlinePlayers()) {
                    economyManager.setBalance(online, amount);
                    Main.sendMessage(plugin, online, plugin.getLanguageManager().getMessage("economy.set-target")
                            .replace("%amount%", String.valueOf(amount)));
                    count++;
                }
                Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("economy.set-all")
                        .replace("%amount%", String.valueOf(amount))
                        .replace("%count%", String.valueOf(count)));
            } else {
                Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("economy.eco-usage"));
            }
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

        if (giveAliases.contains(arg0)) {
            if (!sender.hasPermission("widcore.eco.give")) {
                Main.sendNoPermission(plugin, sender, "widcore.eco.give");
                return true;
            }
            economyManager.deposit(target, amount);
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("economy.given")
                    .replace("%player%", target.getName() != null ? target.getName() : "Bilinmiyor")
                    .replace("%amount%", String.valueOf(amount)));

            if (target.isOnline() && target.getPlayer() != null) {
                Main.sendMessage(plugin, target.getPlayer(), plugin.getLanguageManager().getMessage("economy.given-target")
                        .replace("%amount%", String.valueOf(amount)));
            }

        } else if (setAliases.contains(arg0)) {
            if (!sender.hasPermission("widcore.eco.set")) {
                Main.sendNoPermission(plugin, sender, "widcore.eco.set");
                return true;
            }
            economyManager.setBalance(target, amount);
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("economy.set")
                    .replace("%player%", target.getName() != null ? target.getName() : "Bilinmiyor")
                    .replace("%amount%", String.valueOf(amount)));

            if (target.isOnline() && target.getPlayer() != null) {
                Main.sendMessage(plugin, target.getPlayer(), plugin.getLanguageManager().getMessage("economy.set-target")
                        .replace("%amount%", String.valueOf(amount)));
            }

        } else if (takeAliases.contains(arg0)) {
            if (!sender.hasPermission("widcore.eco.take")) {
                Main.sendNoPermission(plugin, sender, "widcore.eco.take");
                return true;
            }
            economyManager.withdraw(target, amount);
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("economy.taken")
                    .replace("%player%", target.getName() != null ? target.getName() : "Bilinmiyor")
                    .replace("%amount%", String.valueOf(amount)));

            if (target.isOnline() && target.getPlayer() != null) {
                Main.sendMessage(plugin, target.getPlayer(), plugin.getLanguageManager().getMessage("economy.taken-target")
                        .replace("%amount%", String.valueOf(amount)));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], allSubCommands, completions);

            List<String> playerNames = Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .collect(Collectors.toList());
            StringUtil.copyPartialMatches(args[0], playerNames, completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (giveAliases.contains(sub) || setAliases.contains(sub)) {
                StringUtil.copyPartialMatches(args[1], Collections.singletonList("**"), completions);
            }
            List<String> playerNames = Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .collect(Collectors.toList());
            StringUtil.copyPartialMatches(args[1], playerNames, completions);
        } else if (args.length == 3) {
            List<String> amounts = Arrays.asList("10", "100", "1000", "10000");
            StringUtil.copyPartialMatches(args[2], amounts, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}