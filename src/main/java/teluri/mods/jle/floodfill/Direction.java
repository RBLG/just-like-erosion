package teluri.mods.jle.floodfill;

import java.util.Arrays;

public enum Direction {
	NORTH(+1, 0), //
	SOUTH(-1, 0), //
	WEST(0, -1), //
	EAST(0, +1), //
	// NORTH_EAST(+1, +1), //
	// NORTH_WEST(+1, -1), //
	// SOUTH_WEST(-1, -1), //
	// SOUTH_EAST(-1, +1), //
	NONE(0, 0);

	Direction(int nx, int ny) {
		x = nx;
		y = ny;
	}

	public final int x;
	public final int y;

	public Direction opposite() {
		return opposite(this);
	}

	public static Direction opposite(Direction dir) {
		return switch (dir) {
		// case NORTH_EAST -> SOUTH_WEST;
		// case SOUTH_WEST -> NORTH_EAST;
		// case NORTH_WEST -> SOUTH_EAST;
		// case SOUTH_EAST -> NORTH_WEST;
		case NORTH -> SOUTH;
		case SOUTH -> NORTH;
		case WEST -> EAST;
		case EAST -> WEST;
		case NONE -> NONE;
		case null -> NONE;
		};
	}

	public static final Direction[] VALUES_FULL = values();
	public static final Direction[] VALUES = Arrays.stream(values()).filter((dir) -> dir != NONE).toArray((size) -> new Direction[size]);
}