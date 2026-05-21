package org.widnees.widCore.manager;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;

public class ChatMetaManager {
    private final Main plugin;

    public ChatMetaManager(Main plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
    }

    public String getPrefix(Player player) {
        LuckPerms lp = this.plugin.getLuckPerms();
        if (lp != null) {
            try {
                User user = lp.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    String prefix = user.getCachedData().getMetaData(QueryOptions.defaultContextualOptions()).getPrefix();
                    return prefix != null ? prefix : "";
                }
            }
            catch (Throwable throwable) {

            }
        }
        return "";
    }

    public String getSuffix(Player player) {
        LuckPerms lp = this.plugin.getLuckPerms();
        if (lp != null) {
            try {
                User user = lp.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    String suffix = user.getCachedData().getMetaData(QueryOptions.defaultContextualOptions()).getSuffix();
                    return suffix != null ? suffix : "";
                }
            }
            catch (Throwable throwable) {

            }
        }
        return "";
    }

    public String getPrimaryGroup(Player player) {
        LuckPerms lp = this.plugin.getLuckPerms();
        if (lp != null) {
            try {
                User user = lp.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    return user.getPrimaryGroup().toLowerCase();
                }
            }
            catch (Throwable throwable) {

            }
        }
        return "";
    }

    public String getGroupPrefix(String groupName) {
        LuckPerms lp = this.plugin.getLuckPerms();
        if (lp != null) {
            try {
                Group group = lp.getGroupManager().getGroup(groupName);
                if (group != null) {
                    String prefix = group.getCachedData().getMetaData(QueryOptions.defaultContextualOptions()).getPrefix();
                    return prefix != null ? prefix : "";
                }
            }
            catch (Throwable throwable) {

            }
        }
        return "";
    }

    public String getGroupSuffix(String groupName) {
        LuckPerms lp = this.plugin.getLuckPerms();
        if (lp != null) {
            try {
                Group group = lp.getGroupManager().getGroup(groupName);
                if (group != null) {
                    String suffix = group.getCachedData().getMetaData(QueryOptions.defaultContextualOptions()).getSuffix();
                    return suffix != null ? suffix : "";
                }
            }
            catch (Throwable throwable) {

            }
        }
        return "";
    }
}
