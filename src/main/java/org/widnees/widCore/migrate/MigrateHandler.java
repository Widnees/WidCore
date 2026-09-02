package org.widnees.widCore.migrate;

import java.io.File;

public interface MigrateHandler {

    String getType();

    MigrateResult migrate(File sourceFolder, boolean dryRun);
}