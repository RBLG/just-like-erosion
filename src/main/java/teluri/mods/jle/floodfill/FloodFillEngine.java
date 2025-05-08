package teluri.mods.jle.floodfill;

import java.util.ArrayDeque;

public class FloodFillEngine {
	protected ArrayDeque<FloodFillCursor> available = new ArrayDeque<>();
	protected ArrayDeque<FloodFillCursor> scheduled = new ArrayDeque<>();

	public void schedule(int x, int y, float value, Direction next) {
		FloodFillCursor cursor = available.pop();
		cursor = cursor != null ? cursor : new FloodFillCursor();
		scheduled.push(cursor.set(x, y, value, next));
	}

	public void scheduleNeighbors(int x, int y, float value, Direction previous) {
		for (Direction dir : Direction.values()) {
			if (dir != previous.opposite()) {
				schedule(x + dir.x, y + dir.y, value, dir);
			}
		}
	}

	public void processScheduled(CursorConsumer cc) {
		FloodFillCursor cursor;
		while ((cursor = scheduled.poll()) != null) {
			int x = cursor.x;
			int y = cursor.y;
			float value = cursor.value;
			Direction origin = cursor.origin;

			available.push(cursor);
			cc.consume(x, y, value, origin);
		}
	}

	@FunctionalInterface
	public static interface CursorConsumer {
		void consume(int x, int y, float value, Direction origin);
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
	}
}