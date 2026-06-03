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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.widnees.widCore.Main;

public class LanguageManager {
    private final Main plugin;
    private FileConfiguration langConfig;
    private File langFile;

    public LanguageManager(Main plugin) {
        this.plugin = plugin;
        try {
            this.saveDefaultLanguages();
            this.loadLanguage();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Dil sistemi ba\u015flat\u0131l\u0131rken kritik bir hata olu\u015ftu.", e);
            this.langConfig = new YamlConfiguration();
        }
    }

    private void saveDefaultLanguages() {
        try {
            File langFolder = new File(this.plugin.getDataFolder(), "lang");
            if (!langFolder.exists()) {
                langFolder.mkdirs();
            }
            List<String> languages = Arrays.asList("tr.yml", "en.yml");
            for (String langName : languages) {
                File file = new File(langFolder, langName);
                if (file.exists() || this.plugin.getResource("lang/" + langName) == null) continue;
                this.plugin.saveResource("lang/" + langName, false);
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Varsay\u0131lan dil dosyalar\u0131 kaydedilirken bir sorun olu\u015ftu: " + e.getMessage());
        }
    }

    public void loadLanguage() {
        try {
            String langName = this.plugin.getConfig().getString("lang", "tr");
            if (langName != null) {
                langName = langName.trim();
            }
            if (langName == null || langName.isEmpty() || langName.contains("/") || langName.contains("\\")) {
                langName = "tr";
            }
            File langFolder = new File(this.plugin.getDataFolder(), "lang");
            if (!langFolder.exists()) {
                langFolder.mkdirs();
            }
            File candidateFile = new File(langFolder, langName + ".yml");
            boolean existsLocally = candidateFile.exists();
            boolean existsInJar = this.plugin.getResource("lang/" + langName + ".yml") != null;
            if (!existsLocally && !existsInJar) {
                this.plugin.getLogger().warning("[WidCore] Config dosyas\u0131nda belirtilen '" + langName + "' dili bulunamad\u0131!");
                this.plugin.getLogger().warning("[WidCore] Otomatik olarak 'tr' (T\u00fcrk\u00e7e) diline ge\u00e7iliyor...");
                langName = "tr";
                this.plugin.getConfig().set("lang", "tr");
                this.plugin.saveConfig();
            }
            this.langFile = new File(langFolder, langName + ".yml");
            if (!this.langFile.exists()) {
                try {
                    String resourcePath = "lang/" + langName + ".yml";
                    InputStream in = this.plugin.getResource(resourcePath);
                    if (in != null) {
                        this.plugin.saveResource(resourcePath, false);
                    } else {
                        this.langFile.createNewFile();
                        this.plugin.getLogger().warning("[WidCore] Yeni bo\u015f dil dosyas\u0131 olu\u015fturuldu: " + langName + ".yml");
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().log(Level.SEVERE, "[WidCore] Dil dosyas\u0131 olu\u015fturulurken hata olu\u015ftu!", e);
                }
            }
            this.updateLanguageFile(langName);
            try {
                this.langConfig = YamlConfiguration.loadConfiguration(this.langFile);
            } catch (Exception e) {
                this.plugin.getLogger().severe("[WidCore] Dil dosyas\u0131 (" + this.langFile.getName() + ") bozuk! Bo\u015f bir yap\u0131land\u0131rma kullan\u0131l\u0131yor.");
                this.langConfig = new YamlConfiguration();
            }
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "[WidCore] Dil y\u00fckleme i\u015flemi s\u0131ras\u0131nda beklenmedik bir hata olu\u015ftu.", e);
            if (this.langConfig == null) {
                this.langConfig = new YamlConfiguration();
            }
        }
    }

    private void updateLanguageFile(String langName) {
        String resourcePath = "lang/" + langName + ".yml";
        InputStream resourceStream = this.plugin.getResource(resourcePath);
        if (resourceStream == null) {
            return;
        }
        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(this.langFile);
        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration((Reader) new InputStreamReader(this.plugin.getResource(resourcePath)));
        int currentVer = currentConfig.getInt("version", 0);
        int newVer = defaultConfig.getInt("version", 1);
        if (currentVer >= newVer && this.langFile.exists() && this.langFile.length() > 0L) {
            return;
        }
        try {
            List<String> templateLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(this.plugin.getResource(resourcePath), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    templateLines.add(line);
                }
            }
            StringBuilder newContent = new StringBuilder();
            List<String> currentPath = new ArrayList<>();
            int lastIndent = 0;
            int newKeysCount = 0;
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
                        String keyName = matcher.group(2);
                        currentPath.add(keyName);
                        String fullPath = String.join(".", currentPath);
                        boolean forceUpdate = fullPath.equals("updater.available") || fullPath.equalsIgnoreCase("version");
                        if (forceUpdate || !currentConfig.contains(fullPath)) {
                            if (!forceUpdate && !currentConfig.contains(fullPath)) {
                                newKeysCount++;
                            }
                            newContent.append(line).append("\n");
                        } else {
                            Object userValue = currentConfig.get(fullPath);
                            if (currentConfig.isList(fullPath)) {
                                newContent.append(matcher.group(1)).append(keyName).append(":\n");
                                List<?> list = currentConfig.getList(fullPath);
                                if (list != null) {
                                    for (Object obj : list) {
                                        if (obj instanceof String) {
                                            newContent.append(matcher.group(1)).append("  - \"").append(obj.toString().replace("\"", "\\\"")).append("\"\n");
                                        } else {
                                            newContent.append(matcher.group(1)).append("  - ").append(obj).append("\n");
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
                            } else if (currentConfig.isConfigurationSection(fullPath)) {
                                newContent.append(matcher.group(1)).append(keyName).append(":\n");
                            } else {
                                String newValue = userValue.toString();
                                if (userValue instanceof String) {
                                    newValue = "\"" + newValue.replace("\"", "\\\"") + "\"";
                                }
                                newContent.append(matcher.group(1)).append(keyName).append(": ").append(newValue).append("\n");
                                currentPath.remove(currentPath.size() - 1);
                            }
                        }
                    }
                }
                ++i;
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(this.langFile, StandardCharsets.UTF_8))) {
                writer.write(newContent.toString());
            }
            String entry = "lang/" + langName + ".yml" + org.bukkit.ChatColor.GRAY
                    + " (v" + currentVer + " \u2192 v" + newVer + ")";
            this.plugin.getConfigManager().addUpdatedFile(entry);
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "[WidCore] Dil dosyas\u0131 g\u00fcncellenirken hata olu\u015ftu: " + langName + ".yml", e);
        }
    }

    public String getMessage(String key) {
        if (this.langConfig == null) {
            return key;
        }
        if (!this.langConfig.contains(key)) {
            if (key.equals("console.message-not-found")) {
                return "\u0026cMesaj Bulunamad\u0131: " + key;
            }
            return this.getMessage("console.message-not-found").replace("%key%", key);
        }
        return this.langConfig.getString(key);
    }

    public List<String> getMessageList(String key) {
        if (this.langConfig == null || !this.langConfig.contains(key)) {
            return null;
        }
        return this.langConfig.getStringList(key);
    }

    public void reload() {
        try {
            this.saveDefaultLanguages();
            this.loadLanguage();
        } catch (Exception e) {
            this.plugin.getLogger().severe("Dil dosyalar\u0131 yenilenirken hata olu\u015ftu.");
            e.printStackTrace();
        }
    }
        @SuppressWarnings("unused")
    private static final String _0xWb8d2e = "\u0077\u0069\u0064" + "\u006e\u0065" + "\u0065\u0073";

}
