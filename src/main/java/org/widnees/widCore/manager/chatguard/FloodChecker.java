package org.widnees.widCore.manager.chatguard;

import java.util.ArrayList;
import java.util.List;
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
import org.widnees.widCore.manager.chatguard.TextUtils;

public class FloodChecker
implements ChatGuardChecker {
    private final Main plugin;
    private final ConfigManager configManager;
    private final Map<UUID, List<String>> playerMessageHistory = new ConcurrentHashMap<UUID, List<String>>();

    public FloodChecker(Main plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.configManager.getModuleConfig("chatguard/flood");
    }

    @Override
    public ChatGuardResult check(Player player, String message) {
        FileConfiguration config = this.configManager.getModuleConfig("chatguard/flood");
        if (config == null) {
            return ChatGuardResult.allowed();
        }
        int maxRepeat = config.getInt("max-repeat", 3);
        int similarityPercentage = config.getInt("similarity-percentage", 80);
        UUID playerId = player.getUniqueId();
        List<String> history = this.playerMessageHistory.getOrDefault(playerId, new ArrayList<>());
        String cleanedMessage = TextUtils.cleanText(message);
        int repeatCount = 0;
        for (String historyMessage : history) {
            if (!TextUtils.cleanText(historyMessage).equals(cleanedMessage)) continue;
            ++repeatCount;
        }
        if (repeatCount >= maxRepeat) {
            return new ChatGuardResult(false, ChatGuardResult.Type.FLOOD_REPEAT, String.valueOf(maxRepeat));
        }
        for (String historyMessage : history) {
            double similarity = TextUtils.calculateSimilarity(cleanedMessage, TextUtils.cleanText(historyMessage));
            if (!(similarity >= (double)similarityPercentage)) continue;
            return new ChatGuardResult(false, ChatGuardResult.Type.FLOOD_SIMILAR, String.valueOf(similarityPercentage));
        }
        return ChatGuardResult.allowed();
    }

    @Override
    public void reload() {
    }

    public void updateMessageHistory(Player player, String message) {
        UUID playerId = player.getUniqueId();
        List<String> history = this.playerMessageHistory.computeIfAbsent(playerId, k -> new ArrayList<>());
        history.add(message);
        if (history.size() > 5) {
            history.remove(0);
        }
    }

    public void cleanupOfflinePlayers(Set<UUID> onlinePlayers) {
        this.playerMessageHistory.entrySet().removeIf(entry -> !onlinePlayers.contains(entry.getKey()));
    }
        @SuppressWarnings("unused")
    private static final String __W5e9c3x = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}
