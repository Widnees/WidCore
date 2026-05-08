package org.widnees.widCore.manager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.EconomyManager;
import org.widnees.widCore.manager.TextParser;

public class BaltopManager {
    private final Main plugin;
    private final EconomyManager economyManager;
    private List<Map.Entry<UUID, Double>> cachedTopPlayers = new ArrayList<Map.Entry<UUID, Double>>();
    private long lastUpdateTime = 0L;
    private static final long UPDATE_INTERVAL = 60000L;

    public BaltopManager(Main plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    public void updateTopPlayers() {
        long now = System.currentTimeMillis();
        if (now - this.lastUpdateTime < 60000L && !this.cachedTopPlayers.isEmpty()) {
            return;
        }
        Map<UUID, Double> allBalances = this.economyManager.getAllBalances();
        this.cachedTopPlayers = allBalances.entrySet().stream().filter(entry -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer((UUID)((UUID)entry.getKey()));
            return op.getName() != null && op.hasPlayedBefore();
        }).sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(10L).collect(Collectors.toList());
        this.lastUpdateTime = now;
    }

    public void openBaltopMenu(Player player) {
        this.updateTopPlayers();
        String title = TextParser.colorize(this.plugin.getLanguageManager().getMessage("economy.baltop-title"));
        Inventory menu = Bukkit.createInventory(null, (InventoryType)InventoryType.DISPENSER, (String)title);
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        int i = 0;
        while (i < 9) {
            if (i != 4) {
                menu.setItem(i, filler);
            }
            ++i;
        }
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String rankItemName = this.plugin.getLanguageManager().getMessage("economy.baltop-rank-item-name");
            meta.setDisplayName(TextParser.colorize(rankItemName));
            ArrayList<String> lore = new ArrayList<String>();
            int rank = 1;
            for (Map.Entry<UUID, Double> entry : this.cachedTopPlayers) {
                OfflinePlayer target = Bukkit.getOfflinePlayer((UUID)entry.getKey());
                String name = target.getName() != null ? target.getName() : "Bilinmiyor";
                String balance = this.economyManager.formatMoney(entry.getValue());
                lore.add(TextParser.colorize("&6" + rank + ". &f" + name + " &8- &a" + balance));
                ++rank;
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        menu.setItem(4, item);
        player.openInventory(menu);
    }
}
