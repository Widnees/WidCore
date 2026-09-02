package org.widnees.widCore.migrate;

import org.widnees.widCore.Main;
import org.widnees.widCore.database.BinaryDataManager.PunishmentEntry;
import org.widnees.widCore.manager.PunishmentManager;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class LitebansPunishmentMigrator implements MigrateHandler {

    private final Main plugin;

    public LitebansPunishmentMigrator(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getType() { return "punishment"; }

    @Override
    public MigrateResult migrate(File sourceFolder, boolean dryRun) {
        MigrateResult result = new MigrateResult();
        PunishmentManager pm = plugin.getPunishmentManager();
        if (pm == null) {
            result.addMessage("§cPunishment modülü aktif değil.");
            return result;
        }
        File[] files = sourceFolder.listFiles(
                f -> f.isFile() && f.getName().toLowerCase().endsWith(".sql"));
        if (files == null || files.length == 0) {
            result.addMessage(plugin.getLanguageManager()
                    .getMessage("migrate.no-files-found"));
            return result;
        }
        for (File file : files) {
            result.addMessage("§7Dosya: §e" + file.getName());
            try {
                processBans(file, pm, dryRun, result);
                processMutes(file, pm, dryRun, result);
                processHistory(file, pm, dryRun, result);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "migrate/punishment: " + file.getName(), e);
                result.addMessage("§c" + file.getName() + " hatası: " + e.getMessage());
            }
        }
        if (!dryRun) pm.savePunishments();
        return result;
    }

    private void processBans(File file, PunishmentManager pm,
            boolean dryRun, MigrateResult result) throws IOException {
        List<List<String>> rows = LitebansDumpParser.parseTable(file, "litebans_bans");
        Map<String, List<List<String>>> byUuid = new LinkedHashMap<>();
        Map<String, List<List<String>>> byIp = new LinkedHashMap<>();
        for (List<String> row : rows) {
            boolean isIp = LitebansDumpParser.isBitTrue(LitebansDumpParser.col(row, 16));
            String uuid = LitebansDumpParser.col(row, 1);
            String ipv  = LitebansDumpParser.col(row, 2);
            if (isIp) { if (ipv != null && !ipv.isEmpty() && !ipv.equals("#"))
                byIp.computeIfAbsent(ipv, k -> new ArrayList<>()).add(row);
            } else { if (uuid != null && !uuid.isEmpty())
                byUuid.computeIfAbsent(uuid, k -> new ArrayList<>()).add(row); }
        }
        for (Map.Entry<String, List<List<String>>> e : byUuid.entrySet()) {
            UUID uuid; try { uuid = UUID.fromString(e.getKey()); }
            catch (IllegalArgumentException ex) { result.addSkipped(); continue; }
            List<List<String>> pr = sorted(e.getValue());
            for (int i = 0; i < pr.size() - 1; i++) {
                PunishmentEntry he = toEntry(pr.get(i));
                if (he == null) { result.addSkipped(); continue; }
                if (!dryRun) pm.importBanHistory(uuid, he);
                result.addSuccess();
            }
            PunishmentEntry le = toEntry(pr.get(pr.size() - 1));
            if (le == null) { result.addSkipped(); continue; }
            if (pm.isBanned(uuid)) { if (!dryRun) pm.importBanHistory(uuid, le); }
            else { if (!dryRun) pm.importBan(uuid, le); }
            result.addSuccess();
        }
        for (Map.Entry<String, List<List<String>>> e : byIp.entrySet()) {
            PunishmentEntry le = toEntry(sorted(e.getValue()).get(e.getValue().size() - 1));
            if (le == null) { result.addSkipped(); continue; }
            if (!dryRun) pm.importIpBan(e.getKey(), le);
            result.addSuccess();
        }
    }

    private void processMutes(File file, PunishmentManager pm,
            boolean dryRun, MigrateResult result) throws IOException {
        List<List<String>> rows = LitebansDumpParser.parseTable(file, "litebans_mutes");
        Map<String, List<List<String>>> byUuid = new LinkedHashMap<>();
        Map<String, List<List<String>>> byIp = new LinkedHashMap<>();
        for (List<String> row : rows) {
            boolean isIp = LitebansDumpParser.isBitTrue(LitebansDumpParser.col(row, 16));
            String uuid = LitebansDumpParser.col(row, 1);
            String ipv  = LitebansDumpParser.col(row, 2);
            if (isIp) { if (ipv != null && !ipv.isEmpty())
                byIp.computeIfAbsent(ipv, k -> new ArrayList<>()).add(row);
            } else { if (uuid != null && !uuid.isEmpty())
                byUuid.computeIfAbsent(uuid, k -> new ArrayList<>()).add(row); }
        }
        for (Map.Entry<String, List<List<String>>> e : byUuid.entrySet()) {
            UUID uuid; try { uuid = UUID.fromString(e.getKey()); }
            catch (IllegalArgumentException ex) { result.addSkipped(); continue; }
            List<List<String>> pr = sorted(e.getValue());
            for (int i = 0; i < pr.size() - 1; i++) {
                PunishmentEntry he = toEntry(pr.get(i));
                if (he == null) { result.addSkipped(); continue; }
                if (!dryRun) pm.importMuteHistory(uuid, he);
                result.addSuccess();
            }
            PunishmentEntry le = toEntry(pr.get(pr.size() - 1));
            if (le == null) { result.addSkipped(); continue; }
            if (pm.isMuted(uuid)) { if (!dryRun) pm.importMuteHistory(uuid, le); }
            else { if (!dryRun) pm.importMute(uuid, le); }
            result.addSuccess();
        }
        for (Map.Entry<String, List<List<String>>> e : byIp.entrySet()) {
            PunishmentEntry le = toEntry(sorted(e.getValue()).get(e.getValue().size() - 1));
            if (le == null) { result.addSkipped(); continue; }
            if (!dryRun) pm.importIpMute(e.getKey(), le);
            result.addSuccess();
        }
    }

    private void processHistory(File file, PunishmentManager pm,
            boolean dryRun, MigrateResult result) throws IOException {
        List<List<String>> rows = LitebansDumpParser.parseTable(file, "litebans_history");
        for (List<String> row : rows) {
            String uuidStr = LitebansDumpParser.col(row, 3);
            String ip = LitebansDumpParser.col(row, 4);
            if (uuidStr == null || ip == null || ip.isEmpty()
                    || ip.equals("#") || ip.equalsIgnoreCase("CONSOLE")) continue;
            UUID uuid; try { uuid = UUID.fromString(uuidStr); }
            catch (IllegalArgumentException ex) { continue; }
            if (!dryRun) pm.importLastKnownIp(uuid, ip);
        }
    }

    private List<List<String>> sorted(List<List<String>> rows) {
        rows.sort(Comparator.comparingLong(r ->
                LitebansDumpParser.parseLong(LitebansDumpParser.col(r, 10))));
        return rows;
    }

    private PunishmentEntry toEntry(List<String> row) {
        try {
            long until = LitebansDumpParser.parseLong(LitebansDumpParser.col(row, 11));
            long expiry = (until == 0L) ? -1L : until;
            PunishmentEntry pe = new PunishmentEntry(
                    expiry,
                    LitebansDumpParser.col(row, 3),
                    LitebansDumpParser.parseUuid(LitebansDumpParser.col(row, 4)),
                    LitebansDumpParser.parseLong(LitebansDumpParser.col(row, 10)));
            String rb = LitebansDumpParser.col(row, 7);
            if (rb != null && !rb.isEmpty()) pe.removedBy = rb;
            return pe;
        } catch (Exception e) { return null; }
    }
}