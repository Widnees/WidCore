package org.widnees.widCore.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.DisguiseManager;
import org.widnees.widCore.util.FoliaScheduler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DisguisePacketHandler extends PacketListenerAbstract {

    private final Main plugin;
    private static DisguisePacketHandler instance;

    private static final Map<UUID, Integer> observerFakeIds = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> observerFakeUuids = new ConcurrentHashMap<>();

    private static final AtomicInteger fakeIdCounter = new AtomicInteger(900000);

    public DisguisePacketHandler(Main plugin) {
        super(PacketListenerPriority.HIGH);
        this.plugin = plugin;
    }

    public static void register(Main plugin) {
        if (instance != null) {
            unregister();
        }
        instance = new DisguisePacketHandler(plugin);
        PacketEvents.getAPI().getEventManager().registerListener(instance);
    }

    public static void unregister() {
        if (instance != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(instance);
            instance = null;
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player observer = (Player) event.getPlayer();

        if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
            int entityId = packet.getEntityId();
            Player disguised = findPlayerByEntityId(entityId);
            if (disguised != null && !disguised.equals(observer)
                    && plugin.getDisguiseManager().getDisguise(disguised) != null) {

                event.setCancelled(true);
                DisguiseManager.DisguiseData data = plugin.getDisguiseManager().getDisguise(disguised);
                FoliaScheduler.runTaskLater(plugin, () ->
                        sendFakeEntityToObserver(disguised, observer, data), 2L);
            }
        }
    }

    public static void applyEntityDisguise(Main plugin, Player player, DisguiseManager.DisguiseData data) {
        int fakeId = fakeIdCounter.incrementAndGet();
        observerFakeIds.put(player.getUniqueId(), fakeId);
        observerFakeUuids.put(player.getUniqueId(), UUID.randomUUID());

        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.equals(player)) continue;
            if (!observer.canSee(player)) continue;

            sendEntityDestroy(observer, player.getEntityId());
            applyEntityDisguiseForObserver(plugin, player, observer, data);
        }
    }

    public static void applyEntityDisguiseForObserver(Main plugin, Player player, Player observer,
                                                       DisguiseManager.DisguiseData data) {

        Integer oldFakeId = observerFakeIds.get(player.getUniqueId());
        if (oldFakeId != null) sendEntityDestroy(observer, oldFakeId);

        FoliaScheduler.runTaskLater(plugin, () -> {
            sendFakeEntityToObserver(player, observer, data);
        }, 2L);
    }

    public static void applyItemDisguise(Main plugin, Player player, DisguiseManager.DisguiseData data) {
        int fakeId = fakeIdCounter.incrementAndGet();
        observerFakeIds.put(player.getUniqueId(), fakeId);
        observerFakeUuids.put(player.getUniqueId(), UUID.randomUUID());

        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.equals(player)) continue;
            if (!observer.canSee(player)) continue;
            sendEntityDestroy(observer, player.getEntityId());
            applyItemDisguiseForObserver(plugin, player, observer, data);
        }
    }

    public static void applyItemDisguiseForObserver(Main plugin, Player player, Player observer,
                                                     DisguiseManager.DisguiseData data) {
        Integer oldFakeId = observerFakeIds.get(player.getUniqueId());
        if (oldFakeId != null) sendEntityDestroy(observer, oldFakeId);

        FoliaScheduler.runTaskLater(plugin, () -> {
            sendFakeEntityToObserver(player, observer, data);
        }, 2L);
    }

    public static void applyPlayerDisguise(Main plugin, Player player, DisguiseManager.DisguiseData data) {
        int fakeId = fakeIdCounter.incrementAndGet();
        observerFakeIds.put(player.getUniqueId(), fakeId);
        observerFakeUuids.put(player.getUniqueId(), UUID.randomUUID());

        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.equals(player)) continue;
            if (!observer.canSee(player)) continue;
            sendEntityDestroy(observer, player.getEntityId());
            applyPlayerDisguiseForObserver(plugin, player, observer, data);
        }
    }

    public static void applyPlayerDisguiseForObserver(Main plugin, Player player, Player observer,
                                                       DisguiseManager.DisguiseData data) {
        Integer oldFakeId = observerFakeIds.get(player.getUniqueId());
        if (oldFakeId != null) sendEntityDestroy(observer, oldFakeId);

        FoliaScheduler.runTaskLater(plugin, () -> {
            sendFakeEntityToObserver(player, observer, data);
        }, 2L);
    }

    private static void sendFakeEntityToObserver(Player disguised, Player observer,
                                                   DisguiseManager.DisguiseData data) {
        Integer fakeId = observerFakeIds.get(disguised.getUniqueId());
        UUID fakeUuid = observerFakeUuids.get(disguised.getUniqueId());
        if (fakeId == null || fakeUuid == null) return;

        try {
            Location loc = disguised.getLocation();
            float yaw = getAdjustedYaw(loc.getYaw(), data);

            switch (data.getType()) {
                case ENTITY: {
                    EntityType peType = getBukkitToPacketEventsEntityType(data.getEntityType());
                    if (peType == null) return;
                    WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                            fakeId, Optional.of(fakeUuid), peType,
                            new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                            loc.getPitch(), yaw, yaw,
                            0, Optional.of(new Vector3d(0, 0, 0))
                    );
                    PacketEvents.getAPI().getPlayerManager().sendPacket(observer, spawnPacket);
                    break;
                }

                case ITEM: {
                    WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                            fakeId, Optional.of(fakeUuid), EntityTypes.ITEM,
                            new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                            loc.getPitch(), loc.getYaw(), loc.getYaw(),
                            0, Optional.of(new Vector3d(0, 0, 0))
                    );
                    PacketEvents.getAPI().getPlayerManager().sendPacket(observer, spawnPacket);
                    sendItemMetadata(observer, fakeId, data.getMaterial());
                    break;
                }
                case PLAYER: {
                    Main mainPlugin = (Main) Bukkit.getPluginManager().getPlugin("WidCore");
                    UserProfile disguisedProfile = createDisguisedProfile(fakeUuid, data.getPlayerName(), data);
                    sendPlayerInfoAddFake(observer, fakeUuid, disguisedProfile, data.getPlayerName(),
                            toPacketEventsGameMode(disguised.getGameMode()));
                    FoliaScheduler.runTaskLater(mainPlugin, () -> {
                        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                                fakeId, Optional.of(fakeUuid), EntityTypes.PLAYER,
                                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                                loc.getPitch(), loc.getYaw(), loc.getYaw(),
                                0, Optional.of(new Vector3d(0, 0, 0))
                        );
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, spawnPacket);
                        FoliaScheduler.runTaskLater(mainPlugin, () ->
                                sendPlayerInfoUpdateListed(observer, fakeUuid, false), 2L);
                    }, 2L);
                    return;
                }
            }
        } catch (Exception e) {

        }
    }

    public static void removeDisguise(Main plugin, Player player, boolean respawnReal) {
        Integer fakeId = observerFakeIds.remove(player.getUniqueId());
        observerFakeUuids.remove(player.getUniqueId());

        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.equals(player)) continue;
            if (fakeId != null) sendEntityDestroy(observer, fakeId);
            if (respawnReal) {
                sendRealPlayerSpawn(observer, player);
            }
        }
    }

    public static void removeDisguise(Main plugin, Player player) {
        removeDisguise(plugin, player, true);
    }

    private static void sendRealPlayerSpawn(Player observer, Player player) {
        try {
            Location loc = player.getLocation();
            WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                    player.getEntityId(), Optional.of(player.getUniqueId()), EntityTypes.PLAYER,
                    new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                    loc.getPitch(), loc.getYaw(), loc.getYaw(),
                    0, Optional.of(new Vector3d(0, 0, 0))
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, spawnPacket);
            sendRealPlayerEquipment(observer, player);
        } catch (Exception ignored) {}
    }

    private static void sendRealPlayerEquipment(Player observer, Player player) {
        try {
            List<com.github.retrooper.packetevents.protocol.player.Equipment> equipmentList = new ArrayList<>();
            addEquipment(equipmentList,
                    com.github.retrooper.packetevents.protocol.player.EquipmentSlot.MAIN_HAND,
                    player.getInventory().getItemInMainHand());
            addEquipment(equipmentList,
                    com.github.retrooper.packetevents.protocol.player.EquipmentSlot.OFF_HAND,
                    player.getInventory().getItemInOffHand());
            addEquipment(equipmentList,
                    com.github.retrooper.packetevents.protocol.player.EquipmentSlot.HELMET,
                    player.getInventory().getHelmet());
            addEquipment(equipmentList,
                    com.github.retrooper.packetevents.protocol.player.EquipmentSlot.CHEST_PLATE,
                    player.getInventory().getChestplate());
            addEquipment(equipmentList,
                    com.github.retrooper.packetevents.protocol.player.EquipmentSlot.LEGGINGS,
                    player.getInventory().getLeggings());
            addEquipment(equipmentList,
                    com.github.retrooper.packetevents.protocol.player.EquipmentSlot.BOOTS,
                    player.getInventory().getBoots());

            if (!equipmentList.isEmpty()) {
                WrapperPlayServerEntityEquipment equipPacket =
                        new WrapperPlayServerEntityEquipment(player.getEntityId(), equipmentList);
                PacketEvents.getAPI().getPlayerManager().sendPacket(observer, equipPacket);
            }
        } catch (Exception ignored) {}
    }

    private static void addEquipment(
            List<com.github.retrooper.packetevents.protocol.player.Equipment> list,
            com.github.retrooper.packetevents.protocol.player.EquipmentSlot slot,
            org.bukkit.inventory.ItemStack bukkitItem) {
        com.github.retrooper.packetevents.protocol.item.ItemStack peItem;
        if (bukkitItem == null || bukkitItem.getType().isAir()) {
            peItem = com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY;
        } else {
            com.github.retrooper.packetevents.protocol.item.type.ItemType itemType =
                    com.github.retrooper.packetevents.protocol.item.type.ItemTypes.getByName(
                            bukkitItem.getType().getKey().getKey());
            if (itemType == null) {
                peItem = com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY;
            } else {
                peItem = com.github.retrooper.packetevents.protocol.item.ItemStack.builder()
                        .type(itemType)
                        .amount(bukkitItem.getAmount())
                        .build();
            }
        }
        list.add(new com.github.retrooper.packetevents.protocol.player.Equipment(slot, peItem));
    }

    public static void applySelfDisguise(Main plugin, Player player, DisguiseManager.DisguiseData data) {
        int selfFakeId = player.getEntityId() + 100000;
        UUID selfFakeUuid = UUID.randomUUID();

        sendEntityDestroy(player, selfFakeId);

        FoliaScheduler.runTaskLater(plugin, () -> {
            boolean isPlayer = data.getType() == DisguiseManager.DisguiseType.PLAYER;

            if (isPlayer) {
                UserProfile fakeProfile = createDisguisedProfile(selfFakeUuid, data.getPlayerName(), data);
                sendPlayerInfoAddFake(player, selfFakeUuid, fakeProfile, data.getPlayerName(),
                        toPacketEventsGameMode(player.getGameMode()));
            }

            FoliaScheduler.runTaskLater(plugin, () -> {
                try {
                    Location loc = player.getLocation();
                    float yaw = getAdjustedYaw(loc.getYaw(), data);
                    EntityType peType;

                    if (data.getType() == DisguiseManager.DisguiseType.ENTITY) {
                        peType = getBukkitToPacketEventsEntityType(data.getEntityType());
                        if (peType == null) return;
                    } else if (data.getType() == DisguiseManager.DisguiseType.ITEM) {
                        peType = EntityTypes.ITEM;
                    } else {
                        peType = EntityTypes.PLAYER;
                    }

                    WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                            selfFakeId, Optional.of(selfFakeUuid), peType,
                            new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                            loc.getPitch(), yaw, yaw,
                            0, Optional.of(new Vector3d(0, 0, 0))
                    );
                    PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnPacket);

                    if (data.getType() == DisguiseManager.DisguiseType.ITEM) {
                        sendItemMetadata(player, selfFakeId, data.getMaterial());
                    }
                } catch (Exception ignored) {}


                if (isPlayer) {
                    FoliaScheduler.runTaskLater(plugin, () ->
                            sendPlayerInfoUpdateListed(player, selfFakeUuid, false), 2L);
                }
            }, 2L);
        }, 2L);
    }

    public static void removeSelfDisguise(Player player) {
        int selfFakeId = player.getEntityId() + 100000;
        sendEntityDestroy(player, selfFakeId);
    }

    public static void sendTeleportPacket(Player player, int entityId) {
        try {
            Location loc = player.getLocation();
            float yaw = getAdjustedYaw(loc.getYaw(), getDisguiseData(player));
            WrapperPlayServerEntityTeleport telePacket = new WrapperPlayServerEntityTeleport(
                    entityId,
                    new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                    yaw, loc.getPitch(), false
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, telePacket);
        } catch (Exception ignored) {}
    }

    public static void sendHeadRotationPacket(Player player, int entityId) {
        try {
            float yaw = getAdjustedYaw(player.getLocation().getYaw(), getDisguiseData(player));
            WrapperPlayServerEntityHeadLook headPacket = new WrapperPlayServerEntityHeadLook(
                    entityId, yaw
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, headPacket);
        } catch (Exception ignored) {}
    }

    public static void syncFakeEntityForObservers(Player player) {
        Integer fakeId = observerFakeIds.get(player.getUniqueId());
        if (fakeId == null) return;
        Location loc = player.getLocation();
        float yaw = getAdjustedYaw(loc.getYaw(), getDisguiseData(player));
        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.equals(player)) continue;
            try {
                WrapperPlayServerEntityTeleport telePacket = new WrapperPlayServerEntityTeleport(
                        fakeId,
                        new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                        yaw, loc.getPitch(), false
                );
                PacketEvents.getAPI().getPlayerManager().sendPacket(observer, telePacket);
                WrapperPlayServerEntityHeadLook headPacket = new WrapperPlayServerEntityHeadLook(
                        fakeId, yaw
                );
                PacketEvents.getAPI().getPlayerManager().sendPacket(observer, headPacket);
            } catch (Exception ignored) {}
        }
    }


    private static void sendSetInvisibleMetadata(Player observer, int entityId, boolean invisible) {
        try {
            List<EntityData<?>> metadata = new ArrayList<>();
            byte flags = invisible ? (byte) (1 << 5) : (byte) 0;
            metadata.add(new EntityData(0, EntityDataTypes.BYTE, flags));
            WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(entityId, metadata);
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, metaPacket);
        } catch (Exception ignored) {}
    }

    private static void sendItemMetadata(Player observer, int entityId, org.bukkit.Material material) {
        try {
            List<EntityData<?>> metadata = new ArrayList<>();
            metadata.add(new EntityData(0, EntityDataTypes.BYTE, (byte) 0));

            com.github.retrooper.packetevents.protocol.item.type.ItemType itemType =
                    com.github.retrooper.packetevents.protocol.item.type.ItemTypes.getByName(
                            material.getKey().getKey());
            if (itemType != null) {
                com.github.retrooper.packetevents.protocol.item.ItemStack peItem =
                        com.github.retrooper.packetevents.protocol.item.ItemStack.builder()
                                .type(itemType)
                                .amount(1)
                                .build();
                metadata.add(new EntityData(8, EntityDataTypes.ITEMSTACK, peItem));
            }

            WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(entityId, metadata);
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, metaPacket);
        } catch (Exception ignored) {}
    }

    private static void sendEntityDestroy(Player observer, int entityId) {
        try {
            WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, destroyPacket);
        } catch (Exception ignored) {}
    }

    private static void sendPlayerInfoUpdateListed(Player observer, UUID uuid, boolean listed) {
        try {
            UserProfile profile = new UserProfile(uuid, "");
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                    profile, listed, 0, GameMode.SURVIVAL, null, null
            );
            WrapperPlayServerPlayerInfoUpdate infoPacket = new WrapperPlayServerPlayerInfoUpdate(
                    EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                    Collections.singletonList(info)
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, infoPacket);
        } catch (Exception ignored) {}
    }

    private static void sendPlayerInfoAdd(Player observer, Player player, UserProfile profile, String displayName) {
        try {
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                    profile, true, 0, toPacketEventsGameMode(player.getGameMode()),
                    net.kyori.adventure.text.Component.text(displayName), null
            );
            WrapperPlayServerPlayerInfoUpdate infoPacket = new WrapperPlayServerPlayerInfoUpdate(
                    EnumSet.of(
                            WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED
                    ),
                    Collections.singletonList(info)
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, infoPacket);
        } catch (Exception ignored) {}
    }

    private static void sendPlayerInfoAddFake(Player observer, UUID fakeUuid, UserProfile profile,
                                              String displayName, GameMode gameMode) {
        try {
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                    profile, true, 0, gameMode,
                    net.kyori.adventure.text.Component.text(displayName), null
            );
            WrapperPlayServerPlayerInfoUpdate infoPacket = new WrapperPlayServerPlayerInfoUpdate(
                    EnumSet.of(
                            WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED
                    ),
                    Collections.singletonList(info)
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, infoPacket);
        } catch (Exception ignored) {}
    }

    private static UserProfile createDisguisedProfile(UUID ownerUuid, String name,
                                                       DisguiseManager.DisguiseData data) {
        UserProfile profile = new UserProfile(ownerUuid, name);
        if (data != null && data.getSkinValue() != null && !data.getSkinValue().isEmpty()) {
            profile.getTextureProperties().add(new TextureProperty(
                    "textures",
                    data.getSkinValue(),
                    data.getSkinSignature() != null ? data.getSkinSignature() : ""
            ));
        }
        return profile;
    }

    private static UserProfile getRealProfile(Player player) {
        UserProfile profile = new UserProfile(player.getUniqueId(), player.getName());
        String[] skin = getSkinFromPlayer(player);
        if (skin != null) {
            profile.getTextureProperties().add(new TextureProperty("textures", skin[0], skin[1]));
        }
        return profile;
    }

    public static String[] getSkinFromPlayer(Player player) {
        try {
            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Object gameProfile = craftPlayer.getClass().getMethod("getGameProfile").invoke(craftPlayer);
            @SuppressWarnings("unchecked")
            com.google.common.collect.Multimap<String, Object> properties =
                    (com.google.common.collect.Multimap<String, Object>)
                            gameProfile.getClass().getMethod("getProperties").invoke(gameProfile);
            for (Object prop : properties.get("textures")) {
                String value = (String) prop.getClass().getMethod("getValue").invoke(prop);
                String signature = "";
                try {
                    signature = (String) prop.getClass().getMethod("getSignature").invoke(prop);
                } catch (Exception ignored) {}
                return new String[]{value, signature != null ? signature : ""};
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Player findPlayerByEntityId(int entityId) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getEntityId() == entityId) return p;
        }
        return null;
    }

    private static GameMode toPacketEventsGameMode(org.bukkit.GameMode bukkit) {
        switch (bukkit) {
            case CREATIVE: return GameMode.CREATIVE;
            case ADVENTURE: return GameMode.ADVENTURE;
            case SPECTATOR: return GameMode.SPECTATOR;
            default: return GameMode.SURVIVAL;
        }
    }

    private static EntityType getBukkitToPacketEventsEntityType(org.bukkit.entity.EntityType type) {
        try {
            return EntityTypes.getByName(type.name().toLowerCase());
        } catch (Exception e) {
            return null;
        }
    }

    private static float getAdjustedYaw(float yaw, DisguiseManager.DisguiseData data) {
        if (data != null
                && data.getType() == DisguiseManager.DisguiseType.ENTITY
                && data.getEntityType() == org.bukkit.entity.EntityType.ENDER_DRAGON) {
            return yaw + 180f;
        }
        return yaw;
    }

    private static DisguiseManager.DisguiseData getDisguiseData(Player player) {
        if (instance == null || instance.plugin == null || instance.plugin.getDisguiseManager() == null) {
            return null;
        }
        return instance.plugin.getDisguiseManager().getDisguise(player);
    }

    @SuppressWarnings("unused")
    private static final String _xCr7w3n = "\u0077\u0069\u0064\u006e" + "\u0065\u0065\u0073";

}

