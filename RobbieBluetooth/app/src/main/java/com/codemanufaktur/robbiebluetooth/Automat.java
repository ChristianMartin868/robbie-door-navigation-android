package com.codemanufaktur.robbiebluetooth;

import android.os.CountDownTimer;
import android.os.SystemClock;

public class Automat {
    State state;
    String command;
    long stateStartTime;

    public Automat() {
        state = State.START;
        command = "Left turn";
    }

    public void input(String target, String obstacleTarget) {
        switch (state) {
            case START:
                if (target == "No door") {
                    setState(State.START);
                    command = "Left turn";
                }
                else {
                    setState(State.DOOR);
                    command = "Forward";
                }
                break;
            case DOOR:
                if ((target == "Door not reached") && (obstacleTarget == "No obstacle")) {
                    setState(State.DOOR);
                    command = "Forward";
                }
                else if ((target == "Door is left") && (obstacleTarget == "No obstacle")) {
                    setState(State.DOOR);
                    command = "Left turn";
                }
                else if ((target == "Door is right") && (obstacleTarget == "No obstacle")) {
                    setState(State.DOOR);
                    command = "Right turn";
                }
                else if (target == "Door reached") {
                    setState(State.END);
                    command = "Stop";
                }
                if ((obstacleTarget == "Obstacle left") || (obstacleTarget == "Obstacle right")) {
                    setState(State.OBSTACLE);
                    command = "Stop";
                }
                break;
            case OBSTACLE:
                if (target == "Obstacle left") {
                    setState(State.OBSTACLE);
                    command = "Right turn";
                    //state = "Right turn to avoid obstacle";
                }
                else if (target == "Obstacle right") {
                    setState(State.OBSTACLE);
                    command = "Left turn";
                    //state = "Left turn to avoid obstacle";
                }
                else {
                    setState(State.PASSOBSTACLE);
                    command = "Forward";
                    //state = "Forward to pass obstacle";
                }
                break;
            case PASSOBSTACLE:
                //Count Down Timer to pass obstacle
                if (obstacleTarget == "No obstacle") {
                    command = "Forward";
                    //state = "Cas3 - Forward to pass obstacle";
                }
                else {
                    setState(State.OBSTACLE);
                    command = "Stop";
                }
                if (getStateTimeMs() > 3000) {
                    setState(State.START);
                    command = "Left turn";
                }
                break;
            case END:
                command = "Stop";
                break;
            default:
                System.out.println("error");
                command = "Error";
                break;
        }
    }

    private void setState(State newState) {
        stateStartTime = System.currentTimeMillis();
        state = newState;
    }

    private long getStateTimeMs() {
        return System.currentTimeMillis() - stateStartTime;
    }

    public State getState() {
        return state;
    }

    public String getCommand() {
        return command;
    }
}
