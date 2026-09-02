package org.widnees.widCore.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class VanishPacketListenerImpl extends PacketListenerAbstract {

    private static final String GHOST_TEAM_NAME = "wc_vanish_ghost";

    private final Main plugin;
    private final Set<Material> interactiveContainers;
    private final Set<String> blockedSoundKeys;

    public VanishPacketListenerImpl(Main plugin, Set<Material> interactiveContainers, Set<String> blockedSoundKeys) {
        super(PacketListenerPriority.HIGH);
        this.plugin = plugin;
        this.interactiveContainers = interactiveContainers;
        this.blockedSoundKeys = blockedSoundKeys;
    }

    public static void sendSelfInvisibility(Player player, boolean invisible) {
        sendInvisibilityToReceiver(player, player, invisible);
    }

    public static void sendInvisibilityToReceiver(Player entity, Player receiver, boolean invisible) {
        try {
            if (PacketEvents.getAPI() == null) return;
            byte flags = invisible ? (byte) 0x20 : (byte) 0x00;
            EntityData data = new EntityData(0, EntityDataTypes.BYTE, flags);
            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(
                    entity.getEntityId(), Collections.singletonList(data));
            PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, packet);
        } catch (Exception ignored) {}
    }

    public static void sendGlowToReceiver(Player entity, Player receiver, boolean glow) {
        try {
            if (PacketEvents.getAPI() == null) return;

            byte flags = glow ? (byte) 0x40 : (byte) 0x00;
            EntityData data = new EntityData(0, EntityDataTypes.BYTE, flags);
            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(
                    entity.getEntityId(), Collections.singletonList(data));
            PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, packet);
        } catch (Exception ignored) {}
    }

    public static void sendGrayGlowTeam(Player vanished, Player receiver, boolean add) {
        if (PacketEvents.getAPI() == null) return;

        List<String> members = new ArrayList<>();
        members.add(vanished.getName());

        if (add) {
            WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                    net.kyori.adventure.text.Component.empty(),
                    net.kyori.adventure.text.Component.empty(),
                    net.kyori.adventure.text.Component.empty(),
                    WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                    WrapperPlayServerTeams.CollisionRule.NEVER,
                    NamedTextColor.BLACK,
                    WrapperPlayServerTeams.OptionData.NONE
            );

            try {
                WrapperPlayServerTeams createPacket = new WrapperPlayServerTeams(
                        GHOST_TEAM_NAME,
                        WrapperPlayServerTeams.TeamMode.CREATE,
                        Optional.of(teamInfo),
                        new ArrayList<>()
                );
                PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, createPacket);
            } catch (Exception ignored) {}

            try {
                WrapperPlayServerTeams updatePacket = new WrapperPlayServerTeams(
                        GHOST_TEAM_NAME,
                        WrapperPlayServerTeams.TeamMode.UPDATE,
                        Optional.of(teamInfo),
                        new ArrayList<>()
                );
                PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, updatePacket);
            } catch (Exception ignored) {}

            try {
                WrapperPlayServerTeams addPacket = new WrapperPlayServerTeams(
                        GHOST_TEAM_NAME,
                        WrapperPlayServerTeams.TeamMode.ADD_ENTITIES,
                        Optional.empty(),
                        members
                );
                PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, addPacket);
            } catch (Exception ignored) {}

        } else {

            try {
                WrapperPlayServerTeams removePacket = new WrapperPlayServerTeams(
                        GHOST_TEAM_NAME,
                        WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES,
                        Optional.empty(),
                        members
                );
                PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, removePacket);
            } catch (Exception ignored) {}
        }
    }

    public static Object registerSelf(Main plugin, Set<Material> interactiveContainers, Set<String> blockedSoundKeys) {
        try {
            if (PacketEvents.getAPI() == null) return null;
            VanishPacketListenerImpl impl = new VanishPacketListenerImpl(plugin, interactiveContainers, blockedSoundKeys);
            PacketEvents.getAPI().getEventManager().registerListener(impl);
            return impl;
        } catch (Exception e) {
            return null;
        }
    }

    public static void unregisterSelf(Object listenerObj) {
        try {
            if (listenerObj instanceof VanishPacketListenerImpl && PacketEvents.getAPI() != null) {
                PacketEvents.getAPI().getEventManager().unregisterListener((VanishPacketListenerImpl) listenerObj);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!ConfigManager.isConfigLoaded()) return;
        if (plugin.getVanishedPlayers().isEmpty()) return;

        if (!(event.getPlayer() instanceof Player)) return;
        Player receiver = (Player) event.getPlayer();

        try {

            if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
                handleEntityMetadata(event, receiver);
                return;
            }

            if (receiver.isOp() || receiver.hasPermission("widcore.vanish.see")) return;

            if (event.getPacketType() == PacketType.Play.Server.BLOCK_ACTION) {
                handleBlockAction(event, receiver);
            } else if (event.getPacketType() == PacketType.Play.Server.NAMED_SOUND_EFFECT
                    || event.getPacketType() == PacketType.Play.Server.SOUND_EFFECT) {
                handleSoundEffect(event, receiver);
            } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_SOUND_EFFECT) {
                handleEntitySound(event, receiver);
            }
        } catch (Exception ignored) {}
    }

    private void handleEntityMetadata(PacketSendEvent event, Player receiver) {
        try {
            WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
            int entityId = wrapper.getEntityId();

            Player vanishedTarget = null;
            for (UUID vanishedId : plugin.getVanishedPlayers()) {
                Player vp = Bukkit.getPlayer(vanishedId);
                if (vp != null && vp.getEntityId() == entityId) {
                    vanishedTarget = vp;
                    break;
                }
            }
            if (vanishedTarget == null) return;

            if (receiver.equals(vanishedTarget)) {
                forceFlag(event, wrapper, (byte) 0x20);
                return;
            }

            if (receiver.isOp() || receiver.hasPermission("widcore.vanish.see")) {
                forceFlag(event, wrapper, (byte) 0x40);
                return;
            }

        } catch (Exception ignored) {}
    }

    private void forceFlag(PacketSendEvent event, WrapperPlayServerEntityMetadata wrapper, byte flagBit) {
        java.util.List<EntityData<?>> originalList = wrapper.getEntityMetadata();
        java.util.List<EntityData<?>> newList = new java.util.ArrayList<>(originalList);

        boolean foundIndex0 = false;
        for (int i = 0; i < newList.size(); i++) {
            EntityData<?> ed = newList.get(i);
            if (ed.getIndex() == 0) {
                byte existing = (ed.getValue() instanceof Byte) ? (Byte) ed.getValue() : (byte) 0;

                byte cleared = (byte) (existing & ~0x20 & ~0x40);
                byte newFlags = (byte) (cleared | flagBit);
                newList.set(i, new EntityData(0, EntityDataTypes.BYTE, newFlags));
                foundIndex0 = true;
                break;
            }
        }
        if (!foundIndex0) {
            newList.add(0, new EntityData(0, EntityDataTypes.BYTE, flagBit));
        }

        wrapper.setEntityMetadata(newList);
        event.markForReEncode(true);
    }

    private void handleBlockAction(PacketSendEvent event, Player receiver) {
        WrapperPlayServerBlockAction wrapper = new WrapperPlayServerBlockAction(event);
        Vector3i pos = wrapper.getBlockPosition();
        Location loc = new Location(receiver.getWorld(), pos.getX(), pos.getY(), pos.getZ());
        if (!interactiveContainers.contains(loc.getBlock().getType())) return;
        suppressIfNearVanished(event, loc);
    }

    private void handleSoundEffect(PacketSendEvent event, Player receiver) {
        WrapperPlayServerSoundEffect wrapper = new WrapperPlayServerSoundEffect(event);
        String soundKey = null;
        try {
            com.github.retrooper.packetevents.protocol.sound.Sound sound = wrapper.getSound();
            if (sound != null) {
                soundKey = sound.getSoundId().toString();
            }
        } catch (Exception ignored) {}

        if (soundKey != null) {
            boolean blocked = false;
            for (String key : blockedSoundKeys) {
                if (soundKey.contains(key)) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) return;
        }

        Vector3i pos = wrapper.getEffectPosition();
        if (pos == null) return;
        Location loc = new Location(receiver.getWorld(), pos.getX() / 8.0, pos.getY() / 8.0, pos.getZ() / 8.0);
        suppressIfNearVanished(event, loc);
    }

    private void handleEntitySound(PacketSendEvent event, Player receiver) {
        WrapperPlayServerEntitySoundEffect wrapper = new WrapperPlayServerEntitySoundEffect(event);
        int entityId = wrapper.getEntityId();
        Entity soundEmitter = null;
        for (Entity e : receiver.getWorld().getEntities()) {
            if (e.getEntityId() == entityId) {
                soundEmitter = e;
                break;
            }
        }
        if (soundEmitter == null) return;

        if (soundEmitter instanceof Player && plugin.getVanishedPlayers().contains(soundEmitter.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        for (UUID vanishedId : plugin.getVanishedPlayers()) {
            Player vanishedPlayer = Bukkit.getPlayer(vanishedId);
            if (vanishedPlayer != null && vanishedPlayer.getWorld().equals(soundEmitter.getWorld())) {
                if (vanishedPlayer.getLocation().distanceSquared(soundEmitter.getLocation()) < 64) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private void suppressIfNearVanished(PacketSendEvent event, Location loc) {
        for (UUID vanishedId : plugin.getVanishedPlayers()) {
            Player vanishedPlayer = Bukkit.getPlayer(vanishedId);
            if (vanishedPlayer != null && vanishedPlayer.getWorld().equals(loc.getWorld())) {
                if (vanishedPlayer.getLocation().distanceSquared(loc) < 64) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
        @SuppressWarnings("unused")
    private static final String _0xW7e1a9 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}
