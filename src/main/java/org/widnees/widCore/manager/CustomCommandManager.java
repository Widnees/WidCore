package org.widnees.widCore.manager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.TextParser;
import org.widnees.widCore.util.FoliaScheduler;

public class CustomCommandManager {
    private final Main plugin;
    private final List<DynamicCommand> registeredCommands = new ArrayList<DynamicCommand>();
    private CommandMap commandMap;
    private final Map<UUID, Set<String>> executingCommands = new ConcurrentHashMap<UUID, Set<String>>();

    public CustomCommandManager(Main plugin) {
        this.plugin = plugin;
        this.setupCommandMap();
        this.loadCommands();
    }

    private void setupCommandMap() {
        try {
            this.commandMap = Bukkit.getCommandMap();
        }
        catch (NoSuchMethodError e) {
            try {
                Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
                commandMapField.setAccessible(true);
                this.commandMap = (CommandMap)commandMapField.get(Bukkit.getServer());
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void loadCommands() {
        FileConfiguration config = this.plugin.getConfigManager().getModuleConfig("customcommand");
        ConfigurationSection commandsSection = config.getConfigurationSection("commands");
        if (commandsSection == null) {
            return;
        }
        for (String commandName : commandsSection.getKeys(false)) {
            if (this.commandMap.getCommand(commandName) != null) continue;
            List aliases = config.getStringList("commands." + commandName + ".aliases");
            boolean permissionRequired = config.getBoolean("commands." + commandName + ".permission", false);
            List messages = config.getStringList("commands." + commandName + ".actions.MESSAGE");
            List playerCommands = config.getStringList("commands." + commandName + ".actions.P_COMMAND");
            List consoleCommands = config.getStringList("commands." + commandName + ".actions.C_COMMAND");
            DynamicCommand dynCmd = new DynamicCommand(commandName, aliases, permissionRequired, messages, playerCommands, consoleCommands);
            if (this.commandMap == null) continue;
            this.commandMap.register(this.plugin.getName(), (Command)dynCmd);
            this.registeredCommands.add(dynCmd);
        }
    }

    public void unloadCommands() {
        if (this.commandMap == null) {
            return;
        }
        try {
            Map knownCommands = null;
            if (this.commandMap instanceof SimpleCommandMap) {
                knownCommands = ((SimpleCommandMap)this.commandMap).getKnownCommands();
            }
            if (knownCommands == null) {
                try {
                    Map result;
                    Method getKnownCommandsMethod = this.commandMap.getClass().getMethod("getKnownCommands", new Class[0]);
                    knownCommands = result = (Map)getKnownCommandsMethod.invoke((Object)this.commandMap, new Object[0]);
                }
                catch (NoSuchMethodException getKnownCommandsMethod) {

                }
            }
            for (DynamicCommand cmd : this.registeredCommands) {
                if (knownCommands != null) {
                    knownCommands.remove(cmd.getName());
                    knownCommands.remove(String.valueOf(this.plugin.getName().toLowerCase()) + ":" + cmd.getName());
                    for (String alias : cmd.getAliases()) {
                        knownCommands.remove(alias);
                        knownCommands.remove(String.valueOf(this.plugin.getName().toLowerCase()) + ":" + alias);
                    }
                }
                cmd.unregister(this.commandMap);
            }
            this.registeredCommands.clear();
        }
        catch (Exception e) {
            this.plugin.getLogger().warning("Failed to unregister custom commands: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private class DynamicCommand
    extends Command {
        private final boolean permissionRequired;
        private final List<String> messages;
        private final List<String> playerCommands;
        private final List<String> consoleCommands;

        protected DynamicCommand(String name, List<String> aliases, boolean permissionRequired, List<String> messages, List<String> playerCommands, List<String> consoleCommands) {
            super(name);
            if (aliases != null && !aliases.isEmpty()) {
                this.setAliases(aliases);
            }
            this.permissionRequired = permissionRequired;
            this.messages = messages;
            this.playerCommands = playerCommands;
            this.consoleCommands = consoleCommands;
        }

        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            if (!(sender instanceof Player)) {
                return true;
            }
            Player player = (Player)sender;
            UUID playerId = player.getUniqueId();
            String cmdName = this.getName().toLowerCase();
            Set playerExecuting = CustomCommandManager.this.executingCommands.computeIfAbsent(playerId, k -> new HashSet());
            if (playerExecuting.contains(cmdName)) {
                CustomCommandManager.this.plugin.getLogger().warning("Prevented recursive execution of command '" + cmdName + "' for player " + player.getName());
                return true;
            }
            playerExecuting.add(cmdName);
            try {
                if (this.permissionRequired && !player.hasPermission("widcore.customcommand." + this.getName())) {
                    Main.sendMessage(CustomCommandManager.this.plugin, (CommandSender)player, CustomCommandManager.this.plugin.getLanguageManager().getMessage("general.no-permission"));
                    return true;
                }
                if (this.messages != null && !this.messages.isEmpty()) {
                    StringBuilder messageBuilder = new StringBuilder();
                    int i = 0;
                    while (i < this.messages.size()) {
                        String msg = this.messages.get(i);
                        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                            msg = PlaceholderAPI.setPlaceholders((Player)player, (String)msg);
                        }
                        msg = msg.replace("%player%", player.getName());
                        messageBuilder.append(msg);
                        if (i < this.messages.size() - 1) {
                            messageBuilder.append("\n");
                        }
                        ++i;
                    }
                    player.sendMessage(TextParser.colorize(messageBuilder.toString()));
                }
                if (this.playerCommands != null) {
                    for (String cmd : this.playerCommands) {
                        String finalCmd = cmd.replace("%player%", player.getName());
                        if (finalCmd.startsWith("/")) {
                            finalCmd = finalCmd.substring(1);
                        }
                        if (finalCmd.equalsIgnoreCase(cmdName) || finalCmd.toLowerCase().startsWith(String.valueOf(cmdName) + " ")) {
                            CustomCommandManager.this.plugin.getLogger().warning("Skipping self-referencing P_COMMAND '" + finalCmd + "' in command '" + cmdName + "'");
                            continue;
                        }
                        player.performCommand(finalCmd);
                    }
                }
                if (this.consoleCommands != null) {
                    ArrayList<String> commandsToRun = new ArrayList<String>();
                    for (String cmd : this.consoleCommands) {
                        String finalCmd = cmd.replace("%player%", player.getName());
                        if (finalCmd.startsWith("/")) {
                            finalCmd = finalCmd.substring(1);
                        }
                        commandsToRun.add(finalCmd);
                    }
                    FoliaScheduler.runTask((Plugin)CustomCommandManager.this.plugin, () -> {
                        for (String cmd : commandsToRun) {
                            Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)cmd);
                        }
                    });
                }
                return true;
            }
            finally {
                playerExecuting.remove(cmdName);
            }
        }
    }
        @SuppressWarnings("unused")
    private static final String _xN3e7W1 = "\u0077" + "\u0069\u0064\u006e\u0065" + "\u0065\u0073";

}
