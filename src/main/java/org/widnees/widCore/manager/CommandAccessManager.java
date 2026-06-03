package org.widnees.widCore.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;

public class CommandAccessManager {
    public static final String MODULE_KEY = "plugin-hider";
    private final Main plugin;
    private FileConfiguration config;
    private String bypassPermission;
    private boolean opBypass;
    private boolean defaultDeny;
    private final LinkedHashMap<String, GroupRules> groups = new LinkedHashMap();

    public CommandAccessManager(Main plugin) {
        this.plugin = plugin;
        this.reload();
    }

    public void reload() {
        this.config = this.plugin.getConfigManager().getModuleConfig(MODULE_KEY);
        this.bypassPermission = this.config.getString("bypass-permission", "");
        this.opBypass = this.config.getBoolean("op-bypass", true);
        String behavior = this.config.getString("default-behavior", "deny");
        this.defaultDeny = !"allow".equalsIgnoreCase(behavior);
        this.groups.clear();
        ConfigurationSection groupSec = this.config.getConfigurationSection("groups");
        if (groupSec != null) {
            for (String groupName : groupSec.getKeys(false)) {
                ConfigurationSection g = groupSec.getConfigurationSection(groupName);
                GroupRules rules = new GroupRules(groupName);
                if (g != null) {
                    List<String> cmd = CommandAccessManager.mergedList(g, new String[]{"command", "commands"});
                    List<String> tab = CommandAccessManager.mergedList(g, new String[]{"tabacomplate", "tabcomplate", "tabcomplete"});
                    rules.allowAllCommands = CommandAccessManager.isAllowAllList(cmd);
                    rules.allowAllTab = CommandAccessManager.isAllowAllList(tab);
                    rules.commandRules = cmd.stream().map(Rule::parse).collect(Collectors.toList());
                    rules.tabRules = tab.stream().map(Rule::parse).collect(Collectors.toList());
                }
                this.groups.put(groupName, rules);
            }
        }
        this.groups.putIfAbsent("default", new GroupRules("default"));
    }

    private static List<String> mergedList(ConfigurationSection sec, String[] keys) {
        ArrayList<String> out = new ArrayList<String>();
        if (sec == null || keys == null) {
            return out;
        }
        for (String k : keys) {
            List l = sec.getStringList(k);
            if (l == null || l.isEmpty()) continue;
            out.addAll(l);
        }
        return out;
    }

    private static boolean isAllowAllList(List<String> list) {
        String v;
        if (list == null) {
            return false;
        }
        return list.size() == 1 && (v = list.get(0)) != null && v.trim().equals("*");
    }

    public boolean hasBypass(Player p) {
        if (p == null) {
            return true;
        }
        return p.isOp();
    }

    public String resolveGroup(Player p) {
        if (p == null) {
            return "default";
        }
        for (String g : this.groups.keySet()) {
            String perm;
            if ("default".equalsIgnoreCase(g) || !p.hasPermission(perm = "widcore.group." + g)) continue;
            return g;
        }
        return "default";
    }

    private GroupRules getRulesFor(Player p) {
        String group = this.resolveGroup(p);
        GroupRules r = this.groups.get(group);
        if (r == null) {
            r = this.groups.get("default");
        }
        if (r == null) {
            r = new GroupRules("default");
        }
        return r;
    }

    public boolean isExecutionAllowed(Player p, String root, List<String> args) {
        if (this.hasBypass(p)) {
            return true;
        }
        GroupRules r = this.getRulesFor(p);
        boolean allowed = r.matchesAnyCommand(root, args);
        return this.defaultDeny ? allowed : !allowed;
    }

    public boolean isRootVisible(Player p, String root) {
        if (this.hasBypass(p)) {
            return true;
        }
        GroupRules r = this.getRulesFor(p);
        boolean allowed = r.matchesAnyTab(root, Collections.emptyList());
        return this.defaultDeny ? allowed : !allowed;
    }

