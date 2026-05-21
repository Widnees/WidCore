package org.widnees.widCore.command;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.widnees.widCore.Main;
import org.widnees.widCore.generator.EmptyChunkGenerator;
import org.widnees.widCore.generator.SingleBiomeProvider;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.WorldDataManager;
import org.widnees.widCore.manager.WorldManagerGUI;
import org.widnees.widCore.util.FoliaScheduler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WorldManagerCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final WorldDataManager worldDataManager;
    private WorldManagerGUI worldManagerGUI;

    public WorldManagerCommand(Main plugin, WorldDataManager worldDataManager) {
        this.plugin = plugin;
        this.worldDataManager = worldDataManager;
    }

    public void setWorldManagerGUI(WorldManagerGUI gui) {
        this.worldManagerGUI = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                if (FoliaScheduler.isFolia()) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("worldmanager.folia-unsupported"));
                    return true;
                }
                handleCreateCommand(sender, args);
                break;
            case "delete":
                if (FoliaScheduler.isFolia()) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("worldmanager.folia-unsupported"));
                    return true;
                }
                handleDeleteCommand(sender, args);
                break;
            case "load":
                if (FoliaScheduler.isFolia()) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("worldmanager.folia-unsupported"));
                    return true;
                }
                handleLoadCommand(sender, args);
                break;
            case "unload":
                if (FoliaScheduler.isFolia()) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("worldmanager.folia-unsupported"));
                    return true;
                }
                handleUnloadCommand(sender, args);
                break;
            case "tp":
                if (!(sender instanceof Player)) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("general.only-players"));
                    return true;
                }
                handleTeleportCommand((Player) sender, args);
                break;
            case "setspawn":
                if (!(sender instanceof Player)) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("general.only-players"));
                    return true;
                }
                handleSetSpawnCommand((Player) sender);
                break;
            case "settings":
                if (!(sender instanceof Player)) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("general.only-players"));
                    return true;
                }
                handleSettingsCommand((Player) sender);
                break;
            case "list":
                handleListCommand(sender);
                break;
            case "info":
                handleInfoCommand(sender, args);
                break;
            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    private void handleCreateCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("widcore.worldmanager.create")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.worldmanager.create");
            return;
        }

        if (args.length < 4) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("worldmanager.create-usage"));
            return;
        }

        String worldName = args[1];

        if (!worldName.matches("[a-zA-Z0-9._-]+")) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("worldmanager.invalid-name"));
            return;
        }

        if (Bukkit.getWorld(worldName) != null) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("worldmanager.world-exists").replace("%world%", worldName));
            return;
        }

        World.Environment environment;
        try {
            environment = World.Environment.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("worldmanager.invalid-env"));
            return;
        }

        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(environment);

        String worldType = args[3].toLowerCase();
        Biome selectedBiome = null;

        if (args.length >= 5) {
            try {
                selectedBiome = Biome.valueOf(args[4].toUpperCase());
            } catch (IllegalArgumentException e) {
                Main.sendMessage(this.plugin, sender,
                        plugin.getLanguageManager().getMessage("worldmanager.invalid-biome"));
                return;
            }
        }

        switch (worldType) {
            case "flat":

                if (environment != World.Environment.NORMAL) {
                    Main.sendMessage(this.plugin, sender,
                            plugin.getLanguageManager().getMessage("worldmanager.flat-env-error"));
                    return;
                }

                creator.type(WorldType.FLAT);
                String biomeKey = selectedBiome != null ? selectedBiome.getKey().getKey() : "plains";
                String layersJson = "{\"biome\":\"minecraft:" + biomeKey
                        + "\",\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:dirt\",\"height\":2},{\"block\":\"minecraft:grass_block\",\"height\":1}]}";
                creator.generatorSettings(layersJson);
                break;
            case "empty":
                creator.generator(new EmptyChunkGenerator());
                if (selectedBiome != null)
                    creator.biomeProvider(new SingleBiomeProvider(selectedBiome));
                break;
            case "normal":
                if (selectedBiome != null)
                    creator.biomeProvider(new SingleBiomeProvider(selectedBiome));
                break;
            default:
                Main.sendMessage(this.plugin, sender,
                        plugin.getLanguageManager().getMessage("worldmanager.invalid-type"));
                return;
        }

        Main.sendMessage(this.plugin, sender,
                plugin.getLanguageManager().getMessage("worldmanager.creating").replace("%world%", worldName));
        World newWorld = Bukkit.createWorld(creator);

        if (newWorld != null) {
            worldDataManager.saveWorldData(worldName, creator, worldType, selectedBiome);
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("worldmanager.create-success")
                    .replace("%world%", worldName));
        } else {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("worldmanager.create-fail").replace("%world%", worldName));
        }
    }

    private void handleDeleteCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("widcore.worldmanager.delete")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.worldmanager.delete");
            return;
        }
        if (args.length != 2) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("worldmanager.delete-usage"));
            return;
        }
        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("worldmanager.world-not-found")
                    .replace("%world%", worldName));
            return;
        }
        if (Bukkit.getWorlds().get(0).equals(world)) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("worldmanager.main-world-error"));
            return;
        }

        World mainWorld = Bukkit.getWorlds().get(0);
        for (Player p : world.getPlayers()) {
            p.teleportAsync(mainWorld.getSpawnLocation());
            Main.sendMessage(plugin, p, plugin.getLanguageManager().getMessage("worldmanager.moved-to-spawn"));
        }

        Main.sendMessage(this.plugin, sender,
                plugin.getLanguageManager().getMessage("worldmanager.deleting").replace("%world%", worldName));
        Bukkit.unloadWorld(world, false);
        worldDataManager.deleteWorldData(worldName);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                deleteDirectory(world.getWorldFolder().toPath());
                Bukkit.getScheduler().runTask(plugin, () -> Main.sendMessage(this.plugin, sender, plugin
                        .getLanguageManager().getMessage("worldmanager.delete-success").replace("%world%", worldName)));
            } catch (IOException e) {
                Bukkit.getScheduler().runTask(plugin, () -> Main.sendMessage(this.plugin, sender, plugin
                        .getLanguageManager().getMessage("worldmanager.delete-fail").replace("%world%", worldName)));
                e.printStackTrace();
            }
        });
    }

    private void handleLoadCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("widcore.worldmanager.load")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.worldmanager.load");
            return;
        }
        if (args.length != 2) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("worldmanager.load-usage"));
            return;
        }
        String worldName = args[1];
        if (Bukkit.getWorld(worldName) != null) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("worldmanager.world-exists"));
            return;
        }
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (!worldFolder.exists() || !new File(worldFolder, "level.dat").exists()) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager()
                    .getMessage("worldmanager.folder-not-found").replace("%world%", worldName));
            return;
        }

        Main.sendMessage(this.plugin, sender,
                plugin.getLanguageManager().getMessage("worldmanager.loading").replace("%world%", worldName));

        String savedGeneratorType = worldDataManager.getGeneratorTypeForWorld(worldName);
        String savedBiome = worldDataManager.getBiomeForWorld(worldName);

        WorldCreator creator = new WorldCreator(worldName);

        if (savedGeneratorType != null) {
            switch (savedGeneratorType.toUpperCase()) {
                case "EMPTY":
                    creator.generator(new EmptyChunkGenerator());
                    if (savedBiome != null && !savedBiome.isEmpty()) {
                        try {
                            Biome biome = Biome.valueOf(savedBiome);
                            creator.biomeProvider(new SingleBiomeProvider(biome));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    break;
                case "FLAT":
                    creator.type(WorldType.FLAT);
                    break;
                case "NORMAL":
                default:
                    if (savedBiome != null && !savedBiome.isEmpty()) {
                        try {
                            Biome biome = Biome.valueOf(savedBiome);
                            creator.biomeProvider(new SingleBiomeProvider(biome));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    break;
            }
        }

        World newWorld = Bukkit.createWorld(creator);

        if (newWorld != null) {

            if (savedGeneratorType == null) {
                creator.environment(newWorld.getEnvironment());
                worldDataManager.saveWorldData(worldName, creator, "NORMAL", null);
            }
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("worldmanager.load-success").replace("%world%", worldName));
        } else {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("worldmanager.load-fail"));
        }
    }

    private void handleUnloadCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("widcore.worldmanager.unload")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.worldmanager.unload");
            return;
        }
        if (args.length != 2) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("worldmanager.unload-usage"));
            return;
        }
        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("worldmanager.world-not-found")
                    .replace("%world%", worldName));
            return;
        }
        if (Bukkit.getWorlds().get(0).equals(world)) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("worldmanager.main-world-error"));
            return;
        }

        World mainWorld = Bukkit.getWorlds().get(0);
        for (Player p : world.getPlayers()) {
            p.teleportAsync(mainWorld.getSpawnLocation());
            Main.sendMessage(plugin, p, plugin.getLanguageManager().getMessage("worldmanager.moved-to-spawn"));
        }

        worldDataManager.saveGameRules(worldName, world);

        boolean success = Bukkit.unloadWorld(world, true);
        if (success) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("worldmanager.unload-success")
                    .replace("%world%", worldName));
        } else {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("worldmanager.unload-fail").replace("%world%", worldName));
        }
    }

    private void handleSetSpawnCommand(Player player) {
        if (!player.hasPermission("widcore.worldmanager.setspawn")) {
            Main.sendNoPermission(this.plugin, player, "widcore.worldmanager.setspawn");
            return;
        }
        World world = player.getWorld();
        world.setSpawnLocation(player.getLocation());
        worldDataManager.setWorldSpawn(world.getName(), player.getLocation());
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("worldmanager.setspawn-success")
                .replace("%world%", world.getName()));
    }

    private void handleTeleportCommand(Player player, String[] args) {
        if (!player.hasPermission("widcore.worldmanager.tp")) {
            Main.sendNoPermission(this.plugin, player, "widcore.worldmanager.tp");
            return;
        }
        if (args.length != 2) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("worldmanager.tp-usage"));
            return;
        }
        World targetWorld = Bukkit.getWorld(args[1]);
        if (targetWorld == null) {
            Main.sendMessage(this.plugin, player,
                    plugin.getLanguageManager().getMessage("worldmanager.world-not-found").replace("%world%", args[1]));
            return;
        }
        player.teleportAsync(targetWorld.getSpawnLocation()).thenAccept(success -> {
            if (success) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("worldmanager.tp-success")
                        .replace("%world%", targetWorld.getName()));
            }
        });
    }

    private void handleSettingsCommand(Player player) {
        if (!player.hasPermission("widcore.worldmanager.settings")) {
            Main.sendNoPermission(this.plugin, player, "widcore.worldmanager.settings");
            return;
        }

        if (worldManagerGUI == null) {
            Main.sendMessage(this.plugin, player, "&cWorldManager GUI not initialized.");
            return;
        }

        World world = player.getWorld();
        worldManagerGUI.openGameRuleMenu(player, world);
    }

    private void handleListCommand(CommandSender sender) {
        if (!sender.hasPermission("widcore.worldmanager.list")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.worldmanager.list");
            return;
        }

        Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("worldmanager.list-header"));

        for (World world : Bukkit.getWorlds()) {
            String env = world.getEnvironment().name();
            String item = plugin.getLanguageManager().getMessage("worldmanager.list-item")
                    .replace("%world%", world.getName())
                    .replace("%env%", env);
            Main.sendMessage(this.plugin, sender, item);
        }
    }

    private void handleInfoCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("widcore.worldmanager.info")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.worldmanager.info");
            return;
        }

        World world;
        if (args.length >= 2) {
            world = Bukkit.getWorld(args[1]);
            if (world == null) {
                Main.sendMessage(this.plugin, sender,
                        plugin.getLanguageManager().getMessage("worldmanager.world-not-found").replace("%world%",
                                args[1]));
                return;
            }
        } else {
            if (!(sender instanceof Player)) {
                Main.sendMessage(this.plugin, sender,
                        plugin.getLanguageManager().getMessage("general.only-players"));
                return;
            }
            world = ((Player) sender).getWorld();
        }

        String generatorType = worldDataManager.getGeneratorTypeForWorld(world.getName());
        String biome = worldDataManager.getBiomeForWorld(world.getName());

        Main.sendMessage(this.plugin, sender,
                plugin.getLanguageManager().getMessage("worldmanager.info-header")
                        .replace("%world%", world.getName()));
        Main.sendMessage(this.plugin, sender,
                plugin.getLanguageManager().getMessage("worldmanager.info-generator")
                        .replace("%type%", generatorType != null ? generatorType : "UNKNOWN"));
        Main.sendMessage(this.plugin, sender,
                plugin.getLanguageManager().getMessage("worldmanager.info-biome")
                        .replace("%biome%", biome != null ? biome : "DEFAULT"));
        Main.sendMessage(this.plugin, sender,
                plugin.getLanguageManager().getMessage("worldmanager.info-players")
                        .replace("%count%", String.valueOf(world.getPlayers().size())));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        String currentArg = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            completions.addAll(
                    Arrays.asList("create", "delete", "load", "unload", "tp", "setspawn", "settings", "list", "info"));
        } else {
            String sub = args[0].toLowerCase();
            if (args.length == 2) {
                if (sub.equals("tp") || sub.equals("unload") || sub.equals("delete") || sub.equals("info")) {
                    completions.addAll(Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList()));
                } else if (sub.equals("load")) {
                    File[] worldFolders = Bukkit.getWorldContainer().listFiles(File::isDirectory);
                    if (worldFolders != null) {
                        for (File folder : worldFolders) {
                            if (new File(folder, "level.dat").exists() && Bukkit.getWorld(folder.getName()) == null) {
                                completions.add(folder.getName());
                            }
                        }
                    }
                }
            } else if (sub.equals("create")) {
                if (args.length == 3)
                    completions.addAll(Arrays.asList("NORMAL", "NETHER", "THE_END"));
                else if (args.length == 4)
                    completions.addAll(Arrays.asList("normal", "flat", "empty"));
                else if (args.length == 5) {

                    completions.addAll(Arrays.stream(Biome.values()).map(b -> b.name().toLowerCase())
                            .collect(Collectors.toList()));
                }
            }
        }
        return completions.stream().filter(s -> s.toLowerCase().startsWith(currentArg)).collect(Collectors.toList());
    }

    private void deleteDirectory(Path path) throws IOException {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    private void sendUsage(CommandSender sender) {
        Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("worldmanager.usage"));
    }
}