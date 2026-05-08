package org.widnees.widCore.listener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.HomeManager;
import org.widnees.widCore.manager.TeleportManager;
import org.widnees.widCore.manager.TextParser;
import org.widnees.widCore.util.FoliaScheduler;

import java.util.*;

public class HomeListener implements Listener {

    private final Main plugin;
    private final HomeManager homeManager;
    private final Set<UUID> pendingMenus = new HashSet<>();
    private final Map<UUID, String> pendingDeletes = new HashMap<>();
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Set<UUID> changingPage = new HashSet<>(); 

    public HomeListener(Main plugin) {
        this.plugin = plugin;
        this.homeManager = plugin.getHomeManager();
    }

    public void openHomeMenu(Player player) {
        openHomeMenuPage(player, 0);
    }

    private void openHomeMenuPage(Player player, int page) {
        int maxHomes = homeManager.getMaxHomes(player);
        FileConfiguration homeConfig = homeManager.getHomeConfig();
        boolean showLocation = homeConfig.getBoolean("menu.show-location", true);

        int homeRows = Math.max(1, Math.min(4, (int) Math.ceil((double) maxHomes / 7.0)));
        int homesPerPage = homeRows * 7;
        int totalRows = homeRows + 2; 
        int menuSize = totalRows * 9;
        int bottomRowStart = menuSize - 9;

        String title = TextParser.colorize(plugin.getLanguageManager().getMessage("home.menu-title"));
        Inventory menu = Bukkit.createInventory(null, menuSize, title);

        homeManager.getHomes(player.getUniqueId()).thenAccept(homes -> {
            FoliaScheduler.runTask(plugin, () -> {
                List<String> homeNames = new ArrayList<>(homes.keySet());
                Collections.sort(homeNames);

                int totalPages = (int) Math.ceil((double) maxHomes / homesPerPage);
                if (totalPages < 1)
                    totalPages = 1;

                int currentPage = Math.max(0, Math.min(page, totalPages - 1));
                playerPages.put(player.getUniqueId(), currentPage);

                ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta borderMeta = border.getItemMeta();
                borderMeta.setDisplayName(" ");
                border.setItemMeta(borderMeta);

                for (int i = 0; i < 9; i++) {
                    menu.setItem(i, border);
                }
                
                for (int i = bottomRowStart; i < menuSize; i++) {
                    menu.setItem(i, border);
                }
                
                for (int row = 1; row <= homeRows; row++) {
                    menu.setItem(row * 9, border);      
                    menu.setItem(row * 9 + 8, border);  
                }

                List<Integer> centerSlots = new ArrayList<>();
                for (int row = 1; row <= homeRows; row++) {
                    for (int col = 1; col <= 7; col++) {
                        centerSlots.add(row * 9 + col);
                    }
                }

                int startIndex = currentPage * homesPerPage;

                for (int i = 0; i < centerSlots.size() && (startIndex + i) < maxHomes; i++) {
                    int homeIndex = startIndex + i;
                    int slot = centerSlots.get(i);

                    if (homeIndex < homeNames.size()) {
                        
                        String homeName = homeNames.get(homeIndex);
                        Location loc = homes.get(homeName);

                        ItemStack bed = new ItemStack(Material.RED_BED);
                        ItemMeta bedMeta = bed.getItemMeta();
                        bedMeta.setDisplayName(TextParser.colorize(
                                plugin.getLanguageManager().getMessage("home.menu-items.bed-set-name")
                                        .replace("%home%", homeName)));

                        List<String> lore = new ArrayList<>();
                        if (showLocation) {
                            for (String line : plugin.getLanguageManager()
                                    .getMessageList("home.menu-items.bed-set-lore")) {
                                lore.add(TextParser.colorize(line
                                        .replace("%world%",
                                                loc.getWorld() != null ? loc.getWorld().getName() : "Unknown")
                                        .replace("%x%", String.valueOf(loc.getBlockX()))
                                        .replace("%y%", String.valueOf(loc.getBlockY()))
                                        .replace("%z%", String.valueOf(loc.getBlockZ()))));
                            }
                        } else {
                            lore.add(TextParser.colorize(plugin.getLanguageManager().getMessage("home.pagination.click-teleport")));
                        }
                        bedMeta.setLore(lore);
                        bed.setItemMeta(bedMeta);
                        menu.setItem(slot, bed);
                    } else {
                        
                        ItemStack bed = new ItemStack(Material.GRAY_BED);
                        ItemMeta bedMeta = bed.getItemMeta();
                        bedMeta.setDisplayName(TextParser.colorize(
                                plugin.getLanguageManager().getMessage("home.menu-items.bed-empty-name")));

                        List<String> lore = new ArrayList<>();
                        for (String line : plugin.getLanguageManager()
                                .getMessageList("home.menu-items.bed-empty-lore")) {
                            lore.add(TextParser.colorize(line));
                        }
                        bedMeta.setLore(lore);
                        bed.setItemMeta(bedMeta);
                        menu.setItem(slot, bed);
                    }
                }

                if (totalPages > 1) {
                    int prevSlot = bottomRowStart + 3;
                    int pageSlot = bottomRowStart + 4;
                    int nextSlot = bottomRowStart + 5;

                    if (currentPage > 0) {
                        ItemStack prevButton = new ItemStack(Material.ARROW);
                        ItemMeta prevMeta = prevButton.getItemMeta();
                        prevMeta.setDisplayName(TextParser.colorize(plugin.getLanguageManager().getMessage("home.pagination.prev-page")));
                        prevMeta.setLore(
                                Arrays.asList(TextParser.colorize(plugin.getLanguageManager().getMessage("home.pagination.page-format-lore")
                                        .replace("%current%", String.valueOf(currentPage))
                                        .replace("%total%", String.valueOf(totalPages)))));
                        prevButton.setItemMeta(prevMeta);
                        menu.setItem(prevSlot, prevButton);
                    }

                    ItemStack pageIndicator = new ItemStack(Material.PAPER);
                    ItemMeta pageMeta = pageIndicator.getItemMeta();
                    pageMeta.setDisplayName(TextParser.colorize(plugin.getLanguageManager().getMessage("home.pagination.page-format-title")
                            .replace("%current%", String.valueOf(currentPage + 1))
                            .replace("%total%", String.valueOf(totalPages))));
                    pageIndicator.setItemMeta(pageMeta);
                    menu.setItem(pageSlot, pageIndicator);

                    if (currentPage < totalPages - 1) {
                        ItemStack nextButton = new ItemStack(Material.ARROW);
                        ItemMeta nextMeta = nextButton.getItemMeta();
                        nextMeta.setDisplayName(TextParser.colorize(plugin.getLanguageManager().getMessage("home.pagination.next-page")));
                        nextMeta.setLore(
                                Arrays.asList(TextParser.colorize(plugin.getLanguageManager().getMessage("home.pagination.page-format-lore")
                                        .replace("%current%", String.valueOf(currentPage + 2))
                                        .replace("%total%", String.valueOf(totalPages)))));
                        nextButton.setItemMeta(nextMeta);
                        menu.setItem(nextSlot, nextButton);
                    }
                }

                pendingMenus.add(player.getUniqueId());
                player.openInventory(menu);
            });
        });
    }

