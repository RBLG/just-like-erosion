package teluri.mods.jle.worldgen;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

public class JleChunkGenerator extends ChunkGenerator {

	public JleChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion level) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getGenDepth() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getSeaLevel() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMinY() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getBaseHeight(int x, int z, Types type, LevelHeightAccessor level, RandomState random) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
		// TODO Auto-generated method stub
		
	}

}
