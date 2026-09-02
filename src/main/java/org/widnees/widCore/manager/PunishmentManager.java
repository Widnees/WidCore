package org.widnees.widCore.manager;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.database.BinaryDataManager;
import org.widnees.widCore.manager.TextParser;

public class PunishmentManager {
    private final Main plugin;
    private Map<UUID, BinaryDataManager.PunishmentEntry> mutes = new ConcurrentHashMap<UUID, BinaryDataManager.PunishmentEntry>();
    private Map<UUID, BinaryDataManager.PunishmentEntry> bans = new ConcurrentHashMap<UUID, BinaryDataManager.PunishmentEntry>();
    private Map<UUID, BinaryDataManager.PunishmentEntry> freezes = new ConcurrentHashMap<UUID, BinaryDataManager.PunishmentEntry>();
    private Map<UUID, BinaryDataManager.JailEntry> jails = new ConcurrentHashMap<UUID, BinaryDataManager.JailEntry>();
    /** Past ban entries per player — newest first within each list */
    private Map<UUID, List<BinaryDataManager.PunishmentEntry>> banHistory = new ConcurrentHashMap<>();
    /** Past mute entries per player — newest first within each list */
    private Map<UUID, List<BinaryDataManager.PunishmentEntry>> muteHistory = new ConcurrentHashMap<>();
    /** IP-based mutes: key = IP address string */
    private Map<String, BinaryDataManager.PunishmentEntry> mutedIPs = new ConcurrentHashMap<>();
    /** IP-based bans: key = IP address string */
    private Map<String, BinaryDataManager.PunishmentEntry> bannedIPs = new ConcurrentHashMap<>();
    /** Last known IP per UUID — persisted so offline players can be ip-banned/muted */
    private Map<UUID, String> lastKnownIps = new ConcurrentHashMap<>();
    private FileConfiguration kickConfig;
    private FileConfiguration banConfig;
    private FileConfiguration muteConfig;
    private static final UUID CONSOLE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public PunishmentManager(Main plugin) {
        this.plugin = plugin;
        this.loadConfigs();
        this.loadPunishments();
    }

    public void loadConfigs() {
        this.kickConfig = this.plugin.getConfigManager().getModuleConfig("punishment/kick");
        this.banConfig = this.plugin.getConfigManager().getModuleConfig("punishment/ban");
        this.muteConfig = this.plugin.getConfigManager().getModuleConfig("punishment/mute");
    }

    /**
     * Reads the punishment config file directly from disk (bypasses cache).
     * Used for webhook settings so that changes take effect without plugin restart.
     */
    private FileConfiguration readConfigFromDisk(String modulePath) {
        String lang = this.plugin.getConfig().getString("lang", "en").toLowerCase();
        File modulesFolder = new File(this.plugin.getDataFolder(), "modules");
        File langFile = new File(modulesFolder, lang + File.separator + modulePath.replace("/", File.separator) + ".yml");
        if (langFile.exists()) {
            return YamlConfiguration.loadConfiguration(langFile);
        }
        File fallbackFile = new File(modulesFolder, modulePath.replace("/", File.separator) + ".yml");
        if (fallbackFile.exists()) {
            return YamlConfiguration.loadConfiguration(fallbackFile);
        }
        return this.plugin.getConfigManager().getModuleConfig(modulePath);
    }

    public boolean isExempt(String type, String playerName) {
        Collection<String> exemptList = null;
        switch (type.toLowerCase()) {
            case "kick": {
                exemptList = this.kickConfig.getStringList("exempt-players");
                break;
            }
            case "ban": {
                exemptList = this.banConfig.getStringList("exempt-players");
                break;
            }
            case "mute": {
                exemptList = this.muteConfig.getStringList("exempt-players");
            }
        }
        if (exemptList == null) {
            return false;
        }
        return exemptList.stream().anyMatch(name -> name.equalsIgnoreCase(playerName));
    }

    public void kickPlayer(Player target, CommandSender sender, String reason) {
        if (this.kickConfig.getBoolean("broadcast", true)) {
            String msg = this.kickConfig.getString("messages.broadcast",
                    "&c%target% &7was kicked by &c%player%&7. &8(&f%reason%&8)")
                    .replace("%target%", target.getName())
                    .replace("%player%", sender.getName())
                    .replace("%reason%", reason);
            TextParser.broadcast(msg);
        }
        String kickScreen = this.getFormattedScreenFromConfig(this.kickConfig, "messages.kick-screen", reason, null);
        target.kickPlayer(kickScreen);
        FileConfiguration fresh = readConfigFromDisk("punishment/kick");
        sendWebhookFromSection(fresh, "kick-webhook", target.getName(), sender.getName(), reason, null);
    }

    public void banPlayer(Player target, CommandSender sender, String reason) {
        banPlayer((org.bukkit.OfflinePlayer) target, sender, reason);
    }

    public void banPlayer(org.bukkit.OfflinePlayer target, CommandSender sender, String reason) {
        UUID punisherUUID = sender instanceof Player ? ((Player)sender).getUniqueId() : CONSOLE_UUID;
        long now = System.currentTimeMillis();
        archiveBan(target.getUniqueId());
        BinaryDataManager.PunishmentEntry entry = new BinaryDataManager.PunishmentEntry(-1L, reason, punisherUUID, now);
        this.bans.put(target.getUniqueId(), entry);
        this.savePunishments();
        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        String durationStr = this.plugin.getLanguageManager().getMessage("punishment.permanent");
        String punisherName = sender.getName();
        String banDate = formatTimestamp(now);
        String expiryStr = this.plugin.getLanguageManager().getMessage("punishment.permanent");
        if (this.banConfig.getBoolean("broadcast", true)) {
            String msg = getConfigMessage(this.banConfig, "messages.broadcast",
                    "&c%target% &7was banned by &c%player%&7. &8(&f%reason%&8)",
                    targetName, punisherName, reason, durationStr);
            TextParser.broadcast(msg);
        }
        // Kick if online
        Player online = Bukkit.getPlayer(target.getUniqueId());
        if (online != null) {
            online.kickPlayer(this.getFormattedScreenFromConfig(this.banConfig, "messages.ban-screen", reason, durationStr, punisherName, banDate, expiryStr));
        }
        FileConfiguration fresh = readConfigFromDisk("punishment/ban");
        sendWebhookFromSection(fresh, "ban-webhook", targetName, punisherName, reason, durationStr);
    }

