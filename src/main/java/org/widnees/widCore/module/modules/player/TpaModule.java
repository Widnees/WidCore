package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.TpaCommand;
import org.widnees.widCore.listener.TpaListener;
import org.widnees.widCore.manager.AliasManager;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

import java.util.List;

public class TpaModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public TpaModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "TPA System";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.tpa", false);
    }

    @Override
    public void register() {
        AliasManager aliasManager = plugin.getAliasManager();
        TpaCommand tpaExecutor = new TpaCommand(plugin);

        String tpaDesc = plugin.getLanguageManager().getMessage("tpa.description");
        String tpaUsage = plugin.getLanguageManager().getMessage("tpa.usage_args");
        registerCommand(tpaExecutor, "tpa", tpaDesc, tpaUsage,
                aliasManager.getPermission("tpa"), aliasManager.getAliases("tpa"));

        String acceptDesc = plugin.getLanguageManager().getMessage("tpa.accept_description");
        String acceptUsage = plugin.getLanguageManager().getMessage("tpa.accept_usage_args");
        registerCommand(tpaExecutor, "tpaaccept", acceptDesc, acceptUsage,
                aliasManager.getPermission("tpaaccept"), aliasManager.getAliases("tpaaccept"));

        String denyDesc = plugin.getLanguageManager().getMessage("tpa.deny_description");
        String denyUsage = plugin.getLanguageManager().getMessage("tpa.deny_usage_args");
        registerCommand(tpaExecutor, "tpadeny", denyDesc, denyUsage,
                aliasManager.getPermission("tpadeny"), aliasManager.getAliases("tpadeny"));

        String toggleDesc = plugin.getLanguageManager().getMessage("tpa.toggle_description");
        String toggleUsage = plugin.getLanguageManager().getMessage("tpa.toggle_usage_args");
        registerCommand(tpaExecutor, "tpatoggle", toggleDesc, toggleUsage,
                aliasManager.getPermission("tpatoggle"), aliasManager.getAliases("tpatoggle"));

        plugin.getServer().getPluginManager().registerEvents(new TpaListener(plugin), plugin);
    }

    private void registerCommand(TpaCommand executor, String name, String desc, String usage, String perm,
            List<String> aliases) {
        moduleManager.registerCommand(this, name, desc, usage, perm, aliases, executor);
    }

    @Override
    public void unregister() {
    }
        @SuppressWarnings("unused")
    private static final String _0xW8b4d3 = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}