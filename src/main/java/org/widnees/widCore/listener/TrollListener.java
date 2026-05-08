package org.widnees.widCore.listener;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.widnees.widCore.Main;
import org.widnees.widCore.command.TrollCommand;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.TrollManager;
import org.widnees.widCore.util.FoliaScheduler;

import java.util.UUID;

public class TrollListener implements Listener {

    private final Main plugin;
    private final TrollManager trollManager;
    private final TrollCommand trollCommand;

    public TrollListener(Main plugin, TrollManager trollManager, TrollCommand trollCommand) {
        this.plugin = plugin;
        this.trollManager = trollManager;
        this.trollCommand = trollCommand;
    }

    private boolean isTrollBucket(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(TrollCommand.TROLL_BUCKET_KEY,
                PersistentDataType.BYTE);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.equals(TrollCommand.TROLL_MENU_TITLE)) {
            trollCommand.clearTrollMenuTarget(event.getPlayer().getUniqueId());
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

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.equals(TrollCommand.TROLL_MENU_TITLE)) {
            return;
        }

        event.setCancelled(true);
        Player sender = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        UUID targetId = trollCommand.getTrollMenuTarget(sender.getUniqueId());
        if (targetId == null) {
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.player-offline"));
            return;
        }
        boolean trollExecuted = false;
        if (event.getSlot() == 0 && clickedItem.getType() == Material.WATER_BUCKET) {
            trollCommand.executeMlgTroll(sender, target);
            trollExecuted = true;
        } else if (event.getSlot() == 1 && clickedItem.getType() == Material.PLAYER_HEAD) {
            trollCommand.executeRotateTroll(sender, target);
            trollExecuted = true;
        } else if (event.getSlot() == 2 && clickedItem.getType() == Material.COW_SPAWN_EGG) {
            trollCommand.executeMobLookTroll(sender, target);
            trollExecuted = true;
        } else if (event.getSlot() == 3 && clickedItem.getType() == Material.CHEST) {
            trollCommand.executeCreeperChestTroll(sender, target);
            trollExecuted = true;
        }
        if (trollExecuted) {
            sender.closeInventory();
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!trollManager.isCreeperChestPlayer(player.getUniqueId())) {
            return;
        }
        Location chestLocation = trollManager.getCreeperChestLocation(player.getUniqueId());
        if (chestLocation == null || !event.getClickedBlock().getLocation().equals(chestLocation)) {
            return;
        }

        event.setCancelled(true);

        trollManager.addFrozenByCreeper(player.getUniqueId());
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 120, 1));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("troll.trap-active"));

        Creeper creeper = (Creeper) player.getWorld()
                .spawnEntity(event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5), EntityType.CREEPER);
        creeper.setPowered(true);
        creeper.ignite();

        trollManager.registerTrollCreeper(player.getUniqueId(), creeper.getUniqueId());

        FoliaScheduler.runAtLocationLater(plugin, event.getClickedBlock().getLocation(), () -> {
            UUID cid = trollManager.getCreeperForPlayer(player.getUniqueId());
            Entity e = (cid != null) ? Bukkit.getEntity(cid) : null;
            if (e == null || e.isDead()) {
                cleanupAfterExplosion(player);
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("troll.trap-failed"));
                if (cid != null) {
                    trollManager.removeTrollByCreeper(cid);
                }
            }
        }, 1L);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        Entity damager = event.getDamager();
        if (trollManager.isTrollCreeper(damager.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        UUID creeperId = event.getEntity().getUniqueId();
        if (trollManager.isTrollCreeper(creeperId)) {
            event.blockList().clear();

            UUID playerId = trollManager.getPlayerForCreeper(creeperId);
            if (playerId != null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    cleanupAfterExplosion(player);
                }
                trollManager.removeTrollByCreeper(creeperId);
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        UUID entityId = event.getEntity().getUniqueId();
        if (trollManager.isTrollCreeper(entityId)) {
            UUID playerId = trollManager.getPlayerForCreeper(entityId);
            if (playerId != null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    cleanupAfterExplosion(player);
                }
            }
            trollManager.removeTrollByCreeper(entityId);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        Player player = event.getEntity();
        if (trollManager.isMlgPlayer(player.getUniqueId())) {
            event.getDrops().removeIf(this::isTrollBucket);
            trollManager.removeMlgPlayer(player.getUniqueId());
        }
        if (trollManager.isCreeperChestPlayer(player.getUniqueId())) {
            cleanupCreeperChest(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        trollCommand.clearTrollMenuTarget(event.getPlayer().getUniqueId());

        if (trollManager.isCreeperChestPlayer(event.getPlayer().getUniqueId())) {
            cleanupCreeperChest(event.getPlayer());
        }
    }

    private void cleanupCreeperChest(Player player) {
        UUID creeperId = trollManager.getCreeperForPlayer(player.getUniqueId());
        if (creeperId != null) {
            Entity creeper = Bukkit.getEntity(creeperId);
            if (creeper != null && !creeper.isDead()) {
                creeper.remove();
            }
        }
        cleanupAfterExplosion(player);
        trollManager.removeTrollByPlayer(player.getUniqueId());
    }

    private void cleanupAfterExplosion(Player player) {
        Location chestLoc = trollManager.getCreeperChestLocation(player.getUniqueId());
        if (chestLoc != null && chestLoc.getBlock().getType() == Material.CHEST) {
            chestLoc.getBlock().setType(Material.AIR);
        }
        trollManager.removeFrozenByCreeper(player.getUniqueId());
        trollManager.removeCreeperChestPlayer(player.getUniqueId());
        if (player.isOnline()) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("troll.trap-safe"));
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack bucketInHand = player.getInventory().getItem(event.getHand());

        if (trollManager.isMlgPlayer(player.getUniqueId()) && isTrollBucket(bucketInHand)) {
            event.setCancelled(true);

            player.getInventory().setItem(event.getHand(), new ItemStack(Material.AIR));

            Block blockToPlaceWater = event.getBlockClicked().getRelative(event.getBlockFace());
            blockToPlaceWater.setType(Material.WATER);

            FoliaScheduler.runAtLocationLater(plugin, blockToPlaceWater.getLocation(), () -> {
                if (blockToPlaceWater.getType() == Material.WATER) {
                    blockToPlaceWater.setType(Material.AIR);
                }
            }, 1L);

            trollManager.removeMlgPlayer(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        if (trollManager.isFrozenByCreeper(event.getPlayer().getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }
        Player player = event.getPlayer();

        if (trollManager.isCreeperChestPlayer(player.getUniqueId())) {
            Location chestLocation = trollManager.getCreeperChestLocation(player.getUniqueId());
            Location blockLocation = event.getBlock().getLocation();

            if (chestLocation != null && chestLocation.equals(blockLocation)) {
                event.setCancelled(true);
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("troll.chest-break"));
            }
        }
    }
}