package org.widnees.widCore.listener;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.database.BinaryDataManager;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.JailManager;
import org.widnees.widCore.manager.PunishmentManager;
import org.widnees.widCore.util.FoliaScheduler;

public class JailListener implements Listener {

    private final Main plugin;
    private final JailManager jailManager;
    private final PunishmentManager punishmentManager;

    public JailListener(Main plugin) {
        this.plugin = plugin;
        this.jailManager = plugin.getJailManager();
        this.punishmentManager = plugin.getPunishmentManager();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSetupInteract(PlayerInteractEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        Player player = event.getPlayer();
        if (!jailManager.isInSetupSession(player))
            return;
        if (!jailManager.isJailAxe(event.getItem()))
            return;

        event.setCancelled(true);
        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_BLOCK) {
            Location loc = event.getClickedBlock().getLocation();
            jailManager.setJailPos(player, 1, loc);

            String msg = plugin.getLanguageManager().getMessage("jail.setup-pos1")
                    .replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()));
            Main.sendMessage(this.plugin, player, msg);
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            Location loc = event.getClickedBlock().getLocation();
            jailManager.setJailPos(player, 2, loc);

            String msg = plugin.getLanguageManager().getMessage("jail.setup-pos2")
                    .replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()));
            Main.sendMessage(this.plugin, player, msg);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (!punishmentManager.isJailed(player.getUniqueId()))
            return;

        BinaryDataManager.JailEntry jailEntry = punishmentManager.getJailEntry(player.getUniqueId());
        if (jailEntry == null)
            return;

        if (!jailManager.isLocationInJail(event.getTo(), jailEntry.jailName)) {
            Location jailSpawn = jailManager.getJailSpawn(jailEntry.jailName);
            if (jailSpawn != null) {
                event.setTo(jailSpawn);
                Main.sendMessage(this.plugin, player, jailManager.getEscapeMessage());
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        Player player = event.getPlayer();
        if (!punishmentManager.isJailed(player.getUniqueId()))
            return;

        BinaryDataManager.JailEntry jailEntry = punishmentManager.getJailEntry(player.getUniqueId());
        if (jailEntry == null)
            return;

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND
                || event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            if (!jailManager.isLocationInJail(event.getTo(), jailEntry.jailName)) {
                event.setCancelled(true);
                Main.sendMessage(this.plugin, player, jailManager.getEscapeMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        Player player = event.getPlayer();
        if (!punishmentManager.isJailed(player.getUniqueId()))
            return;

        String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();

        if (!jailManager.isCommandAllowed(command)) {
            event.setCancelled(true);
            Main.sendMessage(this.plugin, player, jailManager.getCommandBlockedMessage());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        jailManager.stopSetupSession(event.getPlayer());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        jailManager.resolvePendingJails(event.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (punishmentManager.isJailed(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        Player player = event.getPlayer();

        if (jailManager.isInSetupSession(player) && jailManager.isJailLantern(event.getItemInHand())) {
            event.setCancelled(true);
            Location loc = event.getBlock().getLocation().add(0.5, 0, 0.5); 
            jailManager.setJailSpawn(player, loc);

            String msg = plugin.getLanguageManager().getMessage("jail.spawn-set")
                    .replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()));
            Main.sendMessage(this.plugin, player, msg);
            return;
        }

        if (punishmentManager.isJailed(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (event.getDamager() instanceof Player) {
            Player damager = (Player) event.getDamager();
            if (punishmentManager.isJailed(damager.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (event.getEntity().getShooter() instanceof Player) {
            Player shooter = (Player) event.getEntity().getShooter();
            if (punishmentManager.isJailed(shooter.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        Player player = event.getPlayer();
        if (jailManager.isInSetupSession(player)) {
            return;
        }

        if (punishmentManager.isJailed(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (punishmentManager.isJailed(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            if (punishmentManager.isJailed(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        Player player = event.getPlayer();
        if (!punishmentManager.isJailed(player.getUniqueId())) {
            return;
        }

        BinaryDataManager.JailEntry jailEntry = punishmentManager.getJailEntry(player.getUniqueId());
        if (jailEntry == null) {
            return;
        }

        Location jailSpawn = jailManager.getJailSpawn(jailEntry.jailName);
        if (jailSpawn != null) {

            FoliaScheduler.runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    FoliaScheduler.teleportAsync(player, jailSpawn, null);

                    String durationMsg;
                    if (jailEntry.expiry == -1L) {
                        durationMsg = plugin.getLanguageManager().getMessage("time.permanent");
                    } else {
                        long remaining = jailEntry.expiry - System.currentTimeMillis();
                        durationMsg = punishmentManager.formatDuration(remaining);
                    }

                    Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("jail.message")
                            .replace("%duration%", durationMsg)
                            .replace("%jail%", jailEntry.jailName));
                }
            }, 5L);
        }
    }
}