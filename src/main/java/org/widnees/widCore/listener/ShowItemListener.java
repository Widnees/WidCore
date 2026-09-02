package org.widnees.widCore.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.ShowItemManager;
import org.widnees.widCore.manager.TextParser;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ShowItemListener implements Listener {

    private final Main plugin;
    private final ShowItemManager showItemManager;
    private static final String INTERNAL_COMMAND_PREFIX = "/widcore-show-item-internal";
    private static final String MESSAGE_PLACEHOLDER = "⟦WIDCORE_SHOWITEM_MSG⟧";

    private static final Map<String, ShowItemCallback> pendingCallbacks = new ConcurrentHashMap<>();
    private static final AtomicInteger callbackCounter = new AtomicInteger(0);

    private static class ShowItemCallback {
        final UUID targetUUID;
        final String type;
        final long createdAt;

        ShowItemCallback(UUID targetUUID, String type) {
            this.targetUUID = targetUUID;
            this.type = type;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private String createCallback(UUID targetUUID, String type) {
        String id = String.valueOf(callbackCounter.incrementAndGet());
        pendingCallbacks.put(id, new ShowItemCallback(targetUUID, type));

        long now = System.currentTimeMillis();
        pendingCallbacks.entrySet().removeIf(e -> now - e.getValue().createdAt > 300000);
        return id;
    }

    public ShowItemListener(Main plugin, ShowItemManager showItemManager) {
        this.plugin = plugin;
        this.showItemManager = showItemManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        if (!ConfigManager.isConfigLoaded())
            return;
        if (!plugin.getConfig().getBoolean("features.show-item", false))
            return;

        String msg = event.getMessage();
        if (!msg.contains("[i]") && !msg.contains("[item]") && !msg.contains("[inv]") && !msg.contains("[ec]")) {
            return;
        }

        Player player = event.getPlayer();
        boolean hasPermI = (msg.contains("[i]") || msg.contains("[item]"))
                && player.hasPermission("widcore.showitem.i");
        boolean hasPermInv = msg.contains("[inv]") && player.hasPermission("widcore.showitem.inv");
        boolean hasPermEc = msg.contains("[ec]") && player.hasPermission("widcore.showitem.ec");

        if (!hasPermI && !hasPermInv && !hasPermEc)
            return;

        String strippedMsg = ChatFormatListener.stripPlayerColorCodes(msg);
        Component messageComp = TextParser.parse(strippedMsg);

        if (hasPermI) {
            ItemStack item = player.getInventory().getItemInMainHand();
            Component itemNameComp = getItemNameComponent(item);

            String itemFormatStr = plugin.getLanguageManager().getMessage("showitem.chat.item-format")
                    .replace("%item%", "⟦ITEM_NAME⟧");

            HoverEvent<?> itemHover = (item != null && item.getType() != Material.AIR)
                    ? item.asHoverEvent()
                    : HoverEvent.showText(TextParser.parse(
                            plugin.getLanguageManager().getMessage("showitem.chat.hover-item")));

            Component itemComp = TextParser.parse(itemFormatStr)
                    .replaceText(TextReplacementConfig.builder()
                            .matchLiteral("⟦ITEM_NAME⟧")
                            .replacement(itemNameComp)
                            .build())
                    .hoverEvent(itemHover)
                    .clickEvent(ClickEvent.runCommand(INTERNAL_COMMAND_PREFIX + " mainhand " + player.getUniqueId()));

            messageComp = messageComp
                    .replaceText(TextReplacementConfig.builder().matchLiteral("[i]").replacement(itemComp).build());
            messageComp = messageComp
                    .replaceText(TextReplacementConfig.builder().matchLiteral("[item]").replacement(itemComp).build());
        }

        if (hasPermInv) {
            Component invComp = TextParser.parse(plugin.getLanguageManager().getMessage("showitem.chat.inv-format").replace("%player%", player.getName()))
                    .hoverEvent(HoverEvent.showText(TextParser.parse(plugin.getLanguageManager().getMessage("showitem.chat.hover-inv"))))
                    .clickEvent(ClickEvent.runCommand(INTERNAL_COMMAND_PREFIX + " inv " + player.getUniqueId()));

            messageComp = messageComp
                    .replaceText(TextReplacementConfig.builder().matchLiteral("[inv]").replacement(invComp).build());
        }

        if (hasPermEc) {
            Component ecComp = TextParser.parse(plugin.getLanguageManager().getMessage("showitem.chat.ec-format").replace("%player%", player.getName()))
                    .hoverEvent(HoverEvent.showText(TextParser.parse(plugin.getLanguageManager().getMessage("showitem.chat.hover-ec"))))
                    .clickEvent(ClickEvent.runCommand(INTERNAL_COMMAND_PREFIX + " ec " + player.getUniqueId()));

            messageComp = messageComp
                    .replaceText(TextReplacementConfig.builder().matchLiteral("[ec]").replacement(ecComp).build());
        }

        String formatString = ChatFormatListener.getFormatForPlayer(plugin, player);
        if (formatString == null) {
            formatString = "&7{prefix} &f{name} &8» &f{message}";
        }

        formatString = ChatFormatListener.applyChatPlaceholders(plugin, player, formatString, MESSAGE_PLACEHOLDER);

        Component finalMessage = TextParser.parse(formatString)
                .replaceText(TextReplacementConfig.builder()
                        .matchLiteral(MESSAGE_PLACEHOLDER)
                        .replacement(messageComp)
                        .build());

        ChatFormatListener.FORMAT_CANCELLED_EVENTS.add(System.identityHashCode(event));
        event.setCancelled(true);

        for (Player p : event.getRecipients()) {
            p.sendMessage(finalMessage);
        }
        Bukkit.getConsoleSender().sendMessage(finalMessage);
    }

    private Component getItemNameComponent(ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                return item.getItemMeta().displayName();
            } else {
                String name = item.getType().name().replace("_", " ").toLowerCase();
                StringBuilder formatted = new StringBuilder();
                for (String word : name.split(" ")) {
                    if (word.length() > 0)
                        formatted.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
                }
                return Component.text(formatted.toString().trim());
            }
        }
        return Component.text(plugin.getLanguageManager().getMessage("showitem.chat.no-item-name"));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!ConfigManager.isConfigLoaded())
            return;

        if (!event.getMessage().startsWith(INTERNAL_COMMAND_PREFIX))
            return;
        event.setCancelled(true);

        Player viewer = event.getPlayer();
        String[] args = event.getMessage().split(" ");
        if (args.length != 3)
            return;

        executeInternalCommand(viewer, args);
    }

    private void executeInternalCommand(Player viewer, String[] args) {
        UUID targetUUID;
        try {
            targetUUID = UUID.fromString(args[2]);
        } catch (IllegalArgumentException e) {
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        if (!target.isOnline()) {
            Main.sendMessage(this.plugin, viewer, plugin.getLanguageManager().getMessage("general.player-offline"));
            return;
        }

        Player targetPlayer = target.getPlayer();
        if (targetPlayer == null)
            return;

        String type = args[1].toLowerCase();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!viewer.isOnline() || !targetPlayer.isOnline()) return;

            switch (type) {
                case "inv":
                    Inventory inv = showItemManager.createPlayerInventory(viewer, targetPlayer);
                    if (inv != null)
                        viewer.openInventory(inv);
                    break;
                case "ec":
                    Inventory ec = showItemManager.createEnderChestInventory(viewer, targetPlayer);
                    if (ec != null)
                        viewer.openInventory(ec);
                    break;
                case "mainhand":
                    Inventory itemInv = showItemManager.createItemInventory(viewer, targetPlayer, "mainhand");
                    if (itemInv != null)
                        viewer.openInventory(itemInv);
                    break;
            }
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!ConfigManager.isConfigLoaded())
            return;
        if (event.getSlot() < 0)
            return;

        if (!(event.getWhoClicked() instanceof Player))
            return;

        Player viewer = (Player) event.getWhoClicked();

        if (showItemManager.isShowItemViewer(viewer.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;

        Player viewer = (Player) event.getPlayer();
        showItemManager.removeShowItemViewer(viewer.getUniqueId());
    }

    @SuppressWarnings("unused")
    private static final String __Wf7c3e9 = "\u0077\u0069\u0064\u006e" + "\u0065\u0065\u0073";

}
