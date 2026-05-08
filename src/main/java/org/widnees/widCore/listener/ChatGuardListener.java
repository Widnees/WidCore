package org.widnees.widCore.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ChatGuardManager;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.TextParser;
import org.widnees.widCore.manager.chatguard.ChatGuardResult;

public class ChatGuardListener implements Listener {

    private final Main plugin;
    private final ChatGuardManager chatGuardManager;

    public ChatGuardListener(Main plugin, ChatGuardManager chatGuardManager) {
        this.plugin = plugin;
        this.chatGuardManager = chatGuardManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!ConfigManager.isConfigLoaded()) return;

        Player player = event.getPlayer();
        String message = event.getMessage();

        if (hasBypassPermission(player)) return;

        if (plugin.getPunishmentManager().isMuted(player.getUniqueId())) {
            return;
        }

        ChatGuardResult result = chatGuardManager.checkMessage(player, message);

        if (!result.isAllowed()) {
            event.setCancelled(true);

            Bukkit.getScheduler().runTask(plugin, () -> {
                String warningMessage = getWarningMessage(result);
                Main.sendMessage(this.plugin, player, warningMessage);
                sendAlert(player, message, result, "CHAT");
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnvilRename(PrepareAnvilEvent event) {
        if (!ConfigManager.isConfigLoaded()) return;

        if (event.getResult() == null) return;
        Player player = (Player) event.getView().getPlayer();
        if (hasBypassPermission(player)) return;

        ItemStack resultItem = event.getResult();
        if (resultItem.hasItemMeta() && resultItem.getItemMeta().hasDisplayName()) {
            String itemName = resultItem.getItemMeta().getDisplayName();
            ChatGuardResult guardResult = chatGuardManager.checkBannedWordsOnly(itemName);

            if (!guardResult.isAllowed()) {
                event.setResult(null);
                String warningMessage = getWarningMessage(guardResult);
                Main.sendMessage(this.plugin, player, warningMessage);
                sendAlert(player, itemName, guardResult, "ANVIL");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBookEdit(PlayerEditBookEvent event) {
        if (!ConfigManager.isConfigLoaded()) return;

        Player player = event.getPlayer();
        if (hasBypassPermission(player)) return;

        BookMeta bookMeta = event.getNewBookMeta();
        if (bookMeta.hasPages()) {
            for (String page : bookMeta.getPages()) {
                ChatGuardResult result = chatGuardManager.checkBannedWordsOnly(page);
                if (!result.isAllowed()) {
                    event.setCancelled(true);
                    String warningMessage = getWarningMessage(result);
                    Main.sendMessage(this.plugin, player, warningMessage);
                    sendAlert(player, page, result, "BOOK");
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!ConfigManager.isConfigLoaded()) return;

        Player player = event.getPlayer();
        if (hasBypassPermission(player)) return;

        String fullText = String.join(" ", event.getLines());
        if (!fullText.trim().isEmpty()) {
            ChatGuardResult result = chatGuardManager.checkBannedWordsOnly(fullText);
            if (!result.isAllowed()) {
                for (int i = 0; i < 4; i++) {
                    event.setLine(i, "");
                }
                String warningMessage = getWarningMessage(result);
                Main.sendMessage(this.plugin, player, warningMessage);
                sendAlert(player, fullText, result, "SIGN");
            }
        }
    }

    private boolean hasBypassPermission(Player player) {
        return player.isOp() || player.hasPermission("widcore.chatguard.bypass");
    }

    private void sendAlert(Player violator, String content, ChatGuardResult result, String eventType) {
        String reasonKey;
        String detectedContent = result.getData() != null ? result.getData() : "";

        switch (result.getType()) {
            case SPAM:
                reasonKey = "chatguard.reasons.spam";
                break;
            case FLOOD_REPEAT:
                reasonKey = "chatguard.reasons.flood-repeat";
                break;
            case FLOOD_SIMILAR:
                reasonKey = "chatguard.reasons.flood-similar";
                break;
            case ADVERTISEMENT_IP:
                reasonKey = "chatguard.reasons.ad-ip";
                break;
            case ADVERTISEMENT_DOMAIN:
                reasonKey = "chatguard.reasons.ad-domain";
                detectedContent = content;
                break;
            case ADVERTISEMENT_DISCORD:
                reasonKey = "chatguard.reasons.ad-discord";
                detectedContent = content;
                break;
            default:
                reasonKey = "chatguard.reasons.banned-word";
                break;
        }

        String reason = plugin.getLanguageManager().getMessage(reasonKey)
                .replace("%data%", detectedContent);

        String alertMessage = plugin.getLanguageManager().getMessage("chatguard.alert")
                .replace("%player%", violator.getName())
                .replace("%reason%", reason)
                .replace("%source%", eventType)
                .replace("%content%", content);

        Main.sendMessage(this.plugin, Bukkit.getConsoleSender(), alertMessage);

        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission("widcore.chatguard.alert"))
                .filter(player -> !player.equals(violator))
                .forEach(admin -> Main.sendMessage(this.plugin, admin, alertMessage));
    }

    private String getWarningMessage(ChatGuardResult result) {
        String reasonKey;
        switch (result.getType()) {
            case BANNED_WORD:
            case BANNED_WORD_SYMBOL:
            case BANNED_WORD_SQUEEZED:
            case BANNED_WORD_CONSONANT:
                reasonKey = "chatguard.warning-reasons.banned-word";
                break;
            case SPAM:
                reasonKey = "chatguard.warning-reasons.spam";
                break;
            case FLOOD_REPEAT:
            case FLOOD_SIMILAR:
                reasonKey = "chatguard.warning-reasons.flood";
                break;
            case ADVERTISEMENT_DOMAIN:
            case ADVERTISEMENT_IP:
            case ADVERTISEMENT_DISCORD:
                reasonKey = "chatguard.warning-reasons.ad";
                break;
            default:
                reasonKey = "chatguard.warning-reasons.default";
                break;
        }

        String reason = plugin.getLanguageManager().getMessage(reasonKey)
                .replace("%data%", result.getData() != null ? result.getData() : "");

        return plugin.getLanguageManager().getMessage("chatguard.warning")
                .replace("%reason%", reason);
    }
}