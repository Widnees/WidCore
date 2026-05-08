package org.widnees.widCore.listener;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.VoidSpawnManager;

public class VoidSpawnListener implements Listener {

    private final Main plugin;
    private final VoidSpawnManager voidSpawnManager;
    private final int voidLevel = -64;

    public VoidSpawnListener(Main plugin, VoidSpawnManager voidSpawnManager) {
        this.plugin = plugin;
        this.voidSpawnManager = voidSpawnManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }

        if (event.getFrom().getBlockY() == event.getTo().getBlockY()) {
            return;
        }

        Player player = event.getPlayer();

        if (event.getTo().getY() <= voidLevel) {
            Location voidSpawnLocation = voidSpawnManager.getVoidSpawn(player.getWorld().getName());

            if (voidSpawnLocation != null) {
                player.teleportAsync(voidSpawnLocation).thenAccept(success -> {
                    if (success) {
                        player.setFallDistance(0);
                        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("voidspawn.saved"));

                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
                        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 150, 0.5, 1,
                                0.5, 0.2);
                        player.getWorld().spawnParticle(Particle.DRAGON_BREATH, player.getLocation().add(0, 1, 0), 50,
                                0.5, 1, 0.5, 0.05);
                    }
                });
            }
        }
    }
}