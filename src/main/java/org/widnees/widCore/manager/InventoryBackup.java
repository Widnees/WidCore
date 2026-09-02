package org.widnees.widCore.manager;

import org.bukkit.inventory.ItemStack;

public class InventoryBackup {
    private final ItemStack[] inventoryContents;
    private final ItemStack[] enderChestContents;
    private final long timestamp;
    private final BackupReason reason;
    private final int totalExperience;
    private final int level;
    private final String deathWorld;
    private final int deathX;
    private final int deathY;
    private final int deathZ;
    /** Bukkit death message string (e.g. "Steve was slain by Zombie"). Null for non-DEATH backups. */
    private final String deathCause;
    /** SQLite row id for lazy content loading; -1 if unknown/legacy. */
    private final long storageId;
    private final boolean contentsLoaded;

    /** Convenience constructor — no deathCause. */
    public InventoryBackup(ItemStack[] inventoryContents, ItemStack[] enderChestContents, long timestamp,
            BackupReason reason, int totalExperience, int level, String deathWorld, int deathX, int deathY,
            int deathZ) {
        this(inventoryContents, enderChestContents, timestamp, reason, totalExperience, level, deathWorld, deathX,
                deathY, deathZ, null);
    }

    /** Convenience constructor — no death info. */
    public InventoryBackup(ItemStack[] inventoryContents, ItemStack[] enderChestContents, long timestamp,
            BackupReason reason, int totalExperience, int level) {
        this(inventoryContents, enderChestContents, timestamp, reason, totalExperience, level, null, 0, 0, 0, null);
    }

    /** Full new-backup constructor with deathCause (storageId assigned by DB on insert). */
    public InventoryBackup(ItemStack[] inventoryContents, ItemStack[] enderChestContents, long timestamp,
            BackupReason reason, int totalExperience, int level, String deathWorld, int deathX, int deathY,
            int deathZ, String deathCause) {
        this(inventoryContents, enderChestContents, timestamp, reason, totalExperience, level, deathWorld, deathX,
                deathY, deathZ, deathCause, -1L, true);
    }

    /** Backward-compat bridge: storageId-aware but no deathCause. */
    public InventoryBackup(ItemStack[] inventoryContents, ItemStack[] enderChestContents, long timestamp,
            BackupReason reason, int totalExperience, int level, String deathWorld, int deathX, int deathY,
            int deathZ, long storageId, boolean contentsLoaded) {
        this(inventoryContents, enderChestContents, timestamp, reason, totalExperience, level, deathWorld, deathX,
                deathY, deathZ, null, storageId, contentsLoaded);
    }

    /** Canonical constructor. */
    public InventoryBackup(ItemStack[] inventoryContents, ItemStack[] enderChestContents, long timestamp,
            BackupReason reason, int totalExperience, int level, String deathWorld, int deathX, int deathY,
            int deathZ, String deathCause, long storageId, boolean contentsLoaded) {
        this.inventoryContents = inventoryContents;
        this.enderChestContents = enderChestContents;
        this.timestamp = timestamp;
        this.reason = reason;
        this.totalExperience = totalExperience;
        this.level = level;
        this.deathWorld = deathWorld;
        this.deathX = deathX;
        this.deathY = deathY;
        this.deathZ = deathZ;
        this.deathCause = deathCause;
        this.storageId = storageId;
        this.contentsLoaded = contentsLoaded;
    }

    /** Metadata-only backup for list menus (no inventory deserialize). */
    public static InventoryBackup metadata(long storageId, long timestamp, BackupReason reason, int totalExperience,
            int level, String deathWorld, int deathX, int deathY, int deathZ, String deathCause) {
        return new InventoryBackup(new ItemStack[0], new ItemStack[0], timestamp, reason, totalExperience, level,
                deathWorld, deathX, deathY, deathZ, deathCause, storageId, false);
    }

    public InventoryBackup withContents(ItemStack[] inventoryContents, ItemStack[] enderChestContents) {
        return new InventoryBackup(inventoryContents, enderChestContents, timestamp, reason, totalExperience, level,
                deathWorld, deathX, deathY, deathZ, deathCause, storageId, true);
    }

    public ItemStack[] getInventoryContents() {
        return this.inventoryContents;
    }

    public ItemStack[] getEnderChestContents() {
        return this.enderChestContents;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public BackupReason getReason() {
        return this.reason;
    }

    public int getTotalExperience() {
        return this.totalExperience;
    }

    public int getLevel() {
        return this.level;
    }

    public String getDeathWorld() {
        return this.deathWorld;
    }

    public int getDeathX() {
        return this.deathX;
    }

    public int getDeathY() {
        return this.deathY;
    }

    public int getDeathZ() {
        return this.deathZ;
    }

    /** Returns the Bukkit death message (e.g. "Steve was slain by Zombie"), or null if not recorded. */
    public String getDeathCause() {
        return this.deathCause;
    }

    public long getStorageId() {
        return this.storageId;
    }

    public boolean isContentsLoaded() {
        return this.contentsLoaded;
    }

    public static enum BackupReason {
        DEATH("menu.type-death"),
        JOIN("menu.type-join"),
        QUIT("menu.type-quit"),
        PERIODIC("menu.type-periodic"),
        WORLD_CHANGE("menu.type-world");

        private final String langKey;

        private BackupReason(String langKey) {
            this.langKey = langKey;
        }

        public String getLangKey() {
            return this.langKey;
        }
    }
}
