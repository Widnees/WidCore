package org.widnees.widCore.manager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.widnees.widCore.Main;
import org.widnees.widCore.util.FoliaScheduler;
import org.widnees.widCore.util.VersionSupport;

public class ItemCleanerManager {
    private final Main plugin;
    private final Set<Item> trackedItems = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Object> itemTasks = new ConcurrentHashMap<UUID, Object>();
    private final File persistenceFile;
    private int deathLifespan;
    private int defaultLifespan;
    private int hologramVisibleDistance;
    private boolean enablePlayerDeathRemoval;
    private boolean enableDefaultRemoval;
    private final NamespacedKey timeKey;
    private final NamespacedKey deathDropKey;
    private final NamespacedKey legacyTimeKey;
    private final NamespacedKey legacyDeathDropKey;

    public ItemCleanerManager(Main plugin) {
        this.plugin = plugin;
        this.timeKey = new NamespacedKey((Plugin)plugin, "itemcleaner_time");
        this.deathDropKey = new NamespacedKey((Plugin)plugin, "itemcleaner_death_drop");
        this.legacyTimeKey = new NamespacedKey((Plugin)plugin, "item_removal_time");
        this.legacyDeathDropKey = new NamespacedKey((Plugin)plugin, "item_removal_death_drop");
        this.reloadConfigValues();
        File databaseDir = new File(plugin.getDataFolder(), "database");
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }
        this.persistenceFile = new File(databaseDir, "itemcleaner.dat");
        File legacyPersistenceFile = new File(databaseDir, "item_removals.dat");
        if (!this.persistenceFile.exists() && legacyPersistenceFile.exists()) {
            if (!legacyPersistenceFile.renameTo(this.persistenceFile)) {
                this.plugin.getLogger().warning("Failed to migrate item_removals.dat to itemcleaner.dat");
            }
        }
    }

    public NamespacedKey getTimeKey() {
        return this.timeKey;
    }

    public NamespacedKey getDeathDropKey() {
        return this.deathDropKey;
    }

    public NamespacedKey getLegacyTimeKey() {
        return this.legacyTimeKey;
    }

    public NamespacedKey getLegacyDeathDropKey() {
        return this.legacyDeathDropKey;
    }

    public void reloadConfigValues() {
        FileConfiguration config = this.plugin.getConfigManager().getModuleConfig("itemcleaner");
        this.enablePlayerDeathRemoval = config.getBoolean("enable-player-death-removal", true);
        this.enableDefaultRemoval = config.getBoolean("enable-default-removal", true);
        this.deathLifespan = config.getInt("player-death-lifespan-seconds", 300);
        this.defaultLifespan = config.getInt("default-lifespan-seconds", 60);
        this.hologramVisibleDistance = config.getInt("hologram-visible-distance", 10);
    }

    public void trackItem(Item item, boolean fromDeath) {
        if (fromDeath && !this.enablePlayerDeathRemoval) {
            return;
        }
        if (!fromDeath && !this.enableDefaultRemoval) {
            return;
        }
        int lifespan = fromDeath ? this.deathLifespan : this.defaultLifespan;
        this.trackItem(item, fromDeath, lifespan);
    }

    public void trackItem(Item item, boolean fromDeath, int initialLifespan) {
        if (item == null || !item.isValid() || this.trackedItems.contains(item)) {
            return;
        }
        if (this.isGiveAnimationItem(item)) {
            return;
        }
        item.setTicksLived(1);
        item.getPersistentDataContainer().set(this.timeKey, PersistentDataType.INTEGER, initialLifespan);
        item.getPersistentDataContainer().set(this.deathDropKey, PersistentDataType.BYTE, ((byte)(fromDeath ? 1 : 0)));
        item.getPersistentDataContainer().remove(this.legacyTimeKey);
        item.getPersistentDataContainer().remove(this.legacyDeathDropKey);
        this.trackedItems.add(item);
        this.startItemTask(item);
    }

    public boolean isGiveAnimationItem(Item item) {
        if (item == null) {
            return false;
        }
        return item.getPickupDelay() >= Short.MAX_VALUE && item.getTicksLived() >= 10;
    }

    private void startItemTask(Item item) {
        UUID itemId = item.getUniqueId();
        Object task = FoliaScheduler.runAtEntityTimer((Plugin)this.plugin, (Entity)item, () -> {
            if (item == null || !item.isValid()) {
                this.cancelItemTask(itemId);
                this.trackedItems.remove(item);
                return;
            }
            Integer timeLeft = this.getRemainingTime(item);
            if (timeLeft == null || timeLeft <= 0) {
                item.remove();
                this.cancelItemTask(itemId);
                this.trackedItems.remove(item);
                return;
            }
            boolean shouldDisplay = false;
            Location loc = item.getLocation();
            for (Entity entity : loc.getWorld().getNearbyEntities(loc, (double)this.hologramVisibleDistance, (double)this.hologramVisibleDistance, (double)this.hologramVisibleDistance)) {
                if (!(entity instanceof Player)) continue;
                shouldDisplay = true;
                break;
            }
            if (shouldDisplay) {
                String format = this.plugin.getLanguageManager().getMessage("itemcleaner.hologram");
                String name = format.replace("%time%", String.valueOf(timeLeft));
                Component nameComponent = TextParser.parse(name);
                VersionSupport vs = this.plugin.getVersionSupport();
                vs.setItemCustomName((Entity)item, nameComponent, vs.componentToLegacy(nameComponent));
            } else {
                item.setCustomNameVisible(false);
            }
            item.getPersistentDataContainer().set(this.timeKey, PersistentDataType.INTEGER, (timeLeft - 1));
            item.getPersistentDataContainer().remove(this.legacyTimeKey);
        }, 1L, 20L);
        if (task != null) {
            this.itemTasks.put(itemId, task);
        }
    }

    public Integer getRemainingTime(Item item) {
        Integer timeLeft = item.getPersistentDataContainer().get(this.timeKey, PersistentDataType.INTEGER);
        if (timeLeft == null) {
            timeLeft = item.getPersistentDataContainer().get(this.legacyTimeKey, PersistentDataType.INTEGER);
        }
        return timeLeft;
    }

    public boolean isDeathDrop(Item item) {
        Byte isDeathDropByte = item.getPersistentDataContainer().get(this.deathDropKey, PersistentDataType.BYTE);
        if (isDeathDropByte == null) {
            isDeathDropByte = item.getPersistentDataContainer().get(this.legacyDeathDropKey, PersistentDataType.BYTE);
        }
        return isDeathDropByte != null && isDeathDropByte == 1;
    }

    public boolean hasTrackedData(Item item) {
        return item.getPersistentDataContainer().has(this.timeKey, PersistentDataType.INTEGER)
                || item.getPersistentDataContainer().has(this.legacyTimeKey, PersistentDataType.INTEGER);
    }

    private void cancelItemTask(UUID itemId) {
        Object task = this.itemTasks.remove(itemId);
        if (task != null) {
            FoliaScheduler.cancelTask(task);
        }
    }

    public void untrackItem(Item item) {
        this.trackedItems.remove(item);
        if (item != null) {
            this.cancelItemTask(item.getUniqueId());
            if (item.isValid()) {
                item.getPersistentDataContainer().remove(this.timeKey);
                item.getPersistentDataContainer().remove(this.deathDropKey);
                item.getPersistentDataContainer().remove(this.legacyTimeKey);
                item.getPersistentDataContainer().remove(this.legacyDeathDropKey);
                item.setCustomNameVisible(false);
            }
        }
    }

    public void pauseTasks() {
        for (UUID itemId : this.itemTasks.keySet()) {
            this.cancelItemTask(itemId);
        }
        this.itemTasks.clear();
    }

    public void resumeTasks() {
        this.reloadConfigValues();
        for (Item item : this.trackedItems) {
            if (item == null || !item.isValid()) continue;
            this.startItemTask(item);
        }
    }

    public void startup() {
        if (!this.persistenceFile.exists()) {
            return;
        }
        List<StoredItem> loadedItems = this.loadItemsFromFile();
        if (loadedItems.isEmpty()) {
            this.persistenceFile.delete();
            return;
        }
        for (StoredItem stored : loadedItems) {
            World world = Bukkit.getWorld((String)stored.worldName);
            if (world == null) continue;
            try {
                ItemStack stack;
                ItemStack[] items = ItemStackSerializer.fromBase64(stored.serializedItemStack);
                if (items == null || items.length <= 0 || (stack = items[0]) == null || stack.getType().isAir()) continue;
                Location loc = new Location(world, stored.x, stored.y, stored.z);
                FoliaScheduler.runAtLocation((Plugin)this.plugin, loc, () -> {
                    Item newItem = world.dropItem(loc, stack);
                    this.trackItem(newItem, stored.isDeathDrop, stored.remainingSeconds);
                });
            }
            catch (Exception e) {
                this.plugin.getLogger().warning(String.valueOf(this.plugin.getLanguageManager().getMessage("database.load-item-error")) + ": " + e.getMessage());
            }
        }
        this.persistenceFile.delete();
    }

    public void shutdown() {
        if (this.trackedItems.isEmpty()) {
            return;
        }
        ArrayList<StoredItem> itemsToSave = new ArrayList<StoredItem>();
        for (Item item : this.trackedItems) {
            if (item.isValid()) {
                StoredItem stored = new StoredItem();
                stored.worldName = item.getWorld().getName();
                stored.x = item.getLocation().getX();
                stored.y = item.getLocation().getY();
                stored.z = item.getLocation().getZ();
                Integer remainingTime = this.getRemainingTime(item);
                stored.remainingSeconds = remainingTime != null ? remainingTime : 0;
                stored.isDeathDrop = this.isDeathDrop(item);
                try {
                    stored.serializedItemStack = ItemStackSerializer.toBase64(new ItemStack[]{item.getItemStack()});
                    itemsToSave.add(stored);
                }
                catch (IOException e) {
                    this.plugin.getLogger().warning(String.valueOf(this.plugin.getLanguageManager().getMessage("database.save-item-error")) + ": " + e.getMessage());
                }
                item.remove();
            }
            this.cancelItemTask(item.getUniqueId());
        }
        this.saveItemsToFile(itemsToSave);
        this.trackedItems.clear();
        this.itemTasks.clear();
    }

    private void saveItemsToFile(List<StoredItem> items) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(this.persistenceFile))) {
            oos.writeObject(items);
        }
        catch (IOException e) {
            this.plugin.getLogger().severe(String.valueOf(this.plugin.getLanguageManager().getMessage("database.save-file-error")) + ": " + e.getMessage());
        }
    }

    private List<StoredItem> loadItemsFromFile() {
        List<StoredItem> items = new ArrayList<StoredItem>();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(this.persistenceFile))) {
            Object readObject = ois.readObject();
            if (readObject instanceof List) {
                items = (List)readObject;
            }
        }
        catch (IOException | ClassNotFoundException e) {
            this.plugin.getLogger().severe(String.valueOf(this.plugin.getLanguageManager().getMessage("database.read-file-error")) + ": " + e.getMessage());
        }
        return items;
    }

    private static class StoredItem
    implements Serializable {
        private static final long serialVersionUID = 1L;
        String serializedItemStack;
        String worldName;
        double x;
        double y;
        double z;
        int remainingSeconds;
        boolean isDeathDrop;

        private StoredItem() {
        }
    }
        @SuppressWarnings("unused")
    private static final String _W3f0b7c = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}
