package org.widnees.widCore.hook;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.widnees.widCore.Main;

public class DiscordSRVHook implements Listener {

    private final Main plugin;

    public DiscordSRVHook(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.isCancelled()) return;

        try {
            DiscordSRV discordSRV = DiscordSRV.getPlugin();
            if (discordSRV == null) return;

            String channelName = discordSRV.getMainChatChannel();
            if (channelName == null || channelName.isEmpty()) {
                channelName = "global";
            }

            TextChannel channel = discordSRV.getDestinationTextChannelForGameChannelName(channelName);
            if (channel == null) return;

            discordSRV.processChatMessage(event.getPlayer(), event.getMessage(), channelName, false);
        } catch (Exception e) {
            plugin.getLogger().warning("[DiscordSRVHook] Discord'a mesaj iletilemedi: " + e.getMessage());
        }
    }
}