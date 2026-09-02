package org.widnees.widCore.manager;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.widnees.widCore.Main;
import org.widnees.widCore.database.BinaryDataManager;
import org.widnees.widCore.manager.PunishmentManager;
import org.widnees.widCore.manager.TextParser;

public class JailManager {
    private final Main plugin;
    private final File jailsFile;
    private FileConfiguration jailsConfig;
    private PunishmentManager punishmentManager;
    private final Map<String, Jail> jails = new HashMap<String, Jail>();
    private final Map<UUID, SetupSession> setupSessions = new HashMap<UUID, SetupSession>();
    private final Map<UUID, ItemStack[]> savedInventorySlots = new HashMap<UUID, ItemStack[]>();
    private final Map<String, PendingJail> pendingJails = new HashMap<String, PendingJail>();
    private boolean useWhitelistMode;
    private List<String> blockedCommands;
    private List<String> whitelistCommands;
    public static final NamespacedKey JAIL_AXE_KEY = new NamespacedKey((Plugin)Main.getPlugin(Main.class), "jail_axe");
    public static final NamespacedKey JAIL_LANTERN_KEY = new NamespacedKey((Plugin)Main.getPlugin(Main.class), "jail_lantern");

    public JailManager(Main plugin) {
        this.plugin = plugin;
        File databaseDir = new File(plugin.getDataFolder(), "database");
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }
        this.jailsFile = new File(databaseDir, "jails.yml");
    }

    public void loadJails() {
        if (this.punishmentManager == null) {
            this.punishmentManager = this.plugin.getPunishmentManager();
        }
        this.jailsConfig = this.plugin.getConfigManager().getModuleConfig("jail");
        this.useWhitelistMode = this.jailsConfig.getBoolean("command-enforcement.use-whitelist-mode", false);
        this.blockedCommands = this.jailsConfig.getStringList("command-enforcement.blocked-commands").stream().map(String::toLowerCase).toList();
        this.whitelistCommands = this.jailsConfig.getStringList("command-enforcement.whitelist-commands").stream().map(String::toLowerCase).toList();
        File dataFile = new File(this.plugin.getDataFolder(), "database/jails.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            }
            catch (IOException e) {
                String msg = String.valueOf(this.plugin.getLanguageManager().getMessage("database.create-error").replace("%file%", "jails.yml")) + ": " + e.getMessage();
                this.plugin.getLogger().severe(msg);
            }
            return;
        }
        YamlConfiguration dataConfig = YamlConfiguration.loadConfiguration((File)dataFile);
        this.jails.clear();
        this.pendingJails.clear();
        ConfigurationSection jailsSection = dataConfig.getConfigurationSection("jails");
        if (jailsSection != null) {
            for (String jailName : jailsSection.getKeys(false)) {
                String worldName;
                ConfigurationSection spawnSection;
                ConfigurationSection jailData = jailsSection.getConfigurationSection(jailName);
                if (jailData == null || (spawnSection = jailData.getConfigurationSection("spawn")) == null || (worldName = spawnSection.getString("world")) == null) continue;
                Location spawn = this.parseLocation(spawnSection);
                if (spawn != null && spawn.getWorld() != null) {
                    Jail jail = new Jail(spawn);
                    jail.pos1 = this.parseLocation(jailData.getConfigurationSection("pos1"));
                    jail.pos2 = this.parseLocation(jailData.getConfigurationSection("pos2"));
                    this.jails.put(jailName.toLowerCase(), jail);
                    continue;
                }
                this.pendingJails.put(jailName.toLowerCase(), new PendingJail(worldName, spawnSection, jailData.getConfigurationSection("pos1"), jailData.getConfigurationSection("pos2")));
            }
        }
    }

    public void resolvePendingJails(String worldName) {
        this.pendingJails.entrySet().removeIf(entry -> {
            Location spawn;
            PendingJail pending = (PendingJail)entry.getValue();
            if (pending.worldName.equals(worldName) && (spawn = this.parseLocation(pending.spawnSection)) != null && spawn.getWorld() != null) {
                Jail jail = new Jail(spawn);
                jail.pos1 = this.parseLocation(pending.pos1Section);
                jail.pos2 = this.parseLocation(pending.pos2Section);
                this.jails.put((String)entry.getKey(), jail);
                return true;
            }
            return false;
        });
    }

    private Location parseLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld((String)worldName);
        if (world == null) {
            return null;
        }
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float)section.getDouble("yaw");
        float pitch = (float)section.getDouble("pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    public void saveJails() {
        File dataFile = new File(this.plugin.getDataFolder(), "database/jails.yml");
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, Jail> entry : this.jails.entrySet()) {
            String path = "jails." + entry.getKey();
            this.saveLocation((FileConfiguration)config, String.valueOf(path) + ".spawn", entry.getValue().spawn);
            this.saveLocation((FileConfiguration)config, String.valueOf(path) + ".pos1", entry.getValue().pos1);
            this.saveLocation((FileConfiguration)config, String.valueOf(path) + ".pos2", entry.getValue().pos2);
        }
        try {
            config.save(dataFile);
        }
        catch (IOException e) {
            String msg = String.valueOf(this.plugin.getLanguageManager().getMessage("database.save-error").replace("%file%", "jails.yml")) + ": " + e.getMessage();
            this.plugin.getLogger().severe(msg);
        }
    }

    private void saveLocation(FileConfiguration config, String path, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        config.set(String.valueOf(path) + ".world", (Object)loc.getWorld().getName());
        config.set(String.valueOf(path) + ".x", (Object)loc.getX());
        config.set(String.valueOf(path) + ".y", (Object)loc.getY());
        config.set(String.valueOf(path) + ".z", (Object)loc.getZ());
        config.set(String.valueOf(path) + ".yaw", (Object)Float.valueOf(loc.getYaw()));
        config.set(String.valueOf(path) + ".pitch", (Object)Float.valueOf(loc.getPitch()));
    }

    public int getJailPlayerCount(String jailName) {
        if (this.punishmentManager == null) {
            return 0;
        }
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            BinaryDataManager.JailEntry entry;
            if (!this.punishmentManager.isJailed(player.getUniqueId()) || (entry = this.punishmentManager.getJailEntry(player.getUniqueId())) == null || !entry.jailName.equalsIgnoreCase(jailName)) continue;
            ++count;
        }
        return count;
    }

    public String getEmptiestJail() {
        if (this.jails.isEmpty()) {
            return null;
        }
        String emptiestJail = null;
        int minPlayers = Integer.MAX_VALUE;
        for (String jailName : this.jails.keySet()) {
            int playerCount = this.getJailPlayerCount(jailName);
            if (playerCount >= minPlayers) continue;
            minPlayers = playerCount;
            emptiestJail = jailName;
        }
        return emptiestJail;
    }

    public boolean jailExists(String name) {
        return this.jails.containsKey(name.toLowerCase());
    }

    public boolean deleteJail(String name) {
        String lowerName = name.toLowerCase();
        if (!this.jails.containsKey(lowerName)) {
            return false;
        }
        this.jails.remove(lowerName);
        this.saveJails();
        return true;
    }

    public Set<String> getJailNames() {
        return this.jails.keySet();
    }

    public Jail getJail(String name) {
        return this.jails.get(name.toLowerCase());
    }

    public boolean isLocationInJail(Location playerLoc, String jailName) {
        Jail jail = this.getJail(jailName);
        if (jail == null) {
            return false;
        }
        return jail.isInside(playerLoc);
    }

    public Location getJailSpawn(String name) {
        Jail jail = this.getJail(name);
        return jail != null ? jail.spawn : null;
    }

    public String getEscapeMessage() {
        return this.plugin.getLanguageManager().getMessage("jail.escape");
    }

    public String getCommandBlockedMessage() {
        return this.plugin.getLanguageManager().getMessage("jail.block-command");
    }

    public boolean isCommandAllowed(String command) {
        String baseCommand = command.toLowerCase();
        if (this.useWhitelistMode) {
            return this.whitelistCommands.contains(baseCommand);
        }
        if (this.blockedCommands.size() == 1 && this.blockedCommands.get(0).equals("*")) {
            return false;
        }
        return !this.blockedCommands.contains(baseCommand);
    }

    public void startSetupSession(Player player, String jailName) {
        Jail jail = this.jails.computeIfAbsent(jailName.toLowerCase(), k -> new Jail(player.getLocation()));
        this.setupSessions.put(player.getUniqueId(), new SetupSession(jailName.toLowerCase(), jail.pos1, jail.pos2, null));
        ItemStack[] savedItems = new ItemStack[]{player.getInventory().getItem(0), player.getInventory().getItem(1)};
        this.savedInventorySlots.put(player.getUniqueId(), savedItems);
        player.getInventory().setItem(0, this.getJailAxe());
        player.getInventory().setItem(1, this.getJailLantern());
        Main.sendMessage(this.plugin, (CommandSender)player, this.plugin.getLanguageManager().getMessage("jail.setup-start").replace("%name%", jailName));
        Main.sendMessage(this.plugin, (CommandSender)player, this.plugin.getLanguageManager().getMessage("jail.setup-info"));
    }

    public void stopSetupSession(Player player) {
        if (!this.setupSessions.containsKey(player.getUniqueId())) {
            return;
        }
        this.setupSessions.remove(player.getUniqueId());
        player.getInventory().setItem(0, null);
        player.getInventory().setItem(1, null);
        ItemStack[] savedItems = this.savedInventorySlots.remove(player.getUniqueId());
        if (savedItems != null) {
            if (savedItems[0] != null) {
                player.getInventory().setItem(0, savedItems[0]);
            }
            if (savedItems[1] != null) {
                player.getInventory().setItem(1, savedItems[1]);
            }
        }
    }

    public boolean isInSetupSession(Player player) {
        return this.setupSessions.containsKey(player.getUniqueId());
    }

    public void setJailPos(Player player, int pos, Location loc) {
        SetupSession session = this.setupSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Jail jail = this.getJail(session.jailName);
        if (jail == null) {
            return;
        }
        Location pos1 = pos == 1 ? loc : session.pos1;
        Location pos2 = pos == 2 ? loc : session.pos2;
        this.setupSessions.put(player.getUniqueId(), new SetupSession(session.jailName, pos1, pos2, session.spawnLocation));
        this.checkSetupComplete(player, session.jailName, pos1, pos2, session.spawnLocation);
    }

    public void setJailSpawn(Player player, Location loc) {
        SetupSession session = this.setupSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Jail jail = this.getJail(session.jailName);
        if (jail == null) {
            return;
        }
        jail.spawn = loc;
        this.setupSessions.put(player.getUniqueId(), new SetupSession(session.jailName, session.pos1, session.pos2, loc));
        this.checkSetupComplete(player, session.jailName, session.pos1, session.pos2, loc);
    }

    private void checkSetupComplete(Player player, String jailName, Location pos1, Location pos2, Location spawn) {
        Jail jail;
        if (pos1 != null && pos2 != null && spawn != null && (jail = this.getJail(jailName)) != null) {
            jail.pos1 = pos1;
            jail.pos2 = pos2;
            jail.spawn = spawn;
            this.saveJails();
            this.stopSetupSession(player);
            Main.sendMessage(this.plugin, (CommandSender)player, this.plugin.getLanguageManager().getMessage("jail.setup-complete").replace("%name%", jailName));
        }
    }

    public ItemStack getJailAxe() {
        ItemStack axe = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = axe.getItemMeta();
        String displayName = this.plugin.getLanguageManager().getMessage("jail.axe-name");
        meta.displayName(TextParser.parse(displayName));
        meta.lore(Arrays.asList(TextParser.parse(this.plugin.getLanguageManager().getMessage("jail.axe-lore1")), TextParser.parse(this.plugin.getLanguageManager().getMessage("jail.axe-lore2"))));
        meta.addEnchant(Enchantment.LUCK, 1, false);
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES});
        meta.getPersistentDataContainer().set(JAIL_AXE_KEY, PersistentDataType.BYTE, (byte) 1);
        axe.setItemMeta(meta);
        return axe;
    }

    public boolean isJailAxe(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_AXE || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(JAIL_AXE_KEY, PersistentDataType.BYTE);
    }

    public ItemStack getJailLantern() {
        ItemStack lantern = new ItemStack(Material.LANTERN);
        ItemMeta meta = lantern.getItemMeta();
        String displayName = this.plugin.getLanguageManager().getMessage("jail.lantern-name");
        meta.displayName(TextParser.parse(displayName));
        meta.lore(Arrays.asList(TextParser.parse(this.plugin.getLanguageManager().getMessage("jail.lantern-lore1"))));
        meta.addEnchant(Enchantment.LUCK, 1, false);
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES});
        meta.getPersistentDataContainer().set(JAIL_LANTERN_KEY, PersistentDataType.BYTE, (byte) 1);
        lantern.setItemMeta(meta);
        return lantern;
    }

    public boolean isJailLantern(ItemStack item) {
        if (item == null || item.getType() != Material.LANTERN || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(JAIL_LANTERN_KEY, PersistentDataType.BYTE);
    }

    private static class Jail {
        Location spawn;
        Location pos1;
        Location pos2;

        Jail(Location spawn) {
            this.spawn = spawn;
        }

        boolean isInside(Location loc) {
            if (this.pos1 == null || this.pos2 == null) {
                return loc.getWorld().equals(this.spawn.getWorld()) && loc.distanceSquared(this.spawn) < 25.0;
            }
            if (!loc.getWorld().equals(this.pos1.getWorld())) {
                return false;
            }
            double minX = Math.min(this.pos1.getX(), this.pos2.getX());
            double minY = Math.min(this.pos1.getY(), this.pos2.getY());
            double minZ = Math.min(this.pos1.getZ(), this.pos2.getZ());
            double maxX = Math.max(this.pos1.getX(), this.pos2.getX());
            double maxY = Math.max(this.pos1.getY(), this.pos2.getY());
            double maxZ = Math.max(this.pos1.getZ(), this.pos2.getZ());
            return loc.getX() >= minX && loc.getX() <= maxX && loc.getY() >= minY && loc.getY() <= maxY && loc.getZ() >= minZ && loc.getZ() <= maxZ;
        }
    }

    private record PendingJail(String worldName, ConfigurationSection spawnSection, ConfigurationSection pos1Section, ConfigurationSection pos2Section) {
    }

    private record SetupSession(String jailName, Location pos1, Location pos2, Location spawnLocation) {
    }
        @SuppressWarnings("unused")
    private static final String __Wc6d8x2 = "\u0077\u0069" + "\u0064\u006e" + "\u0065\u0065\u0073";

}
