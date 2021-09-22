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
    }

    public void setState(State newState) {
        stateStartTime = System.currentTimeMillis();

        switch (newState) {
            case START:
                initialize();
                break;
            case DOOR:
                break;
            case OBSTACLE:
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

    public long getStateStartTime() { return  stateStartTime; }

    public State getState() {
        return state;
    }

    public void initialize() {
        if (target == "No door") {
            command = "Left turn";
        }
        else {
            setState(State.DOOR);
        }
    }

    public void end() {
        command = "Stop";
    }
}
