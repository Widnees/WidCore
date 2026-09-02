package org.widnees.widCore.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.widnees.widCore.Main;

public class UpdateManager {
    private final Main plugin;
    private final String currentVersion;
    private final String projectId = "wQ4Jsx5U";
    private String latestVersion = null;
    private boolean updateAvailable = false;

    public UpdateManager(Main plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    public void checkForUpdates(CommandSender sender) {
        if (sender != null) {
            Main.sendMessage(this.plugin, sender, this.plugin.getLanguageManager().getMessage("updater.checking"));
        }
        CompletableFuture.runAsync(() -> {
            try {
                try {
                    URL url = new URL("https://api.modrinth.com/v2/project/" + this.projectId + "/version");
                    HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("User-Agent", "WidCore/" + this.currentVersion);
                    if (connection.getResponseCode() == 200) {
                        JsonArray versions;
                        InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                        JsonElement element = JsonParser.parseReader((Reader)reader);
                        if (element.isJsonArray() && (versions = element.getAsJsonArray()).size() > 0) {
                            JsonObject latest = versions.get(0).getAsJsonObject();
                            String versionNumber = latest.get("version_number").getAsString();
                            if (!this.currentVersion.equalsIgnoreCase(versionNumber)) {
                                this.latestVersion = versionNumber;
                                this.updateAvailable = true;
                                if (sender != null) {
                                    this.notifySender(sender);
                                } else {
                                    this.plugin.getLogger().info(this.plugin.getLanguageManager().getMessage("updater.found-console").replace("%version%", versionNumber));
                                }
                            } else if (sender != null) {
                                Main.sendMessage(this.plugin, sender, this.plugin.getLanguageManager().getMessage("updater.already-latest"));
                            }
                        }
                        reader.close();
                    }
                }
                catch (Exception e) {
                    this.plugin.getLogger().warning(this.plugin.getLanguageManager().getMessage("updater.check-error").replace("%error%", e.getMessage()));
                    if (sender != null && sender instanceof ConsoleCommandSender) {
                        this.printDiscordInvite((ConsoleCommandSender)sender);
                    } else if (sender == null) {
                        this.printDiscordInvite(Bukkit.getConsoleSender());
                    }
                }
            }
            finally {
                if (sender != null && sender instanceof ConsoleCommandSender) {
                    this.printDiscordInvite((ConsoleCommandSender)sender);
                } else if (sender == null) {
                    this.printDiscordInvite(Bukkit.getConsoleSender());
                }
            }
        });
    }

    private void printDiscordInvite(ConsoleCommandSender console) {
        console.sendMessage("");
        console.sendMessage(ChatColor.GOLD + "====================================================");
        console.sendMessage(ChatColor.YELLOW + "For support, update notifications, suggestions,");
        console.sendMessage(ChatColor.YELLOW + " and to see other servers using our plugins,");
        console.sendMessage(ChatColor.YELLOW + "        you can join our Discord:");
        console.sendMessage("");
        console.sendMessage(ChatColor.AQUA + "     https://discord.gg/dCDYQ3HPct");
        console.sendMessage(ChatColor.GOLD + "====================================================");
        console.sendMessage("");
    }

    public void notifySender(CommandSender sender) {
        if (!this.updateAvailable) {
            return;
        }
        List<String> messages = this.plugin.getLanguageManager().getMessageList("updater.available");
        for (String line : messages) {
            String formatted = line.replace("%current%", this.currentVersion).replace("%new%", this.latestVersion);
            if (line.isEmpty()) {
                sender.sendMessage("");
                continue;
            }
            Main.sendMessage(this.plugin, sender, formatted);
        }
    }

    public boolean isUpdateAvailable() {
        return this.updateAvailable;
    }
        @SuppressWarnings("unused")
    private static final String _0xW7e1a9 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}