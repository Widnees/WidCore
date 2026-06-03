package org.widnees.widCore.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.util.FoliaScheduler;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CompassListener implements Listener {

    private final Main plugin;
    private static final int MAX_DISTANCE = 200;
    private final Set<UUID> recentlyDroppedCompass = new HashSet<>();

    public CompassListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (event.getItemDrop().getItemStack().getType() == Material.COMPASS) {
            final UUID playerUuid = event.getPlayer().getUniqueId();
            recentlyDroppedCompass.add(playerUuid);
            FoliaScheduler.runTaskLater(plugin, () -> recentlyDroppedCompass.remove(playerUuid), 1L);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        Player player = event.getPlayer();

        if (recentlyDroppedCompass.contains(player.getUniqueId())) {
            return;
        }

        if ((event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)
                && player.getInventory().getItemInMainHand().getType() == Material.COMPASS) {

            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
            }

            if (!player.hasPermission("widcore.compass")) {
                return;
            }

            Block targetBlock = player.getTargetBlock(null, MAX_DISTANCE);

            if (targetBlock == null || targetBlock.getType().isAir()) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("compass.need-block"));
                return;
            }

            Location safeLocation = findSafeTeleportLocation(targetBlock, player.getLocation());

            if (safeLocation != null) {
                FoliaScheduler.teleportAsync(player, safeLocation,
                        () -> player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F));
            } else {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("compass.unsafe"));
            }
        }
    }

    private Location findSafeTeleportLocation(Block targetBlock, Location playerLocation) {
        BlockFace[] faces = { BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };

        for (BlockFace face : faces) {
            Block adjacentBlock = targetBlock.getRelative(face);
            Block blockAbove = adjacentBlock.getRelative(BlockFace.UP);

            if (adjacentBlock.isPassable() && blockAbove.isPassable()) {
                Location newLoc = adjacentBlock.getLocation().add(0.5, 0, 0.5);
                newLoc.setYaw(playerLocation.getYaw());
                newLoc.setPitch(playerLocation.getPitch());
                return newLoc;
            }
        }
        return null;
    }
        @SuppressWarnings("unused")
    private static final String _xCr7w3n = "\u0077\u0069\u0064\u006e" + "\u0065\u0065\u0073";

}