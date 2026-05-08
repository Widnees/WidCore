package org.widnees.widCore.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.InventoryBackup;
import org.widnees.widCore.manager.ItemStackSerializer;
import org.widnees.widCore.manager.TextParser;
import org.widnees.widCore.util.FoliaScheduler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

public class BinaryDataManager {

    private final Main plugin;
    private final File databaseDir;
    private final Map<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();
    private final Map<String, Object> fileLocks = new ConcurrentHashMap<>();

    public BinaryDataManager(Main plugin) {
        this.plugin = plugin;
        this.databaseDir = new File(plugin.getDataFolder(), "database");
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }
    }

    public void close() {
        playerDataCache.clear();
        fileLocks.clear();
    }

    public void cachePlayerData(UUID uuid, PlayerData data) {
        if (data != null)
            playerDataCache.put(uuid, data);
    }

    public void uncachePlayerData(UUID uuid) {
        playerDataCache.remove(uuid);
    }

    public PlayerData getCachedPlayerData(UUID uuid) {
        return playerDataCache.get(uuid);
    }

    public CompletableFuture<Void> saveAllCachedPlayerData() {
        
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            PlayerData data = playerDataCache.get(player.getUniqueId());
            if (data != null) {
                data.inventoryStorage = player.getInventory().getStorageContents().clone();
                data.inventoryArmor = player.getInventory().getArmorContents().clone();
                org.bukkit.inventory.ItemStack offhand = player.getInventory().getItemInOffHand();
                data.offhandItem = offhand != null && offhand.getType() != org.bukkit.Material.AIR ? offhand.clone() : null;
                data.enderChestContents = player.getEnderChest().getContents().clone();
            }
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        playerDataCache.forEach((uuid, data) -> {
            futures.add(savePlayerData(uuid, data));
        });
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private CompletableFuture<Void> saveObjectToFile(File file, Serializable data) {
        Object lock = fileLocks.computeIfAbsent(file.getAbsolutePath(), k -> new Object());
        return CompletableFuture.runAsync(() -> {
            synchronized (lock) {
                
                File parentDir = file.getParentFile();
                if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                    plugin.getLogger().severe("Dizin oluşturulamadı: " + parentDir.getAbsolutePath());
                    return;
                }
                File tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tempFile))) {
                    oos.writeObject(data);
                } catch (IOException e) {
                    String msg = plugin.getLanguageManager().getMessage("database.temp-write-error").replace("%file%",
                            tempFile.getName());
                    plugin.getLogger().log(Level.SEVERE, msg, e);
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                    return;
                }

                int maxRetries = 5;
                long delayMs = 100;

                for (int i = 0; i < maxRetries; i++) {
                    try {
                        Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                        return;
                    } catch (IOException e) {
                        if (i < maxRetries - 1) {
                            try {
                                String msg = plugin.getLanguageManager().getMessage("database.file-move-retry")
                                        .replace("%file%", file.getName())
                                        .replace("%time%", String.valueOf(delayMs))
                                        .replace("%try%", String.valueOf(i + 2));
                                plugin.getLogger().warning(msg);
                                Thread.sleep(delayMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                String msg = plugin.getLanguageManager().getMessage("database.save-interrupt")
                                        .replace("%file%", file.getName());
                                plugin.getLogger().log(Level.SEVERE, msg, ie);
                                break;
                            }
                        } else {
                            String msg = plugin.getLanguageManager().getMessage("database.save-critical")
                                    .replace("%try%", String.valueOf(maxRetries))
                                    .replace("%file%", file.getName());
                            plugin.getLogger().log(Level.SEVERE, msg, e);
                        }
                    }
                }

                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
        });
    }

    private <T> CompletableFuture<T> loadObjectFromFile(File file, Class<T> type) {
        Object lock = fileLocks.computeIfAbsent(file.getAbsolutePath(), k -> new Object());
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                if (!file.exists()) {
                    return null;
                }
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                    Object readObject = ois.readObject();

                    if (type.isInstance(readObject)) {
                        return type.cast(readObject);
                    }

                    if (type == PunishmentData.class && readObject instanceof PunishmentData_V3) {
                        plugin.getLogger().info(plugin.getLanguageManager().getMessage("database.v3-detected"));
                        PunishmentData newData = convertV3PunishmentData((PunishmentData_V3) readObject);
                        saveObjectToFile(file, newData).join();
                        plugin.getLogger().info(plugin.getLanguageManager().getMessage("database.v4-success"));
                        return type.cast(newData);
                    }

                    if (type == PunishmentData.class && readObject instanceof PunishmentData_V2) {
                        plugin.getLogger().info(plugin.getLanguageManager().getMessage("database.v2-detected"));
                        PunishmentData newData = convertV2PunishmentData((PunishmentData_V2) readObject);
                        saveObjectToFile(file, newData).join();
                        plugin.getLogger().info(plugin.getLanguageManager().getMessage("database.v4-success"));
                        return type.cast(newData);
                    }

                    if (type == PunishmentData.class && readObject instanceof PunishmentData_Old) {
                        plugin.getLogger().info(plugin.getLanguageManager().getMessage("database.v1-detected"));
                        PunishmentData newData = convertOldPunishmentData((PunishmentData_Old) readObject);
                        saveObjectToFile(file, newData).join();
                        plugin.getLogger().info(plugin.getLanguageManager().getMessage("database.v4-success"));
                        return type.cast(newData);
                    }
                } catch (Exception e) {
                    String msg = plugin.getLanguageManager().getMessage("database.read-error").replace("%file%",
                            file.getName());
                    plugin.getLogger().log(Level.SEVERE, msg, e);
                }
                return null;
            }
        });
    }

    private File getPlayerDataFile(UUID uuid) {
        File playerDataFolder = new File(databaseDir, "playerdata");
        if (!playerDataFolder.exists())
            playerDataFolder.mkdirs();
        return new File(playerDataFolder, uuid.toString() + ".dat");
    }

    private File getPunishmentsFile() {
        return new File(databaseDir, "punishments.dat");
    }

    private File getBackLocationsFile() {
        return new File(databaseDir, "back_locations.dat");
    }

    public static class SerializableLocation implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String worldName;
        private final double x, y, z;
        private final float yaw, pitch;

        public SerializableLocation(Location loc) {
            this.worldName = loc.getWorld().getName();
            this.x = loc.getX();
            this.y = loc.getY();
            this.z = loc.getZ();
            this.yaw = loc.getYaw();
            this.pitch = loc.getPitch();
        }

        public Location toLocation() {
            World world = Bukkit.getWorld(worldName);
            if (world == null)
                return null;
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    public CompletableFuture<Void> savePunishments(Map<UUID, PunishmentEntry> bans, Map<UUID, PunishmentEntry> mutes,
            Map<UUID, PunishmentEntry> freezes, Map<UUID, JailEntry> jails) {
        PunishmentData data = new PunishmentData();
        data.bans = new HashMap<>(bans);
        data.mutes = new HashMap<>(mutes);
        data.freezes = new HashMap<>(freezes);
        data.jails = new HashMap<>(jails);
        return saveObjectToFile(getPunishmentsFile(), data);
    }

    public void loadPunishments(Consumer<PunishmentData> callback) {
        loadObjectFromFile(getPunishmentsFile(), PunishmentData.class).thenAccept(data -> {
            FoliaScheduler.runTask(plugin, () -> {
                callback.accept(data != null ? data : new PunishmentData());
            });
        });
    }

    private PunishmentData convertOldPunishmentData(PunishmentData_Old oldData) {
        PunishmentData newData = new PunishmentData();
        UUID consoleUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
        String defaultReason = plugin.getLanguageManager().getMessage("punishment.unknown-old-data");

        oldData.bans.forEach((uuid, expiry) -> newData.bans.put(uuid,
                new PunishmentEntry(expiry, defaultReason, consoleUUID, System.currentTimeMillis())));
        oldData.mutes.forEach((uuid, expiry) -> newData.mutes.put(uuid,
                new PunishmentEntry(expiry, defaultReason, consoleUUID, System.currentTimeMillis())));

        return newData;
    }

    private PunishmentData convertV2PunishmentData(PunishmentData_V2 oldData) {
        PunishmentData newData = new PunishmentData();
        newData.bans.putAll(oldData.bans);
        newData.mutes.putAll(oldData.mutes);
        return newData;
    }

    private PunishmentData convertV3PunishmentData(PunishmentData_V3 oldData) {
        PunishmentData newData = new PunishmentData();
        newData.bans.putAll(oldData.bans);
        newData.mutes.putAll(oldData.mutes);
        newData.freezes.putAll(oldData.freezes);
        return newData;
    }

    public CompletableFuture<Void> saveDeathLocations(Map<UUID, Location> locations) {
        BackData data = new BackData();
        locations.forEach((uuid, location) -> data.locations.put(uuid, new SerializableLocation(location)));
        return saveObjectToFile(getBackLocationsFile(), data);
    }

    public void loadDeathLocations(Consumer<Map<UUID, Location>> callback) {
        loadObjectFromFile(getBackLocationsFile(), BackData.class).thenAccept(data -> {
            Map<UUID, Location> loadedLocations = new ConcurrentHashMap<>();
            if (data != null && data.locations != null) {
                data.locations.forEach((uuid, serLoc) -> {
                    Location loc = serLoc.toLocation();
                    if (loc != null)
                        loadedLocations.put(uuid, loc);
                });
            }
            FoliaScheduler.runTask(plugin, () -> callback.accept(loadedLocations));
        });
    }

    private File getTempFlyFile() {
        return new File(databaseDir, "tempfly.dat");
    }

    public CompletableFuture<Void> saveTempFlyData(TempFlyData data) {
        return saveObjectToFile(getTempFlyFile(), data);
    }

    public void loadTempFlyData(Consumer<TempFlyData> callback) {
        loadObjectFromFile(getTempFlyFile(), TempFlyData.class).thenAccept(data -> {
            FoliaScheduler.runTask(plugin, () -> callback.accept(data != null ? data : new TempFlyData()));
        });
    }

    public CompletableFuture<PlayerData> loadPlayerData(UUID uuid) {
        if (playerDataCache.containsKey(uuid)) {
            return CompletableFuture.completedFuture(playerDataCache.get(uuid));
        }

        File playerDataFile = getPlayerDataFile(uuid);
        Object lock = fileLocks.computeIfAbsent(playerDataFile.getAbsolutePath(), k -> new Object());

        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                if (!playerDataFile.exists()) {
                    PlayerData newData = new PlayerData();
                    playerDataCache.put(uuid, newData);
                    return newData;
                }

                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(playerDataFile))) {
                    Object readObject = ois.readObject();

                    if (readObject instanceof PlayerData) {
                        PlayerData data = (PlayerData) readObject;
                        data.inventoryStorage = deserializeItemStackArray(data.inventoryStorageData);
                        data.inventoryArmor = deserializeItemStackArray(data.inventoryArmorData);
                        data.offhandItem = deserializeItemStack(data.offhandItemData);
                        data.enderChestContents = deserializeItemStackArray(data.enderChestContentsData);
                        if (data.homes == null)
                            data.homes = new HashMap<>();
                        playerDataCache.put(uuid, data);
                        return data;
                    }

                    if (readObject instanceof PlayerData_V2) {
                        plugin.getLogger().info("Converting old PlayerData format for: " + uuid);
                        PlayerData_V2 oldData = (PlayerData_V2) readObject;
                        PlayerData newData = new PlayerData();
                        newData.inventoryStorageData = oldData.inventoryStorageData;
                        newData.inventoryArmorData = oldData.inventoryArmorData;
                        newData.offhandItemData = oldData.offhandItemData;
                        newData.enderChestContentsData = oldData.enderChestContentsData;
                        newData.homes = new HashMap<>();

                        newData.inventoryStorage = deserializeItemStackArray(newData.inventoryStorageData);
                        newData.inventoryArmor = deserializeItemStackArray(newData.inventoryArmorData);
                        newData.offhandItem = deserializeItemStack(newData.offhandItemData);
                        newData.enderChestContents = deserializeItemStackArray(newData.enderChestContentsData);

                        savePlayerData(uuid, newData).join();
                        playerDataCache.put(uuid, newData);
                        return newData;
                    }
                } catch (java.io.InvalidClassException e) {
                    
                    plugin.getLogger().info("Migrating old PlayerData format for: " + uuid);
                    try (ObjectInputStream ois2 = new ObjectInputStream(new FileInputStream(playerDataFile)) {
                        @Override
                        protected java.io.ObjectStreamClass readClassDescriptor()
                                throws IOException, ClassNotFoundException {
                            java.io.ObjectStreamClass desc = super.readClassDescriptor();
                            if (desc.getName().equals("org.widnees.widCore.database.BinaryDataManager$PlayerData")) {
                                return java.io.ObjectStreamClass.lookup(PlayerData_V2.class);
                            }
                            return desc;
                        }
                    }) {
                        Object readObject = ois2.readObject();
                        if (readObject instanceof PlayerData_V2) {
                            PlayerData_V2 oldData = (PlayerData_V2) readObject;
                            PlayerData newData = new PlayerData();
                            newData.inventoryStorageData = oldData.inventoryStorageData;
                            newData.inventoryArmorData = oldData.inventoryArmorData;
                            newData.offhandItemData = oldData.offhandItemData;
                            newData.enderChestContentsData = oldData.enderChestContentsData;
                            newData.homes = new HashMap<>();

                            newData.inventoryStorage = deserializeItemStackArray(newData.inventoryStorageData);
                            newData.inventoryArmor = deserializeItemStackArray(newData.inventoryArmorData);
                            newData.offhandItem = deserializeItemStack(newData.offhandItemData);
                            newData.enderChestContents = deserializeItemStackArray(newData.enderChestContentsData);

                            savePlayerData(uuid, newData).join();
                            plugin.getLogger().info("PlayerData migrated successfully for: " + uuid);
                            playerDataCache.put(uuid, newData);
                            return newData;
                        }
                    } catch (Exception e2) {
                        String msg = plugin.getLanguageManager().getMessage("database.read-error").replace("%file%",
                                playerDataFile.getName());
                        plugin.getLogger().log(Level.SEVERE, msg, e2);
                    }
                } catch (Exception e) {
                    String msg = plugin.getLanguageManager().getMessage("database.read-error").replace("%file%",
                            playerDataFile.getName());
                    plugin.getLogger().log(Level.SEVERE, msg, e);
                }

                PlayerData newData = new PlayerData();
                playerDataCache.put(uuid, newData);
                return newData;
            }
        });
    }

    private CompletableFuture<Void> savePlayerData(UUID uuid, PlayerData data) {
        data.inventoryStorageData = robustSerializeItemStackArray(data.inventoryStorage);
        data.inventoryArmorData = robustSerializeItemStackArray(data.inventoryArmor);
        data.offhandItemData = robustSerializeItemStack(data.offhandItem);
        data.enderChestContentsData = robustSerializeItemStackArray(data.enderChestContents);
        return saveObjectToFile(getPlayerDataFile(uuid), data);
    }

    public CompletableFuture<Map<String, Location>> getPlayerHomes(UUID uuid) {
        return loadPlayerData(uuid).thenApply(data -> {
            Map<String, Location> homes = new HashMap<>();
            if (data.homes != null) {
                for (Map.Entry<String, SerializableLocation> entry : data.homes.entrySet()) {
                    Location loc = entry.getValue().toLocation();
                    if (loc != null) {
                        homes.put(entry.getKey(), loc);
                    }
                }
            }
            return homes;
        });
    }

    public CompletableFuture<Void> setPlayerHomes(UUID uuid, Map<String, Location> homes) {
        return loadPlayerData(uuid).thenCompose(data -> {
            data.homes = new HashMap<>();
            for (Map.Entry<String, Location> entry : homes.entrySet()) {
                data.homes.put(entry.getKey(), new SerializableLocation(entry.getValue()));
            }
            return savePlayerData(uuid, data);
        });
    }

    public void saveAllPlayerData(Player player) {
        final UUID uuid = player.getUniqueId();
        loadPlayerData(uuid).thenAccept(data -> {
            savePlayerData(uuid, data).thenRun(() -> uncachePlayerData(uuid));
        }).exceptionally(ex -> {
            String msg = plugin.getLanguageManager().getMessage("database.player-save-error").replace("%uuid%",
                    uuid.toString());
            plugin.getLogger().log(Level.SEVERE, msg, ex);
            return null;
        });
    }

    public void getOfflineEnderChest(OfflinePlayer player, Consumer<Inventory> callback) {
        loadPlayerData(player.getUniqueId()).thenAccept(data -> {
            String title = TextParser.colorize(plugin.getLanguageManager().getMessage("enderchest.inventory-title")
                    .replace("%player%", player.getName()));
            Inventory offlineEnderChest = Bukkit.createInventory(null, 27, title);
            if (data.enderChestContents != null) {
                offlineEnderChest.setContents(data.enderChestContents);
            }
            FoliaScheduler.runTask(plugin, () -> callback.accept(offlineEnderChest));
        });
    }

    public void saveOfflineEnderChest(OfflinePlayer offlinePlayer, Inventory inventory) {
        loadPlayerData(offlinePlayer.getUniqueId()).thenAccept(data -> {
            data.enderChestContents = inventory.getContents();
            savePlayerData(offlinePlayer.getUniqueId(), data);
        });
    }

    public void saveOfflinePlayerInventory(OfflinePlayer offlinePlayer, Inventory virtualInv) {
        loadPlayerData(offlinePlayer.getUniqueId()).thenAccept(data -> {
            ItemStack[] mainContents = new ItemStack[36];
            for (int i = 0; i < 36; i++)
                mainContents[i] = virtualInv.getItem(i);

            ItemStack[] armorContents = new ItemStack[4];
            armorContents[3] = virtualInv.getItem(45);
            armorContents[2] = virtualInv.getItem(46);
            armorContents[1] = virtualInv.getItem(47);
            armorContents[0] = virtualInv.getItem(48);

            data.inventoryStorage = mainContents;
            data.inventoryArmor = armorContents;
            data.offhandItem = virtualInv.getItem(53);

            savePlayerData(offlinePlayer.getUniqueId(), data);
        });
    }

    public void loadPlayerInventory(Player player) {
        loadPlayerData(player.getUniqueId()).thenAccept(data -> {
            FoliaScheduler.runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.getInventory().clear();
                    if (data.inventoryStorage != null)
                        player.getInventory().setStorageContents(data.inventoryStorage);
                    if (data.inventoryArmor != null)
                        player.getInventory().setArmorContents(data.inventoryArmor);
                    if (data.offhandItem != null)
                        player.getInventory().setItemInOffHand(data.offhandItem);
                }
            });
        });
    }

    public void getOfflinePlayerInventory(OfflinePlayer offlinePlayer, Consumer<Inventory> callback) {
        loadPlayerData(offlinePlayer.getUniqueId()).thenAccept(data -> {
            String title = TextParser.colorize(plugin.getLanguageManager().getMessage("invsee.inventory-title")
                    .replace("%player%", offlinePlayer.getName()));
            Inventory virtualInv = Bukkit.createInventory(null, 54, title);
            if (data.inventoryStorage != null) {
                for (int i = 0; i < 36 && i < data.inventoryStorage.length; i++) {
                    virtualInv.setItem(i, data.inventoryStorage[i]);
                }
            }
            if (data.inventoryArmor != null && data.inventoryArmor.length == 4) {
                virtualInv.setItem(45, data.inventoryArmor[3]);
                virtualInv.setItem(46, data.inventoryArmor[2]);
                virtualInv.setItem(47, data.inventoryArmor[1]);
                virtualInv.setItem(48, data.inventoryArmor[0]);
            }
            virtualInv.setItem(53, data.offhandItem);

            fillPlaceholders(virtualInv);
            FoliaScheduler.runTask(plugin, () -> callback.accept(virtualInv));
        });
    }

    public static void fillPlaceholders(Inventory inv) {
        ItemStack placeholder = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = placeholder.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            placeholder.setItemMeta(meta);
        }
        for (int i = 36; i < 45; i++) {
            if (inv.getItem(i) == null)
                inv.setItem(i, placeholder);
        }
        for (int i = 49; i < 53; i++) {
            if (inv.getItem(i) == null)
                inv.setItem(i, placeholder);
        }
    }

    private boolean isInventoryEmpty(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        for (ItemStack item : player.getEnderChest().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    public void saveBackup(Player player, InventoryBackup.BackupReason reason) {
        
        if (isInventoryEmpty(player)) {
            return;
        }

        final ItemStack[] inventoryContents = player.getInventory().getContents().clone();
        final ItemStack[] enderChestContents = player.getEnderChest().getContents().clone();
        final int totalExperience = player.getTotalExperience();
        final int level = player.getLevel();
        final Location deathLocation = reason == InventoryBackup.BackupReason.DEATH ? player.getLocation() : null;
        final UUID playerUuid = player.getUniqueId();

        CompletableFuture.runAsync(() -> {
            try {
                File playerBackupDir = new File(databaseDir,
                        "backups/" + playerUuid.toString() + "/" + reason.name());
                if (!playerBackupDir.exists() && !playerBackupDir.mkdirs()) {
                    plugin.getLogger().severe("Backup klasörü oluşturulamadı: " + playerBackupDir.getAbsolutePath());
                    return;
                }

                long timestamp = System.currentTimeMillis();
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH-mm-ss_dd-MM-yyyy");
                String fileName = timeFormat.format(new Date(timestamp)) + ".dat";
                File backupFile = new File(playerBackupDir, fileName);

                BackupData backupData = new BackupData();
                backupData.timestamp = timestamp;
                backupData.reason = reason.name();
                backupData.inventoryContents = inventoryContents;
                backupData.enderChestContents = enderChestContents;
                backupData.totalExperience = totalExperience;
                backupData.level = level;
                if (deathLocation != null) {
                    backupData.deathWorld = deathLocation.getWorld().getName();
                    backupData.deathX = deathLocation.getBlockX();
                    backupData.deathY = deathLocation.getBlockY();
                    backupData.deathZ = deathLocation.getBlockZ();
                }
                saveBackupData(backupFile, backupData).join();
            } catch (Exception e) {
                String msg = plugin.getLanguageManager().getMessage("database.backup-save-error").replace("%player%",
                        playerUuid.toString());
                plugin.getLogger().log(Level.SEVERE, msg, e);
            }
        });
    }

    public void getBackupsAsync(OfflinePlayer player, InventoryBackup.BackupReason reason,
            Consumer<List<InventoryBackup>> callback) {
        CompletableFuture.runAsync(() -> {
            File playerBackupDir = new File(databaseDir,
                    "backups/" + player.getUniqueId().toString() + "/" + reason.name());
            if (!playerBackupDir.exists() || !playerBackupDir.isDirectory()) {
                FoliaScheduler.runTask(plugin, () -> callback.accept(Collections.emptyList()));
                return;
            }
            File[] backupFiles = playerBackupDir.listFiles((dir, name) -> name.endsWith(".dat"));
            if (backupFiles == null) {
                FoliaScheduler.runTask(plugin, () -> callback.accept(Collections.emptyList()));
                return;
            }
            List<InventoryBackup> backups = new ArrayList<>();
            for (File file : backupFiles) {
                try {
                    InventoryBackup backup = loadBackupFromFile(file).get();
                    if (backup != null) {
                        backups.add(backup);
                    }
                } catch (Exception e) {
                    String msg = plugin.getLanguageManager().getMessage("database.backup-read-error").replace("%file%",
                            file.getName());
                    plugin.getLogger().log(Level.WARNING, msg, e);
                }
            }
            backups.sort(Comparator.comparingLong(InventoryBackup::getTimestamp).reversed());
            FoliaScheduler.runTask(plugin, () -> callback.accept(backups));
        });
    }

    private CompletableFuture<Void> saveBackupData(File file, BackupData data) {
        data.inventoryContentsData = robustSerializeItemStackArray(data.inventoryContents);
        data.enderChestContentsData = robustSerializeItemStackArray(data.enderChestContents);
        return saveObjectToFile(file, data);
    }

    private CompletableFuture<InventoryBackup> loadBackupFromFile(File file) {
        return loadObjectFromFile(file, Object.class).thenApply(obj -> {
            if (obj == null)
                return null;

            if (obj instanceof BackupData) {
                BackupData data = (BackupData) obj;
                return new InventoryBackup(
                        deserializeItemStackArray(data.inventoryContentsData),
                        deserializeItemStackArray(data.enderChestContentsData),
                        data.timestamp,
                        InventoryBackup.BackupReason.valueOf(data.reason),
                        data.totalExperience,
                        data.level,
                        data.deathWorld,
                        data.deathX,
                        data.deathY,
                        data.deathZ);
            } else if (obj instanceof OldBackupData) {
                OldBackupData oldData = (OldBackupData) obj;
                return new InventoryBackup(
                        deserializeItemStackArray(oldData.inventoryContentsData),
                        deserializeItemStackArray(oldData.enderChestContentsData),
                        oldData.timestamp,
                        InventoryBackup.BackupReason.valueOf(oldData.reason),
                        oldData.totalExperience,
                        oldData.level);
            }
            return null;
        });
    }

    private String robustSerializeItemStack(ItemStack item) {
        if (item == null) {
            return null;
        }
        try {
            return ItemStackSerializer.toBase64(new ItemStack[] { item });
        } catch (Exception e) {
            String displayName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName()
                    : "N/A";
            String msg = plugin.getLanguageManager().getMessage("database.serialization-warning")
                    .replace("%type%", item.getType().toString())
                    .replace("%name%", displayName);
            plugin.getLogger().warning(msg);
            return null;
        }
    }

    private String robustSerializeItemStackArray(ItemStack[] items) {
        if (items == null) {
            return null;
        }
        try {
            return ItemStackSerializer.toBase64(items);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    plugin.getLanguageManager().getMessage("database.serialization-critical"), e);
            return null;
        }
    }

    private ItemStack[] deserializeItemStackArray(String data) {
        if (data == null || data.isEmpty())
            return new ItemStack[0];
        try {
            return ItemStackSerializer.fromBase64(data);
        } catch (Exception e) {
            String msg = plugin.getLanguageManager().getMessage("database.deserialization-array-error")
                    .replace("%error%", e.getMessage());
            plugin.getLogger().severe(msg);
            return new ItemStack[0];
        }
    }

    private ItemStack deserializeItemStack(String data) {
        if (data == null || data.isEmpty())
            return null;
        try {
            ItemStack[] items = ItemStackSerializer.fromBase64(data);
            return items.length > 0 ? items[0] : null;
        } catch (Exception e) {
            String msg = plugin.getLanguageManager().getMessage("database.deserialization-item-error")
                    .replace("%error%", e.getMessage());
            plugin.getLogger().severe(msg);
            return null;
        }
    }

    public static class PunishmentEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        public long expiry;
        public String reason;
        public UUID punisherUUID;
        public long timestamp;

        public PunishmentEntry(long expiry, String reason, UUID punisherUUID, long timestamp) {
            this.expiry = expiry;
            this.reason = reason;
            this.punisherUUID = punisherUUID;
            this.timestamp = timestamp;
        }
    }

    public static class JailEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        public long expiry;
        public String reason;
        public UUID punisherUUID;
        public long timestamp;
        public String jailName;
        public SerializableLocation returnLocation;

        public JailEntry(long expiry, String reason, UUID punisherUUID, long timestamp, String jailName,
                Location returnLoc) {
            this.expiry = expiry;
            this.reason = reason;
            this.punisherUUID = punisherUUID;
            this.timestamp = timestamp;
            this.jailName = jailName;
            this.returnLocation = new SerializableLocation(returnLoc);
        }
    }

    public static class PunishmentData implements Serializable {
        private static final long serialVersionUID = 4L;
        public Map<UUID, PunishmentEntry> bans = new ConcurrentHashMap<>();
        public Map<UUID, PunishmentEntry> mutes = new ConcurrentHashMap<>();
        public Map<UUID, PunishmentEntry> freezes = new ConcurrentHashMap<>();
        public Map<UUID, JailEntry> jails = new ConcurrentHashMap<>();
    }

    private static class PunishmentData_V3 implements Serializable {
        private static final long serialVersionUID = 3L;
        public Map<UUID, PunishmentEntry> bans = new ConcurrentHashMap<>();
        public Map<UUID, PunishmentEntry> mutes = new ConcurrentHashMap<>();
        public Map<UUID, PunishmentEntry> freezes = new ConcurrentHashMap<>();
    }

    private static class PunishmentData_V2 implements Serializable {
        private static final long serialVersionUID = 2L;
        public Map<UUID, PunishmentEntry> bans = new ConcurrentHashMap<>();
        public Map<UUID, PunishmentEntry> mutes = new ConcurrentHashMap<>();
    }

    private static class PunishmentData_Old implements Serializable {
        private static final long serialVersionUID = 1L;
        public Map<UUID, Long> bans = new ConcurrentHashMap<>();
        public Map<UUID, Long> mutes = new ConcurrentHashMap<>();
    }

    public static class BackData implements Serializable {
        private static final long serialVersionUID = 1L;
        public Map<UUID, SerializableLocation> locations = new ConcurrentHashMap<>();
    }

    protected static class PlayerData implements Serializable {
        private static final long serialVersionUID = 3L;
        String inventoryStorageData;
        String inventoryArmorData;
        String offhandItemData;
        String enderChestContentsData;
        Map<String, SerializableLocation> homes = new HashMap<>();
        transient ItemStack[] inventoryStorage;
        transient ItemStack[] inventoryArmor;
        transient ItemStack offhandItem;
        transient ItemStack[] enderChestContents;
    }

    public static class TempFlyEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        public long remainingSeconds;
        public long lastUpdateTimestamp;

        public TempFlyEntry(long remainingSeconds, long lastUpdateTimestamp) {
            this.remainingSeconds = remainingSeconds;
            this.lastUpdateTimestamp = lastUpdateTimestamp;
        }
    }

    public static class TempFlyData implements Serializable {
        private static final long serialVersionUID = 1L;
        public Map<UUID, TempFlyEntry> players = new ConcurrentHashMap<>();
    }

    private static class PlayerData_V2 implements Serializable {
        private static final long serialVersionUID = 2L;
        String inventoryStorageData;
        String inventoryArmorData;
        String offhandItemData;
        String enderChestContentsData;
        transient ItemStack[] inventoryStorage;
        transient ItemStack[] inventoryArmor;
        transient ItemStack offhandItem;
        transient ItemStack[] enderChestContents;
    }

    private static class BackupData implements Serializable {
        private static final long serialVersionUID = 3L;
        long timestamp;
        String reason;
        String inventoryContentsData;
        String enderChestContentsData;
        int totalExperience;
        int level;
        String deathWorld;
        int deathX, deathY, deathZ;
        transient ItemStack[] inventoryContents;
        transient ItemStack[] enderChestContents;
    }

    private static class OldBackupData implements Serializable {
        private static final long serialVersionUID = 2L;
        long timestamp;
        String reason;
        String inventoryContentsData;
        String enderChestContentsData;
        int totalExperience;
        int level;
    }

    private File getEconomyFile() {
        return new File(databaseDir, "economy.dat");
    }

    public CompletableFuture<Void> saveEconomy(Map<UUID, Double> balances) {
        return saveObjectToFile(getEconomyFile(), new HashMap<>(balances));
    }

    public void loadEconomy(Consumer<Map<UUID, Double>> callback) {
        loadObjectFromFile(getEconomyFile(), HashMap.class).thenAccept(data -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (data != null) {
                    callback.accept((Map<UUID, Double>) data);
                } else {
                    callback.accept(new HashMap<>());
                }
            });
        });
    }
}