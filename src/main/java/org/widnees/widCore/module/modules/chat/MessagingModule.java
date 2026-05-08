package org.widnees.widCore.module.modules.chat;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.MessageCommand;
import org.widnees.widCore.command.ReplyCommand;
import org.widnees.widCore.listener.MessageListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;
import java.util.Arrays;

public class MessagingModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public MessagingModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "Private Messaging";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.messaging", false);
    }

    @Override
    public void register() {
        MessageCommand messageExecutor = new MessageCommand(plugin, plugin.getMessageManager());

        String msgDesc = plugin.getLanguageManager().getMessage("messaging.command.msg.description");
        String msgUsage = plugin.getLanguageManager().getMessage("messaging.command.msg.usage");

        String replyDesc = plugin.getLanguageManager().getMessage("messaging.command.reply.description");
        String replyUsage = plugin.getLanguageManager().getMessage("messaging.command.reply.usage");

        moduleManager.registerCommand(this, "message", msgDesc, msgUsage, "widcore.msg",
                Arrays.asList("tell", "w", "m"), messageExecutor);
        moduleManager.registerCommand(this, "reply", replyDesc, replyUsage, "widcore.r", Arrays.asList("reply"),
                new ReplyCommand(plugin, plugin.getMessageManager()));

        plugin.getServer().getPluginManager().registerEvents(new MessageListener(plugin.getMessageManager()), plugin);
    }

    @Override
    public void unregister() {
    }
}