    public void tempBanPlayer(Player target, CommandSender sender, long duration, String reason) {
        tempBanPlayer((org.bukkit.OfflinePlayer) target, sender, duration, reason);
    }

    public void tempBanPlayer(org.bukkit.OfflinePlayer target, CommandSender sender, long duration, String reason) {
        long now = System.currentTimeMillis();
        long expiry = now + duration;
        UUID punisherUUID = sender instanceof Player ? ((Player)sender).getUniqueId() : CONSOLE_UUID;
        archiveBan(target.getUniqueId());
        BinaryDataManager.PunishmentEntry entry = new BinaryDataManager.PunishmentEntry(expiry, reason, punisherUUID, now);
        this.bans.put(target.getUniqueId(), entry);
        this.savePunishments();
        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        String durationStr = this.formatDuration(duration);
        String punisherName = sender.getName();
        String banDate = formatTimestamp(now);
        String expiryStr = formatTimestamp(expiry);
        if (this.banConfig.getBoolean("broadcast", true)) {
            String msg = getConfigMessage(this.banConfig, "messages.broadcast",
                    "&c%target% &7was banned by &c%player%&7. &8(&f%reason%&8)",
                    targetName, punisherName, reason, durationStr);
            TextParser.broadcast(msg);
        }
        // Kick if online
        Player online = Bukkit.getPlayer(target.getUniqueId());
        if (online != null) {
            online.kickPlayer(this.getFormattedScreenFromConfig(this.banConfig, "messages.ban-screen", reason, durationStr, punisherName, banDate, expiryStr));
        }
        FileConfiguration fresh = readConfigFromDisk("punishment/ban");
        sendWebhookFromSection(fresh, "ban-webhook", targetName, punisherName, reason, durationStr);
    }

    /**
     * Reads a config value that can be either a String or a List<String>.
     * If list format, joins with "\n". Replaces common placeholders.
     */
    private String getConfigMessage(FileConfiguration config, String key, String defaultMsg,
            String target, String player, String reason, String duration) {
        List<String> lines = config.getStringList(key);
        String text;
        if (lines != null && !lines.isEmpty()) {
            text = String.join("\n", lines);
        } else {
            String single = config.getString(key, defaultMsg);
            text = single != null ? single : defaultMsg;
        }
        if (target != null) text = text.replace("%target%", target);
        if (player != null) text = text.replace("%player%", player);
        if (reason != null) text = text.replace("%reason%", reason);
        if (duration != null) text = text.replace("%duration%", duration);
        return text;
    }

    private String getFormattedScreenFromConfig(FileConfiguration config, String key, String reason, String duration) {
        return getFormattedScreenFromConfig(config, key, reason, duration, null, null, null);
    }

    private String getFormattedScreenFromConfig(FileConfiguration config, String key, String reason, String duration,
            String punisher, String banDate, String expiry) {
        List<String> lines = config.getStringList(key);
        String text;
        if (lines != null && !lines.isEmpty()) {
            text = String.join("\n", lines);
        } else {
            String single = config.getString(key, "");
            text = single != null ? single : "";
        }
        text = text.replace("%reason%", reason != null ? reason : "");
        if (duration != null) {
            text = text.replace("%duration%", duration);
        }
        if (punisher != null) {
            text = text.replace("%punisher%", punisher).replace("%player%", punisher);
        }
        if (banDate != null) {
            text = text.replace("%ban-date%", banDate);
        }
        if (expiry != null) {
            text = text.replace("%expiry%", expiry);
        }
        return TextParser.colorize(text);
    }

    public void mutePlayer(Player target, CommandSender sender, String reason) {
        mutePlayer((org.bukkit.OfflinePlayer) target, sender, reason);
    }

    public void mutePlayer(org.bukkit.OfflinePlayer target, CommandSender sender, String reason) {
        UUID punisherUUID = sender instanceof Player ? ((Player)sender).getUniqueId() : CONSOLE_UUID;
        archiveMute(target.getUniqueId());
        BinaryDataManager.PunishmentEntry entry = new BinaryDataManager.PunishmentEntry(-1L, reason, punisherUUID, System.currentTimeMillis());
        this.mutes.put(target.getUniqueId(), entry);
        this.savePunishments();
        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        String durationStr = this.plugin.getLanguageManager().getMessage("punishment.permanent");
        if (this.muteConfig.getBoolean("broadcast", true)) {
            String msg = getConfigMessage(this.muteConfig, "messages.broadcast",
                    "&c%target% &7was muted by &c%player%&7. &8(&f%reason%&8)",
                    targetName, sender.getName(), reason, durationStr);
            TextParser.broadcast(msg);
        }
        // Notify if online (no plugin prefix — player-message is sent raw)
        Player online = Bukkit.getPlayer(target.getUniqueId());
        if (online != null) {
            String muteMsg = getConfigMessage(this.muteConfig, "messages.player-message",
                    "&cYou were muted. Reason: &f%reason% &7Duration: &f%duration%",
                    targetName, sender.getName(), reason, durationStr);
            TextParser.send(online, muteMsg);
        }
        FileConfiguration fresh = readConfigFromDisk("punishment/mute");
        sendWebhookFromSection(fresh, "mute-webhook", targetName, sender.getName(), reason, durationStr);
    }

