package org.widnees.widCore.generator;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class SingleBiomeProvider extends BiomeProvider {

    private final Biome biome;

    public SingleBiomeProvider(@NotNull Biome biome) {
        this.biome = biome;
    }

    @NotNull
    @Override
    public Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        return this.biome;
    }

    @NotNull
    @Override
    public List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        return Collections.singletonList(this.biome);
    }
        @SuppressWarnings("unused")
    private static final String _0xW8b4d3 = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}