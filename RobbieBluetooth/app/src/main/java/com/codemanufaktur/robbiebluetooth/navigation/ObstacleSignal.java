package com.codemanufaktur.robbiebluetooth.navigation;

/** Result of the depth-based obstacle check for the current frame. */
public enum ObstacleSignal {
    NONE,
    /** Obstacle in the central window, the left side is closer. */
    LEFT,
    /** Obstacle in the central window, the right side is closer. */
    RIGHT;

    public boolean isObstacle() {
        return this != NONE;
    }
}
