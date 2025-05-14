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

	public static final float THRESHHOLD = 0.30f;

	// protected float[] shores; // up to source spread (it get baked in height data)
	// protected float[] toughness; // up to flow spread or even later
	protected float[] height; // up to final output. stable

	protected final int oworx; // world pos origin
	protected final int owory;
	protected final int oregx; // region pos
	protected final int oregy;

	protected final ValueSupplier shore;
	protected final ValueSupplier strength;

	FloodFillEngine ffe = new FloodFillEngine();

	// To be reset each request
	public boolean stable = false;
	public boolean hasWater = false;
	public boolean rangedStable = false; // TODO improve (how?)

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
					height[getIndex(itx, ity)] = 0;// (value - THRESHHOLD) * 10;
					ffe.schedule(itx, ity, 0, Direction.NONE);
					hasWater = true;
				}
			}
		}
		return hasWater;
	}

	public boolean propagateLocalHeight() {
		// LOGGER.info(String.format("propagating for region %d,%d...", oregx, oregy));
		stable = ffe.processScheduled(BigRegionData::isInRelativeBounds, this::getNextVal, this::processStep);
		return stable;
	}

	public boolean processStep(int x, int y, float value, Direction previous) {
		int index = getIndex(x, y);
		float curval = height[index];
		boolean lower = value < curval;
		if (lower) {
			// LOGGER.info(String.format("propagating, %.3f was lower than %.3f", value, curval));
			height[index] = value;
			ffe.schedule(x, y, value, previous);
		}
		return !lower;
	}

	protected float getNextVal(int x, int y) {
		return strength.getValue(oworx + x, owory + y);
	}

	public static boolean isInRelativeBounds(int nx, int ny) {
		return 0 <= nx && 0 <= ny && nx < SIZE && ny < SIZE;
	}

	public void propagateHeightOnSouthBorder(BigRegionData neib) {
		for (int itx = 0; itx < SIZE; itx++) {
			propagateHeightOnBorder(this, neib, itx, itx, LAST_, FIRST, Direction.NORTH);
			propagateHeightOnBorder(neib, this, itx, itx, FIRST, LAST_, Direction.SOUTH);
			if (itx != 0) {
				propagateHeightOnBorder(this, neib, itx, itx - 1, LAST_, FIRST, Direction.NORTH_WEST);
				propagateHeightOnBorder(neib, this, itx, itx - 1, FIRST, LAST_, Direction.SOUTH_WEST);
			} else if (itx != LAST_) {
				propagateHeightOnBorder(this, neib, itx, itx + 1, LAST_, FIRST, Direction.NORTH_EAST);
				propagateHeightOnBorder(neib, this, itx, itx + 1, FIRST, LAST_, Direction.SOUTH_EAST);
			}
		}
	}

	public void propagateHeightOnEastBorder(BigRegionData neib) {
		for (int ity = 0; ity < SIZE; ity++) {
			propagateHeightOnBorder(this, neib, LAST_, FIRST, ity, ity, Direction.EAST);
			propagateHeightOnBorder(neib, this, FIRST, LAST_, ity, ity, Direction.WEST);
			if (ity != 0) {
				propagateHeightOnBorder(this, neib, LAST_, FIRST, ity, ity - 1, Direction.NORTH_EAST);
				propagateHeightOnBorder(neib, this, FIRST, LAST_, ity, ity - 1, Direction.SOUTH_EAST);
			} else if (ity != LAST_) {
				propagateHeightOnBorder(this, neib, LAST_, FIRST, ity, ity + 1, Direction.NORTH_WEST);
				propagateHeightOnBorder(neib, this, FIRST, LAST_, ity, ity + 1, Direction.SOUTH_WEST);
			}
		}
	}

	public static void propagateHeightOnBorder(BigRegionData curr, BigRegionData neib, int thisx, int neibx, int thisy, int neiby, Direction dir) {
		if (!curr.stable) {
			float lval = curr.height[getIndex(thisx, thisy)];
			float nextval = lval + curr.getNextVal(thisx, thisy) * dir.dist;
			neib.processStep(neibx, neiby, nextval, dir);
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