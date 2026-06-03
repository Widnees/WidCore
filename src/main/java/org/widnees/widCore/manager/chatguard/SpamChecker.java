package org.widnees.widCore.manager.chatguard;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.chatguard.ChatGuardChecker;
import org.widnees.widCore.manager.chatguard.ChatGuardResult;

public class SpamChecker
implements ChatGuardChecker {
    private final Main plugin;
    private final ConfigManager configManager;
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<UUID, Long>();

    public SpamChecker(Main plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.configManager.getModuleConfig("chatguard/spam");
    }

    @Override
    public ChatGuardResult check(Player player, String message) {
        long lastTime;
        long timeDiff;
        FileConfiguration config = this.configManager.getModuleConfig("chatguard/spam");
        if (config == null) {
            return ChatGuardResult.allowed();
        }
        int cooldownSeconds = config.getInt("cooldown-seconds", 3);
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        if (this.lastMessageTime.containsKey(playerId) && (timeDiff = (currentTime - (lastTime = this.lastMessageTime.get(playerId).longValue())) / 1000L) < (long)cooldownSeconds) {
            long remainingTime = (long)cooldownSeconds - timeDiff;
            return new ChatGuardResult(false, ChatGuardResult.Type.SPAM, String.valueOf(remainingTime));
        }
        this.lastMessageTime.put(playerId, currentTime);
        return ChatGuardResult.allowed();
    }

    @Override
    public void reload() {
    }

    public void cleanupOfflinePlayers(Set<UUID> onlinePlayers) {
        this.lastMessageTime.entrySet().removeIf(entry -> !onlinePlayers.contains(entry.getKey()));
    }

    public void updateLastMessageTime(UUID playerId) {
        this.lastMessageTime.put(playerId, System.currentTimeMillis());
    }
        @SuppressWarnings("unused")
    private static final String __wNx8b2c = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}
