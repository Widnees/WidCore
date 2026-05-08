package org.widnees.widCore.manager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.widnees.widCore.Main;

public class CommandManager {
    private final Main plugin;
    private final CommandMap commandMap;
    private final Map<String, Command> knownCommands;

    public CommandManager(Main plugin) {
        this.plugin = plugin;
        CommandMap map = null;
        Map knownCmds = null;
        try {
            map = Bukkit.getServer().getCommandMap();
            Field knownCommandsField = null;
            Class<?> mapClass = map.getClass();
            while (mapClass != null && knownCommandsField == null) {
                try {
                    knownCommandsField = mapClass.getDeclaredField("knownCommands");
                }
                catch (NoSuchFieldException ignored) {
                    mapClass = mapClass.getSuperclass();
                }
            }
            if (knownCommandsField == null) {
                throw new NoSuchFieldException("Could not find knownCommands field in CommandMap.");
            }
            knownCommandsField.setAccessible(true);
            knownCmds = (Map)knownCommandsField.get(map);
        }
        catch (IllegalAccessException | NoSuchFieldException e) {
            plugin.getLogger().severe(plugin.getLanguageManager().getMessage("console.command-manager-error"));
            e.printStackTrace();
        }
        this.commandMap = map;
        this.knownCommands = knownCmds;
    }

    public void register(String name, String description, String usage, List<String> aliases, CommandExecutor executor) {
        if (this.commandMap == null) {
            return;
        }
        try {
            Constructor constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            PluginCommand command = (PluginCommand)constructor.newInstance(new Object[]{name, this.plugin});
            command.setDescription(description);
            command.setUsage(usage);
            if (aliases != null) {
                command.setAliases(aliases);
            }
            command.setExecutor(executor);
            if (executor instanceof TabCompleter) {
                command.setTabCompleter((TabCompleter)executor);
            }
            this.commandMap.register(this.plugin.getName().toLowerCase(), (Command)command);
        }
        catch (Exception e) {
            String msg = this.plugin.getLanguageManager().getMessage("console.command-register-error").replace("%command%", name);
            this.plugin.getLogger().severe(msg);
            e.printStackTrace();
        }
    }

    public void unregister(String name) {
        if (this.knownCommands != null) {
            this.knownCommands.remove(name);
            this.knownCommands.remove(String.valueOf(this.plugin.getName().toLowerCase()) + ":" + name);
        }
    }
}
