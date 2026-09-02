package org.widnees.widCore.manager;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.TeleportAnimator;
import org.widnees.widCore.manager.TeleportManager;
import org.widnees.widCore.manager.TextParser;
import org.widnees.widCore.util.FoliaScheduler;
import org.widnees.widCore.util.TeleportNotifier;

public class TpaManager {
    private final Main plugin;
    private final FileConfiguration tpaConfig;
    private final TeleportManager teleportManager;
    private final TeleportAnimator teleportAnimator;
    private final Map<UUID, TpaRequest> pendingRequests = new ConcurrentHashMap<UUID, TpaRequest>();
    private final Map<UUID, Map<UUID, Long>> cooldowns = new ConcurrentHashMap<UUID, Map<UUID, Long>>();
    private final Set<UUID> autoAccept = new HashSet<UUID>();

    public TpaManager(Main plugin) {
        this.plugin = plugin;
        this.tpaConfig = plugin.getConfigManager().getModuleConfig("tpa");
        this.teleportManager = plugin.getTeleportManager();
        this.teleportAnimator = plugin.getTeleportAnimator();
        this.startCleanupTask();
    }

    private void startCleanupTask() {
        FoliaScheduler.runTaskTimerAsync((Plugin)this.plugin, () -> {
            long now = System.currentTimeMillis();
            int requestTimeout = this.tpaConfig.getInt("request-timeout", 60) * 1000;
            this.pendingRequests.entrySet().removeIf(entry -> {
                boolean expired = now - ((TpaRequest)entry.getValue()).timestamp > (long)requestTimeout;
                boolean bl = expired;
                if (expired) {
                    Player requester = Bukkit.getPlayer((UUID)((TpaRequest)entry.getValue()).requesterId);
                    Player target = Bukkit.getPlayer((UUID)((UUID)entry.getKey()));
                    String reqName = requester != null ? requester.getName() : "???";
                    String tarName = target != null ? target.getName() : "???";
                    String string = tarName;
                    if (requester != null) {
                        Main.sendMessage(this.plugin, (CommandSender)requester, this.plugin.getLanguageManager().getMessage("tpa.timeout-sender").replace("%target%", tarName));
                    }
                    if (target != null) {
                        Main.sendMessage(this.plugin, (CommandSender)target, this.plugin.getLanguageManager().getMessage("tpa.timeout-target").replace("%player%", reqName));
                    }
                }
                return expired;
            });
            this.cooldowns.values().forEach(playerCooldowns -> {
                boolean bl = playerCooldowns.entrySet().removeIf(entry -> now - (Long)entry.getValue() > 0L);
            });
        }, Math.max(1L, 100L), 100L);
    }

    public void cleanupAll() {
        this.pendingRequests.clear();
        this.cooldowns.clear();
        this.autoAccept.clear();
    }

    public void cleanupPlayer(UUID playerId) {
        this.pendingRequests.remove(playerId);
        this.autoAccept.remove(playerId);
        this.cooldowns.remove(playerId);
        this.cooldowns.values().forEach(map -> {
            Object v = map.remove(playerId);
        });
    }

    public boolean hasPendingRequest(UUID targetId, UUID requesterId) {
        TpaRequest request = this.pendingRequests.get(targetId);
        return request != null && request.requesterId.equals(requesterId);
    }

    public TpaRequest getRequest(UUID targetId) {
        return this.pendingRequests.get(targetId);
    }

