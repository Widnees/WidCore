package org.widnees.widCore.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.TextParser;

public class ShowItemManager {
    private final Main plugin;
    private final boolean componentApiExists;
    private Economy economy;
    private final Set<UUID> activeShowItemViewers = ConcurrentHashMap.newKeySet();

    public ShowItemManager(Main plugin) {
        boolean components;
        this.plugin = plugin;
        this.setupEconomy();
        try {
            ItemMeta.class.getMethod("displayName", Component.class);
            components = true;
        }
        catch (NoSuchMethodException e) {
            components = false;
        }
        this.componentApiExists = components;
    }

    private void setupEconomy() {
        if (this.plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider rsp = this.plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return;
        }
        this.economy = (Economy)rsp.getProvider();
    }

    private void setDisplayName(ItemMeta meta, String text) {
        if (this.componentApiExists) {
            meta.displayName(TextParser.parse(text));
        } else {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes((char)'&', (String)text));
        }
    }

    private void setLore(ItemMeta meta, List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            meta.setLore(null);
            return;
        }
        if (this.componentApiExists) {
            ArrayList<Component> componentLore = new ArrayList<Component>();
            for (String line : lore) {
                componentLore.add(TextParser.parse(line));
            }
            meta.lore(componentLore);
        } else {
            ArrayList<String> coloredLore = new ArrayList<String>();
            for (String line : lore) {
                coloredLore.add(ChatColor.translateAlternateColorCodes((char)'&', (String)line));
            }
            meta.setLore(coloredLore);
        }
    }

    public boolean isShowItemViewer(UUID viewerUUID) {
        return activeShowItemViewers.contains(viewerUUID);
    }

    public void removeShowItemViewer(UUID viewerUUID) {
        activeShowItemViewers.remove(viewerUUID);
    }

    public Inventory createItemInventory(Player viewer, Player target, String type) {
        ItemMeta meta;
        ItemStack item;
        String title;
        switch (type) {
            case "mainhand": {
                title = this.plugin.getLanguageManager().getMessage("showitem.mainhand");
                item = target.getInventory().getItemInMainHand();
                break;
            }
            case "offhand": {
                title = this.plugin.getLanguageManager().getMessage("showitem.offhand");
                item = target.getInventory().getItemInOffHand();
                break;
            }
            case "helmet": {
                title = this.plugin.getLanguageManager().getMessage("showitem.helmet");
                item = target.getInventory().getHelmet();
                break;
            }
            case "chestplate": {
                title = this.plugin.getLanguageManager().getMessage("showitem.chestplate");
                item = target.getInventory().getChestplate();
                break;
            }
            case "leggings": {
                title = this.plugin.getLanguageManager().getMessage("showitem.leggings");
                item = target.getInventory().getLeggings();
                break;
            }
            case "boots": {
                title = this.plugin.getLanguageManager().getMessage("showitem.boots");
                item = target.getInventory().getBoots();
                break;
            }
            default: {
                return null;
            }
        }
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer((UUID)target.getUniqueId());
        if (offlineTarget == null) {
            Main.sendMessage(this.plugin, (CommandSender)viewer, this.plugin.getLanguageManager().getMessage("showitem.offline"));
            return null;
        }
        Inventory inv = Bukkit.createInventory(null, (InventoryType)InventoryType.DISPENSER, (String)TextParser.colorize(title));
        ItemStack filler = this.createPlaceholder();
        int i = 0;
        while (i < 9) {
            if (i != 4) {
                inv.setItem(i, filler);
            }
            ++i;
        }
        if ((item == null || item.getType() == Material.AIR) && (meta = (item = this.createPlaceholder()).getItemMeta()) != null) {
            this.setDisplayName(meta, this.plugin.getLanguageManager().getMessage("showitem.no-item"));
            item.setItemMeta(meta);
        }
        inv.setItem(4, item);
        activeShowItemViewers.add(viewer.getUniqueId());
        return inv;
    }

    public Inventory createPlayerInventory(Player viewer, Player target) {
        ItemStack item;
        String title = this.plugin.getLanguageManager().getMessage("showitem.inventory").replace("%player%", target.getName());
        Inventory inv = Bukkit.createInventory(null, (int)54, (String)TextParser.colorize(title));
        Player p = Bukkit.getPlayer((UUID)target.getUniqueId());
        if (p == null) {
            Main.sendMessage(this.plugin, (CommandSender)viewer, this.plugin.getLanguageManager().getMessage("showitem.offline"));
            return null;
        }
        inv.setItem(0, this.createExperienceItem(p));
        inv.setItem(1, this.createEconomyItem(p));
        inv.setItem(2, this.createPlaceholder());
        inv.setItem(3, this.createArmorItem(p, "helmet"));
        inv.setItem(4, this.createArmorItem(p, "chestplate"));
        inv.setItem(5, this.createArmorItem(p, "leggings"));
        inv.setItem(6, this.createArmorItem(p, "boots"));
        inv.setItem(7, this.createPlaceholder());
        inv.setItem(8, this.createArmorItem(p, "offhand"));
        ItemStack glass = this.createPlaceholder();
        int i = 9;
        while (i < 18) {
            inv.setItem(i, glass);
            ++i;
        }
        i = 9;
        while (i < 36) {
            item = p.getInventory().getItem(i);
            if (item != null) {
                inv.setItem(i + 9, item);
            }
            ++i;
        }
        i = 0;
        while (i < 9) {
            item = p.getInventory().getItem(i);
            if (item != null) {
                inv.setItem(i + 45, item);
            }
            ++i;
        }
        activeShowItemViewers.add(viewer.getUniqueId());
        return inv;
    }

    public Inventory createEnderChestInventory(Player viewer, Player target) {
        String title = this.plugin.getLanguageManager().getMessage("showitem.enderchest").replace("%player%", target.getName());
        Inventory inv = Bukkit.createInventory(null, (int)27, (String)TextParser.colorize(title));
        Player p = Bukkit.getPlayer((UUID)target.getUniqueId());
        if (p == null) {
            Main.sendMessage(this.plugin, (CommandSender)viewer, this.plugin.getLanguageManager().getMessage("showitem.offline"));
            return null;
        }
        inv.setContents(p.getEnderChest().getContents());
        activeShowItemViewers.add(viewer.getUniqueId());
        return inv;
    }

    private ItemStack createPlaceholder() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            this.setDisplayName(meta, " ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createArmorItem(Player p, String type) {
        ItemStack item = switch (type) {
            case "helmet" -> p.getInventory().getHelmet();
            case "chestplate" -> p.getInventory().getChestplate();
            case "leggings" -> p.getInventory().getLeggings();
            case "boots" -> p.getInventory().getBoots();
            case "offhand" -> p.getInventory().getItemInOffHand();
            case "mainhand" -> p.getInventory().getItemInMainHand();
            default -> null;
        };
        if (item == null || item.getType() == Material.AIR) {
            ItemStack placeholder = new ItemStack(Material.BARRIER);
            ItemMeta meta = placeholder.getItemMeta();
            if (meta != null) {
                this.setDisplayName(meta, this.plugin.getLanguageManager().getMessage("showitem.no-item"));
                placeholder.setItemMeta(meta);
            }
            return placeholder;
        }
        return item;
    }

    private ItemStack createEconomyItem(Player p) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            this.setDisplayName(meta, this.plugin.getLanguageManager().getMessage("showitem.economy-title"));
            double balance = this.economy != null ? this.economy.getBalance((OfflinePlayer)p) : 0.0;
            ArrayList<String> lore = new ArrayList<String>();
            lore.add(this.plugin.getLanguageManager().getMessage("showitem.economy-balance").replace("%amount%", String.format("%.2f", balance)));
            this.setLore(meta, lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createExperienceItem(Player p) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            this.setDisplayName(meta, this.plugin.getLanguageManager().getMessage("showitem.xp").replace("%level%", String.valueOf(p.getLevel())));
            this.setLore(meta, Collections.singletonList(this.plugin.getLanguageManager().getMessage("showitem.xp-next").replace("%percent%", String.format("%.0f", Float.valueOf(p.getExp() * 100.0f)))));
            item.setItemMeta(meta);
        }
        return item;
    }
        @SuppressWarnings("unused")
    private static final String __W5e9c3x = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}
