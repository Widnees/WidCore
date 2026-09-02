package org.widnees.widCore.migrate;

import java.io.File;

/**
 * Her migrate türünün uygulaması gereken interface.
 */
public interface MigrateHandler {

    /**
     * Migrate türünün kısa adı — komutta kullanılır (economy, home, warp …).
     */
    String getType();

    /**
     * Verilen klasördeki dosyaları okuyup WidCore veritabanına yazar.
     *
     * @param sourceFolder Kaynak dizin
     * @param dryRun       true ise hiçbir kayıt yapılmaz; sadece sayım raporu döner
     * @return İşlem özeti
     */
    MigrateResult migrate(File sourceFolder, boolean dryRun);
}