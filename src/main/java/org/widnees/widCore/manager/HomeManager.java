package org.widnees.widCore.manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.TeleportAnimator;
import org.widnees.widCore.manager.TeleportManager;
import org.widnees.widCore.util.FoliaScheduler;
import org.widnees.widCore.util.TeleportNotifier;

public class HomeManager {
    private final Main plugin;
    private final Map<UUID, Map<String, Location>> homeCache = new ConcurrentHashMap<UUID, Map<String, Location>>();
    public static final int SET_HOME_SUCCESS = 0;
    public static final int SET_HOME_LIMIT_REACHED = 1;
    public static final int SET_HOME_ALREADY_EXISTS = 2;

    public HomeManager(Main plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Integer> setHome(Player player, String name, Location location) {
        UUID uuid = player.getUniqueId();
        String homeName = name.toLowerCase();
        return this.getHomes(uuid).thenCompose(homes -> {
            if (homes.containsKey(homeName)) {
                return CompletableFuture.completedFuture(2);
            }
            int maxHomes = this.getMaxHomes(player);
            if (homes.size() >= maxHomes) {
                return CompletableFuture.completedFuture(1);
            }
            homes.put(homeName, location);
            return this.saveHomes(uuid, (Map<String, Location>)homes).thenApply(v -> 0);
        });
    }

    public CompletableFuture<Boolean> updateHome(Player player, String name, Location location) {
        UUID uuid = player.getUniqueId();
        String homeName = name.toLowerCase();
        return this.getHomes(uuid).thenCompose(homes -> {
            if (!homes.containsKey(homeName)) {
                return CompletableFuture.completedFuture(false);
            }
            homes.put(homeName, location);
            return this.saveHomes(uuid, (Map<String, Location>)homes).thenApply(v -> true);
        });
    }

    public CompletableFuture<Location> getHome(UUID uuid, String name) {
        return this.getHomes(uuid).thenApply(homes -> (Location)homes.get(name.toLowerCase()));
    }

    public CompletableFuture<Boolean> deleteHome(UUID uuid, String name) {
        String homeName = name.toLowerCase();
        return this.getHomes(uuid).thenCompose(homes -> {
            if (homes.remove(homeName) != null) {
                return this.saveHomes(uuid, (Map<String, Location>)homes).thenApply(v -> true);
            }
            return CompletableFuture.completedFuture(false);
        });
    }

    public CompletableFuture<Map<String, Location>> getHomes(UUID uuid) {
        if (this.homeCache.containsKey(uuid)) {
            return CompletableFuture.completedFuture(new HashMap<String, Location>(this.homeCache.get(uuid)));
        }
        return this.plugin.getDataManager().getPlayerHomes(uuid).thenApply(homes -> {
            this.homeCache.put(uuid, (Map<String, Location>)homes);
            return new HashMap(homes);
        });
    }

    private CompletableFuture<Void> saveHomes(UUID uuid, Map<String, Location> homes) {
        this.homeCache.put(uuid, new HashMap<String, Location>(homes));
        return this.plugin.getDataManager().setPlayerHomes(uuid, homes);
    }

    public int getMaxHomes(Player player) {
        int i = 100;
        while (i >= 1) {
            String permission = "widcore.home." + i;
            if (player.isPermissionSet(permission) && player.hasPermission(permission)) {
                return i;
            }
            --i;
        }
        return this.getHomeConfig().getInt("default-max-homes", 3);
    }

    public CompletableFuture<Boolean> hasReachedLimit(Player player) {
        return this.getHomes(player.getUniqueId()).thenApply(homes -> homes.size() >= this.getMaxHomes(player));
    }

    public boolean isValidHomeName(String name) {
        return name != null && !name.isEmpty() && name.matches("^[a-zA-Z0-9]+$");
    }

    public boolean isWorldBanned(String worldName) {
        List<String> bannedWorlds = this.getHomeConfig().getStringList("banned-worlds");
        return bannedWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(worldName));
    }

    public void clearCache(UUID uuid) {
        this.homeCache.remove(uuid);
    }

    public FileConfiguration getHomeConfig() {
        return this.plugin.getConfigManager().getModuleConfig("home");
    }

