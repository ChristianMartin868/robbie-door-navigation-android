package com.codemanufaktur.robbiebluetooth.navigation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ObstacleDetectorTest {

    private static final int W = 256;
    private static final int H = 256;
    private final ObstacleDetector detector = new ObstacleDetector();

    /** Background far away (0) with a gentle gradient so normalisation has a range. */
    private static float[] background() {
        float[] map = new float[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                map[y * W + x] = y * 0.1f;
            }
        }
        return map;
    }

    private static void fill(float[] map, int x0, int x1, float value) {
        for (int y = 0; y < H; y++) {
            for (int x = x0; x < x1; x++) {
                map[y * W + x] = value;
            }
        }
    }

    @Test
    public void emptyScene_noObstacle() {
        float[] map = background();
        fill(map, 0, W, 0f);
        map[0] = 1f; // keep a non-zero range
        assertEquals(ObstacleSignal.NONE, detector.detect(map, W, H));
    }

    @Test
    public void closeObjectInCentre_leaningRight_reportsRight() {
        float[] map = background();
        fill(map, 100, 200, 100f);  // close object in the central window ...
        fill(map, 192, W, 80f);     // ... extending into the right window
        assertEquals(ObstacleSignal.RIGHT, detector.detect(map, W, H));
    }

    @Test
    public void closeObjectInCentre_leaningLeft_reportsLeft() {
        float[] map = background();
        fill(map, 60, 160, 100f);
        fill(map, 0, 64, 80f);
        assertEquals(ObstacleSignal.LEFT, detector.detect(map, W, H));
    }

    /**
     * Regression test for the thesis bug ("sumL =+ v"): only the last pixel of each side window
     * was used. Here the last pixel of the left window is bright, but the right window is on
     * average much closer, so the obstacle must be reported on the right.
     */
    @Test
    public void sideDecisionUsesMeanNotLastPixel() {
        float[] map = background();
        fill(map, 64, 192, 100f);       // obstacle in the centre
        fill(map, 192, W, 90f);         // right side close on average
        int lastLeftX = W / 4 - 1;
        int lastY = H - ObstacleDetector.BOTTOM_MARGIN - 1;
        map[lastY * W + lastLeftX] = 100f; // single bright pixel at the end of the left window
        assertEquals(ObstacleSignal.RIGHT, detector.detect(map, W, H));
    }

    @Test
    public void smallCloseSpot_belowRatio_noObstacle() {
        float[] map = background();
        for (int y = 100; y < 105; y++) {
            for (int x = 120; x < 125; x++) {
                map[y * W + x] = 100f;
            }
        }
        assertEquals(ObstacleSignal.NONE, detector.detect(map, W, H));
    }

    @Test
    public void rotate180_reversesOrder() {
        float[] out = ObstacleDetector.rotate180(new float[] {1, 2, 3, 4});
        assertEquals(4f, out[0], 0f);
        assertEquals(1f, out[3], 0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tooSmallMap_throws() {
        detector.detect(new float[10], 2, 5);
    }
}