    public void sendTpaRequest(Player requester, Player target, String type) {
        if (this.checkCooldown(requester, target)) {
            return;
        }
        long now = System.currentTimeMillis();
        this.pendingRequests.put(target.getUniqueId(), new TpaRequest(requester.getUniqueId(), now, type));
        this.setCooldown(requester, target);
        String requesterMsgKey = type.equals("tpa") ? "tpa.sent" : "tpa.sent-here";
        String requesterMsg = this.plugin.getLanguageManager().getMessage(requesterMsgKey).replace("%target%", target.getName());
        Main.sendMessage(this.plugin, (CommandSender)requester, requesterMsg);
        String targetMsgKey = type.equals("tpa") ? "tpa.received" : "tpa.received-here";
        String targetMsg = this.plugin.getLanguageManager().getMessage(targetMsgKey).replace("%player%", requester.getName());
        String acceptText = this.plugin.getLanguageManager().getMessage("tpa.accept-button");
        String denyText = this.plugin.getLanguageManager().getMessage("tpa.deny-button");
        String acceptHover = this.plugin.getLanguageManager().getMessage("tpa.accept-hover");
        String denyHover = this.plugin.getLanguageManager().getMessage("tpa.deny-hover");
        String divider = this.plugin.getLanguageManager().getMessage("tpa.box-divider");
        target.sendMessage(TextParser.parse(divider));
        target.sendMessage((Component)Component.empty());
        target.sendMessage(TextParser.parse("     " + targetMsg));
        target.sendMessage((Component)Component.empty());
        Component acceptButton = TextParser.parse("         " + acceptText).hoverEvent((HoverEventSource)HoverEvent.showText((Component)TextParser.parse(acceptHover))).clickEvent(ClickEvent.runCommand((String)("/tpaaccept " + requester.getName())));
        Component denyButton = TextParser.parse("           " + denyText).hoverEvent((HoverEventSource)HoverEvent.showText((Component)TextParser.parse(denyHover))).clickEvent(ClickEvent.runCommand((String)("/tpadeny " + requester.getName())));
        target.sendMessage(acceptButton.append(denyButton));
        target.sendMessage((Component)Component.empty());
        target.sendMessage(TextParser.parse(divider));
        String soundName = this.tpaConfig.getString("sounds.request-received", "ENTITY_EXPERIENCE_ORB_PICKUP");
        try {
            target.playSound(target.getLocation(), Sound.valueOf((String)soundName.toUpperCase().replace(".", "_").replace(":", "_")), 1.0f, 1.0f);
        }
        catch (IllegalArgumentException e) {
            target.playSound(target.getLocation(), soundName, 1.0f, 1.0f);
        }
    }

    public void acceptTpaRequest(Player target, Player requester) {
        TpaRequest request = this.pendingRequests.remove(target.getUniqueId());
        if (request == null || !request.requesterId.equals(requester.getUniqueId())) {
            Main.sendMessage(this.plugin, (CommandSender)target, this.plugin.getLanguageManager().getMessage("tpa.no-request"));
            return;
        }
        Main.sendMessage(this.plugin, (CommandSender)target, this.plugin.getLanguageManager().getMessage("tpa.accepted").replace("%player%", requester.getName()));
        Main.sendMessage(this.plugin, (CommandSender)requester, this.plugin.getLanguageManager().getMessage("tpa.accepted-other").replace("%target%", target.getName()));
        Player teleporter = request.type.equals("tpa") ? requester : target;
        Player destination = request.type.equals("tpa") ? target : requester;
        boolean useWarmup = this.tpaConfig.getBoolean("teleport-warmup.enabled", true);
        int warmupTime = this.tpaConfig.getInt("teleport-warmup.duration", 3);
        if (useWarmup && !teleporter.hasPermission("widcore.tpa.bypass.warmup")) {
            this.teleportWithWarmup(teleporter, destination.getLocation(), warmupTime);
        } else {
            String animationType = this.tpaConfig.getString("teleport-animation", "standart").toLowerCase();
            double blindDistance = this.tpaConfig.getDouble("gta-style-blindness-distance", 100.0);
            if (animationType.equals("gta_style") && !FoliaScheduler.isFolia()) {
                this.teleportAnimator.playGtaStyleAnimation(teleporter, destination.getLocation(), blindDistance, this.tpaConfig,
                        () -> TeleportNotifier.send(this.plugin, teleporter, this.tpaConfig, "notifications.success", new HashMap<>()));
            } else {
                this.teleportPlayerStandart(teleporter, destination.getLocation());
            }
        }
    }

    public void denyTpaRequest(Player target, Player requester) {
        TpaRequest request = this.pendingRequests.remove(target.getUniqueId());
        if (request == null) {
            Main.sendMessage(this.plugin, (CommandSender)target, this.plugin.getLanguageManager().getMessage("tpa.no-request"));
            return;
        }
        Main.sendMessage(this.plugin, (CommandSender)target, this.plugin.getLanguageManager().getMessage("tpa.denied").replace("%player%", requester.getName()));
        Main.sendMessage(this.plugin, (CommandSender)requester, this.plugin.getLanguageManager().getMessage("tpa.denied-other").replace("%target%", target.getName()));
    }

