package org.widnees.widCore.manager;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class ListAwareConfig extends YamlConfiguration {

    public static ListAwareConfig from(FileConfiguration source) {
        ListAwareConfig wrapped = new ListAwareConfig();
        if (source != null) {
            for (String key : source.getKeys(true)) {
                wrapped.set(key, source.get(key));
            }
        }
        return wrapped;
    }

    @Override
    public String getString(String path) {
        if (isList(path)) {
            List<String> lines = getStringList(path);
            if (lines != null && !lines.isEmpty()) {
                return String.join("\n", lines);
            }
            return null;
        }
        return super.getString(path);
    }

    @Override
    public String getString(String path, String def) {
        if (isList(path)) {
            List<String> lines = getStringList(path);
            if (lines != null && !lines.isEmpty()) {
                return String.join("\n", lines);
            }
            return def;
        }
        return super.getString(path, def);
    }
}