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
}