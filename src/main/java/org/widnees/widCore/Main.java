package org.widnees.widCore;

import net.luckperms.api.LuckPerms;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.widnees.widCore.command.WidCoreCommand;
import org.widnees.widCore.database.BinaryDataManager;
import org.widnees.widCore.listener.JoinLeaveListener;
import org.widnees.widCore.listener.UpdateListener;
import org.widnees.widCore.manager.*;
import org.widnees.widCore.module.ModuleManager;
import org.widnees.widCore.util.FoliaScheduler;
import org.widnees.widCore.util.VersionSupport;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class Main extends JavaPlugin {

    private ConfigManager configManager;
    private LanguageManager languageManager;
    private BinaryDataManager dataManager;
    private VanishManager vanishManager;
    private TrollManager trollManager;
    private LuckPerms luckPerms;
    private SpawnLocationManager spawnLocationManager;
    private VoidSpawnManager voidSpawnManager;
    private MenuManager menuManager;
    private ShowItemManager showItemManager;
    private ItemEffectManager itemEffectManager;
    private ChatMetaManager chatMetaManager;
    private ChatGuardManager chatGuardManager;
    private MessageManager messageManager;
    private ItemRemovalManager itemRemovalManager;
    private WorldDataManager worldDataManager;
    private PunishmentManager punishmentManager;
    private PunishmentMenuManager punishmentMenuManager;
    private WarpManager warpManager;
    private TeleportManager teleportManager;
    private TeleportAnimator teleportAnimator;
    private BackManager backManager;
    private ModuleManager moduleManager;
    private HelpMenuManager helpMenuManager;
    private DismissMenuManager dismissMenuManager;
    private JoinLeaveListener joinLeaveListener;
    private BannedItemManager bannedItemManager;
    private VersionSupport versionSupport;
    private TpaManager tpaManager;
    private TempFlyManager tempFlyManager;
    private AntiMobSpawnManager antiMobSpawnManager;
    private JailManager jailManager;
    private HomeManager homeManager;
    private UpdateManager updateManager;
    private AliasManager aliasManager;
    private RtpManager rtpManager;
    private DisguiseManager disguiseManager;
    private BinaryDataManager.MentionPrefsData mentionPrefsData;

    private final HashMap<UUID, UUID> openOfflineInventories = new HashMap<>();
    private final HashMap<UUID, UUID> openInvseeInventories = new HashMap<>();
    private final HashMap<UUID, BukkitTask> activeInvseeTasks = new HashMap<>();
    private final Set<UUID> vanishedPlayers = new HashSet<>();
    private final Set<UUID> godModePlayers = new HashSet<>();

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public UpdateManager getUpdateManager() {
        return updateManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public void onEnable() {
        cleanOldVersions();

        int pluginId = 28114;
        Metrics metrics = new Metrics(this, pluginId);

        this.configManager = new ConfigManager(this);

        configManager.setupMainConfig();

        this.languageManager = new LanguageManager(this);

        this.aliasManager = new AliasManager(this);

        this.updateManager = new UpdateManager(this);

        this.dataManager = new BinaryDataManager(this);
        this.mentionPrefsData = new BinaryDataManager.MentionPrefsData();
        this.dataManager.loadMentionPrefs(loaded -> this.mentionPrefsData = loaded);
        this.versionSupport = new VersionSupport(this);
        this.dismissMenuManager = new DismissMenuManager(this);
        this.joinLeaveListener = new JoinLeaveListener(this, this.dismissMenuManager);
        this.vanishManager = new VanishManager(this, this.joinLeaveListener);

        getServer().getPluginManager().registerEvents(
            new org.widnees.widCore.listener.WelcomeMenuListener(this, this.dismissMenuManager), this);

        this.showItemManager = new ShowItemManager(this);
        this.itemRemovalManager = new ItemRemovalManager(this);
        this.worldDataManager = new WorldDataManager(this);
        this.worldDataManager.loadWorldsEarly(); 
        this.messageManager = new MessageManager(this);
        this.trollManager = new TrollManager();
        this.itemEffectManager = new ItemEffectManager(this);

        this.chatMetaManager = new ChatMetaManager(this);
        this.chatGuardManager = new ChatGuardManager(this);
        this.punishmentManager = new PunishmentManager(this);
        this.punishmentMenuManager = new PunishmentMenuManager(this, punishmentManager);
        this.warpManager = new WarpManager(this);
        this.homeManager = new HomeManager(this);
        this.teleportManager = new TeleportManager(this);
        this.teleportAnimator = new TeleportAnimator(this);
        this.backManager = new BackManager(this, this.dataManager);
        this.spawnLocationManager = new SpawnLocationManager(this);
        this.voidSpawnManager = new VoidSpawnManager(this);
        this.tpaManager = new TpaManager(this);
        this.tempFlyManager = new TempFlyManager(this);
        this.antiMobSpawnManager = new AntiMobSpawnManager(this);
        this.rtpManager = new RtpManager(this);
        this.disguiseManager = new DisguiseManager(this);

        this.jailManager = new JailManager(this);
        this.jailManager.loadJails();

        this.bannedItemManager = new BannedItemManager(this);
        this.menuManager = new MenuManager(this, this.dataManager,
                this.configManager.getModuleConfig("inventory_rollback"));
        setupLuckPerms();

        this.moduleManager = new ModuleManager(this);
        this.moduleManager.initializeModules();

        printStartupMessage();

        this.tempFlyManager.loadData();
        this.moduleManager.registerModules();

        configManager.printUpdateLog();

        this.helpMenuManager = new HelpMenuManager(this, this.moduleManager, "widcore");

        getCommand("widcore").setExecutor(new WidCoreCommand(this));

        getServer().getPluginManager().registerEvents(new UpdateListener(this), this);

        FoliaScheduler.runTaskLaterAsync(this, () -> {
            updateManager.checkForUpdates(Bukkit.getConsoleSender());
        }, 100L);

    }

    @Override
    public void onDisable() {
        printDisableMessage();

        try {
            if (this.vanishManager != null) {
                this.vanishManager.unvanishAll();
            }

            if (this.itemRemovalManager != null && getConfig().getBoolean("features.item_removal", false)) {
                this.itemRemovalManager.shutdown();
            }

            if (this.disguiseManager != null) {
                this.disguiseManager.undisguiseAll();
            }

            if (this.warpManager != null) {
                this.warpManager.saveWarps();
            }
            if (this.tempFlyManager != null) {
                this.tempFlyManager.saveData();
            }

            CompletableFuture<Void> punishmentSave = punishmentManager != null ? punishmentManager.savePunishments()
                    : CompletableFuture.completedFuture(null);
            CompletableFuture<Void> backLocationSave = (backManager != null
                    && getConfig().getBoolean("features.back", false)) ? backManager.saveDeathLocations()
                            : CompletableFuture.completedFuture(null);
            CompletableFuture<Void> playerDataSave = dataManager != null ? dataManager.saveAllCachedPlayerData()
                    : CompletableFuture.completedFuture(null);

            CompletableFuture.allOf(punishmentSave, backLocationSave, playerDataSave).join();

            this.dataManager.close();

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin disable: ", e);
        }
    }

    public AliasManager getAliasManager() {
        return aliasManager;
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }

    public JailManager getJailManager() {
        return jailManager;
    }

    public RtpManager getRtpManager() {
        return rtpManager;
    }

    public DisguiseManager getDisguiseManager() {
        return disguiseManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public PunishmentMenuManager getPunishmentMenuManager() {
        return punishmentMenuManager;
    }

    public WarpManager getWarpManager() {
        return warpManager;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public TeleportAnimator getTeleportAnimator() {
        return teleportAnimator;
    }

    public BackManager getBackManager() {
        return backManager;
    }

    public SpawnLocationManager getSpawnLocationManager() {
        return spawnLocationManager;
    }

    public VoidSpawnManager getVoidSpawnManager() {
        return voidSpawnManager;
    }

    public TpaManager getTpaManager() {
        return tpaManager;
    }

    public TempFlyManager getTempFlyManager() {
        return tempFlyManager;
    }

    public AntiMobSpawnManager getAntiMobSpawnManager() {
        return antiMobSpawnManager;
    }

    public BannedItemManager getBannedItemManager() {
        return bannedItemManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public HelpMenuManager getHelpMenuManager() {
        return helpMenuManager;
    }

    public BinaryDataManager getDataManager() {
        return dataManager;
    }

    public BinaryDataManager.MentionPrefsData getMentionPrefsData() {
        return mentionPrefsData;
    }

    public VersionSupport getVersionSupport() {
        return versionSupport;
    }

    public VanishManager getVanishManager() {
        return vanishManager;
    }

    public ShowItemManager getShowItemManager() {
        return showItemManager;
    }

    public ItemRemovalManager getItemRemovalManager() {
        return itemRemovalManager;
    }

    public WorldDataManager getWorldDataManager() {
        return worldDataManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public TrollManager getTrollManager() {
        return trollManager;
    }

    public ItemEffectManager getItemEffectManager() {
        return itemEffectManager;
    }

    public ChatMetaManager getChatMetaManager() {
        return chatMetaManager;
    }

    public ChatGuardManager getChatGuardManager() {
        return chatGuardManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    private void setupLuckPerms() {
        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }

        try {
            RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (provider != null) {
                luckPerms = provider.getProvider();
            }
        } catch (NoClassDefFoundError e) {
            getLogger().warning("LuckPerms API sınıfları yüklenemedi. LuckPerms entegrasyonu devre dışı.");
        }
    }

    public static void sendNoPermission(Main plugin, CommandSender sender, String permission) {
        String msg = plugin.getLanguageManager().getMessage("general.no-permission");
        if (sender instanceof Player && msg.contains("%permission%")) {
            msg = msg.replace("%permission%", permission);
        }
        sendMessage(plugin, sender, msg);
    }

    public static void sendMessage(Main plugin, CommandSender sender, String message) {
        String prefix = plugin.getConfig().getString("prefix", "");
        String fullMessage = prefix + message;
        org.widnees.widCore.manager.TextParser.send(sender, fullMessage);
    }

    private void cleanOldVersions() {
        File dataFolder = getDataFolder();
        File[] files = dataFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".jar.old")) {
                    file.delete();
                }
            }
        }
    }

    private void printDisableMessage() {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        console.sendMessage("");
        console.sendMessage(ChatColor.RED + " _    _ _     _ _____");
        console.sendMessage(ChatColor.RED + "| |  | (_)   | /  __ \\");
        console.sendMessage(ChatColor.RED + "| |  | |_  __| | /  \\/ ___  _ __ ___");
        console.sendMessage(ChatColor.RED + "| |/\\| | |/ _` | |    / _ \\| '__/ _ \\");
        console.sendMessage(ChatColor.RED + "\\  /\\  / | (_| | \\__/\\ (_) | | |  __/");
        console.sendMessage(ChatColor.RED + " \\/  \\/|_|\\__,_|\\____/\\___/|_|  \\___|");
        console.sendMessage("");
    }

    public void printStartupMessage() {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        console.sendMessage("");
        console.sendMessage(ChatColor.GREEN + " _    _ _     _ _____");
        console.sendMessage(ChatColor.GREEN + "| |  | (_)   | /  __ \\");
        console.sendMessage(ChatColor.GREEN + "| |  | |_  __| | /  \\/ ___  _ __ ___");
        console.sendMessage(ChatColor.GREEN + "| |/\\| | |/ _` | |    / _ \\| '__/ _ \\");
        console.sendMessage(ChatColor.GREEN + "\\  /\\  / | (_| | \\__/\\ (_) | | |  __/");
        console.sendMessage(ChatColor.GREEN + " \\/  \\/|_|\\__,_|\\____/\\___/|_|  \\___|");
        console.sendMessage("");
    }

    public void reloadPlugin() {

        moduleManager.unregisterModules();

        moduleManager.clearModules();

        configManager.clearCache();
        configManager.setupMainConfig();
        languageManager.loadLanguage();
        aliasManager.loadConfig();
        jailManager.loadJails();
        bannedItemManager.loadBannedItems();

        moduleManager.initializeModules();
        moduleManager.registerModules();

        getServer().getPluginManager().registerEvents(new UpdateListener(this), this);

        this.helpMenuManager = new HelpMenuManager(this, this.moduleManager, "widcore");

        getLogger().info("Plugin reloaded!");
    }

    public void addOpenOfflineInv(UUID admin, UUID target) {
        openOfflineInventories.put(admin, target);
    }

    public void removeOpenOfflineInv(UUID admin) {
        openOfflineInventories.remove(admin);
    }

    public UUID getOpenOfflineInvTarget(UUID admin) {
        return openOfflineInventories.get(admin);
    }

    public void addOpenInvsee(UUID admin, UUID target) {
        openInvseeInventories.put(admin, target);
    }

    public void removeOpenInvsee(UUID admin) {
        openInvseeInventories.remove(admin);
    }

    public UUID getOpenInvseeTarget(UUID admin) {
        return openInvseeInventories.get(admin);
    }

    public void addActiveInvseeTask(UUID admin, BukkitTask task) {
        activeInvseeTasks.put(admin, task);
    }

    public void removeActiveInvseeTask(UUID admin) {
        BukkitTask task = activeInvseeTasks.remove(admin);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public boolean isPlayerVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public void setPlayerVanished(UUID uuid, boolean content) {
        if (content) {
            vanishedPlayers.add(uuid);
        } else {
            vanishedPlayers.remove(uuid);
        }
    }

    public boolean isGodMode(UUID uuid) {
        return godModePlayers.contains(uuid);
    }

    public void setGodMode(UUID uuid, boolean content) {
        if (content) {
            godModePlayers.add(uuid);
        } else {
            godModePlayers.remove(uuid);
        }
    }

    public HashMap<UUID, UUID> getOpenInvseeInventories() {
        return openInvseeInventories;
    }

    public HashMap<UUID, BukkitTask> getActiveInvseeTasks() {
        return activeInvseeTasks;
    }

    public Set<UUID> getVanishedPlayers() {
        return vanishedPlayers;
    }

    public JoinLeaveListener getJoinLeaveListener() {
        return joinLeaveListener;
    }

    public HashMap<UUID, UUID> getOpenOfflineInventories() {
        return openOfflineInventories;
    }

    public Set<UUID> getGodModePlayers() {
        return godModePlayers;
    }
        @SuppressWarnings("unused")
    private static final String __Wf7c3e9 = "\u0077\u0069\u0064\u006e" + "\u0065\u0065\u0073";

}