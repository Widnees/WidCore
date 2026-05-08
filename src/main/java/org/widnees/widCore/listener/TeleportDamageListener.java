package org.widnees.widCore.listener;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager; 
import org.widnees.widCore.manager.TeleportAnimator;
import org.widnees.widCore.manager.TeleportManager;

public class TeleportDamageListener implements Listener {

    private final Main plugin;
    private final TeleportManager teleportManager;
    private final FileConfiguration spawnConfig;
    private final FileConfiguration warpConfig;
    private final TeleportAnimator teleportAnimator;

    public TeleportDamageListener(Main plugin, TeleportManager teleportManager, TeleportAnimator teleportAnimator) {
        this.plugin = plugin;
        this.teleportManager = teleportManager;
        this.teleportAnimator = teleportAnimator;
        this.spawnConfig = plugin.getConfigManager().getModuleConfig("spawn");
        this.warpConfig = plugin.getConfigManager().getModuleConfig("warp");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!ConfigManager.isConfigLoaded()) {return;}

        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!teleportManager.isTeleporting(player.getUniqueId())) return;

            TeleportManager.TeleportType type = teleportManager.getTeleportType(player.getUniqueId());
            boolean shouldCancel = false;

            if (type == TeleportManager.TeleportType.SPAWN && spawnConfig.getBoolean("cancel-on-damage", false)) {
                shouldCancel = true;
            } else if (type == TeleportManager.TeleportType.WARP && warpConfig.getBoolean("cancel-on-damage", false)) {
                shouldCancel = true;
            }

            if (shouldCancel) {
                teleportManager.cancelTeleport(player, plugin.getLanguageManager().getMessage("teleport_damage.damage"));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!ConfigManager.isConfigLoaded()) {return;}

        Player player = event.getPlayer();
        if (teleportAnimator.isAnimating(player)) {
            teleportAnimator.forceEndAnimation(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDealDamage(EntityDamageByEntityEvent event) {
        if (!ConfigManager.isConfigLoaded()) {return;}

        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (!teleportManager.isTeleporting(player.getUniqueId())) return;

            TeleportManager.TeleportType type = teleportManager.getTeleportType(player.getUniqueId());
            boolean shouldCancel = false;

            if (type == TeleportManager.TeleportType.SPAWN && spawnConfig.getBoolean("cancel-on-damage", false)) {
                shouldCancel = true;
            } else if (type == TeleportManager.TeleportType.WARP && warpConfig.getBoolean("cancel-on-damage", false)) {
                shouldCancel = true;
            }

            if (shouldCancel) {
                teleportManager.cancelTeleport(player, plugin.getLanguageManager().getMessage("teleport_damage.attack"));
            }
        }
    }
}