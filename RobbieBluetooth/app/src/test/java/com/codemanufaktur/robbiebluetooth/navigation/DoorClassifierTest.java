package com.codemanufaktur.robbiebluetooth.navigation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DoorClassifierTest {

    private static final float SCREEN = 1000f;

    @Test
    public void smallCentredBox_notReached() {
        assertEquals(DoorSignal.DOOR_NOT_REACHED, DoorClassifier.classify(500, 300, SCREEN));
    }

    @Test
    public void wideBox_reached() {
        assertEquals(DoorSignal.DOOR_REACHED, DoorClassifier.classify(500, 650, SCREEN));
    }

    /** Thesis version returned nothing for 70-90 % and reused the previous frame's value. */
    @Test
    public void veryWideBox_stillReached() {
        assertEquals(DoorSignal.DOOR_REACHED, DoorClassifier.classify(500, 800, SCREEN));
    }

    @Test
    public void boxCoveringWholeScreen_isFalseDetection() {
        assertEquals(DoorSignal.NO_DOOR, DoorClassifier.classify(500, 950, SCREEN));
    }

    @Test
    public void smallBoxOnTheLeft_doorLeft() {
        assertEquals(DoorSignal.DOOR_LEFT, DoorClassifier.classify(150, 200, SCREEN));
    }

    @Test
    public void smallBoxOnTheRight_doorRight() {
        assertEquals(DoorSignal.DOOR_RIGHT, DoorClassifier.classify(850, 200, SCREEN));
    }

    @Test
    public void invalidInput_noDoor() {
        assertEquals(DoorSignal.NO_DOOR, DoorClassifier.classify(0, 0, SCREEN));
        assertEquals(DoorSignal.NO_DOOR, DoorClassifier.classify(10, 10, 0));
    }
}
