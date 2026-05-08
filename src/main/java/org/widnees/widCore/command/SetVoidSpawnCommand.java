package org.widnees.widCore.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.VoidSpawnManager;

public class SetVoidSpawnCommand implements CommandExecutor {

    private final Main plugin;
    private final VoidSpawnManager voidSpawnManager;

    public SetVoidSpawnCommand(Main plugin, VoidSpawnManager voidSpawnManager) {
        this.plugin = plugin;
        this.voidSpawnManager = voidSpawnManager;
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
        if (!player.hasPermission("widcore.voidspawn.set")) {
            Main.sendNoPermission(this.plugin, player, "widcore.voidspawn.set");
            return true;
        }

        voidSpawnManager.setVoidSpawn(player.getLocation(), player.getWorld().getName());
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("setvoidspawn.success"));
        return true;
    }
}