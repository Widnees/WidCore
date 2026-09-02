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
    private boolean centerText;
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
        this.centerText = this.config.getBoolean("settings.center-text", false);
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
                if (this.centerText) {
                    line = this.centerLine(line);
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

    private enum DefaultFontInfo {
        A('A', 5), a('a', 5), B('B', 5), b('b', 5), C('C', 5), c('c', 5), D('D', 5), d('d', 5),
        E('E', 5), e('e', 5), F('F', 5), f('f', 4), G('G', 5), g('g', 5), H('H', 5), h('h', 5),
        I('I', 3), i('i', 1), J('J', 5), j('j', 5), K('K', 5), k('k', 4), L('L', 5), l('l', 1),
        M('M', 5), m('m', 5), N('N', 5), n('n', 5), O('O', 5), o('o', 5), P('P', 5), p('p', 5),
        Q('Q', 5), q('q', 5), R('R', 5), r('r', 5), S('S', 5), s('s', 5), T('T', 5), t('t', 4),
        U('U', 5), u('u', 5), V('V', 5), v('v', 5), W('W', 5), w('w', 5), X('X', 5), x('x', 5),
        Y('Y', 5), y('y', 5), Z('Z', 5), z('z', 5),
        NUM_1('1', 5), NUM_2('2', 5), NUM_3('3', 5), NUM_4('4', 5), NUM_5('5', 5),
        NUM_6('6', 5), NUM_7('7', 5), NUM_8('8', 5), NUM_9('9', 5), NUM_0('0', 5),
        EXCLAMATION_POINT('!', 1), AT_SYMBOL('@', 6), NUM_SIGN('#', 5), DOLLAR_SIGN('$', 5),
        PERCENT('%', 5), UP_ARROW('^', 5), AMPERSAND('&', 5), ASTERISK('*', 5),
        LEFT_PARENTHESIS('(', 4), RIGHT_PERENTHESIS(')', 4), MINUS('-', 5), UNDERSCORE('_', 5),
        PLUS_SIGN('+', 5), EQUALS_SIGN('=', 5), LEFT_CURL_BRACE('{', 4), RIGHT_CURL_BRACE('}', 4),
        LEFT_BRACKET('[', 3), RIGHT_BRACKET(']', 3), COLON(':', 1), SEMI_COLON(';', 1),
        DOUBLE_QUOTE('"', 3), SINGLE_QUOTE('\'', 1), LEFT_ARROW('<', 4), RIGHT_ARROW('>', 4),
        QUESTION_MARK('?', 5), SLASH('/', 5), BACK_SLASH('\\', 5), LINE('|', 1), TILDE('~', 5),
        TICK('`', 2), PERIOD('.', 1), COMMA(',', 1), SPACE(' ', 3), DEFAULT('a', 4);

        private final char character;
        private final int length;

        DefaultFontInfo(char character, int length) {
            this.character = character;
            this.length = length;
        }

        public int getLength() {
            return this.length;
        }

        public int getBoldLength() {
            if (this == SPACE) return this.getLength();
            return this.length + 1;
        }

        public static DefaultFontInfo getDefaultFontInfo(char c) {
            for (DefaultFontInfo dFI : DefaultFontInfo.values()) {
                if (dFI.character == c) return dFI;
            }
            return DEFAULT;
        }
    }

    private String centerLine(String line) {
        boolean isBold = false;
        int messagePxSize = 0;
        char[] rawChars = line.toCharArray();

        for (int i = 0; i < rawChars.length; i++) {
            char c = rawChars[i];

            if ((c == '&' || c == '§') && i + 1 < rawChars.length) {
                char next = rawChars[i + 1];

                if (Character.toLowerCase(next) == 'x' && i + 13 < rawChars.length) {
                    boolean isHex = true;
                    for (int j = 2; j <= 12; j += 2) {
                        if (i + j < rawChars.length && (rawChars[i + j] == '&' || rawChars[i + j] == '§')
                                && i + j + 1 < rawChars.length && "0123456789abcdefABCDEF".indexOf(rawChars[i + j + 1]) != -1) {
                            continue;
                        }
                        isHex = false;
                        break;
                    }
                    if (isHex) {
                        i += 13; 
                        isBold = false;
                        continue;
                    }
                }

                if ("0123456789abcdefklmnorABCDEFKLMNOR".indexOf(next) != -1) {
                    if (Character.toLowerCase(next) == 'l') {
                        isBold = true;
                    } else if (Character.toLowerCase(next) == 'r' || "0123456789abcdefABCDEF".indexOf(Character.toLowerCase(next)) != -1) {
                        isBold = false;
                    }
                    i++; // Sonraki karakteri atla
                    continue;
                }
            }

            if (c == '<') {
                int closeIdx = line.indexOf('>', i);
                if (closeIdx != -1) {
                    String tagContent = line.substring(i + 1, closeIdx).toLowerCase();
                    if (tagContent.equals("bold") || tagContent.equals("b")) {
                        isBold = true;
                    } else if (tagContent.equals("/bold") || tagContent.equals("/b") || tagContent.equals("reset") || tagContent.equals("/reset")) {
                        isBold = false;
                    }
                    i = closeIdx;
                    continue;
                }
            }

            if (c == '&' && i + 1 < rawChars.length && rawChars[i + 1] == '#' && i + 7 < rawChars.length) {
                boolean isHex = true;
                for (int j = 2; j <= 7; j++) {
                    if ("0123456789abcdefABCDEF".indexOf(rawChars[i + j]) == -1) {
                        isHex = false;
                        break;
                    }
                }
                if (isHex) {
                    i += 7;
                    isBold = false;
                    continue;
                }
            }

            DefaultFontInfo dFI = DefaultFontInfo.getDefaultFontInfo(c);
            messagePxSize += isBold ? dFI.getBoldLength() : dFI.getLength();
            messagePxSize++; // Karakterler arası 1px boşluk
        }

        int halvedMessageSize = messagePxSize / 2;
        int toCompensate = 154 - halvedMessageSize; // 154 = CENTER_PX

        int spaceLength = DefaultFontInfo.SPACE.getLength() + 1; // 3 + 1 = 4
        int spaces = toCompensate / spaceLength;

        if (spaces <= 0) {
            return line;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spaces; i++) {
            sb.append(' ');
        }
        sb.append(line);

        return sb.toString();
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
        @SuppressWarnings("unused")
    private static final String __Wc6d8x2 = "\u0077\u0069" + "\u0064\u006e" + "\u0065\u0065\u0073";

}
