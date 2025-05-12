package teluri.mods.jle.gen;

import java.util.Arrays;

import org.slf4j.Logger;

import teluri.mods.jle.JustLikeErosion;
import teluri.mods.jle.floodfill.Direction;
import teluri.mods.jle.floodfill.FloodFillEngine;
import teluri.mods.jle.gen.JleWorldGenEngine.ChunkData;
import teluri.mods.jle.gen.JleWorldGenEngine.ValueSupplier;

public class BigRegionData {
	public static final Logger LOGGER = JustLikeErosion.LOGGER;

	// TODO need a list/bitset of water source position for range check (per chunk instead of per block?)
	public static final int SIZE_IN_CHUNKS = 16;
	public static final int SIZE = 16 * ChunkData.SIZE; // TODO switch to powers of two
	public static final int SIZE_SQUARED = SIZE * SIZE;
	public static final int LAST_ = SIZE - 1;
	public static final int FIRST = 0;

	public static final float THRESHHOLD = 0.1f;

	protected static final float SPEED = 0.7f;

	// protected float[] shores; // up to source spread (it get baked in height data)
	// protected float[] toughness; // up to flow spread or even later
	protected float[] height; // up to final output. stable

	protected final int oworx; // TODO decide if as chunk or block pos + handle convertion
	protected final int owory; // world pos, not region pos
	protected final int oregx;
	protected final int oregy;

	protected final ValueSupplier shore;
	protected final ValueSupplier strength;

	FloodFillEngine ffe = new FloodFillEngine();

	// To be reset each request
	public boolean stable = false;
	public boolean hasWater = false;
	public boolean rangedStable = false; // TODO improve

	public BigRegionData(int regx, int regy, ValueSupplier nshoreSupplier, ValueSupplier ntoughnessSupplier) {
		oworx = regx * SIZE;
		owory = regy * SIZE;
		oregx = regx;
		oregy = regy;

		shore = nshoreSupplier;
		strength = ntoughnessSupplier;
	}

	public static float[] newDataArray(float defval) {
		float[] arr = new float[SIZE_SQUARED];
		Arrays.fill(arr, defval);
		return arr;
	}

	public static int getIndex(int x, int y) {
		return x + y * SIZE;
	}

	public boolean scheduleShores() {
		this.stable = false; // make bounds being rechecked
		if (height != null) {
			return hasWater;
		}
		height = newDataArray(9999999999999999f);// TODO use max world height instead
		for (int itx = 0; itx < SIZE; itx++) {
			for (int ity = 0; ity < SIZE; ity++) {
				float value = shore.getValue(oworx + itx, owory + ity); // TODO store noise corners
				if (value < THRESHHOLD) {
					// LOGGER.info("found water");
					height[getIndex(itx, ity)] = 0;
					ffe.schedule(ity, itx, value, Direction.NONE);
					hasWater = true;
				}
			}
		}
		return hasWater;
	}

	public boolean propagateLocalHeight() {
		// LOGGER.info(String.format("propagating for region %d,%d...", oregx, oregy));
		stable = ffe.processScheduled(BigRegionData::isInRelativeBounds, (x, y, value, previous) -> {
			int index = getIndex(x, y);
			float curval = height[index];
			boolean lower = value < curval;
			if (lower) {
				// LOGGER.info(String.format("propagating, %.3f was lower than %.3f", value, curval));
				height[index] = value;
				float nextval = getNextVal(value, x, y);
				ffe.schedule(x, y, nextval, previous);
			}
			return !lower;
		});
		return stable;
	}

	public boolean processStep(int x, int y, float value, Direction previous) {
		int index = getIndex(x, y);
		float curval = height[index];
		boolean lower = value < curval;
		if (lower) {
			// LOGGER.info(String.format("propagating, %.3f was lower than %.3f", value, curval));
			height[index] = value;
			float nextval = getNextVal(value, x, y);
			ffe.schedule(x, y, nextval, previous);
		}
		return !lower;
	}

	protected float getNextVal(float value, int x, int y) {
		return value + Math.clamp(strength.getValue(oworx + x, owory + y) + 0.1f, 0, 1) * SPEED;
	}

	public static boolean isInRelativeBounds(int nx, int ny) {
		return 0 <= nx && 0 <= ny && nx < SIZE && ny < SIZE;
	}

	public void propagateHeightOnSouthBorder(BigRegionData neib) {
		for (int itx = 0; itx < SIZE; itx++) {
			propagateHeightOnBorder(this, neib, itx, itx, LAST_, FIRST);
			propagateHeightOnBorder(neib, this, itx, itx, FIRST, LAST_);
		}
	}

	public void propagateHeightOnEastBorder(BigRegionData neib) {
		for (int ity = 0; ity < SIZE; ity++) {
			propagateHeightOnBorder(this, neib, LAST_, FIRST, ity, ity);
			propagateHeightOnBorder(neib, this, FIRST, LAST_, ity, ity);
		}
	}

	public static void propagateHeightOnBorder(BigRegionData curr, BigRegionData neib, int thisx, int neibx, int thisy, int neiby) {
		if (!curr.stable) {
			float lval = curr.height[getIndex(thisx, thisy)];
			float nextval = curr.getNextVal(lval, thisx, thisy);
			// neib.ffe.schedule(neibx, neiby, nextval, null);
			neib.processStep(neibx, neiby, nextval, Direction.NONE);
			// neib.ffe.schedule(neibx, neiby, nextval, null);
			// TODO add 2 more schedule for 8 way flood fill
		}
	}

	// TODO add two diagonal propag for 8 way ff

	public void save() {
		// TODO handle saving to disk
	}

	public float getHeight(int wx, int wy) { // TODO replace y by z on jle side
		int owx = wx - oregx * SIZE;
		int owy = wy - oregy * SIZE;

		if (!isInRelativeBounds(owx, owy)) {
			return -20;
		}

		return height[BigRegionData.getIndex(owx, owy)]; // TODO properly handle center (?)
	}
}