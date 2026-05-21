package org.widnees.widCore.manager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.widnees.widCore.Main;

public class ConfigManager {
    private final Main plugin;
    private final Map<String, FileConfiguration> moduleConfigs = new ConcurrentHashMap<String, FileConfiguration>();
    private final File modulesFolder;
    private final List<String> restoredFiles = new ArrayList<>();
    private final List<String> updatedFiles = new ArrayList<>();

    public static boolean isConfigLoaded() {
        return true;
    }

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
        this.modulesFolder = new File(plugin.getDataFolder(), "modules");
        if (!this.modulesFolder.exists()) {
            this.modulesFolder.mkdirs();
        }
    }

    public void setupMainConfig() {
        restoredFiles.clear();
        updatedFiles.clear();
        File configFile = new File(this.plugin.getDataFolder(), "config.yml");
        String resourcePath = "config.yml";
        if (!configFile.exists()) {
            this.plugin.saveResource(resourcePath, false);
            restoredFiles.add(configFile.getName());
        } else {
            this.updateConfig(configFile, resourcePath);
        }
        this.plugin.reloadConfig();
        this.restoreMissingConfigs();
    }

    private void restoreMissingConfigs() {
        String lang = this.plugin.getConfig().getString("lang", "en").toLowerCase();
        String[] moduleFiles = new String[]{"announcer", "antimobspawn", "banneditem", "chat", "customcommand", "home", "inventory_rollback", "jail", "joinleave", "mobstacker", "motd", "removeitem", "spawn", "tpa", "void_spawn", "warp", "rtp", "mention", "chatguard/advertisement", "chatguard/bannedword", "chatguard/flood", "chatguard/spam", "punishment/ban", "punishment/kick", "punishment/mute"};
        for (String modulePath : moduleFiles) {
            String langResourcePath = "modules/" + lang + "/" + modulePath + ".yml";
            String fallbackResourcePath = "modules/" + modulePath + ".yml";
            String resourcePath = this.plugin.getResource(langResourcePath) != null ? langResourcePath : fallbackResourcePath;
            if (this.plugin.getResource(resourcePath) != null) {
                String filePath = this.plugin.getResource(langResourcePath) != null
                        ? lang + File.separator + modulePath.replace("/", File.separator) + ".yml"
                        : modulePath.replace("/", File.separator) + ".yml";
                File configFile = new File(this.modulesFolder, filePath);
                if (!configFile.exists()) {
                    if (configFile.getParentFile() != null && !configFile.getParentFile().exists()) {
                        configFile.getParentFile().mkdirs();
                    }
                    this.plugin.saveResource(resourcePath, false);
                    restoredFiles.add(configFile.getName());
                }
            }
        }
        String[] rootFiles = new String[]{"help.yml", "aliases.yml"};
        for (String fileName : rootFiles) {
            File file = new File(this.plugin.getDataFolder(), fileName);
            if (!file.exists() && this.plugin.getResource(fileName) != null) {
                this.plugin.saveResource(fileName, false);
                restoredFiles.add(fileName);
            }
        }
    }

    public List<String> getRestoredFiles() {
        return Collections.unmodifiableList(restoredFiles);
    }

    public List<String> getUpdatedFiles() {
        return Collections.unmodifiableList(updatedFiles);
    }

    public void printChangesBox(org.bukkit.command.ConsoleCommandSender console, int width) {
        boolean hasRestored = !restoredFiles.isEmpty();
        boolean hasUpdated = !updatedFiles.isEmpty();
        if (!hasRestored && !hasUpdated) return;

        String top   = "╔" + "═".repeat(width) + "╗";
        String sep   = "╠" + "═".repeat(width) + "╣";
        String bot   = "╚" + "═".repeat(width) + "╝";
        String empty = "║" + padLine(" ", width) + "║";

        console.sendMessage("");
        console.sendMessage(top);
        console.sendMessage("║" + padLine(" " + ChatColor.YELLOW + "Config Changes", width) + ChatColor.RESET + "║");
        console.sendMessage(sep);

        if (hasRestored) {
            console.sendMessage("║" + padLine(" " + ChatColor.AQUA + "Restored (missing files created):", width) + ChatColor.RESET + "║");
            console.sendMessage(empty);
            for (String fileName : restoredFiles) {
                String line = ChatColor.AQUA + "+" + ChatColor.RESET + " " + fileName;
                console.sendMessage("║" + padLine("  " + line, width) + ChatColor.RESET + "║");
            }
        }

        if (hasUpdated) {
            if (hasRestored) console.sendMessage(empty);
            console.sendMessage("║" + padLine(" " + ChatColor.YELLOW + "Updated (version migrated):", width) + ChatColor.RESET + "║");
            console.sendMessage(empty);
            for (String entry : updatedFiles) {
                String line = ChatColor.YELLOW + "~" + ChatColor.RESET + " " + entry;
                console.sendMessage("║" + padLine("  " + line, width) + ChatColor.RESET + "║");
            }
        }

        console.sendMessage(empty);
        console.sendMessage(bot);
    }

    public FileConfiguration getModuleConfig(String modulePath) {
        String lang = this.plugin.getConfig().getString("lang", "en").toLowerCase();
        String langFileName = lang + File.separator + modulePath.replace("/", File.separator) + ".yml";
        String fileName = modulePath.replace("/", File.separator) + ".yml";
        if (this.moduleConfigs.containsKey(langFileName)) {
            return this.moduleConfigs.get(langFileName);
        }
        File langConfigFile = new File(this.modulesFolder, langFileName);
        String langResourcePath = "modules/" + lang + "/" + modulePath.replace(File.separator, "/") + ".yml";
        File configFile = langConfigFile;
        String resourcePath = langResourcePath;
        if (this.plugin.getResource(langResourcePath) == null) {
            configFile = new File(this.modulesFolder, fileName);
            resourcePath = "modules/" + modulePath.replace(File.separator, "/") + ".yml";
        }
        if (!configFile.exists()) {
            if (configFile.getParentFile() != null && !configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            if (this.plugin.getResource(resourcePath) != null) {
                this.plugin.saveResource(resourcePath, false);
                restoredFiles.add(configFile.getName());
            }
        } else {
            this.updateConfig(configFile, resourcePath);
        }
        YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(configFile);
        this.moduleConfigs.put(langFileName, userConfig);
        return userConfig;
    }

    public void clearCache() {
        this.moduleConfigs.clear();
    }

    @SuppressWarnings("unchecked")
    public void updateConfig(File file, String resourceName) {
        InputStream resourceStream = this.plugin.getResource(resourceName);
        if (resourceStream == null) {
            return;
        }
        FileConfiguration currentDiskConfig = YamlConfiguration.loadConfiguration(file);
        FileConfiguration internalConfig = YamlConfiguration.loadConfiguration((Reader) new InputStreamReader(this.plugin.getResource(resourceName)));
        int currentVer = currentDiskConfig.getInt("version", 0);
        int newVer = internalConfig.getInt("version", 1);
        if (currentVer >= newVer && file.exists()) {
            return;
        }

        updatedFiles.add(file.getName() + ChatColor.GRAY + " (v" + currentVer + " \u2192 v" + newVer + ")");
        try {
            List<String> templateLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(this.plugin.getResource(resourceName), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    templateLines.add(line);
                }
            }
            StringBuilder newContent = new StringBuilder();
            List<String> currentPath = new ArrayList<>();
            int lastIndent = 0;
            int i = 0;
            while (i < templateLines.size()) {
                String line = templateLines.get(i);
                if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                    newContent.append(line).append("\n");
                } else {
                    int indent = 0;
                    while (indent < line.length() && line.charAt(indent) == ' ') {
                        ++indent;
                    }
                    int depth = indent / 2;
                    if (depth <= lastIndent) {
                        while (currentPath.size() > depth) {
                            currentPath.remove(currentPath.size() - 1);
                        }
                    }
                    lastIndent = depth;
                    Matcher matcher = Pattern.compile("^(\\s*)([^#:\\s]+):(.*)$").matcher(line);
                    if (!matcher.find()) {
                        newContent.append(line).append("\n");
                    } else {
                        String prefixSpace = matcher.group(1);
                        String keyName = matcher.group(2);
                        String restOfLine = matcher.group(3);
                        String templateComment = "";
                        boolean inQuote = false;
                        char quoteChar = '\0';
                        int ci = 0;
                        while (ci < restOfLine.length()) {
                            char ch = restOfLine.charAt(ci);
                            if (!inQuote && (ch == '"' || ch == '\'')) {
                                inQuote = true;
                                quoteChar = ch;
                            } else if (inQuote && ch == quoteChar) {
                                inQuote = false;
                            } else if (!inQuote && ch == '#') {
                                templateComment = " " + restOfLine.substring(ci);
                                break;
                            }
                            ++ci;
                        }
                        currentPath.add(keyName);
                        String fullPath = String.join(".", currentPath);
                        if (!currentDiskConfig.contains(fullPath) || keyName.equalsIgnoreCase("version")) {
                            newContent.append(line).append("\n");
                        } else {
                            Object userValue = currentDiskConfig.get(fullPath);
                            if (currentDiskConfig.isList(fullPath)) {
                                newContent.append(prefixSpace).append(keyName).append(":").append(templateComment).append("\n");
                                List<?> list = currentDiskConfig.getList(fullPath);
                                if (list != null) {
                                    for (Object obj : list) {
                                        if (obj instanceof Map) {
                                            Map<?, ?> map = (Map<?, ?>) obj;
                                            boolean first = true;
                                            for (Map.Entry<?, ?> entry : map.entrySet()) {
                                                String mapKey = entry.getKey().toString();
                                                String mapVal = entry.getValue() != null ? entry.getValue().toString() : "";
                                                if (mapVal.contains(":") || mapVal.contains("#") || mapVal.startsWith("&") || mapVal.startsWith("@") || mapVal.startsWith("%") || mapVal.contains("<") || mapVal.contains(">") || mapVal.isEmpty()) {
                                                    mapVal = "\"" + mapVal.replace("\"", "\\\"") + "\"";
                                                }
                                                if (first) {
                                                    newContent.append(prefixSpace).append("  - ").append(mapKey).append(": ").append(mapVal).append("\n");
                                                    first = false;
                                                } else {
                                                    newContent.append(prefixSpace).append("    ").append(mapKey).append(": ").append(mapVal).append("\n");
                                                }
                                            }
                                        } else {
                                            String val = obj.toString();
                                            if (val.contains(":") || val.contains("#") || val.startsWith("&") || val.startsWith("@") || val.startsWith("%")) {
                                                val = "\"" + val.replace("\"", "\\\"") + "\"";
                                            }
                                            newContent.append(prefixSpace).append("  - ").append(val).append("\n");
                                        }
                                    }
                                }
                                while (i + 1 < templateLines.size()) {
                                    String nextLine = templateLines.get(i + 1);
                                    int nextIndent = 0;
                                    while (nextIndent < nextLine.length() && nextLine.charAt(nextIndent) == ' ') {
                                        ++nextIndent;
                                    }
                                    boolean isListItem = nextLine.trim().startsWith("-");
                                    if (nextIndent <= indent && (nextIndent != indent || !isListItem)) break;
                                    ++i;
                                }
                                currentPath.remove(currentPath.size() - 1);
                            } else if (currentDiskConfig.isConfigurationSection(fullPath)) {

                                newContent.append(prefixSpace).append(keyName).append(":").append(templateComment).append("\n");

                            } else {
                                String newValue = userValue.toString();
                                if (userValue instanceof String && (newValue.contains(":") || newValue.startsWith("&") || newValue.contains("#") || newValue.isEmpty() || newValue.startsWith("%"))) {
                                    newValue = "\"" + newValue.replace("\"", "\\\"") + "\"";
                                }
                                newContent.append(prefixSpace).append(keyName).append(": ").append(newValue).append(templateComment).append("\n");
                                currentPath.remove(currentPath.size() - 1);
                            }
                        }
                    }
                }
                ++i;
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
                writer.write(newContent.toString());
            }
        } catch (Exception e) {
            this.plugin.getLogger().severe("Config g\u00fcncellenirken hata olu\u015ftu (" + file.getName() + "): " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String padLine(String s, int width) {
        if (s == null) s = "";

        String stripped = s.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "");
        int visible = stripped.length();
        StringBuilder sb = new StringBuilder(s);
        while (visible < width) {
            sb.append(' ');
            visible++;
        }
        return sb.toString();
    }

    public void printUpdateLog() {
    }

    public static String getFormatVersion() {
        return new String(new char[]{'R', '2', 'v', 'L'});
    }
}