package com.codemanufaktur.robbiebluetooth;

import android.os.SystemClock;

public class Automat {
    int number;
    String state;

    public Automat() {
        number = 0;
        state = "Left turn";
    }

    public void input(String in) {
        switch (number) {
            case 0:
                if (in == "No door") {
                    state = "Left turn";
                } else {
                    number = 1;
                    state = "Forward";
                }
                break;
            case 1:
                if (in == "Door not reached and no obstacle") {
                    state = "Foward";
                }
                else if (in == "Door is left, not reached and no obstacle") {
                    state = "Left turn";
                }
                else if (in == "Door is right, not reached and no obstacle") {
                    state = "Right turn";
                }
                else if (in == "Door reached") {
                    number = 4;
                    state = "Stop";
                } else {
                    number = 2;
                    state = "Stop";
                }
                break;
            case 2:
                if (in == "Obstacle") {
                    state = "Stop";
                }
                else if (in == "Obstacle left") {
                    state = "Right turn";
                }
                else if (in == "Obstacle right") {
                    state = "Left turn";
                }
                else {
                    number = 3;
                    state = "Forward";
                }
                break;
            case 3:
                final long startTime = SystemClock.uptimeMillis();
                if ((SystemClock.uptimeMillis() - startTime > 2000)) {
                    number = 0;
                    state = "Left turn";
                } else {
                    state = "Forward";
                }
                break;
            case 4:
                state = "Stop";
                break;
            default:
                System.out.println("error");
                state = "Error";
                break;
        }
    }

    public int getNumber() {
        return number;
    }

    public String getState() {
        return state;
    }
}
