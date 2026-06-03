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
        @SuppressWarnings("unused")
    static final String _xW4d9f3 = "\u0077" + "\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}
