package org.widnees.widCore.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.listener.HomeListener;
import org.widnees.widCore.manager.HomeManager;

public class HomeCommand implements CommandExecutor {

    private final Main plugin;
    private final HomeManager homeManager;
    private final HomeListener homeListener;

    public HomeCommand(Main plugin, HomeListener homeListener) {
        this.plugin = plugin;
        this.homeManager = plugin.getHomeManager();
        this.homeListener = homeListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
            return true;
        }

        Player player = (Player) sender;

        String cmdName = plugin.getAliasManager().lookupKey(command.getName());

        switch (cmdName) {
            case "home":
                handleHome(player, args);
                break;
            case "sethome":
                handleSetHome(player, args);
                break;
            case "delhome":
                handleDelHome(player, args);
                break;
        }

        return true;
    }

    private void handleHome(Player player, String[] args) {
        if (!player.hasPermission("widcore.home.teleport")) {
            Main.sendNoPermission(plugin, player, "widcore.home.teleport");
            return;
        }

        if (args.length == 0) {

            homeListener.openHomeMenu(player);
        } else {

            String homeName = args[0];
            homeManager.getHome(player.getUniqueId(), homeName).thenAccept(location -> {
                if (location == null) {
                    Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("home.not-found")
                            .replace("%home%", homeName));
                    return;
                }

                homeManager.teleportWithDelay(player, location, homeName,
                        () -> {

                            Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("home.success")
                                    .replace("%home%", homeName));
                        },
                        null 
                );
            });
        }
    }

    private void handleSetHome(Player player, String[] args) {
        if (!player.hasPermission("widcore.home.create")) {
            Main.sendNoPermission(plugin, player, "widcore.home.create");
            return;
        }

        if (args.length == 0) {
            Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("home.usage-sethome"));
            return;
        }

        String homeName = args[0];

        if (!homeManager.isValidHomeName(homeName)) {
            Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("home.invalid-name"));
            return;
        }

        String worldName = player.getWorld().getName();
        if (homeManager.isWorldBanned(worldName)) {
            Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("home.banned-world"));
            return;
        }

        Location location = player.getLocation();

        homeManager.setHome(player, homeName, location).thenAccept(result -> {
            switch (result) {
                case HomeManager.SET_HOME_SUCCESS:
                    Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("home.set")
                            .replace("%home%", homeName));
                    break;
                case HomeManager.SET_HOME_LIMIT_REACHED:
                    int maxHomes = homeManager.getMaxHomes(player);
                    Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("home.limit-reached")
                            .replace("%limit%", String.valueOf(maxHomes)));
                    break;
                case HomeManager.SET_HOME_ALREADY_EXISTS:
                    Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("home.already-exists")
                            .replace("%home%", homeName));
                    break;
            }
        });
    }

    private void handleDelHome(Player player, String[] args) {
        if (!player.hasPermission("widcore.home.delete")) {
            Main.sendNoPermission(plugin, player, "widcore.home.delete");
            return;
        }

        if (args.length == 0) {
            Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("home.usage-delhome"));
            return;
        }

        String homeName = args[0];

        homeManager.getHome(player.getUniqueId(), homeName).thenAccept(location -> {
            if (location == null) {
                Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("home.not-found")
                        .replace("%home%", homeName));
                return;
            }

            homeListener.openDeleteConfirmMenu(player, homeName);
        });
    }
}
