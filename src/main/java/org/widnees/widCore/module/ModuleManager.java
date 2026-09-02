package org.widnees.widCore.module;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.HandlerList;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.CommandManager;
import org.widnees.widCore.module.modules.admin.*;
import org.widnees.widCore.module.modules.chat.*;
import org.widnees.widCore.module.modules.economy.EconomyModule;
import org.widnees.widCore.module.modules.player.*;
import org.widnees.widCore.module.modules.server.*;
import org.widnees.widCore.module.modules.server.AntiMobSpawnModule;
import org.widnees.widCore.module.modules.world.*;

import java.util.*;

public class ModuleManager {

    public record CommandInfo(String name, String description, String usage, String permission, List<String> aliases) {
    }

    private final Main plugin;
    private final CommandManager commandManager;
    private final List<Module> modules = new ArrayList<>();
    private final List<String> enabledFeatures = new ArrayList<>();
    private final List<String> disabledFeatures = new ArrayList<>();
    private final List<Module> currentlyEnabledModules = new ArrayList<>();
    private final Map<Module, List<String>> registeredCommandsByModule = new HashMap<>();
    private final List<CommandInfo> registeredCommandInfo = new ArrayList<>();
    private final Map<String, List<String>> disabledDueToMissingDependencies = new HashMap<>();
    private final Map<String, List<String>> modulesWithMissingOptionalDeps = new HashMap<>();

    public ModuleManager(Main plugin) {
        this.plugin = plugin;
        this.commandManager = new CommandManager(plugin);
    }

    public void clearModules() {
        modules.clear();
    }

    public void initializeModules() {
        addPlayerModules();
        addWorldModules();
        addAdminModules();
        addChatModules();
        addServerModules();
        addEconomyModules();
    }

    private void addPlayerModules() {
        modules.add(new BackModule(plugin));
        modules.add(new CompassTeleportModule(plugin));
        modules.add(new EnderChestModule(plugin));
        modules.add(new FeedModule(plugin));
        modules.add(new FlyModule(plugin));
        modules.add(new GamemodeModule(plugin));
        modules.add(new GodModule(plugin));
        modules.add(new HealModule(plugin));
        modules.add(new HomeModule(plugin));
        modules.add(new InvseeModule(plugin));
        modules.add(new ItemModule(plugin));
        modules.add(new RepairModule(plugin));
        modules.add(new SpeedModule(plugin));
        modules.add(new TeleportModule(plugin));
        modules.add(new TpaModule(plugin));
        modules.add(new HeadModule(plugin));
    }

    private void addWorldModules() {
        modules.add(new SpawnModule(plugin));
        modules.add(new VoidSpawnModule(plugin));
        modules.add(new WarpModule(plugin));
        modules.add(new WorldManagerModule(plugin));
        modules.add(new RtpModule(plugin));
    }

    private void addAdminModules() {
        modules.add(new FreezeModule(plugin));
        modules.add(new InventoryRollbackModule(plugin));
        modules.add(new PunishmentModule(plugin));
        modules.add(new TrollModule(plugin));
        modules.add(new VanishModule(plugin));
        modules.add(new FireballModule(plugin));
        modules.add(new FireballStickModule(plugin));
        modules.add(new LightningModule(plugin));
        modules.add(new JailModule(plugin));
        modules.add(new org.widnees.widCore.module.modules.admin.DisguiseModule(plugin));
    }

    private void addChatModules() {
        modules.add(new ChatFormatModule(plugin));
        modules.add(new ChatGuardModule(plugin));
        modules.add(new MessagingModule(plugin));
        modules.add(new ShowItemModule(plugin));
        modules.add(new MentionModule(plugin));
        modules.add(new ChatControlModule(plugin));
    }

    private void addServerModules() {
        modules.add(new ItemEditModule(plugin));
        modules.add(new ItemCleanerModule(plugin));
        modules.add(new JoinLeaveModule(plugin));
        modules.add(new MergeExperienceOrbModule(plugin));
        modules.add(new StackDeathDropsModule(plugin));
        modules.add(new MotdModule(plugin));
        modules.add(new DeathModule(plugin));
        modules.add(new BannedItemModule(plugin));
        modules.add(new AntiMobSpawnModule(plugin));
        modules.add(new MobStackerModule(plugin));
        modules.add(new AnnouncerModule(plugin));
        modules.add(new CustomCommandModule(plugin));

        modules.add(new org.widnees.widCore.module.modules.server.ServerInfoHiderModule(plugin));
    }

