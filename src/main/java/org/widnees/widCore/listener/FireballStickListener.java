package org.widnees.widCore.listener;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.widnees.widCore.Main;
import org.widnees.widCore.util.FoliaScheduler;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FireballStickListener implements Listener {

    private final Main plugin;
    private final NamespacedKey fireballPowerKey;
    private final NamespacedKey fireballOwnerKey;
    private final NamespacedKey fireballProjKey;
    private final Set<UUID> dropCooldown = new HashSet<>();

    public FireballStickListener(Main plugin) {
        this.plugin = plugin;
        this.fireballPowerKey = new NamespacedKey(plugin, "fireball_power");
        this.fireballOwnerKey = new NamespacedKey(plugin, "fireball_owner");
        this.fireballProjKey = new NamespacedKey(plugin, "fireball_projectile");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        if (!isFireballStick(droppedItem)) {
            return;
        }

        Player player = event.getPlayer();
        final UUID playerUuid = player.getUniqueId();

        dropCooldown.add(playerUuid);
        FoliaScheduler.runAtEntityLater(plugin, player, () -> dropCooldown.remove(playerUuid), 3L);

        event.setCancelled(true);
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("fireballstick.drop-prevent"));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;

        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        if (event.getClickedInventory().getType() != org.bukkit.event.inventory.InventoryType.PLAYER) {
            if (isFireballStick(clickedItem)) {
                event.setCancelled(true);
                Main.sendMessage(this.plugin, event.getWhoClicked(), plugin.getLanguageManager().getMessage("fireballstick.drop-prevent"));
            }
            if (isFireballStick(cursorItem)) {
                event.setCancelled(true);
                Main.sendMessage(this.plugin, event.getWhoClicked(), plugin.getLanguageManager().getMessage("fireballstick.drop-prevent"));
            }
        }

        if (event.getClickedInventory().getType() == org.bukkit.event.inventory.InventoryType.PLAYER) {
            if (event.isShiftClick() && isFireballStick(clickedItem)) {
                if (event.getView().getTopInventory() != null && event.getView().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
                    event.setCancelled(true);
                    Main.sendMessage(this.plugin, event.getWhoClicked(), plugin.getLanguageManager().getMessage("fireballstick.drop-prevent"));
                }
            }
        }

        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            if (event.getClickedInventory().getType() != org.bukkit.event.inventory.InventoryType.PLAYER) {
                ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
                if (isFireballStick(hotbarItem)) {
                    event.setCancelled(true);
                    Main.sendMessage(this.plugin, event.getWhoClicked(), plugin.getLanguageManager().getMessage("fireballstick.drop-prevent"));
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isFireballStick(event.getOldCursor()) || isFireballStick(event.getCursor())) {
            boolean draggingOutsidePlayerInv = false;
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < event.getView().getTopInventory().getSize()) {
                    draggingOutsidePlayerInv = true;
                    break;
                }
            }
            if (draggingOutsidePlayerInv && event.getView().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
                event.setCancelled(true);
                Main.sendMessage(this.plugin, event.getWhoClicked(), plugin.getLanguageManager().getMessage("fireballstick.drop-prevent"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST) 
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (dropCooldown.contains(player.getUniqueId())) {
            return;
        }

        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (isFireballStick(item)) {
            event.setCancelled(true);

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String ownerUuidStr = meta.getPersistentDataContainer().get(fireballOwnerKey, PersistentDataType.STRING);
                if (ownerUuidStr == null || !ownerUuidStr.equals(player.getUniqueId().toString())) {
                    Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("fireballstick.no-perm"));
                    item.setAmount(0); 
                    return;
                }
            }

            if (!player.hasPermission("widcore.fireball")) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("fireballstick.no-perm"));
                return;
            }
            launchFireball(player, item);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isFireballStick);
    }

    @EventHandler
    public void onFireballDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Fireball) {
            Fireball fireball = (Fireball) event.getEntity();
            if (fireball.getPersistentDataContainer().has(fireballProjKey, PersistentDataType.BYTE)) {
                event.setCancelled(true); 
            }
        }
    }

    private boolean isFireballStick(ItemStack item) {
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(fireballPowerKey, PersistentDataType.FLOAT);
    }

    private void launchFireball(Player player, ItemStack stick) {
        ItemMeta meta = stick.getItemMeta();
        if (meta == null)
            return;
        float power = meta.getPersistentDataContainer().getOrDefault(fireballPowerKey, PersistentDataType.FLOAT, 1.0F);
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setYield(power);
        fireball.setIsIncendiary(false);
        fireball.getPersistentDataContainer().set(fireballProjKey, PersistentDataType.BYTE, (byte) 1);
    }
}