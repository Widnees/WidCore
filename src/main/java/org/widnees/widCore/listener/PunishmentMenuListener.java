package org.widnees.widCore.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.PunishmentMenuManager;
import org.widnees.widCore.manager.PunishmentMenuManager.FilterType;
import org.widnees.widCore.manager.TextParser;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PunishmentMenuListener implements Listener {

    private final Main plugin;
    private final PunishmentMenuManager menuManager;

    // UUID -> isBanList (true = ban, false = mute)
    private final Map<UUID, Boolean> awaitingInput = new ConcurrentHashMap<>();
    // UUID -> countdown task id
    private final Map<UUID, Integer> countdownTasks = new ConcurrentHashMap<>();

    public PunishmentMenuListener(Main plugin, PunishmentMenuManager menuManager) {
        this.plugin = plugin;
        this.menuManager = menuManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        if (!title.startsWith(PunishmentMenuManager.BAN_LIST_TITLE) && !title.startsWith(PunishmentMenuManager.MUTE_LIST_TITLE)) {
            return;
        }

        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }

        int clickedSlot = event.getSlot();
        boolean isBanList = title.startsWith(PunishmentMenuManager.BAN_LIST_TITLE);
        int currentPage = menuManager.getCurrentPage(player.getUniqueId());
        FilterType currentFilter = menuManager.getCurrentFilter(player.getUniqueId());

        switch (clickedSlot) {
            case 45:
                // Search button - close menu and enter chat input mode
                player.closeInventory();
                startSearchInput(player, isBanList);
                break;
            case 48:
                if (currentPage > 1) {
                    menuManager.setReopening(player.getUniqueId(), true);
                    menuManager.openPunishmentListMenu(player, currentPage - 1, isBanList, currentFilter);
                    menuManager.setReopening(player.getUniqueId(), false);
                }
                break;
            case 49:
                player.closeInventory();
                break;
            case 50:
                menuManager.setReopening(player.getUniqueId(), true);
                menuManager.openPunishmentListMenu(player, currentPage + 1, isBanList, currentFilter);
                menuManager.setReopening(player.getUniqueId(), false);
                break;
            case 53:
                // Filter button - cycle to next filter
                FilterType nextFilter = menuManager.nextFilter(currentFilter);
                menuManager.setReopening(player.getUniqueId(), true);
                menuManager.openPunishmentListMenu(player, 1, isBanList, nextFilter);
                menuManager.setReopening(player.getUniqueId(), false);
                break;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.startsWith(PunishmentMenuManager.BAN_LIST_TITLE) || title.startsWith(PunishmentMenuManager.MUTE_LIST_TITLE)) {
            if (event.getPlayer() instanceof Player) {
                Player player = (Player) event.getPlayer();
                UUID uuid = player.getUniqueId();
                // Skip clear if: player is reopening menu (filter/page change), or entering search mode
                if (!awaitingInput.containsKey(uuid) && !menuManager.isReopening(uuid)) {
                    menuManager.clearState(uuid);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!awaitingInput.containsKey(uuid)) return;

        event.setCancelled(true);
        boolean isBanList = awaitingInput.get(uuid);

        String cancelCommand = plugin.getLanguageManager().getMessage("punishment_menu.search-cancel-command");
        String message = event.getMessage().trim();

        // Cancel countdown task
        cancelCountdown(uuid);
        awaitingInput.remove(uuid);

        // Clear title
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.clearTitle();
        });

        if (message.equalsIgnoreCase(cancelCommand)) {
            // Cancelled
            String cancelMsg = plugin.getLanguageManager().getMessage("punishment_menu.search-cancelled");
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(TextParser.colorize(cancelMsg));
                menuManager.clearState(uuid);
            });
            return;
        }

        // Set search and reopen menu on main thread
        String searchName = message;
        Bukkit.getScheduler().runTask(plugin, () -> {
            menuManager.setCurrentSearch(uuid, searchName);
            menuManager.openPunishmentListMenu(player, 1, isBanList, menuManager.getCurrentFilter(uuid));
        });
    }

    private void startSearchInput(Player player, boolean isBanList) {
        UUID uuid = player.getUniqueId();
        awaitingInput.put(uuid, isBanList);

        String cancelCommand = plugin.getLanguageManager().getMessage("punishment_menu.search-cancel-command");
        String titleText = plugin.getLanguageManager().getMessage("punishment_menu.search-title");

        // Start countdown
        int[] seconds = {10};
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!awaitingInput.containsKey(uuid)) {
                    this.cancel();
                    return;
                }
                if (seconds[0] <= 0) {
                    // Timeout
                    awaitingInput.remove(uuid);
                    countdownTasks.remove(uuid);
                    player.clearTitle();
                    menuManager.clearState(uuid);
                    this.cancel();
                    return;
                }

                String subtitleText = plugin.getLanguageManager().getMessage("punishment_menu.search-subtitle")
                        .replace("%seconds%", String.valueOf(seconds[0]))
                        .replace("%cancel%", cancelCommand);

                player.showTitle(net.kyori.adventure.title.Title.title(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(titleText),
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(subtitleText),
                        net.kyori.adventure.title.Title.Times.times(
                                java.time.Duration.ofMillis(200),
                                java.time.Duration.ofMillis(1200),
                                java.time.Duration.ofMillis(200)
                        )
                ));

                seconds[0]--;
            }
        }.runTaskTimer(plugin, 0L, 20L).getTaskId();

        countdownTasks.put(uuid, taskId);
    }

    private void cancelCountdown(UUID uuid) {
        Integer taskId = countdownTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    /**
     * Called on plugin disable/reload. Cancels all pending search countdowns
     * and clears the awaiting-input state so no player is left chat-blocked.
     */
    public void shutdown() {
        for (UUID uuid : new java.util.HashSet<>(countdownTasks.keySet())) {
            cancelCountdown(uuid);
        }
        for (UUID uuid : new java.util.HashSet<>(awaitingInput.keySet())) {
            awaitingInput.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.clearTitle();
            }
        }
    }

    @SuppressWarnings("unused")
    private static final String _0xCr3a7F = "\u0077\u0031\u0064\u006e\u0065\u0065\u0073";
}
