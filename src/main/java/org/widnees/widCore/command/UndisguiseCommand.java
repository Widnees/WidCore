package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

import java.util.*;
import java.util.stream.Collectors;

public class UndisguiseCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public UndisguiseCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) return true;

        if (!sender.hasPermission("widcore.disguise")) {
            Main.sendNoPermission(plugin, sender, "widcore.disguise");
            return true;
        }

        Player target;
        if (args.length >= 1) {

            if (!sender.hasPermission("widcore.disguise.other")) {
                Main.sendMessage(plugin, sender,
                        plugin.getLanguageManager().getMessage("disguise.no-perm-other"));
                return true;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                Main.sendMessage(plugin, sender,
                        plugin.getLanguageManager().getMessage("general.player-not-found")
                                .replace("%player%", args[0]));
                return true;
            }
        } else {

            if (!(sender instanceof Player)) {
                Main.sendMessage(plugin, sender,
                        plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }
            target = (Player) sender;
        }

        if (!plugin.getDisguiseManager().isDisguised(target)) {
            Main.sendMessage(plugin, sender,
                    plugin.getLanguageManager().getMessage("disguise.not-disguised"));
            return true;
        }

        plugin.getDisguiseManager().undisguise(target);

        if (sender.equals(target)) {
            Main.sendMessage(plugin, sender,
                    plugin.getLanguageManager().getMessage("disguise.disabled"));
        } else {
            Main.sendMessage(plugin, sender,
                    plugin.getLanguageManager().getMessage("disguise.other-disabled")
                            .replace("%player%", target.getName()));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {

            List<String> disguisedNames = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> plugin.getDisguiseManager().isDisguised(p))
                    .map(Player::getName)
                    .collect(Collectors.toList());
            StringUtil.copyPartialMatches(args[0], disguisedNames, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}
