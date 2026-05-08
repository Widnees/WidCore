package org.widnees.widCore.listener;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.widnees.widCore.manager.PunishmentMenuManager;

import java.util.UUID;

public class PunishmentMenuListener implements Listener {

    private final PunishmentMenuManager menuManager;

    public PunishmentMenuListener(PunishmentMenuManager menuManager) {
        this.menuManager = menuManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        if (!title.startsWith(PunishmentMenuManager.BAN_LIST_TITLE) && !title.startsWith(PunishmentMenuManager.MUTE_LIST_TITLE)) {
            return;
        }

        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }

        int clickedSlot = event.getSlot();
        boolean isBanList = title.startsWith(PunishmentMenuManager.BAN_LIST_TITLE);
        int currentPage = menuManager.getCurrentPage(player.getUniqueId());

        switch (clickedSlot) {
            case 48:
                if (currentPage > 1) {
                    menuManager.openPunishmentListMenu(player, currentPage - 1, isBanList);
                }
                break;
            case 49:
                player.closeInventory();
                break;
            case 50:
                menuManager.openPunishmentListMenu(player, currentPage + 1, isBanList);
                break;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.startsWith(PunishmentMenuManager.BAN_LIST_TITLE) || title.startsWith(PunishmentMenuManager.MUTE_LIST_TITLE)) {
            if(event.getPlayer() instanceof Player) {
                menuManager.clearPage(event.getPlayer().getUniqueId());
            }
        }
    }
}