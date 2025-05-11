package teluri.mods.jle.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.blending.Blender;
import teluri.mods.jle.gen.JleWorldGenEngine;
import teluri.mods.jle.gen.JleWorldGenEngine.GenRequest;

@Mixin(NoiseBasedChunkGeneratorMixin.class)
public abstract class NoiseBasedChunkGeneratorMixin extends ChunkGenerator {

	JleWorldGenEngine wgengine = new JleWorldGenEngine();

	@Overwrite()
	public ChunkAccess doFill(Blender blender, StructureManager structureManager, RandomState random, ChunkAccess chunk, int minCellY, int cellCountY) {
		NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(chunkAccess -> this.createNoiseChunk(chunkAccess, structureManager, blender, random));
		Heightmap heightmapOcean = chunk.getOrCreateHeightmapUnprimed(Types.OCEAN_FLOOR_WG);
		Heightmap heightmapSurface = chunk.getOrCreateHeightmapUnprimed(Types.WORLD_SURFACE_WG);
		ChunkPos chunkPos = chunk.getPos();
		int chunkMinBlockX = chunkPos.getMinBlockX();
		int chunkMinBlockZ = chunkPos.getMinBlockZ();
		Aquifer aquifer = noiseChunk.aquifer();
		noiseChunk.initializeForFirstCellX();
		MutableBlockPos mutableBlockPos = new MutableBlockPos();
		int cellWidth = noiseChunk.cellWidth();
		int cellHeight = noiseChunk.cellHeight();
		int cellWidthDiv16 = 16 / cellWidth;
		int cellWidthDiv16Two = 16 / cellWidth;

		GenRequest completed = this.wgengine.request(chunkPos.x, chunkPos.z);

		for (int o = 0; o < cellWidthDiv16; o++) {
			noiseChunk.advanceCellX(o);

			for (int p = 0; p < cellWidthDiv16Two; p++) {
				int q = chunk.getSectionsCount() - 1;
				LevelChunkSection levelChunkSection = chunk.getSection(q);

				for (int r = cellCountY - 1; r >= 0; r--) {
					noiseChunk.selectCellYZ(r, p);

					for (int s = cellHeight - 1; s >= 0; s--) {
						int cellEndBlockY = (minCellY + r) * cellHeight + s;
						int localY = cellEndBlockY & 15;
						int v = chunk.getSectionIndex(cellEndBlockY);
						if (q != v) {
							q = v;
							levelChunkSection = chunk.getSection(v);
						}

						double realY = (double) s / cellHeight;
						noiseChunk.updateForY(cellEndBlockY, realY);

						for (int w = 0; w < cellWidth; w++) {
							int cellEndBlockX = chunkMinBlockX + o * cellWidth + w;
							int localX = cellEndBlockX & 15;
							double realX = (double) w / cellWidth;
							noiseChunk.updateForX(cellEndBlockX, realX);

							for (int z = 0; z < cellWidth; z++) {
								int cellEndBlockZ = chunkMinBlockZ + p * cellWidth + z;
								int localZ = cellEndBlockZ & 15;
								double realZ = (double) z / cellWidth;
								noiseChunk.updateForZ(cellEndBlockZ, realZ);
								// BlockState blockState = noiseChunk.getInterpolatedState();
								// TODO actual gen instead of that hack
								BlockState blockState = completed.getHeightInCenter(localX, localZ) < localY ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState();
								if (blockState == null) {
									blockState = this.settings.value().defaultBlock();
								}

								if (blockState != Blocks.AIR.defaultBlockState() && !SharedConstants.debugVoidTerrain(chunk.getPos())) {
									levelChunkSection.setBlockState(localX, localY, localZ, blockState, false);
									heightmapOcean.update(localX, cellEndBlockY, localZ, blockState);
									heightmapSurface.update(localX, cellEndBlockY, localZ, blockState);
									if (aquifer.shouldScheduleFluidUpdate() && !blockState.getFluidState().isEmpty()) {
										mutableBlockPos.set(cellEndBlockX, cellEndBlockY, cellEndBlockZ);
										chunk.markPosForPostprocessing(mutableBlockPos);
									}
								}
							}
						}
					}
				}
			}

			noiseChunk.swapSlices();
		}

		noiseChunk.stopInterpolation();
		return chunk;
	}

	@Shadow
	public final Holder<NoiseGeneratorSettings> settings;

	@Shadow
	protected abstract NoiseChunk createNoiseChunk(ChunkAccess chunkAccess, StructureManager structureManager, Blender blender, RandomState random);

	public NoiseBasedChunkGeneratorMixin() {
		super(null);
		settings = null;
	}
}
