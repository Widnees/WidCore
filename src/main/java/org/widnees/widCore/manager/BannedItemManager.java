package org.widnees.widCore.manager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

public class BannedItemManager {
    private final Main plugin;
    private final ConfigManager configManager;
    private final Set<Material> bannedItems = new HashSet<Material>();

    public BannedItemManager(Main plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    public void loadBannedItems() {
        this.bannedItems.clear();
        FileConfiguration config = this.configManager.getModuleConfig("banneditem");
        List<String> itemList = config.getStringList("banned-items");
        for (String itemName : itemList) {
            Material material = Material.matchMaterial((String)itemName.toUpperCase());
            if (material != null) {
                this.bannedItems.add(material);
                continue;
            }
            String msg = this.plugin.getLanguageManager().getMessage("banneditem.not-found").replace("%item%", itemName);
            this.plugin.getLogger().log(Level.WARNING, msg);
        }
    }

    public void reloadConfig() {
        this.loadBannedItems();
    }

    public boolean isBanned(ItemStack item) {
        return item != null && this.bannedItems.contains(item.getType());
    }

    public boolean isBanned(Material material) {
        return material != null && this.bannedItems.contains(material);
    }

    public String getBanMessage() {
        return this.plugin.getLanguageManager().getMessage("banneditem.message");
    }
}
