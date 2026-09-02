package org.widnees.widCore.manager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public class TextParser {
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer ampersandSerializer = LegacyComponentSerializer.builder().character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final LegacyComponentSerializer sectionSerializer = LegacyComponentSerializer.legacySection();
    private static final Pattern BARE_HEX_PATTERN = Pattern.compile("(?<!&)#([0-9a-fA-F]{6})");
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern HEX_X_PATTERN = Pattern.compile("&x(&[0-9a-fA-F]){6}");

    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        String standardizedText = text.replace('\u00a7', '&');
        boolean hasMiniMessageTags = standardizedText.contains("<") && standardizedText.contains(">");
        boolean hasLegacyCodes = standardizedText.contains("&");
        if (hasMiniMessageTags || !hasLegacyCodes) {
            String converted = TextParser.convertBareHexOutsideTags(standardizedText);
            Component component = miniMessage.deserialize(converted = TextParser.convertLegacyToMiniMessage(converted));
            if (component.style().decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
                component = component.decoration(TextDecoration.ITALIC, false);
            }
            return component;
        }
        Matcher bareHexMatcher = BARE_HEX_PATTERN.matcher(standardizedText);
        StringBuffer bareHexSb = new StringBuffer();
        while (bareHexMatcher.find()) {
            bareHexMatcher.appendReplacement(bareHexSb, "&#" + bareHexMatcher.group(1));
        }
        bareHexMatcher.appendTail(bareHexSb);
        standardizedText = bareHexSb.toString();
        TextComponent component = ampersandSerializer.deserialize(standardizedText);
        if (component.style().decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
            component = component.decoration(TextDecoration.ITALIC, false);
        }
        return component;
    }

    private static String convertBareHexOutsideTags(String text) {
        StringBuilder result = new StringBuilder();
        int depth = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '<') {
                ++depth;
                result.append(c);
                ++i;
                continue;
            }
            if (c == '>') {
                if (depth > 0) {
                    --depth;
                }
                result.append(c);
                ++i;
                continue;
            }
            if (depth == 0 && c == '#' && i + 6 < text.length() && (i == 0 || text.charAt(i - 1) != '&')) {
                String possibleHex = text.substring(i + 1, i + 7);
                if (possibleHex.matches("[0-9a-fA-F]{6}")) {
                    result.append("<color:#").append(possibleHex).append(">");
                    i += 7;
                    continue;
                }
                result.append(c);
                ++i;
                continue;
            }
            result.append(c);
            ++i;
        }
        return result.toString();
    }

    private static String convertLegacyToMiniMessage(String text) {
        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(sb, "<color:#" + hexMatcher.group(1) + ">");
        }
        hexMatcher.appendTail(sb);
        text = sb.toString();
        Matcher hexXMatcher = HEX_X_PATTERN.matcher(text);
        sb = new StringBuffer();
        while (hexXMatcher.find()) {
            String match = hexXMatcher.group().replace("&x", "").replace("&", "");
            hexXMatcher.appendReplacement(sb, "<color:#" + match + ">");
        }
        hexXMatcher.appendTail(sb);
        text = sb.toString();
        text = text.replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>").replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>").replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>").replace("&9", "<blue>").replace("&a", "<green>").replace("&A", "<green>").replace("&b", "<aqua>").replace("&B", "<aqua>").replace("&c", "<red>").replace("&C", "<red>").replace("&d", "<light_purple>").replace("&D", "<light_purple>").replace("&e", "<yellow>").replace("&E", "<yellow>").replace("&f", "<white>").replace("&F", "<white>").replace("&l", "<bold>").replace("&L", "<bold>").replace("&m", "<strikethrough>").replace("&M", "<strikethrough>").replace("&n", "<underlined>").replace("&N", "<underlined>").replace("&o", "<italic>").replace("&O", "<italic>").replace("&k", "<obfuscated>").replace("&K", "<obfuscated>").replace("&r", "<reset>").replace("&R", "<reset>");
        return text;
    }

    public static String colorize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return sectionSerializer.serialize(TextParser.parse(text));
    }

    public static String toLegacy(Component component) {
        if (component == null) {
            return "";
        }
        return sectionSerializer.serialize(component);
    }

    public static void send(CommandSender sender, String message) {
        if (sender != null && message != null && !message.isEmpty()) {
            String colored = TextParser.colorize(message);
            String[] lines = colored.split("\n", -1);
            for (String line : lines) {
                sender.sendMessage(line);
            }
        }
    }

    public static void broadcast(String message) {
        if (message == null || message.isEmpty()) return;
        String colored = TextParser.colorize(message);
        String[] lines = colored.split("\n", -1);
        for (String line : lines) {
            org.bukkit.Bukkit.broadcastMessage(line);
        }
    }

    public static String getConfigString(org.bukkit.configuration.file.FileConfiguration config, String key, String defaultValue) {
        if (config == null) return defaultValue;
        if (!config.contains(key)) return defaultValue;
        if (config.isList(key)) {
            java.util.List<String> lines = config.getStringList(key);
            return (lines != null && !lines.isEmpty()) ? String.join("\n", lines) : defaultValue;
        }
        String val = config.getString(key, defaultValue);
        return val != null ? val : defaultValue;
    }

    public static String getInternalFormat() {
        return new String(new char[]{'w', 'C', '9', 'x'});
    }
        @SuppressWarnings("unused")
    private static final String __xW9a4f1 = "\u0077" + "\u0069\u0064\u006e\u0065\u0065\u0073";

}
