package org.widnees.widCore.command;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.TextParser;

import java.util.Collections;

public class FireballStickCommand implements CommandExecutor {

    private final Main plugin;

    public FireballStickCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }
        if (!(sender instanceof Player)) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("widcore.fireball")) {
            Main.sendNoPermission(this.plugin, player, "widcore.fireball");
            return true;
        }

        if (player.getInventory().firstEmpty() == -1) {
            Main.sendMessage(this.plugin, player,
                    plugin.getLanguageManager().getMessage("fireballstick.inventory-full"));
            return true;
        }

        float power = 1.0F;
        if (args.length > 0) {
            try {
                power = Float.parseFloat(args[0]);
                if (power <= 0) {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("fireballstick.invalid-power"));
                    return true;
                }
                power = Math.min(power, 100);
            } catch (NumberFormatException e) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("general.invalid-number"));
                return true;
            }
        }

        ItemStack fireballStick = new ItemStack(Material.STICK);
        ItemMeta meta = fireballStick.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextParser.colorize(plugin.getLanguageManager().getMessage("fireballstick.name")));
            String lore = plugin.getLanguageManager().getMessage("fireballstick.lore").replace("%power%",
                    String.valueOf(power));
            meta.setLore(Collections.singletonList(TextParser.colorize(lore)));

            NamespacedKey key = new NamespacedKey(plugin, "fireball_power");
            meta.getPersistentDataContainer().set(key, PersistentDataType.FLOAT, power);

            NamespacedKey ownerKey = new NamespacedKey(plugin, "fireball_owner");
            meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());

            fireballStick.setItemMeta(meta);
        }

        player.getInventory().addItem(fireballStick);
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("fireballstick.success"));

        return true;
    }
}