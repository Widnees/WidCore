package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.FeedCommand;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class FeedModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public FeedModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Feed Command"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.feed", false); }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("feed.description");
        String usage = plugin.getLanguageManager().getMessage("feed.usage_args");

        moduleManager.registerCommand(this, "feed", desc, usage, "widcore.feed", null, new FeedCommand(plugin));
    }

    @Override
    public void unregister() {}
}