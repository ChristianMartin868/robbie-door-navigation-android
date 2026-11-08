package com.codemanufaktur.robbiebluetooth.navigation;

/**
 * Heuristic obstacle check on a MiDaS disparity map (relative inverse depth: larger = closer).
 *
 * <ol>
 *   <li>Min-max normalise the map to [0, 255].</li>
 *   <li>Count pixels in the central window (middle half of the width) above {@link #threshold}.</li>
 *   <li>If more than {@link #ratio} of the central pixels are "close", there is an obstacle.</li>
 *   <li>The side whose window has the higher mean disparity is the side the obstacle is on.</li>
 * </ol>
 *
 * <p>T = 160 and r = 0.1 were tuned empirically in the thesis. Works on the float map directly;
 * the thesis version read the values back from an RGB_565 bitmap (5-bit red channel) and summed
 * with {@code =+} instead of {@code +=}.
 */
public final class ObstacleDetector {

    public static final int DEFAULT_THRESHOLD = 160;
    public static final float DEFAULT_RATIO = 0.1f;

    /** Margins in pixels, same as in the thesis (ignore image border and floor right in front). */
    static final int TOP_MARGIN = 10;
    static final int BOTTOM_MARGIN = 50;
    static final int LEFT_MARGIN = 19;
    static final int RIGHT_MARGIN = 20;

    private final float threshold;
    private final float ratio;

    public ObstacleDetector() {
        this(DEFAULT_THRESHOLD, DEFAULT_RATIO);
    }

    public ObstacleDetector(float threshold, float ratio) {
        this.threshold = threshold;
        this.ratio = ratio;
    }

    /**
     * @param disparity row-major map in display orientation, {@code disparity[y * width + x]}
     */
    public ObstacleSignal detect(float[] disparity, int width, int height) {
        if (disparity == null || disparity.length < width * height
                || width <= LEFT_MARGIN + RIGHT_MARGIN || height <= TOP_MARGIN + BOTTOM_MARGIN) {
            throw new IllegalArgumentException("disparity map too small");
        }
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < width * height; i++) {
            min = Math.min(min, disparity[i]);
            max = Math.max(max, disparity[i]);
        }
        if (max - min <= 0f) {
            return ObstacleSignal.NONE; // flat map, nothing stands out
        }
        float scale = 255f / (max - min);

        int quarter = width / 4;
        int closeCount = 0;
        int centralCount = 0;
        double sumLeft = 0;
        double sumRight = 0;
        int countLeft = 0;
        int countRight = 0;

        for (int y = TOP_MARGIN; y < height - BOTTOM_MARGIN; y++) {
            int row = y * width;
            for (int x = LEFT_MARGIN; x < width - RIGHT_MARGIN; x++) {
                float v = (disparity[row + x] - min) * scale;
                if (x < quarter) {
                    sumLeft += v;
                    countLeft++;
                } else if (x < 3 * quarter) {
                    if (v > threshold) {
                        closeCount++;
                    }
                    centralCount++;
                } else {
                    sumRight += v;
                    countRight++;
                }
            }
        }

        if (closeCount < ratio * centralCount) {
            return ObstacleSignal.NONE;
        }
        double meanLeft = countLeft == 0 ? 0 : sumLeft / countLeft;
        double meanRight = countRight == 0 ? 0 : sumRight / countRight;
        return meanLeft < meanRight ? ObstacleSignal.RIGHT : ObstacleSignal.LEFT;
    }

    /** The MiDaS output is upside down relative to the preview; rotate by 180 degrees. */
    public static float[] rotate180(float[] map) {
        float[] out = new float[map.length];
        for (int i = 0; i < map.length; i++) {
            out[i] = map[map.length - 1 - i];
        }
        return out;
    }
}
