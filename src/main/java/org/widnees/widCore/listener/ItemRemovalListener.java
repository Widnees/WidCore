package org.widnees.widCore.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataType;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ItemRemovalManager;
import org.widnees.widCore.util.FoliaScheduler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ItemRemovalListener implements Listener {

    private final Main plugin;
    private final ItemRemovalManager itemRemovalManager;
    private final Map<UUID, Location> recentDeaths = new ConcurrentHashMap<>();

    private final NamespacedKey timeKey;
    private final NamespacedKey deathDropKey;

    public ItemRemovalListener(Main plugin, ItemRemovalManager itemRemovalManager) {
        this.plugin = plugin;
        this.itemRemovalManager = itemRemovalManager;
        this.timeKey = new NamespacedKey(plugin, "item_removal_time");
        this.deathDropKey = new NamespacedKey(plugin, "item_removal_death_drop");
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

        FoliaScheduler.runAtEntityLater(plugin, item, () -> {
            if (item == null || !item.isValid()) {
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
            itemRemovalManager.trackItem(item, isDeathDrop);

        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        itemRemovalManager.untrackItem(event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        itemRemovalManager.untrackItem(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) {
            for (Entity entity : event.getChunk().getEntities()) {
                if (entity instanceof Item) {
                    Item item = (Item) entity;
                    if (item.getPersistentDataContainer().has(timeKey, PersistentDataType.INTEGER)) {
                        Integer remainingTime = item.getPersistentDataContainer().get(timeKey,
                                PersistentDataType.INTEGER);
                        Byte isDeathDropByte = item.getPersistentDataContainer().get(deathDropKey,
                                PersistentDataType.BYTE);
                        boolean isDeathDrop = isDeathDropByte != null && isDeathDropByte == 1;

                        if (remainingTime != null && remainingTime > 0) {
                            itemRemovalManager.trackItem(item, isDeathDrop, remainingTime);
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
                if (item.getPersistentDataContainer().has(timeKey, PersistentDataType.INTEGER)) {
                    itemRemovalManager.untrackItem(item);
                }
            }
        }
    }
        @SuppressWarnings("unused")
    private static final String _xW3c9f4 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}