package org.widnees.widCore.manager;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.widnees.widCore.Main;
import org.widnees.widCore.util.FoliaScheduler;

public class MobStackerManager {
    private final Main plugin;
    private final NamespacedKey stackKey;
    private final NamespacedKey spawnReasonKey;
    private FileConfiguration config;
    private final Map<UUID, Object> visibilityTasks = new ConcurrentHashMap<UUID, Object>();
    private final Set<UUID> stackedMobCache = ConcurrentHashMap.newKeySet();
    private int stackRadius;
    private double stackRadiusSq;
    private int maxStackSize;
    private int checkIntervalSeconds;
    private double nameVisibleDistanceSq;
    private String stackNametag;
    private Set<EntityType> blacklistedTypes;
    private Set<EntityType> whitelistedTypes;
    private List<String> blacklistedWorlds;
    private Set<CreatureSpawnEvent.SpawnReason> allowedSpawnReasons;
    private Set<EntityDamageEvent.DamageCause> killEntireStackCauses;
    private final MobSimilarity.Options similarityOptions = new MobSimilarity.Options();
    private boolean neverStackTamed;
    private boolean neverStackNamed;
    private boolean neverStackLeashed;
    private boolean neverStackMounted;
    private boolean neverStackCarryingChest;
    private boolean neverStackConverting;
    private Object mergeTask;

    public MobStackerManager(Main plugin) {
        this.plugin = plugin;
        this.stackKey = new NamespacedKey((Plugin) plugin, "mob_stack_amount");
        this.spawnReasonKey = new NamespacedKey((Plugin) plugin, "mob_stack_spawn_reason");
        this.loadConfig();
    }
    public void loadConfig() {
        this.config = this.plugin.getConfigManager().getModuleConfig("mobstacker");
        this.stackRadius = Math.max(1, this.config.getInt("stack-radius", 5));
        this.stackRadiusSq = (double) this.stackRadius * (double) this.stackRadius;
        this.maxStackSize = Math.max(2, this.config.getInt("max-stack-size", 256));
        this.checkIntervalSeconds = Math.max(1, this.config.getInt("check-interval-seconds", 5));
        double dist = this.config.getDouble("name-visible-distance", 10.0);
        this.nameVisibleDistanceSq = dist * dist;
        this.stackNametag = this.config.getString("stack-nametag", "&e(x{amount}) &f{type}");
        this.blacklistedTypes = this.parseEntityTypes(this.config.getStringList("blacklisted-mobs"));
        this.whitelistedTypes = this.parseEntityTypes(this.config.getStringList("whitelisted-mobs"));
        this.blacklistedWorlds = this.config.getStringList("blacklisted-worlds");
        this.allowedSpawnReasons = this.parseSpawnReasons(this.config.getStringList("stack-spawn-reasons"));
        this.killEntireStackCauses = this.parseDamageCauses(this.config.getStringList("kill-entire-stack-causes"));
        this.neverStackTamed = this.config.getBoolean("never-stack.tamed", true);
        this.neverStackNamed = this.config.getBoolean("never-stack.named", true);
        this.neverStackLeashed = this.config.getBoolean("never-stack.leashed", true);
        this.neverStackMounted = this.config.getBoolean("never-stack.mounted", true);
        this.neverStackCarryingChest = this.config.getBoolean("never-stack.carrying-chest", true);
        this.neverStackConverting = this.config.getBoolean("never-stack.converting", true);
        this.similarityOptions.matchAge = this.config.getBoolean("require-matching.age", true);
        this.similarityOptions.matchVillagerProfession = this.config.getBoolean("require-matching.villager-profession", true);
        this.similarityOptions.matchVillagerType = this.config.getBoolean("require-matching.villager-type", true);
        this.similarityOptions.matchColorAndVariant = this.config.getBoolean("require-matching.color-and-variant", true);
        this.similarityOptions.matchSlimeSize = this.config.getBoolean("require-matching.slime-size", true);
        this.similarityOptions.matchCreeperPowered = this.config.getBoolean("require-matching.creeper-powered", true);
        this.similarityOptions.matchEquipment = this.config.getBoolean("require-matching.equipment", false);
        this.stackedMobCache.clear();
        this.startMergeTask();
    }

