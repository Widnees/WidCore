package org.widnees.widCore.manager;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.widnees.widCore.Main;

public class AliasManager {
    private final Main plugin;
    private File aliasFile;
    private FileConfiguration aliasConfig;
    private final Map<String, CommandAlias> commandAliases = new HashMap<String, CommandAlias>();
    private static final Map<String, String> PERMISSIONS = new HashMap<String, String>();
    private static final Map<String, String> SUB_PERMISSIONS = new HashMap<String, String>();

    static {
        PERMISSIONS.put("gamemode", "widcore.gamemode");
        PERMISSIONS.put("fly", "widcore.fly");
        PERMISSIONS.put("god", "widcore.god");
        PERMISSIONS.put("heal", "widcore.heal");
        PERMISSIONS.put("feed", "widcore.feed");
        PERMISSIONS.put("speed", "widcore.speed");
        PERMISSIONS.put("back", "widcore.back");
        PERMISSIONS.put("repair", "widcore.repair");
        PERMISSIONS.put("item", "widcore.item");
        PERMISSIONS.put("head", "widcore.head");
        PERMISSIONS.put("teleport", "widcore.tp");
        PERMISSIONS.put("tphere", "widcore.tphere");
        PERMISSIONS.put("tpall", "widcore.tpall");
        PERMISSIONS.put("tpa", "widcore.tpa.send");
        PERMISSIONS.put("tpaaccept", "widcore.tpa.accept");
        PERMISSIONS.put("tpadeny", "widcore.tpa.deny");
        PERMISSIONS.put("tpatoggle", "widcore.tpa.autoaccept");
        PERMISSIONS.put("rtp", "widcore.rtp");
        SUB_PERMISSIONS.put("rtp.other", "widcore.rtp.other");
        SUB_PERMISSIONS.put("rtp.free", "widcore.rtp.free");
        SUB_PERMISSIONS.put("rtp.cooldown_bypass", "widcore.rtp.cooldown.bypass");
        PERMISSIONS.put("home", "widcore.home.teleport");
        PERMISSIONS.put("sethome", "widcore.home.create");
        PERMISSIONS.put("delhome", "widcore.home.delete");
        PERMISSIONS.put("warp", "widcore.warp");
        SUB_PERMISSIONS.put("warp.other", "widcore.warp.other");
        PERMISSIONS.put("setwarp", "widcore.setwarp");
        PERMISSIONS.put("delwarp", "widcore.delwarp");
        PERMISSIONS.put("spawn", "widcore.spawn");
        SUB_PERMISSIONS.put("spawn.other", "widcore.spawn.other");
        PERMISSIONS.put("setspawn", "widcore.setspawn");
        PERMISSIONS.put("setvoidspawn", "widcore.voidspawn.set");
        PERMISSIONS.put("worldmanager", "widcore.worldmanager");
        SUB_PERMISSIONS.put("worldmanager.create", "widcore.worldmanager.create");
        SUB_PERMISSIONS.put("worldmanager.delete", "widcore.worldmanager.delete");
        SUB_PERMISSIONS.put("worldmanager.teleport", "widcore.worldmanager.tp");
        SUB_PERMISSIONS.put("worldmanager.list", "widcore.worldmanager.list");
        SUB_PERMISSIONS.put("worldmanager.import", "widcore.worldmanager.import");
        SUB_PERMISSIONS.put("worldmanager.info", "widcore.worldmanager.info");
        SUB_PERMISSIONS.put("worldmanager.setspawn", "widcore.worldmanager.setspawn");
        SUB_PERMISSIONS.put("worldmanager.load", "widcore.worldmanager.load");
        SUB_PERMISSIONS.put("worldmanager.unload", "widcore.worldmanager.unload");
        PERMISSIONS.put("invsee", "widcore.invsee");
        PERMISSIONS.put("enderchest", "widcore.enderchest");
        PERMISSIONS.put("inventoryrollback", "widcore.irp");
        PERMISSIONS.put("message", "widcore.msg");
        PERMISSIONS.put("reply", "widcore.r");
        PERMISSIONS.put("economy", "widcore.eco.admin");
        PERMISSIONS.put("pay", "widcore.pay");
        PERMISSIONS.put("baltop", "widcore.baltop");
        PERMISSIONS.put("ban", "widcore.ban");
        PERMISSIONS.put("unban", "widcore.unban");
        PERMISSIONS.put("banlist", "widcore.banlist");
        PERMISSIONS.put("mute", "widcore.mute");
        PERMISSIONS.put("unmute", "widcore.unmute");
        PERMISSIONS.put("mutelist", "widcore.mutelist");
        PERMISSIONS.put("kick", "widcore.kick");
        PERMISSIONS.put("tempfly", "widcore.tempfly");
        SUB_PERMISSIONS.put("tempfly.give", "widcore.tempfly.give");
        SUB_PERMISSIONS.put("tempfly.remove", "widcore.tempfly.remove");
        SUB_PERMISSIONS.put("tempfly.check", "widcore.tempfly.check");
        PERMISSIONS.put("jail", "widcore.jail.use");
        PERMISSIONS.put("unjail", "widcore.jail.remove");
        PERMISSIONS.put("setjail", "widcore.jail.set");
        PERMISSIONS.put("deljail", "widcore.jail.delete");
        PERMISSIONS.put("vanish", "widcore.vanish");
        PERMISSIONS.put("freeze", "widcore.freeze");
        PERMISSIONS.put("unfreeze", "widcore.freeze");
        PERMISSIONS.put("troll", "widcore.troll");
        PERMISSIONS.put("lightning", "widcore.lightning");
        PERMISSIONS.put("fireball", "widcore.fireball");
        PERMISSIONS.put("fireballstick", "widcore.fireball");
        PERMISSIONS.put("itemedit", "widcore.itemedit.name");
        PERMISSIONS.put("iname", "widcore.itemedit.name");
        PERMISSIONS.put("ilore", "widcore.itemedit.lore");
        PERMISSIONS.put("enchant", "widcore.itemedit.enchant");
        PERMISSIONS.put("widcore", "widcore.admin");
    }

