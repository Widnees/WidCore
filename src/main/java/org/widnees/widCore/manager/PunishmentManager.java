package org.widnees.widCore.manager;

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
            String msg = this.plugin.getLanguageManager().getMessage("punishment.broadcast.kick").replace("%player%", target.getName()).replace("%reason%", reason);
            Bukkit.broadcastMessage((String)TextParser.colorize(msg));
        }
        String kickScreen = this.getFormattedScreen("punishment.kick-screen", reason, null);
        target.kickPlayer(kickScreen);
    }

    public void banPlayer(Player target, CommandSender sender, String reason) {
        UUID punisherUUID = sender instanceof Player ? ((Player)sender).getUniqueId() : CONSOLE_UUID;
        BinaryDataManager.PunishmentEntry entry = new BinaryDataManager.PunishmentEntry(-1L, reason, punisherUUID, System.currentTimeMillis());
        this.bans.put(target.getUniqueId(), entry);
        this.savePunishments();
        String durationStr = "S\u00fcresiz";
        if (this.banConfig.getBoolean("broadcast", true)) {
            String msg = this.plugin.getLanguageManager().getMessage("punishment.broadcast.ban").replace("%player%", target.getName()).replace("%reason%", reason).replace("%duration%", durationStr);
            Bukkit.broadcastMessage((String)TextParser.colorize(msg));
        }
        target.kickPlayer(this.getFormattedScreen("punishment.ban-screen", reason, durationStr));
    }

    public void tempBanPlayer(Player target, CommandSender sender, long duration, String reason) {
        long expiry = System.currentTimeMillis() + duration;
        UUID punisherUUID = sender instanceof Player ? ((Player)sender).getUniqueId() : CONSOLE_UUID;
        BinaryDataManager.PunishmentEntry entry = new BinaryDataManager.PunishmentEntry(expiry, reason, punisherUUID, System.currentTimeMillis());
        this.bans.put(target.getUniqueId(), entry);
        this.savePunishments();
        String durationStr = this.formatDuration(duration);
        if (this.banConfig.getBoolean("broadcast", true)) {
            String msg = this.plugin.getLanguageManager().getMessage("punishment.broadcast.ban").replace("%player%", target.getName()).replace("%reason%", reason).replace("%duration%", durationStr);
            Bukkit.broadcastMessage((String)TextParser.colorize(msg));
        }
        target.kickPlayer(this.getFormattedScreen("punishment.ban-screen", reason, durationStr));
    }

    private String getFormattedScreen(String langKey, String reason, String duration) {
        List<String> lines = this.plugin.getLanguageManager().getMessageList(langKey);
        String text = lines != null && !lines.isEmpty() ? String.join((CharSequence)"\n", lines) : this.plugin.getLanguageManager().getMessage(langKey);
        text = text.replace("%reason%", reason);
        if (duration != null) {
            text = text.replace("%duration%", duration);
        }
        return TextParser.colorize(text);
    }

    public void mutePlayer(Player target, CommandSender sender, String reason) {
        UUID punisherUUID = sender instanceof Player ? ((Player)sender).getUniqueId() : CONSOLE_UUID;
        BinaryDataManager.PunishmentEntry entry = new BinaryDataManager.PunishmentEntry(-1L, reason, punisherUUID, System.currentTimeMillis());
        this.mutes.put(target.getUniqueId(), entry);
        this.savePunishments();
        String durationStr = "S\u00fcresiz";
        if (this.muteConfig.getBoolean("broadcast", true)) {
            String msg = this.plugin.getLanguageManager().getMessage("punishment.broadcast.mute").replace("%player%", target.getName()).replace("%reason%", reason).replace("%duration%", durationStr);
            Bukkit.broadcastMessage((String)TextParser.colorize(msg));
        }
        String muteMsg = this.plugin.getLanguageManager().getMessage("punishment.mute-message").replace("%reason%", reason).replace("%duration%", durationStr);
        Main.sendMessage(this.plugin, (CommandSender)target, muteMsg);
    }

    public void tempMutePlayer(Player target, CommandSender sender, long duration, String reason) {
        long expiry = System.currentTimeMillis() + duration;
        UUID punisherUUID = sender instanceof Player ? ((Player)sender).getUniqueId() : CONSOLE_UUID;
        BinaryDataManager.PunishmentEntry entry = new BinaryDataManager.PunishmentEntry(expiry, reason, punisherUUID, System.currentTimeMillis());
        this.mutes.put(target.getUniqueId(), entry);
        this.savePunishments();
        String durationStr = this.formatDuration(duration);
        if (this.muteConfig.getBoolean("broadcast", true)) {
            String msg = this.plugin.getLanguageManager().getMessage("punishment.broadcast.mute").replace("%player%", target.getName()).replace("%reason%", reason).replace("%duration%", durationStr);
            Bukkit.broadcastMessage((String)TextParser.colorize(msg));
        }
        String muteMsg = this.plugin.getLanguageManager().getMessage("punishment.mute-message").replace("%reason%", reason).replace("%duration%", durationStr);
        Main.sendMessage(this.plugin, (CommandSender)target, muteMsg);
    }

    public boolean unbanPlayer(UUID uuid) {
        if (this.bans.containsKey(uuid)) {
            this.bans.remove(uuid);
            this.savePunishments();
            return true;
        }
        return false;
    }

    public boolean unmutePlayer(UUID uuid) {
        if (this.mutes.containsKey(uuid)) {
            this.mutes.remove(uuid);
            this.savePunishments();
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
        if (System.currentTimeMillis() > expiry) {
            this.mutes.remove(uuid);
            this.savePunishments();
            return false;
        }
        return true;
    }

    public boolean isBanned(UUID uuid) {
        if (!this.bans.containsKey(uuid)) {
            return false;
        }
        long expiry = this.bans.get((Object)uuid).expiry;
        if (expiry == -1L) {
            return true;
        }
        if (System.currentTimeMillis() > expiry) {
            this.bans.remove(uuid);
            this.savePunishments();
            return false;
        }
        return true;
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
        return this.bans.keySet().stream().map(uuid -> Bukkit.getOfflinePlayer((UUID)uuid).getName()).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<String> getMutedPlayerNames() {
        return this.mutes.keySet().stream().map(uuid -> Bukkit.getOfflinePlayer((UUID)uuid).getName()).filter(Objects::nonNull).collect(Collectors.toList());
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
            return "S\u00fcresiz";
        }
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis -= TimeUnit.DAYS.toMillis(days));
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis -= TimeUnit.HOURS.toMillis(hours));
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis -= TimeUnit.MINUTES.toMillis(minutes));
        StringBuilder sb = new StringBuilder();
        if (days > 0L) {
            sb.append(days).append("g ");
        }
        if (hours > 0L) {
            sb.append(hours).append("s ");
        }
        if (minutes > 0L) {
            sb.append(minutes).append("dk ");
        }
        if (seconds > 0L) {
            sb.append(seconds).append("sn");
        }
        return sb.toString().trim();
    }

    public CompletableFuture<Void> savePunishments() {
        return this.plugin.getDataManager().savePunishments(this.bans, this.mutes, this.freezes, this.jails);
    }

    public void loadPunishments() {
        this.plugin.getDataManager().loadPunishments(data -> {
            this.bans.clear();
            this.mutes.clear();
            this.freezes.clear();
            this.jails.clear();
            if (data != null) {
                this.bans.putAll(data.bans);
                this.mutes.putAll(data.mutes);
                if (data.freezes != null) {
                    this.freezes.putAll(data.freezes);
                }
                if (data.jails != null) {
                    this.jails.putAll(data.jails);
                }
            }
        });
    }

    public FileConfiguration getBanConfig() {
        return this.banConfig;
    }

    public FileConfiguration getMuteConfig() {
        return this.muteConfig;
    }
}
