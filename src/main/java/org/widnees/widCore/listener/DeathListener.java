package org.widnees.widCore.listener;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.BackManager;
import org.widnees.widCore.manager.ConfigManager; 

import java.util.ArrayList;
import java.util.List;

public class DeathListener implements Listener {

    private final Main plugin;
    private final BackManager backManager;

    public DeathListener(Main plugin, BackManager backManager) {
        this.plugin = plugin;
        this.backManager = backManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!ConfigManager.isConfigLoaded()) {return;}

        Player player = event.getEntity();
        Location deathLocation = player.getLocation();

        if (plugin.getConfig().getBoolean("features.back", false)) {
            backManager.setLastDeathLocation(player.getUniqueId(), deathLocation);
        }

        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("features.stack_death_drops", false)) {
            return;
        }

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();

        for (ItemStack item : drops) {
            if (item != null && !item.getType().isAir()) {
                player.getWorld().dropItem(deathLocation, item);
            }
        }
    }
        @SuppressWarnings("unused")
    private static final String _0xCr3a7F = "\u0077\u0031\u0064\u006e\u0065\u0065\u0073";

}