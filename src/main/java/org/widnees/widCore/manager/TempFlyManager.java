package org.widnees.widCore.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.widnees.widCore.Main;
import org.widnees.widCore.database.BinaryDataManager;
import org.widnees.widCore.util.FoliaScheduler;

public class TempFlyManager {
    private final Main plugin;
    private final Map<UUID, BinaryDataManager.TempFlyEntry> playersData = new ConcurrentHashMap<UUID, BinaryDataManager.TempFlyEntry>();
    private final Map<UUID, BossBar> activeBossBars = new ConcurrentHashMap<UUID, BossBar>();
    private Object timerTask;
    private Object displayTask;

    public TempFlyManager(Main plugin) {
        this.plugin = plugin;
    }

    public void loadData() {
        this.plugin.getDataManager().loadTempFlyData(data -> {
            this.playersData.clear();
            if (data != null && data.players != null) {
                this.playersData.putAll(data.players);
            }
            this.startTimer();
            this.startDisplayTask();
        });
    }

    public void saveData() {
        if (this.timerTask != null) {
            FoliaScheduler.cancelTask(this.timerTask);
            this.timerTask = null;
        }
        if (this.displayTask != null) {
            FoliaScheduler.cancelTask(this.displayTask);
            this.displayTask = null;
        }
        for (Map.Entry<UUID, BossBar> entry : this.activeBossBars.entrySet()) {
            entry.getValue().removeAll();
        }
        this.activeBossBars.clear();
        for (UUID uuid : this.playersData.keySet()) {
            Player p = Bukkit.getPlayer((UUID)uuid);
            if (p == null || !p.isOnline()) continue;
            p.setAllowFlight(false);
            p.setFlying(false);
        }
        BinaryDataManager.TempFlyData data = new BinaryDataManager.TempFlyData();
        data.players.putAll(this.playersData);
        this.plugin.getDataManager().saveTempFlyData(data).join();
    }

