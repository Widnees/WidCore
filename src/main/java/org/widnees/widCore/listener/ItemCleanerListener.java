package org.widnees.widCore.listener;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ItemCleanerManager;
import org.widnees.widCore.util.FoliaScheduler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ItemCleanerListener implements Listener {

    private final Main plugin;
    private final ItemCleanerManager itemCleanerManager;
    private final Map<UUID, Location> recentDeaths = new ConcurrentHashMap<>();

    public ItemCleanerListener(Main plugin, ItemCleanerManager itemCleanerManager) {
        this.plugin = plugin;
        this.itemCleanerManager = itemCleanerManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        final UUID playerUuid = player.getUniqueId();
        recentDeaths.put(playerUuid, player.getLocation());
        FoliaScheduler.runAtEntityLater(plugin, player, () -> {
            recentDeaths.remove(playerUuid);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        final Item item = event.getEntity();

        // Already-fake at spawn (some plugins). Vanilla /give calls makeFakeItem()
        // AFTER this event, so the delayed task below is the real guard.
        if (itemCleanerManager.isGiveAnimationItem(item)) {
            return;
        }

        // Capture delay now: vanilla /give still has the default delay here and
        // only raises it to Short.MAX_VALUE after the event returns.
        final int spawnPickupDelay = item.getPickupDelay();

        FoliaScheduler.runAtEntityLater(plugin, item, () -> {
            if (item == null || !item.isValid()) {
                return;
            }
            // Delay jumped to never-pickup after spawn = vanilla /give visual copy.
            if (spawnPickupDelay < Short.MAX_VALUE && item.getPickupDelay() >= Short.MAX_VALUE) {
                return;
            }
            if (itemCleanerManager.isGiveAnimationItem(item)) {
                return;
            }

            boolean isDeathDrop = false;
            Location itemLocation = item.getLocation();

            for (Location deathLocation : recentDeaths.values()) {
                if (deathLocation.getWorld().equals(itemLocation.getWorld())
                        && deathLocation.distanceSquared(itemLocation) < 4) {
                    isDeathDrop = true;
                    break;
                }
            }
            itemCleanerManager.trackItem(item, isDeathDrop);

        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        itemCleanerManager.untrackItem(event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        itemCleanerManager.untrackItem(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) {
            for (Entity entity : event.getChunk().getEntities()) {
                if (entity instanceof Item) {
                    Item item = (Item) entity;
                    if (itemCleanerManager.hasTrackedData(item)) {
                        Integer remainingTime = itemCleanerManager.getRemainingTime(item);
                        boolean isDeathDrop = itemCleanerManager.isDeathDrop(item);

                        if (remainingTime != null && remainingTime > 0) {
                            itemCleanerManager.trackItem(item, isDeathDrop, remainingTime);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Item) {
                Item item = (Item) entity;
                if (itemCleanerManager.hasTrackedData(item)) {
                    itemCleanerManager.untrackItem(item);
                }
            }
        }
    }
        @SuppressWarnings("unused")
    private static final String _xW3c9f4 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}
