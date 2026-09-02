package org.widnees.widCore.manager;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.util.StringUtil;
import org.widnees.widCore.Main;
import org.widnees.widCore.util.FoliaScheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory index of every known player name. Loaded once asynchronously so
 * tab-complete never calls {@code Bukkit.getOfflinePlayers()} on the main thread.
 */
public class PlayerNameCache implements Listener {

    private final Main plugin;
    private final ConcurrentHashMap<String, UUID> nameToUuid = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> uuidToName = new ConcurrentHashMap<>();
    private volatile List<String> namesSnapshot = Collections.emptyList();
    private volatile boolean stopped = false;
    private final Object snapshotLock = new Object();

    public PlayerNameCache(Main plugin) {
        this.plugin = plugin;
        seedOnlinePlayers();
        FoliaScheduler.runTaskAsync(plugin, this::loadAllOfflinePlayers);
    }

    public void shutdown() {
        this.stopped = true;
    }

    /**
     * Resolve a known player without a Mojang name lookup.
     * Returns {@code null} when the name is not in the cache (never played / still loading).
     */
    public OfflinePlayer resolveKnown(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        UUID uuid = getUuid(name);
        if (uuid == null) {
            return null;
        }
        return Bukkit.getOfflinePlayer(uuid);
    }

    public UUID getUuid(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return nameToUuid.get(name.toLowerCase(Locale.ROOT));
    }

    public String getName(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return uuidToName.get(uuid);
    }

    /**
     * Tab-complete against cached names. Never touches disk.
     *
     * @param includeOffline {@code true} for invsee/ec/irp; {@code false} for online-only commands
     */
    public List<String> complete(CommandSender sender, String prefix, boolean includeOffline) {
        List<String> source;
        if (includeOffline) {
            source = hideVanishedNames(sender, namesSnapshot);
        } else {
            VanishManager vanishManager = plugin.getVanishManager();
            if (vanishManager != null) {
                source = vanishManager.getVisiblePlayerNames(sender);
            } else {
                source = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    source.add(online.getName());
                }
            }
        }

        List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(prefix == null ? "" : prefix, source, completions);
        Collections.sort(completions);
        return completions;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (stopped) {
            return;
        }
        Player player = event.getPlayer();
        remember(player.getUniqueId(), player.getName());
    }

    public void remember(UUID uuid, String name) {
        if (stopped || uuid == null || name == null || name.isEmpty()) {
            return;
        }
        if (!put(uuid, name)) {
            return;
        }
        rebuildSnapshot();
    }

    private void seedOnlinePlayers() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            put(online.getUniqueId(), online.getName());
        }
        rebuildSnapshot();
    }

    private void loadAllOfflinePlayers() {
        if (stopped) {
            return;
        }
        try {
            OfflinePlayer[] players = Bukkit.getOfflinePlayers();
            if (stopped) {
                return;
            }
            for (OfflinePlayer offline : players) {
                if (stopped) {
                    return;
                }
                if (offline == null) {
                    continue;
                }
                String name = offline.getName();
                if (name == null || name.isEmpty()) {
                    continue;
                }
                put(offline.getUniqueId(), name);
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                put(online.getUniqueId(), online.getName());
            }
            rebuildSnapshot();
            plugin.getLogger().info("Player name cache loaded: " + uuidToName.size() + " players");
        } catch (Exception e) {
            plugin.getLogger().warning("Player name cache failed to load: " + e.getMessage());
            rebuildSnapshot();
        }
    }

    /**
     * @return {@code true} when the mapping changed
     */
    private boolean put(UUID uuid, String name) {
        String previous = uuidToName.put(uuid, name);
        if (previous != null && !previous.equalsIgnoreCase(name)) {
            nameToUuid.remove(previous.toLowerCase(Locale.ROOT), uuid);
        }
        UUID replaced = nameToUuid.put(name.toLowerCase(Locale.ROOT), uuid);
        return previous == null || !previous.equals(name) || replaced == null || !replaced.equals(uuid);
    }

    private void rebuildSnapshot() {
        synchronized (snapshotLock) {
            namesSnapshot = Collections.unmodifiableList(new ArrayList<>(uuidToName.values()));
        }
    }

    private List<String> hideVanishedNames(CommandSender sender, List<String> source) {
        VanishManager vanishManager = plugin.getVanishManager();
        if (vanishManager == null || vanishManager.getVanishedCount() == 0) {
            return source;
        }
        Set<String> hidden = new HashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (vanishManager.isHiddenFrom(online, sender)) {
                hidden.add(online.getName());
            }
        }
        if (hidden.isEmpty()) {
            return source;
        }
        List<String> visible = new ArrayList<>(source.size());
        for (String name : source) {
            if (!hidden.contains(name)) {
                visible.add(name);
            }
        }
        return visible;
    }

    @SuppressWarnings("unused")
    private static final String __pNc8k2 = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}