    public void tempMutePlayer(Player target, CommandSender sender, long duration, String reason) {
        tempMutePlayer((org.bukkit.OfflinePlayer) target, sender, duration, reason);
    }

    public void tempMutePlayer(org.bukkit.OfflinePlayer target, CommandSender sender, long duration, String reason) {
        long expiry = System.currentTimeMillis() + duration;
        UUID punisherUUID = sender instanceof Player ? ((Player)sender).getUniqueId() : CONSOLE_UUID;
        archiveMute(target.getUniqueId());
        BinaryDataManager.PunishmentEntry entry = new BinaryDataManager.PunishmentEntry(expiry, reason, punisherUUID, System.currentTimeMillis());
        this.mutes.put(target.getUniqueId(), entry);
        this.savePunishments();
        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        String durationStr = this.formatDuration(duration);
        if (this.muteConfig.getBoolean("broadcast", true)) {
            String msg = getConfigMessage(this.muteConfig, "messages.broadcast",
                    "&c%target% &7was muted by &c%player%&7. &8(&f%reason%&8)",
                    targetName, sender.getName(), reason, durationStr);
            TextParser.broadcast(msg);
        }
        // Notify if online (no plugin prefix — player-message is sent raw)
        Player online = Bukkit.getPlayer(target.getUniqueId());
        if (online != null) {
            String muteMsg = getConfigMessage(this.muteConfig, "messages.player-message",
                    "&cYou were muted. Reason: &f%reason% &7Duration: &f%duration%",
                    targetName, sender.getName(), reason, durationStr);
            TextParser.send(online, muteMsg);
        }

        FileConfiguration fresh = readConfigFromDisk("punishment/mute");
        sendWebhookFromSection(fresh, "mute-webhook", targetName, sender.getName(), reason, durationStr);
    }

    public boolean unbanPlayer(UUID uuid) {
        return unbanPlayer(uuid, "Console");
    }

    public boolean unbanPlayer(UUID uuid, String punisherName) {
        BinaryDataManager.PunishmentEntry existing = this.bans.get(uuid);
        if (existing != null && isBanned(uuid)) {
            String targetName = Bukkit.getOfflinePlayer(uuid).getName();
            if (targetName == null) targetName = uuid.toString();
            // Mark as expired (keep record for history) instead of deleting
            BinaryDataManager.PunishmentEntry expired = new BinaryDataManager.PunishmentEntry(
                    System.currentTimeMillis() - 1L, existing.reason, existing.punisherUUID, existing.timestamp);
            expired.removedBy = punisherName;
            this.bans.put(uuid, expired);
            this.savePunishments();
            FileConfiguration fresh = readConfigFromDisk("punishment/ban");
            sendWebhookFromSection(fresh, "unban-webhook", targetName, punisherName, null, null);
            return true;
        }
        return false;
    }

    public boolean unmutePlayer(UUID uuid) {
        return unmutePlayer(uuid, "Console");
    }

    public boolean unmutePlayer(UUID uuid, String punisherName) {
        BinaryDataManager.PunishmentEntry existing = this.mutes.get(uuid);
        if (existing != null && isMuted(uuid)) {
            String targetName = Bukkit.getOfflinePlayer(uuid).getName();
            if (targetName == null) targetName = uuid.toString();
            // Mark as expired (keep record for history) instead of deleting
            BinaryDataManager.PunishmentEntry expired = new BinaryDataManager.PunishmentEntry(
                    System.currentTimeMillis() - 1L, existing.reason, existing.punisherUUID, existing.timestamp);
            expired.removedBy = punisherName;
            this.mutes.put(uuid, expired);
            this.savePunishments();
            FileConfiguration fresh = readConfigFromDisk("punishment/mute");
            sendWebhookFromSection(fresh, "unmute-webhook", targetName, punisherName, null, null);
            return true;
        }
        return false;
    }

    public void freezePlayer(Player target, CommandSender sender, long duration, String reason) {
        long expiry = duration == -1L ? -1L : System.currentTimeMillis() + duration;
        UUID punisherUUID = sender instanceof Player ? ((Player)sender).getUniqueId() : CONSOLE_UUID;
        BinaryDataManager.PunishmentEntry entry = new BinaryDataManager.PunishmentEntry(expiry, reason, punisherUUID, System.currentTimeMillis());
        this.freezes.put(target.getUniqueId(), entry);
        this.savePunishments();
    }

    public boolean unfreezePlayer(UUID uuid) {
        if (this.freezes.containsKey(uuid)) {
            this.freezes.remove(uuid);
            this.savePunishments();
            return true;
        }
        return false;
    }

    public void jailPlayer(Player target, CommandSender sender, String jailName, long duration, String reason, Location returnLoc) {
        long expiry = duration == -1L ? -1L : System.currentTimeMillis() + duration;
        UUID punisherUUID = sender instanceof Player ? ((Player)sender).getUniqueId() : CONSOLE_UUID;
        BinaryDataManager.JailEntry entry = new BinaryDataManager.JailEntry(expiry, reason, punisherUUID, System.currentTimeMillis(), jailName, returnLoc);
        this.jails.put(target.getUniqueId(), entry);
        this.savePunishments();
    }

    public Location unjailPlayer(UUID uuid) {
        BinaryDataManager.JailEntry entry = this.jails.remove(uuid);
        if (entry != null) {
            this.savePunishments();
            return entry.returnLocation.toLocation();
        }
        return null;
    }

    public boolean isJailed(UUID uuid) {
        if (!this.jails.containsKey(uuid)) {
            return false;
        }
        long expiry = this.jails.get((Object)uuid).expiry;
        if (expiry == -1L) {
            return true;
        }
        if (System.currentTimeMillis() > expiry) {
            this.unjailPlayer(uuid);
            return false;
        }
        return true;
    }

