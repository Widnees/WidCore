package org.widnees.widCore.migrate;

import org.widnees.widCore.Main;

import java.io.File;
import java.util.*;

/**
 * Migrate handler'larını kaydeden ve /widcore migrate komutuna hizmet eden yönetici.
 *
 * Klasör düzeni {@code src/main/resources/migrate} altından
 * {@code plugins/WidCore/migrate/} dizinine kopyalanır:
 *   migrate/MIGRATE.txt
 *   migrate/economy/
 *   migrate/home/
 *   migrate/warp/
 *   migrate/punishment/
 */
public class MigrateManager {

    private static final String RESOURCE_ROOT = "migrate";
    private static final String GUIDE_FILE = "MIGRATE.txt";
    private static final String FOLDER_MARKER = ".keep";
    private static final String[] TYPE_FOLDERS = {"economy", "home", "warp", "punishment"};

    private final Main plugin;
    private final File migrateRoot;
    private final Map<String, MigrateHandler> handlers = new LinkedHashMap<>();

    public MigrateManager(Main plugin) {
        this.plugin = plugin;
        this.migrateRoot = new File(plugin.getDataFolder(), RESOURCE_ROOT);
        extractFromResources();
    }

    /**
     * migrate/ ağacını JAR (resources) içinden data folder'a çıkarır.
     * MIGRATE.txt her açılışta kaynaktan güncellenir.
     * Tür klasörleri yalnızca yoksa {@code saveResource} ile oluşturulur.
     */
    private void extractFromResources() {
        saveResourceFile(RESOURCE_ROOT + "/" + GUIDE_FILE, true);

        for (String type : TYPE_FOLDERS) {
            saveResourceFile(RESOURCE_ROOT + "/" + type + "/" + FOLDER_MARKER, false);
        }
    }

    private void saveResourceFile(String resourcePath, boolean replace) {
        if (plugin.getResource(resourcePath) == null) {
            plugin.getLogger().warning("Kaynak bulunamadı: " + resourcePath);
            return;
        }

        File outFile = new File(plugin.getDataFolder(), resourcePath.replace('/', File.separatorChar));
        if (!replace && outFile.exists()) {
            return;
        }

        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Klasör oluşturulamadı: " + parent.getPath());
            return;
        }

        try {
            plugin.saveResource(resourcePath, replace);
        } catch (Exception e) {
            plugin.getLogger().warning(resourcePath + " oluşturulamadı: " + e.getMessage());
        }
    }

    public void registerHandler(MigrateHandler handler) {
        handlers.put(handler.getType().toLowerCase(), handler);
    }

    public Set<String> getTypes() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    /**
     * Belirtilen tür için migrate işlemini başlatır.
     *
     * @param type         Handler tipi ("economy", "home" …)
     * @param relativePath migrate/ altında bakılacak alt klasör yolu.
     *                     null verilirse tür adı klasör olarak kullanılır.
     * @param dryRun       Gerçek kayıt yapma, sadece raporla
     * @return Sonuç; handler veya klasör bulunamazsa null
     */
    public MigrateResult run(String type, String relativePath, boolean dryRun) {
        MigrateHandler handler = handlers.get(type.toLowerCase());
        if (handler == null) return null;

        String folderName = (relativePath != null && !relativePath.isEmpty())
                ? relativePath
                : type.toLowerCase();

        File sourceFolder = new File(migrateRoot, folderName);
        if (!sourceFolder.exists() || !sourceFolder.isDirectory()) {
            return null;  // klasör yok sinyali
        }

        return handler.migrate(sourceFolder, dryRun);
    }

    /**
     * Her tür için migrate/ altındaki varsayılan klasörde kaç dosya olduğunu döner.
     */
    public Map<String, Integer> listFileCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String type : handlers.keySet()) {
            File dir = new File(migrateRoot, type);
            if (dir.isDirectory()) {
                File[] files = dir.listFiles(f -> f.isFile() && isCountableFile(f.getName()));
                counts.put(type, files != null ? files.length : 0);
            } else {
                counts.put(type, -1); // klasör yok
            }
        }
        return counts;
    }

    private boolean isCountableFile(String name) {
        if (name.startsWith(".")) return false;
        if (name.equalsIgnoreCase(GUIDE_FILE)) return false;
        if (name.equalsIgnoreCase(FOLDER_MARKER)) return false;
        return true;
    }

    public File getMigrateRoot() {
        return migrateRoot;
    }

    public Main getPlugin() {
        return plugin;
    }
}