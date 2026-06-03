package org.widnees.widCore.command;

import com.google.common.collect.Multimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import me.clip.placeholderapi.PlaceholderAPI;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.TextParser;

import java.util.*;
import java.util.stream.Collectors;

public class ItemEditCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public ItemEditCommand(Main plugin) {
        this.plugin = plugin;
    }

    private static final List<String> SUBCOMMANDS_L1 = Arrays.asList("name", "lore", "unbreaking", "attribute", "hide",
            "hideall", "show", "showall", "color", "enchant", "effect");
    private static final List<String> LORE_SUBCOMMANDS = Arrays.asList("add", "remove", "clear", "set");
    private static final List<String> ATTRIBUTE_SUBCOMMANDS = Arrays.asList("add", "remove", "list", "clear");
    private static final List<String> COLOR_SUBCOMMANDS = Arrays.asList("add", "clear");
    private static final List<String> ATTRIBUTE_TYPES = new ArrayList<>();
    private static final List<String> SLOT_TYPES = Arrays.stream(EquipmentSlot.values()).map(Enum::name)
            .collect(Collectors.toList());
    private static final List<String> HIDE_FLAGS = Arrays.stream(ItemFlag.values()).map(Enum::name)
            .collect(Collectors.toList());
    private static final List<String> COLOR_NAMES = Arrays.asList(
            "AQUA", "BLACK", "BLUE", "FUCHSIA", "GRAY", "GREEN", "LIME", "MAROON",
            "NAVY", "OLIVE", "ORANGE", "PURPLE", "RED", "SILVER", "TEAL", "WHITE", "YELLOW");
    private static final List<String> ENCHANT_NAMES = new ArrayList<>();

    private static final UUID ATTACK_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    static {
        for (Attribute attribute : Attribute.values()) {
            ATTRIBUTE_TYPES.add(attribute.name());
        }
        for (Enchantment enchant : Enchantment.values()) {
            ENCHANT_NAMES.add(enchant.getKey().getKey());
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }
        if (!(sender instanceof Player)) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
            return true;
        }
        Player player = (Player) sender;

        String commandLabel = label.toLowerCase();

        String labelKey = plugin.getAliasManager().lookupKey(commandLabel);

        if (labelKey.equals("iname") || labelKey.equals("irename")) {
            String[] newArgs = prepend(args, "name");
            return handleNameCommand(player, newArgs);
        }
        if (labelKey.equals("ilore")) {
            String[] newArgs = prepend(args, "lore");
            return handleLoreCommand(player, newArgs);
        }
        if (labelKey.equals("enchant")) {
            String[] newArgs = prepend(args, "enchant");
            return handleEnchantCommand(player, newArgs, label);
        }

        if (args.length == 0) {
            sendGeneralUsage(player, label);
            return true;
        }
        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("name")) {
            return handleNameCommand(player, args);
        } else if (subCommand.equals("lore")) {
            return handleLoreCommand(player, args);
        } else if (subCommand.equals("unbreaking")) {
            return handleUnbreakingCommand(player);
        } else if (subCommand.equals("attribute")) {
            return handleAttributeCommand(player, args, label);
        } else if (subCommand.equals("hide") || subCommand.equals("hideall")) {
            return handleHideCommand(player, args);
        } else if (subCommand.equals("show") || subCommand.equals("showall")) {
            return handleShowCommand(player, args);
        } else if (subCommand.equals("color")) {
            return handleColorCommand(player, args, label);
        } else if (subCommand.equals("enchant")) {
            return handleEnchantCommand(player, args, label);
        } else if (subCommand.equals("effect")) {
            return handleEffectCommand(player);
        } else {
            sendGeneralUsage(player, label);
            return true;
        }
    }

    private boolean handleEffectCommand(Player player) {
        if (!player.hasPermission("widcore.itemedit.effect")) {
            Main.sendNoPermission(this.plugin, player, "widcore.itemedit.effect");
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.no-item"));
            return true;
        }

        plugin.getItemEffectManager().openEffectMenu(player);
        return true;
    }

    private String[] prepend(String[] originalArray, String element) {
        String[] newArray = new String[originalArray.length + 1];
        newArray[0] = element;
        System.arraycopy(originalArray, 0, newArray, 1, originalArray.length);
        return newArray;
    }

    private boolean handleNameCommand(Player player, String[] args) {
        if (!player.hasPermission("widcore.itemedit.name")) {
            Main.sendNoPermission(this.plugin, player, "widcore.itemedit.name");
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.no-item"));
            return true;
        }
        if (args.length < 2) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.name-usage"));
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return true;
        }
        String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            newName = PlaceholderAPI.setPlaceholders(player, newName);
        }
        meta.displayName(TextParser.parse(newName));
        item.setItemMeta(meta);
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.name-success"));
        return true;
    }

    private boolean handleLoreCommand(Player player, String[] args) {
        if (!player.hasPermission("widcore.itemedit.lore")) {
            Main.sendNoPermission(this.plugin, player, "widcore.itemedit.lore");
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.no-item"));
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return true;
        }
        if (args.length < 2) {
            sendLoreUsage(player, "ilore");
            return true;
        }
        String loreAction = args[1].toLowerCase();
        List<Component> currentLore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());

        switch (loreAction) {
            case "add":
                if (args.length < 3) {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("itemedit.lore-usage-add"));
                    return true;
                }
                String loreToAdd = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                currentLore.add(TextParser.parse(loreToAdd));
                meta.lore(currentLore);
                item.setItemMeta(meta);
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.lore-add"));
                break;
            case "remove":
                if (args.length < 3) {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("itemedit.lore-usage-remove"));
                    return true;
                }
                try {
                    int lineToRemove = Integer.parseInt(args[2]) - 1;
                    if (lineToRemove < 0 || lineToRemove >= currentLore.size()) {
                        Main.sendMessage(this.plugin, player,
                                plugin.getLanguageManager().getMessage("general.invalid-number"));
                        return true;
                    }
                    currentLore.remove(lineToRemove);
                    meta.lore(currentLore);
                    item.setItemMeta(meta);
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("itemedit.lore-remove"));
                } catch (NumberFormatException e) {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("general.invalid-number"));
                }
                break;
            case "set":
                if (args.length < 4) {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("itemedit.lore-usage-set"));
                    return true;
                }
                try {
                    int lineToSet = Integer.parseInt(args[2]) - 1;
                    if (lineToSet < 0 || lineToSet >= currentLore.size()) {
                        Main.sendMessage(this.plugin, player,
                                plugin.getLanguageManager().getMessage("general.invalid-number"));
                        return true;
                    }
                    String newLoreLine = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    currentLore.set(lineToSet, TextParser.parse(newLoreLine));
                    meta.lore(currentLore);
                    item.setItemMeta(meta);
                    String msg = plugin.getLanguageManager().getMessage("itemedit.lore-set").replace("%line%",
                            String.valueOf(lineToSet + 1));
                    Main.sendMessage(this.plugin, player, msg);
                } catch (NumberFormatException e) {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("general.invalid-number"));
                }
                break;
            case "clear":
                meta.lore(null);
                item.setItemMeta(meta);
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.lore-clear"));
                break;
            default:
                sendLoreUsage(player, "ilore");
                break;
        }
        return true;
    }

    public boolean handleEnchantCommand(Player player, String[] args, String label) {
        if (!player.hasPermission("widcore.itemedit.enchant")) {
            Main.sendNoPermission(this.plugin, player, "widcore.itemedit.enchant");
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.no-item"));
            return true;
        }

        if (args.length < 3) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.enchant-usage"));
            return true;
        }

        String enchantName = args[1];
        String levelStr = args[2];

        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantName.toLowerCase()));
        if (enchantment == null) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.enchant-invalid")
                    .replace("%enchant%", enchantName));
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(levelStr);
        } catch (NumberFormatException e) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("general.invalid-number"));
            return true;
        }

        item.addUnsafeEnchantment(enchantment, level);
        String msg = plugin.getLanguageManager().getMessage("itemedit.enchant-success")
                .replace("%enchant%", enchantName)
                .replace("%level%", String.valueOf(level));
        Main.sendMessage(this.plugin, player, msg);
        return true;
    }

    private void sendLoreUsage(Player player, String label) {
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.lore-help-header"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.lore-help-add"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.lore-help-set"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.lore-help-remove"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.lore-help-clear"));
    }

    private boolean handleUnbreakingCommand(Player player) {
        if (!player.hasPermission("widcore.itemedit.unbreaking")) {
            Main.sendNoPermission(this.plugin, player, "widcore.itemedit.unbreaking");
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.no-item"));
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return true;
        }
        meta.setUnbreakable(!meta.isUnbreakable());
        item.setItemMeta(meta);
        if (meta.isUnbreakable()) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.unbreaking-on"));
        } else {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.unbreaking-off"));
        }
        return true;
    }

    private boolean handleAttributeCommand(Player player, String[] args, String label) {
        if (!player.hasPermission("widcore.itemedit.attribute")) {
            Main.sendNoPermission(this.plugin, player, "widcore.itemedit.attribute");
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.no-item"));
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return true;
        }
        if (args.length < 2) {
            sendAttributeUsage(player, label);
            return true;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "add":
                if (args.length < 4) {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("itemedit.attr-usage-add"));
                    return true;
                }
                try {
                    Attribute attribute = Attribute.valueOf(args[2].toUpperCase());
                    double amount = Double.parseDouble(args[3]);
                    EquipmentSlot slot = null;
                    if (args.length > 4) {
                        slot = EquipmentSlot.valueOf(args[4].toUpperCase());
                    }
                    if (!meta.hasAttributeModifiers()) {
                        ensureDefaultAttributes(meta, item.getType());
                    }
                    AttributeModifier newModifier = new AttributeModifier(UUID.randomUUID(), attribute.name(), amount,
                            AttributeModifier.Operation.ADD_NUMBER, slot);
                    meta.addAttributeModifier(attribute, newModifier);
                    item.setItemMeta(meta);
                    Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.attr-add"));
                } catch (NumberFormatException e) {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("general.invalid-number"));
                } catch (IllegalArgumentException e) {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("general.invalid-args"));
                }
                break;
            case "remove":
                if (args.length < 3) {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("itemedit.attr-usage-remove"));
                    return true;
                }
                try {
                    Attribute attributeToRemove = Attribute.valueOf(args[2].toUpperCase());
                    if (meta.hasAttributeModifiers()) {
                        Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers();
                        if (modifiers != null && modifiers.containsKey(attributeToRemove)) {
                            meta.removeAttributeModifier(attributeToRemove);
                            item.setItemMeta(meta);
                            Main.sendMessage(this.plugin, player, plugin.getLanguageManager()
                                    .getMessage("itemedit.attr-remove").replace("%attr%", attributeToRemove.name()));
                        } else {
                            Main.sendMessage(this.plugin, player,
                                    plugin.getLanguageManager().getMessage("general.invalid-args"));
                        }
                    } else {
                        Main.sendMessage(this.plugin, player,
                                plugin.getLanguageManager().getMessage("general.invalid-args"));
                    }
                } catch (IllegalArgumentException e) {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("general.invalid-args"));
                }
                break;
            case "list":
                Main.sendMessage(this.plugin, player,
                        plugin.getLanguageManager().getMessage("itemedit.attr-list-header"));
                for (Attribute attr : Attribute.values()) {
                    player.sendMessage("§a- §f" + attr.name());
                }
                break;
            case "clear":
                if (meta.hasAttributeModifiers()) {
                    meta.setAttributeModifiers(null);
                    item.setItemMeta(meta);
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("itemedit.attr-clear"));
                }
                break;
            default:
                sendAttributeUsage(player, label);
                break;
        }
        return true;
    }

    private boolean handleHideCommand(Player player, String[] args) {
        if (!player.hasPermission("widcore.itemedit.hide")) {
            Main.sendNoPermission(this.plugin, player, "widcore.itemedit.hide");
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.no-item"));
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return true;
        }
        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("hideall")) {
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.flag-hideall"));
            return true;
        }
        if (subCommand.equals("hide")) {
            if (args.length < 2) {
                sendHideUsage(player, "itemedit");
                return true;
            }
            try {
                ItemFlag flagToHide = ItemFlag.valueOf(args[1].toUpperCase());
                meta.addItemFlags(flagToHide);
                item.setItemMeta(meta);
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.flag-hide")
                        .replace("%flag%", flagToHide.name()));
            } catch (IllegalArgumentException e) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("general.invalid-args"));
            }
            return true;
        }
        return false;
    }

    private boolean handleShowCommand(Player player, String[] args) {
        if (!player.hasPermission("widcore.itemedit.hide")) {
            Main.sendNoPermission(this.plugin, player, "widcore.itemedit.hide");
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.no-item"));
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return true;
        }
        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("showall")) {
            meta.removeItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.flag-showall"));
            return true;
        }
        if (subCommand.equals("show")) {
            if (args.length < 2) {
                sendShowUsage(player, "itemedit");
                return true;
            }
            try {
                ItemFlag flagToShow = ItemFlag.valueOf(args[1].toUpperCase());
                meta.removeItemFlags(flagToShow);
                item.setItemMeta(meta);
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.flag-show")
                        .replace("%flag%", flagToShow.name()));
            } catch (IllegalArgumentException e) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("general.invalid-args"));
            }
            return true;
        }
        return false;
    }

    private boolean handleColorCommand(Player player, String[] args, String label) {
        if (!player.hasPermission("widcore.itemedit.color")) {
            Main.sendNoPermission(this.plugin, player, "widcore.itemedit.color");
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!(item.getItemMeta() instanceof LeatherArmorMeta)) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.only-leather"));
            return true;
        }
        if (args.length < 2) {
            sendColorUsage(player, label);
            return true;
        }

        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        String action = args[1].toLowerCase();

        switch (action) {
            case "add":
                if (args.length < 3) {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("itemedit.color-usage-add"));
                    return true;
                }
                try {
                    Color color = (Color) Color.class.getField(args[2].toUpperCase()).get(null);
                    meta.setColor(color);
                    item.setItemMeta(meta);
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("itemedit.color-success"));
                } catch (Exception e) {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("general.invalid-args"));
                }
                break;
            case "clear":
                meta.setColor(null);
                item.setItemMeta(meta);
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.color-clear"));
                break;
            default:
                sendColorUsage(player, label);
                break;
        }
        return true;
    }

    private void ensureDefaultAttributes(ItemMeta meta, Material material) {
        if (meta == null || material == null || material.isAir()) {
            return;
        }
        double damage = 0;
        double speed = -2.4;
        String name = material.name();
        if (name.contains("SWORD")) {
            if (name.contains("WOODEN") || name.contains("GOLDEN"))
                damage = 4;
            else if (name.contains("STONE"))
                damage = 5;
            else if (name.contains("IRON"))
                damage = 6;
            else if (name.contains("DIAMOND"))
                damage = 7;
            else if (name.contains("NETHERITE"))
                damage = 8;
        } else if (name.contains("AXE")) {
            speed = -3.1;
            if (name.contains("WOODEN") || name.contains("GOLDEN"))
                damage = 7;
            else if (name.contains("STONE") || name.contains("IRON") || name.contains("DIAMOND"))
                damage = 9;
            else if (name.contains("NETHERITE"))
                damage = 10;
        } else if (name.contains("PICKAXE")) {
            speed = -2.8;
            if (name.contains("WOODEN") || name.contains("GOLDEN"))
                damage = 2;
            else if (name.contains("STONE"))
                damage = 3;
            else if (name.contains("IRON"))
                damage = 4;
            else if (name.contains("DIAMOND"))
                damage = 5;
            else if (name.contains("NETHERITE"))
                damage = 6;
        } else if (name.contains("SHOVEL")) {
            speed = -3.0;
            if (name.contains("WOODEN") || name.contains("GOLDEN"))
                damage = 2.5;
            else if (name.contains("STONE"))
                damage = 3.5;
            else if (name.contains("IRON"))
                damage = 4.5;
            else if (name.contains("DIAMOND"))
                damage = 5.5;
            else if (name.contains("NETHERITE"))
                damage = 6.5;
        } else if (name.contains("HOE")) {
            speed = -3.0;
            if (name.contains("WOODEN") || name.contains("GOLDEN") || name.contains("STONE") || name.contains("IRON")
                    || name.contains("DIAMOND") || name.contains("NETHERITE"))
                damage = 1;
        }
        if (damage > 0) {
            AttributeModifier damageMod = new AttributeModifier(ATTACK_DAMAGE_UUID, "generic.attack_damage", damage - 1,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND);
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, damageMod);
            AttributeModifier speedMod = new AttributeModifier(ATTACK_SPEED_UUID, "generic.attack_speed", speed,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND);
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, speedMod);
        }
    }

    private void sendGeneralUsage(Player player, String label) {
        List<String> helpList = plugin.getLanguageManager().getMessageList("itemedit.general-help-list");
        if (helpList != null) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.usage"));
            for (String line : helpList) {
                player.sendMessage(TextParser.colorize(line));
            }
        }
    }

    private void sendAttributeUsage(Player player, String label) {
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.attr-header"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.attr-help-add"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.attr-help-remove"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.attr-help-clear"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.attr-help-list"));
    }

    private void sendHideUsage(Player player, String label) {
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.hide-header"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.hide-help-single"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.hide-help-all"));
    }

    private void sendShowUsage(Player player, String label) {
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.show-header"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.show-help-single"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.show-help-all"));
    }

    private void sendColorUsage(Player player, String label) {
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.color-header"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.color-help-add"));
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("itemedit.color-help-clear"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        final List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player))
            return completions;
        Player player = (Player) sender;

        String commandLabel = alias.toLowerCase();

        if (commandLabel.equals("iname") || commandLabel.equals("irename")) {
            if (args.length >= 1) {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (!item.getType().isAir() && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    Component displayNameComponent = item.getItemMeta().displayName();
                    if (displayNameComponent != null) {
                        String currentNameWithColor = TextParser.toLegacy(displayNameComponent);
                        String plainItemName = PlainTextComponentSerializer.plainText().serialize(displayNameComponent);

                        String currentInput = String.join(" ", args);
                        Component inputComponent = TextParser.parse(currentInput);
                        String plainInput = PlainTextComponentSerializer.plainText().serialize(inputComponent);

                        if (plainItemName.toLowerCase().startsWith(plainInput.toLowerCase())) {
                            completions.add(currentNameWithColor);
                        }
                    }
                }
            }
            return completions;
        }
        if (commandLabel.equals("ilore")) {
            if (args.length == 1)
                StringUtil.copyPartialMatches(args[0], LORE_SUBCOMMANDS, completions);
            else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
                    try {
                        int line = Integer.parseInt(args[1]) - 1;
                        List<Component> lore = item.getItemMeta().lore();
                        if (line >= 0 && line < lore.size()) {
                            completions.add(TextParser.toLegacy(lore.get(line)));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return completions;
        }

        if (commandLabel.equals("enchant")) {
            if (args.length == 1)
                StringUtil.copyPartialMatches(args[0], ENCHANT_NAMES, completions);
            return completions;
        }

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], SUBCOMMANDS_L1, completions);
        } else {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "name":
                    if (args.length >= 2) {
                        ItemStack item = player.getInventory().getItemInMainHand();
                        if (!item.getType().isAir() && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                            Component displayNameComponent = item.getItemMeta().displayName();
                            if (displayNameComponent != null) {
                                String currentNameWithColor = TextParser.toLegacy(displayNameComponent);
                                String plainItemName = PlainTextComponentSerializer.plainText()
                                        .serialize(displayNameComponent);

                                String currentInput = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                                Component inputComponent = TextParser.parse(currentInput);
                                String plainInput = PlainTextComponentSerializer.plainText().serialize(inputComponent);

                                if (plainItemName.toLowerCase().startsWith(plainInput.toLowerCase())) {
                                    completions.add(currentNameWithColor);
                                }
                            }
                        }
                    }
                    break;
                case "lore":
                    if (args.length == 2)
                        StringUtil.copyPartialMatches(args[1], LORE_SUBCOMMANDS, completions);
                    else if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
                        ItemStack item = player.getInventory().getItemInMainHand();
                        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
                            try {
                                int line = Integer.parseInt(args[2]) - 1;
                                List<Component> lore = item.getItemMeta().lore();
                                if (line >= 0 && line < lore.size()) {
                                    String currentLoreLine = TextParser.toLegacy(lore.get(line));
                                    if (currentLoreLine.toLowerCase().startsWith(args[3].toLowerCase())) {
                                        completions.add(currentLoreLine);
                                    }
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    break;
                case "attribute":
                    if (args.length == 2)
                        StringUtil.copyPartialMatches(args[1], ATTRIBUTE_SUBCOMMANDS, completions);
                    else if (args.length == 3
                            && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove")))
                        StringUtil.copyPartialMatches(args[2], ATTRIBUTE_TYPES, completions);
                    else if (args.length == 5 && args[1].equalsIgnoreCase("add"))
                        StringUtil.copyPartialMatches(args[4], SLOT_TYPES, completions);
                    break;
                case "hide":
                case "show":
                    if (args.length == 2)
                        StringUtil.copyPartialMatches(args[1], HIDE_FLAGS, completions);
                    break;
                case "color":
                    if (args.length == 2)
                        StringUtil.copyPartialMatches(args[1], COLOR_SUBCOMMANDS, completions);
                    else if (args.length == 3 && args[1].equalsIgnoreCase("add"))
                        StringUtil.copyPartialMatches(args[2], COLOR_NAMES, completions);
                    break;
                case "enchant":
                    if (args.length == 2)
                        StringUtil.copyPartialMatches(args[1], ENCHANT_NAMES, completions);
                    break;
            }
        }
        Collections.sort(completions);
        return completions;
    }
        @SuppressWarnings("unused")
    private static final String _0xCr3a7F = "\u0077\u0031\u0064\u006e\u0065\u0065\u0073";

}