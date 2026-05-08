package org.widnees.widCore.listener;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.TextParser;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

public class MotdListener implements Listener {

    private final Main plugin;
    private final FileConfiguration motdConfig;
    private final Random random = new Random();
    private final Logger logger;

    public MotdListener(Main plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.motdConfig = plugin.getConfigManager().getModuleConfig("motd");
        registerPacketListener();
    }

    private void registerPacketListener() {
        try {
            
            Class<?> hookClass = Class.forName("org.widnees.widCore.listener.PacketEventsMotdHook");
            java.lang.reflect.Method regMethod = hookClass.getMethod("register",
                    Main.class,
                    org.bukkit.configuration.file.FileConfiguration.class);
            regMethod.invoke(null, plugin, motdConfig);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            
        } catch (Exception ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerListPing(ServerListPingEvent event) {
        List<Map<?, ?>> motdList = motdConfig.getMapList("motds");
        if (motdList != null && !motdList.isEmpty()) {
            Map<?, ?> selectedMotd = motdConfig.getBoolean("random", true)
                    ? motdList.get(random.nextInt(motdList.size()))
                    : motdList.get(0);

            String line1 = selectedMotd.get("line1") instanceof String ? (String) selectedMotd.get("line1") : "";
            String line2 = selectedMotd.get("line2") instanceof String ? (String) selectedMotd.get("line2") : "";

            net.kyori.adventure.text.Component motdComponent = TextParser.parse(line1)
                    .append(net.kyori.adventure.text.Component.newline())
                    .append(TextParser.parse(line2));

            event.motd(motdComponent);

            if (motdConfig.getBoolean("just-one-more", false)) {
                event.setMaxPlayers(event.getNumPlayers() + 1);
            } else {
                int maxPlayers = motdConfig.getInt("max-players", 0);
                if (maxPlayers > 0) {
                    event.setMaxPlayers(maxPlayers);
                }
            }
        }
    }
}