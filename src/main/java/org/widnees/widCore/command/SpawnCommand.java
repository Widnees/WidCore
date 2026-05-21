package org.widnees.widCore.command;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.SpawnLocationManager;
import org.widnees.widCore.manager.TeleportAnimator;
import org.widnees.widCore.manager.TeleportManager;
import org.widnees.widCore.util.FoliaScheduler;

public class SpawnCommand implements CommandExecutor {

    private final Main plugin;
    private final SpawnLocationManager spawnLocationManager;
    private final FileConfiguration spawnConfig;
    private final TeleportManager teleportManager;
    private final TeleportAnimator teleportAnimator;

    public SpawnCommand(Main plugin, SpawnLocationManager spawnLocationManager, FileConfiguration spawnConfig,
            TeleportManager teleportManager, TeleportAnimator teleportAnimator) {
        this.plugin = plugin;
        this.spawnLocationManager = spawnLocationManager;
        this.spawnConfig = spawnConfig;
        this.teleportManager = teleportManager;
        this.teleportAnimator = teleportAnimator;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded())
            return true;

        if (!(sender instanceof Player)) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
            return true;
        }
        Player player = (Player) sender;

        if (teleportAnimator.isAnimating(player)) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("tpa.teleporting"));
            return true;
        }

        if (!player.hasPermission("widcore.spawn")) {
            Main.sendNoPermission(this.plugin, player, "widcore.spawn");
            return true;
        }

        if (teleportManager.isTeleporting(player.getUniqueId())) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("tpa.teleporting"));
            return true;
        }

        Location spawnLocation = spawnLocationManager.getSpawnLocation();
        if (spawnLocation == null) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("spawn.not-set"));
            return true;
        }

        int delay = spawnConfig.getInt("teleport-delay-seconds", 5);
        boolean cancelOnMove = spawnConfig.getBoolean("cancel-on-move", true);
        String animationType = spawnConfig.getString("teleport-animation", "standart").toLowerCase();
        double blindDistance = spawnConfig.getDouble("gta-style-blindness-distance", 100.0);

        if (delay <= 0) {
            if (animationType.equals("gta_style") && !FoliaScheduler.isFolia()) {
                teleportAnimator.playGtaStyleAnimation(player, spawnLocation, blindDistance, spawnConfig);
            } else {
                teleportPlayerStandart(player, spawnLocation);
            }
            return true;
        }

        Main.sendMessage(this.plugin, player,
                plugin.getLanguageManager().getMessage("spawn.teleporting").replace("%time%", String.valueOf(delay)));

        teleportManager.startTeleporting(player, TeleportManager.TeleportType.SPAWN);

        final int totalTicks = delay * 20;
        final int[] ticksPassed = { 0 };
        final Object[] taskHolder = new Object[1];
        final boolean[] isCancelled = { false };

        taskHolder[0] = FoliaScheduler.runAtEntityTimer(plugin, player, () -> {

            if (isCancelled[0]) {
                return;
            }

            if (!player.isOnline() || !teleportManager.isTeleporting(player.getUniqueId())) {
                isCancelled[0] = true;
                FoliaScheduler.cancelTask(taskHolder[0]);
                teleportManager.removeTask(player.getUniqueId());
                return;
            }

            if (cancelOnMove) {
                Location currentLocation = player.getLocation();
                Location initialLocation = teleportManager.getLastLocation(player.getUniqueId());
                if (initialLocation == null ||
                        currentLocation.getBlockX() != initialLocation.getBlockX() ||
                        currentLocation.getBlockY() != initialLocation.getBlockY() ||
                        currentLocation.getBlockZ() != initialLocation.getBlockZ()) {
                    isCancelled[0] = true;
                    FoliaScheduler.cancelTask(taskHolder[0]);
                    teleportManager.cancelTeleport(player,
                            plugin.getLanguageManager().getMessage("tpa.cancelled-move"));
                    return;
                }
            }

            if (ticksPassed[0] >= totalTicks) {
                isCancelled[0] = true;
                if (animationType.equals("gta_style") && !FoliaScheduler.isFolia()) {
                    teleportAnimator.playGtaStyleAnimation(player, spawnLocation, blindDistance, spawnConfig);
                } else {
                    teleportPlayerStandart(player, spawnLocation);
                }
                teleportManager.removeTask(player.getUniqueId());
                FoliaScheduler.cancelTask(taskHolder[0]);
                return;
            }

            if (ticksPassed[0] % 20 == 0) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f,
                        0.5f + (ticksPassed[0] / (float) totalTicks) * 1.5f);
            }

            Location playerLoc = player.getLocation();
            double progress = (double) ticksPassed[0] / totalTicks;
            boolean showParticles = spawnConfig.getBoolean("effects.show-particles", true);

            if (showParticles) {

                if (animationType.equals("fog")) {
                    double fogHeight = progress * 2.2; 
                    double radius = 0.6;
                    for (int i = 0; i < 8; i++) {
                        double angle = (ticksPassed[0] * 0.15) + (i * Math.PI / 4);
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;

                        player.getWorld().spawnParticle(Particle.SMOKE_LARGE, 
                                playerLoc.clone().add(x, fogHeight, z), 1, 0.1, 0.1, 0.1, 0.01);
                        player.getWorld().spawnParticle(Particle.SQUID_INK, 
                                playerLoc.clone().add(-x, fogHeight * 0.8, -z), 1, 0.1, 0.05, 0.1, 0.005);
                    }

                    if (fogHeight > 0.5) {
                        for (double h = 0; h < fogHeight; h += 0.4) {
                            double innerAngle = (ticksPassed[0] * 0.1) + h;
                            double innerX = Math.cos(innerAngle) * 0.3;
                            double innerZ = Math.sin(innerAngle) * 0.3;
                            player.getWorld().spawnParticle(Particle.SMOKE_NORMAL, 
                                    playerLoc.clone().add(innerX, h, innerZ), 1, 0.05, 0.05, 0.05, 0.005);
                        }
                    }
                } else if (animationType.equals("standart")) {

                    double angle = ticksPassed[0] * 0.2;
                    double radius = 1.5 * (1 - progress);
                    double yOffset = progress * 2.5;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;

                    player.getWorld().spawnParticle(Particle.SPELL_WITCH, playerLoc.clone().add(x, yOffset, z), 1, 0, 0, 0, 0);
                    player.getWorld().spawnParticle(Particle.END_ROD, playerLoc.clone().add(-x, yOffset, -z), 1, 0, 0, 0, 0);

                    if (ticksPassed[0] > totalTicks / 2) {
                        player.getWorld().spawnParticle(Particle.PORTAL, playerLoc.clone().add(0, 0.2, 0), 5, 0.3, 0.3, 0.1);
                    }
                }
            }

            ticksPassed[0]++;
        }, 1L, 1L);

        teleportManager.updateTask(player.getUniqueId(), taskHolder[0]);
        return true;
    }

    private void teleportPlayerStandart(Player player, Location location) {
        player.setFallDistance(0f);
        player.teleportAsync(location).thenAccept(success -> {
            if (success) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("spawn.success"));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

                final double maxParticleRadius = spawnConfig.getDouble("effects.teleport-particle.radius", 4.0);
                boolean showParticles = spawnConfig.getBoolean("effects.show-particles", true);
                if (showParticles && maxParticleRadius > 0) {
                    teleportAnimator.runParticleAnimation(player, maxParticleRadius, spawnConfig);
                }
            }
        });
    }
}