    private void startTimer() {
        FileConfiguration config = this.plugin.getConfigManager().getModuleConfig("tempfly");
        String mode = config.getString("countdown-mode", "flying");
        this.timerTask = FoliaScheduler.runTaskTimerAsync((Plugin)this.plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (!this.playersData.containsKey(uuid)) continue;
                BinaryDataManager.TempFlyEntry entry = this.playersData.get(uuid);
                boolean shouldDecrease = false;
                if (mode.equalsIgnoreCase("always") || mode.equalsIgnoreCase("online")) {
                    shouldDecrease = true;
                } else if (mode.equalsIgnoreCase("flying")) {
                    shouldDecrease = player.isFlying();
                }
                if (shouldDecrease) {
                    if (entry.remainingSeconds > 0L) {
                        --entry.remainingSeconds;
                    }
                    if (entry.remainingSeconds <= 0L) {
                        this.playersData.remove(uuid);
                        FoliaScheduler.runTask((Plugin)this.plugin, () -> {
                            if (player.isOnline()) {
                                player.setAllowFlight(false);
                                player.setFlying(false);
                                Main.sendMessage(this.plugin, (CommandSender)player, this.plugin.getLanguageManager().getMessage("tempfly.expired"));
                                this.removeBossBar(uuid);
                            }
                        });
                    }
                }
                entry.lastUpdateTimestamp = now;
            }
        }, 20L, 20L);
    }

    private void startDisplayTask() {
        BarStyle barStyle;
        BarColor barColor;
        FileConfiguration config = this.plugin.getConfigManager().getModuleConfig("tempfly");
        boolean actionBarEnabled = config.getBoolean("display.actionbar", true);
        boolean bossBarEnabled = config.getBoolean("display.bossbar", false);
        if (!actionBarEnabled && !bossBarEnabled) {
            return;
        }
        String bossBarColorStr = config.getString("display.bossbar-color", "GREEN");
        String bossBarStyleStr = config.getString("display.bossbar-style", "SOLID");
        try {
            barColor = BarColor.valueOf((String)bossBarColorStr.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            barColor = BarColor.GREEN;
        }
        try {
            barStyle = BarStyle.valueOf((String)bossBarStyleStr.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            barStyle = BarStyle.SOLID;
        }
        BarColor finalBarColor = barColor;
        BarStyle finalBarStyle = barStyle;
        this.displayTask = FoliaScheduler.runTaskTimerAsync((Plugin)this.plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (!this.playersData.containsKey(uuid)) {
                    this.removeBossBar(uuid);
                    continue;
                }
                BinaryDataManager.TempFlyEntry entry = this.playersData.get(uuid);
                if (entry.remainingSeconds <= 0L) {
                    this.removeBossBar(uuid);
                    continue;
                }
                String timeFormatted = this.formatTime(entry.remainingSeconds);
                if (actionBarEnabled) {
                    String actionBarMsg = this.plugin.getLanguageManager().getMessage("tempfly.actionbar").replace("%time%", timeFormatted);
                    FoliaScheduler.runTask((Plugin)this.plugin, () -> {
                        if (player.isOnline()) {
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent)new TextComponent(actionBarMsg));
                        }
                    });
                }
                if (!bossBarEnabled) continue;
                String bossBarTitle = this.plugin.getLanguageManager().getMessage("tempfly.bossbar").replace("%time%", timeFormatted);
                FoliaScheduler.runTask((Plugin)this.plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    BossBar bossBar = this.activeBossBars.get(uuid);
                    if (bossBar == null) {
                        bossBar = Bukkit.createBossBar((String)bossBarTitle, (BarColor)finalBarColor, (BarStyle)finalBarStyle, (BarFlag[])new BarFlag[0]);
                        bossBar.addPlayer(player);
                        this.activeBossBars.put(uuid, bossBar);
                    } else {
                        bossBar.setTitle(bossBarTitle);
                    }
                    bossBar.setProgress(1.0);
                });
            }
        }, 20L, 1200L);
    }

    private void removeBossBar(UUID uuid) {
        BossBar bossBar = this.activeBossBars.remove(uuid);
        if (bossBar != null) {
            FoliaScheduler.runTask((Plugin)this.plugin, () -> bossBar.removeAll());
        }
    }

    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();
        BinaryDataManager.TempFlyEntry entry = this.playersData.get(uuid);
        if (entry != null) {
            long now;
            long diffSeconds;
            String mode = this.plugin.getConfigManager().getModuleConfig("tempfly").getString("countdown-mode", "flying");
            if (mode.equalsIgnoreCase("always") && (diffSeconds = ((now = System.currentTimeMillis()) - entry.lastUpdateTimestamp) / 1000L) > 0L) {
                entry.remainingSeconds -= diffSeconds;
            }
            entry.lastUpdateTimestamp = System.currentTimeMillis();
            if (entry.remainingSeconds <= 0L) {
                this.playersData.remove(uuid);
            } else {
                FoliaScheduler.runTask((Plugin)this.plugin, () -> player.setAllowFlight(true));
            }
        }
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        BinaryDataManager.TempFlyEntry entry = this.playersData.get(uuid);
        if (entry != null) {
            entry.lastUpdateTimestamp = System.currentTimeMillis();
            player.setAllowFlight(false);
            player.setFlying(false);
        }
        this.removeBossBar(uuid);
    }

    public void addTime(Player target, long seconds) {
        UUID uuid = target.getUniqueId();
        BinaryDataManager.TempFlyEntry entry = this.playersData.getOrDefault(uuid, new BinaryDataManager.TempFlyEntry(0L, System.currentTimeMillis()));
        entry.remainingSeconds = seconds;
        entry.lastUpdateTimestamp = System.currentTimeMillis();
        this.playersData.put(uuid, entry);
        target.setAllowFlight(true);
    }

    public void removeTime(Player target) {
        UUID uuid = target.getUniqueId();
        this.playersData.remove(uuid);
        target.setAllowFlight(false);
        target.setFlying(false);
        this.removeBossBar(uuid);
    }

    public long getRemainingSeconds(Player target) {
        BinaryDataManager.TempFlyEntry entry = this.playersData.get(target.getUniqueId());
        return entry != null ? entry.remainingSeconds : 0L;
    }

    public String formatTime(long totalSeconds) {
        long days = totalSeconds / 86400L;
        long hours = totalSeconds % 86400L / 3600L;
        long minutes = totalSeconds % 86400L % 3600L / 60L;
        long seconds = totalSeconds % 60L;
        StringBuilder sb = new StringBuilder();
        String dayStr = this.plugin.getLanguageManager().getMessage("time.days");
        String hourStr = this.plugin.getLanguageManager().getMessage("time.hours");
        String minStr = this.plugin.getLanguageManager().getMessage("time.minutes");
        String secStr = this.plugin.getLanguageManager().getMessage("time.seconds");
        if (days > 0L) {
            sb.append(days).append(dayStr).append(" ");
        }
        if (hours > 0L) {
            sb.append(hours).append(hourStr).append(" ");
        }
        if (minutes > 0L) {
            sb.append(minutes).append(minStr).append(" ");
        }
        if (seconds > 0L || sb.length() == 0) {
            sb.append(seconds).append(secStr);
        }
        return sb.toString().trim();
    }

    public long parseDuration(String input) {
        try {
            return Long.parseLong(input);
        }
        catch (NumberFormatException numberFormatException) {
            long totalSeconds = 0L;
            Pattern pattern = Pattern.compile("(\\d+)([smhd])");
            Matcher matcher = pattern.matcher(input.toLowerCase());
            while (matcher.find()) {
                String unit;
                long value = Long.parseLong(matcher.group(1));
                switch (unit = matcher.group(2)) {
                    case "s": {
                        totalSeconds += value;
                        break;
                    }
                    case "m": {
                        totalSeconds += value * 60L;
                        break;
                    }
                    case "h": {
                        totalSeconds += value * 3600L;
                        break;
                    }
                    case "d": {
                        totalSeconds += value * 86400L;
                    }
                }
            }
            return totalSeconds;
        }
    }
        @SuppressWarnings("unused")
    private static final String __wN7e3x9 = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}
