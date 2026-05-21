package org.widnees.widCore.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.widnees.widCore.Main;

import java.nio.charset.StandardCharsets;

public final class PacketEventsBrandHider extends PacketListenerAbstract {

    private final String brand;
    private final byte[] brandBytes;

    private PacketEventsBrandHider(String brand) {
        this.brand = brand != null && !brand.isEmpty() ? brand : "vanilla";
        this.brandBytes = encodeBrandPayload(this.brand);
    }

    public static void register(Main plugin, FileConfiguration cfg) {
        try {
            if (PacketEvents.getAPI() == null) {
                return;
            }
            String value = cfg.getString("brand-name", "vanilla");
            PacketEvents.getAPI().getEventManager().registerListener(new PacketEventsBrandHider(value));

        } catch (Throwable t) {
            plugin.getLogger().warning("[WidCore] Failed to register brand customizer: " + t.getMessage());
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        try {
            ConnectionState state = event.getConnectionState();
            if (state != ConnectionState.PLAY && state != ConnectionState.CONFIGURATION) {
                return;
            }

            Object packetType = event.getPacketType();

            if (packetType == PacketType.Configuration.Server.PLUGIN_MESSAGE) {
                WrapperConfigServerPluginMessage wrapper = new WrapperConfigServerPluginMessage(event);
                if ("minecraft:brand".equals(wrapper.getChannelName())) {
                    wrapper.setData(brandBytes);
                    event.markForReEncode(true);
                }
                return;
            }

            if (packetType == PacketType.Play.Server.PLUGIN_MESSAGE) {
                WrapperPlayServerPluginMessage wrapper = new WrapperPlayServerPluginMessage(event);
                if ("minecraft:brand".equals(wrapper.getChannelName())) {
                    wrapper.setData(brandBytes);
                    event.markForReEncode(true);
                }
            }
        } catch (Throwable ignored) {

        }
    }

    private static byte[] encodeBrandPayload(String brand) {
        byte[] data = brand.getBytes(StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(data.length + 5);
        writeVarInt(out, data.length);
        out.write(data, 0, data.length);
        return out.toByteArray();
    }

    private static void writeVarInt(java.io.ByteArrayOutputStream out, int value) {
        while ((value & -128) != 0) {
            out.write(value & 127 | 128);
            value >>>= 7;
        }
        out.write(value);
    }
}
