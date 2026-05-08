package org.widnees.widCore.manager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.widnees.widCore.Main;
import org.widnees.widCore.database.BinaryDataManager;
import org.widnees.widCore.manager.PunishmentManager;
import org.widnees.widCore.manager.TextParser;

public class PunishmentMenuManager {
    private final Main plugin;
    private final PunishmentManager punishmentManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    public static String BAN_LIST_TITLE;
    public static String MUTE_LIST_TITLE;
    private final Map<UUID, Integer> currentPage = new HashMap<UUID, Integer>();

    public PunishmentMenuManager(Main plugin, PunishmentManager punishmentManager) {
        this.plugin = plugin;
        this.punishmentManager = punishmentManager;
        BAN_LIST_TITLE = plugin.getLanguageManager().getMessage("punishment_menu.ban-title");
        MUTE_LIST_TITLE = plugin.getLanguageManager().getMessage("punishment_menu.mute-title");
    }

    public void clearPage(UUID uuid) {
        this.currentPage.remove(uuid);
    }

    public int getCurrentPage(UUID uuid) {
        return this.currentPage.getOrDefault(uuid, 1);
    }

    public void openPunishmentListMenu(Player viewer, int page, boolean isBanList) {
        Map<UUID, BinaryDataManager.PunishmentEntry> entries = isBanList ? this.punishmentManager.getAllBanEntries() : this.punishmentManager.getAllMuteEntries();
        String title = isBanList ? BAN_LIST_TITLE : MUTE_LIST_TITLE;
        ArrayList<Map.Entry<UUID, BinaryDataManager.PunishmentEntry>> sortedList = new ArrayList<Map.Entry<UUID, BinaryDataManager.PunishmentEntry>>(entries.entrySet());
        sortedList.sort((e1, e2) -> Long.compare(((BinaryDataManager.PunishmentEntry)e2.getValue()).timestamp, ((BinaryDataManager.PunishmentEntry)e1.getValue()).timestamp));
        int itemsPerPage = 28;
        int totalPages = Math.max(1, (int)Math.ceil((double)sortedList.size() / (double)itemsPerPage));
        page = Math.max(1, Math.min(page, totalPages));
        Inventory menu = Bukkit.createInventory(null, (int)54, (String)(String.valueOf(title) + " (" + page + "/" + totalPages + ")"));
        int startIndex = (page - 1) * itemsPerPage;
        int slotIndex = 10;
        int i = 0;
        while (i < itemsPerPage) {
            int listIndex;
            if (i > 0 && i % 7 == 0) {
                slotIndex += 2;
            }
            if ((listIndex = startIndex + i) >= sortedList.size()) break;
            Map.Entry entryData = (Map.Entry)sortedList.get(listIndex);
            OfflinePlayer target = Bukkit.getOfflinePlayer((UUID)((UUID)entryData.getKey()));
            menu.setItem(slotIndex++, this.createPlayerHead(target, (BinaryDataManager.PunishmentEntry)entryData.getValue()));
            ++i;
        }
        if (page > 1) {
            menu.setItem(48, this.createCustomMenuItem(Material.ARROW, this.plugin.getLanguageManager().getMessage("punishment_menu.prev")));
        }
        if (page < totalPages) {
            menu.setItem(50, this.createCustomMenuItem(Material.ARROW, this.plugin.getLanguageManager().getMessage("punishment_menu.next")));
        }
        menu.setItem(49, this.createCustomMenuItem(Material.BARRIER, this.plugin.getLanguageManager().getMessage("punishment_menu.close")));
        this.fillBorders(menu);
        this.currentPage.put(viewer.getUniqueId(), page);
        viewer.openInventory(menu);
    }

    private ItemStack createPlayerHead(OfflinePlayer target, BinaryDataManager.PunishmentEntry entry) {
        long remaining;
        String punisherName;
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta)head.getItemMeta();
        if (meta == null) {
            return head;
        }
        meta.setOwningPlayer(target);
        String unknown = this.plugin.getLanguageManager().getMessage("punishment_menu.unknown");
        meta.setDisplayName("\u00a7e" + (target.getName() != null ? target.getName() : unknown));
        ArrayList<String> lore = new ArrayList<String>();
        lore.add("\u00a78\u00a7m--------------------");
        if (entry.punisherUUID.equals(UUID.fromString("00000000-0000-0000-0000-000000000000"))) {
            punisherName = this.plugin.getLanguageManager().getMessage("punishment_menu.console");
        } else {
            OfflinePlayer punisher = Bukkit.getOfflinePlayer((UUID)entry.punisherUUID);
            String string = punisherName = punisher != null && punisher.getName() != null ? punisher.getName() : unknown;
        }
        String expiryString = entry.expiry == -1L ? this.plugin.getLanguageManager().getMessage("punishment_menu.permanent") : ((remaining = entry.expiry - System.currentTimeMillis()) > 0L ? "\u00a7e" + this.punishmentManager.formatDuration(remaining) : this.plugin.getLanguageManager().getMessage("punishment_menu.expired"));
        lore.add(TextParser.colorize(this.plugin.getLanguageManager().getMessage("punishment_menu.item-staff").replace("%staff%", punisherName)));
        lore.add(TextParser.colorize(this.plugin.getLanguageManager().getMessage("punishment_menu.item-reason").replace("%reason%", entry.reason)));
        lore.add(TextParser.colorize(this.plugin.getLanguageManager().getMessage("punishment_menu.item-date").replace("%date%", this.dateFormat.format(new Date(entry.timestamp)))));
        lore.add(TextParser.colorize(this.plugin.getLanguageManager().getMessage("punishment_menu.item-duration").replace("%duration%", expiryString)));
        lore.add("\u00a78\u00a7m--------------------");
        meta.setLore(lore);
        head.setItemMeta((ItemMeta)meta);
        return head;
    }

    private ItemStack createCustomMenuItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextParser.colorize(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBorders(Inventory inv) {
        ItemStack placeholder = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = placeholder.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            placeholder.setItemMeta(meta);
        }
        int size = inv.getSize();
        int i = 0;
        while (i < size) {
            if ((i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) && inv.getItem(i) == null) {
                inv.setItem(i, placeholder);
            }
            ++i;
        }
    }
}
