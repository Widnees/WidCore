package org.widnees.widCore.manager;

import java.util.HashSet;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.chatguard.AdvertisementChecker;
import org.widnees.widCore.manager.chatguard.BannedWordChecker;
import org.widnees.widCore.manager.chatguard.ChatGuardResult;
import org.widnees.widCore.manager.chatguard.FloodChecker;
import org.widnees.widCore.manager.chatguard.SpamChecker;
import org.widnees.widCore.manager.chatguard.TextUtils;

public class ChatGuardManager {
    private final Main plugin;
    private final SpamChecker spamChecker;
    private final FloodChecker floodChecker;
    private final BannedWordChecker bannedWordChecker;
    private final AdvertisementChecker advertisementChecker;
    private long lastCacheCleanup = System.currentTimeMillis();
    private static final long CACHE_CLEANUP_INTERVAL = 300000L;

    public ChatGuardManager(Main plugin) {
        this.plugin = plugin;
        this.spamChecker = new SpamChecker(plugin);
        this.floodChecker = new FloodChecker(plugin);
        this.bannedWordChecker = new BannedWordChecker(plugin);
        this.advertisementChecker = new AdvertisementChecker(plugin);
    }

    public ChatGuardResult checkMessage(Player player, String message) {
        ChatGuardResult result;
        this.performCacheCleanupIfNeeded();
        if (this.plugin.getConfig().getBoolean("chatguard.bannedword", true) && !(result = this.bannedWordChecker.check(player, message)).isAllowed()) {
            return result;
        }
        if (this.plugin.getConfig().getBoolean("chatguard.spam", true) && !(result = this.spamChecker.check(player, message)).isAllowed()) {
            return result;
        }
        if (this.plugin.getConfig().getBoolean("chatguard.flood", true) && !(result = this.floodChecker.check(player, message)).isAllowed()) {
            return result;
        }
        if (this.plugin.getConfig().getBoolean("chatguard.advertisement", true) && !(result = this.advertisementChecker.check(player, message)).isAllowed()) {
            return result;
        }
        this.floodChecker.updateMessageHistory(player, message);
        return ChatGuardResult.allowed();
    }

    public ChatGuardResult checkBannedWordsOnly(String text) {
        if (!this.plugin.getConfig().getBoolean("chatguard.bannedword", true)) {
            return ChatGuardResult.allowed();
        }
        return this.bannedWordChecker.checkBannedWords(text);
    }

    public void reloadSymbolReplacements() {
        this.spamChecker.reload();
        this.floodChecker.reload();
        this.bannedWordChecker.reload();
        this.advertisementChecker.reload();
        this.cleanupCaches();
    }

    public void cleanupCaches() {
        TextUtils.clearCache();
        this.cleanupOfflinePlayerData();
    }

    private void performCacheCleanupIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastCacheCleanup > 300000L) {
            this.cleanupCaches();
            this.lastCacheCleanup = currentTime;
        }
    }

    private void cleanupOfflinePlayerData() {
        HashSet<UUID> onlinePlayers = new HashSet<UUID>();
        this.plugin.getServer().getOnlinePlayers().forEach(p -> {
            boolean bl = onlinePlayers.add(p.getUniqueId());
        });
        this.spamChecker.cleanupOfflinePlayers(onlinePlayers);
        this.floodChecker.cleanupOfflinePlayers(onlinePlayers);
    }

    public SpamChecker getSpamChecker() {
        return this.spamChecker;
    }

    public FloodChecker getFloodChecker() {
        return this.floodChecker;
    }

    public BannedWordChecker getBannedWordChecker() {
        return this.bannedWordChecker;
    }

    public AdvertisementChecker getAdvertisementChecker() {
        return this.advertisementChecker;
    }
        @SuppressWarnings("unused")
    private static final String _0xNe3s7b = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}
