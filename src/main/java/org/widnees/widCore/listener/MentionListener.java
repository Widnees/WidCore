package org.widnees.widCore.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementDisplayType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.database.BinaryDataManager;
import org.widnees.widCore.manager.TextParser;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MentionListener implements Listener {

    private final Main plugin;
    private final FileConfiguration config;

    private final Map<Integer, MentionContext> pendingMentions = new ConcurrentHashMap<>();

    private final Map<UUID, Map<UUID, Long>> notifCooldowns = new ConcurrentHashMap<>();

    private static class MentionContext {
        final List<Player> mentioned;
        final String originalMessage;

        MentionContext(List<Player> mentioned, String originalMessage) {
            this.mentioned = mentioned;
            this.originalMessage = originalMessage;
        }
    }

    public MentionListener(Main plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChatLow(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        boolean requireAt = config.getBoolean("require-at-symbol", true);
        String highlightColor = config.getString("highlight-color", "&b&l");
        String visibility = config.getString("highlight-visibility", "MENTIONED_ONLY").toUpperCase();

        List<Player> mentioned = detectMentioned(message, requireAt, event.getPlayer());
        if (mentioned.isEmpty()) return;

        if ("EVERYONE".equals(visibility)) {
            String modified = applyHighlight(message, mentioned, highlightColor, requireAt);
            event.setMessage(modified);
            pendingMentions.put(System.identityHashCode(event), new MentionContext(mentioned, modified));
        } else {
            event.getRecipients().removeAll(mentioned);
            pendingMentions.put(System.identityHashCode(event), new MentionContext(mentioned, message));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChatMonitor(AsyncPlayerChatEvent event) {
        int key = System.identityHashCode(event);
        MentionContext ctx = pendingMentions.remove(key);
        if (ctx == null || ctx.mentioned.isEmpty()) return;

        String visibility = config.getString("highlight-visibility", "MENTIONED_ONLY").toUpperCase();
        String highlightColor = config.getString("highlight-color", "&b&l");
        String senderName = event.getPlayer().getName();

        for (Player target : ctx.mentioned) {
            if (!target.isOnline()) continue;

            if ("MENTIONED_ONLY".equals(visibility)) {
                String highlightedMessage = applyHighlight(ctx.originalMessage,
                        Collections.singletonList(target), highlightColor,
                        config.getBoolean("require-at-symbol", true));
                target.sendMessage(buildHighlightedChat(event, highlightedMessage));
            }

            final UUID targetUUID = target.getUniqueId();
            final UUID senderUUID = event.getPlayer().getUniqueId();
            final BinaryDataManager.MentionPrefs prefs = getPrefs(target);
            Bukkit.getScheduler().runTask(plugin, () -> {

                long now = System.currentTimeMillis();
                Map<UUID, Long> senderCooldowns = notifCooldowns.computeIfAbsent(targetUUID, k -> new ConcurrentHashMap<>());
                Long expiry = senderCooldowns.get(senderUUID);
                if (expiry != null && now < expiry) return;

                long cooldownMs = config.getLong("notification-cooldown-ms", 5000L);
                senderCooldowns.put(senderUUID, now + cooldownMs);

                if (prefs.title)     sendTitle(target, senderName);
                if (prefs.actionbar) sendActionBar(target, senderName);
                if (prefs.toast)     sendToast(target, senderName);
                if (prefs.sound)     playSound(target);
            });
        }
    }

    private BinaryDataManager.MentionPrefs getPrefs(Player player) {
        BinaryDataManager.MentionPrefsData data = plugin.getMentionPrefsData();
        if (data == null) return new BinaryDataManager.MentionPrefs();
        return data.players.getOrDefault(player.getUniqueId(), new BinaryDataManager.MentionPrefs());
    }

    private List<Player> detectMentioned(String message, boolean requireAt, Player sender) {
        List<Player> result = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(sender)) continue;
            if (!matches(message, online.getName(), requireAt)) continue;

            if (!getPrefs(online).enabled) {

                Bukkit.getScheduler().runTask(plugin, () -> {
                    String msg = plugin.getLanguageManager().getMessage("mention.sender-disabled-notify")
                            .replace("%player%", online.getName());
                    Main.sendMessage(plugin, sender, msg);
                });
                continue;
            }
            result.add(online);
        }
        return result;
    }

    private boolean matches(String message, String name, boolean requireAt) {
        String escapedName = Pattern.quote(name);
        Pattern pattern = requireAt
                ? Pattern.compile("@" + escapedName + "(?=[^a-zA-Z0-9_]|$)", Pattern.CASE_INSENSITIVE)
                : Pattern.compile("(?<![a-zA-Z0-9_])@?" + escapedName + "(?=[^a-zA-Z0-9_]|$)", Pattern.CASE_INSENSITIVE);
        return pattern.matcher(message).find();
    }

    private String applyHighlight(String message, List<Player> targets, String color, boolean requireAt) {
        String result = message;
        for (Player target : targets) {
            String name = target.getName();
            Pattern pattern = requireAt
                    ? Pattern.compile("@" + Pattern.quote(name) + "(?=[^a-zA-Z0-9_]|$)", Pattern.CASE_INSENSITIVE)
                    : Pattern.compile("(?<![a-zA-Z0-9_])@?" + name + "(?=[^a-zA-Z0-9_]|$)", Pattern.CASE_INSENSITIVE);
            result = pattern.matcher(result).replaceAll(color + name + "&r");
        }
        return result;
    }

    private Component buildHighlightedChat(AsyncPlayerChatEvent event, String highlightedMessage) {
        Player player = event.getPlayer();
        FileConfiguration chatConfig = plugin.getConfigManager().getModuleConfig("chat");

        String primaryGroup = plugin.getChatMetaManager().getPrimaryGroup(player);
        String formatString = null;

        if (primaryGroup != null && !primaryGroup.isEmpty()) {
            org.bukkit.configuration.ConfigurationSection sec = chatConfig.getConfigurationSection("group-formats");
            if (sec != null) formatString = sec.getString(primaryGroup);
        }
        if (formatString == null || formatString.isEmpty()) formatString = chatConfig.getString("chat-format");
        if (formatString == null || formatString.isEmpty()) formatString = "<{prefix}{name}&r> {message}";

        String prefix = plugin.getChatMetaManager().getPrefix(player);
        String suffix = plugin.getChatMetaManager().getSuffix(player);
        prefix = (prefix == null) ? "" : prefix;
        suffix = (suffix == null) ? "" : suffix;

        formatString = formatString
                .replace("{prefix}", prefix)
                .replace("{suffix}", suffix)
                .replace("{name}", player.getName())
                .replace("{message}", highlightedMessage);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            formatString = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, formatString);
        }

        return TextParser.parse(formatString);
    }

    private void sendTitle(Player player, String sender) {
        String titleText = config.getString("title.title", "");
        String subtitleText = config.getString("title.subtitle", "");

        if ((titleText == null || titleText.isEmpty()) && (subtitleText == null || subtitleText.isEmpty())) return;

        titleText = titleText == null ? "" : titleText.replace("{sender}", sender);
        subtitleText = subtitleText == null ? "" : subtitleText.replace("{sender}", sender);

        int fadeIn = config.getInt("title.fade-in", 10);
        int stay = config.getInt("title.stay", 60);
        int fadeOut = config.getInt("title.fade-out", 20);

        player.showTitle(net.kyori.adventure.title.Title.title(
                TextParser.parse(titleText),
                TextParser.parse(subtitleText),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(fadeIn * 50L),
                        java.time.Duration.ofMillis(stay * 50L),
                        java.time.Duration.ofMillis(fadeOut * 50L)
                )
        ));
    }

    private void sendActionBar(Player player, String sender) {
        String message = config.getString("actionbar.message", "");
        if (message == null || message.isEmpty()) return;
        player.sendActionBar(TextParser.parse(message.replace("{sender}", sender)));
    }

    private void sendToast(Player player, String sender) {
        String toastTitle = config.getString("toast.title", "");
        if (toastTitle == null || toastTitle.isEmpty()) return;

        toastTitle = toastTitle.replace("{sender}", sender);

        String itemName = config.getString("toast.item", "BELL");
        String frameName = config.getString("toast.frame", "GOAL");

        Material material;
        try {
            material = Material.valueOf(itemName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.BELL;
        }

        AdvancementDisplayType displayType;
        try {
            displayType = AdvancementDisplayType.valueOf(frameName.toUpperCase());
        } catch (IllegalArgumentException e) {
            displayType = AdvancementDisplayType.GOAL;
        }

        sendFakeToast(player, material, displayType, toastTitle);
    }

    private void sendFakeToast(Player player, Material icon, AdvancementDisplayType type, String title) {
        try {

            NamespacedKey key = new NamespacedKey(plugin,
                    "mt_" + Long.toHexString(System.currentTimeMillis()) + "_"
                    + player.getUniqueId().toString().substring(0, 8).replace("-", ""));

            org.bukkit.UnsafeValues unsafe = Bukkit.getUnsafe();

            String frameStr = type.name().toLowerCase();

            String advJson = "{\"display\":{\"icon\":{\"id\":\"minecraft:" + icon.getKey().getKey() + "\"},"
                    + "\"title\":{\"text\":\"" + escapeJson(TextParser.colorize(title)) + "\"},"
                    + "\"description\":{\"text\":\"\"},"
                    + "\"frame\":\"" + frameStr + "\","
                    + "\"announce_to_chat\":false,"
                    + "\"show_toast\":true,"
                    + "\"hidden\":true},"
                    + "\"criteria\":{\"trigger\":{\"trigger\":\"minecraft:impossible\"}},"
                    + "\"requirements\":[[\"trigger\"]]}";

            unsafe.loadAdvancement(key, advJson);

            Advancement adv = Bukkit.getAdvancement(key);
            if (adv == null) return;

            org.bukkit.advancement.AdvancementProgress progress = player.getAdvancementProgress(adv);
            for (String criterion : progress.getRemainingCriteria()) {
                progress.awardCriteria(criterion);
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    for (String criterion : player.getAdvancementProgress(adv).getAwardedCriteria()) {
                        player.getAdvancementProgress(adv).revokeCriteria(criterion);
                    }
                    unsafe.removeAdvancement(key);
                } catch (Exception ignored) {}
            }, 40L);

        } catch (Exception e) {
            plugin.getLogger().warning("[MentionToast] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void playSound(Player player) {
        String soundName = config.getString("sound.sound", "");
        if (soundName == null || soundName.isEmpty()) return;

        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }

        float volume = (float) config.getDouble("sound.volume", 1.0);
        float pitch = (float) config.getDouble("sound.pitch", 1.0);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}