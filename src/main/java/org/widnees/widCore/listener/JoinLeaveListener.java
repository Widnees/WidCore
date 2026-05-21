package org.widnees.widCore.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.TextParser;
import org.widnees.widCore.manager.DismissMenuManager;

import java.time.Duration;

public class JoinLeaveListener implements Listener {

    private final Main plugin;
    private final DismissMenuManager dismissManager;

    public JoinLeaveListener(Main plugin, DismissMenuManager dismissManager) {
        this.plugin = plugin;
        this.dismissManager = dismissManager;
    }

    private FileConfiguration getConfig() {
        return plugin.getConfigManager().getModuleConfig("joinleave");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        FileConfiguration config = getConfig();
        event.joinMessage(null);

        boolean isFirstJoin = !player.hasPlayedBefore();

        if (isFirstJoin && config.getBoolean("first-join.enabled", false)) {

            handleFirstJoin(player, config);
        } else if (config.getBoolean("join.enabled", true)) {

            handleJoinEvent(player, config);
        }

        if (plugin.getTempFlyManager() != null) {
            plugin.getTempFlyManager().handleJoin(player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.getTempFlyManager() != null) {
            plugin.getTempFlyManager().handleQuit(player);
        }

        FileConfiguration config = getConfig();
        if (!config.getBoolean("quit.enabled", true))
            return;
        event.quitMessage(null);

        handleQuitEvent(player, config);
    }

    private void handleJoinEvent(Player player, FileConfiguration config) {

        sendMessages(config, "join.messages.all-players", player, null);

        sendMessages(config, "join.messages.only-player", player, player);

        playSound(config.getString("join.sound.all-players", ""), null);
        playSound(config.getString("join.sound.only-player", ""), player);

        boolean welcomeMenuEnabled = config.getBoolean("join.welcome-menu.enabled", false);
        if (welcomeMenuEnabled) {
            dismissManager.openWelcomeMenu(player, config, "join.welcome-menu");
        }
    }

    private void handleFirstJoin(Player player, FileConfiguration config) {

        sendMessages(config, "first-join.messages.all-players", player, null);

        sendMessages(config, "first-join.messages.only-player", player, player);

        playSound(config.getString("first-join.sound.all-players", ""), null);
        playSound(config.getString("first-join.sound.only-player", ""), player);

        boolean welcomeMenuEnabled = config.getBoolean("first-join.welcome-menu.enabled", false);
        if (welcomeMenuEnabled) {
            dismissManager.openWelcomeMenu(player, config, "first-join.welcome-menu");
        }
    }

    private void handleQuitEvent(Player player, FileConfiguration config) {

        sendMessages(config, "quit.messages.all-players", player, null);

        playSound(config.getString("quit.sound.all-players", ""), null);
    }

    public void sendFakeJoinMessage(Player player) {
        FileConfiguration config = getConfig();
        if (!config.getBoolean("fake-messages-on-vanish", true)) {
            return;
        }
        sendMessages(config, "join.messages.all-players", player, null);
        playSound(config.getString("join.sound.all-players", ""), null);
    }

    public void sendFakeQuitMessage(Player player) {
        FileConfiguration config = getConfig();
        if (!config.getBoolean("fake-messages-on-vanish", true)) {
            return;
        }
        sendMessages(config, "quit.messages.all-players", player, null);
        playSound(config.getString("quit.sound.all-players", ""), null);
    }

    private void sendMessages(FileConfiguration config, String basePath, Player player, Player target) {

        String chatMsg = config.getString(basePath + ".chat", "");
        if (!chatMsg.isEmpty()) {
            Component chatComponent = TextParser.parse(replacePlaceholders(chatMsg, player));
            if (target == null) {
                Bukkit.broadcast(chatComponent);
            } else {
                target.sendMessage(chatComponent);
            }
        }

        String titleMsg = config.getString(basePath + ".title", "");
        String subtitleMsg = config.getString(basePath + ".subtitle", "");

        if (!titleMsg.isEmpty() || !subtitleMsg.isEmpty()) {
            Component titleComponent = titleMsg.isEmpty() ? Component.empty() : TextParser.parse(replacePlaceholders(titleMsg, player));
            Component subtitleComponent = subtitleMsg.isEmpty() ? Component.empty() : TextParser.parse(replacePlaceholders(subtitleMsg, player));

            long durationMs = 3500;
            if (basePath.startsWith("first-join")) {
                durationMs = config.getInt("first-join.title-duration", 3500);
            } else if (basePath.startsWith("quit")) {
                durationMs = config.getInt("quit.title-duration", 3500);
            } else {
                durationMs = config.getInt("join.title-duration", 3500);
            }

            Title title = Title.title(
                titleComponent,
                subtitleComponent,
                Title.Times.times(
                    Duration.ofMillis(500),  
                    Duration.ofMillis(durationMs), 
                    Duration.ofMillis(500)   
                )
            );

            if (target == null) {
                Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(title));
            } else {
                target.showTitle(title);
            }
        }

        String actionbarMsg = config.getString(basePath + ".actionbar", "");
        if (!actionbarMsg.isEmpty()) {
            Component actionbarComponent = TextParser.parse(replacePlaceholders(actionbarMsg, player));

            if (target == null) {
                Bukkit.getOnlinePlayers().forEach(p -> p.sendActionBar(actionbarComponent));
            } else {
                target.sendActionBar(actionbarComponent);
            }

        }
    }

    private String replacePlaceholders(String message, Player player) {
        return message
            .replace("%player%", player.getName())
            .replace("%server%", Bukkit.getServer().getName());
    }

    private void playSound(String soundName, Player targetPlayer) {
        if (soundName == null || soundName.isEmpty())
            return;

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase().replace(".", "_").replace(":", "_"));
            if (targetPlayer != null) {
                targetPlayer.playSound(targetPlayer.getLocation(), sound, 1.0f, 1.0f);
            } else {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
                }
            }
            return;
        } catch (IllegalArgumentException ignored) {

        }

        if (targetPlayer != null) {
            targetPlayer.playSound(targetPlayer.getLocation(), soundName, 1.0f, 1.0f);
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), soundName, 1.0f, 1.0f);
            }
        }
    }
}