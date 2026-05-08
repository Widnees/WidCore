package org.widnees.widCore.listener;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.widnees.widCore.Main;
import org.widnees.widCore.database.BinaryDataManager;
import org.widnees.widCore.manager.InventoryBackup;
import org.widnees.widCore.manager.MenuManager;
import org.widnees.widCore.util.FoliaScheduler;

import java.util.List;
import java.util.UUID;

public class InventoryRollbackListener implements Listener {

    private final Main plugin;
    private final BinaryDataManager dataManager;
    private final MenuManager menuManager;
    private final FileConfiguration moduleConfig;

    public InventoryRollbackListener(Main plugin, BinaryDataManager dataManager, MenuManager menuManager,
            FileConfiguration moduleConfig) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.menuManager = menuManager;
        this.moduleConfig = moduleConfig;
        startPeriodicBackupTask();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (moduleConfig.getBoolean("save-on-join", false)) {
            dataManager.saveBackup(event.getPlayer(), InventoryBackup.BackupReason.JOIN);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (moduleConfig.getBoolean("save-on-death", false)) {
            dataManager.saveBackup(event.getEntity(), InventoryBackup.BackupReason.DEATH);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (moduleConfig.getBoolean("save-on-quit", false)) {
            dataManager.saveBackup(event.getPlayer(), InventoryBackup.BackupReason.QUIT);
        }
        menuManager.cleanup(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (moduleConfig.getBoolean("save-on-world-change", true)) {
            dataManager.saveBackup(event.getPlayer(), InventoryBackup.BackupReason.WORLD_CHANGE);
        }
    }

    private void startPeriodicBackupTask() {
        int minutes = moduleConfig.getInt("periodic-save-minutes", 0);
        if (minutes <= 0)
            return;

        long ticks = minutes * 60 * 20L;

        FoliaScheduler.runTaskTimerAsync(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                dataManager.saveBackup(player, InventoryBackup.BackupReason.PERIODIC);
            }
        }, ticks, ticks);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player viewer = (Player) event.getWhoClicked();
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        String typeSelectTitle = plugin.getLanguageManager().getMessage("menu.type-select-title");
        String backupsTitle = plugin.getLanguageManager().getMessage("menu.backups-title");
        String invBackupTitle = plugin.getLanguageManager().getMessage("menu.inv-backup-title");
        String ecBackupTitle = plugin.getLanguageManager().getMessage("menu.ec-backup-title");

        boolean isOurMenu = title.startsWith(typeSelectTitle) ||
                title.startsWith(backupsTitle) ||
                title.equals(invBackupTitle) ||
                title.equals(ecBackupTitle);

        if (!isOurMenu) {
            return;
        }

        event.setCancelled(true);

        if (event.getSlot() < 0)
            return;
        Inventory clickedInventory = event.getClickedInventory();

        if (clickedInventory == null || !clickedInventory.equals(event.getView().getTopInventory())) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR || clickedItem.getItemMeta() == null) {
            return;
        }

