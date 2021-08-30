package com.codemanufaktur.robbiebluetooth;

import android.os.CountDownTimer;
import android.os.SystemClock;

public class Automat {
    State state;
    String command;

    String target;
    String obstacleTarget;
    long stateStartTime;

    public Automat() {
        state = State.START;
        target = "No door";
        obstacleTarget = "No obstacle";
        command = "Left turn";
        setState(state);
    }

    public void input(String _target, String _obstacleTarget) {
        target = _target;
        obstacleTarget = _obstacleTarget;

        switch (state) {
            case START:
                initialize();
                break;
            case DOOR:
                toDoor();
                break;
            case OBSTACLE:
                obstacleAvoidance();
                break;
            case PASSOBSTACLE:
                break;
            case END:
                end();
                break;
        }
    }

    public void setState(State newState) {
        stateStartTime = System.currentTimeMillis();

        switch (newState) {
            case START:
                initialize();
                break;
            case DOOR:
                toDoor();
                break;
            case OBSTACLE:
                obstacleAvoidance();
                break;
            case PASSOBSTACLE:
                break;
            case END:
                end();
                break;
            default:
                System.out.println("error");
                command = "Error";
                break;
        }
        state = newState;
    }

    public long getStateTimeMs() {
        return System.currentTimeMillis() - stateStartTime;
    }

    public State getState() {
        return state;
    }

    public String getCommand() {
        return command;
    }

    public void initialize() {
        if (target == "No door") {
            command = "Left turn";
        }
        else {
            setState(State.DOOR);
        }
    }

    public void toDoor() {
        if ((target == "Door not reached") && (obstacleTarget == "No obstacle")) {
            command = "Forward";
        }
        else if ((target == "Door is left") && (obstacleTarget == "No obstacle")) {
            command = "Left turn";
        }
        else if ((target == "Door is right") && (obstacleTarget == "No obstacle")) {
            command = "Right turn";
        }
        else if (target == "Door reached") {
            command = "Stop";
            setState(State.END);
        }
        else if ((obstacleTarget == "Obstacle left") || (obstacleTarget == "Obstacle right")) {
            setState(State.OBSTACLE);
        }
    }

    public void obstacleAvoidance() {
        if (target == "Obstacle left") {
            command = "Right turn";
            //state = "Right turn to avoid obstacle";
        }
        else if (target == "Obstacle right") {
            command = "Left turn";
            //state = "Left turn to avoid obstacle";
        }
        else {
            setState(State.PASSOBSTACLE);
        }
    }

    public void end() {
        command = "Stop";
    }
}
