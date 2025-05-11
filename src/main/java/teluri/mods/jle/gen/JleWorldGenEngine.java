package teluri.mods.jle.gen;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.joml.Vector2i;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import teluri.mods.jle.floodfill.FloodFillEngine;
import teluri.mods.jle.floodfill.PackingHelper;

public class JleWorldGenEngine {

	ConcurrentLinkedDeque<GenRequest> genRequests = new ConcurrentLinkedDeque<>();

	public static final int CACHE_SIZE = 10;

	ArrayDeque<GenRequest> cache = new ArrayDeque<>(CACHE_SIZE);

	protected final BadNoise2D shoreProv = new BadNoise2D(20);
	protected final BadNoise2D strenProv = new BadSteepNoise2D(17);

	public GenRequest request(int chkx, int chky) {
		GenRequest request = new GenRequest(this, chkx, chky);
		request.generateHeight();

		if (CACHE_SIZE <= cache.size()) {
			GenRequest uncachedRequest = cache.pop();
			uncachedRequest.unload();
		}
		cache.push(request);
		return request;
	}

	public BigRegionData loadBigRegion(int regx, int regy) {
		for (GenRequest request : cache) {
			long packed = PackingHelper.pack(regx, regy);
			BigRegionData region = request.regions.remove(packed);
			if (region != null) {
				return region;
			}
		}
		// TODO check disk if it already exist and only then make a new one
		return new BigRegionData(regx, regy, shoreProv, strenProv); // TODO shore/strength supplier
	}

	public static class GenRequest {
		long packedChunkPos;
		Vector2i oreg = new Vector2i();
		Vector2i ochk = new Vector2i();

		// ChunkStatus requested;
		public Long2ObjectOpenHashMap<ChunkData> chunks = new Long2ObjectOpenHashMap<>();
		public Long2ObjectOpenHashMap<BigRegionData> regions = new Long2ObjectOpenHashMap<>();
		public BigRegionData center;

		JleWorldGenEngine jwge; // TODO replace it by a supplier

		FloodFillEngine ffe = new FloodFillEngine();

		public GenRequest(JleWorldGenEngine njwge, int nchkposx, int nchkposy) {
			jwge = njwge;
			ochk.set(nchkposx, nchkposy);
			oreg.set(ochk).mul(ChunkData.SIZE).div(BigRegionData.SIZE);

		}

		public void unload() {
			regions.forEach((key, region) -> {
				region.save();
			});
			chunks.forEach((key, chunk) -> {
				chunk.save();
			});
		}

		public float getHeightInCenter(int wx, int wy) { // TODO replace y by z on jle side
			// int regx = Math.floorDiv(wx, BigRegionData.SIZE);
			// int regy = Math.floorDiv(wy, BigRegionData.SIZE);
			int owx = Math.floorMod(wx, BigRegionData.SIZE);
			int owy = Math.floorMod(wy, BigRegionData.SIZE);
			// long packed = PackingHelper.pack(regx, regy);
			// BigRegionData region = this.regions.get(packed);
			BigRegionData region = center;

			return region == null ? -100 : region.height[BigRegionData.getIndex(owx, owy)]; // TODO properly handle center
		}

		public void generateHeight() {
			findWater();
			propagateHeight();
		}

		public static final int SAFETY_RANGE = 2;// TODO replace by range estimate from toughness

		public void findWater() {
			long[] range = new long[] { Long.MAX_VALUE / 2 }; // HACK replace with proper boxing

			this.center = jwge.loadBigRegion(oreg.x, oreg.y);

			ffe.schedule(oreg.x, oreg.y, 0, null);

			ffe.processScheduled((regx, regy, unused, dir) -> {
				long packed = PackingHelper.pack(regx, regy);
				if (this.regions.containsKey(packed)) {
					return true;
				}
				BigRegionData region = jwge.loadBigRegion(regx, regy);
				regions.put(packed, region);
				boolean hasWater = region.scheduleShores();

				long ndist = oreg.gridDistance(regx, regy);
				long nsafedist = ndist + SAFETY_RANGE;

				if (nsafedist < range[0] && hasWater) {
					range[0] = nsafedist;
				}

				if (ndist < range[0]) {
					ffe.scheduleNeighbors(regx, regy, unused, dir, (x, y) -> true);
				}

				return false;
			});
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

	public static class BadNoise2D implements ValueSupplier {
		int scale = 1;

		public BadNoise2D(int nscale) {
			scale = nscale;
		}

		public float getValue(int x, int y) { // interpolated
			int cx1 = Math.floorDiv(x, scale);
			int cy1 = Math.floorDiv(y, scale);
			int cx2 = cx1 + 1;
			int cy2 = cy1 + 1;

			float pre1 = getCornerValue(cx1, cy1);
			float pre2 = (cy1 == y) ? 0 : getCornerValue(cx1, cy2);
			if (cx1 != x) {
				int ratiox = (cx2 - x);
				pre1 = pre1 * ratiox + getCornerValue(cx2, cy1) * (1 - ratiox);
				pre2 = (cy1 == y) ? 0 : pre2 * ratiox + getCornerValue(cx2, cy2) * (1 - ratiox);
			}
			int ratioy = (cy2 - y);
			float value = pre1 * ratioy + pre2 * (1 - ratioy);
			return value;
		}

		public static float getCornerValue(int cx, int cy) {
			double val = (org.joml.Math.sin(cx * 12.9898f + cy * 78.233f) * 43758.5453123);
			return (float) (val - org.joml.Math.floor(val));
		}
	}

	public static class BadSteepNoise2D extends BadNoise2D {
		public BadSteepNoise2D(int nscale) {
			super(nscale);
		}

		public float getValue(int x, int y) { // interpolated
			int cx1 = Math.floorDiv(x, scale);
			int cy1 = Math.floorDiv(y, scale);
			float pre1 = getCornerValue(cx1, cy1) + getCornerValue(cx1 + 1, cy1);
			float pre2 = getCornerValue(cx1, cy1 + 1) + getCornerValue(cx1 + 1, cy1 + 1);
			return (pre1 + pre2) * 0.25f;
		}
	}

	public static enum ChunkStatus {
		EMPTY, SHORES, SOURCES_SPREAD, SOURCES_SPREAD_IN_RANGE, FlOW_SPREAD
	}

	public static class ChunkData {
		public static final int SIZE = 16;
		ChunkStatus status = ChunkStatus.EMPTY;
		float[] flow; // up to final ouput (height=ground lvl, height+flow=river lvl(?))
		float[] finalheight; // up to final output. stable

		BigRegionData bigregion;

		public void save() {
			// TODO handle saving
		}
	}
}