    public void registerCommand(Module module, String name, String description, String usage, String permission,
            List<String> aliases, CommandExecutor executor) {

        String finalName = plugin.getAliasManager().getCommand(name);
        if (finalName == null)
            finalName = name; 

        List<String> finalAliases = plugin.getAliasManager().getAliases(name);
        if (finalAliases == null)
            finalAliases = new ArrayList<>();

        String finalPermission = plugin.getAliasManager().getPermission(name);

        CommandInfo cmdInfo = new CommandInfo(finalName, description, usage, finalPermission, finalAliases);

        String finalNameForCheck = finalName; 
        if (registeredCommandInfo.stream().noneMatch(c -> c.name().equalsIgnoreCase(finalNameForCheck))) {
            registeredCommandInfo.add(cmdInfo);
        }

        commandManager.register(finalName, description, usage, finalAliases, executor);
        List<String> commands = registeredCommandsByModule.computeIfAbsent(module, k -> new ArrayList<>());
        commands.add(finalName);
        if (finalAliases != null && !finalAliases.isEmpty()) {
            commands.addAll(finalAliases);
        }
    }

    private void addEconomyModules() {
        modules.add(new EconomyModule(plugin));
    }

    public void registerModules() {
        enabledFeatures.clear();
        disabledFeatures.clear();
        currentlyEnabledModules.clear();
        registeredCommandInfo.clear();
        disabledDueToMissingDependencies.clear();
        modulesWithMissingOptionalDeps.clear();

        for (Module module : modules) {

            List<String> missingDeps = module.getMissingDependencies();
            if (!missingDeps.isEmpty()) {
                disabledDueToMissingDependencies.put(module.getName(), missingDeps);
                disabledFeatures.add(module.getName());
                continue;
            }

            List<String> missingOptional = module.getMissingOptionalDependencies();
            if (!missingOptional.isEmpty()) {
                modulesWithMissingOptionalDeps.put(module.getName(), missingOptional);
            }

            if (module.isEnabled()) {
                try {
                    module.register();
                    enabledFeatures.add(module.getName());
                    currentlyEnabledModules.add(module);
                } catch (Exception e) {
                    plugin.getLogger().severe(plugin.getLanguageManager().getMessage("module.load-error")
                            .replace("%module%", module.getName()));
                    e.printStackTrace();
                }
            } else {
                disabledFeatures.add(module.getName());
            }
        }
        printFeatureStatus();
    }

    public void unregisterModules() {
        for (Module module : currentlyEnabledModules) {
            if (registeredCommandsByModule.containsKey(module)) {
                for (String cmdName : registeredCommandsByModule.get(module)) {
                    commandManager.unregister(cmdName);
                }
            }
            module.unregister();
        }
        registeredCommandsByModule.clear();
        unregisterListeners();
    }

    public void unregisterListeners() {
        HandlerList.unregisterAll(plugin);
    }

    public List<CommandInfo> getRegisteredCommandInfo() {
        return Collections.unmodifiableList(registeredCommandInfo);
    }

    public List<Module> getAllModules() {
        return Collections.unmodifiableList(modules);
    }

    private void printFeatureStatus() {
        Collections.sort(enabledFeatures);
        Collections.sort(disabledFeatures);

        var console = Bukkit.getConsoleSender();

        int width = 72;
        String top = "╔" + repeat('═', width) + "╗";
        String sep = "╠" + repeat('═', width) + "╣";
        String empty = "║" + padColored(" ", width) + "║";

        console.sendMessage(top);
        String title = " " + plugin.getLanguageManager().getMessage("module.status_header");
        console.sendMessage("║" + padColored(title, width) + "║");
        console.sendMessage(sep);

        String activeTitle = " " + plugin.getLanguageManager().getMessage("module.active_features");
        console.sendMessage("║" + padColored(activeTitle, width) + "║");
        console.sendMessage(empty);

        if (enabledFeatures.isEmpty()) {
            String emptyMsg = "  " + plugin.getLanguageManager().getMessage("module.empty");
            console.sendMessage("║" + padColored(emptyMsg, width) + "║");
        } else {
            for (String feature : enabledFeatures) {
                String name = normalizeModuleName(feature);
                String line = ChatColor.GREEN + "+" + ChatColor.RESET + " " + name;
                console.sendMessage("║" + padColored("  " + line, width) + "║");
            }
        }

        boolean cgBanned = plugin.getConfig().getBoolean("chatguard.bannedword", false);
        boolean cgSpam = plugin.getConfig().getBoolean("chatguard.spam", false);
        boolean cgFlood = plugin.getConfig().getBoolean("chatguard.flood", false);
        boolean cgAds = plugin.getConfig().getBoolean("chatguard.advertisement", false);
        java.util.List<String> chatGuardDisabled = new java.util.ArrayList<>();
        if (!cgBanned)
            chatGuardDisabled.add("bannedword");
        if (!cgSpam)
            chatGuardDisabled.add("spam");
        if (!cgFlood)
            chatGuardDisabled.add("flood");
        if (!cgAds)
            chatGuardDisabled.add("advertisement");

        boolean hasDisabledModules = !disabledFeatures.isEmpty();
        boolean hasChatGuardDisabled = !chatGuardDisabled.isEmpty();

        if (hasDisabledModules || hasChatGuardDisabled) {
            console.sendMessage(empty);
            String inactiveTitle = " " + plugin.getLanguageManager().getMessage("module.inactive_features");
            console.sendMessage("║" + padColored(inactiveTitle, width) + "║");

            if (hasDisabledModules) {
                for (String feature : disabledFeatures) {
                    String name = normalizeModuleName(feature);
                    String line = ChatColor.RED + "-" + ChatColor.RESET + " " + name;
                    console.sendMessage("║" + padColored("  " + line, width) + "║");
                }
            }
            if (hasChatGuardDisabled) {
                console.sendMessage(empty);
                console.sendMessage("║" + padColored("  chatguard:", width) + "║");
                console.sendMessage(empty);
                for (String sub : chatGuardDisabled) {
                    String line = ChatColor.RED + "-" + ChatColor.RESET + " " + sub;
                    console.sendMessage("║" + padColored("    " + line, width) + "║");
                }
            }
        }

        console.sendMessage("╚" + repeat('═', width) + "╝");

        printMissingDependencies(console, width);

        plugin.getConfigManager().printChangesBox(console, width);
    }

