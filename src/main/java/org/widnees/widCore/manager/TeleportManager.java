package org.widnees.widCore.manager;

import java.util.HashMap;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.util.FoliaScheduler;

public class TeleportManager {
    private final Main plugin;
    private final HashMap<UUID, TeleportInfo> teleportTasks = new HashMap();
    private final HashMap<UUID, Location> lastLocations = new HashMap();

    public TeleportManager(Main plugin) {
        this.plugin = plugin;
    }

    public void startTeleporting(Player player, TeleportType type) {
        this.teleportTasks.put(player.getUniqueId(), new TeleportInfo(null, type));
        this.lastLocations.put(player.getUniqueId(), player.getLocation());
    }

    public void updateTask(UUID playerId, Object task) {
        TeleportInfo info = this.teleportTasks.get(playerId);
        if (info != null) {
            info.task = task;
        }
    }

    public void addTeleportTask(Player player, Object task, TeleportType type) {
        this.teleportTasks.put(player.getUniqueId(), new TeleportInfo(task, type));
        this.lastLocations.put(player.getUniqueId(), player.getLocation());
    }

    public void removeTask(UUID playerId) {
        this.teleportTasks.remove(playerId);
        this.lastLocations.remove(playerId);
    }

    public Location getLastLocation(UUID playerId) {
        return this.lastLocations.get(playerId);
    }

    public boolean isTeleporting(UUID playerId) {
        return this.teleportTasks.containsKey(playerId);
    }

    public TeleportType getTeleportType(UUID playerId) {
        if (this.isTeleporting(playerId)) {
            return this.teleportTasks.get((Object)playerId).type;
        }
        return null;
    }

    public void cancelTeleport(Player player, String reason) {
        if (this.isTeleporting(player.getUniqueId())) {
            FoliaScheduler.cancelTask(this.teleportTasks.get((Object)player.getUniqueId()).task);
            Main.sendMessage(this.plugin, (CommandSender)player, "&c" + reason);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.5f);
            this.removeTask(player.getUniqueId());
        }
    }

    public void clearAllTasks() {
        for (TeleportInfo info : this.teleportTasks.values()) {
            FoliaScheduler.cancelTask(info.task);
        }
        this.teleportTasks.clear();
        this.lastLocations.clear();
    }

    private static class TeleportInfo {
        Object task;
        TeleportType type;

        TeleportInfo(Object task, TeleportType type) {
            this.task = task;
            this.type = type;
        }
    }

    public static enum TeleportType {
        SPAWN,
        WARP,
        TPA,
        RTP;

    }
}
