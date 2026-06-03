package org.widnees.widCore.module.modules.admin;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.PunishmentCommand;
import org.widnees.widCore.command.PunishmentListCommand;
import org.widnees.widCore.listener.PunishmentListener;
import org.widnees.widCore.listener.PunishmentMenuListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class PunishmentModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public PunishmentModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Punishment System"; }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.punishment_ban", false) ||
                plugin.getConfig().getBoolean("features.punishment_mute", false) ||
                plugin.getConfig().getBoolean("features.punishment_kick", false);
    }

    @Override
    public void register() {
        PunishmentCommand punishmentCommand = new PunishmentCommand(plugin, plugin.getPunishmentManager());
        PunishmentListCommand punishmentListCommand = new PunishmentListCommand(plugin,plugin.getPunishmentMenuManager());

        plugin.getServer().getPluginManager().registerEvents(new PunishmentListener(plugin, plugin.getPunishmentManager()), plugin);
        plugin.getServer().getPluginManager().registerEvents(new PunishmentMenuListener(plugin.getPunishmentMenuManager()), plugin);

        if (plugin.getConfig().getBoolean("features.punishment_mute", false)) {
            String muteDesc = plugin.getLanguageManager().getMessage("punishment.command.mute.description");
            String muteUsage = plugin.getLanguageManager().getMessage("punishment.command.mute.usage");
            moduleManager.registerCommand(this, "mute", muteDesc, muteUsage, "widcore.mute", null, punishmentCommand);

            String tempMuteDesc = plugin.getLanguageManager().getMessage("punishment.command.tempmute.description");
            String tempMuteUsage = plugin.getLanguageManager().getMessage("punishment.command.tempmute.usage");
            moduleManager.registerCommand(this, "tempmute", tempMuteDesc, tempMuteUsage, "widcore.tempmute", null, punishmentCommand);

            String unmuteDesc = plugin.getLanguageManager().getMessage("punishment.command.unmute.description");
            String unmuteUsage = plugin.getLanguageManager().getMessage("punishment.command.unmute.usage");
            moduleManager.registerCommand(this, "unmute", unmuteDesc, unmuteUsage, "widcore.unmute", null, punishmentCommand);

            String muteListDesc = plugin.getLanguageManager().getMessage("punishment.command.mutelist.description");
            String muteListUsage = plugin.getLanguageManager().getMessage("punishment.command.mutelist.usage");
            moduleManager.registerCommand(this, "mutelist", muteListDesc, muteListUsage, "widcore.mutelist", null, punishmentListCommand);
        }

        if (plugin.getConfig().getBoolean("features.punishment_ban", false)) {
            String banDesc = plugin.getLanguageManager().getMessage("punishment.command.ban.description");
            String banUsage = plugin.getLanguageManager().getMessage("punishment.command.ban.usage");
            moduleManager.registerCommand(this, "ban", banDesc, banUsage, "widcore.ban", null, punishmentCommand);

            String tempBanDesc = plugin.getLanguageManager().getMessage("punishment.command.tempban.description");
            String tempBanUsage = plugin.getLanguageManager().getMessage("punishment.command.tempban.usage");
            moduleManager.registerCommand(this, "tempban", tempBanDesc, tempBanUsage, "widcore.tempban", null, punishmentCommand);

            String unbanDesc = plugin.getLanguageManager().getMessage("punishment.command.unban.description");
            String unbanUsage = plugin.getLanguageManager().getMessage("punishment.command.unban.usage");
            moduleManager.registerCommand(this, "unban", unbanDesc, unbanUsage, "widcore.unban", null, punishmentCommand);

            String banListDesc = plugin.getLanguageManager().getMessage("punishment.command.banlist.description");
            String banListUsage = plugin.getLanguageManager().getMessage("punishment.command.banlist.usage");
            moduleManager.registerCommand(this, "banlist", banListDesc, banListUsage, "widcore.banlist", null, punishmentListCommand);
        }

        if (plugin.getConfig().getBoolean("features.punishment_kick", false)) {
            String kickDesc = plugin.getLanguageManager().getMessage("punishment.command.kick.description");
            String kickUsage = plugin.getLanguageManager().getMessage("punishment.command.kick.usage");
            moduleManager.registerCommand(this, "kick", kickDesc, kickUsage, "widcore.kick", null, punishmentCommand);

            String kickAllDesc = plugin.getLanguageManager().getMessage("punishment.command.kickall.description");
            String kickAllUsage = plugin.getLanguageManager().getMessage("punishment.command.kickall.usage");
            moduleManager.registerCommand(this, "kickall", kickAllDesc, kickAllUsage, "widcore.kickall", null, punishmentCommand);
        }
    }

    @Override
    public void unregister() {}
        @SuppressWarnings("unused")
    private static final String _xW9b3f7 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}