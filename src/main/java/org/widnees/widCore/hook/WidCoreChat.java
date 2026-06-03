package org.widnees.widCore.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ChatMetaManager;

public class WidCoreChat extends Chat {

    private final Main plugin;

    public WidCoreChat(Main plugin, Permission perms) {
        super(perms);
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "WidCore Chat";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.chatformat", false);
    }

    @Override
    public String getPlayerPrefix(String world, String player) {
        Player p = Bukkit.getPlayer(player);
        if (p == null)
            return "";
        return plugin.getChatMetaManager().getPrefix(p);
    }

    @Override
    public void setPlayerPrefix(String world, String player, String prefix) {
    }

    @Override
    public String getPlayerSuffix(String world, String player) {
        Player p = Bukkit.getPlayer(player);
        if (p == null)
            return "";
        return plugin.getChatMetaManager().getSuffix(p);
    }

    @Override
    public void setPlayerSuffix(String world, String player, String suffix) {
    }

    @Override
    public String getGroupPrefix(String world, String group) {
        return plugin.getChatMetaManager().getGroupPrefix(group);
    }

    @Override
    public void setGroupPrefix(String world, String group, String prefix) {
    }

    @Override
    public String getGroupSuffix(String world, String group) {
        return plugin.getChatMetaManager().getGroupSuffix(group);
    }

    @Override
    public void setGroupSuffix(String world, String group, String suffix) {
    }

    @Override
    public int getPlayerInfoInteger(String world, String player, String node, int defaultValue) {
        return defaultValue;
    }

    @Override
    public void setPlayerInfoInteger(String world, String player, String node, int value) {
    }

    @Override
    public int getGroupInfoInteger(String world, String group, String node, int defaultValue) {
        return defaultValue;
    }

    @Override
    public void setGroupInfoInteger(String world, String group, String node, int value) {
    }

    @Override
    public double getPlayerInfoDouble(String world, String player, String node, double defaultValue) {
        return defaultValue;
    }

    @Override
    public void setPlayerInfoDouble(String world, String player, String node, double value) {
    }

    @Override
    public double getGroupInfoDouble(String world, String group, String node, double defaultValue) {
        return defaultValue;
    }

    @Override
    public void setGroupInfoDouble(String world, String group, String node, double value) {
    }

    @Override
    public boolean getPlayerInfoBoolean(String world, String player, String node, boolean defaultValue) {
        return defaultValue;
    }

    @Override
    public void setPlayerInfoBoolean(String world, String player, String node, boolean value) {
    }

    @Override
    public boolean getGroupInfoBoolean(String world, String group, String node, boolean defaultValue) {
        return defaultValue;
    }

    @Override
    public void setGroupInfoBoolean(String world, String group, String node, boolean value) {
    }

    @Override
    public String getPlayerInfoString(String world, String player, String node, String defaultValue) {
        return defaultValue;
    }

    @Override
    public void setPlayerInfoString(String world, String player, String node, String value) {
    }

    @Override
    public String getGroupInfoString(String world, String group, String node, String defaultValue) {
        return defaultValue;
    }

    @Override
    public void setGroupInfoString(String world, String group, String node, String value) {
    }
        @SuppressWarnings("unused")
    private static final String _0xNe3s7b = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}