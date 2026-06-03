package org.widnees.widCore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.DisguiseManager;

public class DisguiseListener implements Listener {

    private final Main plugin;
    private final DisguiseManager disguiseManager;

    public DisguiseListener(Main plugin) {
        this.plugin = plugin;
        this.disguiseManager = plugin.getDisguiseManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();

        org.widnees.widCore.util.FoliaScheduler.runTaskLater(plugin, () -> {
            if (!joiner.isOnline()) return;
            disguiseManager.refreshDisguiseForObserver(joiner);
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (disguiseManager.isDisguised(player)) {
            disguiseManager.undisguise(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DisguiseManager.DisguiseData data = disguiseManager.getDisguise(player);
        if (data == null) return;

        if (data.getType() == DisguiseManager.DisguiseType.PLAYER) {
            String deathMessage = event.getDeathMessage();
            if (deathMessage != null) {
                event.setDeathMessage(deathMessage.replace(player.getName(), data.getPlayerName()));
            }

            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        disguiseManager.undisguise(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {

    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        DisguiseManager.DisguiseData data = disguiseManager.getDisguise(player);
        if (data == null || data.getType() != DisguiseManager.DisguiseType.PLAYER) return;

        String format = event.getFormat();
        String disguisedName = data.getPlayerName();
        String realName = player.getName();

        String maskedFormat = format.replace(realName, disguisedName);
        String disguisedMsg;
        try {
            disguisedMsg = String.format(maskedFormat, disguisedName, event.getMessage());
        } catch (Exception e) {
            disguisedMsg = "<" + disguisedName + "> " + event.getMessage();
        }

        String realMsg;
        try {
            realMsg = String.format(format, realName, event.getMessage());
        } catch (Exception e) {
            realMsg = "<" + realName + "> " + event.getMessage();
        }

        Player impersonatedPlayer = plugin.getServer().getPlayerExact(disguisedName);

        event.setCancelled(true);

        final String dMsg = disguisedMsg;
        final String rMsg = realMsg;

        for (Player recipient : event.getRecipients()) {
            if (impersonatedPlayer != null && recipient.equals(impersonatedPlayer)) {
                recipient.sendMessage(rMsg); 
            } else {
                recipient.sendMessage(dMsg); 
            }
        }

        plugin.getServer().getConsoleSender().sendMessage(dMsg);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        DisguiseManager.DisguiseData data = disguiseManager.getDisguise(player);
        if (data == null || data.getType() != DisguiseManager.DisguiseType.ENTITY) return;

        org.bukkit.Sound hurtSound = disguiseManager.getMobSound(data.getEntityType(), "HURT");
        if (hurtSound != null) {
            for (Player nearby : player.getWorld().getPlayers()) {
                if (nearby.equals(player)) continue;
                if (nearby.getLocation().distanceSquared(player.getLocation()) <= 2500) {
                    nearby.playSound(player.getLocation(), hurtSound, 1.0f, 1.0f);
                }
            }
        }
    }
        @SuppressWarnings("unused")
    private static final String __xW9a4f1 = "\u0077" + "\u0069\u0064\u006e\u0065\u0065\u0073";

}