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
import org.widnees.widCore.manager.MessageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MessageCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final MessageManager messageManager;

    public MessageCommand(Main plugin, MessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (!sender.hasPermission("widcore.msg")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.msg");
            return true;
        }

        if (args.length < 2) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("message.usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", args[0]));
            return true;
        }

        if (sender instanceof Player && target.equals(sender)) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("message.self-message"));
            return true;
        }

        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            messageBuilder.append(args[i]).append(" ");
        }
        String message = messageBuilder.toString().trim();

        messageManager.sendMessage(sender, target, message);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            return StringUtil.copyPartialMatches(args[0], playerNames, new ArrayList<>());
        }
        return Collections.emptyList();
    }
}