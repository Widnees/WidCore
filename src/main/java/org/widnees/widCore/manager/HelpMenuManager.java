package org.widnees.widCore.manager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Content;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.TextParser;
import org.widnees.widCore.module.ModuleManager;

public class HelpMenuManager {
    private final Main plugin;
    private final List<String> helpMessages = new ArrayList<String>();
    private static final int ITEMS_PER_PAGE = 5;
    private final String commandLabel;

    public HelpMenuManager(Main plugin, ModuleManager moduleManager, String commandLabel) {
        this.plugin = plugin;
        this.commandLabel = commandLabel;
        this.loadCommandMessages(moduleManager);
    }

    private void loadCommandMessages(ModuleManager moduleManager) {
        this.helpMessages.clear();
        ArrayList<ModuleManager.CommandInfo> activeCommands = new ArrayList<ModuleManager.CommandInfo>(moduleManager.getRegisteredCommandInfo());
        activeCommands.sort(Comparator.comparing(ModuleManager.CommandInfo::name));
        for (ModuleManager.CommandInfo cmdInfo : activeCommands) {
            if (cmdInfo.name().equalsIgnoreCase(this.commandLabel) || cmdInfo.aliases() != null && cmdInfo.aliases().contains(this.commandLabel)) continue;
            String usage = cmdInfo.usage().replace("/<command>", "/" + cmdInfo.name());
            String description = cmdInfo.description();
            this.helpMessages.add(ChatColor.GOLD + usage + ChatColor.GRAY + " - " + ChatColor.WHITE + description);
        }
    }

    public void showHelpPage(CommandSender sender, int page) {
        if (this.helpMessages.isEmpty()) {
            Main.sendMessage(this.plugin, sender, this.plugin.getLanguageManager().getMessage("help.empty"));
            return;
        }
        int totalPages = (int)Math.ceil((double)this.helpMessages.size() / 5.0);
        if (page < 1) {
            page = 1;
        }
        if (page > totalPages) {
            page = totalPages;
        }
        String header = this.plugin.getLanguageManager().getMessage("help.header").replace("%page%", String.valueOf(page)).replace("%total%", String.valueOf(totalPages));
        Main.sendMessage(this.plugin, sender, ChatColor.translateAlternateColorCodes((char)'&', (String)("&6&l" + header)));
        int startIndex = (page - 1) * 5;
        int endIndex = Math.min(startIndex + 5, this.helpMessages.size());
        int i = startIndex;
        while (i < endIndex) {
            sender.sendMessage(this.helpMessages.get(i));
            ++i;
        }
        if (sender instanceof Player && totalPages > 1) {
            Player player = (Player)sender;
            TextComponent navigationMessage = new TextComponent("");
            if (page > 1) {
                TextComponent prevArrow = new TextComponent(ChatColor.GOLD + this.plugin.getLanguageManager().getMessage("help.prev"));
                prevArrow.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + this.commandLabel + " " + (page - 1)));
                prevArrow.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Content[]{new Text("")}));
                navigationMessage.addExtra((BaseComponent)prevArrow);
            }
            String spacer = page > 1 && page < totalPages ? "    " : " ";
            navigationMessage.addExtra((BaseComponent)new TextComponent(String.valueOf(spacer) + ChatColor.DARK_GRAY + "[" + ChatColor.WHITE + page + "/" + totalPages + ChatColor.DARK_GRAY + "]" + spacer));
            if (page < totalPages) {
                TextComponent nextArrow = new TextComponent(ChatColor.GOLD + this.plugin.getLanguageManager().getMessage("help.next"));
                nextArrow.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + this.commandLabel + " " + (page + 1)));
                nextArrow.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Content[]{new Text("")}));
                navigationMessage.addExtra((BaseComponent)nextArrow);
            }
            player.spigot().sendMessage((BaseComponent)navigationMessage);
        } else if (totalPages > 1) {
            String footer = this.plugin.getLanguageManager().getMessage("help.footer");
            sender.sendMessage(TextParser.colorize(footer));
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes((char)'&', (String)("&8&m" + ChatColor.stripColor((String)header))));
    }
}
