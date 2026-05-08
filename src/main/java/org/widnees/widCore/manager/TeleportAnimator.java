package org.widnees.widCore.manager;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.widnees.widCore.Main;
import org.widnees.widCore.util.FoliaScheduler;

public class TeleportAnimator {
    private final Main plugin;
    private final Set<UUID> animatingPlayers = new HashSet<UUID>();
    private final Map<UUID, Location> animationTargets = new ConcurrentHashMap<UUID, Location>();
    private final Map<UUID, ArmorStand> cameraStands = new ConcurrentHashMap<UUID, ArmorStand>();

    public TeleportAnimator(Main plugin) {
        this.plugin = plugin;
    }

    public void playStandardTeleport(Player player, Location location, FileConfiguration effectsConfig) {
        player.setFallDistance(0.0f);
        if (FoliaScheduler.isFolia()) {
            player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
                if (success.booleanValue()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                    double radius = effectsConfig.getDouble("effects.teleport-particle.radius", 4.0);
                    if (radius > 0.0) {
                        Location center = player.getLocation();
                        double angle = 0.0;
                        while (angle < Math.PI * 2) {
                            double x = Math.cos(angle) * radius;
                            double z = Math.sin(angle) * radius;
                            center.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(x, 0.1, z), 1, 0.0, 0.0, 0.0, 0.0);
                            center.getWorld().spawnParticle(Particle.PORTAL, center.clone().add(x, 0.1, z), 1, 0.0, 0.0, 0.0, 0.0);
                            angle += 0.19634954084936207;
                        }
                    }
                }
            });
        } else {
            player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
            double maxParticleRadius = effectsConfig.getDouble("effects.teleport-particle.radius", 4.0);
            if (maxParticleRadius > 0.0) {
                this.runParticleAnimation(player, maxParticleRadius, effectsConfig);
            }
        }
    }

    public boolean isAnimating(Player player) {
        return this.animatingPlayers.contains(player.getUniqueId());
    }

    public void playGtaStyleAnimation(Player player, Location targetLocation, double blindnessDistance, FileConfiguration spawnConfig) {
        if (FoliaScheduler.isFolia()) {
            this.playStandardTeleport(player, targetLocation, spawnConfig);
            return;
        }
        UUID playerId = player.getUniqueId();
        this.animatingPlayers.add(playerId);
        this.animationTargets.put(playerId, targetLocation);
        GameMode originalGameMode = player.getGameMode();
        Location startLocation = player.getLocation();
        boolean wasFlying = player.isFlying();
        boolean allowFlight = player.getAllowFlight();
        ArmorStand cameraStand = (ArmorStand)player.getWorld().spawn(startLocation, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setInvulnerable(true);
        });
        this.cameraStands.put(playerId, cameraStand);
        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget((Entity)cameraStand);
        boolean useBlindTeleport = !startLocation.getWorld().equals(targetLocation.getWorld()) || blindnessDistance > 0.0 && startLocation.distanceSquared(targetLocation) > blindnessDistance * blindnessDistance;
        this.ascend(player, cameraStand, startLocation, targetLocation, originalGameMode, wasFlying, allowFlight, useBlindTeleport, spawnConfig);
    }

    private void ascend(final Player player, final ArmorStand cameraStand, final Location startLocation, final Location targetLocation, final GameMode originalGameMode, final boolean wasFlying, final boolean allowFlight, final boolean useBlindTeleport, final FileConfiguration spawnConfig) {
        new BukkitRunnable(){
            private int stage = 0;
            private int ticksInStage = 0;
            private final double[] riseHeights = new double[]{20.0, 50.0, 100.0};
            private final float[] risePitches = new float[]{1.2f, 1.5f, 1.8f};
            private final int stageDuration = 10;
            private final double speed = 2.0;

            public void run() {
                if (!(player.isOnline() && cameraStand.isValid() && TeleportAnimator.this.isAnimating(player))) {
                    TeleportAnimator.this.restorePlayerState(player, targetLocation, originalGameMode, wasFlying, allowFlight, spawnConfig);
                    this.cancel();
                    return;
                }
                Location particleLoc = cameraStand.getLocation();
                particleLoc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, particleLoc.clone().add(0.0, 1.0, 0.0), 10, 0.5, 0.5, 0.5, 0.05);
                if (this.stage < this.riseHeights.length) {
                    Location stageTarget = startLocation.clone().add(0.0, this.riseHeights[this.stage], 0.0);
                    Location currentLoc = cameraStand.getLocation();
                    if (currentLoc.distance(stageTarget) > 0.5) {
                        this.ticksInStage = 0;
                        Location nextLoc = currentLoc.distance(stageTarget) <= 2.0 ? stageTarget : currentLoc.clone().add(stageTarget.clone().subtract(currentLoc).toVector().normalize().multiply(2.0));
                        nextLoc.setYaw(targetLocation.getYaw());
                        nextLoc.setPitch(90.0f);
                        cameraStand.teleport(nextLoc);
                    } else {
                        ++this.ticksInStage;
                        if (this.ticksInStage == 1) {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, this.risePitches[this.stage]);
                            particleLoc.getWorld().spawnParticle(Particle.END_ROD, particleLoc.clone().add(0.0, 1.0, 0.0), 30, 0.2, 0.2, 0.2, 0.1);
                        }
                        if (this.ticksInStage >= 10) {
                            ++this.stage;
                            this.ticksInStage = 0;
                        }
                    }
                } else {
                    if (useBlindTeleport) {
                        TeleportAnimator.this.blindTeleportToTarget(player, targetLocation, originalGameMode, wasFlying, allowFlight, spawnConfig);
                    } else {
                        TeleportAnimator.this.flyToTarget(player, cameraStand, targetLocation, originalGameMode, wasFlying, allowFlight, spawnConfig);
                    }
                    this.cancel();
                }
            }
        }.runTaskTimer((Plugin)this.plugin, 1L, 1L);
    }

    private void flyToTarget(final Player player, final ArmorStand cameraStand, final Location targetLocation, final GameMode originalGameMode, final boolean wasFlying, final boolean allowFlight, final FileConfiguration spawnConfig) {
        double flyHeight = 100.0;
        final Location flyTarget = targetLocation.clone().add(0.0, 100.0, 0.0);
        flyTarget.setPitch(90.0f);
        double speed = 3.5;
        new BukkitRunnable(){

            public void run() {
                if (!(player.isOnline() && cameraStand.isValid() && TeleportAnimator.this.isAnimating(player))) {
                    TeleportAnimator.this.restorePlayerState(player, targetLocation, originalGameMode, wasFlying, allowFlight, spawnConfig);
                    this.cancel();
                    return;
                }
                Location currentLoc = cameraStand.getLocation();
                Location horizontalTarget = flyTarget.clone();
                horizontalTarget.setY(currentLoc.getY());
                if (currentLoc.distance(horizontalTarget) > 1.0) {
                    Location nextLoc = currentLoc.distance(horizontalTarget) <= 3.5 ? horizontalTarget : currentLoc.clone().add(horizontalTarget.clone().subtract(currentLoc).toVector().normalize().multiply(3.5));
                    nextLoc.setYaw(targetLocation.getYaw());
                    nextLoc.setPitch(90.0f);
                    cameraStand.teleport(nextLoc);
                } else {
                    cameraStand.teleport(flyTarget);
                    TeleportAnimator.this.descend(player, cameraStand, targetLocation, originalGameMode, wasFlying, allowFlight, spawnConfig);
                    this.cancel();
                }
            }
        }.runTaskTimer((Plugin)this.plugin, 1L, 1L);
    }

    private void blindTeleportToTarget(final Player player, final Location targetLocation, final GameMode originalGameMode, final boolean wasFlying, final boolean allowFlight, final FileConfiguration spawnConfig) {
        double flyHeight = 100.0;
        final Location flyTarget = targetLocation.clone().add(0.0, 100.0, 0.0);
        flyTarget.setPitch(90.0f);
        flyTarget.setYaw(targetLocation.getYaw());
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 1, false, false));
        ArmorStand oldCameraStand = this.cameraStands.get(player.getUniqueId());
        if (oldCameraStand != null && oldCameraStand.isValid()) {
            oldCameraStand.remove();
        }
        player.teleport(flyTarget, PlayerTeleportEvent.TeleportCause.PLUGIN);
        new BukkitRunnable(){

            public void run() {
                if (!player.isOnline() || !TeleportAnimator.this.isAnimating(player)) {
                    TeleportAnimator.this.restorePlayerState(player, targetLocation, originalGameMode, wasFlying, allowFlight, spawnConfig);
                    this.cancel();
                    return;
                }
                if (!flyTarget.getChunk().isLoaded()) {
                    flyTarget.getChunk().load();
                }
                ArmorStand newCameraStand = (ArmorStand)flyTarget.getWorld().spawn(flyTarget, ArmorStand.class, stand -> {
                    stand.setVisible(false);
                    stand.setGravity(false);
                    stand.setMarker(true);
                    stand.setInvulnerable(true);
                });
                TeleportAnimator.this.cameraStands.put(player.getUniqueId(), newCameraStand);
                if (player.getGameMode() != GameMode.SPECTATOR) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
                player.setSpectatorTarget((Entity)newCameraStand);
                TeleportAnimator.this.descend(player, newCameraStand, targetLocation, originalGameMode, wasFlying, allowFlight, spawnConfig);
            }
        }.runTaskLater((Plugin)this.plugin, Math.max(1L, 10L));
    }

    private void descend(final Player player, final ArmorStand cameraStand, final Location targetLocation, final GameMode originalGameMode, final boolean wasFlying, final boolean allowFlight, final FileConfiguration spawnConfig) {
        new BukkitRunnable(){
            private int stage = 0;
            private int ticksInStage = 0;
            private final double[] descendHeights = new double[]{100.0, 50.0, 20.0, 5.0};
            private final float[] descendPitches = new float[]{1.8f, 1.5f, 1.2f, 1.0f};
            private final int stageDuration = 10;
            private final double speed = 2.0;

            public void run() {
                if (!(player.isOnline() && cameraStand.isValid() && TeleportAnimator.this.isAnimating(player))) {
                    TeleportAnimator.this.restorePlayerState(player, targetLocation, originalGameMode, wasFlying, allowFlight, spawnConfig);
                    this.cancel();
                    return;
                }
                Location particleLoc = cameraStand.getLocation();
                particleLoc.getWorld().spawnParticle(Particle.PORTAL, particleLoc.clone().add(0.0, 1.0, 0.0), 10, 0.5, 0.5, 0.5, 0.1);
                if (this.stage >= this.descendHeights.length) {
                    TeleportAnimator.this.smoothLand(player, cameraStand, targetLocation, originalGameMode, wasFlying, allowFlight, spawnConfig);
                    this.cancel();
                    return;
                }
                Location stageTarget = targetLocation.clone().add(0.0, this.descendHeights[this.stage], 0.0);
                Location currentLoc = cameraStand.getLocation();
                if (currentLoc.distance(stageTarget) > 0.5) {
                    this.ticksInStage = 0;
                    Location nextLoc = currentLoc.distance(stageTarget) <= 2.0 ? stageTarget : currentLoc.clone().add(stageTarget.clone().subtract(currentLoc).toVector().normalize().multiply(2.0));
                    nextLoc.setYaw(targetLocation.getYaw());
                    nextLoc.setPitch(90.0f);
                    cameraStand.teleport(nextLoc);
                } else {
                    ++this.ticksInStage;
                    if (this.ticksInStage == 1) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, this.descendPitches[this.stage]);
                    }
                    if (this.ticksInStage >= 10) {
                        ++this.stage;
                        this.ticksInStage = 0;
                    }
                }
            }
        }.runTaskTimer((Plugin)this.plugin, 1L, 1L);
    }

    private void smoothLand(final Player player, final ArmorStand cameraStand, final Location targetLocation, final GameMode originalGameMode, final boolean wasFlying, final boolean allowFlight, final FileConfiguration spawnConfig) {
        new BukkitRunnable(){
            private final double speed = 1.0;
            private final Location startLocation;
            private final float startPitch;
            private final float targetPitch;
            private final double totalDistance;
            private double distanceTraveled;
            {
                this.startLocation = cameraStand.getLocation();
                this.startPitch = this.startLocation.getPitch();
                this.targetPitch = targetLocation.getPitch();
                this.totalDistance = this.startLocation.distance(targetLocation);
                this.distanceTraveled = 0.0;
            }

            public void run() {
                if (!(player.isOnline() && cameraStand.isValid() && TeleportAnimator.this.isAnimating(player))) {
                    TeleportAnimator.this.restorePlayerState(player, targetLocation, originalGameMode, wasFlying, allowFlight, spawnConfig);
                    this.cancel();
                    return;
                }
                if (cameraStand.getLocation().distance(targetLocation) > 0.3) {
                    double moveDistance;
                    Location nextLoc;
                    Vector direction = targetLocation.toVector().subtract(cameraStand.getLocation().toVector());
                    if (direction.length() < 1.0) {
                        nextLoc = targetLocation.clone();
                        moveDistance = direction.length();
                    } else {
                        Vector normalizedDirection = direction.normalize();
                        nextLoc = cameraStand.getLocation().add(normalizedDirection.multiply(1.0));
                        moveDistance = 1.0;
                    }
                    this.distanceTraveled += moveDistance;
                    double progress = Math.min(this.distanceTraveled / this.totalDistance, 1.0);
                    float newPitch = (float)((double)this.startPitch + (double)(this.targetPitch - this.startPitch) * progress);
                    nextLoc.setYaw(targetLocation.getYaw());
                    nextLoc.setPitch(newPitch);
                    cameraStand.teleport(nextLoc);
                } else {
                    cameraStand.teleport(targetLocation);
                    TeleportAnimator.this.restorePlayerState(player, targetLocation, originalGameMode, wasFlying, allowFlight, spawnConfig);
                    this.cancel();
                }
            }
        }.runTaskTimer((Plugin)this.plugin, 1L, 1L);
    }

    public void restorePlayerState(Player player, Location targetLocation, GameMode originalGameMode, boolean wasFlying, boolean allowFlight, FileConfiguration spawnConfig) {
        UUID playerId = player.getUniqueId();
        ArmorStand cameraStand = this.cameraStands.remove(playerId);
        if (cameraStand != null && cameraStand.isValid()) {
            cameraStand.remove();
        }
        this.animatingPlayers.remove(playerId);
        this.animationTargets.remove(playerId);
        if (!player.isOnline()) {
            return;
        }
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setSpectatorTarget(null);
        }
        player.setGameMode(originalGameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(wasFlying);
        player.setFallDistance(0.0f);
        this.playStandardTeleport(player, targetLocation, spawnConfig);
    }

    public void forceEndAnimation(Player player, FileConfiguration spawnConfig) {
        if (!this.isAnimating(player)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Location targetLocation = this.animationTargets.get(playerId);
        this.restorePlayerState(player, targetLocation != null ? targetLocation : player.getLocation(), player.getPreviousGameMode() != null ? player.getPreviousGameMode() : GameMode.SURVIVAL, false, false, spawnConfig);
        Main.sendMessage(this.plugin, (CommandSender)player, this.plugin.getLanguageManager().getMessage("teleport_damage.anim-cancel"));
    }

    public void forceEndAnimation(Player player) {
        FileConfiguration spawnConfig = this.plugin.getConfigManager().getModuleConfig("spawn");
        this.forceEndAnimation(player, spawnConfig);
    }

    public Set<UUID> getAnimatingPlayers() {
        return new HashSet<UUID>(this.animatingPlayers);
    }

    public Location getAnimationTarget(UUID playerId) {
        return this.animationTargets.get(playerId);
    }

    public void runParticleAnimation(final Player player, final double maxRadius, FileConfiguration spawnConfig) {
        new BukkitRunnable(){
            double radius = 0.4;

            public void run() {
                if (this.radius > maxRadius || !player.isOnline()) {
                    this.cancel();
                    return;
                }
                Location center = player.getLocation();
                double angle = 0.0;
                while (angle < Math.PI * 2) {
                    double x = Math.cos(angle) * this.radius;
                    double z = Math.sin(angle) * this.radius;
                    Location particleLoc = center.clone().add(x, 0.1, z);
                    center.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 1, 0.0, 0.0, 0.0, 0.0);
                    center.getWorld().spawnParticle(Particle.PORTAL, particleLoc, 1, 0.0, 0.0, 0.0, 0.0);
                    angle += 0.09817477042468103;
                }
                this.radius += 0.4;
            }
        }.runTaskTimer((Plugin)this.plugin, 1L, 1L);
    }
}
