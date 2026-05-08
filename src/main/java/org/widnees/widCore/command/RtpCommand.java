package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.RtpManager;
import org.widnees.widCore.manager.RtpRetryService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RtpCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final RtpManager rtpManager;

    public RtpCommand(Main plugin, RtpManager rtpManager) {
        this.plugin = plugin;
        this.rtpManager = rtpManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded())
            return true;

        if (args.length == 0) {
            FileConfiguration config = plugin.getConfigManager().getModuleConfig("rtp");
            if (sender instanceof Player && config.getBoolean("default-world.enabled", false)) {
                String worldName = config.getString("default-world.world", "world");
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Player player = (Player) sender;
                    String basePerm = plugin.getAliasManager().getPermission("rtp");
                    if (!player.hasPermission(basePerm)) {
                        Main.sendNoPermission(plugin, player, basePerm);
                        return true;
                    }

                    String worldPerm = "widcore.rtp." + worldName;
                    if (!player.hasPermission(worldPerm)) {
                        Main.sendNoPermission(plugin, player, worldPerm);
                        return true;
                    }

                    RtpRetryService.queue(player, world);
                    return true;
                }
            }

            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("rtp.usage"));
            return true;
        }

        String worldName = args[0];
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("rtp.world-not-found")
                    .replace("%world%", worldName));
            return true;
        }

        Player targetPlayer;
        String otherPerm = plugin.getAliasManager().getSubpermission("rtp", "other");
        String basePerm = plugin.getAliasManager().getPermission("rtp");

        if (args.length >= 2) {
            
            if (!sender.hasPermission(otherPerm)) {
                Main.sendNoPermission(plugin, sender, otherPerm);
                return true;
            }

            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("general.player-not-found")
                        .replace("%player%", args[1]));
                return true;
            }

            RtpRetryService.queue(targetPlayer, world);
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("rtp.sent-other")
                    .replace("%player%", targetPlayer.getName())
                    .replace("%world%", worldName));
        } else {
            
            if (!(sender instanceof Player)) {
                Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }

            targetPlayer = (Player) sender;

            if (!targetPlayer.hasPermission(basePerm)) {
                Main.sendNoPermission(plugin, targetPlayer, basePerm);
                return true;
            }

            String worldPerm = "widcore.rtp." + worldName;
            if (!targetPlayer.hasPermission(worldPerm)) {
                Main.sendNoPermission(plugin, targetPlayer, worldPerm);
                return true;
            }

            RtpRetryService.queue(targetPlayer, world);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String otherPerm = plugin.getAliasManager().getSubpermission("rtp", "other");

        if (args.length == 1) {
            
            List<String> worldNames = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                worldNames.add(world.getName());
            }
            StringUtil.copyPartialMatches(args[0], worldNames, completions);
        } else if (args.length == 2 && sender.hasPermission(otherPerm)) {
            
            List<String> playerNames = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                playerNames.add(player.getName());
            }
            StringUtil.copyPartialMatches(args[1], playerNames, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}
