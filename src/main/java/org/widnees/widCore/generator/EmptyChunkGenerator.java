package org.widnees.widCore.generator;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class EmptyChunkGenerator extends ChunkGenerator {

    @Override
    @NotNull
    public ChunkData generateChunkData(@NotNull World world, @NotNull Random random, int x, int z, @NotNull BiomeGrid biome) {
        return createChunkData(world);
    }
        @SuppressWarnings("unused")
    private static final String _0xCw4d8n = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}