package teluri.mods.jle.gen;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

import org.slf4j.Logger;

import teluri.mods.jle.JustLikeErosion;
import teluri.mods.jle.floodfill.PackingHelper;

public class JleWorldGenEngine {
	public static final Logger LOGGER = JustLikeErosion.LOGGER;

	ConcurrentLinkedDeque<GenRequest> genRequests = new ConcurrentLinkedDeque<>();

	public static final int CACHE_SIZE = 32;

	ArrayDeque<GenRequest> cache = new ArrayDeque<>(CACHE_SIZE);

	protected final BadNoise2D shoreProv = new BadNoise2D(40);
	protected final BadNoise2D strenProv = new BadSteepNoise2D(2);

	public synchronized void request(int wox, int woy, Consumer<HeightSupplier> consumer) {
		int oregx = Math.floorDiv(wox, BigRegionData.SIZE);
		int oregy = Math.floorDiv(woy, BigRegionData.SIZE);

		BigRegionData lregion = peekBigRegion(oregx, oregy);
		if (lregion != null && lregion.rangedStable) {
			consumer.accept(lregion::getHeight);
			return;
		}
		GenRequest request = new GenRequest(this, wox, woy);

		request.requestHeight();

		if (CACHE_SIZE <= cache.size()) {
			GenRequest uncachedRequest = cache.pollLast();
			uncachedRequest.unload();
		}
		cache.addFirst(request);

		consumer.accept(request.getOutput());
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
		return new BigRegionData(regx, regy, shoreProv, strenProv);
	}

	public BigRegionData peekBigRegion(int regx, int regy) {
		for (GenRequest request : cache) {
			long packed = PackingHelper.pack(regx, regy);
			BigRegionData region = request.regions.get(packed);
			if (region != null) {
				return region;
			}
		}
		return null;
	}

	@FunctionalInterface
	public static interface ValueSupplier {
		float getValue(float x, float y);
	}

	public static class BadNoise2D implements ValueSupplier {
		int scale = 1;

		public BadNoise2D(int nscale) {
			scale = nscale;
		}

		public float getValue(float x, float y) { // interpolated
			x /= scale;
			y /= scale;
			float cx1 = org.joml.Math.floor(x);
			float cy1 = org.joml.Math.floor(y);
			float cx2 = cx1 + 1;
			float cy2 = cy1 + 1;
			float ratiox = (cx2 - x);
			float ratioy = (cy2 - y);

			float pre1 = getCornerValue(cx1, cy1);
			float pre2 = (cy1 == y) ? 0 : getCornerValue(cx1, cy2);
			if (cx1 != x) {
				pre1 = pre1 * ratiox + getCornerValue(cx2, cy1) * (1 - ratiox);
				pre2 = (cy1 == y) ? 0 : pre2 * ratiox + getCornerValue(cx2, cy2) * (1 - ratiox);
			}
			float value = pre1 * ratioy + pre2 * (1 - ratioy);
			return value;
		}

		public static float getCornerValue(float cx, float cy) {
			double val = (org.joml.Math.sin(cx * 12.9898f + cy * 78.233f) * 43758.5453123);
			return (float) Math.abs((val - org.joml.Math.floor(val)));
		}
	}

	public static class BadSteepNoise2D extends BadNoise2D {
		public BadSteepNoise2D(int nscale) {
			super(nscale);
		}

		public float getValue(float x, float y) { // interpolated
			// int cx1 = Math.floorDiv(x, scale);
			// int cy1 = Math.floorDiv(y, scale);
			// float pre1 = getCornerValue(cx1, cy1) + getCornerValue(cx1 + 1, cy1);
			// float pre2 = getCornerValue(cx1, cy1 + 1) + getCornerValue(cx1 + 1, cy1 + 1);
			// return (pre1 + pre2) * 0.25f;
			return getCornerValue(x, y);
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

	@FunctionalInterface
	public static interface HeightSupplier {
		float get(int wx, int wy);
	}
}
