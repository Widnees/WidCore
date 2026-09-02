package org.widnees.widCore.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderHook;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.events.ExpansionRegisterEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.VanishManager;

import java.util.Locale;
import java.util.UUID;

public final class VanishServerPlaceholderHook {

    private static final String SERVER_IDENTIFIER = "server";

    private final Main plugin;
    private Listener rewrapListener;
    private VanishAwareServerExpansion wrapper;
    private boolean wrapping;

    private VanishServerPlaceholderHook(Main plugin) {
        this.plugin = plugin;
    }

    public static @Nullable VanishServerPlaceholderHook tryRegister(Main plugin) {
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) {
            return null;
        }
        try {
            VanishServerPlaceholderHook hook = new VanishServerPlaceholderHook(plugin);
            hook.install();
            return hook;
        } catch (Throwable t) {
            plugin.getLogger().warning("PlaceholderAPI vanish count hook failed: " + t.getMessage());
            return null;
        }
    }

    private void install() {
        wrapServerExpansion();
        rewrapListener = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onExpansionRegister(ExpansionRegisterEvent event) {
                PlaceholderExpansion expansion = event.getExpansion();
                if (expansion == null || wrapping) {
                    return;
                }
                if (expansion instanceof VanishAwareServerExpansion) {
                    return;
                }
                String id = expansion.getIdentifier();
                if (id != null && SERVER_IDENTIFIER.equalsIgnoreCase(id)) {
                    Bukkit.getScheduler().runTask(plugin, () -> wrapServerExpansion());
                }
            }
        };
        Bukkit.getPluginManager().registerEvents(rewrapListener, plugin);
    }

    public void uninstall() {
        if (rewrapListener != null) {
            HandlerList.unregisterAll(rewrapListener);
            rewrapListener = null;
        }
        wrapping = true;
        try {
            if (wrapper != null) {
                try {
                    wrapper.unregister();
                } catch (Throwable ignored) {
                }
                PlaceholderExpansion original = wrapper.getOriginal();
                if (original != null) {
                    try {
                        original.register();
                    } catch (Throwable ignored) {
                    }
                }
                wrapper = null;
            }
        } finally {
            wrapping = false;
        }
    }

    private void wrapServerExpansion() {
        if (wrapping) {
            return;
        }
        wrapping = true;
        try {
            PlaceholderHook current = PlaceholderAPI.getPlaceholders().get(SERVER_IDENTIFIER);

            if (current instanceof VanishAwareServerExpansion) {
                wrapper = (VanishAwareServerExpansion) current;
                return;
            }

            PlaceholderExpansion original = null;
            if (current instanceof PlaceholderExpansion) {
                original = (PlaceholderExpansion) current;
            }

            if (wrapper != null) {
                try {
                    wrapper.unregister();
                } catch (Throwable ignored) {
                }
                if (original == null) {
                    original = wrapper.getOriginal();
                }
                wrapper = null;
            }

            if (original != null) {
                try {
                    original.unregister();
                } catch (Throwable ignored) {
                }
            }

            VanishAwareServerExpansion next = new VanishAwareServerExpansion(plugin, original);
            if (!next.register()) {
                if (original != null) {
                    try {
                        original.register();
                    } catch (Throwable ignored) {
                    }
                }
                return;
            }
            wrapper = next;
        } finally {
            wrapping = false;
        }
    }


    static int countVanishedOnline(Main plugin) {
        int vanishedOnline = 0;
        for (UUID uuid : plugin.getVanishedPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                vanishedOnline++;
            }
        }
        return vanishedOnline;
    }

    static int countNonVanishedOnline(Main plugin) {
        VanishManager vanish = plugin.getVanishManager();
        if (vanish != null) {
            return vanish.getOnlineCountExcludingVanished();
        }
        return Math.max(0, Bukkit.getOnlinePlayers().size() - countVanishedOnline(plugin));
    }

    static int countNonVanishedInWorld(Main plugin, String worldName) {
        VanishManager vanish = plugin.getVanishManager();
        if (vanish != null) {
            return vanish.getOnlineCountExcludingVanished(worldName);
        }
        int count = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getWorld() != null
                    && online.getWorld().getName().equalsIgnoreCase(worldName)
                    && !plugin.getVanishedPlayers().contains(online.getUniqueId())) {
                count++;
            }
        }
        return count;
    }


    private static final class VanishAwareServerExpansion extends PlaceholderExpansion {

        private final Main plugin;
        private final PlaceholderExpansion original;

        VanishAwareServerExpansion(Main plugin, @Nullable PlaceholderExpansion original) {
            this.plugin = plugin;
            this.original = original;
        }

        @Nullable
        PlaceholderExpansion getOriginal() {
            return original;
        }

        @Override
        public @NotNull String getIdentifier() {
            return SERVER_IDENTIFIER;
        }

        @Override
        public @NotNull String getAuthor() {
            return original != null ? original.getAuthor() : "WidCore";
        }

        @Override
        public @NotNull String getVersion() {
            return original != null ? original.getVersion() : plugin.getDescription().getVersion();
        }

        @Override
        public @NotNull String getName() {
            return original != null ? original.getName() : SERVER_IDENTIFIER;
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public boolean canRegister() {
            return true;
        }

        @Override
        public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
            if (params == null) {
                return null;
            }

            String key = params.toLowerCase(Locale.ROOT);
            VanishManager vanish = plugin.getVanishManager();

            if (key.equals("online")) {
                return String.valueOf(vanish != null
                        ? vanish.getOnlineCountExcludingVanished()
                        : countNonVanishedOnline(plugin));
            }

            if (key.startsWith("online_")) {
                String worldName = params.substring("online_".length());
                if (!worldName.isEmpty()) {
                    return String.valueOf(vanish != null
                            ? vanish.getOnlineCountExcludingVanished(worldName)
                            : countNonVanishedInWorld(plugin, worldName));
                }
            }

            if (original == null) {
                return null;
            }
            String result = original.onRequest(player, params);
            if (result != null) {
                return result;
            }
            if (player instanceof Player) {
                return original.onPlaceholderRequest((Player) player, params);
            }
            return null;
        }

        @Override
        public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
            return onRequest(player, params);
        }
    }
}
