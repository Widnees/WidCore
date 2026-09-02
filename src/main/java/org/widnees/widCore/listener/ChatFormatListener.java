package org.widnees.widCore.listener;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.TextParser;
import org.widnees.widCore.util.VersionSupport;

public class ChatFormatListener implements Listener {

    public static final java.util.Set<Integer> FORMAT_CANCELLED_EVENTS =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private final Main plugin;

    public ChatFormatListener(Main plugin, FileConfiguration moduleConfig) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!ConfigManager.isConfigLoaded())
            return;
        if (!plugin.getConfig().getBoolean("features.chatformat", false))
            return;

        VersionSupport vs = plugin.getVersionSupport();
        String legacyDeathMessage = vs.getDeathMessageString(event);
        if (legacyDeathMessage == null)
            return;

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            legacyDeathMessage = PlaceholderAPI.setPlaceholders(event.getEntity(), legacyDeathMessage);
        }
        vs.setDeathMessage(event, TextParser.parse(legacyDeathMessage), legacyDeathMessage);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        if (!ConfigManager.isConfigLoaded())
            return;

        if (plugin.getConfig().getBoolean("features.show-item", false) && hasActiveShowItemTags(event)) {
            return;
        }

        Player player = event.getPlayer();

        String formatString = getFormatForPlayer(plugin, player);
        if (formatString == null)
            formatString = "<{prefix}{name}&r> {message}";

        String rawMessage = stripPlayerColorCodes(event.getMessage());
        formatString = applyChatPlaceholders(plugin, player, formatString, rawMessage);

        FORMAT_CANCELLED_EVENTS.add(System.identityHashCode(event));
        event.setCancelled(true);

        Component chatComponent = TextParser.parse(formatString);

        for (Player p : event.getRecipients()) {
            p.sendMessage(chatComponent);
        }
        Bukkit.getConsoleSender().sendMessage(chatComponent);
    }

    public static boolean hasActiveShowItemTags(AsyncPlayerChatEvent event) {
        String msg = event.getMessage();
        Player player = event.getPlayer();
        if ((msg.contains("[i]") || msg.contains("[item]")) && player.hasPermission("widcore.showitem.i")) {
            return true;
        }
        if (msg.contains("[inv]") && player.hasPermission("widcore.showitem.inv")) {
            return true;
        }
        if (msg.contains("[ec]") && player.hasPermission("widcore.showitem.ec")) {
            return true;
        }
        return false;
    }

    public static String getFormatForPlayer(Main plugin, Player player) {
        FileConfiguration chatConfig = plugin.getConfigManager().getModuleConfig("chat");
        if (chatConfig == null) {
            return "<{prefix}{name}&r> {message}";
        }

        String primaryGroup = plugin.getChatMetaManager().getPrimaryGroup(player);

        if (primaryGroup != null && !primaryGroup.isEmpty()) {
            ConfigurationSection formatsSection = chatConfig.getConfigurationSection("group-formats");
            if (formatsSection != null) {
                String groupFormat = formatsSection.getString(primaryGroup);
                if (groupFormat != null && !groupFormat.isEmpty()) {
                    return groupFormat;
                }
            }
        }

        String defaultFormat = chatConfig.getString("chat-format");

        return (defaultFormat != null && !defaultFormat.isEmpty())
                ? defaultFormat
                : "<{prefix}{name}&r> {message}";
    }

    public static String stripPlayerColorCodes(String rawMessage) {
        if (rawMessage == null) {
            return "";
        }
        rawMessage = rawMessage.replaceAll("&x(&[0-9a-fA-F]){6}", "");
        rawMessage = rawMessage.replaceAll("&#[0-9a-fA-F]{6}", "");
        rawMessage = rawMessage.replaceAll("(?i)&[0-9a-fA-FkKlLmMnNoOrR]", "");
        return rawMessage;
    }

    public static String applyChatPlaceholders(Main plugin, Player player, String formatString, String message) {
        String prefix = plugin.getChatMetaManager().getPrefix(player);
        String suffix = plugin.getChatMetaManager().getSuffix(player);

        prefix = (prefix == null) ? "" : prefix;
        suffix = (suffix == null) ? "" : suffix;
        if (message == null) {
            message = "";
        }

        formatString = formatString.replace("{prefix}", prefix)
                .replace("{suffix}", suffix)
                .replace("{name}", player.getName())
                .replace("{message}", message);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            formatString = PlaceholderAPI.setPlaceholders(player, formatString);
        }

        return formatString;
    }

    @SuppressWarnings("unused")
    private static final String __wN7e3x9 = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}
