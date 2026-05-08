package org.widnees.widCore.listener;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.BannedItemManager;

import java.util.Set;

public class BannedItemListener implements Listener {

    private final Main plugin;
    private final BannedItemManager manager;
    private final String bypassPermission = "widcore.banneditem.bypass";

    public BannedItemListener(Main plugin, BannedItemManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private void sendBanMessage(Player player) {
        Main.sendMessage(this.plugin, player, manager.getBanMessage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) return;

        ItemStack result = event.getRecipe().getResult();

        if (manager.isBanned(result)) {
            if (event.getView().getPlayer() instanceof Player player) {
                if (player.hasPermission(bypassPermission)) {
                    return;
                }
            }
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        if (player.hasPermission(bypassPermission)) return;

        Inventory clickedInv = event.getClickedInventory();
        InventoryAction action = event.getAction();

        if (event.isShiftClick() && clickedInv != null && !(clickedInv.getHolder() instanceof Player) && clickedInv.getType() != InventoryType.CREATIVE) {
            if (manager.isBanned(event.getCurrentItem())) {
                event.setCancelled(true);
                sendBanMessage(player);
            }
        }

        if (clickedInv != null && clickedInv.getHolder() instanceof Player) {
            if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_SOME) {
                if (manager.isBanned(event.getCursor())) {
                    event.setCancelled(true);
                    sendBanMessage(player);
                }
            }
        }
        if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
            if (clickedInv != null && !(clickedInv.getHolder() instanceof Player)) {
                if (manager.isBanned(event.getCurrentItem())) {
                    event.setCancelled(true);
                    sendBanMessage(player);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        if (player.hasPermission(bypassPermission)) return;

        Item item = event.getItem();
        if (manager.isBanned(item.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        if (player.hasPermission(bypassPermission)) return;

        ItemStack draggedItem = event.getOldCursor();
        if (manager.isBanned(draggedItem)) {
            Set<Integer> slots = event.getRawSlots();
            InventoryView view = event.getView();

            for (int slot : slots) {
                Inventory inv = view.getInventory(slot);
                if (inv != null && inv.getHolder() instanceof Player) {
                    event.setCancelled(true);
                    sendBanMessage(player);
                    return;
                }
            }
        }
    }
}