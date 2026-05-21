package org.widnees.widCore.module;

import java.util.List;

public interface Module {
    String getName();
    boolean isEnabled();
    void register();
    void unregister();

    default List<String> getMissingDependencies() {
        return List.of();
    }

    default List<String> getMissingOptionalDependencies() {
        return List.of();
    }
}
