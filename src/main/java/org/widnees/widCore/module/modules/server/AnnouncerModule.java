package org.widnees.widCore.module.modules.server;

import org.widnees.widCore.Main;
import org.widnees.widCore.manager.AnnouncerManager;
import org.widnees.widCore.module.Module;

public class AnnouncerModule implements Module {

    private final Main plugin;
    private AnnouncerManager announcerManager;

    public AnnouncerModule(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Auto Announcer";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.announcer", false);
    }

    @Override
    public void register() {
        this.announcerManager = new AnnouncerManager(plugin);
        this.announcerManager.loadConfig();
    }

    @Override
    public void unregister() {
        if (this.announcerManager != null) {
            this.announcerManager.stop();
        }
    }
}