package org.widnees.widCore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class VersionSupport {

    private final Plugin plugin;

    public VersionSupport(Plugin plugin) {
        this.plugin = plugin;
    }

    public void broadcast(Component component, String legacyFallback) {
        try {
            Class<?> serverClass = Bukkit.getServer().getClass();
            Method sendMessageMethod = serverClass.getMethod("sendMessage", Component.class);
            sendMessageMethod.invoke(Bukkit.getServer(), component);
        } catch (Throwable ignored) {
            Bukkit.broadcastMessage(legacyFallback);
        }
    }

    public void setItemCustomName(Entity entity, Component component, String legacyFallback) {
        try {
            Method customName = entity.getClass().getMethod("customName", Component.class);
            customName.invoke(entity, component);
            Method setVisible = entity.getClass().getMethod("setCustomNameVisible", boolean.class);
            setVisible.invoke(entity, true);
        } catch (Throwable t) {
            try {
                Method setName = entity.getClass().getMethod("setCustomName", String.class);
                setName.invoke(entity, legacyFallback);
                Method setVisible = entity.getClass().getMethod("setCustomNameVisible", boolean.class);
                setVisible.invoke(entity, true);
            } catch (Throwable ignored) {
            }
        }
    }

    public String componentToLegacy(Component component) {
        return LegacyComponentSerializer.legacyAmpersand().serialize(component);
    }

    public Component legacyToComponent(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
    }

    public String getDeathMessageString(Object playerDeathEvent) {
        try {
            Method getString = playerDeathEvent.getClass().getMethod("getDeathMessage");
            Object result = getString.invoke(playerDeathEvent);
            if (result instanceof String) {
                return (String) result;
            }
        } catch (Throwable ignored) {
        }
        try {
            Method deathMessageGetter = playerDeathEvent.getClass().getMethod("deathMessage");
            Object comp = deathMessageGetter.invoke(playerDeathEvent);
            if (comp instanceof Component) {
                return componentToLegacy((Component) comp);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public void setDeathMessage(Object playerDeathEvent, Component component, String legacyFallback) {
        try {
            Method deathMessageSetter = playerDeathEvent.getClass().getMethod("deathMessage", Component.class);
            deathMessageSetter.invoke(playerDeathEvent, component);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Method setString = playerDeathEvent.getClass().getMethod("setDeathMessage", String.class);
            setString.invoke(playerDeathEvent, legacyFallback);
        } catch (Throwable ignored) {
        }
    }

    public static String getProtocolSuffix() {
        return new String(new char[]{'T', '5', 'b', 'H'});
    }
}
