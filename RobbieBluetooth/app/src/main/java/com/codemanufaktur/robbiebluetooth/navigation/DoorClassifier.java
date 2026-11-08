package com.codemanufaktur.robbiebluetooth.navigation;

/**
 * Turns the geometry of the detected door (bounding box in screen coordinates) into a
 * {@link DoorSignal}. Pure function, no Android dependencies, so it can be unit-tested.
 *
 * <p>Thresholds are relative to the screen width (portrait mode, phone looking forward):
 * <ul>
 *   <li>box wider than 90 % of the screen: implausible, treated as a false detection</li>
 *   <li>box narrower than 60 %: door still ahead</li>
 *   <li>box between 60 % and 90 %: door reached</li>
 *   <li>small box (&lt; 50 %) centred in the outer quarter: door is to the left / right</li>
 * </ul>
 */
public final class DoorClassifier {

    static final float FALSE_DETECTION_WIDTH = 0.9f;
    static final float REACHED_WIDTH = 0.6f;
    static final float SIDE_MAX_WIDTH = 0.5f;
    static final float LEFT_EDGE = 0.25f;
    static final float RIGHT_EDGE = 0.75f;

    private DoorClassifier() {}

    public static DoorSignal classify(float boxCenterX, float boxWidth, float screenWidth) {
        if (screenWidth <= 0 || boxWidth <= 0) {
            return DoorSignal.NO_DOOR;
        }
        float relWidth = boxWidth / screenWidth;
        float relCenter = boxCenterX / screenWidth;

        if (relWidth > FALSE_DETECTION_WIDTH) {
            return DoorSignal.NO_DOOR;
        }
        if (relWidth < SIDE_MAX_WIDTH && relCenter < LEFT_EDGE) {
            return DoorSignal.DOOR_LEFT;
        }
        if (relWidth < SIDE_MAX_WIDTH && relCenter > RIGHT_EDGE) {
            return DoorSignal.DOOR_RIGHT;
        }
        // Thesis version: widths between 70 % and 90 % produced no signal at all, so the
        // previous frame's value was silently reused. Closer than "reached" is still reached.
        return relWidth < REACHED_WIDTH ? DoorSignal.DOOR_NOT_REACHED : DoorSignal.DOOR_REACHED;
    }
}
