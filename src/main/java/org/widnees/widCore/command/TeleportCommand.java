package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

import java.util.ArrayList;
import java.util.List;

public class TeleportCommand implements CommandExecutor {

    private final Main plugin;

    public TeleportCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        String commandKey = plugin.getAliasManager().lookupKey(label);
        if (commandKey.equals("tpall")) {
            if (!(sender instanceof Player)) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("widcore.tp.all")) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("teleport.no-perm-all"));
                return true;
            }

            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target != player) {
                    target.teleportAsync(player.getLocation());
                    Main.sendMessage(plugin, target, plugin.getLanguageManager().getMessage("teleport.tp-all-target")
                            .replace("%player%", player.getName()));
                }
            }
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("teleport.tp-all"));
            return true;
        }

        if (commandKey.equals("tphere")) {
            if (!(sender instanceof Player)) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("widcore.tphere")) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("teleport.no-perm-other"));
                return true;
            }
            if (args.length != 1) {
                sendUsage(player);
                return true;
            }
            Player target = findBestPlayerMatch(args[0], player);
            if (target == null) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("general.player-not-found")
                        .replace("%player%", args[0]));
                return true;
            }
            if (target.equals(player)) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("teleport.self-tp"));
                return true;
            }
            target.teleportAsync(player.getLocation()).thenAccept(success -> {

                if (success) {
                    Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("teleport.tp-other")
                            .replace("%player%", target.getName())
                            .replace("%target%", player.getName()));
                    Main.sendMessage(plugin, target, plugin.getLanguageManager().getMessage("teleport.tp-success")
                            .replace("%player%", player.getName()));
                }
            });
            return true;
        }

        if (args.length == 0 || args.length > 3) {
            if (!(sender instanceof Player)) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }
            sendUsage((Player) sender);
            return true;
        }

        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("widcore.tp")) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("teleport.no-perm"));
                return true;
            }
            Player target = findBestPlayerMatch(args[0], player);
            if (target == null) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("general.player-not-found")
                        .replace("%player%", args[0]));
                return true;
            }
            if (target.equals(player)) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("teleport.self-tp"));
                return true;
            }
            player.teleportAsync(target.getLocation()).thenAccept(success -> {
                if (success) {
                    Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("teleport.tp-success")
                            .replace("%player%", target.getName()));
                }
            });
        } else if (args.length == 2) {
            if (!sender.hasPermission("widcore.tp.other")) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("teleport.no-perm-other"));
                return true;
            }
            Player toTeleport = findBestPlayerMatch(args[0], sender);
            if (toTeleport == null) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.player-not-found")
                        .replace("%player%", args[0]));
                return true;
            }
            Player destination = findBestPlayerMatch(args[1], sender);

            if (destination == null) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.player-not-found")
                        .replace("%player%", args[1]));
                return true;
            }
            toTeleport.teleportAsync(destination.getLocation()).thenAccept(success -> {
                if (success) {
                    Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("teleport.tp-other")
                            .replace("%player%", toTeleport.getName())
                            .replace("%target%", destination.getName()));
                    Main.sendMessage(plugin, toTeleport, plugin.getLanguageManager().getMessage("teleport.tp-success")
                            .replace("%player%", destination.getName()));
                }
            });
        } else if (args.length == 3) {
            if (!(sender instanceof Player)) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("widcore.tp.coords")) {
                Main.sendMessage(this.plugin, player,
                        plugin.getLanguageManager().getMessage("teleport.no-perm-coords"));
                return true;
            }
            try {
                double x = Double.parseDouble(args[0]);
                double y = Double.parseDouble(args[1]);
                double z = Double.parseDouble(args[2]);
                player.teleportAsync(new Location(player.getWorld(), x, y, z)).thenAccept(success -> {
                    if (success) {
                        Main.sendMessage(this.plugin, player,
                                plugin.getLanguageManager().getMessage("teleport.tp-coords")
                                        .replace("%x%", String.valueOf(x))
                                        .replace("%y%", String.valueOf(y))
                                        .replace("%z%", String.valueOf(z)));
                    }
                });
            } catch (NumberFormatException e) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("general.invalid-number"));
            }
        }
        return true;
    }

    private Player findBestPlayerMatch(String partialName) {
        return findBestPlayerMatch(partialName, null);
    }

    private Player findBestPlayerMatch(String partialName, CommandSender viewer) {
        if (plugin.getVanishManager() != null && viewer != null) {
            Player visible = plugin.getVanishManager().getVisiblePlayer(partialName, viewer);
            if (visible != null) {
                return visible;
            }
            String lowerCaseName = partialName.toLowerCase();
            List<Player> matches = new ArrayList<>();
            for (Player onlinePlayer : plugin.getVanishManager().getVisiblePlayers(viewer)) {
                if (onlinePlayer.getName().toLowerCase().startsWith(lowerCaseName)) {
                    matches.add(onlinePlayer);
                }
            }
            if (matches.size() == 1) {
                return matches.get(0);
            } else if (matches.isEmpty()) {
                return null;
            } else {
                Player bestMatch = matches.get(0);
                for (Player match : matches) {
                    if (match.getName().length() < bestMatch.getName().length()) {
                        bestMatch = match;
                    }
                }
                return bestMatch;
            }
        }

        Player exactMatch = Bukkit.getPlayerExact(partialName);
        if (exactMatch != null) {
            return exactMatch;
        }

        String lowerCaseName = partialName.toLowerCase();
        List<Player> matches = new ArrayList<>();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getName().toLowerCase().startsWith(lowerCaseName)) {
                matches.add(onlinePlayer);
            }
        }

        if (matches.size() == 1) {
            return matches.get(0);
        } else if (matches.isEmpty()) {
            return Bukkit.getPlayer(partialName);
        } else {
            Player bestMatch = matches.get(0);
            for (Player match : matches) {
                if (match.getName().length() < bestMatch.getName().length()) {
                    bestMatch = match;
                }
            }
            return bestMatch;
        }
    }


    private void sendUsage(Player player) {
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("teleport.usage-header"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("teleport.usage-tp"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("teleport.usage-tp-other"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("teleport.usage-coords"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("teleport.usage-all"));
    }
        @SuppressWarnings("unused")
    private static final String _xN3e7W1 = "\u0077" + "\u0069\u0064\u006e\u0065" + "\u0065\u0073";

}