    public void openDeleteConfirmMenu(Player player, String homeName) {
        String title = TextParser.colorize(plugin.getLanguageManager().getMessage("home.confirm-title"));
        Inventory menu = Bukkit.createInventory(null, 9, title);

        ItemStack redPane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta redMeta = redPane.getItemMeta();
        redMeta.setDisplayName(TextParser.colorize(
                plugin.getLanguageManager().getMessage("home.menu-items.confirm-deny-name")));
        List<String> redLore = new ArrayList<>();
        for (String line : plugin.getLanguageManager().getMessageList("home.menu-items.confirm-deny-lore")) {
            redLore.add(TextParser.colorize(line));
        }
        redMeta.setLore(redLore);
        redPane.setItemMeta(redMeta);

        for (int i = 0; i <= 3; i++) {
            menu.setItem(i, redPane);
        }

        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta bookMeta = book.getItemMeta();
        bookMeta.setDisplayName(TextParser.colorize(
                plugin.getLanguageManager().getMessage("home.menu-items.confirm-book-name")));
        List<String> bookLore = new ArrayList<>();
        for (String line : plugin.getLanguageManager().getMessageList("home.menu-items.confirm-book-lore")) {
            bookLore.add(TextParser.colorize(line.replace("%home%", homeName)));
        }
        bookMeta.setLore(bookLore);
        book.setItemMeta(bookMeta);
        menu.setItem(4, book);

        ItemStack greenPane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta greenMeta = greenPane.getItemMeta();
        greenMeta.setDisplayName(TextParser.colorize(
                plugin.getLanguageManager().getMessage("home.menu-items.confirm-accept-name")));
        List<String> greenLore = new ArrayList<>();
        for (String line : plugin.getLanguageManager().getMessageList("home.menu-items.confirm-accept-lore")) {
            greenLore.add(TextParser.colorize(line));
        }
        greenMeta.setLore(greenLore);
        greenPane.setItemMeta(greenMeta);

        for (int i = 5; i <= 8; i++) {
            menu.setItem(i, greenPane);
        }

        pendingDeletes.put(player.getUniqueId(), homeName);
        pendingMenus.add(player.getUniqueId());
        player.openInventory(menu);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();

        if (!pendingMenus.contains(player.getUniqueId()))
            return;

        event.setCancelled(true);

        String title = event.getView().getTitle();
        String homeMenuTitle = TextParser.colorize(plugin.getLanguageManager().getMessage("home.menu-title"));
        String confirmMenuTitle = TextParser.colorize(plugin.getLanguageManager().getMessage("home.confirm-title"));

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR)
            return;

