package org.widnees.widCore.manager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.TeleportAnimator;
import org.widnees.widCore.manager.TeleportManager;
import org.widnees.widCore.util.FoliaScheduler;

public class RtpManager {
    private final Main plugin;
    private final Queue<RtpRequest> rtpQueue = new ConcurrentLinkedQueue<RtpRequest>();
    private final Set<UUID> activeRtpPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingRtpPlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, List<Location>> locationCache = new ConcurrentHashMap<String, List<Location>>();
    private final Map<UUID, Float> originalWalkSpeeds = new ConcurrentHashMap<UUID, Float>();
    private final Map<UUID, Object> actionBarTasks = new ConcurrentHashMap<UUID, Object>();
    private final Map<UUID, Object> titleTasks = new ConcurrentHashMap<UUID, Object>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<UUID, Long>();
    private final Random random = new Random();
    private Object queueTask;
    private Economy economy;

    public RtpManager(Main plugin) {
        this.plugin = plugin;
        this.setupEconomy();
        this.loadCache();
        this.startQueueProcessor();
    }

    private void setupEconomy() {
        if (this.plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider rsp = this.plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            this.economy = (Economy)rsp.getProvider();
        }
    }

    private FileConfiguration getConfig() {
        return this.plugin.getConfigManager().getModuleConfig("rtp");
    }

    public void queueRtp(Player player, World world) {
        FileConfiguration config = this.getConfig();
        boolean queueEnabled = config.getBoolean("queue.enabled", true);
        int maxConcurrent = config.getInt("queue.max-concurrent", 3);
        if (this.isPlayerInRtp(player.getUniqueId())) {
            Main.sendMessage(this.plugin, (CommandSender)player, this.plugin.getLanguageManager().getMessage("rtp.already-teleporting"));
            return;
        }
        this.pendingRtpPlayers.add(player.getUniqueId());
        if (!queueEnabled || this.activeRtpPlayers.size() < maxConcurrent) {
            this.startRtp(player, world);
        } else {
            this.rtpQueue.add(new RtpRequest(player, world));
            int position = this.getQueuePosition(player.getUniqueId());
            HashMap<String, String> placeholders = new HashMap<String, String>();
            placeholders.put("%position%", String.valueOf(position));
            this.sendRtpMessage(player, "rtp.queue", placeholders);
            this.sendRtpTitle(player, "rtp.titles.queue", placeholders);
            this.sendRtpActionBar(player, "rtp.actionbar.queue", placeholders);
        }
    }

    private void startQueueProcessor() {
        this.queueTask = FoliaScheduler.runTaskTimerAsync((Plugin)this.plugin, () -> {
            FileConfiguration config = this.getConfig();
            int maxConcurrent = config.getInt("queue.max-concurrent", 3);
            while (!this.rtpQueue.isEmpty() && this.activeRtpPlayers.size() < maxConcurrent) {
                RtpRequest request = this.rtpQueue.poll();
                if (request == null || !request.player.isOnline()) continue;
                FoliaScheduler.runAtEntity((Plugin)this.plugin, (Entity)request.player, () -> this.startRtp(request.player, request.world));
            }
        }, 20L, 20L);
    }

    private int getQueuePosition(UUID playerId) {
        int pos = 1;
        for (RtpRequest req : this.rtpQueue) {
            if (req.player.getUniqueId().equals(playerId)) {
                return pos;
            }
            ++pos;
        }
        return pos;
    }

    public boolean isPlayerInRtp(UUID playerId) {
        return this.activeRtpPlayers.contains(playerId) || this.pendingRtpPlayers.contains(playerId);
    }

    public void removeFromRtp(UUID playerId) {
        this.activeRtpPlayers.remove(playerId);
        this.pendingRtpPlayers.remove(playerId);
        this.rtpQueue.removeIf(req -> req.player.getUniqueId().equals(playerId));
        this.restoreWalkSpeed(playerId);
        this.stopActionBarTask(playerId);
        this.stopTitleTask(playerId);
        this.removeInvisibility(playerId);
    }

