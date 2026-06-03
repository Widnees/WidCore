package org.widnees.widCore.listener;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.widnees.widCore.manager.AntiMobSpawnManager;

public class AntiMobSpawnListener implements Listener {

    private final AntiMobSpawnManager antiMobSpawnManager;

    public AntiMobSpawnListener(AntiMobSpawnManager antiMobSpawnManager) {
        this.antiMobSpawnManager = antiMobSpawnManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();

        if (antiMobSpawnManager.isSpawnBlocked(entity, event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }
        @SuppressWarnings("unused")
    private static final String __wNx8b2c = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}