    public AliasManager(Main plugin) {
        this.plugin = plugin;
        this.loadConfig();
    }

    public void loadConfig() {
        if (this.aliasFile == null) {
            this.aliasFile = new File(this.plugin.getDataFolder(), "aliases.yml");
        }
        if (!this.aliasFile.exists()) {
            this.plugin.saveResource("aliases.yml", false);
        } else {
            this.plugin.getConfigManager().updateConfig(this.aliasFile, "aliases.yml");
        }
        this.aliasConfig = YamlConfiguration.loadConfiguration((File)this.aliasFile);
        InputStream defConfigStream = this.plugin.getResource("aliases.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration((Reader)new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            this.aliasConfig.setDefaults((Configuration)defConfig);
        }
        this.parseAliases();
    }

    private void parseAliases() {
        this.commandAliases.clear();
        for (String key : this.aliasConfig.getKeys(false)) {
            ConfigurationSection section = this.aliasConfig.getConfigurationSection(key);
            if (section == null || key.equalsIgnoreCase("widcore")) continue;
            String command = section.getString("command", key);
            List aliases = section.getStringList("aliases");
            String permission = PERMISSIONS.getOrDefault(key, "widcore." + key);
            HashMap<String, SubCommandAlias> subcommands = new HashMap<String, SubCommandAlias>();
            ConfigurationSection subcmdSection = section.getConfigurationSection("subcommands");
            if (subcmdSection != null) {
                for (String subKey : subcmdSection.getKeys(false)) {
                    ConfigurationSection subSection = subcmdSection.getConfigurationSection(subKey);
                    if (subSection == null) continue;
                    String subPerm = SUB_PERMISSIONS.getOrDefault(String.valueOf(key) + "." + subKey, String.valueOf(permission) + "." + subKey);
                    List subAliases = subSection.getStringList("aliases");
                    subcommands.put(subKey, new SubCommandAlias(subKey, subAliases, subPerm));
                }
            }
            HashMap<String, String> subpermissions = new HashMap<String, String>();
            if (key.equals("rtp")) {
                subpermissions.put("other", SUB_PERMISSIONS.get("rtp.other"));
                subpermissions.put("free", SUB_PERMISSIONS.get("rtp.free"));
                subpermissions.put("cooldown_bypass", SUB_PERMISSIONS.get("rtp.cooldown_bypass"));
            }
            if (key.equals("spawn")) {
                subpermissions.put("other", SUB_PERMISSIONS.get("spawn.other"));
            }
            if (key.equals("warp")) {
                subpermissions.put("other", SUB_PERMISSIONS.get("warp.other"));
            }
            if (key.equals("worldmanager")) {
                subpermissions.put("create", SUB_PERMISSIONS.get("worldmanager.create"));
                subpermissions.put("delete", SUB_PERMISSIONS.get("worldmanager.delete"));
                subpermissions.put("teleport", SUB_PERMISSIONS.get("worldmanager.teleport"));
                subpermissions.put("list", SUB_PERMISSIONS.get("worldmanager.list"));
                subpermissions.put("import", SUB_PERMISSIONS.get("worldmanager.import"));
                subpermissions.put("info", SUB_PERMISSIONS.get("worldmanager.info"));
                subpermissions.put("setspawn", SUB_PERMISSIONS.get("worldmanager.setspawn"));
                subpermissions.put("load", SUB_PERMISSIONS.get("worldmanager.load"));
                subpermissions.put("unload", SUB_PERMISSIONS.get("worldmanager.unload"));
            }
            HashMap<String, ModeAlias> modes = new HashMap<String, ModeAlias>();
            ConfigurationSection modesSection = section.getConfigurationSection("modes");
            if (modesSection != null) {
                for (String modeKey : modesSection.getKeys(false)) {
                    ConfigurationSection modeSection = modesSection.getConfigurationSection(modeKey);
                    if (modeSection == null) continue;
                    String modePerm = "widcore.gamemode." + modeKey;
                    List modeAliases = modeSection.getStringList("aliases");
                    modes.put(modeKey, new ModeAlias(modeKey, modeAliases, modePerm));
                }
            }
            this.commandAliases.put(key, new CommandAlias(key, command, aliases, permission, subcommands, subpermissions, modes));
        }
    }

