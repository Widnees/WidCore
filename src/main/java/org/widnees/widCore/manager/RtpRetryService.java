package org.widnees.widCore.manager;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.widnees.widCore.Main;

import org.widnees.widCore.util.FoliaScheduler;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RtpRetryService {

    private static Main plugin;
    private static RtpManager rtpManager;

    private static final Map<UUID, Integer> failAttempts = new ConcurrentHashMap<>();

    private static final Set<UUID> successMarker = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, Long> townyCancelledAt = new ConcurrentHashMap<>();

    private RtpRetryService() {}

    public static void initialize(Main pl) {
        plugin = pl;
        rtpManager = plugin.getRtpManager();
    }

    public static void queue(Player player, World world) {
        if (plugin == null || rtpManager == null) {

            if (player != null && world != null) {
                try { plugin.getRtpManager().queueRtp(player, world); } catch (Throwable ignored) {}
            }
            return;
        }

        FileConfiguration cfg = plugin.getConfigManager().getModuleConfig("rtp");
        boolean enabled = cfg.getBoolean("retry", false);

        rtpManager.queueRtp(player, world);

        if (!enabled) return;

        UUID uuid = player.getUniqueId();
        failAttempts.putIfAbsent(uuid, 0);
        watchAndRetry(uuid, world);
    }

    private static void watchAndRetry(UUID uuid, World world) {
        long delay = 20L; 

        FoliaScheduler.runTaskLater(plugin, () -> {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                cleanup(uuid);
                return;
            }

            if (plugin.getRtpManager().isPlayerInRtp(uuid)) {
                watchAndRetry(uuid, world);
                return;
            }

            if (successMarker.remove(uuid)) {
                cleanup(uuid);
                return;
            }

            Long townyTime = townyCancelledAt.get(uuid);
            if (townyTime != null && (System.currentTimeMillis() - townyTime) < 1000L) {
                watchAndRetry(uuid, world);
                return;
            }

            int next = failAttempts.getOrDefault(uuid, 0) + 1;
            failAttempts.put(uuid, next);

            plugin.getRtpManager().queueRtp(p, world);
            watchAndRetry(uuid, world);
        }, delay);
    }

    public static void markSuccess(UUID uuid) {
        successMarker.add(uuid);
    }

    public static void markTownyCancelled(UUID uuid) {
        townyCancelledAt.put(uuid, System.currentTimeMillis());
    }

    private static void cleanup(UUID uuid) {
        failAttempts.remove(uuid);
        successMarker.remove(uuid);
        townyCancelledAt.remove(uuid);
    }
        @SuppressWarnings("unused")
    private static final String __Wx7c4e2 = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}
