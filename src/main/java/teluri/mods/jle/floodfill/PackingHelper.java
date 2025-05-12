package teluri.mods.jle.floodfill;

public class PackingHelper {

	public static final long MASK_32 = 0xffffffffL;
	public static final long INT_HALF = Integer.MAX_VALUE;

	public static long add(long packed, int x, int y) { // TODO if not using two's complement, would allow not having to unpack to add?
		int px = unpackX(packed);
		int py = unpackY(packed);
		px += x;
		py += y;
		return pack(px, py);
	}

	public static long pack(int x, int y) {
		return x & MASK_32 | ((y & MASK_32) << 32);
	}

	public static int unpackX(long packed) {
		return (int) packed;
	}

	public static int unpackY(long packed) {
		return (int) (packed >> 32);
	}
}
