package teluri.mods.jle.gen;

import java.util.Arrays;

import teluri.mods.jle.floodfill.Direction;
import teluri.mods.jle.floodfill.FloodFillEngine;
import teluri.mods.jle.gen.JleWorldGenEngine.ChunkData;
import teluri.mods.jle.gen.JleWorldGenEngine.ValueSupplier;

public class BigRegionData { // TODO need a list/bitset of water source position for range check (per chunk instead of per block?)
	public static final int SIZE = 16 * ChunkData.SIZE; // TODO switch to powers of two
	public static final int SIZE_SQUARED = SIZE * SIZE;
	public static final int LAST_ = SIZE - 1;
	public static final int FIRST = 0;

	public static final float THRESHHOLD = 0.5f;

	// protected float[] shores; // up to source spread (it get baked in height data)
	// protected float[] toughness; // up to flow spread or even later
	protected float[] height; // up to final output. stable

	protected final int originx; // TODO decide if as chunk or block pos + handle convertion
	protected final int originy; // world pos, not region pos

	protected final ValueSupplier shore;
	protected final ValueSupplier strength;

	FloodFillEngine ffe = new FloodFillEngine();

	// To be reset each request
	public boolean stable = false;
	public boolean hasWater = false;

	public BigRegionData(int regx, int regy, ValueSupplier nshoreSupplier, ValueSupplier ntoughnessSupplier) {
		originx = regx * SIZE;
		originy = regy * SIZE;
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
		if (height != null) {
			this.stable = false; // make bounds being rechecked
			return hasWater;
		}
		height = newDataArray(Float.MAX_VALUE * 0.5f);// TODO use max world height instead
		for (int itx = 0; itx < SIZE; itx++) {
			for (int ity = 0; ity < SIZE; ity++) {
				float value = shore.getValue(originx + itx, originy + ity);
				if (value < THRESHHOLD) {
					height[getIndex(itx, ity)] = value;
					ffe.schedule(itx, ity, 0, null);
					hasWater = true;
				}
			}
		}
		return hasWater;
	}

	public boolean propagateLocalHeight() {
		stable = ffe.processScheduled((x, y, value, previous) -> {
			int index = getIndex(x, y);
			float curval = height[index];
			boolean lower = value < curval;
			if (lower) {
				height[index] = value;
				float nextval = value + strength.getValue(originx + x, originy + y);
				ffe.scheduleNeighbors(x, y, nextval, previous, BigRegionData::isInRelativeBounds);
			}
			return !lower;
		});
		return stable;
	}

	public static boolean isInRelativeBounds(int nx, int ny) {
		return 0 <= nx && 0 <= ny && nx < SIZE && ny < SIZE;
	}

	public void propagateHeightOnSouthBorder(BigRegionData neib) {
		for (int itx = 0; itx < SIZE; itx++) {
			propagateHeightOnBorder(this, neib, itx, itx, LAST_, FIRST, Direction.SOUTH);
			propagateHeightOnBorder(neib, this, itx, itx, FIRST, LAST_, Direction.NORTH);
		}
	}

	public void propagateHeightOnEastBorder(BigRegionData neib) {
		for (int ity = 0; ity < SIZE; ity++) {
			propagateHeightOnBorder(this, neib, LAST_, FIRST, ity, ity, Direction.EAST);
			propagateHeightOnBorder(neib, this, FIRST, LAST_, ity, ity, Direction.WEST);
		}
	}

	public static void propagateHeightOnBorder(BigRegionData curr, BigRegionData neib, int thisx, int neibx, int thisy, int neiby, Direction dir) {
		if (!curr.stable) {
			float nextval = curr.height[getIndex(thisx, thisy)] + curr.strength.getValue(curr.originx + thisx, curr.originy + thisy);
			neib.ffe.schedule(neibx, neiby, nextval, dir);
			// TODO neib.ffe.scheduleNeighbors(neibx, neiby, nextval, dir, BigRegionData::isInRelativeBounds);
			// TODO add 2 more schedule for 8 way flood fill
		}
	}

	// TODO add two diagonal propag for 8 way ff

	public void save() {
		// TODO handle saving to disk
	}
}