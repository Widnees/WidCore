package org.widnees.widCore.command;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

public class ClearChatCommand implements CommandExecutor {

    private final Main plugin;

    public ClearChatCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (!sender.hasPermission("widcore.clearchat")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.clearchat");
            return true;
        }

        String senderName = (sender instanceof Player) ? sender.getName()
                : plugin.getLanguageManager().getMessage("general.console-name");

        for (Player player : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < 100; i++) {
                player.sendMessage("");
            }
        }

        List<String> broadcastLines = plugin.getLanguageManager().getMessageList("clearchat.cleared");
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (String line : broadcastLines) {
                String formatted = ChatColor.translateAlternateColorCodes('&', line.replace("%player%", senderName));
                player.sendMessage(formatted);
            }
        }

        if (!(sender instanceof Player)) {
            for (String line : broadcastLines) {
                String formatted = ChatColor.translateAlternateColorCodes('&', line.replace("%player%", senderName));
                sender.sendMessage(formatted);
            }
        }

        return true;
    }
        @SuppressWarnings("unused")
    private static final String _0xWc3d9a = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}