    public void denyAllRequests(Player target) {
        if (this.pendingRequests.containsKey(target.getUniqueId())) {
            this.pendingRequests.remove(target.getUniqueId());
            Main.sendMessage(this.plugin, (CommandSender)target, this.plugin.getLanguageManager().getMessage("tpa.denied-all"));
        } else {
            Main.sendMessage(this.plugin, (CommandSender)target, this.plugin.getLanguageManager().getMessage("tpa.no-request"));
        }
    }

    private void teleportPlayerStandart(Player player, Location location) {
        player.setFallDistance(0.0f);
        player.teleportAsync(location).thenAccept(success -> {
            if (success != null && success.booleanValue()) {
                TeleportNotifier.send(this.plugin, player, this.tpaConfig, "notifications.success", new HashMap<>());
                double maxParticleRadius = this.tpaConfig.getDouble("effects.teleport-particle.radius", 4.0);
                boolean showParticles = this.tpaConfig.getBoolean("effects.show-particles", true);
                if (showParticles && maxParticleRadius > 0.0) {
                    this.teleportAnimator.runParticleAnimation(player, maxParticleRadius, this.tpaConfig);
                }
            }
        });
    }

    private void teleportWithWarmup(Player player, Location location, int delay) {
        Object[] taskHolder = new Object[1];
        Map<String, String> warmupPl = new HashMap<>();
        warmupPl.put("%time%", String.valueOf(delay));
        TeleportNotifier.send(this.plugin, player, this.tpaConfig, "notifications.warmup", warmupPl);
        int totalTicks = delay * 20;
        int[] ticksPassed = new int[1];
        String animationType = this.tpaConfig.getString("teleport-animation", "standart").toLowerCase();
        double blindDistance = this.tpaConfig.getDouble("gta-style-blindness-distance", 100.0);
        taskHolder[0] = FoliaScheduler.runAtEntityTimer((Plugin)this.plugin, (Entity)player, () -> {
            if (!player.isOnline() || !this.teleportManager.isTeleporting(player.getUniqueId())) {
                if (taskHolder[0] != null) FoliaScheduler.cancelTask(taskHolder[0]);
                this.teleportManager.removeTask(player.getUniqueId());
                return;
            }
            if (this.tpaConfig.getBoolean("teleport-warmup.cancel-on-move", true)) {
                Location current = player.getLocation();
                Location initial = this.teleportManager.getLastLocation(player.getUniqueId());
                if (initial != null && (!initial.getWorld().equals((Object)current.getWorld()) || initial.distanceSquared(current) > 0.1)) {
                    if (taskHolder[0] != null) FoliaScheduler.cancelTask(taskHolder[0]);
                    this.teleportManager.cancelTeleport(player, this.plugin.getLanguageManager().getMessage("tpa.cancelled-move"));
                    return;
                }
            }
            if (ticksPassed[0] >= totalTicks) {
                this.teleportManager.removeTask(player.getUniqueId());
                if (taskHolder[0] != null) FoliaScheduler.cancelTask(taskHolder[0]);
                if (animationType.equals("gta_style") && !FoliaScheduler.isFolia()) {
                    this.teleportAnimator.playGtaStyleAnimation(player, location, blindDistance, this.tpaConfig,
                            () -> TeleportNotifier.send(this.plugin, player, this.tpaConfig, "notifications.success", new HashMap<>()));
                } else {
                    this.teleportPlayerStandart(player, location);
                }
                return;
            }
            if (ticksPassed[0] % 20 == 0) {
                int remaining = (totalTicks - ticksPassed[0]) / 20;
                String titleStr = this.tpaConfig.getString("notifications.warmup.title.title", "");
                String subStr = this.tpaConfig.getString("notifications.warmup.title.subtitle", "").replace("%time%", String.valueOf(remaining));
                if (!titleStr.isEmpty() || !subStr.isEmpty()) {
                    player.showTitle(Title.title((Component)TextParser.parse(titleStr), (Component)TextParser.parse(subStr), (Title.Times)Title.Times.times((Duration)Duration.ZERO, (Duration)Duration.ofMillis(1200L), (Duration)Duration.ofMillis(200L))));
                }
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            }
            Location playerLoc = player.getLocation();
            double progress = (double)ticksPassed[0] / (double)totalTicks;
            boolean showParticles = this.tpaConfig.getBoolean("effects.show-particles", true);
            if (showParticles) {
                if (animationType.equals("fog")) {
                    double fogHeight = progress * 2.2;
                    double radius = 0.6;
                    for (int i = 0; i < 8; ++i) {
                        double angle = (double)ticksPassed[0] * 0.15 + (double)i * Math.PI / 4.0;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        player.getWorld().spawnParticle(Particle.SMOKE_LARGE, playerLoc.clone().add(x, fogHeight, z), 1, 0.1, 0.1, 0.1, 0.01);
                        player.getWorld().spawnParticle(Particle.SQUID_INK, playerLoc.clone().add(-x, fogHeight * 0.8, -z), 1, 0.1, 0.05, 0.1, 0.005);
                    }
                    if (fogHeight > 0.5) {
                        for (double h = 0.0; h < fogHeight; h += 0.4) {
                            double innerAngle = (double)ticksPassed[0] * 0.1 + h;
                            double innerX = Math.cos(innerAngle) * 0.3;
                            double innerZ = Math.sin(innerAngle) * 0.3;
                            player.getWorld().spawnParticle(Particle.SMOKE_NORMAL, playerLoc.clone().add(innerX, h, innerZ), 1, 0.05, 0.05, 0.05, 0.005);
                        }
                    }
                } else if (animationType.equals("standart")) {
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
                }
            }
            ticksPassed[0] = ticksPassed[0] + 1;
        }, 1L, 1L);
        if (taskHolder[0] instanceof BukkitTask) {
            this.teleportManager.addTeleportTask(player, (BukkitTask)taskHolder[0], TeleportManager.TeleportType.TPA);
        }
    }

