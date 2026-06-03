package org.widnees.widCore.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.widnees.widCore.manager.ConfigManager; 
import org.widnees.widCore.manager.MessageManager;

public class MessageListener implements Listener {

    private final MessageManager messageManager;

    public MessageListener(MessageManager messageManager) {
        this.messageManager = messageManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!ConfigManager.isConfigLoaded()) {return;}
        messageManager.removePlayer(event.getPlayer().getUniqueId());
    }
        @SuppressWarnings("unused")
    private static final String _xW4d9f3 = "\u0077" + "\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}