        if (title.startsWith(typeSelectTitle)) {
            handleBackupTypeMenuClick(viewer, clickedItem);
        } else if (title.startsWith(backupsTitle)) {
            handleBackupListMenuClick(viewer, clickedItem, event.getSlot());
        } else if (title.equals(invBackupTitle) || title.equals(ecBackupTitle)) {
            handleBackupPreviewMenuClick(viewer, clickedItem, title);
        }
    }

    private void handleBackupTypeMenuClick(Player viewer, ItemStack clickedItem) {
        UUID viewerId = viewer.getUniqueId();
        OfflinePlayer target = menuManager.getViewingTarget(viewerId);
        if (target == null)
            return;

        InventoryBackup.BackupReason reason = null;

        switch (clickedItem.getType()) {
            case SKELETON_SKULL: reason = InventoryBackup.BackupReason.DEATH; break;
            case OAK_DOOR: reason = InventoryBackup.BackupReason.JOIN; break;
            case IRON_DOOR: reason = InventoryBackup.BackupReason.QUIT; break;
            case GRASS_BLOCK: reason = InventoryBackup.BackupReason.WORLD_CHANGE; break;
            case CLOCK: reason = InventoryBackup.BackupReason.PERIODIC; break;
            default: break;
        }

        if (reason != null) {
            menuManager.openBackupListMenu(viewer, target, reason, 1);
        }
    }

    private void handleBackupListMenuClick(Player viewer, ItemStack clickedItem, int slot) {
        UUID viewerId = viewer.getUniqueId();
        OfflinePlayer target = menuManager.getViewingTarget(viewerId);
        InventoryBackup.BackupReason reason = menuManager.getViewingReason(viewerId);
        int page = menuManager.getCurrentPage(viewerId);

        if (target == null || reason == null)
            return;

        if (clickedItem.getType() == Material.ARROW) {
            if (slot == 45) {
                menuManager.openBackupListMenu(viewer, target, reason, page - 1);
            } else {
                menuManager.openBackupListMenu(viewer, target, reason, page + 1);
            }
        } else if (clickedItem.getType() == Material.BARRIER) {
            menuManager.openBackupTypeMenu(viewer, target);
        } else if (clickedItem.getType() == Material.CHEST) {
            int index = (page - 1) * 45 + slot;
            List<InventoryBackup> backups = menuManager.getLoadedBackups(viewerId);
            if (backups != null && index < backups.size()) {
                menuManager.openBackupPreviewMenu(viewer, backups.get(index), false);
            }
        }
    }

    private void handleBackupPreviewMenuClick(Player viewer, ItemStack clickedItem, String title) {
        UUID viewerId = viewer.getUniqueId();
        OfflinePlayer target = menuManager.getViewingTarget(viewerId);
        InventoryBackup backup = menuManager.getViewingBackup(viewerId);
        if (target == null || backup == null)
            return;

        String itemName = PlainTextComponentSerializer.plainText()
                .serialize(clickedItem.getItemMeta().displayName());

        if (clickedItem.getType() == Material.LIME_DYE) {
            handleRestoreAction(viewer, target, backup, title);
        } else if (clickedItem.getType() == Material.RED_DYE) {
            menuManager.openBackupListMenu(viewer, target, backup.getReason(),
                    menuManager.getCurrentPage(viewerId));
        } else if (clickedItem.getType() == Material.CRAFTING_TABLE) {
            menuManager.openBackupPreviewMenu(viewer, backup, false);
        } else if (clickedItem.getType() == Material.ENDER_CHEST) {
            menuManager.openBackupPreviewMenu(viewer, backup, true);
        }
    }

    private void handleRestoreAction(Player viewer, OfflinePlayer target, InventoryBackup backup, String title) {
        Player targetPlayer = target.getPlayer();
        if (targetPlayer != null && targetPlayer.isOnline()) {
            if (title.equals(plugin.getLanguageManager().getMessage("menu.inv-backup-title"))) {
                targetPlayer.getInventory().setContents(backup.getInventoryContents());
                targetPlayer.setTotalExperience(0);
                targetPlayer.setLevel(0);
                targetPlayer.giveExp(backup.getTotalExperience());
                Main.sendMessage(this.plugin, viewer,
                        plugin.getLanguageManager().getMessage("menu.success-inv")
                                .replace("%player%", target.getName()));
                Main.sendMessage(plugin, targetPlayer, plugin.getLanguageManager().getMessage("menu.target-msg-inv"));
            } else {
                targetPlayer.getEnderChest().setContents(backup.getEnderChestContents());
                Main.sendMessage(this.plugin, viewer,
                        plugin.getLanguageManager().getMessage("menu.success-ec")
                                .replace("%player%", target.getName()));
                Main.sendMessage(plugin, targetPlayer, plugin.getLanguageManager().getMessage("menu.target-msg-ec"));
            }
            viewer.closeInventory();
        } else {
            Main.sendMessage(this.plugin, viewer, plugin.getLanguageManager().getMessage("menu.online-only"));
        }
    }
}