package org.widnees.widCore.listener;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.util.FoliaScheduler;

public class ExperienceOrbListener implements Listener {

    private final Main plugin;

    public ExperienceOrbListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOrbSpawn(EntitySpawnEvent event) {
        if (!plugin.getConfig().getBoolean("features.merge_experience_orbs", false)) {
            return;
        }

        if (!(event.getEntity() instanceof ExperienceOrb)) {
            return;
        }

        ExperienceOrb orb = (ExperienceOrb) event.getEntity();

        FoliaScheduler.runAtEntityLater(plugin, orb, () -> {
            if (orb == null || orb.isDead() || !orb.isValid()) {
                return;
            }

            mergeNearbyOrbs(orb);
        }, 5L); 
    }

    private void mergeNearbyOrbs(ExperienceOrb orb) {
        
        Location loc = orb.getLocation();
        World world = loc.getWorld();
        if (world == null)
            return;

        for (Entity nearby : world.getNearbyEntities(loc, 2.0, 2.0, 2.0)) {
            if (nearby instanceof ExperienceOrb) {
                ExperienceOrb other = (ExperienceOrb) nearby;
                if (other.isDead() || !other.isValid() || other.getUniqueId().equals(orb.getUniqueId())) {
                    continue;
                }

                if (orb.getUniqueId().compareTo(other.getUniqueId()) > 0) {
                    orb.setExperience(orb.getExperience() + other.getExperience());
                    other.remove();
                }
            }
        }
    }
}