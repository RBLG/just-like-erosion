package teluri.mods.jle.gen;

import static org.joml.Math.*;

public class Noise2D {
	private Noise2D(float nscale) {
	}

	public static float noiseSmooth(float x, float y, float scale, float seed) {
		x *= scale;
		y *= scale;
		return noiseSmoothSub(x, y, seed);
	}

	public static float noiseOcto__(float x, float y, float scale, float seed) {
		seed *= 1.10963127f;
		x *= scale;
		y *= scale;
		return (noiseSmoothSub(x, y, seed) + noiseSmoothSub(x, y, seed + 111.1f)) * 0.5f;
	}

	private static float noiseSmoothSub(float x, float y, float seed) {
		float cx = floor(x);
		float cy = floor(y);
		float pre1 = noise(cx, cy, seed);
		float pre2 = (cy == y) ? 0 : noise(cx, cy + 1, seed);
		if (cx != x) {
			float ratiox = easeSquare(x - cx);
			pre1 = lerp(pre1, noise(cx + 1, cy, seed), ratiox);
			pre2 = (cy == y) ? 0 : lerp(pre2, noise(cx + 1, cy + 1, seed), ratiox);
		}
		return lerp(pre1, pre2, easeSquare(y - cy));
	}

	public static float noiseNearest(float x, float y, float scale, float seed) {
		x = floor(x / scale);
		y = floor(y / scale);
		return noise(x, y, seed);
	}

	public static float noise(float x, float y, float seed) {
		float val = sin(x * 12.9898f + y * 78.233f + seed) * 43758.5453123f;
		return val - floor(val);
	}

	public static float easeSquare(float ratio) {// TODO move to ease class?
		return ratio < 0.5f ? 2 * ratio * ratio : (-2 * ratio + 4) * ratio - 1;
	}

	public static float easeCubic(float ratio) {
		return (-2 * ratio + 3) * ratio * ratio; // -2x^3 +3x^2 -> 3x^2 +2x
	}

	public static float easeQuintic(float ratio) {
		return ((6 * ratio - 15) * ratio + 10) * ratio * ratio * ratio;
	}
}