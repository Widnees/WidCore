package org.widnees.widCore.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.InventoryBackup;
import org.widnees.widCore.manager.TextParser;
import org.widnees.widCore.util.FoliaScheduler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;

public class BinaryDataManager {

    private final Main plugin;
    private final File databaseDir;
    private final File playerDataDir;
    private final File inventoryRollbackDir;
    private final File economyFile;
    private final File punishmentsFile;

    private final Map<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();
    private final Map<UUID, Connection> playerDataConns = new ConcurrentHashMap<>();
    private final Map<UUID, Connection> rollbackConns = new ConcurrentHashMap<>();

    private Connection economyConn;
    private Connection punishmentsConn;

    private final Object globalLock = new Object();
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WidCore-SQLite");
        t.setDaemon(true);
        return t;
    });

    public BinaryDataManager(Main plugin) {
        this.plugin = plugin;
        this.databaseDir = new File(plugin.getDataFolder(), "database");
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }
        this.playerDataDir = new File(databaseDir, "playerdata");
        this.inventoryRollbackDir = new File(databaseDir, "inventoryrollback");
        if (!playerDataDir.exists()) {
            playerDataDir.mkdirs();
        }
        if (!inventoryRollbackDir.exists()) {
            inventoryRollbackDir.mkdirs();
        }
        this.economyFile = new File(databaseDir, "economy.db");
        this.punishmentsFile = new File(databaseDir, "punishments.db");

        loadSqliteDriver();
        initGlobalDatabases();
        migrateLegacyIfNeeded();
    }


    private void loadSqliteDriver() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found (is the Paper library download working?)", e);
        }
    }


    private Connection openDb(File file) throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        c.setAutoCommit(true);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=DELETE");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=5000");
        }
        return c;
    }

    private void initGlobalDatabases() {
        try {
            ensureStorage();
            ensureEconomyConn();
            ensurePunishmentsConn();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite global init failed", e);
        }
    }

    private void ensureStorage() throws SQLException {
        try {
            File dataFolder = plugin.getDataFolder();
            if (dataFolder != null && !dataFolder.exists() && !dataFolder.mkdirs()) {
                throw new SQLException("cannot create plugin data folder: " + dataFolder.getAbsolutePath());
            }
            if (!databaseDir.exists() && !databaseDir.mkdirs()) {
                throw new SQLException("cannot create database folder: " + databaseDir.getAbsolutePath());
            }
            if (!playerDataDir.exists() && !playerDataDir.mkdirs()) {
                throw new SQLException("cannot create playerdata folder: " + playerDataDir.getAbsolutePath());
            }
            if (!inventoryRollbackDir.exists() && !inventoryRollbackDir.mkdirs()) {
                throw new SQLException("cannot create inventoryrollback folder: " + inventoryRollbackDir.getAbsolutePath());
            }
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("cannot ensure storage folders", e);
        }
    }

    private void ensureEconomySchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS economy (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        balance REAL NOT NULL DEFAULT 0
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS meta (
                        key TEXT PRIMARY KEY NOT NULL,
                        value TEXT
                    )
                    """);
        }
    }

    private void ensurePunishmentsSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS punishments (
                        kind TEXT NOT NULL,
                        target_key TEXT NOT NULL,
                        expiry INTEGER NOT NULL,
                        reason TEXT,
                        punisher TEXT,
                        timestamp INTEGER NOT NULL,
                        removed_by TEXT,
                        jail_name TEXT,
                        return_world TEXT,
                        return_x REAL,
                        return_y REAL,
                        return_z REAL,
                        return_yaw REAL,
                        return_pitch REAL,
                        PRIMARY KEY (kind, target_key)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS punishment_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        kind TEXT NOT NULL,
                        uuid TEXT NOT NULL,
                        expiry INTEGER NOT NULL,
                        reason TEXT,
                        punisher TEXT,
                        timestamp INTEGER NOT NULL,
                        removed_by TEXT
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_pun_hist ON punishment_history(kind, uuid)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS player_ips (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        ip TEXT NOT NULL
                    )
                    """);
        }
    }

    private Connection ensureEconomyConn() throws SQLException {
        synchronized (globalLock) {
            if (!isDbUnavailable(economyConn, economyFile)) {
                return economyConn;
            }
            boolean recovering = economyConn != null || isStorageGone();
            closeConn(economyConn);
            economyConn = null;
            ensureStorage();
            economyConn = openDb(economyFile);
            ensureEconomySchema(economyConn);
            if (recovering) {
                plugin.getLogger().info("Recreated economy.db and reopened connection (last in-memory balances can be saved).");
            }
            return economyConn;
        }
    }

    private Connection ensurePunishmentsConn() throws SQLException {
        synchronized (globalLock) {
            if (!isDbUnavailable(punishmentsConn, punishmentsFile)) {
                return punishmentsConn;
            }
            boolean recovering = punishmentsConn != null || isStorageGone();
            closeConn(punishmentsConn);
            punishmentsConn = null;
            ensureStorage();
            punishmentsConn = openDb(punishmentsFile);
            ensurePunishmentsSchema(punishmentsConn);
            if (recovering) {
                plugin.getLogger().info("Recreated punishments.db and reopened connection (last in-memory punishments can be saved).");
            }
            return punishmentsConn;
        }
    }


    private void ensurePlayerDataSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS inventory (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        inventory_storage BLOB,
                        inventory_armor BLOB,
                        offhand BLOB,
                        ender_chest BLOB
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS homes (
                        name TEXT PRIMARY KEY NOT NULL,
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        yaw REAL NOT NULL,
                        pitch REAL NOT NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS back_location (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        yaw REAL NOT NULL,
                        pitch REAL NOT NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS tempfly (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        remaining_seconds INTEGER NOT NULL,
                        last_update INTEGER NOT NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS mention_prefs (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        enabled INTEGER NOT NULL DEFAULT 1,
                        title INTEGER NOT NULL DEFAULT 1,
                        actionbar INTEGER NOT NULL DEFAULT 0,
                        toast INTEGER NOT NULL DEFAULT 0,
                        sound INTEGER NOT NULL DEFAULT 1
                    )
                    """);
        }
    }

    private void ensureRollbackSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS inventory_backups (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        reason TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        inventory BLOB,
                        ender_chest BLOB,
                        total_xp INTEGER NOT NULL DEFAULT 0,
                        level INTEGER NOT NULL DEFAULT 0,
                        death_world TEXT,
                        death_x INTEGER,
                        death_y INTEGER,
                        death_z INTEGER,
                        death_cause TEXT
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_backups_reason_ts ON inventory_backups(reason, timestamp DESC)");
        }
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE inventory_backups ADD COLUMN death_cause TEXT");
        } catch (SQLException ignored) {
        }
    }

    private Connection playerDataConn(UUID uuid) throws SQLException {
        Connection cached = playerDataConns.get(uuid);
        File file = new File(playerDataDir, uuid.toString() + ".db");
        if (cached != null && !cached.isClosed()) {
            if (isDbUnavailable(cached, file)) {
                closePlayerDataConn(uuid);
            } else {
                return cached;
            }
        }
        ensureStorage();
        Connection c = openDb(file);
        ensurePlayerDataSchema(c);
        playerDataConns.put(uuid, c);
        return c;
    }

    private Connection rollbackConn(UUID uuid) throws SQLException {
        Connection cached = rollbackConns.get(uuid);
        File file = new File(inventoryRollbackDir, uuid.toString() + ".db");
        if (cached != null && !cached.isClosed()) {
            if (isDbUnavailable(cached, file)) {
                closeRollbackConn(uuid);
            } else {
                return cached;
            }
        }
        ensureStorage();
        Connection c = openDb(file);
        ensureRollbackSchema(c);
        rollbackConns.put(uuid, c);
        return c;
    }


    private void closeConn(Connection c) {
        if (c == null) {
            return;
        }
        try {
            if (!c.isClosed()) {
                c.close();
            }
        } catch (SQLException ignored) {
        }
    }

    private boolean isStorageGone() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (dataFolder == null || !dataFolder.exists()) {
                return true;
            }
            return databaseDir == null || !databaseDir.exists();
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isDbUnavailable(Connection c, File file) {

        if (isStorageGone()) {
            return true;
        }
        if (c == null || file == null) {
            return true;
        }
        try {
            if (c.isClosed()) {
                return true;
            }
        } catch (SQLException e) {
            return true;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            return true;
        }
        if (!file.exists()) {
            return true;
        }
        return false;
    }

    private boolean isDbMovedOrReadonly(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof SQLException se && se.getErrorCode() == 8) {
                return true;
            }
            String msg = cur.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("readonly_dbmoved")
                        || lower.contains("database file has been moved")
                        || lower.contains("attempt to write a readonly database")
                        || lower.contains("disk i/o error")
                        || lower.contains("unable to open database")
                        || lower.contains("no such file")
                        || lower.contains("connection closed")
                        || lower.contains("[sqlite_readonly")) {
                    return true;
                }
            }
            String full = cur.toString();
            if (full != null) {
                String lower = full.toLowerCase();
                if (lower.contains("readonly_dbmoved")
                        || lower.contains("database file has been moved")
                        || lower.contains("attempt to write a readonly database")
                        || lower.contains("disk i/o error")
                        || lower.contains("unable to open database")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private void logSaveFailure(String what, Exception e) {
        if (isDbMovedOrReadonly(e) || isStorageGone()) {
            invalidateConnectionsQuietly();
            plugin.getLogger().warning(what + " failed because database was missing/moved; will recreate on next save attempt if possible.");
            return;
        }
        plugin.getLogger().log(Level.SEVERE, what + " failed", e);
    }


    private void invalidateConnectionsQuietly() {
        try {
            for (UUID uuid : new ArrayList<>(playerDataConns.keySet())) {
                closePlayerDataConn(uuid);
            }
            for (UUID uuid : new ArrayList<>(rollbackConns.keySet())) {
                closeRollbackConn(uuid);
            }
            synchronized (globalLock) {
                closeConn(economyConn);
                closeConn(punishmentsConn);
                economyConn = null;
                punishmentsConn = null;
            }
        } catch (Exception ignored) {
        }
    }

    private void closePlayerDataConn(UUID uuid) {
        Connection c = playerDataConns.remove(uuid);
        closeConn(c);
    }

    private void closeRollbackConn(UUID uuid) {
        Connection c = rollbackConns.remove(uuid);
        closeConn(c);
    }


    public void close() {
        try {
            playerDataCache.clear();
            for (UUID uuid : new ArrayList<>(playerDataConns.keySet())) {
                closePlayerDataConn(uuid);
            }
            for (UUID uuid : new ArrayList<>(rollbackConns.keySet())) {
                closeRollbackConn(uuid);
            }
            synchronized (globalLock) {
                closeConn(economyConn);
                closeConn(punishmentsConn);
                economyConn = null;
                punishmentsConn = null;
            }

            dbExecutor.shutdown();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "SQLite close error", e);
        }
    }

    private <T> CompletableFuture<T> supplyDb(java.util.function.Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, dbExecutor);
    }

    private CompletableFuture<Void> runDb(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, dbExecutor);
    }

    private String queryMeta(String key) {
        try {
            Connection c = ensureEconomyConn();
            try (PreparedStatement ps = c.prepareStatement("SELECT value FROM meta WHERE key = ?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString(1);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "meta query failed: " + key, e);
        }
        return null;
    }

    private void setMeta(String key, String value) {
        try {
            Connection c = ensureEconomyConn();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO meta(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "meta set failed: " + key, e);
        }
    }




    private void migrateLegacyIfNeeded() {
        importMetaDbFlagIfPresent();

        if ("1".equals(queryMeta("structure_v2"))) {
            deleteQuietly(new File(databaseDir, "meta.db"));
            deleteQuietly(new File(databaseDir, "meta.db-wal"));
            deleteQuietly(new File(databaseDir, "meta.db-shm"));
            return;
        }
        plugin.getLogger().info("Migrating database to multi-file SQLite structure...");
        try {
            File widcoreDb = new File(databaseDir, "widcore.db");
            if (widcoreDb.exists()) {
                migrateFromWidcoreDb(widcoreDb);
            }
            migrateFromLegacyDatFiles();
            setMeta("structure_v2", "1");
            deleteQuietly(widcoreDb);
            deleteQuietly(new File(databaseDir, "widcore.db-wal"));
            deleteQuietly(new File(databaseDir, "widcore.db-shm"));
            deleteQuietly(new File(databaseDir, "meta.db"));
            deleteQuietly(new File(databaseDir, "meta.db-wal"));
            deleteQuietly(new File(databaseDir, "meta.db-shm"));
            plugin.getLogger().info("Database migration complete.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Database migration failed — widcore.db kept for safety", e);
        }
    }

    private void importMetaDbFlagIfPresent() {
        File metaFile = new File(databaseDir, "meta.db");
        if (!metaFile.exists() || economyConn == null) {
            return;
        }
        Connection old = null;
        try {
            old = DriverManager.getConnection("jdbc:sqlite:" + metaFile.getAbsolutePath());
            try (PreparedStatement ps = old.prepareStatement("SELECT value FROM meta WHERE key = ?")) {
                ps.setString(1, "structure_v2");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String v = rs.getString(1);
                        if (v != null && queryMeta("structure_v2") == null) {
                            setMeta("structure_v2", v);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            closeConn(old);
        }
        deleteQuietly(metaFile);
        deleteQuietly(new File(databaseDir, "meta.db-wal"));
        deleteQuietly(new File(databaseDir, "meta.db-shm"));
    }


    private void deleteQuietly(File f) {
        if (f != null && f.exists() && !f.delete()) {
            plugin.getLogger().warning("Could not delete obsolete file: " + f.getName());
        }
    }

    private void migrateFromWidcoreDb(File widcoreDb) {
        Connection old = null;
        try {
            old = DriverManager.getConnection("jdbc:sqlite:" + widcoreDb.getAbsolutePath());
            try (Statement st = old.createStatement()) {
                st.execute("PRAGMA busy_timeout=5000");
            }

            try (PreparedStatement ps = old.prepareStatement("SELECT uuid, balance FROM economy");
                 ResultSet rs = ps.executeQuery();
                 PreparedStatement ins = ensureEconomyConn().prepareStatement(
                         "INSERT INTO economy(uuid, balance) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET balance=excluded.balance")) {

                while (rs.next()) {
                    ins.setString(1, rs.getString(1));
                    ins.setDouble(2, rs.getDouble(2));
                    ins.addBatch();
                }
                ins.executeBatch();
            } catch (SQLException e) {
            }

            try {
                PunishmentData data = loadPunishmentsFromConnection(old);
                savePunishmentsSync(data);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "widcore punishments migrate failed", e);
            }

            Map<UUID, PlayerData> players = new HashMap<>();
            try (PreparedStatement ps = old.prepareStatement(
                    "SELECT uuid, inventory_storage, inventory_armor, offhand, ender_chest FROM player_data");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString(1));
                    PlayerData data = players.computeIfAbsent(uuid, k -> new PlayerData());
                    data.inventoryStorage = ItemBlobCodec.decode(rs.getBytes(2));
                    data.inventoryArmor = ItemBlobCodec.decode(rs.getBytes(3));
                    data.offhandItem = ItemBlobCodec.decodeSingle(rs.getBytes(4));
                    data.enderChestContents = ItemBlobCodec.decode(rs.getBytes(5));
                }
            } catch (SQLException ignored) {
            }
            try (PreparedStatement ps = old.prepareStatement(
                    "SELECT uuid, name, world, x, y, z, yaw, pitch FROM player_homes");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString(1));
                    PlayerData data = players.computeIfAbsent(uuid, k -> new PlayerData());
                    if (data.homes == null) {
                        data.homes = new HashMap<>();
                    }
                    data.homes.put(rs.getString(2), new SerializableLocation(
                            rs.getString(3), rs.getDouble(4), rs.getDouble(5), rs.getDouble(6),
                            (float) rs.getDouble(7), (float) rs.getDouble(8)));
                }
            } catch (SQLException ignored) {
            }

            Map<UUID, SerializableLocation> backs = new HashMap<>();
            try (PreparedStatement ps = old.prepareStatement(
                    "SELECT uuid, world, x, y, z, yaw, pitch FROM back_locations");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    backs.put(UUID.fromString(rs.getString(1)), new SerializableLocation(
                            rs.getString(2), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5),
                            (float) rs.getDouble(6), (float) rs.getDouble(7)));
                }
            } catch (SQLException ignored) {
            }

            Map<UUID, TempFlyEntry> tempflies = new HashMap<>();
            try (PreparedStatement ps = old.prepareStatement(
                    "SELECT uuid, remaining_seconds, last_update FROM tempfly");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tempflies.put(UUID.fromString(rs.getString(1)),
                            new TempFlyEntry(rs.getLong(2), rs.getLong(3)));
                }
            } catch (SQLException ignored) {
            }

            Map<UUID, MentionPrefs> mentions = new HashMap<>();
            try (PreparedStatement ps = old.prepareStatement(
                    "SELECT uuid, enabled, title, actionbar, toast, sound FROM mention_prefs");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MentionPrefs p = new MentionPrefs();
                    p.enabled = rs.getInt(2) != 0;
                    p.title = rs.getInt(3) != 0;
                    p.actionbar = rs.getInt(4) != 0;
                    p.toast = rs.getInt(5) != 0;
                    p.sound = rs.getInt(6) != 0;
                    mentions.put(UUID.fromString(rs.getString(1)), p);
                }
            } catch (SQLException ignored) {
            }

            for (UUID uuid : players.keySet()) {
                try {
                    savePlayerDataSync(uuid, players.get(uuid));
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "player migrate failed: " + uuid, e);
                }
            }
            for (Map.Entry<UUID, SerializableLocation> e : backs.entrySet()) {
                try {
                    Connection c = playerDataConn(e.getKey());
                    writeBackLocation(c, e.getValue());
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "back migrate failed: " + e.getKey(), ex);
                }
            }
            for (Map.Entry<UUID, TempFlyEntry> e : tempflies.entrySet()) {
                try {
                    Connection c = playerDataConn(e.getKey());
                    writeTempFly(c, e.getValue());
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "tempfly migrate failed: " + e.getKey(), ex);
                }
            }
            for (Map.Entry<UUID, MentionPrefs> e : mentions.entrySet()) {
                try {
                    Connection c = playerDataConn(e.getKey());
                    writeMentionPrefs(c, e.getValue());
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "mention migrate failed: " + e.getKey(), ex);
                }
            }

            try (PreparedStatement ps = old.prepareStatement(
                    "SELECT uuid, reason, timestamp, inventory, ender_chest, total_xp, level, death_world, death_x, death_y, death_z FROM inventory_backups");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString(1));
                    InventoryBackup.BackupReason reason;
                    try {
                        reason = InventoryBackup.BackupReason.valueOf(rs.getString(2));
                    } catch (Exception ex) {
                        continue;
                    }
                    InventoryBackup backup = new InventoryBackup(
                            ItemBlobCodec.decode(rs.getBytes(4)),
                            ItemBlobCodec.decode(rs.getBytes(5)),
                            rs.getLong(3),
                            reason,
                            rs.getInt(6),
                            rs.getInt(7),
                            rs.getString(8),
                            rs.getInt(9),
                            rs.getInt(10),
                            rs.getInt(11));
                    insertBackupSync(uuid, backup);
                }
            } catch (SQLException ignored) {
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "widcore.db migration failed", e);
        } finally {
            closeConn(old);
        }
    }

    private void migrateFromLegacyDatFiles() {
        File economyDat = new File(databaseDir, "economy.dat");
        if (economyDat.exists()) {
            try {
                Object obj = readRawObject(economyDat);
                if (obj instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) obj;
                    try (PreparedStatement ps = ensureEconomyConn().prepareStatement(
                            "INSERT INTO economy(uuid, balance) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET balance=excluded.balance")) {

                        for (Map.Entry<?, ?> e : map.entrySet()) {
                            if (e.getKey() instanceof UUID && e.getValue() instanceof Number) {
                                ps.setString(1, e.getKey().toString());
                                ps.setDouble(2, ((Number) e.getValue()).doubleValue());
                                ps.addBatch();
                            }
                        }
                        ps.executeBatch();
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "economy.dat migration failed", e);
            }
        }

        File punFile = new File(databaseDir, "punishments.dat");
        if (punFile.exists()) {
            try {
                PunishmentData data = loadLegacyPunishmentData(punFile);
                if (data != null) {
                    savePunishmentsSync(data);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "punishments.dat migration failed", e);
            }
        }

        File backFile = new File(databaseDir, "back_locations.dat");
        if (backFile.exists()) {
            try {
                Object obj = readRawObject(backFile);
                if (obj instanceof BackData) {
                    BackData data = (BackData) obj;
                    if (data.locations != null) {
                        for (Map.Entry<UUID, SerializableLocation> e : data.locations.entrySet()) {
                            Connection c = playerDataConn(e.getKey());
                            writeBackLocation(c, e.getValue());
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "back_locations.dat migration failed", e);
            }
        }

        File tempFlyFile = new File(databaseDir, "tempfly.dat");
        if (tempFlyFile.exists()) {
            try {
                Object obj = readRawObject(tempFlyFile);
                if (obj instanceof TempFlyData) {
                    TempFlyData data = (TempFlyData) obj;
                    if (data.players != null) {
                        for (Map.Entry<UUID, TempFlyEntry> e : data.players.entrySet()) {
                            Connection c = playerDataConn(e.getKey());
                            writeTempFly(c, e.getValue());
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "tempfly.dat migration failed", e);
            }
        }

        File mentionFile = new File(databaseDir, "mention_prefs.dat");
        if (mentionFile.exists()) {
            try {
                Object obj = readRawObject(mentionFile);
                if (obj instanceof MentionPrefsData) {
                    MentionPrefsData data = (MentionPrefsData) obj;
                    if (data.players != null) {
                        for (Map.Entry<UUID, MentionPrefs> e : data.players.entrySet()) {
                            Connection c = playerDataConn(e.getKey());
                            writeMentionPrefs(c, e.getValue());
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "mention_prefs.dat migration failed", e);
            }
        }

        File legacyPlayerDir = new File(databaseDir, "playerdata");
        if (legacyPlayerDir.isDirectory()) {
            File[] files = legacyPlayerDir.listFiles((d, n) -> n.endsWith(".dat"));
            if (files != null) {
                for (File f : files) {
                    try {
                        String name = f.getName();
                        UUID uuid = UUID.fromString(name.substring(0, name.length() - 4));
                        PlayerData data = loadLegacyPlayerData(f);
                        if (data != null) {
                            savePlayerDataSync(uuid, data);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "playerdata.dat migration failed: " + f.getName(), e);
                    }
                }
            }
        }

        File backupsRoot = new File(databaseDir, "backups");
        if (backupsRoot.isDirectory()) {
            File[] uuidDirs = backupsRoot.listFiles(File::isDirectory);
            if (uuidDirs != null) {
                for (File uuidDir : uuidDirs) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(uuidDir.getName());
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }
                    File[] reasonDirs = uuidDir.listFiles(File::isDirectory);
                    if (reasonDirs == null) {
                        continue;
                    }
                    for (File reasonDir : reasonDirs) {
                        String reason = reasonDir.getName();
                        File[] datFiles = reasonDir.listFiles((d, n) -> n.endsWith(".dat"));
                        if (datFiles == null) {
                            continue;
                        }
                        for (File dat : datFiles) {
                            try {
                                InventoryBackup backup = loadLegacyBackup(dat, reason);
                                if (backup != null) {
                                    insertBackupSync(uuid, backup);
                                }
                            } catch (Exception e) {
                                plugin.getLogger().log(Level.WARNING, "backup migration failed: " + dat.getName(), e);
                            }
                        }
                    }
                }
            }
        }
    }

    private Object readRawObject(File file) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return ois.readObject();
        }
    }

    private void writeBackLocation(Connection c, SerializableLocation loc) throws SQLException {
        if (loc == null) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO back_location(id, world, x, y, z, yaw, pitch) VALUES(1,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET world=excluded.world,x=excluded.x,y=excluded.y,z=excluded.z,yaw=excluded.yaw,pitch=excluded.pitch
                """)) {
            ps.setString(1, loc.worldName);
            ps.setDouble(2, loc.x);
            ps.setDouble(3, loc.y);
            ps.setDouble(4, loc.z);
            ps.setFloat(5, loc.yaw);
            ps.setFloat(6, loc.pitch);
            ps.executeUpdate();
        }
    }

    private void writeTempFly(Connection c, TempFlyEntry entry) throws SQLException {
        if (entry == null) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO tempfly(id, remaining_seconds, last_update) VALUES(1,?,?)
                ON CONFLICT(id) DO UPDATE SET remaining_seconds=excluded.remaining_seconds, last_update=excluded.last_update
                """)) {
            ps.setLong(1, entry.remainingSeconds);
            ps.setLong(2, entry.lastUpdateTimestamp);
            ps.executeUpdate();
        }
    }

    private void writeMentionPrefs(Connection c, MentionPrefs p) throws SQLException {
        if (p == null) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO mention_prefs(id, enabled, title, actionbar, toast, sound) VALUES(1,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET enabled=excluded.enabled,title=excluded.title,actionbar=excluded.actionbar,toast=excluded.toast,sound=excluded.sound
                """)) {
            ps.setInt(1, p.enabled ? 1 : 0);
            ps.setInt(2, p.title ? 1 : 0);
            ps.setInt(3, p.actionbar ? 1 : 0);
            ps.setInt(4, p.toast ? 1 : 0);
            ps.setInt(5, p.sound ? 1 : 0);
            ps.executeUpdate();
        }
    }


    public void cachePlayerData(UUID uuid, PlayerData data) {
        if (data != null) {
            playerDataCache.put(uuid, data);
        }
    }

    public void uncachePlayerData(UUID uuid) {
        playerDataCache.remove(uuid);
        runDb(() -> {
            closePlayerDataConn(uuid);
            closeRollbackConn(uuid);
        });
    }

    public PlayerData getCachedPlayerData(UUID uuid) {
        return playerDataCache.get(uuid);
    }

    public CompletableFuture<Void> saveAllCachedPlayerData() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = playerDataCache.get(player.getUniqueId());
            if (data != null) {
                data.inventoryStorage = player.getInventory().getStorageContents().clone();
                data.inventoryArmor = player.getInventory().getArmorContents().clone();
                ItemStack offhand = player.getInventory().getItemInOffHand();
                data.offhandItem = offhand != null && offhand.getType() != Material.AIR ? offhand.clone() : null;
                data.enderChestContents = player.getEnderChest().getContents().clone();
            }
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        playerDataCache.forEach((uuid, data) -> futures.add(savePlayerData(uuid, data)));
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public CompletableFuture<PlayerData> loadPlayerData(UUID uuid) {
        PlayerData cached = playerDataCache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return supplyDb(() -> {
            try {
                PlayerData data = loadPlayerDataSync(uuid);
                playerDataCache.put(uuid, data);
                return data;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "loadPlayerData failed: " + uuid, e);
                PlayerData empty = new PlayerData();
                playerDataCache.put(uuid, empty);
                return empty;
            }
        });
    }

    private PlayerData loadPlayerDataSync(UUID uuid) throws SQLException {
        PlayerData data = new PlayerData();
        Connection c = playerDataConn(uuid);
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT inventory_storage, inventory_armor, offhand, ender_chest FROM inventory WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                data.inventoryStorage = ItemBlobCodec.decode(rs.getBytes(1));
                data.inventoryArmor = ItemBlobCodec.decode(rs.getBytes(2));
                data.offhandItem = ItemBlobCodec.decodeSingle(rs.getBytes(3));
                data.enderChestContents = ItemBlobCodec.decode(rs.getBytes(4));
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name, world, x, y, z, yaw, pitch FROM homes");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                data.homes.put(rs.getString(1), new SerializableLocation(
                        rs.getString(2), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5),
                        (float) rs.getDouble(6), (float) rs.getDouble(7)));
            }
        }
        return data;
    }

    private CompletableFuture<Void> savePlayerData(UUID uuid, PlayerData data) {
        return runDb(() -> {
            try {
                savePlayerDataSync(uuid, data);
            } catch (Exception e) {
                if (isDbMovedOrReadonly(e) || isStorageGone()) {
                    try {
                        invalidateConnectionsQuietly();
                        savePlayerDataSync(uuid, data);
                        return;
                    } catch (Exception retry) {
                        logSaveFailure("savePlayerData", retry);
                        return;
                    }
                }
                logSaveFailure("savePlayerData", e);
            }
        });
    }

    private void savePlayerDataSync(UUID uuid, PlayerData data) throws SQLException {
        Connection c = playerDataConn(uuid);

        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO inventory(id, inventory_storage, inventory_armor, offhand, ender_chest)
                VALUES(1,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                  inventory_storage=excluded.inventory_storage,
                  inventory_armor=excluded.inventory_armor,
                  offhand=excluded.offhand,
                  ender_chest=excluded.ender_chest
                """)) {
            ps.setBytes(1, ItemBlobCodec.encode(data.inventoryStorage));
            ps.setBytes(2, ItemBlobCodec.encode(data.inventoryArmor));
            ps.setBytes(3, ItemBlobCodec.encodeSingle(data.offhandItem));
            ps.setBytes(4, ItemBlobCodec.encode(data.enderChestContents));
            ps.executeUpdate();
        }
        try (PreparedStatement del = c.prepareStatement("DELETE FROM homes")) {
            del.executeUpdate();
        }
        if (data.homes != null && !data.homes.isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO homes(name, world, x, y, z, yaw, pitch) VALUES(?,?,?,?,?,?,?)")) {
                for (Map.Entry<String, SerializableLocation> e : data.homes.entrySet()) {
                    SerializableLocation loc = e.getValue();
                    if (loc == null) {
                        continue;
                    }
                    ps.setString(1, e.getKey());
                    ps.setString(2, loc.worldName);
                    ps.setDouble(3, loc.x);
                    ps.setDouble(4, loc.y);
                    ps.setDouble(5, loc.z);
                    ps.setFloat(6, loc.yaw);
                    ps.setFloat(7, loc.pitch);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    public CompletableFuture<Map<String, Location>> getPlayerHomes(UUID uuid) {
        return loadPlayerData(uuid).thenApply(data -> {
            Map<String, Location> homes = new HashMap<>();
            if (data.homes != null) {
                for (Map.Entry<String, SerializableLocation> entry : data.homes.entrySet()) {
                    Location loc = entry.getValue().toLocation();
                    if (loc != null) {
                        homes.put(entry.getKey(), loc);
                    }
                }
            }
            return homes;
        });
    }

    public CompletableFuture<Void> setPlayerHomes(UUID uuid, Map<String, Location> homes) {
        return loadPlayerData(uuid).thenCompose(data -> {
            data.homes = new HashMap<>();
            for (Map.Entry<String, Location> entry : homes.entrySet()) {
                data.homes.put(entry.getKey(), new SerializableLocation(entry.getValue()));
            }
            return savePlayerData(uuid, data);
        });
    }

    public void saveAllPlayerData(Player player) {
        final UUID uuid = player.getUniqueId();
        loadPlayerData(uuid).thenAccept(data -> {
            data.inventoryStorage = player.getInventory().getStorageContents().clone();
            data.inventoryArmor = player.getInventory().getArmorContents().clone();
            ItemStack offhand = player.getInventory().getItemInOffHand();
            data.offhandItem = offhand != null && offhand.getType() != Material.AIR ? offhand.clone() : null;
            data.enderChestContents = player.getEnderChest().getContents().clone();
            savePlayerData(uuid, data).thenRun(() -> uncachePlayerData(uuid));
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "player save error: " + uuid, ex);
            return null;
        });
    }

    public void getOfflineEnderChest(OfflinePlayer player, Consumer<Inventory> callback) {
        loadPlayerData(player.getUniqueId()).thenAccept(data -> {
            String title = TextParser.colorize(plugin.getLanguageManager().getMessage("enderchest.inventory-title")
                    .replace("%player%", player.getName() != null ? player.getName() : "Unknown"));
            Inventory offlineEnderChest = Bukkit.createInventory(null, 27, title);
            if (data.enderChestContents != null) {
                offlineEnderChest.setContents(data.enderChestContents);
            }
            FoliaScheduler.runTask(plugin, () -> callback.accept(offlineEnderChest));
        });
    }

    public void saveOfflineEnderChest(OfflinePlayer offlinePlayer, Inventory inventory) {
        loadPlayerData(offlinePlayer.getUniqueId()).thenAccept(data -> {
            data.enderChestContents = inventory.getContents();
            savePlayerData(offlinePlayer.getUniqueId(), data);
        });
    }

    public void saveOfflinePlayerInventory(OfflinePlayer offlinePlayer, Inventory virtualInv) {
        loadPlayerData(offlinePlayer.getUniqueId()).thenAccept(data -> {
            ItemStack[] mainContents = new ItemStack[36];
            for (int i = 0; i < 36; i++) {
                mainContents[i] = virtualInv.getItem(i);
            }
            ItemStack[] armorContents = new ItemStack[4];
            armorContents[3] = virtualInv.getItem(45);
            armorContents[2] = virtualInv.getItem(46);
            armorContents[1] = virtualInv.getItem(47);
            armorContents[0] = virtualInv.getItem(48);
            data.inventoryStorage = mainContents;
            data.inventoryArmor = armorContents;
            data.offhandItem = virtualInv.getItem(53);
            savePlayerData(offlinePlayer.getUniqueId(), data);
        });
    }

    public void loadPlayerInventory(Player player) {
        loadPlayerData(player.getUniqueId()).thenAccept(data -> FoliaScheduler.runTask(plugin, () -> {
            if (player.isOnline()) {
                player.getInventory().clear();
                if (data.inventoryStorage != null) {
                    player.getInventory().setStorageContents(data.inventoryStorage);
                }
                if (data.inventoryArmor != null) {
                    player.getInventory().setArmorContents(data.inventoryArmor);
                }
                if (data.offhandItem != null) {
                    player.getInventory().setItemInOffHand(data.offhandItem);
                }
            }
        }));
    }

    public void getOfflinePlayerInventory(OfflinePlayer offlinePlayer, Consumer<Inventory> callback) {
        loadPlayerData(offlinePlayer.getUniqueId()).thenAccept(data -> {
            String title = TextParser.colorize(plugin.getLanguageManager().getMessage("invsee.inventory-title")
                    .replace("%player%", offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown"));
            Inventory virtualInv = Bukkit.createInventory(null, 54, title);
            if (data.inventoryStorage != null) {
                for (int i = 0; i < 36 && i < data.inventoryStorage.length; i++) {
                    virtualInv.setItem(i, data.inventoryStorage[i]);
                }
            }
            if (data.inventoryArmor != null && data.inventoryArmor.length == 4) {
                virtualInv.setItem(45, data.inventoryArmor[3]);
                virtualInv.setItem(46, data.inventoryArmor[2]);
                virtualInv.setItem(47, data.inventoryArmor[1]);
                virtualInv.setItem(48, data.inventoryArmor[0]);
            }
            virtualInv.setItem(53, data.offhandItem);
            fillPlaceholders(virtualInv);
            FoliaScheduler.runTask(plugin, () -> callback.accept(virtualInv));
        });
    }

    public static void fillPlaceholders(Inventory inv) {
        ItemStack placeholder = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = placeholder.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            placeholder.setItemMeta(meta);
        }
        for (int i = 36; i < 45; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, placeholder);
            }
        }
        for (int i = 49; i < 53; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, placeholder);
            }
        }
    }


    private boolean isInventoryEmpty(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        for (ItemStack item : player.getEnderChest().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    public void saveBackup(Player player, InventoryBackup.BackupReason reason) {
        saveBackup(player, reason, null);
    }

    public void saveBackup(Player player, InventoryBackup.BackupReason reason, String deathCause) {
        if (isInventoryEmpty(player)) {
            return;
        }

        final ItemStack[] inventoryContents = player.getInventory().getContents().clone();
        final ItemStack[] enderChestContents = player.getEnderChest().getContents().clone();
        final int totalExperience = player.getTotalExperience();
        final int level = player.getLevel();
        final Location deathLocation = reason == InventoryBackup.BackupReason.DEATH ? player.getLocation() : null;
        final UUID playerUuid = player.getUniqueId();
        final long timestamp = System.currentTimeMillis();

        runDb(() -> {
            try {
                InventoryBackup backup = new InventoryBackup(
                        inventoryContents, enderChestContents, timestamp, reason, totalExperience, level,
                        deathLocation != null ? deathLocation.getWorld().getName() : null,
                        deathLocation != null ? deathLocation.getBlockX() : 0,
                        deathLocation != null ? deathLocation.getBlockY() : 0,
                        deathLocation != null ? deathLocation.getBlockZ() : 0,
                        deathCause);
                insertBackupSync(playerUuid, backup);
            } catch (Exception e) {
                if (isDbMovedOrReadonly(e) || isStorageGone()) {
                    try {
                        invalidateConnectionsQuietly();
                        InventoryBackup backup = new InventoryBackup(
                                inventoryContents, enderChestContents, timestamp, reason, totalExperience, level,
                                deathLocation != null ? deathLocation.getWorld().getName() : null,
                                deathLocation != null ? deathLocation.getBlockX() : 0,
                                deathLocation != null ? deathLocation.getBlockY() : 0,
                                deathLocation != null ? deathLocation.getBlockZ() : 0,
                                deathCause);
                        insertBackupSync(playerUuid, backup);
                        return;
                    } catch (Exception retry) {
                        logSaveFailure("saveBackup", retry);
                        return;
                    }
                }
                logSaveFailure("saveBackup", e);
            }
        });

    }

    private void insertBackupSync(UUID uuid, InventoryBackup backup) throws SQLException {
        Connection c = rollbackConn(uuid);
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO inventory_backups(
                  reason, timestamp, inventory, ender_chest, total_xp, level,
                  death_world, death_x, death_y, death_z, death_cause
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            ps.setString(1, backup.getReason().name());
            ps.setLong(2, backup.getTimestamp());
            ps.setBytes(3, ItemBlobCodec.encode(backup.getInventoryContents()));
            ps.setBytes(4, ItemBlobCodec.encode(backup.getEnderChestContents()));
            ps.setInt(5, backup.getTotalExperience());
            ps.setInt(6, backup.getLevel());
            ps.setString(7, backup.getDeathWorld());
            if (backup.getDeathWorld() != null) {
                ps.setInt(8, backup.getDeathX());
                ps.setInt(9, backup.getDeathY());
                ps.setInt(10, backup.getDeathZ());
            } else {
                ps.setNull(8, Types.INTEGER);
                ps.setNull(9, Types.INTEGER);
                ps.setNull(10, Types.INTEGER);
            }
            ps.setString(11, backup.getDeathCause());
            ps.executeUpdate();
        }
    }

    public void getBackupsAsync(OfflinePlayer player, InventoryBackup.BackupReason reason,
            Consumer<List<InventoryBackup>> callback) {
        final UUID uuid = player.getUniqueId();
        supplyDb(() -> {
            List<InventoryBackup> list = new ArrayList<>();
            try {
                Connection c = rollbackConn(uuid);
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT id, timestamp, total_xp, level, death_world, death_x, death_y, death_z, death_cause
                        FROM inventory_backups
                        WHERE reason = ?
                        ORDER BY timestamp DESC
                        """)) {
                    ps.setString(1, reason.name());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(InventoryBackup.metadata(
                                    rs.getLong(1),
                                    rs.getLong(2),
                                    reason,
                                    rs.getInt(3),
                                    rs.getInt(4),
                                    rs.getString(5),
                                    rs.getInt(6),
                                    rs.getInt(7),
                                    rs.getInt(8),
                                    rs.getString(9)));
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "getBackupsAsync failed: " + uuid, e);
            }
            return list;
        }).thenAccept(list -> FoliaScheduler.runTask(plugin, () -> callback.accept(list)));
    }

    public void loadBackupContentsAsync(InventoryBackup backup, Consumer<InventoryBackup> callback) {
        if (backup == null) {
            FoliaScheduler.runTask(plugin, () -> callback.accept(null));
            return;
        }
        if (backup.isContentsLoaded()) {
            FoliaScheduler.runTask(plugin, () -> callback.accept(backup));
            return;
        }
        if (backup.getStorageId() < 0) {
            FoliaScheduler.runTask(plugin, () -> callback.accept(backup));
            return;
        }
        FoliaScheduler.runTask(plugin, () -> callback.accept(backup));
    }

    public void loadBackupContentsAsync(UUID uuid, InventoryBackup backup, Consumer<InventoryBackup> callback) {
        if (backup == null) {
            FoliaScheduler.runTask(plugin, () -> callback.accept(null));
            return;
        }
        if (backup.isContentsLoaded()) {
            FoliaScheduler.runTask(plugin, () -> callback.accept(backup));
            return;
        }
        if (backup.getStorageId() < 0 || uuid == null) {
            FoliaScheduler.runTask(plugin, () -> callback.accept(backup));
            return;
        }
        supplyDb(() -> {
            try {
                Connection c = rollbackConn(uuid);
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT inventory, ender_chest FROM inventory_backups WHERE id = ?")) {
                    ps.setLong(1, backup.getStorageId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return backup.withContents(
                                    ItemBlobCodec.decode(rs.getBytes(1)),
                                    ItemBlobCodec.decode(rs.getBytes(2)));
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "loadBackupContents failed id=" + backup.getStorageId(), e);
            }
            return backup;
        }).thenAccept(loaded -> FoliaScheduler.runTask(plugin, () -> callback.accept(loaded)));
    }


    public CompletableFuture<Void> savePunishments(Map<UUID, PunishmentEntry> bans, Map<UUID, PunishmentEntry> mutes,
            Map<UUID, PunishmentEntry> freezes, Map<UUID, JailEntry> jails) {
        return savePunishments(bans, mutes, freezes, jails, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    public CompletableFuture<Void> savePunishments(Map<UUID, PunishmentEntry> bans, Map<UUID, PunishmentEntry> mutes,
            Map<UUID, PunishmentEntry> freezes, Map<UUID, JailEntry> jails,
            Map<UUID, List<PunishmentEntry>> banHistory, Map<UUID, List<PunishmentEntry>> muteHistory,
            Map<String, PunishmentEntry> mutedIPs) {
        return savePunishments(bans, mutes, freezes, jails, banHistory, muteHistory, mutedIPs, Map.of(), Map.of());
    }

    public CompletableFuture<Void> savePunishments(Map<UUID, PunishmentEntry> bans, Map<UUID, PunishmentEntry> mutes,
            Map<UUID, PunishmentEntry> freezes, Map<UUID, JailEntry> jails,
            Map<UUID, List<PunishmentEntry>> banHistory, Map<UUID, List<PunishmentEntry>> muteHistory,
            Map<String, PunishmentEntry> mutedIPs, Map<String, PunishmentEntry> bannedIPs,
            Map<UUID, String> lastKnownIps) {
        PunishmentData data = new PunishmentData();
        data.bans = new HashMap<>(bans);
        data.mutes = new HashMap<>(mutes);
        data.freezes = new HashMap<>(freezes);
        data.jails = new HashMap<>(jails);
        data.banHistory = new ConcurrentHashMap<>(banHistory);
        data.muteHistory = new ConcurrentHashMap<>(muteHistory);
        data.mutedIPs = new ConcurrentHashMap<>(mutedIPs);
        data.bannedIPs = new ConcurrentHashMap<>(bannedIPs);
        data.lastKnownIps = new ConcurrentHashMap<>(lastKnownIps);
        return runDb(() -> {
            try {
                savePunishmentsSync(data);
            } catch (Exception e) {
                if (isDbMovedOrReadonly(e) || isStorageGone()) {
                    try {
                        invalidateConnectionsQuietly();
                        savePunishmentsSync(data);
                        return;
                    } catch (Exception retry) {
                        logSaveFailure("savePunishments", retry);
                        return;
                    }
                }
                logSaveFailure("savePunishments", e);
            }
        });
    }

    private void savePunishmentsSync(PunishmentData data) throws SQLException {
        Connection c = ensurePunishmentsConn();
        boolean oldAuto = c.getAutoCommit();
        c.setAutoCommit(false);

        try {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM punishments");
                st.executeUpdate("DELETE FROM punishment_history");
                st.executeUpdate("DELETE FROM player_ips");
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO punishments(kind, target_key, expiry, reason, punisher, timestamp, removed_by,
                      jail_name, return_world, return_x, return_y, return_z, return_yaw, return_pitch)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """)) {
                insertPunEntries(ps, "ban", data.bans);
                insertPunEntries(ps, "mute", data.mutes);
                insertPunEntries(ps, "freeze", data.freezes);
                if (data.mutedIPs != null) {
                    for (Map.Entry<String, PunishmentEntry> e : data.mutedIPs.entrySet()) {
                        bindPun(ps, "ipmute", e.getKey(), e.getValue(), null, null);
                        ps.addBatch();
                    }
                }
                if (data.bannedIPs != null) {
                    for (Map.Entry<String, PunishmentEntry> e : data.bannedIPs.entrySet()) {
                        bindPun(ps, "ipban", e.getKey(), e.getValue(), null, null);
                        ps.addBatch();
                    }
                }
                if (data.jails != null) {
                    for (Map.Entry<UUID, JailEntry> e : data.jails.entrySet()) {
                        JailEntry j = e.getValue();
                        PunishmentEntry pe = new PunishmentEntry(j.expiry, j.reason, j.punisherUUID, j.timestamp);
                        bindPun(ps, "jail", e.getKey().toString(), pe, j.jailName, j.returnLocation);
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO punishment_history(kind, uuid, expiry, reason, punisher, timestamp, removed_by)
                    VALUES(?,?,?,?,?,?,?)
                    """)) {
                insertHistory(ps, "ban", data.banHistory);
                insertHistory(ps, "mute", data.muteHistory);
                ps.executeBatch();
            }
            if (data.lastKnownIps != null && !data.lastKnownIps.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO player_ips(uuid, ip) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET ip=excluded.ip")) {
                    for (Map.Entry<UUID, String> e : data.lastKnownIps.entrySet()) {
                        if (e.getValue() == null) {
                            continue;
                        }
                        ps.setString(1, e.getKey().toString());
                        ps.setString(2, e.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(oldAuto);
        }
    }

    private void insertPunEntries(PreparedStatement ps, String kind, Map<UUID, PunishmentEntry> map)
            throws SQLException {
        if (map == null) {
            return;
        }
        for (Map.Entry<UUID, PunishmentEntry> e : map.entrySet()) {
            bindPun(ps, kind, e.getKey().toString(), e.getValue(), null, null);
            ps.addBatch();
        }
    }

    private void bindPun(PreparedStatement ps, String kind, String key, PunishmentEntry e,
            String jailName, SerializableLocation ret) throws SQLException {
        ps.setString(1, kind);
        ps.setString(2, key);
        ps.setLong(3, e.expiry);
        ps.setString(4, e.reason);
        ps.setString(5, e.punisherUUID != null ? e.punisherUUID.toString() : null);
        ps.setLong(6, e.timestamp);
        ps.setString(7, e.removedBy);
        ps.setString(8, jailName);
        if (ret != null) {
            ps.setString(9, ret.worldName);
            ps.setDouble(10, ret.x);
            ps.setDouble(11, ret.y);
            ps.setDouble(12, ret.z);
            ps.setFloat(13, ret.yaw);
            ps.setFloat(14, ret.pitch);
        } else {
            ps.setNull(9, Types.VARCHAR);
            ps.setNull(10, Types.DOUBLE);
            ps.setNull(11, Types.DOUBLE);
            ps.setNull(12, Types.DOUBLE);
            ps.setNull(13, Types.FLOAT);
            ps.setNull(14, Types.FLOAT);
        }
    }

    private void insertHistory(PreparedStatement ps, String kind, Map<UUID, List<PunishmentEntry>> history)
            throws SQLException {
        if (history == null) {
            return;
        }
        for (Map.Entry<UUID, List<PunishmentEntry>> e : history.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            for (PunishmentEntry pe : e.getValue()) {
                ps.setString(1, kind);
                ps.setString(2, e.getKey().toString());
                ps.setLong(3, pe.expiry);
                ps.setString(4, pe.reason);
                ps.setString(5, pe.punisherUUID != null ? pe.punisherUUID.toString() : null);
                ps.setLong(6, pe.timestamp);
                ps.setString(7, pe.removedBy);
                ps.addBatch();
            }
        }
    }

    public void loadPunishments(Consumer<PunishmentData> callback) {
        supplyDb(() -> {
            try {
                return loadPunishmentsFromConnection(ensurePunishmentsConn());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "loadPunishments failed", e);
                return new PunishmentData();
            }
        }).thenAccept(data -> FoliaScheduler.runTask(plugin, () -> callback.accept(data)));
    }


    private PunishmentData loadPunishmentsFromConnection(Connection c) throws SQLException {
        PunishmentData data = new PunishmentData();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM punishments");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String kind = rs.getString("kind");
                String key = rs.getString("target_key");
                PunishmentEntry pe = readPunEntry(rs);
                switch (kind) {
                    case "ban" -> data.bans.put(UUID.fromString(key), pe);
                    case "mute" -> data.mutes.put(UUID.fromString(key), pe);
                    case "freeze" -> data.freezes.put(UUID.fromString(key), pe);
                    case "ipmute" -> data.mutedIPs.put(key, pe);
                    case "ipban" -> data.bannedIPs.put(key, pe);
                    case "jail" -> {
                        String jailName = rs.getString("jail_name");
                        SerializableLocation ret = null;
                        String world = rs.getString("return_world");
                        if (world != null) {
                            ret = new SerializableLocation(world,
                                    rs.getDouble("return_x"), rs.getDouble("return_y"), rs.getDouble("return_z"),
                                    (float) rs.getDouble("return_yaw"), (float) rs.getDouble("return_pitch"));
                        }
                        data.jails.put(UUID.fromString(key),
                                new JailEntry(pe.expiry, pe.reason, pe.punisherUUID, pe.timestamp, jailName, ret));
                    }
                    default -> {
                    }
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT kind, uuid, expiry, reason, punisher, timestamp, removed_by FROM punishment_history");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String kind = rs.getString(1);
                UUID uuid = UUID.fromString(rs.getString(2));
                PunishmentEntry pe = new PunishmentEntry(
                        rs.getLong(3), rs.getString(4), parseUuid(rs.getString(5)), rs.getLong(6));
                pe.removedBy = rs.getString(7);
                if ("ban".equals(kind)) {
                    data.banHistory.computeIfAbsent(uuid, k -> new ArrayList<>()).add(pe);
                } else if ("mute".equals(kind)) {
                    data.muteHistory.computeIfAbsent(uuid, k -> new ArrayList<>()).add(pe);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid, ip FROM player_ips");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                data.lastKnownIps.put(UUID.fromString(rs.getString(1)), rs.getString(2));
            }
        }
        return data;
    }

    private PunishmentEntry readPunEntry(ResultSet rs) throws SQLException {
        PunishmentEntry pe = new PunishmentEntry(
                rs.getLong("expiry"),
                rs.getString("reason"),
                parseUuid(rs.getString("punisher")),
                rs.getLong("timestamp"));
        pe.removedBy = rs.getString("removed_by");
        return pe;
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    public CompletableFuture<Void> saveDeathLocations(Map<UUID, Location> locations) {
        return runDb(() -> {
            try {
                for (Map.Entry<UUID, Location> e : locations.entrySet()) {
                    Location loc = e.getValue();
                    if (loc == null || loc.getWorld() == null) {
                        continue;
                    }
                    Connection c = playerDataConn(e.getKey());
                    writeBackLocation(c, new SerializableLocation(loc));
                }
            } catch (Exception e) {
                if (isDbMovedOrReadonly(e) || isStorageGone()) {
                    try {
                        invalidateConnectionsQuietly();
                        for (Map.Entry<UUID, Location> entry : locations.entrySet()) {
                            Location loc = entry.getValue();
                            if (loc == null || loc.getWorld() == null) {
                                continue;
                            }
                            Connection c = playerDataConn(entry.getKey());
                            writeBackLocation(c, new SerializableLocation(loc));
                        }
                        return;
                    } catch (Exception retry) {
                        logSaveFailure("saveDeathLocations", retry);
                        return;
                    }
                }
                logSaveFailure("saveDeathLocations", e);
            }
        });
    }


    public void loadDeathLocations(Consumer<Map<UUID, Location>> callback) {
        supplyDb(() -> {
            Map<UUID, Location> map = new ConcurrentHashMap<>();
            File[] files = playerDataDir.listFiles((d, n) -> n.endsWith(".db") && !n.contains("-"));
            if (files == null) {
                return map;
            }
            for (File f : files) {
                String name = f.getName();
                if (name.endsWith(".db-wal") || name.endsWith(".db-shm")) {
                    continue;
                }
                UUID uuid;
                try {
                    uuid = UUID.fromString(name.substring(0, name.length() - 3));
                } catch (Exception ex) {
                    continue;
                }
                try {
                    Connection c = playerDataConn(uuid);
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT world, x, y, z, yaw, pitch FROM back_location WHERE id = 1");
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            SerializableLocation ser = new SerializableLocation(
                                    rs.getString(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                                    (float) rs.getDouble(5), (float) rs.getDouble(6));
                            Location loc = ser.toLocation();
                            if (loc != null) {
                                map.put(uuid, loc);
                            }
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "loadDeathLocations entry failed: " + uuid, e);
                }
            }
            return map;
        }).thenAccept(map -> FoliaScheduler.runTask(plugin, () -> callback.accept(map)));
    }

    public CompletableFuture<Void> saveTempFlyData(TempFlyData data) {
        return runDb(() -> {
            try {
                if (data == null || data.players == null) {
                    return;
                }
                for (Map.Entry<UUID, TempFlyEntry> e : data.players.entrySet()) {
                    Connection c = playerDataConn(e.getKey());
                    writeTempFly(c, e.getValue());
                }
            } catch (Exception e) {
                if (isDbMovedOrReadonly(e) || isStorageGone()) {
                    try {
                        invalidateConnectionsQuietly();
                        if (data != null && data.players != null) {
                            for (Map.Entry<UUID, TempFlyEntry> entry : data.players.entrySet()) {
                                Connection c = playerDataConn(entry.getKey());
                                writeTempFly(c, entry.getValue());
                            }
                        }
                        return;
                    } catch (Exception retry) {
                        logSaveFailure("saveTempFlyData", retry);
                        return;
                    }
                }
                logSaveFailure("saveTempFlyData", e);
            }
        });
    }


    public void loadTempFlyData(Consumer<TempFlyData> callback) {
        supplyDb(() -> {
            TempFlyData data = new TempFlyData();
            File[] files = playerDataDir.listFiles((d, n) -> n.endsWith(".db"));
            if (files == null) {
                return data;
            }
            for (File f : files) {
                String name = f.getName();
                if (!name.endsWith(".db") || name.contains("-wal") || name.contains("-shm")) {
                    continue;
                }
                UUID uuid;
                try {
                    uuid = UUID.fromString(name.substring(0, name.length() - 3));
                } catch (Exception ex) {
                    continue;
                }
                try {
                    Connection c = playerDataConn(uuid);
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT remaining_seconds, last_update FROM tempfly WHERE id = 1");
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            data.players.put(uuid, new TempFlyEntry(rs.getLong(1), rs.getLong(2)));
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "loadTempFly entry failed: " + uuid, e);
                }
            }
            return data;
        }).thenAccept(data -> FoliaScheduler.runTask(plugin, () -> callback.accept(data)));
    }

    public void saveMentionPrefs(MentionPrefsData data) {
        runDb(() -> {
            try {
                if (data == null || data.players == null) {
                    return;
                }
                for (Map.Entry<UUID, MentionPrefs> e : data.players.entrySet()) {
                    Connection c = playerDataConn(e.getKey());
                    writeMentionPrefs(c, e.getValue());
                }
            } catch (Exception e) {
                if (isDbMovedOrReadonly(e) || isStorageGone()) {
                    try {
                        invalidateConnectionsQuietly();
                        if (data != null && data.players != null) {
                            for (Map.Entry<UUID, MentionPrefs> entry : data.players.entrySet()) {
                                Connection c = playerDataConn(entry.getKey());
                                writeMentionPrefs(c, entry.getValue());
                            }
                        }
                        return;
                    } catch (Exception retry) {
                        logSaveFailure("saveMentionPrefs", retry);
                        return;
                    }
                }
                logSaveFailure("saveMentionPrefs", e);
            }
        });
    }


    public void loadMentionPrefs(Consumer<MentionPrefsData> callback) {
        supplyDb(() -> {
            MentionPrefsData data = new MentionPrefsData();
            File[] files = playerDataDir.listFiles((d, n) -> n.endsWith(".db"));
            if (files == null) {
                return data;
            }
            for (File f : files) {
                String name = f.getName();
                if (!name.endsWith(".db") || name.contains("-wal") || name.contains("-shm")) {
                    continue;
                }
                UUID uuid;
                try {
                    uuid = UUID.fromString(name.substring(0, name.length() - 3));
                } catch (Exception ex) {
                    continue;
                }
                try {
                    Connection c = playerDataConn(uuid);
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT enabled, title, actionbar, toast, sound FROM mention_prefs WHERE id = 1");
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            MentionPrefs p = new MentionPrefs();
                            p.enabled = rs.getInt(1) != 0;
                            p.title = rs.getInt(2) != 0;
                            p.actionbar = rs.getInt(3) != 0;
                            p.toast = rs.getInt(4) != 0;
                            p.sound = rs.getInt(5) != 0;
                            data.players.put(uuid, p);
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "loadMentionPrefs entry failed: " + uuid, e);
                }
            }
            return data;
        }).thenAccept(data -> FoliaScheduler.runTask(plugin, () -> callback.accept(data)));
    }

    public CompletableFuture<Void> saveEconomy(Map<UUID, Double> balances) {
        return runDb(() -> {
            try {
                saveEconomySync(balances);
            } catch (Exception e) {
                if (isDbMovedOrReadonly(e) || isStorageGone()) {
                    try {
                        invalidateConnectionsQuietly();
                        saveEconomySync(balances);
                        return;
                    } catch (Exception retry) {
                        logSaveFailure("saveEconomy", retry);
                        return;
                    }
                }
                logSaveFailure("saveEconomy", e);
            }
        });
    }

    private void saveEconomySync(Map<UUID, Double> balances) throws SQLException {
        Connection c = ensureEconomyConn();
        boolean old = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM economy");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO economy(uuid, balance) VALUES(?,?)")) {
                for (Map.Entry<UUID, Double> e : balances.entrySet()) {
                    ps.setString(1, e.getKey().toString());
                    ps.setDouble(2, e.getValue() != null ? e.getValue() : 0D);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        } catch (SQLException ex) {
            try {
                c.rollback();
            } catch (SQLException ignored) {
            }
            throw ex;
        } finally {
            try {
                c.setAutoCommit(old);
            } catch (SQLException ignored) {
            }
        }
    }


    public void loadEconomy(Consumer<Map<UUID, Double>> callback) {
        supplyDb(() -> {
            Map<UUID, Double> map = new HashMap<>();
            try {
                Connection c = ensureEconomyConn();
                try (PreparedStatement ps = c.prepareStatement("SELECT uuid, balance FROM economy");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        map.put(UUID.fromString(rs.getString(1)), rs.getDouble(2));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "loadEconomy failed", e);
            }
            return map;
        }).thenAccept(map -> FoliaScheduler.runTask(plugin, () -> callback.accept(map)));
    }



    private PlayerData loadLegacyPlayerData(File file) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object readObject = ois.readObject();
            if (readObject instanceof PlayerData) {
                PlayerData data = (PlayerData) readObject;
                data.inventoryStorage = decodeLegacyItems(data.inventoryStorageData);
                data.inventoryArmor = decodeLegacyItems(data.inventoryArmorData);
                data.offhandItem = decodeLegacySingle(data.offhandItemData);
                data.enderChestContents = decodeLegacyItems(data.enderChestContentsData);
                if (data.homes == null) {
                    data.homes = new HashMap<>();
                }
                return data;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "legacy playerdata read failed: " + file.getName(), e);
        }
        return null;
    }

    private InventoryBackup loadLegacyBackup(File file, String reasonName) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = ois.readObject();
            if (obj instanceof BackupData) {
                BackupData b = (BackupData) obj;
                ItemStack[] inv = b.inventoryContents != null ? b.inventoryContents
                        : decodeLegacyItems(b.inventoryContentsData);
                ItemStack[] ec = b.enderChestContents != null ? b.enderChestContents
                        : decodeLegacyItems(b.enderChestContentsData);
                InventoryBackup.BackupReason reason;
                try {
                    reason = InventoryBackup.BackupReason.valueOf(
                            b.reason != null ? b.reason : reasonName);
                } catch (Exception ex) {
                    reason = InventoryBackup.BackupReason.valueOf(reasonName);
                }
                return new InventoryBackup(inv, ec, b.timestamp, reason, b.totalExperience, b.level,
                        b.deathWorld, b.deathX, b.deathY, b.deathZ);
            }
            if (obj instanceof OldBackupData) {
                OldBackupData b = (OldBackupData) obj;
                InventoryBackup.BackupReason reason;
                try {
                    reason = InventoryBackup.BackupReason.valueOf(
                            b.reason != null ? b.reason : reasonName);
                } catch (Exception ex) {
                    reason = InventoryBackup.BackupReason.valueOf(reasonName);
                }
                return new InventoryBackup(decodeLegacyItems(b.inventoryContentsData),
                        decodeLegacyItems(b.enderChestContentsData), b.timestamp, reason, b.totalExperience, b.level);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "legacy backup read failed: " + file.getName(), e);
        }
        return null;
    }

    private PunishmentData loadLegacyPunishmentData(File file) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = ois.readObject();
            if (obj instanceof PunishmentData) {
                return (PunishmentData) obj;
            }
        } catch (Exception ignored) {
        }
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file)) {
            @Override
            protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                ObjectStreamClass desc = super.readClassDescriptor();
                if (desc.getName().equals("org.widnees.widCore.database.BinaryDataManager$PunishmentData")) {
                    return ObjectStreamClass.lookup(PunishmentData.class);
                }
                return desc;
            }
        }) {
            Object obj = ois.readObject();
            if (obj instanceof PunishmentData) {
                return (PunishmentData) obj;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "legacy punishments read failed", e);
        }
        return new PunishmentData();
    }

    private static ItemStack[] decodeLegacyItems(String data) {
        return ItemBlobCodec.decodeBase64(data);
    }

    private static ItemStack decodeLegacySingle(String data) {
        return ItemBlobCodec.decodeSingleBase64(data);
    }


    public static class SerializableLocation implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String worldName;
        public final double x, y, z;
        public final float yaw, pitch;

        public SerializableLocation(Location loc) {
            this.worldName = loc.getWorld().getName();
            this.x = loc.getX();
            this.y = loc.getY();
            this.z = loc.getZ();
            this.yaw = loc.getYaw();
            this.pitch = loc.getPitch();
        }

        public SerializableLocation(String worldName, double x, double y, double z, float yaw, float pitch) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public Location toLocation() {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return null;
            }
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    public static class PunishmentEntry implements Serializable {
        private static final long serialVersionUID = 2L;
        public long expiry;
        public String reason;
        public UUID punisherUUID;
        public long timestamp;
        public String removedBy;

        public PunishmentEntry(long expiry, String reason, UUID punisherUUID, long timestamp) {
            this.expiry = expiry;
            this.reason = reason;
            this.punisherUUID = punisherUUID;
            this.timestamp = timestamp;
        }
    }

    public static class JailEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        public long expiry;
        public String reason;
        public UUID punisherUUID;
        public long timestamp;
        public String jailName;
        public SerializableLocation returnLocation;

        public JailEntry(long expiry, String reason, UUID punisherUUID, long timestamp, String jailName,
                Location returnLoc) {
            this.expiry = expiry;
            this.reason = reason;
            this.punisherUUID = punisherUUID;
            this.timestamp = timestamp;
            this.jailName = jailName;
            this.returnLocation = returnLoc != null ? new SerializableLocation(returnLoc) : null;
        }

        public JailEntry(long expiry, String reason, UUID punisherUUID, long timestamp, String jailName,
                SerializableLocation returnLocation) {
            this.expiry = expiry;
            this.reason = reason;
            this.punisherUUID = punisherUUID;
            this.timestamp = timestamp;
            this.jailName = jailName;
            this.returnLocation = returnLocation;
        }
    }

    public static class PunishmentData implements Serializable {
        private static final long serialVersionUID = 6L;
        public Map<UUID, PunishmentEntry> bans = new ConcurrentHashMap<>();
        public Map<UUID, PunishmentEntry> mutes = new ConcurrentHashMap<>();
        public Map<UUID, PunishmentEntry> freezes = new ConcurrentHashMap<>();
        public Map<UUID, JailEntry> jails = new ConcurrentHashMap<>();
        public Map<UUID, List<PunishmentEntry>> banHistory = new ConcurrentHashMap<>();
        public Map<UUID, List<PunishmentEntry>> muteHistory = new ConcurrentHashMap<>();
        public Map<String, PunishmentEntry> mutedIPs = new ConcurrentHashMap<>();
        public Map<String, PunishmentEntry> bannedIPs = new ConcurrentHashMap<>();
        public Map<UUID, String> lastKnownIps = new ConcurrentHashMap<>();
    }

    public static class BackData implements Serializable {
        private static final long serialVersionUID = 1L;
        public Map<UUID, SerializableLocation> locations = new ConcurrentHashMap<>();
    }

    public static class PlayerData implements Serializable {
        private static final long serialVersionUID = 3L;
        String inventoryStorageData;
        String inventoryArmorData;
        String offhandItemData;
        String enderChestContentsData;
        public Map<String, SerializableLocation> homes = new HashMap<>();
        public transient ItemStack[] inventoryStorage;
        public transient ItemStack[] inventoryArmor;
        public transient ItemStack offhandItem;
        public transient ItemStack[] enderChestContents;
    }

    public static class TempFlyEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        public long remainingSeconds;
        public long lastUpdateTimestamp;

        public TempFlyEntry(long remainingSeconds, long lastUpdateTimestamp) {
            this.remainingSeconds = remainingSeconds;
            this.lastUpdateTimestamp = lastUpdateTimestamp;
        }
    }

    public static class TempFlyData implements Serializable {
        private static final long serialVersionUID = 1L;
        public Map<UUID, TempFlyEntry> players = new ConcurrentHashMap<>();
    }

    private static class BackupData implements Serializable {
        private static final long serialVersionUID = 3L;
        long timestamp;
        String reason;
        String inventoryContentsData;
        String enderChestContentsData;
        int totalExperience;
        int level;
        String deathWorld;
        int deathX, deathY, deathZ;
        transient ItemStack[] inventoryContents;
        transient ItemStack[] enderChestContents;
    }

    private static class OldBackupData implements Serializable {
        private static final long serialVersionUID = 2L;
        long timestamp;
        String reason;
        String inventoryContentsData;
        String enderChestContentsData;
        int totalExperience;
        int level;
    }

    public static class MentionPrefsData implements Serializable {
        private static final long serialVersionUID = 1L;
        public Map<UUID, MentionPrefs> players = new ConcurrentHashMap<>();
    }

    public static class MentionPrefs implements Serializable {
        private static final long serialVersionUID = 1L;
        public boolean enabled = true;
        public boolean title = true;
        public boolean actionbar = false;
        public boolean toast = false;
        public boolean sound = true;

        public static MentionPrefs fromConfig(FileConfiguration config) {
            MentionPrefs prefs = new MentionPrefs();
            if (config != null) {
                prefs.enabled = config.getBoolean("defaults.enabled", true);
                prefs.title = config.getBoolean("defaults.title", true);
                prefs.actionbar = config.getBoolean("defaults.actionbar", false);
                prefs.toast = config.getBoolean("defaults.toast", false);
                prefs.sound = config.getBoolean("defaults.sound", true);
            }
            return prefs;
        }
    }
}
