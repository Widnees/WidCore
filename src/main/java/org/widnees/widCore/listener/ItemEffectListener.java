package org.widnees.widCore.listener;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ItemEffectManager;
import org.widnees.widCore.util.FoliaScheduler;

public class ItemEffectListener implements Listener {

    private final ItemEffectManager itemEffectManager;
    private final Main plugin;

    public ItemEffectListener(Main plugin, ItemEffectManager itemEffectManager) {
        this.plugin = plugin;
        this.itemEffectManager = itemEffectManager;
    }

    private void scheduleUpdate(Player player) {
        FoliaScheduler.runAtEntityLater(plugin, player, () -> itemEffectManager.updatePlayerEffects(player), 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        if (title.equals(ItemEffectManager.EFFECT_MENU_TITLE)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player))
                return;
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType().isAir())
                return;
            itemEffectManager.handleClick(player, event.getSlot(), clickedItem, event.getClick());
            return;
        }

        if (event.getWhoClicked() instanceof Player) {
            if (event.getSlotType() == InventoryType.SlotType.ARMOR || event.getSlot() == 40) {
                Player player = (Player) event.getWhoClicked();
                scheduleUpdate(player);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.equals(ItemEffectManager.EFFECT_MENU_TITLE)) {
            itemEffectManager.cleanupEditor(event.getPlayer().getUniqueId());
            if (event.getPlayer() instanceof Player) {
                scheduleUpdate((Player) event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        scheduleUpdate(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        scheduleUpdate(event.getPlayer());
    }

    @EventHandler
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        ItemStack oldItem = player.getInventory().getItem(event.getPreviousSlot());

        if (itemEffectManager.itemHasEffects(newItem) || itemEffectManager.itemHasEffects(oldItem)) {
            scheduleUpdate(player);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (itemEffectManager.itemHasEffects(event.getItemDrop().getItemStack())) {
            scheduleUpdate(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            if (itemEffectManager.itemHasEffects(event.getItem().getItemStack())) {
                scheduleUpdate((Player) event.getEntity());
            }
        }
    }

    @EventHandler
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent event) {
        if (itemEffectManager.itemHasEffects(event.getMainHandItem())
                || itemEffectManager.itemHasEffects(event.getOffHandItem())) {
            scheduleUpdate(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        itemEffectManager.cleanupPlayerSession(event.getPlayer().getUniqueId());
    }
}