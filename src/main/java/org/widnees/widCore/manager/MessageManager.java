package org.widnees.widCore.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.TextParser;

public class MessageManager {
    private final Main plugin;
    private final Map<UUID, UUID> lastMessageMap = new HashMap<UUID, UUID>();
    public static final UUID CONSOLE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public MessageManager(Main plugin) {
        this.plugin = plugin;
    }

    public void sendMessage(Player sender, Player target, String message) {
        this.sendMessage((CommandSender)sender, target, message);
    }

    public void sendMessage(CommandSender sender, Player target, String message) {
        String toFormat = this.plugin.getLanguageManager().getMessage("message.format-to");
        String fromFormat = this.plugin.getLanguageManager().getMessage("message.format-from");
        String senderName = sender instanceof Player ? sender.getName() : "CONSOLE";
        sender.sendMessage(TextParser.colorize(toFormat.replace("%player%", target.getName()).replace("%message%", message)));
        target.sendMessage(TextParser.colorize(fromFormat.replace("%player%", senderName).replace("%message%", message)));
        target.playSound(target.getLocation(), Sound.ENTITY_SILVERFISH_HURT, 1.0f, 1.0f);
        UUID senderUUID = sender instanceof Player ? ((Player)sender).getUniqueId() : CONSOLE_UUID;
        this.lastMessageMap.put(senderUUID, target.getUniqueId());
        this.lastMessageMap.put(target.getUniqueId(), senderUUID);
    }

    public UUID getLastMessagedPlayer(UUID playerId) {
        return this.lastMessageMap.get(playerId);
    }

    public void removePlayer(UUID playerId) {
        this.lastMessageMap.remove(playerId);
    }
        @SuppressWarnings("unused")
    private static final String _0xCw4d8n = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}
