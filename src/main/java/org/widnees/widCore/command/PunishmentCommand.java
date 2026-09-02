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
import org.widnees.widCore.manager.TextParser;

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

        if (commandKey.equals("muteip") || commandKey.equals("unmuteip") || commandKey.equals("kickip")
                || commandKey.equals("banip") || commandKey.equals("unbanip")) {
            handleIPCommand(sender, commandKey, args);
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

        if (commandKey.equals("kick")) {
            Player onlineTarget = Bukkit.getPlayer(args[0]);
            if (onlineTarget == null) {
                Main.sendMessage(this.plugin, sender,
                        plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", args[0]));
                return true;
            }
            if (sender instanceof Player && ((Player) sender).getUniqueId().equals(onlineTarget.getUniqueId())) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("punishment.self-punish"));
                return true;
            }
            if (punishmentManager.isExempt("kick", onlineTarget.getName())) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("punishment.exempt"));
                return true;
            }
            String reason = (args.length > 1) ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                    : plugin.getLanguageManager().getMessage("punishment.default-reason");
            handleKick(sender, onlineTarget, reason);
            return true;
        }

        OfflinePlayer target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
            if (offline != null && (offline.hasPlayedBefore() || offline.isOnline())) {
                target = offline;
            }
        }
        if (target == null || (!((target instanceof Player) && ((Player) target).isOnline()) && !target.hasPlayedBefore())) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", args[0]));
            return true;
        }

        if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId())) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("punishment.self-punish"));
            return true;
        }

        String targetName = target.getName() != null ? target.getName() : args[0];
        if (punishmentManager.isExempt(commandKey, targetName)) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("punishment.exempt"));
            return true;
        }

        if (commandKey.equals("ban")) {
            handleBanWithOptionalDuration(sender, target, args);
            return true;
        }

        if (commandKey.equals("mute")) {
            handleMuteWithOptionalDuration(sender, target, args);
            return true;
        }

        return true;
    }

    private boolean checkPermission(CommandSender sender, String commandName) {

        String permission = null;
        switch (commandName) {
            case "ban":
                permission = "widcore.ban";
                break;
            case "unban":
                permission = "widcore.unban";
                break;
            case "mute":
                permission = "widcore.mute";
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
            case "muteip":
                permission = "widcore.muteip";
                break;
            case "unmuteip":
                permission = "widcore.unmuteip";
                break;
            case "kickip":
                permission = "widcore.kickip";
                break;
            case "banip":
                permission = "widcore.banip";
                break;
            case "unbanip":
                permission = "widcore.unbanip";
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

        java.util.UUID exactUUID = commandName.equals("unban")
                ? punishmentManager.getBannedUUIDByExactName(args[0])
                : punishmentManager.getMutedUUIDByExactName(args[0]);

        OfflinePlayer target;
        if (exactUUID != null) {
            target = Bukkit.getOfflinePlayer(exactUUID);
        } else {
            target = Bukkit.getOfflinePlayer(args[0]);
        }

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("punishment.never-played"));
            return;
        }

        boolean success;
        if (commandName.equals("unban")) {
            success = punishmentManager.unbanPlayer(target.getUniqueId(), sender.getName());
        } else {
            success = punishmentManager.unmutePlayer(target.getUniqueId(), sender.getName());
        }

        if (success) {
            FileConfiguration config = commandName.equals("unban")
                    ? punishmentManager.getBanConfig()
                    : punishmentManager.getMuteConfig();
            boolean broadcastEnabled = config.getBoolean("broadcast", true);
            if (broadcastEnabled) {
                String broadcastKey = commandName.equals("unban") ? "messages.unban-broadcast" : "messages.unmute-broadcast";
                String broadcastMsg = config.getString(broadcastKey, "");
                if (broadcastMsg != null && !broadcastMsg.isEmpty()) {
                    broadcastMsg = broadcastMsg
                            .replace("%target%", target.getName() != null ? target.getName() : args[0])
                            .replace("%player%", sender.getName());
                    TextParser.broadcast(broadcastMsg);
                }
            }
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("punishment.remove-success")
                    .replace("%player%", target.getName() != null ? target.getName() : args[0])
                    .replace("%type%", actionPast));
        } else {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("punishment.not-punished")
                    .replace("%player%", target.getName() != null ? target.getName() : args[0])
                    .replace("%type%", actionPast));
        }
    }

    private long getGroupMaxDuration(CommandSender sender, org.bukkit.configuration.file.FileConfiguration config) {
        if (!(sender instanceof Player)) {
            return -1L;
        }
        Player player = (Player) sender;
        org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("group-max-duration");
        if (section == null) return -1L;

        String primaryGroup = "default";
        if (plugin.getChatMetaManager() != null) {
            String g = plugin.getChatMetaManager().getPrimaryGroup(player);
            if (g != null && !g.isEmpty()) primaryGroup = g.toLowerCase();
        }

        if (section.contains(primaryGroup)) {
            String maxStr = section.getString(primaryGroup);
            if (maxStr == null) return -1L;
            return punishmentManager.parseDuration(maxStr);
        }

        if (section.contains("default")) {
            String maxStr = section.getString("default");
            if (maxStr == null) return -1L;
            return punishmentManager.parseDuration(maxStr);
        }

        return -1L;
    }

    private void handleBanWithOptionalDuration(CommandSender sender, OfflinePlayer target, String[] args) {
        long maxDuration = getGroupMaxDuration(sender, punishmentManager.getBanConfig());

        if (args.length >= 2) {
            long duration = punishmentManager.parseDuration(args[1]);
            if (duration > 0) {
                if (maxDuration > 0 && duration > maxDuration) {
                    String maxStr = punishmentManager.formatDuration(maxDuration);
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("punishment.usage.ban-duration-exceeded")
                                    .replace("%max%", maxStr));
                    return;
                }
                String reason = (args.length > 2)
                        ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                        : plugin.getLanguageManager().getMessage("punishment.default-reason");
                punishmentManager.tempBanPlayer(target, sender, duration, reason);
                return;
            }
        }

        if (maxDuration > 0) {
            String maxStr = punishmentManager.formatDuration(maxDuration);
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("punishment.usage.ban-duration-exceeded")
                            .replace("%max%", maxStr));
            return;
        }
        if (!sender.hasPermission("widcore.ban.permanent")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.ban.permanent");
            return;
        }
        String reason = (args.length > 1)
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : plugin.getLanguageManager().getMessage("punishment.default-reason");
        punishmentManager.banPlayer(target, sender, reason);
    }

    private void handleMuteWithOptionalDuration(CommandSender sender, OfflinePlayer target, String[] args) {
        long maxDuration = getGroupMaxDuration(sender, punishmentManager.getMuteConfig());

        if (args.length >= 2) {
            long duration = punishmentManager.parseDuration(args[1]);
            if (duration > 0) {
                if (maxDuration > 0 && duration > maxDuration) {
                    String maxStr = punishmentManager.formatDuration(maxDuration);
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("punishment.usage.mute-duration-exceeded")
                                    .replace("%max%", maxStr));
                    return;
                }
                String reason = (args.length > 2)
                        ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                        : plugin.getLanguageManager().getMessage("punishment.default-reason");
                punishmentManager.tempMutePlayer(target, sender, duration, reason);
                return;
            }
        }

        if (maxDuration > 0) {
            String maxStr = punishmentManager.formatDuration(maxDuration);
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("punishment.usage.mute-duration-exceeded")
                            .replace("%max%", maxStr));
            return;
        }
        if (!sender.hasPermission("widcore.mute.permanent")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.mute.permanent");
            return;
        }
        String reason = (args.length > 1)
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : plugin.getLanguageManager().getMessage("punishment.default-reason");
        punishmentManager.mutePlayer(target, sender, reason);
    }

    private void handleKick(CommandSender sender, Player target, String reason) {
        punishmentManager.kickPlayer(target, sender, reason);
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

        FileConfiguration kickConfig = punishmentManager.getKickConfig();
        String msg = kickConfig.getString("messages.kickall-success",
                "&e%count% &aplayers kicked. Reason: &f%reason%")
                .replace("%count%", String.valueOf(count))
                .replace("%reason%", reason);
        Main.sendMessage(this.plugin, sender, msg);
    }

    private void handleIPCommand(CommandSender sender, String commandKey, String[] args) {
        if (args.length < 1) {
            sendUsage(sender, commandKey);
            return;
        }

        String input = args[0];
        String ip;

        if (commandKey.equals("unmuteip")) {
            if (input.matches("^(\\d{1,3}\\.){3}\\d{1,3}$")) {
                ip = input;
            } else {
                ip = punishmentManager.getMutedIPByPlayerName(input);
                if (ip == null) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", input));
                    return;
                }
            }
        } else if (commandKey.equals("unbanip")) {
            if (input.matches("^(\\d{1,3}\\.){3}\\d{1,3}$") || input.contains(":")) {
                ip = input;
            } else {
                ip = punishmentManager.getBannedIPByPlayerName(input);
                if (ip == null) {
                    String resolved = punishmentManager.resolveIpForPlayerName(input);
                    if (resolved != null && punishmentManager.isIPBanned(resolved)) {
                        ip = resolved;
                    }
                }
                if (ip == null) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", input));
                    return;
                }
            }
        } else {
            if (input.matches("^(\\d{1,3}\\.){3}\\d{1,3}$")) {
                ip = input;
            } else if (commandKey.equals("tempbanip") || commandKey.equals("tempmuteip")) {
                ip = punishmentManager.resolveIpForPlayerName(input);
                if (ip == null) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", input));
                    return;
                }
            } else {
                Player targetPlayer = Bukkit.getPlayer(input);
                if (targetPlayer == null) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", input));
                    return;
                }
                ip = punishmentManager.getPlayerIP(targetPlayer);
                if (ip == null) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("punishment.invalid-ip").replace("%ip%", input));
                    return;
                }
            }
        }

        FileConfiguration muteConfig = punishmentManager.getMuteConfig();
        FileConfiguration banConfig = punishmentManager.getBanConfig();
        FileConfiguration kickConfig = punishmentManager.getKickConfig();

        switch (commandKey) {
            case "muteip": {
                if (punishmentManager.isIPMuted(ip)) {
                    String msg = muteConfig.getString("messages.ip-already-muted",
                            "&c%player%&c's IP is already muted.")
                            .replace("%player%", input).replace("%ip%", ip);
                    Main.sendMessage(this.plugin, sender, msg);
                    return;
                }
                String reason = (args.length > 1)
                        ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                        : plugin.getLanguageManager().getMessage("punishment.default-reason");
                punishmentManager.muteIP(ip, sender, reason);
                String msg = muteConfig.getString("messages.muteip-success",
                        "&a%player% &amuted via IP. Reason: &f%reason%")
                        .replace("%player%", input).replace("%ip%", ip).replace("%reason%", reason);
                Main.sendMessage(this.plugin, sender, msg);
                break;
            }
            case "unmuteip": {
                if (!punishmentManager.isIPMuted(ip)) {
                    String msg = muteConfig.getString("messages.ip-not-muted",
                            "&c%player%&c's IP is not muted.")
                            .replace("%player%", input).replace("%ip%", ip);
                    Main.sendMessage(this.plugin, sender, msg);
                    return;
                }
                boolean success = punishmentManager.unmuteIP(ip, sender.getName());
                if (success) {
                    if (muteConfig.getBoolean("broadcast", true)) {
                        String broadcastMsg = muteConfig.getString("messages.unmuteip-broadcast", "");
                        if (broadcastMsg != null && !broadcastMsg.isEmpty()) {
                            broadcastMsg = broadcastMsg
                                    .replace("%target%", input).replace("%ip%", ip)
                                    .replace("%player%", sender.getName());
                            TextParser.broadcast(broadcastMsg);
                        }
                    }
                    String msg = muteConfig.getString("messages.unmuteip-success",
                            "&a%player%&a's IP mute removed.")
                            .replace("%player%", input).replace("%ip%", ip);
                    Main.sendMessage(this.plugin, sender, msg);
                }
                break;
            }
            case "kickip": {
                String reason = (args.length > 1)
                        ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                        : plugin.getLanguageManager().getMessage("punishment.default-reason");
                int count = punishmentManager.kickPlayersWithIP(ip, sender, reason);
                String msg = kickConfig.getString("messages.kickip-success",
                        "&aKicked &e%count% &aplayer(s) with IP &e%ip%&a. Reason: &f%reason%")
                        .replace("%player%", input).replace("%ip%", ip)
                        .replace("%count%", String.valueOf(count))
                        .replace("%reason%", reason);
                Main.sendMessage(this.plugin, sender, msg);
                break;
            }
            case "banip": {
                if (punishmentManager.isIPBanned(ip)) {
                    String msg = banConfig.getString("messages.ip-already-banned",
                            "&c%player%&c's IP is already banned.")
                            .replace("%player%", input).replace("%ip%", ip);
                    Main.sendMessage(this.plugin, sender, msg);
                    return;
                }
                String reason = (args.length > 1)
                        ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                        : plugin.getLanguageManager().getMessage("punishment.default-reason");
                punishmentManager.banIP(ip, sender, reason);
                String msg = banConfig.getString("messages.banip-success",
                        "&a%player% &abanned via IP. Reason: &f%reason%")
                        .replace("%player%", input).replace("%ip%", ip).replace("%reason%", reason);
                Main.sendMessage(this.plugin, sender, msg);
                break;
            }
            case "unbanip": {
                if (!punishmentManager.isIPBanned(ip)) {
                    String msg = banConfig.getString("messages.ip-not-banned",
                            "&c%player%&c's IP is not banned.")
                            .replace("%player%", input).replace("%ip%", ip);
                    Main.sendMessage(this.plugin, sender, msg);
                    return;
                }
                boolean success = punishmentManager.unbanIP(ip, sender.getName());
                if (success) {
                    if (banConfig.getBoolean("broadcast", true)) {
                        String broadcastMsg = banConfig.getString("messages.unbanip-broadcast", "");
                        if (broadcastMsg != null && !broadcastMsg.isEmpty()) {
                            broadcastMsg = broadcastMsg
                                    .replace("%target%", input).replace("%ip%", ip)
                                    .replace("%player%", sender.getName());
                            TextParser.broadcast(broadcastMsg);
                        }
                    }
                    String msg = banConfig.getString("messages.unbanip-success",
                            "&a%player%&a's IP ban removed.")
                            .replace("%player%", input).replace("%ip%", ip);
                    Main.sendMessage(this.plugin, sender, msg);
                }
                break;
            }
            case "tempbanip": {
                if (args.length < 2) {
                    sendUsage(sender, commandKey);
                    return;
                }
                long duration = punishmentManager.parseDuration(args[1]);
                if (duration <= 0) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("punishment.invalid-duration-format"));
                    return;
                }
                if (punishmentManager.isIPBanned(ip)) {
                    String msg = banConfig.getString("messages.ip-already-banned",
                            "&cIP &e%ip% &cis already banned.").replace("%ip%", ip);
                    Main.sendMessage(this.plugin, sender, msg);
                    return;
                }
                String reason = (args.length > 2)
                        ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                        : plugin.getLanguageManager().getMessage("punishment.default-reason");
                punishmentManager.tempBanIP(ip, sender, duration, reason);
                String durationStr = punishmentManager.formatDuration(duration);
                String msg = banConfig.getString("messages.tempbanip-success",
                        "&aIP &e%ip% &atemporarily banned for &e%duration%&a. Reason: &f%reason%")
                        .replace("%ip%", ip)
                        .replace("%duration%", durationStr)
                        .replace("%reason%", reason);
                Main.sendMessage(this.plugin, sender, msg);
                break;
            }
            case "tempmuteip": {
                if (args.length < 2) {
                    sendUsage(sender, commandKey);
                    return;
                }
                long duration = punishmentManager.parseDuration(args[1]);
                if (duration <= 0) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("punishment.invalid-duration-format"));
                    return;
                }
                if (punishmentManager.isIPMuted(ip)) {
                    String msg = muteConfig.getString("messages.ip-already-muted",
                            "&cIP &e%ip% &cis already muted.").replace("%ip%", ip);
                    Main.sendMessage(this.plugin, sender, msg);
                    return;
                }
                String reason = (args.length > 2)
                        ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                        : plugin.getLanguageManager().getMessage("punishment.default-reason");
                punishmentManager.tempMuteIP(ip, sender, duration, reason);
                String durationStr = punishmentManager.formatDuration(duration);
                String msg = muteConfig.getString("messages.tempmuteip-success",
                        "&aIP &e%ip% &atemporarily muted for &e%duration%&a. Reason: &f%reason%")
                        .replace("%ip%", ip)
                        .replace("%duration%", durationStr)
                        .replace("%reason%", reason);
                Main.sendMessage(this.plugin, sender, msg);
                break;
            }
        }
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
            } else if (commandKey.equals("unmuteip")) {
                StringUtil.copyPartialMatches(args[0], punishmentManager.getMutedIPPlayerNames(), completions);
            } else if (commandKey.equals("unbanip")) {
                StringUtil.copyPartialMatches(args[0], punishmentManager.getBannedIPPlayerNames(), completions);
            } else if (commandKey.equals("muteip") || commandKey.equals("kickip") || commandKey.equals("banip")) {
                List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[0], playerNames, completions);
            } else {
                List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[0], playerNames, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
        @SuppressWarnings("unused")
    private static final String __Wc6d8x2 = "\u0077\u0069" + "\u0064\u006e" + "\u0065\u0065\u0073";

}