    public void shutdown() {
        this.stopMergeTask();
        for (Object task : this.visibilityTasks.values()) {
            FoliaScheduler.cancelTask(task);
        }
        this.visibilityTasks.clear();
        this.stackedMobCache.clear();
    }

    public void markSpawnReason(LivingEntity entity, CreatureSpawnEvent.SpawnReason reason) {
        if (entity == null || reason == null) {
            return;
        }
        entity.getPersistentDataContainer().set(this.spawnReasonKey, PersistentDataType.STRING, reason.name());
    }
    public void tryStackNearby(LivingEntity entity) {
        if (!this.canStack(entity)) {
            return;
        }
        if (this.getStackSize(entity) >= this.maxStackSize) {
            return;
        }
        Location loc = entity.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        LivingEntity best = null;
        int bestSize = -1;
        for (Entity target : world.getNearbyEntities(loc, (double) this.stackRadius, (double) this.stackRadius, (double) this.stackRadius)) {
            if (!(target instanceof LivingEntity) || target.equals(entity)) {
                continue;
            }
            LivingEntity livingTarget = (LivingEntity) target;
            if (livingTarget.getType() != entity.getType() || !livingTarget.isValid() || livingTarget.isDead()) {
                continue;
            }
            if (livingTarget.getLocation().distanceSquared(loc) > this.stackRadiusSq) {
                continue;
            }
            if (!this.canStack(livingTarget) || !MobSimilarity.isSimilar(entity, livingTarget, this.similarityOptions)) {
                continue;
            }
            int size = this.getStackSize(livingTarget);
            if (size >= this.maxStackSize) {
                continue;
            }
            if (size > bestSize) {
                bestSize = size;
                best = livingTarget;
            }
        }
        if (best != null) {
            if (this.getStackSize(entity) >= this.getStackSize(best)) {
                this.stackMobs(entity, best);
            } else {
                this.stackMobs(best, entity);
            }
        }
    }

    public void stackMobs(LivingEntity base, LivingEntity target) {
        int baseStack = this.getStackSize(base);
        int targetStack = this.getStackSize(target);
        if (baseStack >= this.maxStackSize || targetStack <= 0) {
            return;
        }
        int moved = Math.min(this.maxStackSize - baseStack, targetStack);
        if (moved <= 0) {
            return;
        }
        this.setStackSize(base, baseStack + moved);
        int remaining = targetStack - moved;
        if (remaining <= 0) {
            this.cancelVisibilityTask(target.getUniqueId());
            this.stackedMobCache.remove(target.getUniqueId());
            target.remove();
        } else {
            this.setStackSize(target, remaining);
        }
    }
    private void startVisibilityTask(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        if (this.visibilityTasks.containsKey(uuid)) {
            return;
        }
        Object task = FoliaScheduler.runAtEntityTimer((Plugin) this.plugin, (Entity) entity, () -> {
            if (entity == null || !entity.isValid() || entity.isDead()) {
                this.cancelVisibilityTask(uuid);
                this.stackedMobCache.remove(uuid);
                return;
            }
            if (!this.isStacked(entity)) {
                this.cancelVisibilityTask(uuid);
                this.stackedMobCache.remove(uuid);
                return;
            }
            if (this.blacklistedWorlds.contains(entity.getWorld().getName())) {
                return;
            }
            boolean shouldShow = false;
            Location entityLoc = entity.getLocation();
            for (Player player : entity.getWorld().getPlayers()) {
                if (!(player.getLocation().distanceSquared(entityLoc) <= this.nameVisibleDistanceSq)) {
                    continue;
                }
                shouldShow = true;
                break;
            }
            if (entity.isCustomNameVisible() != shouldShow) {
                entity.setCustomNameVisible(shouldShow);
            }
        }, 20L, 20L);
        if (task != null) {
            this.visibilityTasks.put(uuid, task);
        }
    }

    private void cancelVisibilityTask(UUID uuid) {
        Object task = this.visibilityTasks.remove(uuid);
        if (task != null) {
            FoliaScheduler.cancelTask(task);
        }
    }

    public boolean handleStackDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        int stackSize = this.getStackSize(entity);
        if (stackSize <= 1) {
            this.removeFromCache(entity.getUniqueId());
            return false;
        }
        if (this.shouldKillEntireStack(entity)) {
            this.removeFromCache(entity.getUniqueId());
            return false;
        }

