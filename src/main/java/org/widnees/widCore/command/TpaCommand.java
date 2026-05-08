package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.TpaManager;

import java.util.Collections;
import java.util.List;

public class TpaCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final TpaManager tpaManager;

    public TpaCommand(Main plugin) {
        this.plugin = plugin;
        this.tpaManager = plugin.getTpaManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded())
            return true;

        if (!(sender instanceof Player)) {
            Main.sendMessage(plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
            return true;
        }

        Player player = (Player) sender;
        
        String commandKey = plugin.getAliasManager().lookupKey(command.getName());

        switch (commandKey) {
            case "tpa":
                handleTpaRequest(player, args, "tpa", "widcore.tpa.send");
                break;
            case "tpahere":
                handleTpaRequest(player, args, "tpahere", "widcore.tpa.send");
                break;
            case "tpaaccept":
                handleResponse(player, args, true);
                break;
            case "tpadeny":
                handleResponse(player, args, false);
                break;
            case "tpatoggle":
                if (!player.hasPermission("widcore.tpa.autoaccept")) {
                    Main.sendNoPermission(plugin, player, "widcore.tpa.autoaccept");
                    return true;
                }
                tpaManager.toggleAutoAccept(player);
                break;
        }
        return true;
    }

    private void handleTpaRequest(Player player, String[] args, String type, String perm) {
        if (!player.hasPermission(perm)) {
            Main.sendNoPermission(plugin, player, perm);
            return;
        }
        if (args.length < 1) {
            Main.sendMessage(plugin, player,
                    plugin.getLanguageManager().getMessage("tpa.usage").replace("%cmd%", type));
            return;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            Main.sendMessage(plugin, player,
                    plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", args[0]));
            return;
        }
        if (target.equals(player)) {
            Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("tpa.self-request"));
            return;
        }
        tpaManager.sendTpaRequest(player, target, type);
    }

    private void handleResponse(Player player, String[] args, boolean accept) {
        String perm = accept ? "widcore.tpa.accept" : "widcore.tpa.deny";
        if (!player.hasPermission(perm)) {
            Main.sendNoPermission(plugin, player, perm);
            return;
        }

        if (args.length > 0) {
            Player requester = Bukkit.getPlayer(args[0]);
            if (requester == null) {
                Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("general.player-not-found")
                        .replace("%player%", args[0]));
                return;
            }
            if (accept)
                tpaManager.acceptTpaRequest(player, requester);
            else
                tpaManager.denyTpaRequest(player, requester);
        } else {
            if (!accept) {
                tpaManager.denyAllRequests(player);
            } else {
                TpaManager.TpaRequest request = tpaManager.getRequest(player.getUniqueId());
                if (request != null) {
                    Player req = Bukkit.getPlayer(request.requesterId);
                    if (req != null)
                        tpaManager.acceptTpaRequest(player, req);
                } else {
                    Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("tpa.no-request"));
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
        }
        return Collections.emptyList();
    }
}