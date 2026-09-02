package org.widnees.widCore.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.configuration.file.FileConfiguration;
import org.widnees.widCore.Main;

public class PacketEventsMotdHook extends PacketListenerAbstract {

    private final Main plugin;
    private final FileConfiguration motdConfig;

    private static PacketEventsMotdHook instance;

    private PacketEventsMotdHook(Main plugin, FileConfiguration motdConfig) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        this.motdConfig = motdConfig;
    }

    public static void register(Main plugin, FileConfiguration motdConfig) {
        if (instance != null) {
            unregister();
        }
        instance = new PacketEventsMotdHook(plugin, motdConfig);
        PacketEvents.getAPI().getEventManager().registerListener(instance);
    }

    public static void unregister() {
        if (instance != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(instance);
            instance = null;
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Status.Server.RESPONSE) return;

        try {
            WrapperStatusServerResponse wrapper = new WrapperStatusServerResponse(event);
            JsonObject root = wrapper.getComponent();
            if (root == null) return;

            boolean changed = false;

            JsonObject players = root.has("players") ? root.getAsJsonObject("players") : null;
            if (players != null) {

                if (plugin.getConfig().getBoolean("features.vanish", false)) {
                    int vanishedCount = plugin.getVanishedPlayers().size();
                    int online = players.has("online") ? players.get("online").getAsInt() : 0;
                    int adjusted = Math.max(0, online - vanishedCount);
                    if (adjusted != online) {
                        players.addProperty("online", adjusted);
                        changed = true;
                    }
                }

                if (motdConfig.getBoolean("disable-player-list-hover", false)) {
                    players.add("sample", new JsonArray());
                    changed = true;
                }
            }

            if (changed) {
                wrapper.setComponent(root);
            }

        } catch (Exception ignored) {

        }
    }
        @SuppressWarnings("unused")
    private static final String _0xCr3a7F = "\u0077\u0031\u0064\u006e\u0065\u0065\u0073";

}