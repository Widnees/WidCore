package org.widnees.widCore.manager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.widnees.widCore.Main;

public class AntiMobSpawnManager {
    private final Main plugin;
    private FileConfiguration mobConfig;
    private final Set<EntityType> globalBlockedMobs = new HashSet<EntityType>();
    private final Map<String, Set<EntityType>> worldBlockedMobs = new HashMap<String, Set<EntityType>>();

    public AntiMobSpawnManager(Main plugin) {
        this.plugin = plugin;
        this.loadConfig();
    }

    public void loadConfig() {
        this.mobConfig = this.plugin.getConfigManager().getModuleConfig("antimobspawn");
        this.globalBlockedMobs.clear();
        this.worldBlockedMobs.clear();
        List<String> globalList = this.mobConfig.getStringList("all_worlds");
        for (String mobName : globalList) {
            try {
                EntityType type = EntityType.valueOf((String)mobName.toUpperCase());
                this.globalBlockedMobs.add(type);
            }
            catch (IllegalArgumentException e) {
                this.plugin.getLogger().warning(this.plugin.getLanguageManager().getMessage("antimobspawn.invalid-mob-global").replace("%mob%", mobName));
            }
        }
        if (this.mobConfig.isConfigurationSection("worlds")) {
            for (String worldName : this.mobConfig.getConfigurationSection("worlds").getKeys(false)) {
                List<String> worldList = this.mobConfig.getStringList("worlds." + worldName);
                HashSet<EntityType> worldSet = new HashSet<EntityType>();
                for (String mobName : worldList) {
                    try {
                        EntityType type = EntityType.valueOf((String)mobName.toUpperCase());
                        worldSet.add(type);
                    }
                    catch (IllegalArgumentException e) {
                        String msg = this.plugin.getLanguageManager().getMessage("antimobspawn.invalid-mob-world").replace("%world%", worldName).replace("%mob%", mobName);
                        this.plugin.getLogger().warning(msg);
                    }
                }
                this.worldBlockedMobs.put(worldName.toLowerCase(), worldSet);
            }
        }
    }

    public boolean isSpawnBlocked(LivingEntity entity, World world) {
        EntityType type = entity.getType();
        if (this.globalBlockedMobs.contains(type)) {
            return true;
        }
        String worldName = world.getName().toLowerCase();
        return this.worldBlockedMobs.containsKey(worldName) && this.worldBlockedMobs.get(worldName).contains(type);
    }
        @SuppressWarnings("unused")
    private static final String _0xWb8d2e = "\u0077\u0069\u0064" + "\u006e\u0065" + "\u0065\u0073";

}