    public boolean isTabPathAllowed(Player p, String root, List<String> args) {
        if (this.hasBypass(p)) {
            return true;
        }
        GroupRules r = this.getRulesFor(p);
        boolean allowed = r.matchesAnyTab(root, args);
        return this.defaultDeny ? allowed : !allowed;
    }

    private static class GroupRules {
        final String name;
        List<Rule> commandRules = Collections.emptyList();
        List<Rule> tabRules = Collections.emptyList();
        boolean allowAllCommands = false;
        boolean allowAllTab = false;

        GroupRules(String name) {
            this.name = name;
        }

        boolean matchesAnyCommand(String root, List<String> args) {
            if (this.allowAllCommands) {
                return true;
            }
            for (Rule r : this.commandRules) {
                if (!r.matches(root, args)) continue;
                return true;
            }
            return false;
        }

        boolean matchesAnyTab(String root, List<String> args) {
            if (this.allowAllTab) {
                return true;
            }
            for (Rule r : this.tabRules) {
                if (!r.matches(root, args)) continue;
                return true;
            }
            return false;
        }
    }

    private static class Rule {
        final String root;
        final List<String> subTokens;
        final boolean trailingStar;
        final boolean wildcardRootPrefix;

        private Rule(String root, List<String> subs, boolean star, boolean wildcardRootPrefix) {
            this.root = root;
            this.subTokens = subs;
            this.trailingStar = star;
            this.wildcardRootPrefix = wildcardRootPrefix;
        }

        static Rule parse(String pattern) {
            String trimmed;
            if (pattern == null) {
                pattern = "";
            }
            if ((trimmed = pattern.trim()).isEmpty()) {
                return new Rule("", Collections.emptyList(), false, false);
            }
            String[] parts = trimmed.split("\\s+");
            boolean star = parts.length > 0 && parts[parts.length - 1].equals("*");
            int len = star ? parts.length - 1 : parts.length;
            String root = parts[0];
            ArrayList<String> subs = new ArrayList<String>();
            for (int i = 1; i < len; ++i) {
                subs.add(parts[i]);
            }
            boolean wildcardRoot = star && subs.isEmpty();
            return new Rule(Rule.normalizeRoot(root), Rule.toLower(subs), star, wildcardRoot);
        }

        boolean matches(String inRoot, List<String> inArgs) {
            String rootNorm = Rule.normalizeRoot(inRoot);
            if (this.wildcardRootPrefix ? !rootNorm.equalsIgnoreCase(this.root) && !rootNorm.toLowerCase(Locale.ROOT).startsWith(this.root.toLowerCase(Locale.ROOT)) : !rootNorm.equalsIgnoreCase(this.root)) {
                return false;
            }
            if (this.subTokens.isEmpty()) {
                if (this.trailingStar) {
                    return true;
                }
                return inArgs == null || inArgs.isEmpty();
            }
            if (inArgs == null) {
                inArgs = Collections.emptyList();
            }
            if (inArgs.size() < this.subTokens.size()) {
                return false;
            }
            for (int i = 0; i < this.subTokens.size(); ++i) {
                String got;
                String req = this.subTokens.get(i);
                if (req.equalsIgnoreCase(got = inArgs.get(i))) continue;
                return false;
            }
            return this.trailingStar || inArgs.size() == this.subTokens.size();
        }

        private static String normalizeRoot(String root) {
            int idx;
            if (root == null) {
                return "";
            }
            if ((root = root.trim()).startsWith("/")) {
                root = root.substring(1);
            }
            if ((idx = root.indexOf(58)) >= 0 && idx + 1 < root.length()) {
                root = root.substring(idx + 1);
            }
            return root;
        }

        private static List<String> toLower(List<String> list) {
            return list.stream().map(s -> s == null ? "" : s.toLowerCase(Locale.ROOT)).collect(Collectors.toList());
        }
    }
        @SuppressWarnings("unused")
    private static final String _xW3c9f4 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}
