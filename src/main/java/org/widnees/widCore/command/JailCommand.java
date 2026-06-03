package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.JailManager;
import org.widnees.widCore.manager.PunishmentManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JailCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final PunishmentManager punishmentManager;
    private final JailManager jailManager;

    public JailCommand(Main plugin) {
        this.plugin = plugin;
        this.punishmentManager = plugin.getPunishmentManager();
        this.jailManager = plugin.getJailManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        String commandKey = plugin.getAliasManager().lookupKey(command.getName());

        switch (commandKey) {
            case "setjail":
                return handleSetJail(sender, args);
            case "deljail":
                return handleDelJail(sender, args);
            case "jail":
                return handleJail(sender, args);
            case "unjail":
                return handleUnjail(sender, args);
        }
        return false;
    }

    private boolean handleSetJail(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
            return true;
        }
        if (!player.hasPermission("widcore.jail.set")) {
            Main.sendNoPermission(this.plugin, player, "widcore.jail.set");
            return true;
        }
        if (args.length == 0) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("jail.set-usage"));
            return true;
        }

        String jailName = args[0];
        jailManager.startSetupSession(player, jailName);
        return true;
    }

    private boolean handleDelJail(CommandSender sender, String[] args) {
        if (!sender.hasPermission("widcore.jail.delete")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.jail.delete");
            return true;
        }
        if (args.length == 0) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("jail.deljail-usage"));
            return true;
        }

        String jailName = args[0];
        if (!jailManager.jailExists(jailName)) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("jail.not-found").replace("%jail%", jailName));
            return true;
        }

        jailManager.deleteJail(jailName);
        Main.sendMessage(this.plugin, sender,
                plugin.getLanguageManager().getMessage("jail.deleted").replace("%jail%", jailName));
        return true;
    }

    private boolean handleJail(CommandSender sender, String[] args) {
        if (!sender.hasPermission("widcore.jail.use")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.jail.use");
            return true;
        }
        if (args.length < 2) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("jail.usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("general.player-not-found").replace("%player%", args[0]));
            return true;
        }

        String jailName = jailManager.getEmptiestJail();
        if (jailName == null) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("jail.no-jails"));
            return true;
        }

        Location jailSpawn = jailManager.getJailSpawn(jailName);
        if (jailSpawn == null) {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("jail.not-found").replace("%jail%", jailName));
            return true;
        }

        String durationStr = args[1];
        long duration;
        if (durationStr.equals("-1")) {
            duration = -1L;
        } else {
            duration = punishmentManager.parseDuration(durationStr);
            if (duration <= 0) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.invalid-number"));
                return true;
            }
        }

        String reason = (args.length > 2) ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : plugin.getLanguageManager().getMessage("punishment.default-reason");
        Location returnLocation = target.getLocation();

        punishmentManager.jailPlayer(target, sender, jailName.toLowerCase(), duration, reason, returnLocation);

        target.teleportAsync(jailSpawn).thenAccept(success -> {
            if (success) {
                target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
            }
        });

        String durationMsg = (duration == -1) ? plugin.getLanguageManager().getMessage("freeze.status-perm")
                : punishmentManager.formatDuration(duration);

        String targetMsg = plugin.getLanguageManager().getMessage("jail.message")
                .replace("%duration%", durationMsg)
                .replace("%jail%", jailName);
        Main.sendMessage(plugin, target, targetMsg);

        String senderMsg = plugin.getLanguageManager().getMessage("jail.sent")
                .replace("%player%", target.getName())
                .replace("%duration%", durationMsg)
                .replace("%jail%", jailName);
        Main.sendMessage(this.plugin, sender, senderMsg);
        return true;
    }

    private boolean handleUnjail(CommandSender sender, String[] args) {
        if (!sender.hasPermission("widcore.jail.remove")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.jail.remove");
            return true;
        }
        if (args.length != 1) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("jail.unjail-usage"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            if (punishmentManager.getJailEntry(target.getUniqueId()) == null) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.player-not-found")
                        .replace("%player%", args[0]));
                return true;
            }
        }

        Location returnLocation = punishmentManager.unjailPlayer(target.getUniqueId());

        if (returnLocation != null) {
            if (target.isOnline()) {
                Player targetOnline = target.getPlayer();
                targetOnline.teleportAsync(returnLocation).thenAccept(success -> {
                    if (success) {
                        targetOnline.playSound(targetOnline.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        Main.sendMessage(plugin, targetOnline,
                                plugin.getLanguageManager().getMessage("jail.released-target"));
                    }
                });
            }
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("jail.released-sender")
                    .replace("%player%", target.getName()));
        } else {
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("jail.not-in-jail").replace("%player%", target.getName()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        String commandKey = plugin.getAliasManager().lookupKey(command.getName());
        List<String> completions = new ArrayList<>();

        if (commandKey.equals("jail")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0],
                        Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()),
                        completions);
            }
        } else if (commandKey.equals("unjail")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], punishmentManager.getJailedPlayerNames(), completions);
            }
        } else if (commandKey.equals("deljail")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], new ArrayList<>(jailManager.getJailNames()), completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
        @SuppressWarnings("unused")
    private static final String _0xW7e1a9 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}