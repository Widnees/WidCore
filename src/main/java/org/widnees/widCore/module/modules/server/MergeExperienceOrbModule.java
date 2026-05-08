package org.widnees.widCore.module.modules.server;

import org.widnees.widCore.Main;
import org.widnees.widCore.listener.ExperienceOrbListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class MergeExperienceOrbModule implements Module {
    private final Main plugin;
    public MergeExperienceOrbModule(Main plugin) { this.plugin = plugin; }
    @Override public String getName() { return "Merge Experience Orbs"; }
    @Override public boolean isEnabled() { return plugin.getConfig().getBoolean("features.merge_experience_orbs", false); }
    @Override public void register() {
        plugin.getServer().getPluginManager().registerEvents(new ExperienceOrbListener(plugin), plugin);
    }
    @Override public void unregister() {}
}