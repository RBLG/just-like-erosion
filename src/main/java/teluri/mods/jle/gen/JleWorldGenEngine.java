package teluri.mods.jle.gen;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;

import teluri.mods.jle.JustLikeErosion;
import teluri.mods.jle.floodfill.PackingHelper;

public class JleWorldGenEngine {
	public static final Logger LOGGER = JustLikeErosion.LOGGER;

	ConcurrentLinkedDeque<GenRequest> genRequests = new ConcurrentLinkedDeque<>();

	public static final int CACHE_SIZE = 32;

	protected final ArrayDeque<GenRequest> cache = new ArrayDeque<>(CACHE_SIZE);

	protected static final float SPEED = 1f;

	protected static final ValueSupplier shoreProv;
	protected static final ValueSupplier strenProv;
	static {
		shoreProv = (x, y) -> {
			float value1 = 0;
			value1 += Noise2D.noiseSmooth(x, y, 1f / 41, 1.6f) * 0.1f;
			value1 += Noise2D.noiseOcto__(x, y, 1f / 131, 2.2f) * 0.2f;
			value1 += Noise2D.noiseOcto__(x, y, 1f / 331, 4.1f) * 0.7f;
			value1 = Noise2D.easeSquare(value1);
			return value1;
		};
		strenProv = (x, y) -> {
			float value1 = 0.1f;
			value1 += Noise2D.noiseOcto__(x, y, 5, 4) * 0.9f;
			// value1 += Noise2D.noiseOcto__(x, y, 1f / 3, 4) * 0.75f;
			// value1 += Noise2D.noiseOcto__(x, y, 1f / 30, 5) * 0.2f;
			// value1 += Noise2D.noiseOcto__(x, y, 1f / 100, 6) * 0.45f;
			//value1 = Noise2D.easeSquare(value1);
			//float value2 = 0.2f;
			// value2 += Noise2D.noiseOcto__(x, y, 1f / 133, 82.1f) * 0.2f;
			// value2 += Noise2D.noiseOcto__(x, y, 1f / 234, 74.6f) * 0.6f;
			// value2 = Noise2D.easeQuintic(value2);
			return value1;// * org.joml.Math.lerp(0.1f, 1, value2) * SPEED;
		};
	}

	public void request(int wox, int woy, Consumer<HeightSupplier> consumer) {
		int oregx = Math.floorDiv(wox, BigRegionData.SIZE);
		int oregy = Math.floorDiv(woy, BigRegionData.SIZE);

		BigRegionData lregion = peekBigRegion(oregx, oregy);
		if (lregion != null && lregion.rangedStable) {
			consumer.accept(lregion::getHeight);
			return;
		}
		HeightSupplier output = actualRequest(wox, woy);

		consumer.accept(output);
	}

	public synchronized HeightSupplier actualRequest(int wox, int woy) {
		GenRequest request = new GenRequest(this, wox, woy);

		request.requestHeight();

		if (CACHE_SIZE <= cache.size()) {
			GenRequest uncachedRequest = cache.pollLast();
			uncachedRequest.unload();
		}
		cache.addFirst(request);
		return request.getOutput();
	}

	public BigRegionData loadBigRegion(int regx, int regy) {
		return usingCache((cache) -> {
			for (GenRequest request : cache) {
				long packed = PackingHelper.pack(regx, regy);
				BigRegionData region = request.regions.remove(packed);
				if (region != null) {
					return region;
				}
			}
			// TODO check disk if it already exist and only then make a new one
			return new BigRegionData(regx, regy, shoreProv, strenProv);
		});
	}

	public BigRegionData peekBigRegion(int regx, int regy) {
		return usingCache((cache) -> {
			for (GenRequest request : cache) {
				long packed = PackingHelper.pack(regx, regy);
				BigRegionData region = request.regions.get(packed);
				if (region != null) {
					return region;
				}
			}
			return null;
		});
	}

	public synchronized BigRegionData usingCache(Function<ArrayDeque<GenRequest>, BigRegionData> consumer) {
		return consumer.apply(cache);
	}

	@FunctionalInterface
	public static interface ValueSupplier {
		float getValue(float x, float y);
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
