package org.widnees.widCore.manager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.widnees.widCore.Main;
import org.widnees.widCore.util.FoliaScheduler;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DisguiseManager {

    public enum DisguiseType {
        ENTITY, ITEM, PLAYER
    }

    public static class DisguiseData {
        private final DisguiseType type;
        private final String choice;
        private EntityType entityType;       
        private Material material;           
        private String playerName;           
        private String skinValue;
        private String skinSignature;
        private ItemStack[] savedInventory;
        private ItemStack[] savedArmor;
        private ItemStack savedOffhand;
        private String savedDisplayName;
        private String savedPlayerListName;
        private Object ambientTask;          
        private Object selfDisguiseTask;     

        public DisguiseData(DisguiseType type, String choice) {
            this.type = type;
            this.choice = choice;
        }

        public DisguiseType getType() { return type; }
        public String getChoice() { return choice; }
        public EntityType getEntityType() { return entityType; }
        public void setEntityType(EntityType entityType) { this.entityType = entityType; }
        public Material getMaterial() { return material; }
        public void setMaterial(Material material) { this.material = material; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public String getSkinValue() { return skinValue; }
        public void setSkinValue(String skinValue) { this.skinValue = skinValue; }
        public String getSkinSignature() { return skinSignature; }
        public void setSkinSignature(String skinSignature) { this.skinSignature = skinSignature; }
        public ItemStack[] getSavedInventory() { return savedInventory; }
        public void setSavedInventory(ItemStack[] savedInventory) { this.savedInventory = savedInventory; }
        public ItemStack[] getSavedArmor() { return savedArmor; }
        public void setSavedArmor(ItemStack[] savedArmor) { this.savedArmor = savedArmor; }
        public ItemStack getSavedOffhand() { return savedOffhand; }
        public void setSavedOffhand(ItemStack savedOffhand) { this.savedOffhand = savedOffhand; }
        public String getSavedDisplayName() { return savedDisplayName; }
        public void setSavedDisplayName(String savedDisplayName) { this.savedDisplayName = savedDisplayName; }
        public String getSavedPlayerListName() { return savedPlayerListName; }
        public void setSavedPlayerListName(String savedPlayerListName) { this.savedPlayerListName = savedPlayerListName; }
        public Object getAmbientTask() { return ambientTask; }
        public void setAmbientTask(Object ambientTask) { this.ambientTask = ambientTask; }
        public Object getSelfDisguiseTask() { return selfDisguiseTask; }
        public void setSelfDisguiseTask(Object selfDisguiseTask) { this.selfDisguiseTask = selfDisguiseTask; }
    }

    private final Main plugin;
    private final Map<UUID, DisguiseData> activeDisguises = new ConcurrentHashMap<>();

    private static final Set<EntityType> SUPPORTED_ENTITIES = new LinkedHashSet<>();
    static {
        for (EntityType type : EntityType.values()) {
            if (type.isSpawnable() && type != EntityType.PLAYER) {
                SUPPORTED_ENTITIES.add(type);
            }
        }
    }

    private static final Set<Material> SUPPORTED_ITEMS = new LinkedHashSet<>();
    static {
        for (Material mat : Material.values()) {
            if (mat.isItem() && !mat.isAir() && !mat.name().contains("LEGACY")) {
                SUPPORTED_ITEMS.add(mat);
            }
        }
    }

    public DisguiseManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isDisguised(Player player) {
        return activeDisguises.containsKey(player.getUniqueId());
    }

    public DisguiseData getDisguise(Player player) {
        return activeDisguises.get(player.getUniqueId());
    }

    public DisguiseData getDisguise(UUID uuid) {
        return activeDisguises.get(uuid);
    }

    public Map<UUID, DisguiseData> getActiveDisguises() {
        return Collections.unmodifiableMap(activeDisguises);
    }

    public String getEffectiveName(Player player) {
        DisguiseData data = getDisguise(player);
        if (data != null && data.getType() == DisguiseType.PLAYER) {
            return data.getPlayerName();
        }
        return player.getName();
    }

    private void silentUndisguise(Player player) {
        DisguiseData data = activeDisguises.remove(player.getUniqueId());
        if (data == null) return;

        if (data.getAmbientTask() != null) {
            FoliaScheduler.cancelTask(data.getAmbientTask());
        }
        if (data.getSelfDisguiseTask() != null) {
            FoliaScheduler.cancelTask(data.getSelfDisguiseTask());
        }

        try {

            org.widnees.widCore.listener.DisguisePacketHandler.removeDisguise(plugin, player, false);
            org.widnees.widCore.listener.DisguisePacketHandler.removeSelfDisguise(player);
        } catch (NoClassDefFoundError ignored) {}

        restorePlayerState(player, data);
    }

    public boolean disguiseAsEntity(Player player, EntityType entityType) {
        if (!SUPPORTED_ENTITIES.contains(entityType)) return false;

        if (isDisguised(player)) {
            silentUndisguise(player);
        }

        DisguiseData data = new DisguiseData(DisguiseType.ENTITY, entityType.name());
        data.setEntityType(entityType);
        savePlayerState(player, data);
        activeDisguises.put(player.getUniqueId(), data);

        applyInvisibility(player);
        applyEntityDisguise(player, data);
        startAmbientSoundTask(player, data);
        startSelfDisguiseTask(player, data);

        return true;
    }

    public boolean disguiseAsItem(Player player, Material material) {
        if (!SUPPORTED_ITEMS.contains(material)) return false;

        if (isDisguised(player)) {
            silentUndisguise(player);
        }

        DisguiseData data = new DisguiseData(DisguiseType.ITEM, material.name());
        data.setMaterial(material);
        savePlayerState(player, data);
        activeDisguises.put(player.getUniqueId(), data);

        applyInvisibility(player);
        applyItemDisguise(player, data);
        startSelfDisguiseTask(player, data);

        return true;
    }

    public CompletableFuture<Boolean> disguiseAsPlayer(Player player, String targetName) {

        if (isDisguised(player)) {
            silentUndisguise(player);
        }

        DisguiseData data = new DisguiseData(DisguiseType.PLAYER, targetName);
        data.setPlayerName(targetName);
        savePlayerState(player, data);

        Player targetPlayer = Bukkit.getPlayerExact(targetName);
        if (targetPlayer != null && targetPlayer.isOnline()) {

            fetchSkinFromPlayer(targetPlayer, data);

            return CompletableFuture.supplyAsync(() -> true).thenApplyAsync(result -> {
                FoliaScheduler.runTask(plugin, () -> {
                    copyInventoryFrom(player, targetPlayer, data);
                    activeDisguises.put(player.getUniqueId(), data);
                    applyInvisibility(player);
                    applyPlayerDisguise(player, data);
                    startSelfDisguiseTask(player, data);
                });
                return true;
            }).exceptionally(ex -> {
                plugin.getLogger().warning("Failed to disguise as player " + targetName + ": " + ex.getMessage());
                return false;
            });
        } else {

            return fetchSkinFromMojang(targetName).thenApplyAsync(skinData -> {
                if (skinData != null) {
                    data.setSkinValue(skinData[0]);
                    data.setSkinSignature(skinData[1]);
                }
                FoliaScheduler.runTask(plugin, () -> {
                    activeDisguises.put(player.getUniqueId(), data);
                    applyInvisibility(player);
                    applyPlayerDisguise(player, data);
                    startSelfDisguiseTask(player, data);
                });
                return true;
            }).exceptionally(ex -> {
                FoliaScheduler.runTask(plugin, () -> {
                    activeDisguises.put(player.getUniqueId(), data);
                    applyInvisibility(player);
                    applyPlayerDisguise(player, data);
                    startSelfDisguiseTask(player, data);
                });
                return true;
            });
        }
    }

    public void undisguise(Player player) {
        DisguiseData data = activeDisguises.remove(player.getUniqueId());
        if (data == null) return;

        if (data.getAmbientTask() != null) {
            FoliaScheduler.cancelTask(data.getAmbientTask());
        }
        if (data.getSelfDisguiseTask() != null) {
            FoliaScheduler.cancelTask(data.getSelfDisguiseTask());
        }

        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        restorePlayerState(player, data);

        try {
            org.widnees.widCore.listener.DisguisePacketHandler.removeSelfDisguise(player);
            org.widnees.widCore.listener.DisguisePacketHandler.removeDisguise(plugin, player);
        } catch (NoClassDefFoundError ignored) {}
    }

    public void undisguiseAll() {
        for (UUID uuid : new HashSet<>(activeDisguises.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                undisguise(player);
            } else {
                activeDisguises.remove(uuid);
            }
        }
    }

    private void savePlayerState(Player player, DisguiseData data) {
        data.setSavedInventory(cloneInventory(player.getInventory().getContents()));
        data.setSavedArmor(cloneInventory(player.getInventory().getArmorContents()));
        data.setSavedOffhand(player.getInventory().getItemInOffHand().clone());
        data.setSavedDisplayName(player.getDisplayName());
        data.setSavedPlayerListName(player.getPlayerListName());
    }

    private void restorePlayerState(Player player, DisguiseData data) {
        if (data.getSavedDisplayName() != null) {
            player.setDisplayName(data.getSavedDisplayName());
        }
        if (data.getSavedPlayerListName() != null) {
            player.setPlayerListName(data.getSavedPlayerListName());
        }

        if (data.getType() == DisguiseType.PLAYER) {
            player.getInventory().setContents(data.getSavedInventory());
            player.getInventory().setArmorContents(data.getSavedArmor());
            player.getInventory().setItemInOffHand(data.getSavedOffhand());
        }
    }

    private ItemStack[] cloneInventory(ItemStack[] original) {
        if (original == null) return new ItemStack[0];
        ItemStack[] clone = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            clone[i] = original[i] != null ? original[i].clone() : null;
        }
        return clone;
    }

    private void copyInventoryFrom(Player target, Player source, DisguiseData data) {
        target.getInventory().setContents(cloneInventory(source.getInventory().getContents()));
        target.getInventory().setArmorContents(cloneInventory(source.getInventory().getArmorContents()));
        target.getInventory().setItemInOffHand(
                source.getInventory().getItemInOffHand() != null
                        ? source.getInventory().getItemInOffHand().clone()
                        : new ItemStack(Material.AIR));
    }

    private void applyInvisibility(Player player) {

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE,
                0,
                false,
                false,
                false
        ));
    }

    private void applyEntityDisguise(Player player, DisguiseData data) {
        try {
            org.widnees.widCore.listener.DisguisePacketHandler.applyEntityDisguise(plugin, player, data);
            org.widnees.widCore.listener.DisguisePacketHandler.applySelfDisguise(plugin, player, data);
        } catch (NoClassDefFoundError ignored) {
            plugin.getLogger().warning("PacketEvents not found, cannot apply entity disguise.");
        }
    }

    private void applyItemDisguise(Player player, DisguiseData data) {
        try {
            org.widnees.widCore.listener.DisguisePacketHandler.applyItemDisguise(plugin, player, data);
            org.widnees.widCore.listener.DisguisePacketHandler.applySelfDisguise(plugin, player, data);
        } catch (NoClassDefFoundError ignored) {
            plugin.getLogger().warning("PacketEvents not found, cannot apply item disguise.");
        }
    }

    private void applyPlayerDisguise(Player player, DisguiseData data) {
        player.setDisplayName(data.getPlayerName());
        player.setPlayerListName(data.getPlayerName());

        try {
            org.widnees.widCore.listener.DisguisePacketHandler.applyPlayerDisguise(plugin, player, data);
            org.widnees.widCore.listener.DisguisePacketHandler.applySelfDisguise(plugin, player, data);
        } catch (NoClassDefFoundError ignored) {
            plugin.getLogger().warning("PacketEvents not found, cannot apply player disguise.");
        }
    }

    public void refreshDisguiseForObserver(Player observer) {
        for (Map.Entry<UUID, DisguiseData> entry : activeDisguises.entrySet()) {
            Player disguised = Bukkit.getPlayer(entry.getKey());
            if (disguised == null || !disguised.isOnline()) continue;

            DisguiseData data = entry.getValue();

            if (disguised.equals(observer)) {

                try {
                    org.widnees.widCore.listener.DisguisePacketHandler.applySelfDisguise(plugin, disguised, data);
                } catch (NoClassDefFoundError ignored) {}
                continue;
            }

            try {
                switch (data.getType()) {
                    case ENTITY:
                        org.widnees.widCore.listener.DisguisePacketHandler.applyEntityDisguiseForObserver(
                                plugin, disguised, observer, data);
                        break;
                    case ITEM:
                        org.widnees.widCore.listener.DisguisePacketHandler.applyItemDisguiseForObserver(
                                plugin, disguised, observer, data);
                        break;
                    case PLAYER:
                        org.widnees.widCore.listener.DisguisePacketHandler.applyPlayerDisguiseForObserver(
                                plugin, disguised, observer, data);
                        break;
                }
            } catch (NoClassDefFoundError ignored) {}
        }
    }

    private void fetchSkinFromPlayer(Player target, DisguiseData data) {
        try {
            String[] skin = org.widnees.widCore.listener.DisguisePacketHandler.getSkinFromPlayer(target);
            if (skin != null) {
                data.setSkinValue(skin[0]);
                data.setSkinSignature(skin[1]);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to fetch skin from player " + target.getName() + ": " + e.getMessage());
        }
    }

    public CompletableFuture<String[]> fetchSkinFromMojang(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String uuidUrl = "https://api.mojang.com/users/profiles/minecraft/" + playerName;
                HttpURLConnection conn = (HttpURLConnection) new URL(uuidUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() != 200) return null;

                JSONParser parser = new JSONParser();
                JSONObject uuidResponse;
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    uuidResponse = (JSONObject) parser.parse(reader);
                }
                String uuid = (String) uuidResponse.get("id");
                if (uuid == null) return null;

                String profileUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false";
                conn = (HttpURLConnection) new URL(profileUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() != 200) return null;

                JSONObject profileResponse;
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    profileResponse = (JSONObject) parser.parse(reader);
                }

                JSONArray properties = (JSONArray) profileResponse.get("properties");
                if (properties == null) return null;

                for (Object obj : properties) {
                    JSONObject prop = (JSONObject) obj;
                    if ("textures".equals(prop.get("name"))) {
                        String value = (String) prop.get("value");
                        String signature = (String) prop.get("signature");
                        return new String[]{value, signature != null ? signature : ""};
                    }
                }
                return null;
            } catch (Exception e) {
                plugin.getLogger().warning("Mojang API skin fetch failed for " + playerName + ": " + e.getMessage());
                return null;
            }
        });
    }

    private void startAmbientSoundTask(Player player, DisguiseData data) {
        Random random = new Random();
        Object task = FoliaScheduler.runAtEntityTimer(plugin, player, () -> {
            if (!player.isOnline() || !isDisguised(player)) return;

            if (random.nextInt(4) == 0) {
                Sound ambientSound = getMobSound(data.getEntityType(), "AMBIENT");
                if (ambientSound != null) {
                    player.getWorld().playSound(player.getLocation(), ambientSound, 1.0f, 1.0f);
                }
            }
        }, 20L, 20L);
        data.setAmbientTask(task);
    }

    private void startSelfDisguiseTask(Player player, DisguiseData data) {
        Object task = FoliaScheduler.runAtEntityTimer(plugin, player, () -> {
            if (!player.isOnline() || !isDisguised(player)) return;

            int selfFakeId = player.getEntityId() + 100000;
            org.widnees.widCore.listener.DisguisePacketHandler.sendTeleportPacket(player, selfFakeId);
            org.widnees.widCore.listener.DisguisePacketHandler.sendHeadRotationPacket(player, selfFakeId);

            org.widnees.widCore.listener.DisguisePacketHandler.syncFakeEntityForObservers(player);
        }, 1L, 1L);
        data.setSelfDisguiseTask(task);
    }

    public Sound getMobSound(EntityType type, String soundType) {
        String mobName = type.name();
        switch (type) {
            case MUSHROOM_COW: mobName = "MOOSHROOM"; break;
            case SNOWMAN: mobName = "SNOW_GOLEM"; break;
            case IRON_GOLEM: mobName = "IRON_GOLEM"; break;
            case WITHER_SKELETON: mobName = "WITHER_SKELETON"; break;
            case ZOMBIFIED_PIGLIN: mobName = "ZOMBIFIED_PIGLIN"; break;
            default: break;
        }

        String soundName = "ENTITY_" + mobName + "_" + soundType.toUpperCase();
        try {
            return Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Sound getReplacementSound(UUID playerUuid, String originalSoundKey) {
        DisguiseData data = activeDisguises.get(playerUuid);
        if (data == null || data.getType() != DisguiseType.ENTITY) return null;

        String lowerKey = originalSoundKey.toLowerCase();
        if (lowerKey.contains("player.hurt") || lowerKey.contains("player.damage")) {
            return getMobSound(data.getEntityType(), "HURT");
        } else if (lowerKey.contains("player.death")) {
            return getMobSound(data.getEntityType(), "DEATH");
        } else if (lowerKey.contains("player.swim") || lowerKey.contains("player.splash")) {
            return getMobSound(data.getEntityType(), "SWIM");
        } else if (lowerKey.contains("step") || lowerKey.contains("walk")) {
            return getMobSound(data.getEntityType(), "STEP");
        } else if (lowerKey.contains("player.eat")) {
            return getMobSound(data.getEntityType(), "EAT");
        }
        return null;
    }

    public static Set<EntityType> getSupportedEntities() {
        return Collections.unmodifiableSet(SUPPORTED_ENTITIES);
    }

    public static Set<Material> getSupportedItems() {
        return Collections.unmodifiableSet(SUPPORTED_ITEMS);
    }
}
