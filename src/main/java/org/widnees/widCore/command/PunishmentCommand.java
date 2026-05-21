package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.PunishmentManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PunishmentCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final PunishmentManager punishmentManager;

    public PunishmentCommand(Main plugin, PunishmentManager punishmentManager) {
        this.plugin = plugin;
        this.punishmentManager = punishmentManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        String commandName = command.getName().toLowerCase();

        String commandKey = plugin.getAliasManager().lookupKey(commandName);

        if (!checkPermission(sender, commandKey)) {
            return true;
        }

        if (commandKey.equals("kickall")) {
            handleKickAll(sender, args);
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender, commandKey);
            return true;
        }

        if (commandKey.equals("unban") || commandKey.equals("unmute")) {
            handleUnpunish(sender, commandKey, args);
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", args[0]));
            return true;
        }

        if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId())) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("punishment.self-punish"));
            return true;
        }

        if (punishmentManager.isExempt(commandKey.replace("temp", ""), target.getName())) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("punishment.exempt"));
            return true;
        }

        if (commandKey.equals("tempban") || commandKey.equals("tempmute")) {
            handleTempPunishment(sender, target, commandKey, args);
            return true;
        }

        String reason = (args.length > 1) ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : plugin.getLanguageManager().getMessage("punishment.default-reason");

        switch (commandKey) {
            case "kick":
                handleKick(sender, target, reason);
                break;
            case "ban":
                handleBan(sender, target, reason);
                break;
            case "mute":
                handleMute(sender, target, reason);
                break;
        }

        return true;
    }

    private boolean checkPermission(CommandSender sender, String commandName) {

        String permission = null;
        switch (commandName) {
            case "ban":
                permission = "widcore.ban";
                break;
            case "tempban":
                permission = "widcore.tempban";
                break;
            case "unban":
                permission = "widcore.unban";
                break;
            case "mute":
                permission = "widcore.mute";
                break;
            case "tempmute":
                permission = "widcore.tempmute";
                break;
            case "unmute":
                permission = "widcore.unmute";
                break;
            case "kick":
                permission = "widcore.kick";
                break;
            case "kickall":
                permission = "widcore.kickall";
                break;
            case "banlist":
                permission = "widcore.banlist";
                break;
            case "mutelist":
                permission = "widcore.mutelist";
                break;
        }

        if (permission != null && !sender.hasPermission(permission)) {
            Main.sendNoPermission(this.plugin, sender, permission);
            return false;
        }
        return true;
    }

    private void handleUnpunish(CommandSender sender, String commandName, String[] args) {

        String actionPast;
        if (commandName.equals("unban")) {
            actionPast = plugin.getLanguageManager().getMessage("punishment.type-ban");
        } else {
            actionPast = plugin.getLanguageManager().getMessage("punishment.type-mute");
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("punishment.never-played"));
            return;
        }

        boolean success;
        if (commandName.equals("unban")) {
            success = punishmentManager.unbanPlayer(target.getUniqueId());
        } else {
            success = punishmentManager.unmutePlayer(target.getUniqueId());
        }

        if (success) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("punishment.remove-success")
                    .replace("%player%", target.getName())
                    .replace("%type%", actionPast));
        } else {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("punishment.not-punished")
                    .replace("%player%", target.getName())
                    .replace("%type%", actionPast));
        }
    }

    private void handleTempPunishment(CommandSender sender, Player target, String commandName, String[] args) {

        if (args.length < 2) {
            sendUsage(sender, commandName);
            return;
        }

        FileConfiguration config = (commandName.equals("tempban")) ? punishmentManager.getBanConfig()
                : punishmentManager.getMuteConfig();

        boolean useShortcuts = config.getBoolean("use-reason-shortcuts", false);
        boolean forceShortcuts = config.getBoolean("force-reason-shortcuts", false);
        String potentialDurationOrReason = args[1];
        long duration;
        String reason;
        int reasonStartIndex = 2;

        boolean isShortcut = useShortcuts && config.isSet("reasons." + potentialDurationOrReason.toLowerCase());

        if (isShortcut) {
            String durationString = config.getString("reasons." + potentialDurationOrReason.toLowerCase());
            duration = punishmentManager.parseDuration(durationString);
        } else {
            if (forceShortcuts) {
                Main.sendMessage(this.plugin, sender,
                        plugin.getLanguageManager().getMessage("punishment.shortcuts-only"));
                return;
            }

            duration = punishmentManager.parseDuration(potentialDurationOrReason);
            if (duration <= 0) {
                if (useShortcuts) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("punishment.invalid-duration-shortcut")
                                    .replace("%arg%", potentialDurationOrReason));
                } else {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("punishment.invalid-duration-format"));
                }
                return;
            }
        }

        if (args.length > reasonStartIndex) {
            reason = potentialDurationOrReason + " "
                    + String.join(" ", Arrays.copyOfRange(args, reasonStartIndex, args.length));
        } else {
            reason = potentialDurationOrReason;
        }

        if (commandName.equals("tempban")) {
            punishmentManager.tempBanPlayer(target, sender, duration, reason);
        } else {
            punishmentManager.tempMutePlayer(target, sender, duration, reason);
        }
    }

    private void handleKick(CommandSender sender, Player target, String reason) {
        punishmentManager.kickPlayer(target, sender, reason);
    }

    private void handleBan(CommandSender sender, Player target, String reason) {
        punishmentManager.banPlayer(target, sender, reason);
    }

    private void handleMute(CommandSender sender, Player target, String reason) {
        punishmentManager.mutePlayer(target, sender, reason);
    }

    private void handleKickAll(CommandSender sender, String[] args) {
        String reason = (args.length > 0) ? String.join(" ", args)
                : plugin.getLanguageManager().getMessage("punishment.default-reason");

        int count = 0;
        for (Player online : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (sender instanceof Player && ((Player) sender).getUniqueId().equals(online.getUniqueId())) {
                continue;
            }
            punishmentManager.kickPlayer(online, sender, reason);
            count++;
        }

        Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("punishment.kickall-success")
                .replace("%count%", String.valueOf(count))
                .replace("%reason%", reason));
    }

    private void sendUsage(CommandSender sender, String commandName) {
        String usageKey = "punishment.usage." + commandName;
        Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage(usageKey));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        String commandName = command.getName().toLowerCase();

        String commandKey = plugin.getAliasManager().lookupKey(commandName);

        if (args.length == 1) {
            if (commandKey.equals("unban")) {
                StringUtil.copyPartialMatches(args[0], punishmentManager.getBannedPlayerNames(), completions);
            } else if (commandKey.equals("unmute")) {
                StringUtil.copyPartialMatches(args[0], punishmentManager.getMutedPlayerNames(), completions);
            } else {
                List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[0], playerNames, completions);
            }
        } else if (args.length == 2 && (commandKey.equals("tempmute") || commandKey.equals("tempban"))) {
            FileConfiguration config = (commandKey.equals("tempban")) ? punishmentManager.getBanConfig()
                    : punishmentManager.getMuteConfig();

            if (config.getBoolean("use-reason-shortcuts", false)) {
                List<String> reasons = new ArrayList<>(config.getConfigurationSection("reasons").getKeys(false));
                StringUtil.copyPartialMatches(args[1], reasons, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}