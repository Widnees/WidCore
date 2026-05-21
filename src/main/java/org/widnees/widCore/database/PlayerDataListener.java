package org.widnees.widCore.database;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.widnees.widCore.Main;

import java.util.UUID;
import java.util.logging.Level;

public class PlayerDataListener implements Listener {

    private final Main plugin;

    public PlayerDataListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getDataManager().loadPlayerData(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        try {

            BinaryDataManager.PlayerData cached = plugin.getDataManager().getCachedPlayerData(player.getUniqueId());
            if (cached != null) {
                cached.inventoryStorage = player.getInventory().getStorageContents().clone();
                cached.inventoryArmor = player.getInventory().getArmorContents().clone();
                ItemStack offhand = player.getInventory().getItemInOffHand();
                cached.offhandItem = offhand != null && offhand.getType() != Material.AIR ? offhand.clone() : null;
                cached.enderChestContents = player.getEnderChest().getContents().clone();
            }
            plugin.getDataManager().saveAllPlayerData(player);
        } catch (Exception e) {
            String msg = plugin.getLanguageManager().getMessage("database.player-data-save-error").replace("%player%",
                    player.getName());
            plugin.getLogger().log(Level.SEVERE, msg, e);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;

        Player player = (Player) event.getPlayer();
        UUID viewerId = player.getUniqueId();

        if (plugin.getOpenOfflineInventories().containsKey(viewerId)) {
            UUID targetId = plugin.getOpenOfflineInventories().remove(viewerId);
            OfflinePlayer targetOfflinePlayer = Bukkit.getOfflinePlayer(targetId);

            plugin.getDataManager().saveOfflineEnderChest(targetOfflinePlayer, event.getInventory());
        }
    }
}