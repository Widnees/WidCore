package org.widnees.widCore.listener;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.TeleportManager;
import org.widnees.widCore.manager.TpaManager;

public class TpaListener implements Listener {

    private final Main plugin;
    private final TpaManager tpaManager;
    private final TeleportManager teleportManager;
    private final FileConfiguration tpaConfig;

    public TpaListener(Main plugin) {
        this.plugin = plugin;
        this.tpaManager = plugin.getTpaManager();
        this.teleportManager = plugin.getTeleportManager();
        this.tpaConfig = plugin.getConfigManager().getModuleConfig("tpa");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!ConfigManager.isConfigLoaded()) return;
        tpaManager.cleanupPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDealDamage(EntityDamageByEntityEvent event) {
        if (!ConfigManager.isConfigLoaded()) return;
        if (!(event.getDamager() instanceof Player)) return;

        Player damager = (Player) event.getDamager();
        if (teleportManager.isTeleporting(damager.getUniqueId()) && teleportManager.getTeleportType(damager.getUniqueId()) == TeleportManager.TeleportType.TPA) {
            if (tpaConfig.getBoolean("teleport-warmup.cancel-on-damage-deal", true)) {
                cancel(damager, "tpa.cancelled-damage");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!ConfigManager.isConfigLoaded()) return;
        Player player = event.getPlayer();

        if (teleportManager.isTeleporting(player.getUniqueId()) && teleportManager.getTeleportType(player.getUniqueId()) == TeleportManager.TeleportType.TPA) {
            if (tpaConfig.getBoolean("teleport-warmup.cancel-on-block-break", true)) {
                cancel(player, "tpa.cancelled-move");
            }
        }
    }

    private void cancel(Player player, String msgKey) {
        String msg = plugin.getLanguageManager().getMessage(msgKey);
        teleportManager.cancelTeleport(player, msg);
        tpaManager.showTitle(player, "tpa.titles.cancel", msgKey);
    }
}