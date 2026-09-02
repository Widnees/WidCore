package org.widnees.widCore.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.PunishmentManager;
import org.widnees.widCore.manager.TextParser;

import java.util.UUID;

public class PunishmentListener implements Listener {

    private final Main plugin;
    private final PunishmentManager punishmentManager;

    public PunishmentListener(Main plugin, PunishmentManager punishmentManager) {
        this.plugin = plugin;
        this.punishmentManager = punishmentManager;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!ConfigManager.isConfigLoaded()) {return;}

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (punishmentManager.isMuted(uuid)) {
            event.setCancelled(true);
            long expiry = punishmentManager.getMuteExpiry(uuid);
            org.bukkit.configuration.file.FileConfiguration muteConfig = punishmentManager.getMuteConfig();

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (expiry == -1L) {
                    String msg = muteConfig.getString("messages.mute-permanent",
                            "&cYou are permanently muted on this server.");
                    Main.sendMessage(this.plugin, player, msg);
                } else {
                    String msg = muteConfig.getString("messages.mute-message",
                            "&cYou are muted! Time remaining: %duration%")
                            .replace("%duration%", punishmentManager.formatDuration(expiry - System.currentTimeMillis()));
                    Main.sendMessage(this.plugin, player, msg);
                }
            });
            return;
        }

        String ip = punishmentManager.getPlayerIP(player);
        if (ip != null && punishmentManager.isIPMuted(ip)) {
            event.setCancelled(true);
            org.bukkit.configuration.file.FileConfiguration muteConfig = punishmentManager.getMuteConfig();
            Bukkit.getScheduler().runTask(plugin, () -> {
                String msg = muteConfig.getString("messages.mute-permanent",
                        "&cYou are permanently muted on this server.");
                Main.sendMessage(this.plugin, player, msg);
            });
        }
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!ConfigManager.isConfigLoaded()) {return;}

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        org.bukkit.configuration.file.FileConfiguration banConfig = punishmentManager.getBanConfig();

        String ip = event.getAddress().getHostAddress();
        punishmentManager.setLastKnownIp(uuid, ip);

        if (punishmentManager.isBanned(uuid)) {
            org.widnees.widCore.database.BinaryDataManager.PunishmentEntry entry = punishmentManager.getBanEntry(uuid);
            long expiry = entry != null ? entry.expiry : -1L;
            String reason = entry != null ? entry.reason : "-";
            String punisher = resolvePunisherName(entry);
            String banDate  = entry != null ? formatTimestamp(entry.timestamp) : "-";
            String expiryStr = expiry == -1L
                    ? plugin.getLanguageManager().getMessage("punishment.permanent")
                    : formatTimestamp(expiry);
            String remaining = expiry == -1L ? "" : punishmentManager.formatDuration(expiry - System.currentTimeMillis());

            java.util.List<String> lines = banConfig.getStringList("messages.ban-screen");
            String kickMessage;
            if (lines != null && !lines.isEmpty()) {
                kickMessage = String.join("\n", lines);
            } else {
                kickMessage = banConfig.getString("messages.ban-screen", "&cYou are banned from this server!");
            }
            kickMessage = applyBanPlaceholders(kickMessage, reason, punisher, banDate, expiryStr, remaining);
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, TextParser.colorize(kickMessage));
            return;
        }

        if (punishmentManager.isIPBanned(ip)) {
            org.widnees.widCore.database.BinaryDataManager.PunishmentEntry ipEntry = punishmentManager.getIPBanEntry(ip);
            String ipReason = ipEntry != null ? ipEntry.reason : "-";
            String ipPunisher = resolvePunisherName(ipEntry);
            String ipBanDate = ipEntry != null ? formatTimestamp(ipEntry.timestamp) : "-";
            long ipExpiry = ipEntry != null ? ipEntry.expiry : -1L;
            String ipExpiryStr = ipExpiry == -1L
                    ? plugin.getLanguageManager().getMessage("punishment.permanent")
                    : formatTimestamp(ipExpiry);
            String ipRemaining = ipExpiry == -1L ? "" : punishmentManager.formatDuration(ipExpiry - System.currentTimeMillis());
            java.util.List<String> lines = banConfig.getStringList("messages.ban-screen-ip");
            String kickMessage;
            if (lines != null && !lines.isEmpty()) {
                kickMessage = String.join("\n", lines);
            } else {
                kickMessage = banConfig.getString("messages.ban-screen-ip",
                        "&cYou are banned from this server! (IP Ban)");
            }
            kickMessage = applyBanPlaceholders(kickMessage, ipReason, ipPunisher, ipBanDate, ipExpiryStr, ipRemaining);
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, TextParser.colorize(kickMessage));
            return;
        }

    }

    private String resolvePunisherName(org.widnees.widCore.database.BinaryDataManager.PunishmentEntry entry) {
        if (entry == null) return "Console";
        UUID punisherUUID = entry.punisherUUID;
        if (punisherUUID == null) return "Console";
        if (punisherUUID.equals(java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"))) {
            return "Console";
        }
        String name = Bukkit.getOfflinePlayer(punisherUUID).getName();
        return name != null ? name : punisherUUID.toString();
    }

    private String formatTimestamp(long millis) {
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter
                .ofPattern("dd.MM.yyyy HH:mm")
                .withZone(java.time.ZoneId.systemDefault());
        return fmt.format(java.time.Instant.ofEpochMilli(millis));
    }

    private String applyBanPlaceholders(String text, String reason, String punisher,
            String banDate, String expiry, String remaining) {
        return text
                .replace("%reason%",    reason)
                .replace("%punisher%",  punisher)
                .replace("%ban-date%",  banDate)
                .replace("%expiry%",    expiry)
                .replace("%duration%",  remaining.isEmpty() ? plugin.getLanguageManager().getMessage("punishment.permanent") : remaining)
                .replace("%player%",    punisher);
    }
        @SuppressWarnings("unused")
    private static final String _xCr7w3n = "\u0077\u0069\u0064\u006e" + "\u0065\u0065\u0073";

}