    private void removeInvisibility(UUID playerId) {
        Player player = Bukkit.getPlayer((UUID)playerId);
        if (player != null && player.isOnline()) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    public void startRtp(Player player, World world) {
        Location cachedLoc;
        long elapsed;
        Long lastUsed;
        int cooldownSeconds;
        FileConfiguration config = this.getConfig();
        String cooldownBypassPerm = this.plugin.getAliasManager().getSubpermission("rtp", "cooldown_bypass");
        if (!player.hasPermission(cooldownBypassPerm) && (cooldownSeconds = this.getCooldownForPlayer(player, config)) > 0 && (lastUsed = this.cooldowns.get(player.getUniqueId())) != null && (elapsed = (System.currentTimeMillis() - lastUsed) / 1000L) < (long)cooldownSeconds) {
            long remaining = (long)cooldownSeconds - elapsed;
            HashMap<String, String> placeholders = new HashMap<String, String>();
            placeholders.put("%time%", String.valueOf(remaining));
            this.sendRtpMessage(player, "rtp.cooldown", placeholders);
            return;
        }
        this.activeRtpPlayers.add(player.getUniqueId());
        double cost = config.getDouble("cost.amount", 0.0);
        String freePerm = this.plugin.getAliasManager().getSubpermission("rtp", "free");
        if (cost > 0.0 && !player.hasPermission(freePerm) && this.economy != null) {
            if (!this.economy.has((OfflinePlayer)player, cost)) {
                HashMap<String, String> placeholders = new HashMap<String, String>();
                placeholders.put("%cost%", String.valueOf(cost));
                this.sendRtpMessage(player, "rtp.not-enough-money", placeholders);
                this.removeFromRtp(player.getUniqueId());
                return;
            }
            this.economy.withdrawPlayer((OfflinePlayer)player, cost);
            HashMap<String, String> placeholders = new HashMap<String, String>();
            placeholders.put("%cost%", String.valueOf(cost));
            this.sendRtpMessage(player, "rtp.cost-charged", placeholders);
        }
        if (config.getBoolean("freeze-on-teleport", false)) {
            this.originalWalkSpeeds.put(player.getUniqueId(), Float.valueOf(player.getWalkSpeed()));
            player.setWalkSpeed(0.0f);
        }
        if (config.getBoolean("invisibility-during-rtp", true)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
        }
        this.sendRtpMessage(player, "rtp.searching", null);
        this.sendRtpTitle(player, "rtp.titles.searching", null);
        this.sendRtpActionBar(player, "rtp.actionbar.searching", null);
        if (config.getBoolean("performance.enabled", true)) {
            double minTps = config.getDouble("performance.min-tps", 18.0);
            double currentTps = this.getCurrentTps();
            if (currentTps < minTps) {
                this.sendRtpMessage(player, "rtp.low-tps", null);
            }
        }
        if (config.getBoolean("cache.enabled", false) && (cachedLoc = this.getCachedLocation(world.getName())) != null) {
            this.performTeleport(player, cachedLoc);
            int maxLocations = config.getInt("cache.max-locations", 10);
            List<Location> cached = this.locationCache.get(world.getName());
            if (cached == null || cached.size() < maxLocations) {
                this.findSafeLocationAsync(world).thenAccept(newLoc -> {
                    if (newLoc != null) {
                        this.cacheLocation(world.getName(), (Location)newLoc);
                    }
                });
            }
            return;
        }
        this.findSafeLocationAsync(world).thenAccept(location -> {
            if (location == null) {
                FoliaScheduler.runAtEntity((Plugin)this.plugin, (Entity)player, () -> {
                    this.sendRtpMessage(player, "rtp.no-safe-location", null);
                    this.sendRtpTitle(player, "rtp.titles.failed", null);
                    this.removeFromRtp(player.getUniqueId());
                });
                return;
            }
            if (config.getBoolean("cache.enabled", false)) {
                this.cacheLocation(world.getName(), (Location)location);
            }
            FoliaScheduler.runAtEntity((Plugin)this.plugin, (Entity)player, () -> this.performTeleport(player, (Location)location));
        });
    }

    private void sendRtpMessage(Player player, String key, Map<String, String> placeholders) {
        String message = this.plugin.getLanguageManager().getMessage(key);
        if (message != null && !message.isEmpty()) {
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    message = message.replace(entry.getKey(), entry.getValue());
                }
            }
            Main.sendMessage(this.plugin, (CommandSender)player, message);
        }
    }

    private void sendRtpTitle(Player player, String key, Map<String, String> placeholders) {
        String title = this.plugin.getLanguageManager().getMessage(String.valueOf(key) + ".title");
        String subtitle = this.plugin.getLanguageManager().getMessage(String.valueOf(key) + ".subtitle");
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                if (title != null) {
                    title = title.replace(entry.getKey(), entry.getValue());
                }
                if (subtitle == null) continue;
                subtitle = subtitle.replace(entry.getKey(), entry.getValue());
            }
        }
        if (title != null && !title.isEmpty() || subtitle != null && !subtitle.isEmpty()) {
            this.stopTitleTask(player.getUniqueId());
            String finalTitle = title != null && !title.isEmpty() ? ChatColor.translateAlternateColorCodes((char)'&', (String)title) : "";
            String finalSubtitle = subtitle != null && !subtitle.isEmpty() ? ChatColor.translateAlternateColorCodes((char)'&', (String)subtitle) : "";
            player.sendTitle(finalTitle, finalSubtitle, 10, 60, 10);
            UUID playerId = player.getUniqueId();
            Object task = FoliaScheduler.runTaskTimer((Plugin)this.plugin, () -> {
                Player p = Bukkit.getPlayer((UUID)playerId);
                if (p != null && p.isOnline() && this.isPlayerInRtp(playerId)) {
                    p.sendTitle(finalTitle, finalSubtitle, 0, 60, 10);
                } else {
                    this.stopTitleTask(playerId);
                }
            }, 40L, 40L);
            this.titleTasks.put(playerId, task);
        }
    }

    private void stopTitleTask(UUID playerId) {
        Object task = this.titleTasks.remove(playerId);
        if (task != null) {
            FoliaScheduler.cancelTask(task);
        }
    }

    private void sendRtpActionBar(Player player, String key, Map<String, String> placeholders) {
        String message = this.plugin.getLanguageManager().getMessage(key);
        if (message != null && !message.isEmpty()) {
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    message = message.replace(entry.getKey(), entry.getValue());
                }
            }
            this.stopActionBarTask(player.getUniqueId());
            String finalMessage = ChatColor.translateAlternateColorCodes((char)'&', (String)message);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText((String)finalMessage));
            UUID playerId = player.getUniqueId();
            Object task = FoliaScheduler.runTaskTimer((Plugin)this.plugin, () -> {
                Player p = Bukkit.getPlayer((UUID)playerId);
                if (p != null && p.isOnline() && this.isPlayerInRtp(playerId)) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText((String)finalMessage));
                } else {
                    this.stopActionBarTask(playerId);
                }
            }, 20L, 20L);
            this.actionBarTasks.put(playerId, task);
        }
    }

    private void stopActionBarTask(UUID playerId) {
        Object task = this.actionBarTasks.remove(playerId);
        if (task != null) {
            FoliaScheduler.cancelTask(task);
        }
    }

    private void performTeleport(Player player, Location location) {
        FileConfiguration config = this.getConfig();
        String animationType = config.getString("teleport-animation", "standart").toLowerCase();
        TeleportAnimator teleportAnimator = this.plugin.getTeleportAnimator();
        if (animationType.equals("gta_style") && !FoliaScheduler.isFolia()) {
            teleportAnimator.playGtaStyleAnimation(player, location, 1.0, config);
        } else {
            this.teleportPlayerStandart(player, location, config);
        }
        this.setCooldown(player.getUniqueId());
        this.removeFromRtp(player.getUniqueId());
    }

    private void teleportPlayerStandart(Player player, Location location, FileConfiguration config) {
        TeleportAnimator teleportAnimator = this.plugin.getTeleportAnimator();
        this.sendRtpMessage(player, "rtp.loading-chunk", null);
        this.sendRtpTitle(player, "rtp.titles.loading-chunk", null);
        this.sendRtpActionBar(player, "rtp.actionbar.loading-chunk", null);
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        World world = location.getWorld();
        int dx = -1;
        while (dx <= 1) {
            int dz = -1;
            while (dz <= 1) {
                int cx = chunkX + dx;
                int cz = chunkZ + dz;
                if (!world.isChunkLoaded(cx, cz)) {
                    world.getChunkAt(cx, cz);
                }
                ++dz;
            }
            ++dx;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 1, false, false, false));
        FoliaScheduler.teleportAsync((Entity)player, location, null);
        FoliaScheduler.runAtEntityLater((Plugin)this.plugin, (Entity)player, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            String successMsg = this.plugin.getLanguageManager().getMessage("rtp.success");
            if (successMsg != null && !successMsg.isEmpty()) {
                Main.sendMessage(this.plugin, (CommandSender)player, successMsg.replace("%world%", location.getWorld().getName()).replace("%x%", String.valueOf(location.getBlockX())).replace("%z%", String.valueOf(location.getBlockZ())));
            }
            HashMap<String, String> placeholders = new HashMap<String, String>();
            placeholders.put("%world%", location.getWorld().getName());
            placeholders.put("%x%", String.valueOf(location.getBlockX()));
            placeholders.put("%z%", String.valueOf(location.getBlockZ()));
            this.sendRtpTitle(player, "rtp.titles.success", placeholders);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
            double radius = config.getDouble("effects.teleport-particle.radius", 2.0);
            if (radius > 0.0 && config.getBoolean("effects.show-particles", true)) {
                teleportAnimator.runParticleAnimation(player, radius, config);
            }
        }, 60L);
    }

    private void restoreWalkSpeed(UUID playerId) {
        Player player;
        Float original = this.originalWalkSpeeds.remove(playerId);
        if (original != null && (player = Bukkit.getPlayer((UUID)playerId)) != null && player.isOnline()) {
            player.setWalkSpeed(original.floatValue());
        }
    }

    private int getCooldownForPlayer(Player player, FileConfiguration config) {
        String cooldownBypassPerm = this.plugin.getAliasManager().getSubpermission("rtp", "cooldown_bypass");
        String cooldownPrefix = cooldownBypassPerm.replace("bypass", "");
        for (PermissionAttachmentInfo perm : player.getEffectivePermissions()) {
            String permission = perm.getPermission();
            if (!permission.startsWith(cooldownPrefix) || permission.equals(cooldownBypassPerm)) continue;
            try {
                String seconds = permission.substring(cooldownPrefix.length());
                return Integer.parseInt(seconds);
            }
            catch (NumberFormatException numberFormatException) {
                
            }
        }
        return config.getInt("cooldown.default", 60);
    }

    private void setCooldown(UUID playerId) {
        this.cooldowns.put(playerId, System.currentTimeMillis());
    }

    public CompletableFuture<Location> findSafeLocationAsync(World world) {
        return CompletableFuture.supplyAsync(() -> {
            FileConfiguration config = this.getConfig();
            String worldName = world.getName();
            int minY = config.getInt("min-y", 64);
            int maxY = config.getInt("max-y", 256);
            int minX = config.getInt("min-x", 0);
            int maxX = config.getInt("max-x", 0);
            int minZ = config.getInt("min-z", 0);
            int maxZ = config.getInt("max-z", 0);
            if (config.isConfigurationSection("worlds." + worldName)) {
                minY = config.getInt("worlds." + worldName + ".min-y", minY);
                maxY = config.getInt("worlds." + worldName + ".max-y", maxY);
                minX = config.getInt("worlds." + worldName + ".min-x", minX);
                maxX = config.getInt("worlds." + worldName + ".max-x", maxX);
                minZ = config.getInt("worlds." + worldName + ".min-z", minZ);
                maxZ = config.getInt("worlds." + worldName + ".max-z", maxZ);
            }
            List blockedBlocks = config.getStringList("blocked-blocks");
            WorldBorder border = world.getWorldBorder();
            double borderSize = border.getSize() / 2.0;
            Location borderCenter = border.getCenter();
            if (minX == 0 && maxX == 0) {
                minX = (int)(borderCenter.getX() - borderSize);
                maxX = (int)(borderCenter.getX() + borderSize);
            }
            if (minZ == 0 && maxZ == 0) {
                minZ = (int)(borderCenter.getZ() - borderSize);
                maxZ = (int)(borderCenter.getZ() + borderSize);
            }
            int mainAttempt = 0;
            while (mainAttempt < 5) {
                int startZ;
                int startX = minX + this.random.nextInt(Math.max(1, maxX - minX));
                Location result = this.searchChunkAndNeighbors(world, startX, startZ = minZ + this.random.nextInt(Math.max(1, maxZ - minZ)), minY, maxY, blockedBlocks);
                if (result != null) {
                    return result;
                }
                ++mainAttempt;
            }
            return null;
        });
    }

    private Location searchChunkAndNeighbors(World world, int centerX, int centerZ, int minY, int maxY, List<String> blockedBlocks) {
        int chunkX = centerX >> 4;
        int chunkZ = centerZ >> 4;
        try {
            int[][] neighborOffsets;
            CompletableFuture chunkFuture = world.getChunkAtAsync(chunkX, chunkZ);
            Chunk chunk = (Chunk)chunkFuture.join();
            Location result = this.findSafeLocationInChunk(world, chunkX, chunkZ, minY, maxY, blockedBlocks);
            if (result != null) {
                return result;
            }
            int[][] nArrayArray = new int[4][];
            int[] nArray = new int[2];
            nArray[0] = 1;
            nArrayArray[0] = nArray;
            int[] nArray2 = new int[2];
            nArray2[0] = -1;
            nArrayArray[1] = nArray2;
            int[] nArray3 = new int[2];
            nArray3[1] = 1;
            nArrayArray[2] = nArray3;
            int[] nArray4 = new int[2];
            nArray4[1] = -1;
            nArrayArray[3] = nArray4;
            int[][] nArrayArray2 = neighborOffsets = nArrayArray;
            int n = neighborOffsets.length;
            int n2 = 0;
            while (n2 < n) {
                Location neighborResult;
                int[] offset = nArrayArray2[n2];
                int neighborChunkX = chunkX + offset[0];
                int neighborChunkZ = chunkZ + offset[1];
                if (world.isChunkLoaded(neighborChunkX, neighborChunkZ) && (neighborResult = this.findSafeLocationInChunk(world, neighborChunkX, neighborChunkZ, minY, maxY, blockedBlocks)) != null) {
                    return neighborResult;
                }
                ++n2;
            }
        }
        catch (Exception exception) {
            
        }
        return null;
    }

    private Location findSafeLocationInChunk(World world, int chunkX, int chunkZ, int minY, int maxY, List<String> blockedBlocks) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int attempt = 0;
        while (attempt < 16) {
            int z;
            int x = baseX + this.random.nextInt(16);
            int highestY = world.getHighestBlockYAt(x, z = baseZ + this.random.nextInt(16));
            if (highestY >= minY && highestY <= maxY) {
                Block block = world.getBlockAt(x, highestY, z);
                Block aboveBlock = world.getBlockAt(x, highestY + 1, z);
                Block headBlock = world.getBlockAt(x, highestY + 2, z);
                if (!this.isBlockBlocked(block, blockedBlocks) && !this.isBlockBlocked(aboveBlock, blockedBlocks) && aboveBlock.isPassable() && headBlock.isPassable()) {
                    return new Location(world, (double)x + 0.5, (double)(highestY + 1), (double)z + 0.5);
                }
            }
            ++attempt;
        }
        return null;
    }

    private boolean isBlockBlocked(Block block, List<String> blockedBlocks) {
        String blockName = block.getType().name();
        for (String blocked : blockedBlocks) {
            if (!blockName.equalsIgnoreCase(blocked) && !blockName.contains(blocked.toUpperCase())) continue;
            return true;
        }
        return block.isLiquid();
    }

    private Location getCachedLocation(String worldName) {
        List<Location> cached = this.locationCache.get(worldName);
        if (cached == null || cached.isEmpty()) {
            return null;
        }
        int index = this.random.nextInt(cached.size());
        return cached.get(index).clone();
    }

    private void cacheLocation(String worldName, Location location) {
        FileConfiguration config = this.getConfig();
        int maxLocations = config.getInt("cache.max-locations", 10);
        this.locationCache.computeIfAbsent(worldName, k -> new ArrayList());
        List<Location> cached = this.locationCache.get(worldName);
        if (cached.size() < maxLocations) {
            cached.add(location.clone());
            this.saveCache();
        }
    }

    public void refillCache(World world) {
        FileConfiguration config = this.getConfig();
        if (!config.getBoolean("cache.enabled", false)) {
            return;
        }
        int maxLocations = config.getInt("cache.max-locations", 10);
        String worldName = world.getName();
        List cached = this.locationCache.computeIfAbsent(worldName, k -> new ArrayList());
        int needed = maxLocations - cached.size();
        int i = 0;
        while (i < needed) {
            this.findSafeLocationAsync(world).thenAccept(loc -> {
                if (loc != null) {
                    List list2 = cached;
                    synchronized (list2) {
                        if (cached.size() < maxLocations) {
                            cached.add(loc);
                            this.saveCache();
                        }
                    }
                }
            });
            ++i;
        }
    }

    private void loadCache() {
        File cacheFile = new File(this.plugin.getDataFolder(), "database/rtp_cache.dat");
        if (!cacheFile.exists()) {
            return;
        }
        try (DataInputStream dis = new DataInputStream(new FileInputStream(cacheFile))) {
            int worldCount = dis.readInt();
            int i = 0;
            while (i < worldCount) {
                String worldName = dis.readUTF();
                int locCount = dis.readInt();
                ArrayList<Location> locations = new ArrayList<Location>();
                int j = 0;
                while (j < locCount) {
                    double x = dis.readDouble();
                    double y = dis.readDouble();
                    double z = dis.readDouble();
                    World world = Bukkit.getWorld((String)worldName);
                    if (world != null) {
                        locations.add(new Location(world, x, y, z));
                    }
                    ++j;
                }
                if (!locations.isEmpty()) {
                    this.locationCache.put(worldName, locations);
                }
                ++i;
            }
        }
        catch (IOException e) {
            this.plugin.getLogger().warning("RTP cache y\u00fcklenemedi: " + e.getMessage());
        }
    }

    private void saveCache() {
        File cacheFile = new File(this.plugin.getDataFolder(), "database/rtp_cache.dat");
        cacheFile.getParentFile().mkdirs();
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(cacheFile))) {
            dos.writeInt(this.locationCache.size());
            for (Map.Entry<String, List<Location>> entry : this.locationCache.entrySet()) {
                dos.writeUTF(entry.getKey());
                List<Location> locs = entry.getValue();
                dos.writeInt(locs.size());
                for (Location loc : locs) {
                    dos.writeDouble(loc.getX());
                    dos.writeDouble(loc.getY());
                    dos.writeDouble(loc.getZ());
                }
            }
        }
        catch (IOException e) {
            this.plugin.getLogger().warning("RTP cache kaydedilemedi: " + e.getMessage());
        }
    }

    private double getCurrentTps() {
        try {
            Object server = Bukkit.getServer().getClass().getMethod("getServer", new Class[0]).invoke((Object)Bukkit.getServer(), new Object[0]);
            double[] recentTps = (double[])server.getClass().getField("recentTps").get(server);
            return recentTps[0];
        }
        catch (Exception e) {
            return 20.0;
        }
    }

    public void cancelRtp(Player player, String reason) {
        if (!this.isPlayerInRtp(player.getUniqueId())) {
            return;
        }
        TeleportManager teleportManager = this.plugin.getTeleportManager();
        teleportManager.cancelTeleport(player, reason);
        this.removeFromRtp(player.getUniqueId());
    }

    public void shutdown() {
        if (this.queueTask != null) {
            FoliaScheduler.cancelTask(this.queueTask);
        }
        this.saveCache();
        for (UUID playerId : this.originalWalkSpeeds.keySet()) {
            this.restoreWalkSpeed(playerId);
        }
        this.activeRtpPlayers.clear();
        this.pendingRtpPlayers.clear();
        this.rtpQueue.clear();
    }

    public static class RtpRequest {
        public final Player player;
        public final World world;
        public final long timestamp;

        public RtpRequest(Player player, World world) {
            this.player = player;
            this.world = world;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
