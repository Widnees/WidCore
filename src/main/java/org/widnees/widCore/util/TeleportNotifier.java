package org.widnees.widCore.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementDisplayType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.TextParser;

import java.time.Duration;
import java.util.Map;

public class TeleportNotifier {

    private TeleportNotifier() {}

    public static void send(Main plugin, Player player, FileConfiguration config,
                            String section, Map<String, String> placeholders) {
        ConfigurationSection sec = config.getConfigurationSection(section);
        if (sec == null) return;

        String chat = sec.getString("chat", "");
        if (chat != null && !chat.isEmpty()) {
            Main.sendMessage(plugin, player, applyPlaceholders(chat, placeholders));
        }

        ConfigurationSection titleSec = sec.getConfigurationSection("title");
        if (titleSec != null) {
            String titleText    = applyPlaceholders(titleSec.getString("title", ""), placeholders);
            String subtitleText = applyPlaceholders(titleSec.getString("subtitle", ""), placeholders);

            if ((titleText != null && !titleText.isEmpty()) ||
                (subtitleText != null && !subtitleText.isEmpty())) {

                int fadeIn  = titleSec.getInt("fade-in",  10);
                int stay    = titleSec.getInt("stay",     50);
                int fadeOut = titleSec.getInt("fade-out", 20);

                player.showTitle(net.kyori.adventure.title.Title.title(
                        TextParser.parse(titleText != null ? titleText : ""),
                        TextParser.parse(subtitleText != null ? subtitleText : ""),
                        net.kyori.adventure.title.Title.Times.times(
                                Duration.ofMillis(fadeIn  * 50L),
                                Duration.ofMillis(stay    * 50L),
                                Duration.ofMillis(fadeOut * 50L)
                        )
                ));
            }
        }

        String actionbar = sec.getString("actionbar", "");
        if (actionbar != null && !actionbar.isEmpty()) {
            player.sendActionBar(TextParser.parse(applyPlaceholders(actionbar, placeholders)));
        }

        ConfigurationSection toastSec = sec.getConfigurationSection("toast");
        if (toastSec != null) {
            String toastTitle = applyPlaceholders(toastSec.getString("title", ""), placeholders);
            if (toastTitle != null && !toastTitle.isEmpty()) {
                String itemName  = toastSec.getString("item",  "ENDER_PEARL");
                String frameName = toastSec.getString("frame", "GOAL");

                Material material;
                try {
                    material = Material.valueOf(itemName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    material = Material.ENDER_PEARL;
                }

                AdvancementDisplayType displayType;
                try {
                    displayType = AdvancementDisplayType.valueOf(frameName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    displayType = AdvancementDisplayType.GOAL;
                }

                sendFakeToast(plugin, player, material, displayType, toastTitle);
            }
        }
    }

    private static String applyPlaceholders(String text, Map<String, String> placeholders) {
        if (text == null || placeholders == null) return text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return text;
    }

    private static void sendFakeToast(Main plugin, Player player,
                                      Material icon, AdvancementDisplayType type, String title) {
        try {
            NamespacedKey key = new NamespacedKey(plugin,
                    "tp_" + Long.toHexString(System.currentTimeMillis()) + "_"
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
            plugin.getLogger().warning("[TeleportNotifier] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}