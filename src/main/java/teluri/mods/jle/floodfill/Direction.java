package teluri.mods.jle.floodfill;

public enum Direction {
	NORTH(+1, 0), //
	SOUTH(-1, 0), //
	WEST(0, -1), //
	EAST(0, +1), //
	//NORTH_EAST(+1, +1), //
	//NORTH_WEST(+1, -1), //
	//SOUTH_WEST(-1, -1), //
	//SOUTH_EAST(-1, +1), //
	;

	Direction(int nx, int ny) {
		x = nx;
		y = ny;
	}

	public final int x;
	public final int y;

	public Direction opposite() {
		return switch (this) {
		//case NORTH_EAST -> SOUTH_WEST;
		//case SOUTH_WEST -> NORTH_EAST;
		//case NORTH_WEST -> SOUTH_EAST;
		//case SOUTH_EAST -> NORTH_WEST;
		case NORTH -> SOUTH;
		case SOUTH -> NORTH;
		case WEST -> EAST;
		case EAST -> WEST;
		case null -> null;
		};
	}
}