package org.widnees.widCore.hook;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

public class TownyHook {

    private final Plugin plugin;
    private boolean available;

    private Class<?> townyApiClass;
    private java.lang.reflect.Method getInstanceMethod;
    private java.lang.reflect.Method isWildernessMethod; 
    private java.lang.reflect.Method getTownBlockMethod; 
    private java.lang.reflect.Method getTownBlockOrNullMethod; 

    public TownyHook(Plugin plugin) {
        this.plugin = plugin;
        this.available = initialize();
    }

    private boolean initialize() {
        try {
            boolean hasTowny = plugin.getServer().getPluginManager().getPlugin("Towny") != null
                    || plugin.getServer().getPluginManager().getPlugin("TownyAdvanced") != null;
            if (!hasTowny) return false;

            this.townyApiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            this.getInstanceMethod = townyApiClass.getMethod("getInstance");

            try {
                this.isWildernessMethod = townyApiClass.getMethod("isWilderness", org.bukkit.Location.class);
            } catch (NoSuchMethodException ignored) {

            }

            try {
                this.getTownBlockMethod = townyApiClass.getMethod("getTownBlock", org.bukkit.Location.class);
            } catch (NoSuchMethodException ignored) {}

            try {
                this.getTownBlockOrNullMethod = townyApiClass.getMethod("getTownBlockOrNull", org.bukkit.Location.class);
            } catch (NoSuchMethodException ignored) {}

            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isClaimed(Location location) {
        if (!available || location == null) return false;
        try {
            Object api = getInstanceMethod.invoke(null);

            if (isWildernessMethod != null) {
                boolean wilderness = (Boolean) isWildernessMethod.invoke(api, location);
                return !wilderness;
            }

            if (getTownBlockMethod != null) {
                Object townBlock = getTownBlockMethod.invoke(api, location);
                if (townBlock != null) return true;
            }

            if (getTownBlockOrNullMethod != null) {
                Object townBlock = getTownBlockOrNullMethod.invoke(api, location);
                if (townBlock != null) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
