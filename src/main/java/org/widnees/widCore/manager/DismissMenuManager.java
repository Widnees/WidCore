package org.widnees.widCore.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.widnees.widCore.Main;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DismissMenuManager {
    private final Main plugin;
    private final Map<UUID, Inventory> openMenus = new ConcurrentHashMap<>();

    public DismissMenuManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean shouldDismiss(Player player, String key) {
        return false;
    }

    public void openMenu(Player player, FileConfiguration config, String basePath) {
        int size = clampSize(config.getInt(basePath + ".size", 27));
        String rawTitle = config.getString(basePath + ".title", "Menu");
        String title = ChatColor.translateAlternateColorCodes('&', rawTitle == null ? "Menu" : rawTitle);
        Inventory inv = Bukkit.createInventory(player, size, title);
        openMenus.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public void openWelcomeMenu(Player player, FileConfiguration config, String basePath) {
        int size = clampSize(config.getInt(basePath + ".size", 27));
        String rawTitle = config.getString(basePath + ".title", "Welcome");
        String title = ChatColor.translateAlternateColorCodes('&', rawTitle == null ? "Welcome" : rawTitle);
        Inventory inv = Bukkit.createInventory(player, size, title);
        openMenus.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public boolean isDismissMenu(Player player, Inventory inv) {
        Inventory tracked = openMenus.get(player.getUniqueId());
        return tracked != null && tracked.equals(inv);
    }

    public void dismissMenu(Player player) {
        openMenus.remove(player.getUniqueId());
    }

    public void cleanup(UUID playerId) {
        openMenus.remove(playerId);
    }

    private int clampSize(int size) {
        if (size < 9) return 9;
        if (size > 54) return 54;
        
        int rem = size % 9;
        return rem == 0 ? size : (size + (9 - rem));
    }
}
