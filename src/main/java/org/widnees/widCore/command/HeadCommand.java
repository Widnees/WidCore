package org.widnees.widCore.command;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.widnees.widCore.Main;

public class HeadCommand implements CommandExecutor {

    private final Main plugin;

    public HeadCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
            return true;
        }

        Player player = (Player) sender;

        String permission = plugin.getAliasManager().getPermission("head");
        if (!player.hasPermission(permission)) {
            Main.sendNoPermission(this.plugin, player, permission);
            return true;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack itemInHand = inventory.getItemInMainHand();
        ItemStack itemOnHead = inventory.getHelmet();

        if ((itemInHand == null || itemInHand.getType() == Material.AIR) && (itemOnHead == null || itemOnHead.getType() == Material.AIR)) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("head.empty-hand"));
            return true;
        }

        inventory.setHelmet(itemInHand);
        inventory.setItemInMainHand(itemOnHead);

        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("head.success"));

        return true;
    }
}
