package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.DisguiseManager;

import java.util.*;
import java.util.stream.Collectors;

public class DisguiseCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final DisguiseManager disguiseManager;

    private static List<String> entityNames;
    private static List<String> itemNames;

    public DisguiseCommand(Main plugin) {
        this.plugin = plugin;
        this.disguiseManager = plugin.getDisguiseManager();
        buildCompletionLists();
    }

    private void buildCompletionLists() {
        entityNames = DisguiseManager.getSupportedEntities().stream()
                .map(e -> e.name().toLowerCase())
                .sorted()
                .collect(Collectors.toList());

        itemNames = DisguiseManager.getSupportedItems().stream()
                .map(m -> m.name().toLowerCase())
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) return true;

        if (!sender.hasPermission("widcore.disguise")) {
            Main.sendNoPermission(plugin, sender, "widcore.disguise");
            return true;
        }

        if (args.length < 1) {
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("disguise.usage"));
            return true;
        }

        String subCommand = resolveSubCommand(args[0].toLowerCase());

        if (args.length < 2) {
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("disguise.usage"));
            return true;
        }

        Player target;
        if (args.length >= 3) {
            if (!sender.hasPermission("widcore.disguise.other")) {
                Main.sendMessage(plugin, sender,
                        plugin.getLanguageManager().getMessage("disguise.no-perm-other"));
                return true;
            }
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                Main.sendMessage(plugin, sender,
                        plugin.getLanguageManager().getMessage("general.player-not-found")
                                .replace("%player%", args[2]));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                Main.sendMessage(plugin, sender,
                        plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }
            target = (Player) sender;
        }

        switch (subCommand) {
            case "entity":
                return handleEntityDisguise(sender, target, args[1]);
            case "item":
                return handleItemDisguise(sender, target, args[1]);
            case "player":
                return handlePlayerDisguise(sender, target, args[1]);
            default:
                Main.sendMessage(plugin, sender,
                        plugin.getLanguageManager().getMessage("disguise.invalid-type"));
                return true;
        }
    }

    private boolean handleEntityDisguise(CommandSender sender, Player target, String entityName) {
        if (!sender.hasPermission("widcore.disguise.entity")) {
            Main.sendMessage(plugin, sender,
                    plugin.getLanguageManager().getMessage("disguise.no-perm-type"));
            return true;
        }

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entityName.toUpperCase());
        } catch (IllegalArgumentException e) {
            Main.sendMessage(plugin, sender,
                    plugin.getLanguageManager().getMessage("disguise.invalid-entity")
                            .replace("%entity%", entityName));
            return true;
        }

        boolean success = disguiseManager.disguiseAsEntity(target, entityType);
        if (!success) {
            Main.sendMessage(plugin, sender,
                    plugin.getLanguageManager().getMessage("disguise.invalid-entity")
                            .replace("%entity%", entityName));
            return true;
        }

        String typeName = plugin.getLanguageManager().getMessage("disguise.type-entity");
        sendSuccessMessage(sender, target, typeName, entityName.toLowerCase());
        return true;
    }

    private boolean handleItemDisguise(CommandSender sender, Player target, String itemName) {
        if (!sender.hasPermission("widcore.disguise.item")) {
            Main.sendMessage(plugin, sender,
                    plugin.getLanguageManager().getMessage("disguise.no-perm-type"));
            return true;
        }

        Material material;
        try {
            material = Material.valueOf(itemName.toUpperCase());
        } catch (IllegalArgumentException e) {
            Main.sendMessage(plugin, sender,
                    plugin.getLanguageManager().getMessage("disguise.invalid-item")
                            .replace("%item%", itemName));
            return true;
        }

        boolean success = disguiseManager.disguiseAsItem(target, material);
        if (!success) {
            Main.sendMessage(plugin, sender,
                    plugin.getLanguageManager().getMessage("disguise.invalid-item")
                            .replace("%item%", itemName));
            return true;
        }

        String typeName = plugin.getLanguageManager().getMessage("disguise.type-item");
        sendSuccessMessage(sender, target, typeName, itemName.toLowerCase());
        return true;
    }

    private boolean handlePlayerDisguise(CommandSender sender, Player target, String playerName) {
        if (!sender.hasPermission("widcore.disguise.player")) {
            Main.sendMessage(plugin, sender,
                    plugin.getLanguageManager().getMessage("disguise.no-perm-type"));
            return true;
        }

        Main.sendMessage(plugin, sender,
                plugin.getLanguageManager().getMessage("disguise.player-skin-loading"));

        disguiseManager.disguiseAsPlayer(target, playerName).thenAccept(success -> {
            if (success) {
                String typeName = plugin.getLanguageManager().getMessage("disguise.type-player");
                org.widnees.widCore.util.FoliaScheduler.runTask(plugin, () -> {
                    sendSuccessMessage(sender, target, typeName, playerName);
                });
            } else {
                org.widnees.widCore.util.FoliaScheduler.runTask(plugin, () -> {
                    Main.sendMessage(plugin, sender,
                            plugin.getLanguageManager().getMessage("disguise.player-skin-failed")
                                    .replace("%name%", playerName));
                });
            }
        });

        return true;
    }

    private void sendSuccessMessage(CommandSender sender, Player target, String typeName, String choiceName) {
        if (sender.equals(target)) {
            Main.sendMessage(plugin, sender,
                    plugin.getLanguageManager().getMessage("disguise.enabled")
                            .replace("%type%", typeName)
                            .replace("%name%", choiceName));
        } else {
            Main.sendMessage(plugin, sender,
                    plugin.getLanguageManager().getMessage("disguise.other-enabled")
                            .replace("%player%", target.getName())
                            .replace("%name%", choiceName));
        }
    }

    private String resolveSubCommand(String input) {
        List<String> entityAliases = plugin.getAliasManager().getSubcommandAliases("disguise", "entity");
        List<String> itemAliases = plugin.getAliasManager().getSubcommandAliases("disguise", "item");
        List<String> playerAliases = plugin.getAliasManager().getSubcommandAliases("disguise", "player");

        if (input.equals("entity") || entityAliases.stream().anyMatch(a -> a.equalsIgnoreCase(input))) return "entity";
        if (input.equals("item") || itemAliases.stream().anyMatch(a -> a.equalsIgnoreCase(input))) return "item";
        if (input.equals("player") || playerAliases.stream().anyMatch(a -> a.equalsIgnoreCase(input))) return "player";

        return input;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> options = Arrays.asList("entity", "item", "player");
            StringUtil.copyPartialMatches(args[0], options, completions);
        } else if (args.length == 2) {
            String sub = resolveSubCommand(args[0].toLowerCase());
            switch (sub) {
                case "entity":
                    StringUtil.copyPartialMatches(args[1], entityNames, completions);
                    break;
                case "item":
                    StringUtil.copyPartialMatches(args[1], itemNames, completions);
                    break;
                case "player":
                    List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList());
                    StringUtil.copyPartialMatches(args[1], playerNames, completions);
                    break;
            }
        } else if (args.length == 3) {

            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            StringUtil.copyPartialMatches(args[2], playerNames, completions);
        }

        Collections.sort(completions);
        return completions;
    }
        @SuppressWarnings("unused")
    private static final String _W3f0b7c = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}
