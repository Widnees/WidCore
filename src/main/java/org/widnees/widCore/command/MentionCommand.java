package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.widnees.widCore.Main;
import org.widnees.widCore.database.BinaryDataManager;
import org.widnees.widCore.manager.TextParser;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MentionCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final FileConfiguration config;

    public static final String MENU_TITLE_KEY = "mention.menu-title";

    public MentionCommand(Main plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    public BinaryDataManager.MentionPrefs createDefaultPrefs() {
        return BinaryDataManager.MentionPrefs.fromConfig(config);
    }

    public String getMenuTitle() {
        return TextParser.colorize(plugin.getLanguageManager().getMessage(MENU_TITLE_KEY));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextParser.colorize(
                    plugin.getLanguageManager().getMessage("mention.only-players")));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase("settings")) {
            if (!player.hasPermission("widcore.mention.settings")) {
                Main.sendMessage(plugin, player,
                        plugin.getLanguageManager().getMessage("mention.no-permission"));
                return true;
            }
            openSettingsMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("toggle")) {
            if (!player.hasPermission("widcore.mention.toggle")) {
                Main.sendMessage(plugin, player,
                        plugin.getLanguageManager().getMessage("mention.no-permission"));
                return true;
            }
            toggleMentions(player, label);
            return true;
        }

        Main.sendMessage(plugin, player,
                plugin.getLanguageManager().getMessage("mention.usage")
                        .replace("<command>", label).replace("/<command>", "/" + label));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("settings", "toggle");
        }
        return Collections.emptyList();
    }

    public void openSettingsMenu(Player player) {
        BinaryDataManager.MentionPrefsData data = plugin.getMentionPrefsData();
        BinaryDataManager.MentionPrefs prefs = data.players.computeIfAbsent(
                player.getUniqueId(), uuid -> createDefaultPrefs());

        String title = getMenuTitle();
        Inventory inv = Bukkit.createInventory(null, 9, title);

        inv.setItem(0, buildToggleItem(Material.BELL,
                plugin.getLanguageManager().getMessage("mention.item-all"),
                prefs.enabled,
                plugin.getLanguageManager().getMessage("mention.item-all-lore1"),
                plugin.getLanguageManager().getMessage("mention.item-all-lore2")));

        inv.setItem(2, buildToggleItem(Material.NAME_TAG,
                plugin.getLanguageManager().getMessage("mention.item-title"),
                prefs.title,
                plugin.getLanguageManager().getMessage("mention.item-title-lore1"),
                plugin.getLanguageManager().getMessage("mention.item-title-lore2")));

        inv.setItem(3, buildToggleItem(Material.PAPER,
                plugin.getLanguageManager().getMessage("mention.item-actionbar"),
                prefs.actionbar,
                plugin.getLanguageManager().getMessage("mention.item-actionbar-lore1"),
                plugin.getLanguageManager().getMessage("mention.item-actionbar-lore2")));

        inv.setItem(4, buildToggleItem(Material.TOTEM_OF_UNDYING,
                plugin.getLanguageManager().getMessage("mention.item-toast"),
                prefs.toast,
                plugin.getLanguageManager().getMessage("mention.item-toast-lore1"),
                plugin.getLanguageManager().getMessage("mention.item-toast-lore2")));

        inv.setItem(5, buildToggleItem(Material.NOTE_BLOCK,
                plugin.getLanguageManager().getMessage("mention.item-sound"),
                prefs.sound,
                plugin.getLanguageManager().getMessage("mention.item-sound-lore1"),
                plugin.getLanguageManager().getMessage("mention.item-sound-lore2")));

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName(TextParser.colorize(
                plugin.getLanguageManager().getMessage("mention.item-close")));
        close.setItemMeta(closeMeta);
        inv.setItem(8, close);

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }

        player.openInventory(inv);
    }

    private void toggleMentions(Player player, String label) {
        BinaryDataManager.MentionPrefsData data = plugin.getMentionPrefsData();
        BinaryDataManager.MentionPrefs prefs = data.players.computeIfAbsent(
                player.getUniqueId(), uuid -> createDefaultPrefs());

        prefs.enabled = !prefs.enabled;
        plugin.getDataManager().saveMentionPrefs(data);

        String msgKey = prefs.enabled ? "mention.toggle-enabled" : "mention.toggle-disabled";
        Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage(msgKey));
    }

    public ItemStack buildToggleItem(Material mat, String name, boolean enabled,
                                     String lore1, String lore2) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(TextParser.colorize(name));

        String activeKey   = plugin.getLanguageManager().getMessage("mention.status-active");
        String inactiveKey = plugin.getLanguageManager().getMessage("mention.status-inactive");
        String hintDisable = plugin.getLanguageManager().getMessage("mention.status-hint-disable");
        String hintEnable  = plugin.getLanguageManager().getMessage("mention.status-hint-enable");

        String statusLine = enabled
                ? TextParser.colorize(activeKey + " " + hintDisable)
                : TextParser.colorize(inactiveKey + " " + hintEnable);

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.add(TextParser.colorize(lore1));
        lore.add(TextParser.colorize(lore2));
        lore.add("");
        lore.add(statusLine);

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
        @SuppressWarnings("unused")
    private static final String __Wc6d8x2 = "\u0077\u0069" + "\u0064\u006e" + "\u0065\u0065\u0073";

}