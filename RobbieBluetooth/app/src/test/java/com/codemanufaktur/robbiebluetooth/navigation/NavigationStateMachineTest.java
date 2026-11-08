package com.codemanufaktur.robbiebluetooth.navigation;

import static com.codemanufaktur.robbiebluetooth.navigation.DoorSignal.DOOR_LEFT;
import static com.codemanufaktur.robbiebluetooth.navigation.DoorSignal.DOOR_NOT_REACHED;
import static com.codemanufaktur.robbiebluetooth.navigation.DoorSignal.DOOR_REACHED;
import static com.codemanufaktur.robbiebluetooth.navigation.DoorSignal.NO_DOOR;
import static com.codemanufaktur.robbiebluetooth.navigation.DriveCommand.FORWARD;
import static com.codemanufaktur.robbiebluetooth.navigation.DriveCommand.LEFT_TURN;
import static com.codemanufaktur.robbiebluetooth.navigation.DriveCommand.RIGHT_TURN;
import static com.codemanufaktur.robbiebluetooth.navigation.DriveCommand.STOP;
import static com.codemanufaktur.robbiebluetooth.navigation.NavigationStateMachine.State;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NavigationStateMachineTest {

    private long now = 0;
    private final NavigationStateMachine sm = new NavigationStateMachine(0);

    /** One control-loop tick (100 ms). */
    private DriveCommand tick(DoorSignal door, ObstacleSignal obstacle) {
        DriveCommand c = sm.step(door, obstacle, now);
        now += 100;
        return c;
    }

    @Test
    public void searchesByTurningLeftUntilDoorSeen() {
        assertEquals(LEFT_TURN, tick(NO_DOOR, ObstacleSignal.NONE));
        assertEquals(State.START, sm.getState());
        assertEquals(FORWARD, tick(DOOR_NOT_REACHED, ObstacleSignal.NONE));
        assertEquals(State.DOOR, sm.getState());
    }

    @Test
    public void steersTowardsDoor() {
        tick(DOOR_NOT_REACHED, ObstacleSignal.NONE);
        assertEquals(LEFT_TURN, tick(DOOR_LEFT, ObstacleSignal.NONE));
        assertEquals(RIGHT_TURN, tick(DoorSignal.DOOR_RIGHT, ObstacleSignal.NONE));
    }

    @Test
    public void endsAfterFourConsecutiveDoorReachedFrames() {
        tick(DOOR_NOT_REACHED, ObstacleSignal.NONE);
        for (int i = 0; i < 3; i++) {
            assertEquals(STOP, tick(DOOR_REACHED, ObstacleSignal.NONE));
            assertEquals(State.DOOR, sm.getState());
        }
        assertEquals(STOP, tick(DOOR_REACHED, ObstacleSignal.NONE));
        assertEquals(State.END, sm.getState());
        assertEquals(STOP, tick(DOOR_NOT_REACHED, ObstacleSignal.NONE));
        assertEquals(400, sm.getRunDurationMs());
    }

    @Test
    public void doorReachedDebounceResetsOnNoisyFrame() {
        tick(DOOR_NOT_REACHED, ObstacleSignal.NONE);
        tick(DOOR_REACHED, ObstacleSignal.NONE);
        tick(DOOR_REACHED, ObstacleSignal.NONE);
        tick(DOOR_REACHED, ObstacleSignal.NONE);
        tick(DOOR_NOT_REACHED, ObstacleSignal.NONE); // flicker
        tick(DOOR_REACHED, ObstacleSignal.NONE);
        assertEquals(State.DOOR, sm.getState());
    }

    @Test
    public void avoidsObstacleOnTheLeftByTurningRightThenPasses() {
        tick(DOOR_NOT_REACHED, ObstacleSignal.NONE);
        assertEquals(STOP, tick(DOOR_NOT_REACHED, ObstacleSignal.LEFT));
        assertEquals(State.OBSTACLE, sm.getState());
        assertEquals(RIGHT_TURN, tick(NO_DOOR, ObstacleSignal.LEFT));
        assertEquals(STOP, tick(NO_DOOR, ObstacleSignal.NONE));
        assertEquals(State.PASS_OBSTACLE, sm.getState());
        assertEquals(FORWARD, tick(NO_DOOR, ObstacleSignal.NONE));
    }

    @Test
    public void afterPassingTurnsBackUntilDoorIsSeenAgain() {
        tick(DOOR_NOT_REACHED, ObstacleSignal.NONE);
        tick(DOOR_NOT_REACHED, ObstacleSignal.LEFT);   // -> OBSTACLE
        tick(NO_DOOR, ObstacleSignal.LEFT);            // turn right
        tick(NO_DOOR, ObstacleSignal.NONE);            // -> PASS_OBSTACLE
        long passStart = now - 100;
        int forwardTicks = 0;
        while (sm.getState() == State.PASS_OBSTACLE && forwardTicks < 100) {
            if (tick(NO_DOOR, ObstacleSignal.NONE) == FORWARD) {
                forwardTicks++;
            }
        }
        assertEquals(State.FIND_DOOR_AGAIN, sm.getState());
        assertEquals(NavigationStateMachine.PASS_OBSTACLE_MS, now - 100 - passStart); // left after exactly 3 s
        assertEquals(29, forwardTicks); // drove forward for 2.9 s worth of 100 ms ticks
        assertEquals(LEFT_TURN, tick(NO_DOOR, ObstacleSignal.NONE)); // undo the right turn
        assertEquals(FORWARD, tick(DOOR_NOT_REACHED, ObstacleSignal.NONE));
        assertEquals(State.DOOR, sm.getState());
    }

    /** Thesis bug: once an avoidance turn was remembered, obstacles were ignored in FIND_DOOR_AGAIN. */
    @Test
    public void findDoorAgain_checksObstaclesBeforeTurning() {
        tick(DOOR_NOT_REACHED, ObstacleSignal.NONE);
        tick(DOOR_NOT_REACHED, ObstacleSignal.RIGHT);  // -> OBSTACLE
        tick(NO_DOOR, ObstacleSignal.RIGHT);           // turn left, remember
        tick(NO_DOOR, ObstacleSignal.NONE);            // -> PASS_OBSTACLE
        now += NavigationStateMachine.PASS_OBSTACLE_MS;
        tick(NO_DOOR, ObstacleSignal.NONE);            // -> FIND_DOOR_AGAIN
        assertEquals(STOP, tick(NO_DOOR, ObstacleSignal.LEFT));
        assertEquals(State.OBSTACLE, sm.getState());
    }

    /** Thesis bug: the remembered turn was never reset, so FORWARD was unreachable afterwards. */
    @Test
    public void avoidanceTurnIsForgottenOnceDoorIsFound() {
        tick(DOOR_NOT_REACHED, ObstacleSignal.NONE);
        tick(DOOR_NOT_REACHED, ObstacleSignal.LEFT);
        tick(NO_DOOR, ObstacleSignal.LEFT);
        tick(NO_DOOR, ObstacleSignal.NONE);
        now += NavigationStateMachine.PASS_OBSTACLE_MS;
        tick(NO_DOOR, ObstacleSignal.NONE);            // -> FIND_DOOR_AGAIN
        tick(DOOR_NOT_REACHED, ObstacleSignal.NONE);   // door again -> DOOR, turn forgotten
        assertEquals(FORWARD, tick(NO_DOOR, ObstacleSignal.NONE)); // lost door: search straight ahead
        assertEquals(State.FIND_DOOR_AGAIN, sm.getState());
    }

    @Test
    public void passObstacle_exactlyThreeSeconds_movesOn() {
        tick(DOOR_NOT_REACHED, ObstacleSignal.NONE);
        tick(DOOR_NOT_REACHED, ObstacleSignal.LEFT);
        tick(NO_DOOR, ObstacleSignal.NONE);            // -> PASS_OBSTACLE at t = 200
        now = 200 + NavigationStateMachine.PASS_OBSTACLE_MS;
        assertEquals(STOP, tick(NO_DOOR, ObstacleSignal.NONE));
        assertEquals(State.FIND_DOOR_AGAIN, sm.getState());
    }
}