    public boolean isMuted(UUID uuid) {
        if (!this.mutes.containsKey(uuid)) {
            return false;
        }
        long expiry = this.mutes.get((Object)uuid).expiry;
        if (expiry == -1L) {
            return true;
        }
        // Keep expired entries in map for history — just return false
        return System.currentTimeMillis() < expiry;
    }

    public boolean isBanned(UUID uuid) {
        if (!this.bans.containsKey(uuid)) {
            return false;
        }
        long expiry = this.bans.get((Object)uuid).expiry;
        if (expiry == -1L) {
            return true;
        }
        // Keep expired entries in map for history — just return false
        return System.currentTimeMillis() < expiry;
    }

    public boolean isFrozen(UUID uuid) {
        if (!this.freezes.containsKey(uuid)) {
            return false;
        }
        long expiry = this.freezes.get((Object)uuid).expiry;
        if (expiry == -1L) {
            return true;
        }
        if (System.currentTimeMillis() > expiry) {
            this.freezes.remove(uuid);
            this.savePunishments();
            return false;
        }
        return true;
    }

    public BinaryDataManager.JailEntry getJailEntry(UUID uuid) {
        return this.jails.get(uuid);
    }

    public List<String> getJailedPlayerNames() {
        return this.jails.keySet().stream().map(uuid -> Bukkit.getOfflinePlayer((UUID)uuid).getName()).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<String> getBannedPlayerNames() {
        return this.bans.keySet().stream()
                .filter(uuid -> isBanned(uuid))
                .map(uuid -> Bukkit.getOfflinePlayer((UUID)uuid).getName())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<String> getMutedPlayerNames() {
        return this.mutes.keySet().stream()
                .filter(uuid -> isMuted(uuid))
                .map(uuid -> Bukkit.getOfflinePlayer((UUID)uuid).getName())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Searches the bans map for a UUID whose offline player name matches the given name exactly (case-sensitive)
     * AND whose ban is still active.
     * This prevents case-insensitive Bukkit.getOfflinePlayer() from returning the wrong player
     * when two players have names differing only in case (e.g. "Syro" vs "syro").
     */
    public UUID getBannedUUIDByExactName(String name) {
        for (UUID uuid : this.bans.keySet()) {
            if (!isBanned(uuid)) continue;
            String playerName = Bukkit.getOfflinePlayer(uuid).getName();
            if (name.equals(playerName)) {
                return uuid;
            }
        }
        return null;
    }

    /**
     * Searches the mutes map for a UUID whose offline player name matches the given name exactly (case-sensitive)
     * AND whose mute is still active.
     */
    public UUID getMutedUUIDByExactName(String name) {
        for (UUID uuid : this.mutes.keySet()) {
            if (!isMuted(uuid)) continue;
            String playerName = Bukkit.getOfflinePlayer(uuid).getName();
            if (name.equals(playerName)) {
                return uuid;
            }
        }
        return null;
    }

    public BinaryDataManager.PunishmentEntry getBanEntry(UUID uuid) {
        return this.bans.get(uuid);
    }

    public BinaryDataManager.PunishmentEntry getMuteEntry(UUID uuid) {
        return this.mutes.get(uuid);
    }

    public Map<UUID, BinaryDataManager.PunishmentEntry> getAllBanEntries() {
        return new ConcurrentHashMap<UUID, BinaryDataManager.PunishmentEntry>(this.bans);
    }

    public Map<UUID, BinaryDataManager.PunishmentEntry> getAllMuteEntries() {
        return new ConcurrentHashMap<UUID, BinaryDataManager.PunishmentEntry>(this.mutes);
    }

    public long getMuteExpiry(UUID uuid) {
        BinaryDataManager.PunishmentEntry entry = this.mutes.get(uuid);
        return entry != null ? entry.expiry : 0L;
    }

    public long getBanExpiry(UUID uuid) {
        BinaryDataManager.PunishmentEntry entry = this.bans.get(uuid);
        return entry != null ? entry.expiry : 0L;
    }

    public long getFreezeExpiry(UUID uuid) {
        BinaryDataManager.PunishmentEntry entry = this.freezes.get(uuid);
        return entry != null ? entry.expiry : 0L;
    }

    public long parseDuration(String durationStr) {
        if (durationStr == null || durationStr.length() < 2) {
            return 0L;
        }
        try {
            long value = Long.parseLong(durationStr.substring(0, durationStr.length() - 1));
            char unit = durationStr.charAt(durationStr.length() - 1);
            switch (unit) {
                case 's': {
                    return value * 1000L;
                }
                case 'm': {
                    return value * 60L * 1000L;
                }
                case 'h': {
                    return value * 60L * 60L * 1000L;
                }
                case 'd': {
                    return value * 24L * 60L * 60L * 1000L;
                }
            }
            return 0L;
        }
        catch (Exception e) {
            return 0L;
        }
    }

    public String formatDuration(long millis) {
        if (millis < 0L) {
            return this.plugin.getLanguageManager().getMessage("punishment.permanent");
        }
        String unitDay  = this.plugin.getLanguageManager().getMessage("time.days");
        String unitHour = this.plugin.getLanguageManager().getMessage("time.hours");
        String unitMin  = this.plugin.getLanguageManager().getMessage("time.minutes");
        String unitSec  = this.plugin.getLanguageManager().getMessage("time.seconds");
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis -= TimeUnit.DAYS.toMillis(days));
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis -= TimeUnit.HOURS.toMillis(hours));
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis -= TimeUnit.MINUTES.toMillis(minutes));
        StringBuilder sb = new StringBuilder();
        if (days > 0L) {
            sb.append(days).append(unitDay).append(" ");
        }
        if (hours > 0L) {
            sb.append(hours).append(unitHour).append(" ");
        }
        if (minutes > 0L) {
            sb.append(minutes).append(unitMin).append(" ");
        }
        if (seconds > 0L) {
            sb.append(seconds).append(unitSec);
        }
        return sb.toString().trim();
    }

