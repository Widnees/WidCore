package org.widnees.widCore.command;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.TeleportAnimator;
import org.widnees.widCore.manager.TeleportManager;
import org.widnees.widCore.manager.WarpManager;
import org.widnees.widCore.util.FoliaScheduler;
import org.widnees.widCore.util.TeleportNotifier;

import java.util.HashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WarpCommands implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final TeleportManager teleportManager;
    private final WarpManager warpManager;
    private final FileConfiguration warpConfig;
    private final TeleportAnimator teleportAnimator;

    public WarpCommands(Main plugin, TeleportManager teleportManager, WarpManager warpManager) {
        this.plugin = plugin;
        this.teleportManager = teleportManager;
        this.warpManager = warpManager;
        this.warpConfig = plugin.getConfigManager().getModuleConfig("warp");
        this.teleportAnimator = plugin.getTeleportAnimator();
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

        String commandKey = plugin.getAliasManager().lookupKey(command.getName());

        if (commandKey.equals("setwarp")) {
            if (!player.hasPermission("widcore.setwarp")) {
                Main.sendNoPermission(this.plugin, player, "widcore.setwarp");
                return true;
            }
            if (args.length == 0) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("warp.usage-set"));
                return true;
            }
            String warpName = args[0];
            warpManager.setWarp(warpName, player.getLocation());
            Main.sendMessage(this.plugin, player,
                    plugin.getLanguageManager().getMessage("warp.set").replace("%warp%", warpName));

        } else if (commandKey.equals("warp")) {
            if (!player.hasPermission("widcore.warp")) {
                Main.sendNoPermission(this.plugin, player, "widcore.warp");
                return true;
            }
            if (args.length == 0) {
                String warpList = String.join(", ", warpManager.getWarpNames());
                if (warpList.isEmpty()) {
                    Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("warp.no-warps"));
                } else {
                    Main.sendMessage(this.plugin, player,
                            plugin.getLanguageManager().getMessage("warp.list").replace("%warps%", warpList));
                }
                return true;
            }
            String warpName = args[0];
            Location warpLocation = warpManager.getWarp(warpName);
            if (warpLocation == null) {
                Main.sendMessage(this.plugin, player,
                        plugin.getLanguageManager().getMessage("warp.not-found").replace("%warp%", warpName));
                return true;
            }

            if (!player.hasPermission("widcore.warp.*") && !player.hasPermission("widcore.warp." + warpName)) {
                Main.sendNoPermission(this.plugin, player, "widcore.warp." + warpName);
                return true;
            }

            if (teleportAnimator.isAnimating(player)) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("tpa.teleporting"));
                return true;
            }

            if (teleportManager.isTeleporting(player.getUniqueId())) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("tpa.teleporting"));
                return true;
            }

            int delay = warpConfig.getInt("teleport-delay-seconds", 5);
            boolean cancelOnMove = warpConfig.getBoolean("cancel-on-move", true);
            String animationType = warpConfig.getString("teleport-animation", "standart").toLowerCase();
            double blindDistance = warpConfig.getDouble("gta-style-blindness-distance", 100.0);

            if (delay <= 0) {
                if (animationType.equals("gta_style") && !FoliaScheduler.isFolia()) {
                    teleportAnimator.playGtaStyleAnimation(player, warpLocation, blindDistance, warpConfig);
                    Map<String, String> successPlaceholders = new HashMap<>();
                    successPlaceholders.put("%warp%", warpName);
                    TeleportNotifier.send(plugin, player, warpConfig, "notifications.success", successPlaceholders);
                } else {
                    teleportPlayerStandart(player, warpLocation, warpName);
                }
                return true;
            }

            Map<String, String> warmupPlaceholders = new HashMap<>();
            warmupPlaceholders.put("%warp%", warpName);
            warmupPlaceholders.put("%time%", String.valueOf(delay));
            TeleportNotifier.send(plugin, player, warpConfig, "notifications.warmup", warmupPlaceholders);

            teleportManager.startTeleporting(player, TeleportManager.TeleportType.WARP);

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
                    if (initialLocation != null &&
                            initialLocation.getWorld().equals(currentLocation.getWorld()) &&
                            initialLocation.distanceSquared(currentLocation) > 0.1) {
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
                        teleportAnimator.playGtaStyleAnimation(player, warpLocation, blindDistance, warpConfig);
                        Map<String, String> successPl = new HashMap<>();
                        successPl.put("%warp%", warpName);
                        TeleportNotifier.send(plugin, player, warpConfig, "notifications.success", successPl);
                    } else {
                        teleportPlayerStandart(player, warpLocation, warpName);
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
                } else {

                    double angle = ticksPassed[0] * 0.2;
                    double radius = 1.5 * (1 - progress);
                    double yOffset = progress * 2.5;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;

                    player.getWorld().spawnParticle(Particle.SPELL_WITCH, playerLoc.clone().add(x, yOffset, z), 1, 0, 0,
                            0,
                            0);
                    player.getWorld().spawnParticle(Particle.END_ROD, playerLoc.clone().add(-x, yOffset, -z), 1, 0, 0,
                            0,
                            0);

                    if (ticksPassed[0] > totalTicks / 2) {
                        player.getWorld().spawnParticle(Particle.PORTAL, playerLoc.clone().add(0, 0.2, 0), 5, 0.3, 0.3,
                                0.1);
                    }
                }
                ticksPassed[0]++;
            }, 1L, 1L);

            teleportManager.updateTask(player.getUniqueId(), taskHolder[0]);

        } else if (commandKey.equals("delwarp")) {
            if (!player.hasPermission("widcore.delwarp")) {
                Main.sendNoPermission(this.plugin, player, "widcore.delwarp");
                return true;
            }
            if (args.length == 0) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("warp.usage-del"));
                return true;
            }
            String warpName = args[0];
            if (warpManager.getWarp(warpName) == null) {
                Main.sendMessage(this.plugin, player,
                        plugin.getLanguageManager().getMessage("warp.not-found").replace("%warp%", warpName));
                return true;
            }
            warpManager.delWarp(warpName);
            Main.sendMessage(this.plugin, player,
                    plugin.getLanguageManager().getMessage("warp.deleted").replace("%warp%", warpName));
        }
        return true;
    }

    private void teleportPlayerStandart(Player player, Location location, String warpName) {
        player.setFallDistance(0f);
        player.teleportAsync(location).thenAccept(success -> {
            if (success) {
                Map<String, String> pl = new HashMap<>();
                pl.put("%warp%", warpName);
                TeleportNotifier.send(plugin, player, warpConfig, "notifications.success", pl);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

                final double maxParticleRadius = warpConfig.getDouble("effects.teleport-particle.radius", 4.0);
                if (maxParticleRadius > 0) {
                    teleportAnimator.runParticleAnimation(player, maxParticleRadius, warpConfig);
                }
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        String commandKey = plugin.getAliasManager().lookupKey(command.getName());

        if (args.length == 1 && (commandKey.equals("warp") || commandKey.equals("delwarp"))) {
            WarpManager warpManager = plugin.getWarpManager();
            List<String> warpNames = new ArrayList<>(warpManager.getWarpNames());
            return StringUtil.copyPartialMatches(args[0], warpNames, new ArrayList<>());
        }
        return Collections.emptyList();
    }
        @SuppressWarnings("unused")
    private static final String _xCr7w3n = "\u0077\u0069\u0064\u006e" + "\u0065\u0065\u0073";

}