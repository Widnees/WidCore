package org.widnees.widCore.listener;

import org.bukkit.Material;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.VanishManager;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VanishListener implements Listener {

    private final Main plugin;
    private final VanishManager vanishManager;

    private static Object packetListener;

    private static final Set<Material> INTERACTIVE_CONTAINERS = new HashSet<>(Arrays.asList(
            Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST, Material.BARREL,
            Material.SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX
    ));

    private static final Set<String> BLOCKED_CONTAINER_SOUNDS = new HashSet<>(Arrays.asList(
            "chest.open", "chest.close", "chest_locked",
            "ender_chest.open", "ender_chest.close",
            "barrel.open", "barrel.close",
            "shulker_box.open", "shulker_box.close"
    ));

    public VanishListener(Main plugin) {
        this.plugin = plugin;
        this.vanishManager = plugin.getVanishManager();
        registerPacketListeners();
    }

    private void registerPacketListeners() {
        try {
            Class<?> implClass = Class.forName("org.widnees.widCore.listener.VanishPacketListenerImpl");

            if (packetListener != null) {
                try {
                    Method unregMethod = implClass.getMethod("unregisterSelf", Object.class);
                    unregMethod.invoke(null, packetListener);
                } catch (Exception ignored) {}
                packetListener = null;
            }
            Method regMethod = implClass.getMethod("registerSelf", Main.class, Set.class, Set.class);
            packetListener = regMethod.invoke(null, plugin, INTERACTIVE_CONTAINERS, BLOCKED_CONTAINER_SOUNDS);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {

        } catch (Exception ignored) {}
    }

    private void sendSelfInvisibilityPacket(Player player, boolean invisible) {
        try {
            Class<?> implClass = Class.forName("org.widnees.widCore.listener.VanishPacketListenerImpl");
            Method m = implClass.getMethod("sendSelfInvisibility", Player.class, boolean.class);
            m.invoke(null, player, invisible);
        } catch (Exception | NoClassDefFoundError ignored) {}
    }

    private void sendGlowToReceiverPacket(Player entity, Player receiver, boolean glow) {
        try {
            Class<?> implClass = Class.forName("org.widnees.widCore.listener.VanishPacketListenerImpl");
            Method m = implClass.getMethod("sendGlowToReceiver", Player.class, Player.class, boolean.class);
            m.invoke(null, entity, receiver, glow);
        } catch (Exception | NoClassDefFoundError ignored) {}
    }

    private void sendGrayGlowTeamPacket(Player entity, Player receiver, boolean add) {
        try {
            Class<?> implClass = Class.forName("org.widnees.widCore.listener.VanishPacketListenerImpl");
            Method m = implClass.getMethod("sendGrayGlowTeam", Player.class, Player.class, boolean.class);
            m.invoke(null, entity, receiver, add);
        } catch (Exception | NoClassDefFoundError ignored) {}
    }

    public static void unregisterPacketListeners() {
        if (packetListener != null) {
            try {
                Class<?> implClass = Class.forName("org.widnees.widCore.listener.VanishPacketListenerImpl");
                Method unregMethod = implClass.getMethod("unregisterSelf", Object.class);
                unregMethod.invoke(null, packetListener);
            } catch (Exception | NoClassDefFoundError ignored) {}
            packetListener = null;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!ConfigManager.isConfigLoaded()) return;
        Player player = event.getPlayer();

        if (vanishManager.isVanished(player)) {
            vanishManager.applyVanishMetadata(player, true);
            sendSelfInvisibilityPacket(player, true);

            for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (online.equals(player)) continue;
                if (online.isOp() || online.hasPermission("widcore.vanish.see")) {

                    online.showPlayer(plugin, player);
                    final Player obs = online;
                    final Player vanished = player;
                    org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (obs.isOnline() && vanished.isOnline()) {
                            sendGlowToReceiverPacket(vanished, obs, true);
                            sendGrayGlowTeamPacket(vanished, obs, true);
                        }
                    }, 2L);
                } else {
                    online.hidePlayer(plugin, player);
                }
            }
            plugin.getLogger().info(player.getName() + " sunucuya görünmez olarak katıldı.");
        } else {
            vanishManager.handlePlayerJoin(player);
        }
        vanishManager.updateVanishedForEveryone();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!ConfigManager.isConfigLoaded()) return;
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        boolean wasVanished = plugin.getVanishedPlayers().contains(playerUuid);
        if (wasVanished) {
            plugin.getVanishedPlayers().remove(playerUuid);
        }
    }

    @EventHandler
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (!ConfigManager.isConfigLoaded()) return;
        if (!(event.getTarget() instanceof Player) || !(event.getEntity() instanceof Monster)) return;

        Player targetPlayer = (Player) event.getTarget();
        if (vanishManager.isVanished(targetPlayer)) {
            event.setCancelled(true);
        }
    }
        @SuppressWarnings("unused")
    private static final String _0xCw4d8n = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}