    private void printMissingDependencies(org.bukkit.command.ConsoleCommandSender console, int width) {
        boolean hasRequiredMissing = !disabledDueToMissingDependencies.isEmpty();
        boolean hasOptionalMissing = !modulesWithMissingOptionalDeps.isEmpty();

        if (!hasRequiredMissing && !hasOptionalMissing) {
            return;
        }

        console.sendMessage("");
        String top = "╔" + repeat('═', width) + "╗";
        String sep = "╠" + repeat('═', width) + "╣";
        String empty = "║" + padColored(" ", width) + "║";

        console.sendMessage(top);
        console.sendMessage("║" + padColored(" " + ChatColor.YELLOW + "Missing Dependencies", width) + "║");
        console.sendMessage(sep);

        if (hasRequiredMissing) {
            console.sendMessage("║" + padColored(" " + ChatColor.RED + "Required (features disabled):", width) + "║");
            console.sendMessage(empty);

            for (Map.Entry<String, List<String>> entry : disabledDueToMissingDependencies.entrySet()) {
                String moduleName = normalizeModuleName(entry.getKey());
                String deps = String.join(", ", entry.getValue());
                String line = ChatColor.RED + "-" + ChatColor.RESET + " " + moduleName + 
                             ChatColor.GRAY + " (requires: " + deps + ")";
                console.sendMessage("║" + padColored("  " + line, width) + "║");
            }
        }

        if (hasOptionalMissing) {
            if (hasRequiredMissing) {
                console.sendMessage(empty);
            }
            console.sendMessage("║" + padColored(" " + ChatColor.YELLOW + "Optional (features limited):", width) + "║");
            console.sendMessage(empty);

            for (Map.Entry<String, List<String>> entry : modulesWithMissingOptionalDeps.entrySet()) {
                String moduleName = normalizeModuleName(entry.getKey());
                List<String> deps = entry.getValue();
                if (deps.isEmpty()) continue;

                String header = ChatColor.YELLOW + "!" + ChatColor.RESET + " " + moduleName;
                console.sendMessage("║" + padColored("  " + header, width) + "║");

                for (String dep : deps) {
                    String sub = ChatColor.GRAY + "- " + ChatColor.RESET + dep;
                    console.sendMessage("║" + padColored("    " + sub, width) + "║");
                }
            }
        }

        console.sendMessage("╚" + repeat('═', width) + "╝");
    }

    private String normalizeModuleName(String featureName) {
        if (featureName == null)
            return "";
        return featureName.toLowerCase().replaceAll("[\\s/]+", "");
    }

    private String padColored(String s, int width) {
        if (s == null)
            s = "";
        int visible = stripColors(s).length();
        if (visible >= width) {
            StringBuilder out = new StringBuilder();
            int vis = 0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                out.append(c);
                if (c == ChatColor.COLOR_CHAR && i + 1 < s.length()) {
                    out.append(s.charAt(++i));
                    continue;
                }
                vis++;
                if (vis >= width)
                    break;
            }
            s = out.toString();
            visible = width;
        }
        StringBuilder sb = new StringBuilder(s);
        while (visible < width) {
            sb.append(' ');
            visible++;
        }
        return sb.toString();
    }

    private String repeat(char c, int times) {
        StringBuilder sb = new StringBuilder(times);
        for (int i = 0; i < times; i++)
            sb.append(c);
        return sb.toString();
    }

    private String stripColors(String s) {
        if (s == null || s.isEmpty())
            return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ChatColor.COLOR_CHAR && i + 1 < s.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
        @SuppressWarnings("unused")
    private static final String __wN7e3x9 = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}