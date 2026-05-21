package org.widnees.widCore.module.modules.chat;

import org.bukkit.configuration.file.FileConfiguration;
import org.widnees.widCore.Main;
import org.widnees.widCore.command.MentionCommand;
import org.widnees.widCore.listener.MentionListener;
import org.widnees.widCore.listener.MentionSettingsListener;
import org.widnees.widCore.module.Module;

import java.util.Collections;

public class MentionModule implements Module {

    private final Main plugin;

    public MentionModule(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Mention";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.mention", false);
    }

    @Override
    public void register() {
        FileConfiguration mentionConfig = plugin.getConfigManager().getModuleConfig("mention");

        MentionListener mentionListener = new MentionListener(plugin, mentionConfig);
        plugin.getServer().getPluginManager().registerEvents(mentionListener, plugin);

        MentionCommand mentionCommand = new MentionCommand(plugin, mentionConfig);

        plugin.getModuleManager().registerCommand(
                this,
                "mention",
                "Mention bildirim ayarları",
                "/mention [settings|toggle]",
                "widcore.mention.settings",
                Collections.emptyList(),
                mentionCommand
        );

        plugin.getServer().getPluginManager().registerEvents(
                new MentionSettingsListener(plugin, mentionCommand), plugin);
    }

    @Override
    public void unregister() {
    }
}