package org.widnees.widCore.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.TextParser;
import org.widnees.widCore.util.FoliaScheduler;

public class AnnouncerManager {
    private final Main plugin;
    private FileConfiguration config;
    private Object task;
    private final Random random = new Random();
    private final List<Announcement> globalAnnouncements = new ArrayList<Announcement>();
    private final Map<String, List<Announcement>> worldAnnouncements = new HashMap<String, List<Announcement>>();
    private final Map<String, Replacement> replacements = new HashMap<String, Replacement>();
    private int globalIndex = 0;
    private final Map<String, Integer> worldIndexes = new HashMap<String, Integer>();
    private int interval;
    private boolean randomOrder;
    private Sound sound;
    private String customSound;

    public AnnouncerManager(Main plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        ConfigurationSection repSection;
        this.stop();
        this.config = this.plugin.getConfigManager().getModuleConfig("announcer");
        this.globalAnnouncements.clear();
        this.worldAnnouncements.clear();
        this.worldIndexes.clear();
        this.replacements.clear();
        this.globalIndex = 0;
        this.interval = this.config.getInt("settings.interval", 60);
        this.randomOrder = this.config.getBoolean("settings.random-order", false);
        String soundName = this.config.getString("settings.sound", "");
        this.sound = null;
        this.customSound = null;
        if (!soundName.isEmpty()) {
            try {
                this.sound = Sound.valueOf((String)soundName.toUpperCase().replace(".", "_").replace(":", "_"));
            }
            catch (IllegalArgumentException e) {
                this.customSound = soundName;
            }
        }
        if ((repSection = this.config.getConfigurationSection("replacements")) != null) {
            for (String key : repSection.getKeys(false)) {
                String input = repSection.getString(String.valueOf(key) + ".input", key);
                String output = repSection.getString(String.valueOf(key) + ".output", "");
                String hover = repSection.getString(String.valueOf(key) + ".hover", null);
                if (output.isEmpty()) continue;
                this.replacements.put(key, new Replacement(input, output, hover));
            }
        }
        this.loadAnnouncements(this.config.getConfigurationSection("global"), this.globalAnnouncements);
        ConfigurationSection worldsSection = this.config.getConfigurationSection("worlds");
        if (worldsSection != null) {
            for (String worldName : worldsSection.getKeys(false)) {
                ArrayList<Announcement> list = new ArrayList<Announcement>();
                this.loadAnnouncements(worldsSection.getConfigurationSection(worldName), list);
                if (list.isEmpty()) continue;
                this.worldAnnouncements.put(worldName, list);
                this.worldIndexes.put(worldName, 0);
            }
        }
        if (!this.globalAnnouncements.isEmpty() || !this.worldAnnouncements.isEmpty()) {
            this.start();
        }
    }

    private void loadAnnouncements(ConfigurationSection section, List<Announcement> targetList) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            List text = section.getStringList(String.valueOf(key) + ".text");
            if (text.isEmpty()) continue;
            targetList.add(new Announcement(text));
        }
    }

    public void start() {
        this.task = FoliaScheduler.runTaskTimer((Plugin)this.plugin, this::broadcast, Math.max(1L, 20L * (long)this.interval), 20L * (long)this.interval);
    }

    public void stop() {
        if (this.task != null) {
            FoliaScheduler.cancelTask(this.task);
            this.task = null;
        }
    }

    private void broadcast() {
        Announcement currentGlobal = this.getNextAnnouncement(this.globalAnnouncements, -1);
        HashMap<String, Announcement> currentWorldAnnouncements = new HashMap<String, Announcement>();
        for (String worldName : this.worldAnnouncements.keySet()) {
            currentWorldAnnouncements.put(worldName, this.getNextAnnouncement(this.worldAnnouncements.get(worldName), worldName));
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            String worldName = player.getWorld().getName();
            Announcement messageToSend = this.worldAnnouncements.containsKey(worldName) ? (Announcement)currentWorldAnnouncements.get(worldName) : currentGlobal;
            if (messageToSend == null) continue;
            for (String line : messageToSend.text) {
                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    line = PlaceholderAPI.setPlaceholders((Player)player, (String)line);
                }
                Component component = TextParser.parse(line);
                for (Map.Entry<String, Replacement> entry : this.replacements.entrySet()) {
                    String placeholder = "%" + entry.getKey() + "%";
                    Replacement repData = entry.getValue();
                    Component replacementComponent = TextParser.parse(repData.input).clickEvent(ClickEvent.openUrl((String)repData.output));
                    if (repData.hover != null) {
                        replacementComponent = replacementComponent.hoverEvent((HoverEventSource)HoverEvent.showText((Component)TextParser.parse(repData.hover)));
                    }
                    component = component.replaceText((TextReplacementConfig)TextReplacementConfig.builder().matchLiteral(placeholder).replacement((ComponentLike)replacementComponent).build());
                }
                player.sendMessage(component);
            }
            if (this.sound != null) {
                player.playSound(player.getLocation(), this.sound, 1.0f, 1.0f);
                continue;
            }
            if (this.customSound == null) continue;
            player.playSound(player.getLocation(), this.customSound, 1.0f, 1.0f);
        }
    }

    private Announcement getNextAnnouncement(List<Announcement> list, Object key) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        if (this.randomOrder) {
            return list.get(this.random.nextInt(list.size()));
        }
        int index = key instanceof String ? this.worldIndexes.getOrDefault(key, 0) : this.globalIndex;
        if (index >= list.size()) {
            index = 0;
        }
        Announcement announcement = list.get(index);
        ++index;
        if (key instanceof String) {
            this.worldIndexes.put((String)key, index);
        } else {
            this.globalIndex = index;
        }
        return announcement;
    }

    private static class Announcement {
        List<String> text;

        public Announcement(List<String> text) {
            this.text = text;
        }
    }

    private static class Replacement {
        String input;
        String output;
        String hover;

        public Replacement(String input, String output, String hover) {
            this.input = input;
            this.output = output;
            this.hover = hover;
        }
    }
}
