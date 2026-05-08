package org.widnees.widCore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.PunishmentManager;

public class FreezeListener implements Listener {

    private final Main plugin;
    private final PunishmentManager punishmentManager;

    public FreezeListener(Main plugin, PunishmentManager punishmentManager) {
        this.plugin = plugin;
        this.punishmentManager = punishmentManager;
    }

    private void sendMessageToFrozenPlayer(Player player) {
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("freeze.frozen"));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        Player player = event.getPlayer();
        if (punishmentManager.isFrozen(player.getUniqueId())) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("freeze.frozen-login"));
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (punishmentManager.isFrozen(event.getPlayer().getUniqueId())) {
            
            if (event.getFrom().getX() != event.getTo().getX() ||
                    event.getFrom().getY() != event.getTo().getY() ||
                    event.getFrom().getZ() != event.getTo().getZ() ||
                    event.getFrom().getYaw() != event.getTo().getYaw() ||
                    event.getFrom().getPitch() != event.getTo().getPitch()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (punishmentManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            sendMessageToFrozenPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (punishmentManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            sendMessageToFrozenPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (punishmentManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            sendMessageToFrozenPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (punishmentManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            sendMessageToFrozenPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerAttemptPickupItemEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (punishmentManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (event.getSlot() < 0) {
            return;
        }
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (punishmentManager.isFrozen(player.getUniqueId())) {
                event.setCancelled(true);
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("freeze.frozen-inv"));
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (event.getDamager() instanceof Player) {
            Player damager = (Player) event.getDamager();
            if (punishmentManager.isFrozen(damager.getUniqueId())) {
                event.setCancelled(true);
                Main.sendMessage(this.plugin, damager, plugin.getLanguageManager().getMessage("freeze.frozen-attack"));
            }
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        Player player = event.getPlayer();
        if (punishmentManager.isFrozen(player.getUniqueId())) {
            
            String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();
            
            java.util.List<String> allowedCommands = plugin.getConfigManager()
                    .getModuleConfig("freeze")
                    .getStringList("allowed-commands");
            
            if (!allowedCommands.contains(command)) {
                event.setCancelled(true);
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("freeze.frozen"));
            }
        }
    }
}
