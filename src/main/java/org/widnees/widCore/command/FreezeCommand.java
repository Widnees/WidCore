package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.PunishmentManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FreezeCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final PunishmentManager punishmentManager;

    public FreezeCommand(Main plugin, PunishmentManager punishmentManager) {
        this.plugin = plugin;
        this.punishmentManager = punishmentManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (!sender.hasPermission("widcore.freeze")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.freeze");
            return true;
        }

        String commandKey = plugin.getAliasManager().lookupKey(command.getName());

        if (commandKey.equals("freeze")) {
            return handleFreeze(sender, label, args);
        } else if (commandKey.equals("unfreeze")) {
            return handleUnfreeze(sender, label, args);
        }

        return false;
    }

    private boolean handleFreeze(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("punishment.freeze.usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", args[0]));
            return true;
        }

        if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId())) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("punishment.freeze.self-freeze"));
            return true;
        }

        if (punishmentManager.isFrozen(target.getUniqueId())) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("punishment.freeze.already-frozen"));
            return true;
        }

        String reason = plugin.getLanguageManager().getMessage("punishment.freeze.default-reason");

        if (args.length == 1) {
            if (!sender.hasPermission("widcore.freeze.permanent")) {
                Main.sendNoPermission(this.plugin, sender, "widcore.freeze.permanent");
                return true;
            }
            punishmentManager.freezePlayer(target, sender, -1L, reason);

            String senderMsg = plugin.getLanguageManager().getMessage("punishment.freeze.frozen-sender")
                    .replace("%player%", target.getName())
                    .replace("%duration%", "süresiz");
            Main.sendMessage(this.plugin, sender, senderMsg);

            String targetMsg = plugin.getLanguageManager().getMessage("punishment.freeze.frozen-target")
                    .replace("%duration%", "süresiz");
            Main.sendMessage(plugin, target, targetMsg);

        } else {
            if (!sender.hasPermission("widcore.freeze.temp")) {
                Main.sendNoPermission(this.plugin, sender, "widcore.freeze.temp");
                return true;
            }

            long durationMillis = punishmentManager.parseDuration(args[1]);
            if (durationMillis <= 0) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.invalid-number"));
                return true;
            }

            punishmentManager.freezePlayer(target, sender, durationMillis, reason + " (" + args[1] + ")");
            String formattedDuration = punishmentManager.formatDuration(durationMillis);

            String senderMsg = plugin.getLanguageManager().getMessage("punishment.freeze.frozen-sender")
                    .replace("%player%", target.getName())
                    .replace("%duration%", formattedDuration);
            Main.sendMessage(this.plugin, sender, senderMsg);

            String targetMsg = plugin.getLanguageManager().getMessage("punishment.freeze.frozen-target")
                    .replace("%duration%", formattedDuration);
            Main.sendMessage(plugin, target, targetMsg);
        }

        return true;
    }

    private boolean handleUnfreeze(CommandSender sender, String label, String[] args) {
        if (args.length != 1) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("punishment.freeze.unfreeze-usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", args[0]));
            return true;
        }

        if (!punishmentManager.isFrozen(target.getUniqueId())) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("punishment.freeze.not-frozen"));
            return true;
        }

        punishmentManager.unfreezePlayer(target.getUniqueId());

        String senderMsg = plugin.getLanguageManager().getMessage("punishment.freeze.unfrozen-sender")
                .replace("%player%", target.getName());
        Main.sendMessage(this.plugin, sender, senderMsg);

        Main.sendMessage(plugin, target, plugin.getLanguageManager().getMessage("punishment.freeze.unfrozen-target"));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
        @SuppressWarnings("unused")
    private static final String _0xWd3f9b = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}
