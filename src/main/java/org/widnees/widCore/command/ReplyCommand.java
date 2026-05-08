package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.MessageManager;

import java.util.UUID;

public class ReplyCommand implements CommandExecutor {

    private final Main plugin;
    private final MessageManager messageManager;

    public ReplyCommand(Main plugin, MessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (!sender.hasPermission("widcore.r")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.r");
            return true;
        }

        if (args.length == 0) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("reply.usage"));
            return true;
        }

        UUID senderUUID = sender instanceof Player ? ((Player) sender).getUniqueId() : MessageManager.CONSOLE_UUID;

        UUID lastMessagedPlayer = messageManager.getLastMessagedPlayer(senderUUID);
        if (lastMessagedPlayer == null) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("reply.no-target"));
            return true;
        }

        Player target = Bukkit.getPlayer(lastMessagedPlayer);
        if (target == null || !target.isOnline()) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("reply.target-offline"));
            return true;
        }

        String message = String.join(" ", args);
        messageManager.sendMessage(sender, target, message);
        return true;
    }
}