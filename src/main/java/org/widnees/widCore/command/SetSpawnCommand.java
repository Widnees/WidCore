package org.widnees.widCore.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.SpawnLocationManager;

public class SetSpawnCommand implements CommandExecutor {

    private final Main plugin;
    private final SpawnLocationManager spawnLocationManager;

    public SetSpawnCommand(Main plugin, SpawnLocationManager spawnLocationManager) {
        this.plugin = plugin;
        this.spawnLocationManager = spawnLocationManager;
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
        if (!player.hasPermission("widcore.setspawn")) {
            Main.sendNoPermission(this.plugin, player, "widcore.setspawn");
            return true;
        }

        spawnLocationManager.setSpawn(player.getLocation());
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("setspawn.success"));
        return true;
    }
        @SuppressWarnings("unused")
    private static final String _0xWd3f9b = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}