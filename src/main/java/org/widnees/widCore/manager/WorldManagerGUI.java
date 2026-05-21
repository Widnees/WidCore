package org.widnees.widCore.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.TextParser;
import org.widnees.widCore.manager.WorldDataManager;

public class WorldManagerGUI
implements InventoryHolder {
    private final Main plugin;
    private final WorldDataManager worldDataManager;
    private final Map<UUID, Integer> playerPages = new HashMap<UUID, Integer>();
    private final Map<UUID, String> playerWorlds = new HashMap<UUID, String>();
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREV_SLOT = 48;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int CLOSE_SLOT = 53;

    public WorldManagerGUI(Main plugin) {
        this.plugin = plugin;
        this.worldDataManager = plugin.getWorldDataManager();
    }

    @NotNull
    public Inventory getInventory() {
        return Bukkit.createInventory((InventoryHolder)this, (int)54, (String)"WorldManager");
    }

    public void openGameRuleMenu(Player player, World world) {
        this.openGameRuleMenu(player, world, 0);
    }

    public void openGameRuleMenu(Player player, World world, int page) {
        String title = this.plugin.getLanguageManager().getMessage("worldmanager.settings-title").replace("%world%", world.getName());
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        Inventory inv = Bukkit.createInventory((InventoryHolder)this, (int)54, (String)TextParser.colorize(title));
        ArrayList<GameRule> rules = new ArrayList<GameRule>();
        GameRule[] gameRuleArray = GameRule.values();
        int n = gameRuleArray.length;
        int n2 = 0;
        while (n2 < n) {
            GameRule rule = gameRuleArray[n2];
            try {
                world.getGameRuleValue(rule);
                rules.add(rule);
            }
            catch (IllegalArgumentException illegalArgumentException) {

            }
            ++n2;
        }
        rules.sort(Comparator.comparing(GameRule::getName));
        int totalPages = (int)Math.ceil((double)rules.size() / 45.0);
        if (page < 0) {
            page = 0;
        }
        if (page >= totalPages) {
            page = totalPages - 1;
        }
        if (totalPages == 0) {
            totalPages = 1;
        }
        int startIndex = page * 45;
        int endIndex = Math.min(startIndex + 45, rules.size());
        int i = startIndex;
        while (i < endIndex) {
            GameRule rule = (GameRule)rules.get(i);
            ItemStack item = this.createGameRuleItem(world, rule);
            if (item != null) {
                inv.setItem(i - startIndex, item);
            }
            ++i;
        }
        this.addNavigationButtons(inv, page, totalPages, world);
        this.playerPages.put(player.getUniqueId(), page);
        this.playerWorlds.put(player.getUniqueId(), world.getName());
        player.openInventory(inv);
    }

    private ItemStack createGameRuleItem(World world, GameRule<?> rule) {
        boolean isDefault = this.worldDataManager.isGameRuleDefault(world, rule);
        Object currentValue = world.getGameRuleValue(rule);
        Object defaultValue = this.worldDataManager.getDefaultGameRuleValue(rule);
        Material material = isDefault ? Material.GRAY_DYE : Material.LIME_DYE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + rule.getName());
            ArrayList<String> lore = new ArrayList<String>();
            String valueColor = isDefault ? ChatColor.GRAY.toString() : ChatColor.GREEN.toString();
            lore.add(ChatColor.GRAY + "De\u011fer: " + valueColor + currentValue);
            lore.add(ChatColor.GRAY + "Varsay\u0131lan: " + ChatColor.WHITE + defaultValue);
            if (isDefault) {
                lore.add("");
                lore.add(ChatColor.GRAY + this.plugin.getLanguageManager().getMessage("worldmanager.settings-default"));
            } else {
                lore.add("");
                lore.add(ChatColor.GREEN + this.plugin.getLanguageManager().getMessage("worldmanager.settings-modified"));
            }
            lore.add("");
            if (rule.getType() == Boolean.class) {
                lore.add(ChatColor.YELLOW + "Sol T\u0131k: " + ChatColor.WHITE + "De\u011fi\u015ftir (A\u00e7/Kapa)");
            } else {
                lore.add(ChatColor.YELLOW + "Sol T\u0131k: " + ChatColor.WHITE + "+1");
                lore.add(ChatColor.YELLOW + "Sa\u011f T\u0131k: " + ChatColor.WHITE + "-1");
                lore.add(ChatColor.YELLOW + "Shift+Sol: " + ChatColor.WHITE + "+10");
                lore.add(ChatColor.YELLOW + "Shift+Sa\u011f: " + ChatColor.WHITE + "-10");
            }
            lore.add(ChatColor.RED + "Orta T\u0131k: " + ChatColor.WHITE + "Varsay\u0131lana S\u0131f\u0131rla");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void addNavigationButtons(Inventory inv, int currentPage, int totalPages, World world) {
        ItemStack close;
        ItemMeta closeMeta;
        ItemStack info;
        ItemMeta infoMeta;
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        int i = 45;
        while (i < 54) {
            inv.setItem(i, filler);
            ++i;
        }
        if (currentPage > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName(ChatColor.GOLD + "\u00ab " + this.plugin.getLanguageManager().getMessage("worldmanager.settings-prev"));
                prevMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Sayfa " + currentPage + "/" + totalPages));
                prev.setItemMeta(prevMeta);
            }
            inv.setItem(48, prev);
        }
        if ((infoMeta = (info = new ItemStack(Material.GRASS_BLOCK)).getItemMeta()) != null) {
            infoMeta.setDisplayName(ChatColor.GREEN + world.getName());
            ArrayList<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + "Sayfa: " + ChatColor.WHITE + (currentPage + 1) + "/" + totalPages);
            lore.add(ChatColor.GRAY + "Ortam: " + ChatColor.WHITE + world.getEnvironment().name());
            lore.add(ChatColor.GRAY + "Oyuncu: " + ChatColor.WHITE + world.getPlayers().size());
            infoMeta.setLore(lore);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(49, info);
        if (currentPage < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName(ChatColor.GOLD + this.plugin.getLanguageManager().getMessage("worldmanager.settings-next") + " \u00bb");
                nextMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Sayfa " + (currentPage + 2) + "/" + totalPages));
                next.setItemMeta(nextMeta);
            }
            inv.setItem(50, next);
        }
        if ((closeMeta = (close = new ItemStack(Material.BARRIER)).getItemMeta()) != null) {
            closeMeta.setDisplayName(ChatColor.RED + this.plugin.getLanguageManager().getMessage("worldmanager.settings-close"));
            close.setItemMeta(closeMeta);
        }
        inv.setItem(53, close);
    }

    public void handleClick(Player player, int slot, boolean leftClick, boolean shiftClick, boolean middleClick) {
        String worldName = this.playerWorlds.get(player.getUniqueId());
        Integer page = this.playerPages.get(player.getUniqueId());
        if (worldName == null || page == null) {
            return;
        }
        World world = Bukkit.getWorld((String)worldName);
        if (world == null) {
            player.closeInventory();
            return;
        }
        if (slot >= 45) {
            if (slot == 53) {
                player.closeInventory();
                return;
            }
            if (slot == 49) {
                return;
            }
            ArrayList<GameRule> rulesForNav = new ArrayList<GameRule>();
            GameRule[] gameRuleArray = GameRule.values();
            int n = gameRuleArray.length;
            int n2 = 0;
            while (n2 < n) {
                GameRule r = gameRuleArray[n2];
                try {
                    world.getGameRuleValue(r);
                    rulesForNav.add(r);
                }
                catch (IllegalArgumentException illegalArgumentException) {

                }
                ++n2;
            }
            int totalPagesNav = (int)Math.ceil((double)rulesForNav.size() / 45.0);
            if (totalPagesNav == 0) {
                totalPagesNav = 1;
            }
            if (slot == 48 && page > 0) {
                this.openGameRuleMenu(player, world, page - 1);
                return;
            }
            if (slot == 50 && page < totalPagesNav - 1) {
                this.openGameRuleMenu(player, world, page + 1);
                return;
            }
            return;
        }
        ArrayList<GameRule> rules = new ArrayList<GameRule>();
        GameRule[] gameRuleArray = GameRule.values();
        int n = gameRuleArray.length;
        int n3 = 0;
        while (n3 < n) {
            GameRule rule = gameRuleArray[n3];
            try {
                world.getGameRuleValue(rule);
                rules.add(rule);
            }
            catch (IllegalArgumentException illegalArgumentException) {

            }
            ++n3;
        }
        rules.sort(Comparator.comparing(GameRule::getName));
        if (slot >= 0 && slot < 45) {
            int ruleIndex = page * 45 + slot;
            if (ruleIndex >= rules.size()) {
                return;
            }
            GameRule rule = (GameRule)rules.get(ruleIndex);
            if (middleClick) {
                this.worldDataManager.resetGameRule(world, rule);
                Main.sendMessage(this.plugin, (CommandSender)player, this.plugin.getLanguageManager().getMessage("worldmanager.gamerule-reset").replace("%gamerule%", rule.getName()));
                this.openGameRuleMenu(player, world, page);
                return;
            }
            if (rule.getType() == Boolean.class) {
                GameRule<Boolean> boolRule = (GameRule<Boolean>) rule;
                Boolean currentBool = world.getGameRuleValue(boolRule);
                if (currentBool != null) {
                    boolean newValue = !currentBool;
                    this.worldDataManager.setGameRule(world, boolRule, newValue);
                    Main.sendMessage(this.plugin, (CommandSender)player, this.plugin.getLanguageManager().getMessage("worldmanager.gamerule-changed").replace("%gamerule%", rule.getName()).replace("%value%", String.valueOf(newValue)));
                }
            } else if (rule.getType() == Integer.class) {
                GameRule<Integer> intRule = (GameRule<Integer>) rule;
                Integer currentInt = world.getGameRuleValue(intRule);
                if (currentInt != null) {
                    int change = shiftClick ? 10 : 1;
                    if (!leftClick) {
                        change = -change;
                    }
                    int newValue = currentInt + change;
                    if (newValue < 0) {
                        newValue = 0;
                    }
                    if (newValue > 99999) {
                        newValue = 99999;
                    }
                    this.worldDataManager.setGameRule(world, intRule, newValue);
                    Main.sendMessage(this.plugin, (CommandSender)player, this.plugin.getLanguageManager().getMessage("worldmanager.gamerule-changed").replace("%gamerule%", rule.getName()).replace("%value%", String.valueOf(newValue)));
                }
            }
            this.openGameRuleMenu(player, world, page);
        }
    }

    public void handleClose(Player player) {
        this.playerPages.remove(player.getUniqueId());
        this.playerWorlds.remove(player.getUniqueId());
    }

    public String getPlayerWorld(UUID playerId) {
        return this.playerWorlds.get(playerId);
    }

    public boolean hasMenuOpen(UUID playerId) {
        return this.playerWorlds.containsKey(playerId);
    }
}