    public void showTitle(Player player, String titleKey, String subKey) {
        String title = this.plugin.getLanguageManager().getMessage(titleKey);
        String sub = this.plugin.getLanguageManager().getMessage(subKey);
        player.showTitle(Title.title((Component)TextParser.parse(title), (Component)TextParser.parse(sub)));
    }

    public boolean checkCooldown(Player requester, Player target) {
        long now = System.currentTimeMillis();
        Map<UUID, Long> playerCooldowns = this.cooldowns.getOrDefault(requester.getUniqueId(), new HashMap<>());
        long end = playerCooldowns.getOrDefault(target.getUniqueId(), 0L);
        if (now < end) {
            long remaining = (end - now) / 1000L;
            Main.sendMessage(this.plugin, (CommandSender)requester, this.plugin.getLanguageManager().getMessage("tpa.cooldown").replace("%time%", String.valueOf(remaining)));
            return true;
        }
        return false;
    }

    public void setCooldown(Player requester, Player target) {
        int time = this.tpaConfig.getInt("cooldown", 30) * 1000;
        if (time <= 0) {
            return;
        }
        Map map = this.cooldowns.getOrDefault(requester.getUniqueId(), new HashMap());
        map.put(target.getUniqueId(), System.currentTimeMillis() + (long)time);
        this.cooldowns.put(requester.getUniqueId(), map);
    }

    public void toggleAutoAccept(Player player) {
        if (this.autoAccept.contains(player.getUniqueId())) {
            this.autoAccept.remove(player.getUniqueId());
            Main.sendMessage(this.plugin, (CommandSender)player, this.plugin.getLanguageManager().getMessage("tpa.auto-accept-off"));
        } else {
            this.autoAccept.add(player.getUniqueId());
            Main.sendMessage(this.plugin, (CommandSender)player, this.plugin.getLanguageManager().getMessage("tpa.auto-accept-on"));
        }
    }

    public boolean isAutoAccepting(UUID playerId) {
        return this.autoAccept.contains(playerId);
    }

    public static class TpaRequest {
        public final UUID requesterId;
        final long timestamp;
        final String type;

        TpaRequest(UUID requesterId, long timestamp, String type) {
            this.requesterId = requesterId;
            this.timestamp = timestamp;
            this.type = type;
        }
    }
        @SuppressWarnings("unused")
    private static final String _xCr7w3n = "\u0077\u0069\u0064\u006e" + "\u0065\u0065\u0073";

}
