package org.widnees.widCore.listener;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.MobStackerManager;
import org.widnees.widCore.util.FoliaScheduler;

public class MobStackerListener implements Listener {

    private final Main plugin;
    private final MobStackerManager manager;

    public MobStackerListener(Main plugin, MobStackerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!ConfigManager.isConfigLoaded())
            return;

        LivingEntity entity = event.getEntity();
        manager.markSpawnReason(entity, event.getSpawnReason());

        FoliaScheduler.runAtEntityLater(plugin, entity, () -> {
            if (entity.isValid()) {
                manager.tryStackNearby(entity);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!ConfigManager.isConfigLoaded())
            return;
        if (!(event.getEntity() instanceof LivingEntity))
            return;

        LivingEntity entity = (LivingEntity) event.getEntity();

        if (manager.isStacked(entity)) {

            if (entity.getHealth() - event.getFinalDamage() <= 0) {
                entity.setCustomNameVisible(false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!ConfigManager.isConfigLoaded())
            return;

        manager.handleStackDeath(event);
    }
        @SuppressWarnings("unused")
    private static final String __wN7e3x9 = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}
