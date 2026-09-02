package org.widnees.widCore.module.modules.admin;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.VanishCommand;
import org.widnees.widCore.listener.VanishListener;
import org.widnees.widCore.listener.VanishTabCompleteListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;
import java.util.Arrays;

public class VanishModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public VanishModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "Vanish";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.vanish", false);
    }

    @Override
    public java.util.List<String> getMissingDependencies() {
        return java.util.List.of();
    }

    @Override
    public java.util.List<String> getMissingOptionalDependencies() {
        if (!isPacketEventsAvailable()) {
            return java.util.Arrays.asList(
                "sound suppression disabled (requires packetevents)",
                "block animation suppression disabled (requires packetevents)",
                "entity sound suppression disabled (requires packetevents)"
            );
        }
        return java.util.List.of();
    }

    private static boolean isPacketEventsAvailable() {
        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
            return com.github.retrooper.packetevents.PacketEvents.getAPI() != null;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("vanish.description");
        String usage = plugin.getLanguageManager().getMessage("vanish.usage_args");

        moduleManager.registerCommand(this, "vanish", desc, usage, "widcore.vanish", Arrays.asList("v"),
                new VanishCommand(plugin));
        plugin.getServer().getPluginManager().registerEvents(new VanishListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new VanishTabCompleteListener(plugin), plugin);
    }

    @Override
    public void unregister() {
    }
        @SuppressWarnings("unused")
    private static final String _0xWb8d2e = "\u0077\u0069\u0064" + "\u006e\u0065" + "\u0065\u0073";

}