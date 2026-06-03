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

    public InventoryBackup(ItemStack[] inventoryContents, ItemStack[] enderChestContents, long timestamp, BackupReason reason, int totalExperience, int level, String deathWorld, int deathX, int deathY, int deathZ) {
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
    }

    public InventoryBackup(ItemStack[] inventoryContents, ItemStack[] enderChestContents, long timestamp, BackupReason reason, int totalExperience, int level) {
        this(inventoryContents, enderChestContents, timestamp, reason, totalExperience, level, null, 0, 0, 0);
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
        @SuppressWarnings("unused")
    private static final String _0xW7e1a9 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}
