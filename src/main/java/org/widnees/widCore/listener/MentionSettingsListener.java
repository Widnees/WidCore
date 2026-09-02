package org.widnees.widCore.listener;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.command.MentionCommand;
import org.widnees.widCore.database.BinaryDataManager;

public class MentionSettingsListener implements Listener {

    private final Main plugin;
    private final MentionCommand mentionCommand;

    public MentionSettingsListener(Main plugin, MentionCommand mentionCommand) {
        this.plugin = plugin;
        this.mentionCommand = mentionCommand;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        String title = event.getView().getTitle();
        if (title == null || !title.equals(mentionCommand.getMenuTitle())) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == 8) {
            player.closeInventory();
            return;
        }

        BinaryDataManager.MentionPrefsData data = plugin.getMentionPrefsData();
        BinaryDataManager.MentionPrefs prefs = data.players.computeIfAbsent(
                player.getUniqueId(), uuid -> mentionCommand.createDefaultPrefs());

        boolean changed = true;
        switch (slot) {
            case 0: prefs.enabled   = !prefs.enabled;   break;
            case 2: prefs.title     = !prefs.title;     break;
            case 3: prefs.actionbar = !prefs.actionbar; break;
            case 4: prefs.toast     = !prefs.toast;     break;
            case 5: prefs.sound     = !prefs.sound;     break;
            default: changed = false;
        }

        if (changed) {
            plugin.getDataManager().saveMentionPrefs(data);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
            mentionCommand.openSettingsMenu(player);
        }
    }
        @SuppressWarnings("unused")
    private static final String _W3f0b7c = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}
