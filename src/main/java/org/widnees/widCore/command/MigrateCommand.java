package org.widnees.widCore.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.widnees.widCore.Main;
import org.widnees.widCore.migrate.MigrateManager;
import org.widnees.widCore.migrate.MigrateResult;

import java.util.*;

public class MigrateCommand implements CommandExecutor, TabCompleter {

    private static final String DRYRUN_FLAG = "--dryrun";
    private final Main plugin;

    public MigrateCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }

        String sub = args[0].toLowerCase();
        MigrateManager mm = plugin.getMigrateManager();

        if (sub.equals("list")) { sendList(sender, mm); return true; }

        if (!mm.getTypes().contains(sub)) {
            Main.sendMessage(plugin, sender,
                    plugin.getLanguageManager().getMessage("migrate.unknown-type")
                            .replace("%type%", sub)
                            .replace("%types%", String.join(", ", mm.getTypes())));
            return true;
        }

        boolean dryRun = false;
        String relativePath = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase(DRYRUN_FLAG)) dryRun = true;
            else relativePath = args[i];
        }

        final boolean fd = dryRun;
        final String fp = relativePath;
        final String fs = sub;

        Main.sendMessage(plugin, sender,
                plugin.getLanguageManager().getMessage("migrate.starting")
                        .replace("%type%", fs)
                        .replace("%dryrun%", fd
                                ? plugin.getLanguageManager().getMessage("migrate.dryrun-label")
                                : ""));

        org.widnees.widCore.util.FoliaScheduler.runTaskLaterAsync(plugin, () -> {
            MigrateResult result = mm.run(fs, fp, fd);
            org.widnees.widCore.util.FoliaScheduler.runTask(plugin, () -> {
                if (result == null) {
                    Main.sendMessage(plugin, sender,
                            plugin.getLanguageManager().getMessage("migrate.folder-not-found")
                                    .replace("%type%", fs)
                                    .replace("%folder%", fp != null ? fp : fs));
                    return;
                }
                for (String msg : result.getMessages()) sender.sendMessage(msg);
                String dryTag = fd ? " §e[DRY-RUN]" : "";
                sender.sendMessage("§6§l--- " + fs.toUpperCase() + " Migrate Sonucu" + dryTag + " §6§l---");
                sender.sendMessage("§a✔ Başarılı : §f" + result.getSuccess());
                sender.sendMessage("§c✘ Başarısız: §f" + result.getFailed());
                sender.sendMessage("§e⚠ Atlandı  : §f" + result.getSkipped());
            });
        }, 1L);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l=== WidCore Migrate ===");
        sender.sendMessage("§e/widcore migrate list              §7- Klasörlerde kaç dosya olduğunu gösterir");
        sender.sendMessage("§e/widcore migrate economy           §7- Economy verilerini migrate eder");
        sender.sendMessage("§e/widcore migrate home              §7- Home verilerini migrate eder");
        sender.sendMessage("§e/widcore migrate warp              §7- Warp konumlarını migrate eder");
        sender.sendMessage("§e/widcore migrate punishment        §7- LiteBans ceza verilerini migrate eder");
        sender.sendMessage("§e/widcore migrate economy --dryrun  §7- Economy için test çalıştırır");
        sender.sendMessage("§e/widcore migrate home --dryrun     §7- Home için test çalıştırır");
        sender.sendMessage("§e/widcore migrate warp --dryrun     §7- Warp için test çalıştırır");
        sender.sendMessage("§e/widcore migrate punishment --dryrun §7- LiteBans için test çalıştırır");
        sender.sendMessage("§7Yetki: §fwidcore.migrate");
    }

    private void sendList(CommandSender sender, MigrateManager mm) {
        sender.sendMessage("§6§l=== Migrate Klasör Durumu ===");
        mm.listFileCounts().forEach((type, count) -> {
            if (count < 0) sender.sendMessage("§e" + type + " §c— klasör yok (migrate/" + type + "/)");
            else sender.sendMessage("§e" + type + " §a→ §f" + count + " §7dosya");
        });
        sender.sendMessage("§7Dizin: §f" + mm.getMigrateRoot().getAbsolutePath());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        MigrateManager mm = plugin.getMigrateManager();
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(mm.getTypes());
            opts.add("list");
            StringUtil.copyPartialMatches(args[0], opts, completions);
        } else if (args.length == 2) {
            StringUtil.copyPartialMatches(args[1], Collections.singletonList(DRYRUN_FLAG), completions);
        }
        Collections.sort(completions);
        return completions;
    }
        @SuppressWarnings("unused")
    private static final String __Wm1g3x9 = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";
}
