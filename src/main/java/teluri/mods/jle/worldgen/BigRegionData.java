package teluri.mods.jle.worldgen;

import java.util.Arrays;

import teluri.mods.jle.floodfill.Direction;
import teluri.mods.jle.floodfill.FloodFillEngine;
import teluri.mods.jle.worldgen.JleWorldGenEngine.ChunkRegionProgressData;
import teluri.mods.jle.worldgen.JleWorldGenEngine.ValueSupplier;

public class BigRegionData { // TODO need a list/bitset of water source position for range check (per chunk instead of per block?)
	public static final int SIZE = 16 * ChunkRegionProgressData.SIZE;
	public static final int SIZE_SQUARED = SIZE * SIZE;
	public static final int LAST_ = SIZE - 1;
	public static final int FIRST = 0;

	public static final float THRESHHOLD = 0.5f;

	// protected float[] shores; // up to source spread (it get baked in height data)
	// protected float[] toughness; // up to flow spread or even later
	protected float[] height; // up to final output. stable

	protected final int posx; // TODO decide if as chunk or block pos + handle convertion
	protected final int posy;

	protected final ValueSupplier shore;
	protected final ValueSupplier strength;

	FloodFillEngine ffe = new FloodFillEngine();

	//To be reset each request
	public boolean stable = false;

	public BigRegionData(int nposx, int nposy, ValueSupplier nshoreSupplier, ValueSupplier ntoughnessSupplier) {
		posx = nposx;
		posy = nposy;
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

	public void beginGeneration() {
		this.stable = false;
	}

	public void scheduleShores() {
		if (height != null) {
			return;
		}
		height = newDataArray(Float.MAX_VALUE);// TODO use max world height instead
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

	public boolean propagateLocalHeight() {
		stable = ffe.processScheduled((x, y, value, previous) -> {
			int index = getIndex(x, y);
			float curval = height[index];
			boolean lower = value < curval;
			if (lower) {
				height[index] = value;
				float nextval = value + strength.getValue(posx + x, posy + y);
				ffe.scheduleNeighbors(x, y, nextval, previous);
			}
			return !lower;
		});
		return stable;
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
			float nextval = curr.height[getIndex(thisx, thisy)] + curr.strength.getValue(curr.posx + thisx, curr.posy + thisy);
			neib.ffe.schedule(neibx, neiby, nextval, dir);
			// TODO add 2 more schedule for 8 way flood fill
		}
	}

	// TODO add two diagonal propag for 8 way ff
}