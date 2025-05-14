package teluri.mods.jle.floodfill;

import org.slf4j.Logger;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import teluri.mods.jle.JustLikeErosion;
import teluri.mods.jle.gen.BigRegionData;

public class FloodFillEngine {
	public static final Logger LOGGER = JustLikeErosion.LOGGER;

	protected LongArrayFIFOQueue scheduled = new LongArrayFIFOQueue(); // TODO replace by a long array manually managed
	protected LongArrayFIFOQueue scheduledNext = new LongArrayFIFOQueue();

	// protected ArrayDeque<FloodFillCursor> available = new ArrayDeque<>();
	// protected ArrayDeque<FloodFillCursor> scheduled = new ArrayDeque<>();

	public void schedule(int x, int y, float value, Direction next) {
		// FloodFillCursor cursor = available.pollFirst();
		// cursor = cursor != null ? cursor : new FloodFillCursor();
		// scheduled.addLast(cursor.set(x, y, value, next));
		scheduledNext.enqueue(getPacked(x, y, value, next));
	}

	public long getPacked(int x, int y, float value, Direction next) {
		return FloodFillCursor.packCursor(x, y, value, next);
	}

	public boolean processScheduled(BoundsChecker checker, NextValProvider nvp, CursorConsumer cc) {
		boolean stable = true;
		long count = 0;
		while (!scheduledNext.isEmpty()) {
			LongArrayFIFOQueue tmp = scheduled;
			scheduled = scheduledNext;
			scheduledNext = tmp;
			// LOGGER.info(String.format("next batch at %d steps", scheduled.size()));
			while (!scheduled.isEmpty()) {
				long packedCursor = scheduled.dequeueLastLong();
				// FloodFillCursor cursor = scheduled.pollFirst();
				// int x = cursor.x;
				// int y = cursor.y;
				// float value = cursor.value;
				// Direction origin = cursor.origin;
				// stable &= cc.consume(x, y, value, origin);
				stable &= usePacked(packedCursor, cc, checker, nvp);
				count++;
			}
		}
		if (count != 0) {
			float ratio = count / BigRegionData.SIZE_SQUARED;
			LOGGER.info(String.format("floodfill completed as %s in %d steps (r:%.3f)", stable ? "stable  " : "unstable", count, ratio));
		}
		return stable;
	}

	protected boolean usePacked(long packedCursor, CursorConsumer cc, BoundsChecker checker, NextValProvider nvp) {
		int x = FloodFillCursor.unpackX(packedCursor);
		int y = FloodFillCursor.unpackY(packedCursor);
		float value = FloodFillCursor.unpackValue(packedCursor);
		Direction previous = FloodFillCursor.unpackDir(packedCursor);
		boolean stable = true;
		float nextval = nvp.getVal(x, y);
		for (Direction dir : Direction.VALUES) {
			if (dir.opposite() == previous) {
				continue;
			}
			int x2 = x + dir.x;
			int y2 = y + dir.y;
			if (!checker.isInBounds(x2, y2)) {
				continue;
			}
			float fullnextval = value + nextval * dir.dist;
			stable &= cc.consume(x2, y2, fullnextval, dir);
		}
		return stable;
	}

	@FunctionalInterface
	public static interface BoundsChecker {
		boolean isInBounds(int x, int y);
	}

	@FunctionalInterface
	public static interface CursorConsumer {
		boolean consume(int x, int y, float value, Direction origin);
	}

	@FunctionalInterface
	public static interface NextValProvider {
		float getVal(int x, int y);
	}

	public static class FloodFillCursor {
		public int x, y;
		public float value;
		public Direction origin = null;

		public FloodFillCursor set(int nx, int ny, float nvalue, Direction norigin) {
			x = nx;
			y = ny;
			value = nvalue;
			origin = norigin;
			return this;
		}

		public static final long MASK_12 = 0xfffL;
		public static final long MASK_8 = 0xffL;
		public static final long MASK_4 = 0xfL;
		public static final long OFFSET_DIR = 56;
		public static final long OFFSET_X = 44;
		public static final long OFFSET_Y = 32;

		public static long packCursor(int nx, int ny, float nvalue, Direction norigin) { // Direction has 8 values max
			int ordinal = norigin == null ? Direction.VALUES.length : norigin.ordinal();
			long origin = (ordinal & MASK_4) << OFFSET_DIR;
			long x = (nx & MASK_12) << OFFSET_X;
			long y = (ny & MASK_12) << OFFSET_Y;
			long value = Float.floatToIntBits(nvalue) & PackingHelper.MASK_32;

			return origin | x | y | value;
		}

		public static float unpackValue(long packed) {
			long raw = packed & PackingHelper.MASK_32;
			return Float.intBitsToFloat((int) raw);
		}

		public static Direction unpackDir(long packed) {
			long raw = (packed >>> OFFSET_DIR) & MASK_4;
			return Direction.VALUES_FULL[(int) raw];
		}

		public static int unpackX(long packed) {
			long raw = (packed >>> OFFSET_X) & MASK_12;
			return (int) raw;
		}

		public static int unpackY(long packed) {
			long raw = (packed >>> OFFSET_Y) & MASK_12;
			return (int) raw;
		}
	}

	public static class FloodFillEngineValueless extends FloodFillEngine {
		@Override
		public boolean usePacked(long packedCursor, CursorConsumer cc, BoundsChecker checker, NextValProvider nvp) {
			int x = PackingHelper.unpackX(packedCursor);
			int y = PackingHelper.unpackY(packedCursor);
			float value = 0;
			// Direction origin = null;
			boolean stable = true;
			for (Direction dir : Direction.VALUES) {
				int x2 = x + dir.x;
				int y2 = y + dir.y;
				if (!checker.isInBounds(x2, y2)) {
					continue;
				}
				stable &= cc.consume(x2, y2, value, dir);
			}
			return stable;
		}

		@Override
		public long getPacked(int x, int y, float value, Direction next) {
			return PackingHelper.pack(x, y);
		}

		public boolean processScheduled(CursorConsumer cc) {
			return super.processScheduled((x, y) -> true, (x, y) -> 0, cc);
		}
	}

}