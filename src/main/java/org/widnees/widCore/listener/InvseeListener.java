package org.widnees.widCore.listener;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.TextParser;

import java.util.UUID;

public class InvseeListener implements Listener {

    private final Main plugin;

    public InvseeListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player viewer = (Player) event.getPlayer();
        UUID viewerId = viewer.getUniqueId();
        if (plugin.getActiveInvseeTasks().containsKey(viewerId)) {
            plugin.getActiveInvseeTasks().get(viewerId).cancel();
            plugin.getActiveInvseeTasks().remove(viewerId);
        }
        if (plugin.getOpenInvseeInventories().containsKey(viewerId)) {
            UUID targetId = plugin.getOpenInvseeInventories().remove(viewerId);
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
            if (!target.isOnline()) {
                plugin.getDataManager().saveOfflinePlayerInventory(target, event.getInventory());
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getSlot() < 0) {
            return;
        }

        Player viewer = (Player) event.getWhoClicked();
        UUID viewerId = viewer.getUniqueId();

        if (!plugin.getOpenInvseeInventories().containsKey(viewerId)) {
            return;
        }

        String currentTitle = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        UUID targetId = plugin.getOpenInvseeInventories().get(viewerId);
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);

        String langTitleFormat = plugin.getLanguageManager().getMessage("invsee.inventory-title");
        String expectedTitleColored = langTitleFormat.replace("%player%", target.getName());
        String expectedTitlePlain = ChatColor.stripColor(TextParser.colorize(expectedTitleColored));

        if (!currentTitle.contains(expectedTitlePlain)) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        ItemStack currentItem = event.getCurrentItem();

        if (currentItem != null && currentItem.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            event.setCancelled(true);
            return;
        }

        if (clickedInventory != null && clickedInventory.equals(viewer.getInventory())) {
            if (event.getClick().isShiftClick()) {
                event.setCancelled(true);
                ItemStack clickedStack = currentItem.clone();
                Inventory topInventory = event.getView().getTopInventory();

                for (int i = 0; i < 36; i++) {
                    ItemStack slotItem = topInventory.getItem(i);
                    if (slotItem == null || slotItem.getType() == Material.AIR) {
                        topInventory.setItem(i, clickedStack);
                        viewer.getInventory().setItem(event.getSlot(), null);
                        syncTargetInventoryLater(viewer, topInventory);
                        return;
                    } else if (slotItem.isSimilar(clickedStack) && slotItem.getAmount() < slotItem.getMaxStackSize()) {
                        int needed = slotItem.getMaxStackSize() - slotItem.getAmount();
                        int toAdd = Math.min(needed, clickedStack.getAmount());
                        slotItem.setAmount(slotItem.getAmount() + toAdd);
                        clickedStack.setAmount(clickedStack.getAmount() - toAdd);
                        if (clickedStack.getAmount() <= 0) {
                            viewer.getInventory().setItem(event.getSlot(), null);
                            syncTargetInventoryLater(viewer, topInventory);
                            return;
                        }
                    }
                }
                viewer.getInventory().setItem(event.getSlot(), clickedStack);
                syncTargetInventoryLater(viewer, topInventory);
                return;
            }
        }
        syncTargetInventoryLater(viewer, event.getView().getTopInventory());
    }

    private void syncTargetInventoryLater(Player viewer, Inventory virtualInv) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            UUID targetId = plugin.getOpenInvseeInventories().get(viewer.getUniqueId());
            if (targetId == null) return;
            Player target = Bukkit.getPlayer(targetId);
            if (target == null || !target.isOnline()) return;

            ItemStack[] storageContents = new ItemStack[36];
            for (int i = 0; i < 36; i++) {
                storageContents[i] = virtualInv.getItem(i);
            }
            target.getInventory().setStorageContents(storageContents);

            ItemStack[] armorContents = new ItemStack[4];
            armorContents[3] = virtualInv.getItem(45);
            armorContents[2] = virtualInv.getItem(46);
            armorContents[1] = virtualInv.getItem(47);
            armorContents[0] = virtualInv.getItem(48);
            target.getInventory().setArmorContents(armorContents);

            target.getInventory().setItemInOffHand(virtualInv.getItem(53));
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!ConfigManager.isConfigLoaded()) {return;}
        Player playerWhoQuit = event.getPlayer();
        UUID quitPlayerId = playerWhoQuit.getUniqueId();

        if (plugin.getOpenInvseeInventories().containsValue(quitPlayerId)) {
            plugin.getOpenInvseeInventories().entrySet().stream()
                    .filter(entry -> entry.getValue().equals(quitPlayerId))
                    .findFirst()
                    .ifPresent(entry -> {
                        Player viewer = Bukkit.getPlayer(entry.getKey());
                        if (viewer != null) {
                            viewer.closeInventory();
                            String msg = plugin.getLanguageManager().getMessage("invsee.closed-quit")
                                    .replace("%player%", playerWhoQuit.getName());
                            Main.sendMessage(this.plugin, viewer, msg);
                        }
                    });
        }
        plugin.getOpenInvseeInventories().remove(quitPlayerId);
        if (plugin.getActiveInvseeTasks().containsKey(quitPlayerId)) {
            plugin.getActiveInvseeTasks().get(quitPlayerId).cancel();
            plugin.getActiveInvseeTasks().remove(quitPlayerId);
        }
    }
}