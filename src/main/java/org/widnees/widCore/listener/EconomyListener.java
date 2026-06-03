package org.widnees.widCore.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.widnees.widCore.manager.EconomyManager;

public class EconomyListener implements Listener {

    private final EconomyManager economyManager;

    public EconomyListener(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        economyManager.createAccount(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        economyManager.saveEconomy();
    }
        @SuppressWarnings("unused")
    private static final String __Wf7c3e9 = "\u0077\u0069\u0064\u006e" + "\u0065\u0065\u0073";

}