        if (title.equals(homeMenuTitle)) {
            handleHomeMenuClick(player, event.getSlot(), clicked);
        } else if (title.equals(confirmMenuTitle)) {
            handleConfirmMenuClick(player, clicked);
        }
    }

    private void handleHomeMenuClick(Player player, int slot, ItemStack clicked) {
        Material type = clicked.getType();

        if (type == Material.ARROW) {
            int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);
            String displayName = clicked.getItemMeta().getDisplayName();

            changingPage.add(player.getUniqueId());

            if (displayName.contains("Önceki") || displayName.contains("Previous")) {
                openHomeMenuPage(player, currentPage - 1);
            } else if (displayName.contains("Sonraki") || displayName.contains("Next")) {
                openHomeMenuPage(player, currentPage + 1);
            }

            FoliaScheduler.runAtEntityLater(plugin, player, () -> changingPage.remove(player.getUniqueId()), 2L);
            return;
        }

        if (type == Material.RED_BED) {
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                
                String displayName = meta.getDisplayName();
                String homeName = ChatColor.stripColor(displayName).trim();

                player.closeInventory();
                pendingMenus.remove(player.getUniqueId());
                playerPages.remove(player.getUniqueId());

                homeManager.getHome(player.getUniqueId(), homeName).thenAccept(location -> {
                    if (location == null) {
                        Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("home.not-found")
                                .replace("%home%", homeName));
                        return;
                    }

                    homeManager.teleportWithDelay(player, location, homeName,
                            () -> Main.sendMessage(plugin, player,
                                    plugin.getLanguageManager().getMessage("home.success")
                                            .replace("%home%", homeName)),
                            null);
                });
            }
        }
    }

    private void handleConfirmMenuClick(Player player, ItemStack clicked) {
        Material type = clicked.getType();
        String homeName = pendingDeletes.get(player.getUniqueId());

        if (homeName == null) {
            player.closeInventory();
            return;
        }

        if (type == Material.LIME_STAINED_GLASS_PANE) {
            
            player.closeInventory();
            pendingMenus.remove(player.getUniqueId());
            pendingDeletes.remove(player.getUniqueId());

            homeManager.deleteHome(player.getUniqueId(), homeName).thenAccept(success -> {
                if (success) {
                    Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("home.deleted")
                            .replace("%home%", homeName));
                } else {
                    Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("home.not-found")
                            .replace("%home%", homeName));
                }
            });
        } else if (type == Material.RED_STAINED_GLASS_PANE) {
            
            player.closeInventory();
            pendingMenus.remove(player.getUniqueId());
            pendingDeletes.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;
        Player player = (Player) event.getPlayer();

        if (changingPage.contains(player.getUniqueId())) {
            return;
        }

        pendingMenus.remove(player.getUniqueId());
        pendingDeletes.remove(player.getUniqueId());
        playerPages.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        Player player = (Player) event.getEntity();

        TeleportManager teleportManager = plugin.getTeleportManager();
        if (!teleportManager.isTeleporting(player.getUniqueId()))
            return;

        TeleportManager.TeleportType type = teleportManager.getTeleportType(player.getUniqueId());
        if (type != TeleportManager.TeleportType.WARP)
            return;

        boolean cancelOnDamage = homeManager.getHomeConfig().getBoolean("cancel-on-damage", true);
        if (cancelOnDamage) {
            teleportManager.cancelTeleport(player,
                    plugin.getLanguageManager().getMessage("home.cancelled"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player))
            return;
        Player player = (Player) event.getDamager();

        TeleportManager teleportManager = plugin.getTeleportManager();
        if (!teleportManager.isTeleporting(player.getUniqueId()))
            return;

        TeleportManager.TeleportType type = teleportManager.getTeleportType(player.getUniqueId());
        if (type != TeleportManager.TeleportType.WARP)
            return;

        boolean cancelOnDamage = homeManager.getHomeConfig().getBoolean("cancel-on-damage", true);
        if (cancelOnDamage) {
            teleportManager.cancelTeleport(player,
                    plugin.getLanguageManager().getMessage("home.cancelled"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        TeleportManager teleportManager = plugin.getTeleportManager();
        if (!teleportManager.isTeleporting(player.getUniqueId()))
            return;

        TeleportManager.TeleportType type = teleportManager.getTeleportType(player.getUniqueId());
        if (type != TeleportManager.TeleportType.WARP)
            return;

        boolean cancelOnBlockBreak = homeManager.getHomeConfig().getBoolean("cancel-on-block-break", true);
        if (cancelOnBlockBreak) {
            teleportManager.cancelTeleport(player,
                    plugin.getLanguageManager().getMessage("home.cancelled"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        TeleportManager teleportManager = plugin.getTeleportManager();
        if (!teleportManager.isTeleporting(player.getUniqueId()))
            return;

        TeleportManager.TeleportType type = teleportManager.getTeleportType(player.getUniqueId());
        if (type != TeleportManager.TeleportType.WARP)
            return;

        boolean cancelOnBlockBreak = homeManager.getHomeConfig().getBoolean("cancel-on-block-break", true);
        if (cancelOnBlockBreak) {
            teleportManager.cancelTeleport(player,
                    plugin.getLanguageManager().getMessage("home.cancelled"));
        }
    }
}
