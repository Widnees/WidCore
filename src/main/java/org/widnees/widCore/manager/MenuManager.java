package org.widnees.widCore.manager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.widnees.widCore.Main;
import org.widnees.widCore.database.BinaryDataManager;
import org.widnees.widCore.manager.InventoryBackup;

public class MenuManager {
    private final Main plugin;
    private final BinaryDataManager dataManager;
    private final FileConfiguration moduleConfig;
    private final Map<UUID, OfflinePlayer> viewingTarget = new HashMap<UUID, OfflinePlayer>();
    private final Map<UUID, Integer> currentPage = new HashMap<UUID, Integer>();
    private final Map<UUID, InventoryBackup.BackupReason> viewingReason = new HashMap<UUID, InventoryBackup.BackupReason>();
    private final Map<UUID, InventoryBackup> viewingBackup = new HashMap<UUID, InventoryBackup>();
    private final Map<UUID, List<InventoryBackup>> loadedBackups = new HashMap<UUID, List<InventoryBackup>>();

    public MenuManager(Main plugin, BinaryDataManager dataManager, FileConfiguration moduleConfig) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.moduleConfig = moduleConfig;
    }

    public void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        this.viewingTarget.remove(uuid);
        this.currentPage.remove(uuid);
        this.viewingReason.remove(uuid);
        this.viewingBackup.remove(uuid);
        this.loadedBackups.remove(uuid);
    }

    public OfflinePlayer getViewingTarget(UUID viewerId) {
        return this.viewingTarget.get(viewerId);
    }

    public InventoryBackup.BackupReason getViewingReason(UUID viewerId) {
        return this.viewingReason.get(viewerId);
    }

    public int getCurrentPage(UUID viewerId) {
        return this.currentPage.getOrDefault(viewerId, 1);
    }

    public List<InventoryBackup> getLoadedBackups(UUID viewerId) {
        return this.loadedBackups.get(viewerId);
    }

    public InventoryBackup getViewingBackup(UUID viewerId) {
        return this.viewingBackup.get(viewerId);
    }

    public void openBackupTypeMenu(Player viewer, OfflinePlayer target) {
        Inventory menu = Bukkit.createInventory(null, (int)27, (String)(this.plugin.getLanguageManager().getMessage("menu.type-select-title") + target.getName()));
        int slot = 11;
        if (this.moduleConfig.getBoolean("save-on-death")) {
            menu.setItem(slot++, this.createMenuItem(Material.SKELETON_SKULL, this.plugin.getLanguageManager().getMessage("menu.type-death"), null));
        }
        if (this.moduleConfig.getBoolean("save-on-join")) {
            menu.setItem(slot++, this.createMenuItem(Material.OAK_DOOR, this.plugin.getLanguageManager().getMessage("menu.type-join"), null));
        }
        if (this.moduleConfig.getBoolean("save-on-quit")) {
            menu.setItem(slot++, this.createMenuItem(Material.IRON_DOOR, this.plugin.getLanguageManager().getMessage("menu.type-quit"), null));
        }
        if (this.moduleConfig.getBoolean("save-on-world-change", true)) {
            menu.setItem(slot++, this.createMenuItem(Material.GRASS_BLOCK, this.plugin.getLanguageManager().getMessage("menu.type-world"), null));
        }
        if (this.moduleConfig.getInt("periodic-save-minutes", 0) > 0) {
            menu.setItem(slot, this.createMenuItem(Material.CLOCK, this.plugin.getLanguageManager().getMessage("menu.type-periodic"), null));
        }
        this.viewingTarget.put(viewer.getUniqueId(), target);
        viewer.openInventory(menu);
    }

    public void openBackupListMenu(Player viewer, OfflinePlayer target, InventoryBackup.BackupReason reason, int page) {
        Main.sendMessage(this.plugin, (CommandSender)viewer, this.plugin.getLanguageManager().getMessage("menu.loading"));
        this.dataManager.getBackupsAsync(target, reason, backupList -> {
            this.loadedBackups.put(viewer.getUniqueId(), (List<InventoryBackup>)backupList);
            int totalPages = (int)Math.ceil((double)backupList.size() / 45.0);
            if (totalPages == 0) {
                totalPages = 1;
            }
            Inventory menu = Bukkit.createInventory(null, (int)54, (String)(this.plugin.getLanguageManager().getMessage("menu.backups-title") + reason.name() + ") - " + page + "/" + totalPages));
            int startIndex = (page - 1) * 45;
            int i = 0;
            while (i < 45) {
                int listIndex = startIndex + i;
                if (listIndex >= backupList.size()) break;
                InventoryBackup backup = (InventoryBackup)backupList.get(listIndex);
                Date date = new Date(backup.getTimestamp());
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
                ArrayList<String> lore = new ArrayList<String>();
                lore.add(this.plugin.getLanguageManager().getMessage("menu.date-format").replace("%date%", dateFormat.format(date)));
                if (reason == InventoryBackup.BackupReason.DEATH && backup.getDeathWorld() != null) {
                    lore.add(this.plugin.getLanguageManager().getMessage("menu.location-format").replace("%world%", backup.getDeathWorld()).replace("%x%", String.valueOf(backup.getDeathX())).replace("%y%", String.valueOf(backup.getDeathY())).replace("%z%", String.valueOf(backup.getDeathZ())));
                    if (backup.getDeathCause() != null && !backup.getDeathCause().isEmpty()) {
                        lore.add(this.plugin.getLanguageManager().getMessage("menu.death-cause-format").replace("%cause%", backup.getDeathCause()));
                    }
                }
                ItemStack chestItem = this.createMenuItem(Material.CHEST, this.plugin.getLanguageManager().getMessage("menu.backup-format").replace("%date%", timeFormat.format(date)), lore);
                menu.setItem(i, chestItem);
                ++i;
            }
            if (page > 1) {
                menu.setItem(45, this.createMenuItem(Material.ARROW, this.plugin.getLanguageManager().getMessage("menu.prev"), null));
            }
            if (page < totalPages) {
                menu.setItem(53, this.createMenuItem(Material.ARROW, this.plugin.getLanguageManager().getMessage("menu.next"), null));
            }
            menu.setItem(49, this.createMenuItem(Material.BARRIER, this.plugin.getLanguageManager().getMessage("menu.cancel"), null));
            this.currentPage.put(viewer.getUniqueId(), page);
            this.viewingReason.put(viewer.getUniqueId(), reason);
            viewer.openInventory(menu);
        });
    }

    public void openBackupPreviewMenu(Player viewer, InventoryBackup backup, boolean isEnderChest) {
        // Lazy-load inventory blobs only when preview is opened (not for list menu).
        if (backup != null && !backup.isContentsLoaded()) {
            OfflinePlayer target = this.viewingTarget.get(viewer.getUniqueId());
            UUID targetUuid = target != null ? target.getUniqueId() : null;
            Main.sendMessage(this.plugin, (CommandSender)viewer, this.plugin.getLanguageManager().getMessage("menu.loading"));
            this.dataManager.loadBackupContentsAsync(targetUuid, backup, loaded -> {
                if (loaded == null) {
                    return;
                }
                // Keep list entry updated with hydrated backup if present.
                List<InventoryBackup> list = this.loadedBackups.get(viewer.getUniqueId());
                if (list != null) {
                    for (int i = 0; i < list.size(); i++) {
                        InventoryBackup entry = list.get(i);
                        if (entry.getStorageId() == loaded.getStorageId()
                                || (entry.getStorageId() < 0 && entry.getTimestamp() == loaded.getTimestamp())) {
                            list.set(i, loaded);
                            break;
                        }
                    }
                }
                openBackupPreviewMenuLoaded(viewer, loaded, isEnderChest);
            });
            return;
        }
        openBackupPreviewMenuLoaded(viewer, backup, isEnderChest);
    }


    private void openBackupPreviewMenuLoaded(Player viewer, InventoryBackup backup, boolean isEnderChest) {
        int menuSize = 54;
        String title = isEnderChest ? this.plugin.getLanguageManager().getMessage("menu.ec-backup-title") : this.plugin.getLanguageManager().getMessage("menu.inv-backup-title");
        Inventory menu = Bukkit.createInventory(null, (int)menuSize, (String)title);
        ItemStack[] contents = isEnderChest ? backup.getEnderChestContents() : backup.getInventoryContents();
        if (contents != null) {
            int i = 0;
            while (i < contents.length) {
                if (i < 45 && contents[i] != null) {
                    menu.setItem(i, contents[i]);
                }
                ++i;
            }
        }
        int bottomRowStart = menuSize - 9;
        ItemStack glass = this.createMenuItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        int i2 = bottomRowStart;
        while (i2 < menuSize) {
            menu.setItem(i2, glass);
            ++i2;
        }
        menu.setItem(bottomRowStart, this.createMenuItem(Material.LIME_DYE, this.plugin.getLanguageManager().getMessage("menu.apply"), Arrays.asList(this.plugin.getLanguageManager().getMessage("menu.apply-desc"))));
        menu.setItem(bottomRowStart + 1, this.createMenuItem(Material.EXPERIENCE_BOTTLE, this.plugin.getLanguageManager().getMessage("menu.experience-format").replace("%level%", String.valueOf(backup.getLevel())), Arrays.asList(this.plugin.getLanguageManager().getMessage("menu.experience-total").replace("%total%", String.valueOf(backup.getTotalExperience())))));
        menu.setItem(bottomRowStart + 4, this.createMenuItem(isEnderChest ? Material.CRAFTING_TABLE : Material.ENDER_CHEST, isEnderChest ? this.plugin.getLanguageManager().getMessage("menu.view-inv") : this.plugin.getLanguageManager().getMessage("menu.view-ec"), null));
        menu.setItem(bottomRowStart + 8, this.createMenuItem(Material.RED_DYE, this.plugin.getLanguageManager().getMessage("menu.cancel"), null));
        this.viewingBackup.put(viewer.getUniqueId(), backup);
        viewer.openInventory(menu);
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes((char)'&', (String)name));
            if (lore != null) {
                ArrayList<String> coloredLore = new ArrayList<String>();
                for (String line : lore) {
                    coloredLore.add(ChatColor.translateAlternateColorCodes((char)'&', (String)line));
                }
                meta.setLore(coloredLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
        @SuppressWarnings("unused")
    private static final String __Wx7c4e2 = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}
