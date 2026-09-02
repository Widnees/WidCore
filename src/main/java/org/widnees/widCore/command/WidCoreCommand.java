package org.widnees.widCore.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WidCoreCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final MigrateCommand migrateCommand;

    public WidCoreCommand(Main plugin) {
        this.plugin = plugin;
        this.migrateCommand = new MigrateCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("migrate")) {
            if (!sender.hasPermission("widcore.migrate")) {
                Main.sendNoPermission(this.plugin, sender, "widcore.migrate");
                return true;
            }
            String[] subArgs = args.length > 1
                    ? java.util.Arrays.copyOfRange(args, 1, args.length)
                    : new String[0];
            return migrateCommand.onCommand(sender, command, label, subArgs);
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("widcore.reload")) {
                Main.sendNoPermission(this.plugin, sender, "widcore.reload");
                return true;
            }

            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.reload-start"));
            plugin.reloadPlugin();
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.reload-success"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("debug")) {
            if (!sender.hasPermission("widcore.debug")) {
                Main.sendNoPermission(this.plugin, sender, "widcore.debug");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /widcore debug <player> <command>");
                return true;
            }
            org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            String testCmd = args[2];
            org.widnees.widCore.manager.CommandAccessManager cam = org.widnees.widCore.manager.CommandAccessManager.getInstance();
            if (cam == null) {
                sender.sendMessage("§cCommandAccessManager is null!");
                return true;
            }

            String resolved = cam.resolveGroup(target);
            boolean exec = cam.isExecutionAllowed(target, testCmd, java.util.Collections.emptyList());
            boolean tab = cam.isRootVisible(target, testCmd);

            sender.sendMessage("§a[WidCore Debug] §7Player: §f" + target.getName());
            sender.sendMessage("§a[WidCore Debug] §7Resolved Group: §f" + resolved);
            sender.sendMessage("§a[WidCore Debug] §7Command: §f" + testCmd);
            sender.sendMessage("§a[WidCore Debug] §7Execution Allowed: " + (exec ? "§aYes" : "§cNo"));
            sender.sendMessage("§a[WidCore Debug] §7Tab Visible: " + (tab ? "§aYes" : "§cNo"));
            return true;
        }

        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.invalid-number"));
                return true;
            }
        }

        plugin.getHelpMenuManager().showHelpPage(sender, page);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> opts = Arrays.asList("reload", "debug", "migrate");
            org.bukkit.util.StringUtil.copyPartialMatches(args[0], opts, completions);
        } else if (args.length > 1 && args[0].equalsIgnoreCase("migrate")) {
            String[] subArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
            List<String> sub = migrateCommand.onTabComplete(sender, command, alias, subArgs);
            if (sub != null) completions.addAll(sub);
        }
        java.util.Collections.sort(completions);
        return completions;
    }

        @SuppressWarnings("unused")
    private static final String _0xCr3a7F = "\u0077\u0031\u0064\u006e\u0065\u0065\u0073";

}