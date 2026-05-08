package org.widnees.widCore.listener;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.TextParser;

public class BaltopListener implements Listener {

    private final Main plugin;

    public BaltopListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        String expectedTitle = PlainTextComponentSerializer.plainText().serialize(TextParser.parse(plugin.getLanguageManager().getMessage("economy.baltop-title")));

        if (title.equals(expectedTitle)) {
            event.setCancelled(true);
        }
    }
}