package org.widnees.widCore.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

public class GodModeListener implements Listener {

    private final Main plugin;

    public GodModeListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!ConfigManager.isConfigLoaded()) {return;}
        if (event.getEntity() instanceof Player) {

            Player player = (Player) event.getEntity();
            if (plugin.getGodModePlayers().contains(player.getUniqueId())) {
                event.setCancelled(true);
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setSaturation(20F);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!ConfigManager.isConfigLoaded()) {return;}
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (plugin.getGodModePlayers().contains(player.getUniqueId())) {
                event.setCancelled(true);
                player.setFoodLevel(20);
                player.setSaturation(20F);
            }
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!ConfigManager.isConfigLoaded()) {return;}
        Entity target = event.getTarget();
        if (target instanceof Player) {
            if (plugin.getGodModePlayers().contains(target.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!ConfigManager.isConfigLoaded()) {return;}
        plugin.getGodModePlayers().remove(event.getPlayer().getUniqueId());
    }
}