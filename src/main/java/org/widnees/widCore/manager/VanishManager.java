package org.widnees.widCore.manager;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.widnees.widCore.Main;
import org.widnees.widCore.listener.JoinLeaveListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class VanishManager {
    private final Main plugin;
    private final JoinLeaveListener joinLeaveListener;

    public VanishManager(Main plugin, JoinLeaveListener joinLeaveListener) {
        this.plugin = plugin;
        this.joinLeaveListener = joinLeaveListener;
    }

    public boolean canSee(CommandSender viewer, Player target) {
        if (viewer == null || target == null) {
            return false;
        }
        if (!(viewer instanceof Player)) {
            return true;
        }
        Player viewerPlayer = (Player) viewer;
        if (viewerPlayer.equals(target)) {
            return true;
        }
        if (!isVanished(target)) {
            return true;
        }
        return viewerPlayer.isOp() || viewerPlayer.hasPermission("widcore.vanish.see");
    }

    public boolean isHiddenFrom(Player target, CommandSender viewer) {
        return target != null && isVanished(target) && !canSee(viewer, target);
    }

    public Player getVisiblePlayer(String name, CommandSender viewer) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            target = Bukkit.getPlayer(name);
        }
        if (target == null || !target.isOnline()) {
            return null;
        }
        if (isHiddenFrom(target, viewer)) {
            return null;
        }
        return target;
    }

    public Collection<? extends Player> getVisiblePlayers(CommandSender viewer) {
        List<Player> visible = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (canSee(viewer, online)) {
                visible.add(online);
            }
        }
        return visible;
    }

    public int getOnlineCountExcludingVanished() {
        int online = Bukkit.getOnlinePlayers().size();
        int vanishedOnline = 0;
        for (UUID uuid : plugin.getVanishedPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                vanishedOnline++;
            }
        }
        return Math.max(0, online - vanishedOnline);
    }

    public int getOnlineCountExcludingVanished(String worldName) {
        if (worldName == null) {
            return getOnlineCountExcludingVanished();
        }
        int count = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getWorld() != null
                    && online.getWorld().getName().equalsIgnoreCase(worldName)
                    && !isVanished(online)) {
                count++;
            }
        }
        return count;
    }

    public List<String> getVisiblePlayerNames(CommandSender viewer) {
        return getVisiblePlayers(viewer).stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }


    public void setVanished(Player player, boolean vanished) {
        boolean fakeMessagesEnabled = this.plugin.getConfigManager().getModuleConfig("joinleave").getBoolean("fake-messages-on-vanish", true);
        if (vanished) {
            this.plugin.getVanishedPlayers().add(player.getUniqueId());
            applyVanishMetadata(player, true);

            sendSelfInvisibilityPacket(player, true);
            Main.sendMessage(this.plugin, (CommandSender)player, this.plugin.getLanguageManager().getMessage("vanish.enabled"));
            if (this.joinLeaveListener != null && fakeMessagesEnabled) {
                this.joinLeaveListener.sendFakeQuitMessage(player);
            }
            for (Entity entity : player.getNearbyEntities(50.0, 50.0, 50.0)) {
                Mob mob;
                if (!(entity instanceof Mob) || (mob = (Mob)entity).getTarget() == null || !mob.getTarget().equals(player)) continue;
                mob.setTarget(null);
            }
        } else {
            this.plugin.getVanishedPlayers().remove(player.getUniqueId());
            applyVanishMetadata(player, false);

            sendSelfInvisibilityPacket(player, false);

            for (Player observer : Bukkit.getOnlinePlayers()) {
                if (!observer.equals(player) && (observer.isOp() || observer.hasPermission("widcore.vanish.see"))) {
                    sendGlowPacket(player, observer, false);
                    sendGrayGlowTeam(player, observer, false);
                }
            }
            Main.sendMessage(this.plugin, (CommandSender)player, this.plugin.getLanguageManager().getMessage("vanish.disabled"));
            if (this.joinLeaveListener != null && fakeMessagesEnabled) {
                this.joinLeaveListener.sendFakeJoinMessage(player);
            }
        }
        this.updateVanishedForEveryone();
    }

    public void applyVanishMetadata(Player player, boolean vanished) {
        if (player == null) {
            return;
        }
        if (vanished) {
            player.setMetadata("vanished", new FixedMetadataValue(plugin, true));
            player.setMetadata("PV_Vanished", new FixedMetadataValue(plugin, true));
        } else {
            player.removeMetadata("vanished", plugin);
            player.removeMetadata("PV_Vanished", plugin);
        }
        applyPlayerListVisibility(player, !vanished);
    }

    private void applyPlayerListVisibility(Player player, boolean listed) {
        try {
            java.lang.reflect.Method setListed = player.getClass().getMethod("setListed", boolean.class);
            setListed.invoke(player, listed);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }


    public boolean isVanished(Player player) {
        return player != null && this.plugin.getVanishedPlayers().contains(player.getUniqueId());
    }

    public int getVanishedCount() {
        int count = 0;
        for (UUID uuid : plugin.getVanishedPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                count++;
            }
        }
        return count;
    }


    public void updateVanishedForEveryone() {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            for (Player vanishedPlayer : Bukkit.getOnlinePlayers()) {
                if (this.isVanished(vanishedPlayer)) {
                    if (onlinePlayer.equals(vanishedPlayer)) {

                        onlinePlayer.showPlayer((Plugin) this.plugin, vanishedPlayer);
                    } else if (onlinePlayer.isOp() || onlinePlayer.hasPermission("widcore.vanish.see")) {

                        onlinePlayer.showPlayer((Plugin) this.plugin, vanishedPlayer);
                        final Player obs = onlinePlayer;
                        final Player vp = vanishedPlayer;
                        Bukkit.getScheduler().runTaskLater((org.bukkit.plugin.Plugin) plugin, () -> {
                            if (obs.isOnline() && vp.isOnline()) {
                                sendGlowPacket(vp, obs, true);
                                sendGrayGlowTeam(vp, obs, true);
                            }
                        }, 3L);
                    } else {

                        onlinePlayer.hidePlayer((Plugin) this.plugin, vanishedPlayer);
                    }
                } else {
                    onlinePlayer.showPlayer((Plugin) this.plugin, vanishedPlayer);
                }
            }
        }
    }

    public void handlePlayerJoin(Player player) {

        for (UUID vanishedId : new java.util.HashSet<>(plugin.getVanishedPlayers())) {
            Player vanished = Bukkit.getPlayer(vanishedId);
            if (vanished == null) continue;
            if (player.isOp() || player.hasPermission("widcore.vanish.see")) {
                player.showPlayer((Plugin) plugin, vanished);
                final Player obs = player;
                final Player vp = vanished;
                Bukkit.getScheduler().runTaskLater((org.bukkit.plugin.Plugin) plugin, () -> {
                    if (obs.isOnline() && vp.isOnline()) {
                        sendGlowPacket(vp, obs, true);
                        sendGrayGlowTeam(vp, obs, true);
                    }
                }, 2L);
            } else {
                player.hidePlayer((Plugin) plugin, vanished);
            }
        }
    }

    private void sendSelfInvisibilityPacket(Player player, boolean invisible) {
        try {
            Class<?> implClass = Class.forName("org.widnees.widCore.listener.VanishPacketListenerImpl");
            java.lang.reflect.Method selfMethod = implClass.getMethod("sendSelfInvisibility", Player.class, boolean.class);
            selfMethod.invoke(null, player, invisible);
        } catch (Exception ignored) {}
    }

    private void sendGlowPacket(Player vanishedPlayer, Player receiver, boolean glow) {
        try {
            Class<?> implClass = Class.forName("org.widnees.widCore.listener.VanishPacketListenerImpl");
            java.lang.reflect.Method glowMethod = implClass.getMethod("sendGlowToReceiver", Player.class, Player.class, boolean.class);
            glowMethod.invoke(null, vanishedPlayer, receiver, glow);
        } catch (Exception ignored) {}
    }

    private void sendGrayGlowTeam(Player vanishedPlayer, Player receiver, boolean add) {
        try {
            Class<?> implClass = Class.forName("org.widnees.widCore.listener.VanishPacketListenerImpl");
            java.lang.reflect.Method teamMethod = implClass.getMethod("sendGrayGlowTeam", Player.class, Player.class, boolean.class);
            teamMethod.invoke(null, vanishedPlayer, receiver, add);
        } catch (Exception ignored) {}
    }

    public void unvanishAll() {
        for (UUID vanishedId : new java.util.HashSet<>(plugin.getVanishedPlayers())) {
            Player vanishedPlayer = Bukkit.getPlayer(vanishedId);
            if (vanishedPlayer != null) {
                applyVanishMetadata(vanishedPlayer, false);
                sendSelfInvisibilityPacket(vanishedPlayer, false);

                for (Player observer : Bukkit.getOnlinePlayers()) {
                    if (!observer.equals(vanishedPlayer) && (observer.isOp() || observer.hasPermission("widcore.vanish.see"))) {
                        sendGlowPacket(vanishedPlayer, observer, false);
                        sendGrayGlowTeam(vanishedPlayer, observer, false);
                    }
                }
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.showPlayer((Plugin) plugin, vanishedPlayer);
                }
            }
        }
        plugin.getVanishedPlayers().clear();
    }

    public static String getStatePrefix() {
        return new String(new char[]{'K', 'p', '3', 'm'});
    }
        @SuppressWarnings("unused")
    private static final String __Wf7c3e9 = "\u0077\u0069\u0064\u006e" + "\u0065\u0065\u0073";

}