    private String formatTimestamp(long millis) {
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter
                .ofPattern("dd.MM.yyyy HH:mm")
                .withZone(java.time.ZoneId.systemDefault());
        return fmt.format(java.time.Instant.ofEpochMilli(millis));
    }

    // ─── History helpers ────────────────────────────────────────────────────────

    /**
     * Moves the current ban entry (if any) into banHistory before overwriting.
     */
    private void archiveBan(UUID uuid) {
        BinaryDataManager.PunishmentEntry existing = this.bans.get(uuid);
        if (existing == null) return;
        List<BinaryDataManager.PunishmentEntry> list =
                this.banHistory.computeIfAbsent(uuid, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        list.add(0, existing);  // newest first
    }

    /**
     * Moves the current mute entry (if any) into muteHistory before overwriting.
     */
    private void archiveMute(UUID uuid) {
        BinaryDataManager.PunishmentEntry existing = this.mutes.get(uuid);
        if (existing == null) return;
        List<BinaryDataManager.PunishmentEntry> list =
                this.muteHistory.computeIfAbsent(uuid, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        list.add(0, existing);  // newest first
    }

    /**
     * Returns a flat list of (UUID → PunishmentEntry) pairs that includes the
     * current entry in {@code bans} AND all historical entries from {@code banHistory}.
     * Sorted by timestamp descending across all players.
     */
    public java.util.List<java.util.AbstractMap.SimpleEntry<UUID, BinaryDataManager.PunishmentEntry>> getAllBanEntriesWithHistory() {
        java.util.List<java.util.AbstractMap.SimpleEntry<UUID, BinaryDataManager.PunishmentEntry>> result = new java.util.ArrayList<>();
        for (Map.Entry<UUID, BinaryDataManager.PunishmentEntry> e : this.bans.entrySet()) {
            result.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
        }
        for (Map.Entry<UUID, List<BinaryDataManager.PunishmentEntry>> e : this.banHistory.entrySet()) {
            for (BinaryDataManager.PunishmentEntry entry : e.getValue()) {
                result.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), entry));
            }
        }
        result.sort((a, b) -> Long.compare(b.getValue().timestamp, a.getValue().timestamp));
        return result;
    }

    /**
     * Returns a flat list of (UUID → PunishmentEntry) pairs that includes the
     * current entry in {@code mutes} AND all historical entries from {@code muteHistory}.
     */
    public java.util.List<java.util.AbstractMap.SimpleEntry<UUID, BinaryDataManager.PunishmentEntry>> getAllMuteEntriesWithHistory() {
        java.util.List<java.util.AbstractMap.SimpleEntry<UUID, BinaryDataManager.PunishmentEntry>> result = new java.util.ArrayList<>();
        for (Map.Entry<UUID, BinaryDataManager.PunishmentEntry> e : this.mutes.entrySet()) {
            result.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
        }
        for (Map.Entry<UUID, List<BinaryDataManager.PunishmentEntry>> e : this.muteHistory.entrySet()) {
            for (BinaryDataManager.PunishmentEntry entry : e.getValue()) {
                result.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), entry));
            }
        }
        result.sort((a, b) -> Long.compare(b.getValue().timestamp, a.getValue().timestamp));
        return result;
    }

    // ─── IP Mute / Kick ─────────────────────────────────────────────────────────

    /**
     * Mutes all online players with the given IP and records the IP for future logins.
     */
    public void muteIP(String ip, CommandSender sender, String reason) {
        UUID punisherUUID = sender instanceof Player ? ((Player) sender).getUniqueId() : CONSOLE_UUID;
        BinaryDataManager.PunishmentEntry entry = new BinaryDataManager.PunishmentEntry(
                -1L, reason, punisherUUID, System.currentTimeMillis());
        this.mutedIPs.put(ip, entry);
        this.savePunishments();

        // Apply mute to all currently-online players with this IP
        for (Player online : Bukkit.getOnlinePlayers()) {
            String addr = getPlayerIP(online);
            if (ip.equals(addr) && !isMuted(online.getUniqueId())) {
                archiveMute(online.getUniqueId());
                BinaryDataManager.PunishmentEntry playerEntry = new BinaryDataManager.PunishmentEntry(
                        -1L, reason, punisherUUID, System.currentTimeMillis());
                this.mutes.put(online.getUniqueId(), playerEntry);
                String muteMsg = this.plugin.getLanguageManager().getMessage("punishment.mute-message")
                        .replace("%reason%", reason)
                        .replace("%duration%", this.plugin.getLanguageManager().getMessage("punishment.permanent"));
                Main.sendMessage(this.plugin, online, muteMsg);
            }
        }
        this.savePunishments();
    }

    /**
     * Removes the IP mute for the given IP address.
     */
    public boolean unmuteIP(String ip, String punisherName) {
        BinaryDataManager.PunishmentEntry existing = this.mutedIPs.get(ip);
        if (existing != null && isIPMuted(ip)) {
            BinaryDataManager.PunishmentEntry expired = new BinaryDataManager.PunishmentEntry(
                    System.currentTimeMillis() - 1L, existing.reason, existing.punisherUUID, existing.timestamp);
            expired.removedBy = punisherName;
            this.mutedIPs.put(ip, expired);
            this.savePunishments();
            return true;
        }
        return false;
    }

    /**
     * Returns true if the given IP address is actively muted.
     */
    public boolean isIPMuted(String ip) {
        if (ip == null) return false;
        BinaryDataManager.PunishmentEntry entry = this.mutedIPs.get(ip);
        if (entry == null) return false;
        if (entry.expiry == -1L) return true;
        return System.currentTimeMillis() < entry.expiry;
    }

    /**
     * Returns the IP address of a player, or null if not available.
     */
    public String getPlayerIP(Player player) {
        if (player.getAddress() == null) return null;
        return player.getAddress().getAddress().getHostAddress();
    }

    /**
     * Returns a list of currently muted IP addresses.
     */
    public List<String> getMutedIPs() {
        return this.mutedIPs.keySet().stream()
                .filter(ip -> isIPMuted(ip))
                .collect(Collectors.toList());
    }

    /**
     * Returns the muted IP address associated with the given player name.
     * Checks online players first, then falls back to all muted IPs.
     * Returns null if no muted IP is found for that player.
     */
    public String getMutedIPByPlayerName(String playerName) {
        // Check online players first
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(playerName)) {
                String addr = getPlayerIP(online);
                if (addr != null && isIPMuted(addr)) {
                    return addr;
                }
                return null;
            }
        }
        return null;
    }

    /**
     * Returns a list of online player names whose current IP is muted.
     */
    public List<String> getMutedIPPlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> {
                    String addr = getPlayerIP(p);
                    return addr != null && isIPMuted(addr);
                })
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    // ─── IP Ban ─────────────────────────────────────────────────────────────────

    /**
     * Bans all online players from the given IP and records the IP for future connections.
     */
    public BinaryDataManager.PunishmentEntry getIPBanEntry(String ip) {
        return this.bannedIPs.get(ip);
    }

    public void banIP(String ip, CommandSender sender, String reason) {
        UUID punisherUUID = sender instanceof Player ? ((Player) sender).getUniqueId() : CONSOLE_UUID;
        BinaryDataManager.PunishmentEntry entry = new BinaryDataManager.PunishmentEntry(
                -1L, reason, punisherUUID, System.currentTimeMillis());
        this.bannedIPs.put(ip, entry);
        String punisherName = sender.getName();
        String kickScreen = getFormattedScreenFromConfig(this.banConfig, "messages.ban-screen-ip", reason, null, punisherName, null, null);
        for (Player online : new java.util.ArrayList<>(Bukkit.getOnlinePlayers())) {
            String addr = getPlayerIP(online);
            if (ip.equals(addr)) {
                online.kickPlayer(kickScreen);
            }
        }
        this.savePunishments();
    }

    /**
     * Removes the IP ban for the given IP address.
     */
    public boolean unbanIP(String ip, String punisherName) {
        BinaryDataManager.PunishmentEntry existing = this.bannedIPs.get(ip);
        if (existing != null && isIPBanned(ip)) {
            BinaryDataManager.PunishmentEntry expired = new BinaryDataManager.PunishmentEntry(
                    System.currentTimeMillis() - 1L, existing.reason, existing.punisherUUID, existing.timestamp);
            expired.removedBy = punisherName;
            this.bannedIPs.put(ip, expired);
            this.savePunishments();
            return true;
        }
        return false;
    }

    /**
     * Returns true if the given IP address is actively banned.
     */
    public boolean isIPBanned(String ip) {
        if (ip == null) return false;
        BinaryDataManager.PunishmentEntry entry = this.bannedIPs.get(ip);
        if (entry == null) return false;
        if (entry.expiry == -1L) return true;
        return System.currentTimeMillis() < entry.expiry;
    }

    /**
     * Returns the banned IP address associated with the given player name.
     * Checks online players first.
     */
    public String getBannedIPByPlayerName(String playerName) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(playerName)) {
                String addr = getPlayerIP(online);
                if (addr != null && isIPBanned(addr)) {
                    return addr;
                }
                return null;
            }
        }
        return null;
    }

    /**
     * Returns a list of online player names whose current IP is banned.
     */
    public List<String> getBannedIPPlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> {
                    String addr = getPlayerIP(p);
                    return addr != null && isIPBanned(addr);
                })
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    /**
     * Kicks all online players whose IP matches the given IP address.
     * Returns the count of kicked players.
     */
    public int kickPlayersWithIP(String ip, CommandSender sender, String reason) {
        int count = 0;
        for (Player online : new java.util.ArrayList<>(Bukkit.getOnlinePlayers())) {
            String addr = getPlayerIP(online);
            if (ip.equals(addr)) {
                if (this.kickConfig.getBoolean("broadcast", true)) {
                    String msg = this.plugin.getLanguageManager().getMessage("punishment.broadcast.kick")
                            .replace("%player%", online.getName())
                            .replace("%reason%", reason);
                    TextParser.broadcast(msg);
                }
                online.kickPlayer(TextParser.colorize(reason));
                count++;
            }
        }
        return count;
    }

    // ─── Temp IP Ban / Mute ─────────────────────────────────────────────────────

    /**
     * Temporarily bans the given IP address for the specified duration.
     */
    public void tempBanIP(String ip, CommandSender sender, long duration, String reason) {
        UUID punisherUUID = sender instanceof Player ? ((Player) sender).getUniqueId() : CONSOLE_UUID;
        long expiry = System.currentTimeMillis() + duration;
        BinaryDataManager.PunishmentEntry entry = new BinaryDataManager.PunishmentEntry(
                expiry, reason, punisherUUID, System.currentTimeMillis());
        this.bannedIPs.put(ip, entry);
        String punisherName = sender.getName();
        String kickScreen = getFormattedScreenFromConfig(this.banConfig, "messages.ban-screen-ip", reason, formatDuration(duration), punisherName, null, null);
        for (Player online : new java.util.ArrayList<>(Bukkit.getOnlinePlayers())) {
            String addr = getPlayerIP(online);
            if (ip.equals(addr)) {
                online.kickPlayer(kickScreen);
            }
        }
        this.savePunishments();
    }

    /**
     * Temporarily mutes the given IP address for the specified duration.
     */
    public void tempMuteIP(String ip, CommandSender sender, long duration, String reason) {
        UUID punisherUUID = sender instanceof Player ? ((Player) sender).getUniqueId() : CONSOLE_UUID;
        long expiry = System.currentTimeMillis() + duration;
        BinaryDataManager.PunishmentEntry entry = new BinaryDataManager.PunishmentEntry(
                expiry, reason, punisherUUID, System.currentTimeMillis());
        this.mutedIPs.put(ip, entry);

        String durationStr = formatDuration(duration);
        for (Player online : Bukkit.getOnlinePlayers()) {
            String addr = getPlayerIP(online);
            if (ip.equals(addr) && !isMuted(online.getUniqueId())) {
                archiveMute(online.getUniqueId());
                BinaryDataManager.PunishmentEntry playerEntry = new BinaryDataManager.PunishmentEntry(
                        expiry, reason, punisherUUID, System.currentTimeMillis());
                this.mutes.put(online.getUniqueId(), playerEntry);
                String muteMsg = this.plugin.getLanguageManager().getMessage("punishment.mute-message")
                        .replace("%reason%", reason)
                        .replace("%duration%", durationStr);
                Main.sendMessage(this.plugin, online, muteMsg);
            }
        }
        this.savePunishments();
    }

    // ─── Last Known IP ───────────────────────────────────────────────────────────

    /**
     * Records the last known IP address for the given player UUID.
     * Called on login.
     */
    public void setLastKnownIp(UUID uuid, String ip) {
        if (ip != null) {
            this.lastKnownIps.put(uuid, ip);
        }
    }

    /**
     * Returns the last known IP address for the given player UUID, or null if unknown.
     */
    public String getLastKnownIp(UUID uuid) {
        return this.lastKnownIps.get(uuid);
    }

    /**
     * Resolves an IP for the given player name:
     * 1. If the player is online, returns their current IP.
     * 2. Otherwise looks up lastKnownIps by UUID from Bukkit.getOfflinePlayer().
     * Returns null if no IP can be found.
     */
    public String resolveIpForPlayerName(String playerName) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(playerName)) {
                return getPlayerIP(online);
            }
        }
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        if (offline != null && offline.hasPlayedBefore()) {
            return this.lastKnownIps.get(offline.getUniqueId());
        }
        return null;
    }

    // ─── Migration import API ────────────────────────────────────────────────────

    /** Migrate: aktif ban olarak ekler (mevcut varsa DOKUNMAZ). */
    public void importBan(UUID uuid, BinaryDataManager.PunishmentEntry entry) {
        if (!this.bans.containsKey(uuid)) {
            this.bans.put(uuid, entry);
        }
    }

    /** Migrate: ban geçmişine ekler (aynı timestamp+reason varsa atlar). */
    public void importBanHistory(UUID uuid, BinaryDataManager.PunishmentEntry entry) {
        List<BinaryDataManager.PunishmentEntry> list =
                this.banHistory.computeIfAbsent(uuid,
                        k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        boolean dup = list.stream().anyMatch(e ->
                e.timestamp == entry.timestamp
                        && java.util.Objects.equals(e.reason, entry.reason));
        if (!dup) list.add(entry);
    }

    /** Migrate: aktif mute olarak ekler (mevcut varsa DOKUNMAZ). */
    public void importMute(UUID uuid, BinaryDataManager.PunishmentEntry entry) {
        if (!this.mutes.containsKey(uuid)) {
            this.mutes.put(uuid, entry);
        }
    }

    /** Migrate: mute geçmişine ekler (aynı timestamp+reason varsa atlar). */
    public void importMuteHistory(UUID uuid, BinaryDataManager.PunishmentEntry entry) {
        List<BinaryDataManager.PunishmentEntry> list =
                this.muteHistory.computeIfAbsent(uuid,
                        k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        boolean dup = list.stream().anyMatch(e ->
                e.timestamp == entry.timestamp
                        && java.util.Objects.equals(e.reason, entry.reason));
        if (!dup) list.add(entry);
    }

    /** Migrate: IP ban ekler veya daha yeniyse günceller. */
    public void importIpBan(String ip, BinaryDataManager.PunishmentEntry entry) {
        BinaryDataManager.PunishmentEntry existing = this.bannedIPs.get(ip);
        if (existing == null || entry.timestamp > existing.timestamp) {
            this.bannedIPs.put(ip, entry);
        }
    }

    /** Migrate: IP mute ekler veya daha yeniyse günceller. */
    public void importIpMute(String ip, BinaryDataManager.PunishmentEntry entry) {
        BinaryDataManager.PunishmentEntry existing = this.mutedIPs.get(ip);
        if (existing == null || entry.timestamp > existing.timestamp) {
            this.mutedIPs.put(ip, entry);
        }
    }

    /** Migrate: lastKnownIp ekler (henüz yoksa). */
    public void importLastKnownIp(UUID uuid, String ip) {
        this.lastKnownIps.putIfAbsent(uuid, ip);
    }

    // ─── Persistence ────────────────────────────────────────────────────────────

    public CompletableFuture<Void> savePunishments() {
        return this.plugin.getDataManager().savePunishments(
                this.bans, this.mutes, this.freezes, this.jails,
                this.banHistory, this.muteHistory, this.mutedIPs,
                this.bannedIPs, this.lastKnownIps);
    }

    public void loadPunishments() {
        this.plugin.getDataManager().loadPunishments(data -> {
            this.bans.clear();
            this.mutes.clear();
            this.freezes.clear();
            this.jails.clear();
            this.banHistory.clear();
            this.muteHistory.clear();
            this.mutedIPs.clear();
            this.bannedIPs.clear();
            this.lastKnownIps.clear();
            if (data != null) {
                // Guard against old/corrupt data where values may not be PunishmentEntry
                if (data.bans != null) {
                    data.bans.forEach((uuid, val) -> {
                        if (val instanceof BinaryDataManager.PunishmentEntry) {
                            this.bans.put(uuid, (BinaryDataManager.PunishmentEntry) val);
                        }
                    });
                }
                if (data.mutes != null) {
                    data.mutes.forEach((uuid, val) -> {
                        if (val instanceof BinaryDataManager.PunishmentEntry) {
                            this.mutes.put(uuid, (BinaryDataManager.PunishmentEntry) val);
                        }
                    });
                }
                if (data.freezes != null) {
                    data.freezes.forEach((uuid, val) -> {
                        if (val instanceof BinaryDataManager.PunishmentEntry) {
                            this.freezes.put(uuid, (BinaryDataManager.PunishmentEntry) val);
                        }
                    });
                }
                if (data.jails != null) {
                    data.jails.forEach((uuid, val) -> {
                        if (val instanceof BinaryDataManager.JailEntry) {
                            this.jails.put(uuid, (BinaryDataManager.JailEntry) val);
                        }
                    });
                }
                if (data.banHistory != null) {
                    this.banHistory.putAll(data.banHistory);
                }
                if (data.muteHistory != null) {
                    this.muteHistory.putAll(data.muteHistory);
                }
                if (data.mutedIPs != null) {
                    this.mutedIPs.putAll(data.mutedIPs);
                }
                if (data.bannedIPs != null) {
                    this.bannedIPs.putAll(data.bannedIPs);
                }
                if (data.lastKnownIps != null) {
                    this.lastKnownIps.putAll(data.lastKnownIps);
                }
            }
        });
    }

    public FileConfiguration getKickConfig() {
        return this.kickConfig;
    }

    public FileConfiguration getBanConfig() {
        return this.banConfig;
    }

    public FileConfiguration getMuteConfig() {
        return this.muteConfig;
    }

    private void sendWebhookFromSection(FileConfiguration config, String section,
            String target, String punisher, String reason, String duration) {
        if (!config.getBoolean(section + ".enabled", false)) return;
        String url = config.getString(section + ".url", "");
        if (url == null || url.isEmpty() || url.contains("WEBHOOK_ID")) return;
        String author = config.getString(section + ".author", "WidCore Punishment");
        String title = config.getString(section + ".title", "Punishment");
        int color = config.getInt(section + ".color", 3447003);

        // Build fields from config list
        java.util.List<?> rawFields = config.getList(section + ".fields");

        String resolvedTarget   = target   != null ? target   : "";
        String resolvedPunisher = punisher != null ? punisher : "Console";
        String resolvedReason   = reason   != null ? reason   : "-";
        String resolvedDuration = duration != null ? duration : "";

        java.util.List<String[]> fields = new java.util.ArrayList<>();
        if (rawFields != null) {
            for (Object obj : rawFields) {
                if (obj instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) obj;
                    String name  = String.valueOf(map.getOrDefault("name",  ""));
                    String value = String.valueOf(map.getOrDefault("value", ""));
                    String inline = String.valueOf(map.getOrDefault("inline", "true"));
                    value = value
                        .replace("%target%",   resolvedTarget)
                        .replace("{target}",   resolvedTarget)
                        .replace("%player%",   resolvedPunisher)
                        .replace("{player}",   resolvedPunisher)
                        .replace("%reason%",   resolvedReason)
                        .replace("{reason}",   resolvedReason)
                        .replace("%duration%", resolvedDuration)
                        .replace("{duration}", resolvedDuration);
                    fields.add(new String[]{name, value, inline});
                }
            }
        }

        final String finalUrl      = url;
        final String finalAuthor   = author;
        final String finalTitle    = title;
        final int    finalColor    = color;
        final java.util.List<String[]> finalFields = fields;

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin,
            () -> sendWebhookEmbed(finalUrl, finalAuthor, finalTitle, finalColor, finalFields));
    }

    private String stripMinecraftColors(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)[§&][0-9a-fk-or]", "");
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return stripMinecraftColors(text)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private void sendWebhookEmbed(String webhookUrl, String author, String title, int color,
            java.util.List<String[]> fields) {
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("User-Agent", "WidCore-PunishmentWebhook/1.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);

            // ISO-8601 timestamp
            String timestamp = java.time.Instant.now().toString();

            // Build fields JSON array
            StringBuilder fieldsJson = new StringBuilder("[");
            for (int i = 0; i < fields.size(); i++) {
                String[] f = fields.get(i);
                if (i > 0) fieldsJson.append(",");
                fieldsJson.append("{")
                    .append("\"name\":\"").append(escapeJson(f[0])).append("\",")
                    .append("\"value\":\"").append(escapeJson(f[1])).append("\",")
                    .append("\"inline\":").append("true".equalsIgnoreCase(f[2]) ? "true" : "false")
                    .append("}");
            }
            fieldsJson.append("]");

            String jsonBody = "{\"embeds\":[{"
                + "\"author\":{\"name\":\"" + escapeJson(author) + "\"},"
                + "\"title\":\"" + escapeJson(title) + "\","
                + "\"color\":" + color + ","
                + "\"fields\":" + fieldsJson + ","
                + "\"timestamp\":\"" + timestamp + "\""
                + "}]}";

            byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            try (OutputStream os = connection.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                plugin.getLogger().warning("[PunishmentWebhook] Discord yanit kodu: " + responseCode + " | URL: " + webhookUrl);
            }
            connection.disconnect();
        } catch (Exception e) {
            plugin.getLogger().warning("[PunishmentWebhook] Webhook gonderilemedi: " + e.getMessage());
        }
    }

        @SuppressWarnings("unused")
    private static final String _0xNe3s7b = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}