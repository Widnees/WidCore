package org.widnees.widCore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.DismissMenuManager;

public class WelcomeMenuListener implements Listener {
    
    private final Main plugin;
    private final DismissMenuManager menuManager;
    
    public WelcomeMenuListener(Main plugin, DismissMenuManager menuManager) {
        this.plugin = plugin;
        this.menuManager = menuManager;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        if (menuManager.isDismissMenu(player, event.getInventory())) {
            event.setCancelled(true);
        }
        
        if (event.getClickedInventory() != null && 
            menuManager.isDismissMenu(player, event.getClickedInventory())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        if (menuManager.isDismissMenu(player, event.getInventory())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        
        if (menuManager.isDismissMenu(player, event.getInventory())) {
            menuManager.dismissMenu(player);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        
        menuManager.cleanup(event.getPlayer().getUniqueId());
    }
}