        List<ItemStack> drops = new ArrayList<ItemStack>(event.getDrops());
        int droppedExp = event.getDroppedExp();
        Location dropLoc = entity.getLocation().clone();
        World world = dropLoc.getWorld();
        this.dropStackLoot(world, dropLoc, drops, droppedExp);

        int remaining = stackSize - 1;
        this.setStackSize(entity, remaining);
        this.healFully(entity);
        if (remaining > 1) {
            entity.setCustomNameVisible(true);
        }
        event.setDroppedExp(0);
        event.getDrops().clear();
        event.setCancelled(true);
        try {
            event.setReviveHealth(this.getMaxHealth(entity));
        } catch (IllegalArgumentException ignored) {
        }
        return true;
    }

    private boolean shouldKillEntireStack(LivingEntity entity) {
        if (this.killEntireStackCauses.isEmpty()) {
            return false;
        }
        EntityDamageEvent last = entity.getLastDamageCause();
        if (last == null) {
            return false;
        }
        return this.killEntireStackCauses.contains(last.getCause());
    }
    public int getStackSize(LivingEntity entity) {
        if (!entity.getPersistentDataContainer().has(this.stackKey, PersistentDataType.INTEGER)) {
            return 1;
        }
        Integer stored = entity.getPersistentDataContainer().get(this.stackKey, PersistentDataType.INTEGER);
        return stored == null ? 1 : stored;
    }

    public void setStackSize(LivingEntity entity, int size) {
        if (size <= 1) {
            entity.getPersistentDataContainer().remove(this.stackKey);
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
            this.cancelVisibilityTask(entity.getUniqueId());
            this.stackedMobCache.remove(entity.getUniqueId());
        } else {
            entity.getPersistentDataContainer().set(this.stackKey, PersistentDataType.INTEGER, size);
            this.updateNametag(entity, size);
            this.stackedMobCache.add(entity.getUniqueId());
            this.startVisibilityTask(entity);
        }
    }

    private void updateNametag(LivingEntity entity, int size) {
        String typeName = entity.getType().name().toLowerCase(Locale.ROOT).replace("_", " ");
        if (typeName.length() > 0) {
            typeName = String.valueOf(typeName.substring(0, 1).toUpperCase(Locale.ROOT)) + typeName.substring(1);
        }
        String name = TextParser.colorize(this.stackNametag.replace("{amount}", String.valueOf(size)).replace("{type}", typeName));
        entity.setCustomName(name);
    }

    public boolean canStack(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return false;
        }
        if (entity instanceof Player || entity instanceof ArmorStand) {
            return false;
        }
        if (this.blacklistedTypes.contains(entity.getType())) {
            return false;
        }
        if (!this.whitelistedTypes.isEmpty() && !this.whitelistedTypes.contains(entity.getType())) {
            return false;
        }
        if (this.blacklistedWorlds.contains(entity.getWorld().getName())) {
            return false;
        }
        if (!this.isAllowedSpawnReason(entity)) {
            return false;
        }
        if (this.neverStackTamed && entity instanceof Tameable && ((Tameable) entity).isTamed()) {
            return false;
        }
        if (this.neverStackLeashed && entity.isLeashed()) {
            return false;
        }
        if (this.neverStackMounted && (!entity.getPassengers().isEmpty() || entity.isInsideVehicle())) {
            return false;
        }
        if (this.neverStackCarryingChest && entity instanceof ChestedHorse && ((ChestedHorse) entity).isCarryingChest()) {
            return false;
        }
        if (this.neverStackConverting && this.isConverting(entity)) {
            return false;
        }
        if (!entity.hasAI()) {
            return false;
        }
        if (this.neverStackNamed && !this.isStacked(entity) && entity.customName() != null) {
            return false;
        }
        return true;
    }

    private boolean isConverting(LivingEntity entity) {
        if (entity instanceof Zombie && ((Zombie) entity).isConverting()) {
            return true;
        }
        if (entity instanceof PiglinAbstract && ((PiglinAbstract) entity).isConverting()) {
            return true;
        }
        return entity instanceof Hoglin && ((Hoglin) entity).isConverting();
    }

    private boolean isAllowedSpawnReason(LivingEntity entity) {
        if (this.allowedSpawnReasons.isEmpty()) {
            return true;
        }
        String stored = entity.getPersistentDataContainer().get(this.spawnReasonKey, PersistentDataType.STRING);
        if (stored == null || stored.isEmpty()) {
            return true;
        }
        try {
            return this.allowedSpawnReasons.contains(CreatureSpawnEvent.SpawnReason.valueOf(stored));
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }
    public boolean isStacked(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(this.stackKey, PersistentDataType.INTEGER);
    }

    public void removeFromCache(UUID uuid) {
        this.cancelVisibilityTask(uuid);
        this.stackedMobCache.remove(uuid);
    }

    private void startMergeTask() {
        this.stopMergeTask();
        long periodTicks = Math.max(20L, (long) this.checkIntervalSeconds * 20L);
        this.mergeTask = FoliaScheduler.runTaskTimer((Plugin) this.plugin, this::scanLoadedWorlds, periodTicks, periodTicks);
    }

    private void stopMergeTask() {
        if (this.mergeTask != null) {
            FoliaScheduler.cancelTask(this.mergeTask);
            this.mergeTask = null;
        }
    }

    private void scanLoadedWorlds() {
        if (FoliaScheduler.isFolia()) {
            this.scanAroundPlayers();
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            if (this.blacklistedWorlds.contains(world.getName())) {
                continue;
            }
            for (LivingEntity entity : new ArrayList<LivingEntity>(world.getLivingEntities())) {
                this.queueStackCheck(entity);
            }
        }
    }

    private void scanAroundPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            FoliaScheduler.runAtEntity((Plugin) this.plugin, player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                World world = player.getWorld();
                if (this.blacklistedWorlds.contains(world.getName())) {
                    return;
                }
                Location loc = player.getLocation();
                double range = Math.max(32.0, this.stackRadius * 4.0);
                for (Entity nearby : world.getNearbyEntities(loc, range, range, range)) {
                    if (nearby instanceof LivingEntity) {
                        this.queueStackCheck((LivingEntity) nearby);
                    }
                }
            });
        }
    }

    private void queueStackCheck(LivingEntity entity) {
        if (!this.canStack(entity)) {
            return;
        }
        FoliaScheduler.runAtEntity((Plugin) this.plugin, entity, () -> {
            if (entity.isValid() && !entity.isDead()) {
                this.tryStackNearby(entity);
            }
        });
    }

    private void dropStackLoot(World world, Location location, List<ItemStack> drops, int droppedExp) {
        if (world == null) {
            return;
        }
        FoliaScheduler.runAtLocation((Plugin) this.plugin, location, () -> {
            for (ItemStack drop : drops) {
                if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) {
                    continue;
                }
                world.dropItemNaturally(location, drop.clone());
            }
            if (droppedExp > 0) {
                ExperienceOrb orb = (ExperienceOrb) world.spawnEntity(location, EntityType.EXPERIENCE_ORB);
                orb.setExperience(droppedExp);
            }
        });
    }

    private void healFully(LivingEntity entity) {
        double maxHealth = this.getMaxHealth(entity);
        if (maxHealth > 0.0) {
            entity.setHealth(maxHealth);
        }
    }

    private double getMaxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute != null) {
            return attribute.getValue();
        }
        return entity.getHealth();
    }
    private Set<EntityType> parseEntityTypes(List<String> names) {
        Set<EntityType> types = EnumSet.noneOf(EntityType.class);
        if (names == null) {
            return types;
        }
        for (String name : names) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            try {
                types.add(EntityType.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return types;
    }

    private Set<CreatureSpawnEvent.SpawnReason> parseSpawnReasons(List<String> names) {
        Set<CreatureSpawnEvent.SpawnReason> reasons = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
        if (names == null) {
            return reasons;
        }
        for (String name : names) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            try {
                reasons.add(CreatureSpawnEvent.SpawnReason.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return reasons;
    }

    private Set<EntityDamageEvent.DamageCause> parseDamageCauses(List<String> names) {
        Set<EntityDamageEvent.DamageCause> causes = EnumSet.noneOf(EntityDamageEvent.DamageCause.class);
        if (names == null) {
            return causes;
        }
        for (String name : names) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            try {
                causes.add(EntityDamageEvent.DamageCause.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return causes;
    }

        @SuppressWarnings("unused")
    private static final String _0xW8b4d3 = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}
