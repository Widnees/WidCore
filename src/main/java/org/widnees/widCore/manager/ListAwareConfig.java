package org.widnees.widCore.manager;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * A FileConfiguration wrapper that automatically supports list-format messages.
 * When getString() is called on a key whose value is a YAML list, the lines are
 * joined with "\n" instead of returning null. This allows config authors to write:
 *
 *   message:
 *     - "Line 1"
 *     - "Line 2"
 *
 * ...instead of a single "message: \"Line 1\nLine 2\"" string, and have both formats
 * work transparently everywhere getString() is used.
 */
public class ListAwareConfig extends YamlConfiguration {

    /**
     * Wraps an existing FileConfiguration into a ListAwareConfig.
     * Copies all values from the source config.
     */
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