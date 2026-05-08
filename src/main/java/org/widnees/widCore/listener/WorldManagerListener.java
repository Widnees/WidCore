package org.widnees.widCore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.WorldManagerGUI;
import org.widnees.widCore.util.FoliaScheduler;

public class WorldManagerListener implements Listener {

    private final Main plugin;
    private final WorldManagerGUI worldManagerGUI;

    public WorldManagerListener(Main plugin, WorldManagerGUI worldManagerGUI) {
        this.plugin = plugin;
        this.worldManagerGUI = worldManagerGUI;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        Player player = (Player) event.getWhoClicked();

        if (!worldManagerGUI.hasMenuOpen(player.getUniqueId()))
            return;

        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);

        int slot = event.getRawSlot();

        if (slot < 0 || slot >= 54)
            return;

        boolean leftClick = event.getClick() == ClickType.LEFT ||
                event.getClick() == ClickType.SHIFT_LEFT;
        boolean shiftClick = event.getClick() == ClickType.SHIFT_LEFT ||
                event.getClick() == ClickType.SHIFT_RIGHT;
        boolean middleClick = event.getClick() == ClickType.MIDDLE;

        worldManagerGUI.handleClick(player, slot, leftClick, shiftClick, middleClick);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;

        Player player = (Player) event.getPlayer();

        if (!worldManagerGUI.hasMenuOpen(player.getUniqueId()))
            return;

        FoliaScheduler.runTaskLater(plugin, () -> {
            
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getSize() != 54) {
                worldManagerGUI.handleClose(player);
            }
        }, 2L);
    }
}