    public void reload() {
        this.aliasConfig = YamlConfiguration.loadConfiguration((File)this.aliasFile);
        this.parseAliases();
    }

    public String getCommand(String key) {
        CommandAlias alias = this.commandAliases.get(key);
        return alias != null ? alias.command() : key;
    }

    public List<String> getAliases(String key) {
        CommandAlias alias = this.commandAliases.get(key);
        return alias != null ? alias.aliases() : Collections.emptyList();
    }

    public String getPermission(String key) {
        CommandAlias alias = this.commandAliases.get(key);
        return alias != null ? alias.permission() : PERMISSIONS.getOrDefault(key, "widcore." + key);
    }

    public String getSubcommandPermission(String commandKey, String subcommandKey) {
        CommandAlias alias = this.commandAliases.get(commandKey);
        if (alias != null && alias.subcommands().containsKey(subcommandKey)) {
            return alias.subcommands().get(subcommandKey).permission();
        }
        return SUB_PERMISSIONS.getOrDefault(String.valueOf(commandKey) + "." + subcommandKey, String.valueOf(this.getPermission(commandKey)) + "." + subcommandKey);
    }

    public List<String> getSubcommandAliases(String commandKey, String subcommandKey) {
        CommandAlias alias = this.commandAliases.get(commandKey);
        if (alias != null && alias.subcommands().containsKey(subcommandKey)) {
            return alias.subcommands().get(subcommandKey).aliases();
        }
        return Collections.emptyList();
    }

    public String getModePermission(String commandKey, String modeKey) {
        CommandAlias alias = this.commandAliases.get(commandKey);
        if (alias != null && alias.modes().containsKey(modeKey)) {
            return alias.modes().get(modeKey).permission();
        }
        return "widcore.gamemode." + modeKey;
    }

    public List<String> getModeAliases(String commandKey, String modeKey) {
        CommandAlias alias = this.commandAliases.get(commandKey);
        if (alias != null && alias.modes().containsKey(modeKey)) {
            return alias.modes().get(modeKey).aliases();
        }
        return Collections.emptyList();
    }

    public String matchMode(String commandKey, String input) {
        CommandAlias alias = this.commandAliases.get(commandKey);
        if (alias == null) {
            return null;
        }
        String lowerInput = input.toLowerCase();
        for (Map.Entry<String, ModeAlias> entry : alias.modes().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(input)) {
                return entry.getKey();
            }
            for (String modeAlias : entry.getValue().aliases()) {
                if (!modeAlias.equalsIgnoreCase(lowerInput)) continue;
                return entry.getKey();
            }
        }
        return null;
    }

    public String lookupKey(String commandName) {
        for (Map.Entry<String, CommandAlias> entry : this.commandAliases.entrySet()) {
            if (!entry.getValue().command().equalsIgnoreCase(commandName)) continue;
            return entry.getKey();
        }
        return commandName;
    }

    public Map<String, CommandAlias> getAllAliases() {
        return Collections.unmodifiableMap(this.commandAliases);
    }

    public String getSubpermission(String commandKey, String subpermissionKey) {
        CommandAlias alias = this.commandAliases.get(commandKey);
        if (alias != null && alias.subpermissions().containsKey(subpermissionKey)) {
            return alias.subpermissions().get(subpermissionKey);
        }
        return SUB_PERMISSIONS.getOrDefault(String.valueOf(commandKey) + "." + subpermissionKey, String.valueOf(this.getPermission(commandKey)) + "." + subpermissionKey);
    }

    public record CommandAlias(String key, String command, List<String> aliases, String permission, Map<String, SubCommandAlias> subcommands, Map<String, String> subpermissions, Map<String, ModeAlias> modes) {
    }

    public record ModeAlias(String key, List<String> aliases, String permission) {
    }

    public record SubCommandAlias(String key, List<String> aliases, String permission) {
    }
        @SuppressWarnings("unused")
    private static final String _xW9b3f7 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}
