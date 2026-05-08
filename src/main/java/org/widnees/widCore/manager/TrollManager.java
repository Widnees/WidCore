package org.widnees.widCore.manager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.widnees.widCore.util.FoliaScheduler;

public class TrollManager {
    private final Set<UUID> mlgTrollPlayers = new HashSet<UUID>();
    private final Map<UUID, Object> mobLookTrollTasks = new HashMap<UUID, Object>();
    private final Set<UUID> creeperChestTrollPlayers = new HashSet<UUID>();
    private final Map<UUID, Location> creeperChestLocations = new HashMap<UUID, Location>();
    private final Set<UUID> frozenByCreeper = new HashSet<UUID>();
    private final Map<UUID, UUID> playerToCreeperMap = new HashMap<UUID, UUID>();
    private final Map<UUID, UUID> creeperToPlayerMap = new HashMap<UUID, UUID>();

    public void addMlgPlayer(UUID playerId) {
        this.mlgTrollPlayers.add(playerId);
    }

    public void removeMlgPlayer(UUID playerId) {
        this.mlgTrollPlayers.remove(playerId);
    }

    public boolean isMlgPlayer(UUID playerId) {
        return this.mlgTrollPlayers.contains(playerId);
    }

    public void addMobLookTask(UUID playerId, Object task) {
        if (this.isMobLookTrolled(playerId)) {
            this.removeMobLookTask(playerId);
        }
        this.mobLookTrollTasks.put(playerId, task);
    }

    public void removeMobLookTask(UUID playerId) {
        if (this.mobLookTrollTasks.containsKey(playerId)) {
            FoliaScheduler.cancelTask(this.mobLookTrollTasks.get(playerId));
            this.mobLookTrollTasks.remove(playerId);
        }
    }

    public boolean isMobLookTrolled(UUID playerId) {
        return this.mobLookTrollTasks.containsKey(playerId);
    }

    public void addCreeperChestPlayer(UUID playerId, Location chestLocation) {
        this.creeperChestTrollPlayers.add(playerId);
        this.creeperChestLocations.put(playerId, chestLocation);
    }

    public void removeCreeperChestPlayer(UUID playerId) {
        this.creeperChestTrollPlayers.remove(playerId);
        this.creeperChestLocations.remove(playerId);
    }

    public boolean isCreeperChestPlayer(UUID playerId) {
        return this.creeperChestTrollPlayers.contains(playerId);
    }

    public Location getCreeperChestLocation(UUID playerId) {
        return this.creeperChestLocations.get(playerId);
    }

    public void addFrozenByCreeper(UUID playerId) {
        this.frozenByCreeper.add(playerId);
    }

    public void removeFrozenByCreeper(UUID playerId) {
        this.frozenByCreeper.remove(playerId);
    }

    public boolean isFrozenByCreeper(UUID playerId) {
        return this.frozenByCreeper.contains(playerId);
    }

    public void registerTrollCreeper(UUID playerId, UUID creeperId) {
        this.playerToCreeperMap.put(playerId, creeperId);
        this.creeperToPlayerMap.put(creeperId, playerId);
    }

    public UUID getCreeperForPlayer(UUID playerId) {
        return this.playerToCreeperMap.get(playerId);
    }

    public UUID getPlayerForCreeper(UUID creeperId) {
        return this.creeperToPlayerMap.get(creeperId);
    }

    public boolean isTrollCreeper(UUID creeperId) {
        return this.creeperToPlayerMap.containsKey(creeperId);
    }

    public void removeTrollByPlayer(UUID playerId) {
        UUID creeperId = this.playerToCreeperMap.remove(playerId);
        if (creeperId != null) {
            this.creeperToPlayerMap.remove(creeperId);
        }
    }

    public void removeTrollByCreeper(UUID creeperId) {
        UUID playerId = this.creeperToPlayerMap.remove(creeperId);
        if (playerId != null) {
            this.playerToCreeperMap.remove(playerId);
        }
    }
}
