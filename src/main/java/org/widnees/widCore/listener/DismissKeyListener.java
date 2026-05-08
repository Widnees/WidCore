package org.widnees.widCore.listener;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.DismissMenuManager;

public class DismissKeyListener implements Listener {
    
    private final Main plugin;
    private final DismissMenuManager dismissManager;
    
    public DismissKeyListener(Main plugin, DismissMenuManager dismissManager) {
        this.plugin = plugin;
        this.dismissManager = dismissManager;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        
        if (dismissManager.shouldDismiss(player, "Q")) {
            FileConfiguration config = plugin.getConfigManager().getModuleConfig("joinleave");
            dismissManager.openMenu(player, config, "join.dismiss-menu");
            
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        
        if (dismissManager.shouldDismiss(player, "F")) {
            FileConfiguration config = plugin.getConfigManager().getModuleConfig("joinleave");
            dismissManager.openMenu(player, config, "join.dismiss-menu");
            
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        
        if (!event.isSneaking()) {
            return;
        }
        
        if (dismissManager.shouldDismiss(player, "SHIFT")) {
            FileConfiguration config = plugin.getConfigManager().getModuleConfig("joinleave");
            dismissManager.openMenu(player, config, "join.dismiss-menu");
            
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        if (dismissManager.isDismissMenu(player, event.getInventory())) {
            event.setCancelled(true);
        }
        
        if (event.getClickedInventory() != null && 
            dismissManager.isDismissMenu(player, event.getClickedInventory())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        if (dismissManager.isDismissMenu(player, event.getInventory())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        
        if (dismissManager.isDismissMenu(player, event.getInventory())) {
            dismissManager.dismissMenu(player);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        
        dismissManager.cleanup(event.getPlayer().getUniqueId());
    }
}