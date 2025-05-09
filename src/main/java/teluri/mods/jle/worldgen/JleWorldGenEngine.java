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
import teluri.mods.jle.floodfill.PackingHelper;
import teluri.mods.jle.worldgen.JleWorldGenEngine.WaterToMountainEngine.MegaChunkProgressData;

public class JleWorldGenEngine {

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
		public Long2ObjectOpenHashMap<BigRegionData> regions = new Long2ObjectOpenHashMap<>();

		public void generateHeight() {
			findWater();
			propagateHeight();
		}

		public void findWater() {

		}

		public void propagateHeight() {
			boolean stable = false;
			while (!stable) {
				for (Entry<BigRegionData> entry : regions.long2ObjectEntrySet()) {
					BigRegionData region = entry.getValue();
					long packed = entry.getLongKey();
					BigRegionData south = regions.get(PackingHelper.add(packed, 0, 1));
					if (south != null) {
						region.propagateHeightOnSouthBorder(south);
					}
					BigRegionData east_ = regions.get(PackingHelper.add(packed, 1, 0));
					if (east_ != null) {
						region.propagateHeightOnEastBorder(east_);
					}
				}
				stable = true;
				for (Entry<BigRegionData> entry : regions.long2ObjectEntrySet()) {
					stable &= entry.getValue().propagateLocalHeight();
				}

			}
		}

		protected void propagateHeightOnBorder(boolean init) {

		}
	}

	@FunctionalInterface
	public static interface ValueSupplier {
		float getValue(int x, int y);
	}

	public static enum ChunkStatus {
		EMPTY, SHORES, SOURCES_SPREAD, SOURCES_SPREAD_IN_RANGE, FlOW_SPREAD
	}

	public static class ChunkRegionProgressData {
		public static final int SIZE = 16;
		ChunkStatus status = ChunkStatus.EMPTY;
		float[] flow; // up to final ouput (height=ground lvl, height+flow=river lvl(?))
		float[] finalheight; // up to final output. stable

		BigRegionData bigregion;
	}
}