    public void teleportWithDelay(Player player, Location targetLocation, String homeName, Runnable onSuccess, Runnable onCancel) {
        FileConfiguration homeConfig = this.getHomeConfig();
        TeleportManager teleportManager = this.plugin.getTeleportManager();
        TeleportAnimator teleportAnimator = this.plugin.getTeleportAnimator();
        int delaySeconds = homeConfig.getInt("teleport-delay-seconds", 3);
        boolean cancelOnMove = homeConfig.getBoolean("cancel-on-move", true);
        String animationType = homeConfig.getString("teleport-animation", "gta_style");
        double blindnessDistance = homeConfig.getDouble("gta-style-blindness-distance", 100.0);
        UUID playerId = player.getUniqueId();
        if (teleportAnimator.isAnimating(player)) {
            Main.sendMessage(this.plugin, (CommandSender)player, this.plugin.getLanguageManager().getMessage("home.already-teleporting"));
            return;
        }
        if (teleportManager.isTeleporting(playerId)) {
            Main.sendMessage(this.plugin, (CommandSender)player, this.plugin.getLanguageManager().getMessage("home.already-teleporting"));
            return;
        }
        if (delaySeconds <= 0) {
            this.performTeleportWithAnimation(player, targetLocation, homeConfig, animationType, blindnessDistance);
            if (onSuccess != null) {
                FoliaScheduler.runAtEntityLater((Plugin)this.plugin, (Entity)player, onSuccess, 5L);
            }
            return;
        }
        Map<String, String> warmupPl = new HashMap<>();
        warmupPl.put("%home%", homeName);
        warmupPl.put("%time%", String.valueOf(delaySeconds));
        TeleportNotifier.send(this.plugin, player, homeConfig, "notifications.warmup", warmupPl);
        teleportManager.startTeleporting(player, TeleportManager.TeleportType.WARP);
        int totalTicks = delaySeconds * 20;
        int[] ticksPassed = new int[1];
        Object[] taskHolder = new Object[1];
        boolean[] isCancelled = new boolean[1];
        taskHolder[0] = FoliaScheduler.runAtEntityTimer((Plugin)this.plugin, (Entity)player, () -> {
            if (isCancelled[0]) {
                return;
            }
            if (!player.isOnline() || !teleportManager.isTeleporting(playerId)) {
                isCancelled[0] = true;
                FoliaScheduler.cancelTask(taskHolder[0]);
                teleportManager.removeTask(playerId);
                return;
            }
            if (cancelOnMove) {
                Location currentLocation = player.getLocation();
                Location initialLocation = teleportManager.getLastLocation(playerId);
                if (initialLocation != null && initialLocation.getWorld().equals(currentLocation.getWorld()) && initialLocation.distanceSquared(currentLocation) > 0.1) {
                    isCancelled[0] = true;
                    teleportManager.cancelTeleport(player, this.plugin.getLanguageManager().getMessage("home.cancelled"));
                    FoliaScheduler.cancelTask(taskHolder[0]);
                    return;
                }
            }
            if (ticksPassed[0] >= totalTicks) {
                isCancelled[0] = true;
                this.performTeleportWithAnimation(player, targetLocation, homeConfig, animationType, blindnessDistance);
                teleportManager.removeTask(playerId);
                FoliaScheduler.cancelTask(taskHolder[0]);
                if (onSuccess != null) {
                    FoliaScheduler.runAtEntityLater((Plugin)this.plugin, (Entity)player, onSuccess, 5L);
                }
                return;
            }
            if (ticksPassed[0] % 20 == 0) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f + (float)ticksPassed[0] / (float)totalTicks * 1.5f);
            }
            Location playerLoc = player.getLocation();
            double progress = (double)ticksPassed[0] / (double)totalTicks;
            double angle = (double)ticksPassed[0] * 0.2;
            double radius = 1.5 * (1.0 - progress);
            double yOffset = progress * 2.5;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            player.getWorld().spawnParticle(Particle.SPELL_WITCH, playerLoc.clone().add(x, yOffset, z), 1, 0.0, 0.0, 0.0, 0.0);
            player.getWorld().spawnParticle(Particle.END_ROD, playerLoc.clone().add(-x, yOffset, -z), 1, 0.0, 0.0, 0.0, 0.0);
            if (ticksPassed[0] > totalTicks / 2) {
                player.getWorld().spawnParticle(Particle.PORTAL, playerLoc.clone().add(0.0, 0.2, 0.0), 5, 0.3, 0.3, 0.1);
            }
            ticksPassed[0] = ticksPassed[0] + 1;
        }, 1L, 1L);
        teleportManager.updateTask(playerId, taskHolder[0]);
    }

    private void performTeleportWithAnimation(Player player, Location location, FileConfiguration homeConfig, String animationType, double blindnessDistance) {
        TeleportAnimator animator = this.plugin.getTeleportAnimator();
        if ("gta_style".equalsIgnoreCase(animationType)) {
            animator.playGtaStyleAnimation(player, location, blindnessDistance, homeConfig);
        } else {
            animator.playStandardTeleport(player, location, homeConfig);
        }
    }
        @SuppressWarnings("unused")
    private static final String _0xCr3a7F = "\u0077\u0031\u0064\u006e\u0065\u0065\u0073";

}
