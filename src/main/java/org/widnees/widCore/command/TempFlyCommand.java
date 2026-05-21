package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.AliasManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TempFlyCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private List<String> giveAliases;
    private List<String> removeAliases;
    private List<String> checkAliases;
    private List<String> allSubCommands;

    public TempFlyCommand(Main plugin) {
        this.plugin = plugin;
        loadAliases();
    }

    private void loadAliases() {
        AliasManager aliasManager = plugin.getAliasManager();
        this.giveAliases = aliasManager.getSubcommandAliases("tempfly", "give");
        this.removeAliases = aliasManager.getSubcommandAliases("tempfly", "remove");
        this.checkAliases = aliasManager.getSubcommandAliases("tempfly", "check");

        if (giveAliases.isEmpty())
            giveAliases = Arrays.asList("give", "add");
        if (removeAliases.isEmpty())
            removeAliases = Arrays.asList("remove", "take");
        if (checkAliases.isEmpty())
            checkAliases = Arrays.asList("check", "info");

        this.allSubCommands = new ArrayList<>();
        this.allSubCommands.addAll(giveAliases);
        this.allSubCommands.addAll(removeAliases);
        this.allSubCommands.addAll(checkAliases);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("widcore.tempfly.give") && !sender.hasPermission("widcore.tempfly.remove") && !sender.hasPermission("widcore.tempfly.check")) {
            Main.sendNoPermission(plugin, sender, "widcore.tempfly.*");
            return true;
        }

        if (args.length == 0) {
            if (!sender.hasPermission("widcore.tempfly.check")) {
                Main.sendNoPermission(plugin, sender, "widcore.tempfly.check");
                return true;
            }
            if (!(sender instanceof Player)) {
                Main.sendMessage(plugin, sender, "&cKonsoldan bu komutu kullanmak için bir oyuncu adı girmelisiniz.");
                return true;
            }
            Player target = (Player) sender;
            long remaining = plugin.getTempFlyManager().getRemainingSeconds(target);
            if (remaining <= 0) {
                Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("tempfly.no-time").replace("%player%", target.getName()));
                return true;
            }
            String formattedTime = plugin.getTempFlyManager().formatTime(remaining);
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("tempfly.check")
                    .replace("%player%", target.getName())
                    .replace("%time%", formattedTime));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (giveAliases.contains(subCommand)) {
            if (!sender.hasPermission("widcore.tempfly.give")) {
                Main.sendNoPermission(plugin, sender, "widcore.tempfly.give");
                return true;
            }
            if (args.length < 3) {
                Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("tempfly.usage"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", args[1]));
                return true;
            }
            long seconds = plugin.getTempFlyManager().parseDuration(args[2]);
            if (seconds <= 0) {
                Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("tempfly.invalid-duration"));
                return true;
            }
            plugin.getTempFlyManager().addTime(target, seconds);
            String formattedTime = plugin.getTempFlyManager().formatTime(seconds);
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("tempfly.given")
                    .replace("%player%", target.getName())
                    .replace("%time%", formattedTime));
            Main.sendMessage(plugin, target, plugin.getLanguageManager().getMessage("tempfly.received")
                    .replace("%time%", formattedTime));
            return true;
        } 
        else if (removeAliases.contains(subCommand)) {
            if (!sender.hasPermission("widcore.tempfly.remove")) {
                Main.sendNoPermission(plugin, sender, "widcore.tempfly.remove");
                return true;
            }
            if (args.length < 2) {
                Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("tempfly.usage"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", args[1]));
                return true;
            }
            long remaining = plugin.getTempFlyManager().getRemainingSeconds(target);
            if (remaining <= 0) {
                Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("tempfly.no-time").replace("%player%", target.getName()));
                return true;
            }
            plugin.getTempFlyManager().removeTime(target);
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("tempfly.removed").replace("%player%", target.getName()));
            Main.sendMessage(plugin, target, plugin.getLanguageManager().getMessage("tempfly.removed-target"));
            return true;
        }
        else if (checkAliases.contains(subCommand)) {
            if (!sender.hasPermission("widcore.tempfly.check")) {
                Main.sendNoPermission(plugin, sender, "widcore.tempfly.check");
                return true;
            }
            Player target;
            if (args.length == 1) { 
                if (!(sender instanceof Player)) {
                    Main.sendMessage(plugin, sender, "&cKonsoldan bu komutu kullanmak için bir oyuncu adı girmelisiniz.");
                    return true;
                }
                target = (Player) sender;
            } else { 
                if (!sender.hasPermission("widcore.tempfly.checkother")) {
                    Main.sendNoPermission(plugin, sender, "widcore.tempfly.checkother");
                    return true;
                }
                target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", args[1]));
                    return true;
                }
            }

            long remaining = plugin.getTempFlyManager().getRemainingSeconds(target);
            if (remaining <= 0) {
                Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("tempfly.no-time").replace("%player%", target.getName()));
                return true;
            }
            String formattedTime = plugin.getTempFlyManager().formatTime(remaining);
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("tempfly.check")
                    .replace("%player%", target.getName())
                    .replace("%time%", formattedTime));
            return true;
        }

        Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("tempfly.usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("widcore.tempfly.give") && !sender.hasPermission("widcore.tempfly.remove") && !sender.hasPermission("widcore.tempfly.check")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission("widcore.tempfly.give")) subs.addAll(giveAliases);
            if (sender.hasPermission("widcore.tempfly.remove")) subs.addAll(removeAliases);
            if (sender.hasPermission("widcore.tempfly.check")) subs.addAll(checkAliases);
            StringUtil.copyPartialMatches(args[0], subs, completions);
        } else if (args.length == 2) {
            boolean canSeePlayers = false;
            String sub = args[0].toLowerCase();
            if (giveAliases.contains(sub) && sender.hasPermission("widcore.tempfly.give")) canSeePlayers = true;
            else if (removeAliases.contains(sub) && sender.hasPermission("widcore.tempfly.remove")) canSeePlayers = true;
            else if (checkAliases.contains(sub) && sender.hasPermission("widcore.tempfly.checkother")) canSeePlayers = true;

            if (canSeePlayers) {
                List<String> playerNames = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playerNames.add(p.getName());
                }
                StringUtil.copyPartialMatches(args[1], playerNames, completions);
            }
        } else if (args.length == 3 && giveAliases.contains(args[0].toLowerCase())) {
            String[] times = {"60", "5m", "1h", "1d"};
            StringUtil.copyPartialMatches(args[2], Arrays.asList(times), completions);
        }

        Collections.sort(completions);
        return completions;
    }
}
