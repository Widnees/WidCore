package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;
import org.bukkit.util.Vector;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.TextParser;
import org.widnees.widCore.manager.TrollManager;
import org.widnees.widCore.util.FoliaScheduler;

import java.util.*;
import java.util.stream.Collectors;

public class TrollCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final TrollManager trollManager;
    private final Map<UUID, UUID> trollMenuTargetMap = new HashMap<>();
    public static String TROLL_MENU_TITLE;
    public static NamespacedKey TROLL_BUCKET_KEY;
    private static final List<String> TROLL_TYPES = Arrays.asList("mlg", "rotate", "moblook", "creeperchest");

    public TrollCommand(Main plugin, TrollManager trollManager) {
        this.plugin = plugin;
        this.trollManager = trollManager;
        TROLL_BUCKET_KEY = new NamespacedKey(plugin, "troll_bucket");
        updateMenuTitle();
    }

    public void updateMenuTitle() {
        TROLL_MENU_TITLE = TextParser.colorize(plugin.getLanguageManager().getMessage("troll.menu-title"));
    }

    public void clearTrollMenuTarget(UUID uuid) {
        trollMenuTargetMap.remove(uuid);
    }

    public UUID getTrollMenuTarget(UUID uuid) {
        return trollMenuTargetMap.get(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (!sender.hasPermission("widcore.troll")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.troll");
            return true;
        }

        if (args.length < 1) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("troll.usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", args[0]));
            return true;
        }

        if (args.length == 2) {
            String trollType = args[1].toLowerCase();
            switch (trollType) {
                case "mlg":
                    executeMlgTroll(sender, target);
                    return true;
                case "rotate":
                    executeRotateTroll(sender, target);
                    return true;
                case "moblook":
                    executeMobLookTroll(sender, target);
                    return true;
                case "creeperchest":
                    executeCreeperChestTroll(sender, target);
                    return true;
            }
        }

        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }
            openTrollMenu((Player) sender, target);
            return true;
        }

        Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("troll.usage"));
        return true;
    }

    private void openTrollMenu(Player sender, Player target) {
        
        updateMenuTitle();
        Inventory trollMenu = Bukkit.createInventory(null, 27, TROLL_MENU_TITLE);

        ItemStack mlgBucket = new ItemStack(Material.WATER_BUCKET);
        ItemMeta mlgMeta = mlgBucket.getItemMeta();
        mlgMeta.setDisplayName(
                TextParser.colorize(plugin.getLanguageManager().getMessage("troll.menu-items.mlg-name")));
        mlgMeta.setLore(getColoredLore("troll.menu-items.mlg-lore"));
        mlgBucket.setItemMeta(mlgMeta);

        ItemStack rotateHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta rotateMeta = (SkullMeta) rotateHead.getItemMeta();
        rotateMeta.setOwningPlayer(target);
        rotateMeta.setDisplayName(
                TextParser.colorize(plugin.getLanguageManager().getMessage("troll.menu-items.rotate-name")));
        rotateMeta.setLore(getColoredLore("troll.menu-items.rotate-lore"));
        rotateHead.setItemMeta(rotateMeta);

        ItemStack mobLookItem = new ItemStack(Material.COW_SPAWN_EGG);
        ItemMeta mobLookMeta = mobLookItem.getItemMeta();
        mobLookMeta.setDisplayName(
                TextParser.colorize(plugin.getLanguageManager().getMessage("troll.menu-items.mob-name")));
        mobLookMeta.setLore(getColoredLore("troll.menu-items.mob-lore"));
        mobLookItem.setItemMeta(mobLookMeta);

        ItemStack creeperChestItem = new ItemStack(Material.CHEST);
        ItemMeta creeperChestMeta = creeperChestItem.getItemMeta();
        creeperChestMeta.setDisplayName(
                TextParser.colorize(plugin.getLanguageManager().getMessage("troll.menu-items.chest-name")));
        creeperChestMeta.setLore(getColoredLore("troll.menu-items.chest-lore"));
        creeperChestItem.setItemMeta(creeperChestMeta);

        trollMenu.setItem(0, mlgBucket);
        trollMenu.setItem(1, rotateHead);
        trollMenu.setItem(2, mobLookItem);
        trollMenu.setItem(3, creeperChestItem);

        trollMenuTargetMap.put(sender.getUniqueId(), target.getUniqueId());
        sender.openInventory(trollMenu);
    }

    private List<String> getColoredLore(String key) {
        List<String> lore = plugin.getLanguageManager().getMessageList(key);
        if (lore == null)
            return new ArrayList<>();
        return lore.stream().map(TextParser::colorize).collect(Collectors.toList());
    }

    public void executeMlgTroll(CommandSender sender, Player target) {
        if (target.getInventory().firstEmpty() == -1) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("general.target-inventory-full"));
            return;
        }
        trollManager.addMlgPlayer(target.getUniqueId());

        ItemStack trollBucket = new ItemStack(Material.WATER_BUCKET);
        ItemMeta meta = trollBucket.getItemMeta();
        meta.setDisplayName(TextParser.colorize(plugin.getLanguageManager().getMessage("troll.mlg-item")));
        meta.getPersistentDataContainer().set(TROLL_BUCKET_KEY, PersistentDataType.BYTE, (byte) 1);
        trollBucket.setItemMeta(meta);

        target.getInventory().addItem(trollBucket);
        Location targetLocation = target.getLocation();
        target.teleportAsync(targetLocation.clone().add(0, 100, 0)).thenAccept(success -> {
            if (success) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("troll.mlg-success")
                        .replace("%player%", target.getName()));
            }
        });
    }

    public void executeRotateTroll(CommandSender sender, Player target) {
        Main.sendMessage(this.plugin, sender,
                plugin.getLanguageManager().getMessage("troll.rotate-success").replace("%player%", target.getName()));

        final int[] counter = { 10 };
        Object[] taskRef = new Object[1];
        taskRef[0] = FoliaScheduler.runAtEntityTimer(plugin, target, () -> {
            if (counter[0] <= 0 || !target.isOnline()) {
                FoliaScheduler.cancelTask(taskRef[0]);
                return;
            }
            Location loc = target.getLocation();
            loc.setYaw((float) (Math.random() * 360) - 180);
            loc.setPitch((float) (Math.random() * 180) - 90);
            target.teleportAsync(loc);
            counter[0]--;
        }, 1L, 10L);
    }

    public void executeMobLookTroll(CommandSender sender, Player target) {
        if (trollManager.isMobLookTrolled(target.getUniqueId())) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("troll.already-active"));
            return;
        }

        Main.sendMessage(this.plugin, sender,
                plugin.getLanguageManager().getMessage("troll.mob-success").replace("%player%", target.getName()));

        final int[] duration = { 600 };
        final boolean[] cancelled = { false };
        Object[] taskRef = new Object[1];
        taskRef[0] = FoliaScheduler.runAtEntityTimer(plugin, target, () -> {
            
            if (cancelled[0]) {
                return;
            }

            if (duration[0] <= 0 || !target.isOnline()) {
                cancelled[0] = true;
                trollManager.removeMobLookTask(target.getUniqueId());
                FoliaScheduler.cancelTask(taskRef[0]);
                return;
            }

            Location loc = target.getLocation();
            for (Entity entity : loc.getWorld().getNearbyEntities(loc, 30, 30, 30)) {
                if (entity instanceof Mob) {
                    Mob mob = (Mob) entity;
                    Location mobLoc = mob.getLocation();
                    Vector direction = target.getLocation().toVector().subtract(mobLoc.toVector());
                    if (direction.lengthSquared() > 0) {
                        mobLoc.setDirection(direction);
                        mob.teleportAsync(mobLoc);
                    }
                    mob.setTarget(target);
                    mob.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, 40, 255, false, false, false));
                }
            }
            duration[0] -= 20;
        }, 1L, 20L);
        trollManager.addMobLookTask(target.getUniqueId(), taskRef[0]);
    }

    public void executeCreeperChestTroll(CommandSender sender, Player target) {
        if (target.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("troll.peaceful-error"));
            return;
        }

        if (!isCreeperSpawnAllowed(target.getWorld())) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("troll.mobspawn-disabled"));
            return;
        }

        if (trollManager.isCreeperChestPlayer(target.getUniqueId())) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("troll.already-active"));
            return;
        }

        Location playerLocation = target.getLocation();
        Location chestLocation = playerLocation.clone()
                .add(playerLocation.getDirection().setY(0).normalize().multiply(2));
        chestLocation.setY(playerLocation.getBlockY());

        Block blockAtLocation = chestLocation.getBlock();
        if (blockAtLocation.getType().isSolid()) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("troll.block-error"));
            return;
        }

        blockAtLocation.setType(Material.CHEST);
        trollManager.addCreeperChestPlayer(target.getUniqueId(), blockAtLocation.getLocation());

        Main.sendMessage(this.plugin, sender,
                plugin.getLanguageManager().getMessage("troll.chest-success").replace("%player%", target.getName()));

        FoliaScheduler.runAtEntityLater(plugin, target, () -> {
            if (!target.isOnline() || !trollManager.isCreeperChestPlayer(target.getUniqueId())) {
                if (blockAtLocation.getType() == Material.CHEST) {
                    blockAtLocation.setType(Material.AIR);
                }
                trollManager.removeCreeperChestPlayer(target.getUniqueId());
            }
        }, 600L);
    }

    private boolean isCreeperSpawnAllowed(World world) {
        
        Boolean mobSpawning = world.getGameRuleValue(GameRule.DO_MOB_SPAWNING);
        if (mobSpawning != null && !mobSpawning) {
            return false;
        }

        if (!plugin.getConfig().getBoolean("features.antimobspawn", false)) {
            return true;
        }

        FileConfiguration cfg = plugin.getConfigManager().getModuleConfig("antimobspawn");
        if (cfg == null) {
            return true; 
        }

        for (String s : cfg.getStringList("all_worlds")) {
            if ("CREEPER".equalsIgnoreCase(s)) {
                return false;
            }
        }
        
        for (String s : cfg.getStringList("worlds." + world.getName())) {
            if ("CREEPER".equalsIgnoreCase(s)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            StringUtil.copyPartialMatches(args[0], playerNames, completions);
        } else if (args.length == 2) {
            StringUtil.copyPartialMatches(args[1], TROLL_TYPES, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}