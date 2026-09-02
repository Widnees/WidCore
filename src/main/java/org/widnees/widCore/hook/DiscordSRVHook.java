package org.widnees.widCore.hook;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.widnees.widCore.Main;

/**
 * DiscordSRV entegrasyon hook'u.
 *
 * Sorun: EssentialsChat veya WidCore ChatFormat gibi eklentiler
 * AsyncPlayerChatEvent'i cancel ediyor. DiscordSRV varsayılan olarak
 * cancel edilmiş event'leri yok sayar, bu yüzden Discord'a hiçbir şey gitmiyor.
 *
 * Çözüm: Bu listener MONITOR priority + ignoreCancelled=false ile çalışır.
 * Event zincirinin en sonunda devreye girer. Event cancel edilmişse
 * (yani başka bir eklenti işledi), DiscordSRV API'sini çağırarak
 * mesajı Discord'a biz iletiyoruz.
 */
public class DiscordSRVHook implements Listener {

    private final Main plugin;

    public DiscordSRVHook(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        // Eğer event cancel edilmemişse DiscordSRV zaten kendi listener'ıyla halleder.
        // Sadece cancel edilmiş durumlarda müdahale ediyoruz.
        if (!event.isCancelled()) return;

        try {
            DiscordSRV discordSRV = DiscordSRV.getPlugin();
            if (discordSRV == null) return;

            // DiscordSRV'nin desteklediği channel adı ("global" varsayılan)
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