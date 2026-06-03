package org.widnees.widCore.command;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

import java.util.HashMap;

public class ItemCommand implements CommandExecutor {

    private final Main plugin;

    public ItemCommand(Main plugin) {
        this.plugin = plugin;
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

        if (!player.hasPermission("widcore.i")) {
            Main.sendNoPermission(this.plugin, player, "widcore.i");
            return true;
        }

        if (args.length == 0) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("item.usage"));
            return true;
        }

        String itemNameInput = args[0];
        Material material = Material.matchMaterial(itemNameInput);

        if (material == null) {
            Main.sendMessage(this.plugin, player,
                    plugin.getLanguageManager().getMessage("item.not-found").replace("%item%", args[0]));
            return true;
        }

        if (material.isAir() || !material.isItem()) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("item.invalid"));
            return true;
        }

        int amount = material.getMaxStackSize();

        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount <= 0) {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("item.invalid-amount"));
                    return true;
                }
            } catch (NumberFormatException e) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("general.invalid-number"));
                return true;
            }
        }

        ItemStack itemStack = new ItemStack(material, amount);
        PlayerInventory inventory = player.getInventory();
        HashMap<Integer, ItemStack> leftover = inventory.addItem(itemStack);

        if (!leftover.isEmpty()) {
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("item.inventory-full"));
        }

        String itemName = material.name().toLowerCase().replace("_", " ");
        String msg = plugin.getLanguageManager().getMessage("item.success")
                .replace("%amount%", String.valueOf(amount))
                .replace("%item%", itemName);
        Main.sendMessage(this.plugin, player, msg);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

        return true;
    }
        @SuppressWarnings("unused")
    private static final String _xCr7w3n = "\u0077\u0069\u0064\u006e" + "\u0065\u0065\u0073";

}