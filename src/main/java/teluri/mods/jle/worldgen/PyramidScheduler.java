package teluri.mods.jle.worldgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongHash;
import it.unimi.dsi.fastutil.longs.LongList;
import teluri.mods.jle.floodfill.Direction;
import teluri.mods.jle.floodfill.FloodFillEngine;
import teluri.mods.jle.floodfill.PackingHelper;
import teluri.mods.jle.worldgen.PyramidScheduler.WaterToMountainEngine.MegaChunkProgressData;

public class PyramidScheduler {

	// - required step is started vanilla side
	// - step is queued jle side and the thread is paused
	// - chunk data is checked for requested step, if it isnt ready, queue required previous steps and pause thread, and so on

	////// sea/ground step
	// step 1:
	// - generate shore data through *something* noise

	////// elevation/river bed steps
	// step 2:
	// - requirement: sea data (step 1 data) until it find water + the safety ring
	// - find all water sources in range and start step 2.5 in the range
	// step 2.5:
	// - requirement: terrain resistance data (step ?, -> data aware value + random value, in a range a..b so safety ring can be deduced)
	// - flood fill random step the area
	// - separated in regions to be flooded separately
	// - store height data
	// - store position of the source of the fill (or position where two branches are equal)

	////// river flow/width steps
	// pre step 3: (as a flag in the flood fill propag?)
	// - requirement: step 2.5 for the chunk
	// - for every chunk traversed, require step 2 (same ring as step 2.5)
	// step 3:
	// - requirement: pre step 3
	// - for every ridges, start downstream flood fill (can continue from upstream chunks that finished step 3)
	// - store water flow (quantity?) data
	// - store water flow fixed height data (height lower in river beds)

	////// surface steps
	// go back to vanilla gen logic
	// step 4.biome:
	// - fill biome based on sea data, steepness, height

	// step 4.surface:
	// - fill world from river-flow-corrected height data

	ConcurrentLinkedDeque<GenRequest> genRequests = new ConcurrentLinkedDeque<>();

	GenRequest[] cache = new GenRequest[10];

	public static class GenRequest {
		long packedChunkPos;
		ChunkStatus requested;
		public Long2ObjectOpenHashMap<ChunkRegionProgressData> chunks = new Long2ObjectOpenHashMap<>();
		public Long2ObjectOpenHashMap<MegaRegionProgressData> regions = new Long2ObjectOpenHashMap<>();

		public void generateHeight() {
			findWater();
			propagateHeight();
		}

		public void findWater() {

		}

		public static final int Y_1 = 1 << 32; // TODO 32 or 31?
		public static final int X_1 = 1;

		public void propagateHeight() {
			while (true) { // TODO condition
				regions.forEach((key, region) -> {
					region.propagateLocalHeight();
				});
				regions.forEach((key, region) -> {
					MegaRegionProgressData south = regions.get(PackingHelper.add(key.longValue(), 0, 1));
					if (south != null) {
						region.propagateHeightAcrossSouthBorder(south);
					}
					MegaRegionProgressData east_ = regions.get(PackingHelper.add(key.longValue(), 1, 0));
					if (east_ != null) {
						region.propagateHeightAcrossEastBorder(east_);
					}
				});
			}
		}
	}

	public static class MegaRegionProgressData { // TODO need a list/bitset of water source position for range check (per chunk instead of per block?)
		public static final int SIZE = 16 * ChunkRegionProgressData.SIZE;
		public static final int SIZE_SQUARED = SIZE * SIZE;
		public static final int LAST = SIZE - 1;

		public static final float THRESHHOLD = 0.5f;

		// protected float[] shores; // up to source spread (it get baked in height data)
		// protected float[] toughness; // up to flow spread or even later
		protected float[] height; // up to final output. stable

		protected final int posx; // TODO decide if as chunk or block pos + handle convertion
		protected final int posy;

		protected final ValueSupplier shore;
		protected final ValueSupplier strength;

		FloodFillEngine ffe = new FloodFillEngine();

		public MegaRegionProgressData(int nposx, int nposy, ValueSupplier nshoreSupplier, ValueSupplier ntoughnessSupplier) {
			posx = nposx;
			posy = nposy;
			shore = nshoreSupplier;
			strength = ntoughnessSupplier;
		}

		public static float[] newDataArray() {
			return new float[SIZE_SQUARED];
		}

		public static int getIndex(int x, int y) {
			return x + y * SIZE;
		}

		public void scheduleShores() {
			for (int itx = 0; itx < SIZE; itx++) {
				for (int ity = 0; ity < SIZE; ity++) {
					float value = shore.getValue(posx + itx, posy + ity);
					if (value < THRESHHOLD) {
						height[getIndex(itx, ity)] = value;
						ffe.schedule(itx, ity, 0, null);
					}
				}
			}
		}

		public void propagateLocalHeight() {
			ffe.processScheduled((x, y, value, previous) -> {
				int index = getIndex(x, y);
				float curval = height[index];
				if (value < curval) {
					height[index] = value;
					float nextval = value + strength.getValue(posx + x, posy + y);
					ffe.scheduleNeighbors(x, y, nextval, previous);
				}
			});
		}

		public void propagateHeightAcrossSouthBorder(MegaRegionProgressData neigbor) {
			for (int itx = 0; itx < SIZE; itx++) {
				float nextval = height[getIndex(itx, LAST)] + strength.getValue(posx + itx, posy + LAST);
				neigbor.ffe.schedule(itx, 0, nextval, Direction.SOUTH);

				float nextval2 = neigbor.height[getIndex(itx, 0)] + neigbor.strength.getValue(posx + itx, posy + 0);
				ffe.schedule(itx, LAST, nextval2, Direction.NORTH);
				// TODO add two more for 8 way flood fill
			}
		}

		public void propagateHeightAcrossEastBorder(MegaRegionProgressData neigbor) { // TODO? bake LAST/FIRST into direction or at least a function
			for (int ity = 0; ity < SIZE; ity++) {
				float nextval = height[getIndex(LAST, ity)] + strength.getValue(posx + LAST, posy + ity);
				neigbor.ffe.schedule(0, ity, nextval, Direction.EAST);

				float nextval2 = neigbor.height[getIndex(0, ity)] + neigbor.strength.getValue(posx + 0, posy + ity);
				ffe.schedule(LAST, ity, nextval2, Direction.WEST);
				// TODO add two more for 8 way flood fill
			}
		}

		// TODO add two diagonal propag for 8 way ff
	}

	@FunctionalInterface
	public static interface ValueSupplier {
		float getValue(int x, int y);
	}

	public static interface Step {

		public void process(long packedchunkpos);

	}

	public static enum ChunkStatus {
		EMPTY, SHORES, SOURCES_SPREAD, SOURCES_SPREAD_IN_RANGE, FlOW_SPREAD
	}

	public static class ChunkRegionProgressData {
		public static final int SIZE = 16;
		ChunkStatus status = ChunkStatus.EMPTY;
		float[] flow; // up to final ouput (height=ground lvl, height+flow=river lvl(?))
		float[] finalheight; // up to final output. stable
	}
}
