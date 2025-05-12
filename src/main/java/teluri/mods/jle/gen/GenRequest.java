package teluri.mods.jle.gen;

import org.joml.Vector2i;
import org.slf4j.Logger;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import teluri.mods.jle.JustLikeErosion;
import teluri.mods.jle.floodfill.Direction;
import teluri.mods.jle.floodfill.FloodFillEngine;
import teluri.mods.jle.floodfill.PackingHelper;
import teluri.mods.jle.gen.JleWorldGenEngine.ChunkData;
import teluri.mods.jle.gen.JleWorldGenEngine.HeightSupplier;

public class GenRequest {
	public static final Logger LOGGER = JustLikeErosion.LOGGER;

	// long packedChunkPos;
	long packedRegionPos;
	Vector2i oreg = new Vector2i();
	Vector2i ochk = new Vector2i();

	// ChunkStatus requested;
	public Long2ObjectOpenHashMap<ChunkData> chunks = new Long2ObjectOpenHashMap<>();
	public Long2ObjectOpenHashMap<BigRegionData> regions = new Long2ObjectOpenHashMap<>();

	JleWorldGenEngine jwge; // TODO replace it by a supplier

	FloodFillEngine ffe = new FloodFillEngine.FloodFillEngineValueless();

	public GenRequest(JleWorldGenEngine njwge, int wox, int woy) {
		jwge = njwge;
		ochk.x = Math.floorDiv(wox, ChunkData.SIZE);
		ochk.y = Math.floorDiv(woy, ChunkData.SIZE);
		oreg.x = Math.floorDiv(wox, BigRegionData.SIZE);
		oreg.y = Math.floorDiv(woy, BigRegionData.SIZE);
		// this.packedChunkPos = PackingHelper.pack(ochk.x, ochk.y);
		this.packedRegionPos = PackingHelper.pack(oreg.x, oreg.y);
	}

	public HeightSupplier getOutput() {
		BigRegionData region = this.regions.get(this.packedRegionPos);
		if (region == null) {
			LOGGER.warn(String.format("request center at %d,%d (%d) was null", oreg.x, oreg.y, packedRegionPos));
			return (wx, wy) -> -40;
		}
		return region::getHeight;
	}

	public void unload() {
		regions.forEach((key, region) -> {
			region.save();
		});
		chunks.forEach((key, chunk) -> {
			chunk.save();
		});
	}

	@Deprecated
	public float getHeight(int wx, int wy) { // TODO replace y by z on jle side
		// int regx = Math.floorDiv(wx, BigRegionData.SIZE);
		// int regy = Math.floorDiv(wy, BigRegionData.SIZE);
		int owx = Math.floorMod(wx, BigRegionData.SIZE);
		int owy = Math.floorMod(wy, BigRegionData.SIZE);

		// long packed = PackingHelper.pack(regx, regy);
		BigRegionData region = this.regions.get(this.packedRegionPos);

		return region == null ? -20 : region.height[BigRegionData.getIndex(owx, owy)]; // TODO properly handle center (?)
	}

	public void requestHeight() {
		LOGGER.info(String.format("starting gen for chunk %d,%d", ochk.x, ochk.y));
		LOGGER.info("finding water...");
		findWater();
		LOGGER.info("filling height...");
		propagateHeight();
		LOGGER.info("done!");
	}

	public static final int SAFETY_RANGE = 2;// TODO replace by range estimate from toughness

	public void findWater() {
		long[] range = new long[] { Long.MAX_VALUE / 2 }; // HACK replace with proper boxing

		ffe.schedule(oreg.x, oreg.y, 0, Direction.NONE);

		LOGGER.info("starting floodfill");
		ffe.processScheduled((x, y) -> true, (regx, regy, unused, dir) -> {
			long packed = PackingHelper.pack(regx, regy);
			if (this.regions.containsKey(packed)) {
				return true;
			}
			BigRegionData region = jwge.loadBigRegion(regx, regy);
			regions.put(packed, region);

			if (region.rangedStable) {
				return true;
			}
			boolean hasWater = region.scheduleShores();

			long ndist = oreg.gridDistance(regx, regy);
			long nsafedist = ndist + SAFETY_RANGE;

			if (nsafedist < range[0] && hasWater) {
				LOGGER.info("required range reduced");
				range[0] = nsafedist;
			} else if (hasWater) {
				// LOGGER.info("water in range but not closer, range unchanged");
			}

			if (ndist < range[0]) {
				// LOGGER.info(String.format("distance %d out of range %d, scheduling neigbors", ndist, range[0]));
				ffe.schedule(regx, regy, unused, dir);
			}
			return false;
		});
	}

	public void propagateHeight() {
		BigRegionData center = regions.get(this.packedRegionPos);
		if (center == null) {
			LOGGER.info(String.format("center was null at %d,%d", oreg.x, oreg.y));
			return;
		} else if (center.rangedStable) {
			return;
		}
		boolean stable = false;
		while (!stable) {
			LOGGER.info("propagating regions borders");
			for (Entry<BigRegionData> entry : regions.long2ObjectEntrySet()) {
				BigRegionData region = entry.getValue();
				long packed = entry.getLongKey();
				long packedCheck = PackingHelper.pack(region.oregx, region.oregy);
				if (packed != packedCheck) {
					LOGGER.warn(String.format("incoherent packed oreg %d,%d: %d wasnt %d", region.oregx, region.oregy, packed, packedCheck));
				}
				BigRegionData south = regions.get(PackingHelper.add(packed, 0, 1));
				if (south != null) {
					region.propagateHeightOnSouthBorder(south);
				}
				BigRegionData east_ = regions.get(PackingHelper.add(packed, 1, 0));
				if (east_ != null) {
					region.propagateHeightOnEastBorder(east_);
				}
			}
			LOGGER.info("propagating regions locally");
			// stable = true;
			// for (Entry<BigRegionData> entry : regions.long2ObjectEntrySet()) {
			// stable &= entry.getValue().propagateLocalHeight();
			// LOGGER.info("local height propagated, moving to next region");
			// }
			stable = regions.long2ObjectEntrySet().parallelStream().allMatch((entry) -> {
				return entry.getValue().propagateLocalHeight();
			});
		}
		LOGGER.info("propagation done");

		center.rangedStable = true;
	}

}