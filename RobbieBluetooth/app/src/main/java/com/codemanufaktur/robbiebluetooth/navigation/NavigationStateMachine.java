package com.codemanufaktur.robbiebluetooth.navigation;

/**
 * Combines target navigation (door detection) and obstacle avoidance (depth estimation).
 * Called every 100 ms with the latest perception signals; returns the drive command.
 *
 * <pre>
 * START           -- door seen ----------------> DOOR
 * DOOR            -- door reached (4 frames) ---> END
 * DOOR            -- obstacle ------------------> OBSTACLE
 * DOOR            -- door lost -----------------> FIND_DOOR_AGAIN
 * OBSTACLE        -- path clear ----------------> PASS_OBSTACLE   (turns away while blocked)
 * PASS_OBSTACLE   -- obstacle ------------------> OBSTACLE
 * PASS_OBSTACLE   -- 3 s forward ---------------> FIND_DOOR_AGAIN
 * FIND_DOOR_AGAIN -- door seen ----------------> DOOR            (turns back while searching)
 * FIND_DOOR_AGAIN -- obstacle ------------------> OBSTACLE
 * </pre>
 *
 * <p>Plain Java, deterministic (time is passed in), no Android dependencies.
 * Fixes compared to the thesis version (tag {@code thesis-2021}):
 * <ul>
 *   <li>enums instead of {@code String ==} comparisons,</li>
 *   <li>the remembered avoidance turn is reset once the door is found again (before, the
 *       "drive forward" and "obstacle" branches of FIND_DOOR_AGAIN became unreachable after the
 *       first avoidance manoeuvre),</li>
 *   <li>obstacles are checked first in FIND_DOOR_AGAIN, so the robot never turns into one,</li>
 *   <li>the door-reached debounce counter is reset when the door is not reached in a frame,
 *       and the robot holds still while confirming,</li>
 *   <li>PASS_OBSTACLE handles elapsed == 3000 ms (before: neither branch matched).</li>
 * </ul>
 */
public final class NavigationStateMachine {

    public enum State { START, DOOR, OBSTACLE, PASS_OBSTACLE, FIND_DOOR_AGAIN, END }

    static final long PASS_OBSTACLE_MS = 3000;
    static final int DOOR_REACHED_CONFIRMATIONS = 4;

    private State state = State.START;
    private long stateEnteredMs;
    private final long startedMs;
    private long endedMs = -1;
    private DriveCommand avoidanceTurn; // null = no avoidance manoeuvre since the door was last seen
    private int doorReachedCount;

    public NavigationStateMachine(long nowMs) {
        this.startedMs = nowMs;
        this.stateEnteredMs = nowMs;
    }

    public State getState() {
        return state;
    }

    /** Duration from start until END was reached, or -1 while still running. */
    public long getRunDurationMs() {
        return endedMs < 0 ? -1 : endedMs - startedMs;
    }

    public DriveCommand step(DoorSignal door, ObstacleSignal obstacle, long nowMs) {
        switch (state) {
            case START:
                if (door == DoorSignal.NO_DOOR) {
                    return DriveCommand.LEFT_TURN; // rotate to search for a door
                }
                enter(State.DOOR, nowMs);
                return stepDoor(door, obstacle, nowMs);
            case DOOR:
                return stepDoor(door, obstacle, nowMs);
            case OBSTACLE:
                return stepObstacle(obstacle, nowMs);
            case PASS_OBSTACLE:
                return stepPassObstacle(obstacle, nowMs);
            case FIND_DOOR_AGAIN:
                return stepFindDoorAgain(door, obstacle, nowMs);
            case END:
            default:
                return DriveCommand.STOP;
        }
    }

    private DriveCommand stepDoor(DoorSignal door, ObstacleSignal obstacle, long nowMs) {
        if (door == DoorSignal.DOOR_REACHED) {
            doorReachedCount++;
            if (doorReachedCount >= DOOR_REACHED_CONFIRMATIONS) {
                enter(State.END, nowMs);
                endedMs = nowMs;
            }
            return DriveCommand.STOP;
        }
        doorReachedCount = 0;

        if (obstacle.isObstacle()) {
            enter(State.OBSTACLE, nowMs);
            return DriveCommand.STOP;
        }
        switch (door) {
            case DOOR_NOT_REACHED:
                return DriveCommand.FORWARD;
            case DOOR_LEFT:
                return DriveCommand.LEFT_TURN;
            case DOOR_RIGHT:
                return DriveCommand.RIGHT_TURN;
            case NO_DOOR:
            default:
                enter(State.FIND_DOOR_AGAIN, nowMs);
                return stepFindDoorAgain(door, obstacle, nowMs);
        }
    }

    private DriveCommand stepObstacle(ObstacleSignal obstacle, long nowMs) {
        switch (obstacle) {
            case LEFT:
                avoidanceTurn = DriveCommand.RIGHT_TURN;
                return DriveCommand.RIGHT_TURN;
            case RIGHT:
                avoidanceTurn = DriveCommand.LEFT_TURN;
                return DriveCommand.LEFT_TURN;
            case NONE:
            default:
                enter(State.PASS_OBSTACLE, nowMs);
                return DriveCommand.STOP;
        }
    }

    private DriveCommand stepPassObstacle(ObstacleSignal obstacle, long nowMs) {
        if (nowMs - stateEnteredMs >= PASS_OBSTACLE_MS) {
            enter(State.FIND_DOOR_AGAIN, nowMs);
            return DriveCommand.STOP;
        }
        if (obstacle.isObstacle()) {
            enter(State.OBSTACLE, nowMs);
            return DriveCommand.STOP;
        }
        return DriveCommand.FORWARD; // drive past the obstacle
    }

    private DriveCommand stepFindDoorAgain(DoorSignal door, ObstacleSignal obstacle, long nowMs) {
        if (door != DoorSignal.NO_DOOR) {
            avoidanceTurn = null;
            enter(State.DOOR, nowMs);
            return stepDoor(door, obstacle, nowMs);
        }
        if (obstacle.isObstacle()) {
            enter(State.OBSTACLE, nowMs);
            return DriveCommand.STOP;
        }
        if (avoidanceTurn == DriveCommand.RIGHT_TURN) {
            return DriveCommand.LEFT_TURN; // turn back towards the original heading
        }
        if (avoidanceTurn == DriveCommand.LEFT_TURN) {
            return DriveCommand.RIGHT_TURN;
        }
        return DriveCommand.FORWARD;
    }

    private void enter(State next, long nowMs) {
        state = next;
        stateEnteredMs = nowMs;
    }
}
