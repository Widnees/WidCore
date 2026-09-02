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

    public enum FilterType {
        ALL, ACTIVE, EXPIRED
    }

    private final Main plugin;
    private final PunishmentManager punishmentManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    public static String BAN_LIST_TITLE;
    public static String MUTE_LIST_TITLE;
    private final Map<UUID, Integer> currentPage = new HashMap<UUID, Integer>();
    private final Map<UUID, FilterType> currentFilter = new HashMap<UUID, FilterType>();
    private final Map<UUID, String> currentSearch = new HashMap<UUID, String>();
    // Players currently in the process of reopening the menu — close event should not clear state
    private final java.util.Set<UUID> reopening = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public PunishmentMenuManager(Main plugin, PunishmentManager punishmentManager) {
        this.plugin = plugin;
        this.punishmentManager = punishmentManager;
        BAN_LIST_TITLE = plugin.getLanguageManager().getMessage("punishment_menu.ban-title");
        MUTE_LIST_TITLE = plugin.getLanguageManager().getMessage("punishment_menu.mute-title");
    }

    public void clearState(UUID uuid) {
        this.currentPage.remove(uuid);
        this.currentFilter.remove(uuid);
        this.currentSearch.remove(uuid);
    }

    /** @deprecated use clearState */
    public void clearPage(UUID uuid) {
        clearState(uuid);
    }

    public int getCurrentPage(UUID uuid) {
        return this.currentPage.getOrDefault(uuid, 1);
    }

    public FilterType getCurrentFilter(UUID uuid) {
        return this.currentFilter.getOrDefault(uuid, FilterType.ALL);
    }

    public String getCurrentSearch(UUID uuid) {
        return this.currentSearch.get(uuid);
    }

    public void setCurrentSearch(UUID uuid, String playerName) {
        if (playerName == null) {
            this.currentSearch.remove(uuid);
        } else {
            this.currentSearch.put(uuid, playerName);
        }
    }

    public boolean isReopening(UUID uuid) {
        return reopening.contains(uuid);
    }

    public void setReopening(UUID uuid, boolean value) {
        if (value) reopening.add(uuid);
        else reopening.remove(uuid);
    }

    public void openPunishmentListMenu(Player viewer, int page, boolean isBanList) {
        openPunishmentListMenu(viewer, page, isBanList, getCurrentFilter(viewer.getUniqueId()));
    }

    public void openPunishmentListMenu(Player viewer, int page, boolean isBanList, FilterType filter) {
        this.currentFilter.put(viewer.getUniqueId(), filter);

        java.util.List<java.util.AbstractMap.SimpleEntry<UUID, BinaryDataManager.PunishmentEntry>> allEntries = isBanList
                ? this.punishmentManager.getAllBanEntriesWithHistory()
                : this.punishmentManager.getAllMuteEntriesWithHistory();

        String title = isBanList ? BAN_LIST_TITLE : MUTE_LIST_TITLE;

        // Apply search filter
        String searchName = getCurrentSearch(viewer.getUniqueId());

        ArrayList<Map.Entry<UUID, BinaryDataManager.PunishmentEntry>> sortedList = new ArrayList<>();
        for (java.util.AbstractMap.SimpleEntry<UUID, BinaryDataManager.PunishmentEntry> e : allEntries) {
            // Search filter
            if (searchName != null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
                String name = op.getName();
                if (name == null || !name.equalsIgnoreCase(searchName)) continue;
            }

            // Status filter
            if (filter != FilterType.ALL) {
                BinaryDataManager.PunishmentEntry entry = e.getValue();
                boolean isActive;
                if (entry.expiry == -1L) {
                    isActive = true; // permanent = active
                } else {
                    isActive = System.currentTimeMillis() < entry.expiry;
                }
                if (filter == FilterType.ACTIVE && !isActive) continue;
                if (filter == FilterType.EXPIRED && isActive) continue;
            }

            sortedList.add(e);
        }

        // already sorted by timestamp desc from the manager; keep as-is

        int itemsPerPage = 28;
        int totalPages = Math.max(1, (int) Math.ceil((double) sortedList.size() / (double) itemsPerPage));
        page = Math.max(1, Math.min(page, totalPages));

        Inventory menu = Bukkit.createInventory(null, 54, (String) (String.valueOf(title) + " (" + page + "/" + totalPages + ")"));

        int startIndex = (page - 1) * itemsPerPage;
        int slotIndex = 10;
        int i = 0;
        while (i < itemsPerPage) {
            int listIndex;
            if (i > 0 && i % 7 == 0) {
                slotIndex += 2;
            }
            if ((listIndex = startIndex + i) >= sortedList.size()) break;
            Map.Entry<UUID, BinaryDataManager.PunishmentEntry> entryData = sortedList.get(listIndex);
            OfflinePlayer target = Bukkit.getOfflinePlayer(entryData.getKey());
            menu.setItem(slotIndex++, this.createPlayerHead(target, entryData.getValue()));
            ++i;
        }

        // Navigation buttons
        if (page > 1) {
            menu.setItem(48, this.createCustomMenuItem(Material.ARROW, this.plugin.getLanguageManager().getMessage("punishment_menu.prev")));
        }
        if (page < totalPages) {
            menu.setItem(50, this.createCustomMenuItem(Material.ARROW, this.plugin.getLanguageManager().getMessage("punishment_menu.next")));
        }
        menu.setItem(49, this.createCustomMenuItem(Material.BARRIER, this.plugin.getLanguageManager().getMessage("punishment_menu.close")));

        // Search button (slot 45)
        menu.setItem(45, this.createSearchButton(searchName));

        // Filter button (slot 53)
        menu.setItem(53, this.createFilterButton(filter));

        this.fillBorders(menu);
        this.currentPage.put(viewer.getUniqueId(), page);
        viewer.openInventory(menu);
    }

    private ItemStack createSearchButton(String activeSearch) {
        ItemStack item = new ItemStack(Material.OAK_SIGN);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextParser.colorize(this.plugin.getLanguageManager().getMessage("punishment_menu.search-button")));
            ArrayList<String> lore = new ArrayList<>();
            lore.add(TextParser.colorize(this.plugin.getLanguageManager().getMessage("punishment_menu.search-button-lore")));
            if (activeSearch != null) {
                lore.add(TextParser.colorize(this.plugin.getLanguageManager().getMessage("punishment_menu.search-active").replace("%player%", activeSearch)));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFilterButton(FilterType filter) {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextParser.colorize(
                    this.plugin.getLanguageManager().getMessage("punishment_menu.filter-button")));

            // Selected = aqua (&b), unselected = white (&f)
            String selectedColor = "&b";
            String normalColor = "&f";

            String allLabel     = this.plugin.getLanguageManager().getMessage("punishment_menu.filter-all");
            String activeLabel  = this.plugin.getLanguageManager().getMessage("punishment_menu.filter-active");
            String expiredLabel = this.plugin.getLanguageManager().getMessage("punishment_menu.filter-expired");

            ArrayList<String> lore = new ArrayList<>();
            lore.add(TextParser.colorize(this.plugin.getLanguageManager().getMessage("punishment_menu.filter-lore")));
            lore.add("");
            lore.add(TextParser.colorize((filter == FilterType.ALL     ? selectedColor : normalColor) + allLabel));
            lore.add(TextParser.colorize((filter == FilterType.ACTIVE  ? selectedColor : normalColor) + activeLabel));
            lore.add(TextParser.colorize((filter == FilterType.EXPIRED ? selectedColor : normalColor) + expiredLabel));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public FilterType nextFilter(FilterType current) {
        switch (current) {
            case ALL: return FilterType.ACTIVE;
            case ACTIVE: return FilterType.EXPIRED;
            default: return FilterType.ALL;
        }
    }

    private ItemStack createPlayerHead(OfflinePlayer target, BinaryDataManager.PunishmentEntry entry) {
        long remaining;
        String punisherName;
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
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
            OfflinePlayer punisher = Bukkit.getOfflinePlayer((UUID) entry.punisherUUID);
            punisherName = punisher != null && punisher.getName() != null ? punisher.getName() : unknown;
        }
        // Determine if active
        boolean isActive;
        if (entry.expiry == -1L) {
            isActive = true;
        } else {
            isActive = System.currentTimeMillis() < entry.expiry;
        }
        String statusLabel = isActive
                ? this.plugin.getLanguageManager().getMessage("punishment_menu.status-active")
                : this.plugin.getLanguageManager().getMessage("punishment_menu.status-expired-label");

        lore.add(TextParser.colorize(this.plugin.getLanguageManager().getMessage("punishment_menu.item-staff").replace("%staff%", punisherName)));
        lore.add(TextParser.colorize(this.plugin.getLanguageManager().getMessage("punishment_menu.item-reason").replace("%reason%", entry.reason)));
        lore.add(TextParser.colorize(this.plugin.getLanguageManager().getMessage("punishment_menu.item-date").replace("%date%", this.dateFormat.format(new Date(entry.timestamp)))));

        if (isActive) {
            // Show remaining time for active punishments
            String expiryString = entry.expiry == -1L
                    ? this.plugin.getLanguageManager().getMessage("punishment_menu.permanent")
                    : ((remaining = entry.expiry - System.currentTimeMillis()) > 0L
                            ? "\u00a7e" + this.punishmentManager.formatDuration(remaining)
                            : this.plugin.getLanguageManager().getMessage("punishment_menu.expired"));
            lore.add(TextParser.colorize(this.plugin.getLanguageManager().getMessage("punishment_menu.item-duration").replace("%duration%", expiryString)));
        } else {
            // Show end date for expired punishments
            String endDateStr = this.dateFormat.format(new Date(entry.expiry));
            lore.add(TextParser.colorize(this.plugin.getLanguageManager().getMessage("punishment_menu.item-end-date").replace("%date%", endDateStr)));
            // Show who removed it
            String removedBy = entry.removedBy != null
                    ? entry.removedBy
                    : this.plugin.getLanguageManager().getMessage("punishment_menu.removed-by-expired");
            lore.add(TextParser.colorize(this.plugin.getLanguageManager().getMessage("punishment_menu.item-removed-by").replace("%player%", removedBy)));
        }

        lore.add(TextParser.colorize(this.plugin.getLanguageManager().getMessage("punishment_menu.item-status").replace("%status%", statusLabel)));
        lore.add("\u00a78\u00a7m--------------------");
        meta.setLore(lore);
        head.setItemMeta((ItemMeta) meta);
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

    @SuppressWarnings("unused")
    private static final String _0xWd3f9b = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";
}