package org.widnees.widCore.manager;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.TextParser;
import org.widnees.widCore.util.FoliaScheduler;

public class MobStackerManager {
    private final Main plugin;
    private final NamespacedKey stackKey;
    private FileConfiguration config;
    private final Map<UUID, Object> visibilityTasks = new ConcurrentHashMap<UUID, Object>();
    private final Set<UUID> stackedMobCache = ConcurrentHashMap.newKeySet();
    private int stackRadius;
    private double stackRadiusSq;
    private int maxStackSize;
    private double nameVisibleDistanceSq;
    private String stackNametag;
    private Set<EntityType> blacklistedTypes;
    private List<String> blacklistedWorlds;

    public MobStackerManager(Main plugin) {
        this.plugin = plugin;
        this.stackKey = new NamespacedKey((Plugin)plugin, "mob_stack_amount");
        this.loadConfig();
    }

    public void loadConfig() {
        this.config = this.plugin.getConfigManager().getModuleConfig("mobstacker");
        this.stackRadius = this.config.getInt("stack-radius", 5);
        this.stackRadiusSq = Math.pow(this.stackRadius, 2.0);
        this.maxStackSize = this.config.getInt("max-stack-size", 50);
        double dist = this.config.getDouble("name-visible-distance", 10.0);
        this.nameVisibleDistanceSq = dist * dist;
        this.stackNametag = this.config.getString("stack-nametag", "&e(x{amount}) &f{type}");
        this.blacklistedTypes = EnumSet.noneOf(EntityType.class);
        for (String s : this.config.getStringList("blacklisted-mobs")) {
            try {
                this.blacklistedTypes.add(EntityType.valueOf((String)s.toUpperCase()));
            }
            catch (IllegalArgumentException illegalArgumentException) {

            }
        }
        this.blacklistedWorlds = this.config.getStringList("blacklisted-worlds");
        this.stackedMobCache.clear();
    }

    public void shutdown() {
        for (Object task : this.visibilityTasks.values()) {
            FoliaScheduler.cancelTask(task);
        }
        this.visibilityTasks.clear();
        this.stackedMobCache.clear();
    }

    public void tryStackNearby(LivingEntity entity) {
        if (!this.canStack(entity)) {
            return;
        }
        if (this.blacklistedWorlds.contains(entity.getWorld().getName())) {
            return;
        }
        Location loc = entity.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        for (Entity target : world.getNearbyEntities(loc, (double)this.stackRadius, (double)this.stackRadius, (double)this.stackRadius)) {
            LivingEntity livingTarget;
            if (!(target instanceof LivingEntity) || target.equals(entity) || (livingTarget = (LivingEntity)target).getType() != entity.getType() || !livingTarget.isValid() || !this.canStack(livingTarget)) continue;
            this.stackMobs(livingTarget, entity);
            return;
        }
    }

    public void stackMobs(LivingEntity base, LivingEntity target) {
        int targetStack;
        int baseStack = this.getStackSize(base);
        int newSize = baseStack + (targetStack = this.getStackSize(target));
        if (newSize > this.maxStackSize) {
            return;
        }
        this.setStackSize(base, newSize);
        this.cancelVisibilityTask(target.getUniqueId());
        target.remove();
        this.stackedMobCache.add(base.getUniqueId());
        this.stackedMobCache.remove(target.getUniqueId());
        this.startVisibilityTask(base);
    }

    private void startVisibilityTask(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        if (this.visibilityTasks.containsKey(uuid)) {
            return;
        }
        Object task = FoliaScheduler.runAtEntityTimer((Plugin)this.plugin, (Entity)entity, () -> {
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
                if (!(player.getLocation().distanceSquared(entityLoc) <= this.nameVisibleDistanceSq)) continue;
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

    public void unstackOne(LivingEntity entity) {
        int currentStack = this.getStackSize(entity);
        if (currentStack > 1) {
            int newSize = currentStack - 1;
            Location spawnLoc = entity.getLocation();
            EntityType type = entity.getType();
            FoliaScheduler.runAtLocation((Plugin)this.plugin, spawnLoc, () -> {
                LivingEntity newEntity = (LivingEntity)spawnLoc.getWorld().spawnEntity(spawnLoc, type);
                if (newEntity.getEquipment() != null && entity.getEquipment() != null) {
                    newEntity.getEquipment().setArmorContents(entity.getEquipment().getArmorContents());
                    newEntity.getEquipment().setItemInMainHand(entity.getEquipment().getItemInMainHand());
                    newEntity.getEquipment().setItemInOffHand(entity.getEquipment().getItemInOffHand());
                }
            });
            this.setStackSize(entity, newSize);
            if (newSize <= 1) {
                entity.getPersistentDataContainer().remove(this.stackKey);
                entity.setCustomName(null);
                entity.setCustomNameVisible(false);
                this.cancelVisibilityTask(entity.getUniqueId());
                this.stackedMobCache.remove(entity.getUniqueId());
            }
        }
    }

    public int getStackSize(LivingEntity entity) {
        if (!entity.getPersistentDataContainer().has(this.stackKey, PersistentDataType.INTEGER)) {
            return 1;
        }
        return (Integer)entity.getPersistentDataContainer().get(this.stackKey, PersistentDataType.INTEGER);
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
        String typeName = entity.getType().name().toLowerCase().replace("_", " ");
        if (typeName.length() > 0) {
            typeName = String.valueOf(typeName.substring(0, 1).toUpperCase()) + typeName.substring(1);
        }
        String name = TextParser.colorize(this.stackNametag.replace("{amount}", String.valueOf(size)).replace("{type}", typeName));
        entity.setCustomName(name);
    }

    public boolean canStack(LivingEntity entity) {
        if (this.blacklistedTypes.contains(entity.getType())) {
            return false;
        }
        if (entity instanceof Player) {
            return false;
        }
        if (entity instanceof Tameable && ((Tameable)entity).isTamed()) {
            return false;
        }
        return this.isStacked(entity) || entity.customName() == null;
    }

    public boolean isStacked(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(this.stackKey, PersistentDataType.INTEGER);
    }

    public void removeFromCache(UUID uuid) {
        this.cancelVisibilityTask(uuid);
        this.stackedMobCache.remove(uuid);
    }
}
