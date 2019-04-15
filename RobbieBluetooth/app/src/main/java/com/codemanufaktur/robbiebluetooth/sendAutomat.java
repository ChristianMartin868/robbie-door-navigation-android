package com.codemanufaktur.robbiebluetooth;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

public class sendAutomat {

    boolean initialized;

    /*
    init
    33-0-127-0-127-
    34-0-127-0-127-
    16-0-127-0-127-
    18-0-127-0-127-
    32-0-127-0-127-
    39-0-127-0-127-
    35-1-0-127-0-127-
    37-1-1-127-0-127-
    36-1-1-127-0-127-
    3-0-127-0-127-

    fwd
    1-1-63-127-0-127-
    17-0-127-0-127-
     */

    byte init1[] = new byte[] {33,0,127,0,127}; //0x21
    byte init2[] = new byte[] {34,0,127,0,127}; //0x22
    byte init3[] = new byte[] {16,0,127,0,127}; //0x10
    byte init4[] = new byte[] {18,0,127,0,127}; //0x12
    byte init5[] = new byte[] {32,0,127,0,127}; //0x20
    byte init6[] = new byte[] {39,0,127,0,127}; //0x27
    byte init7[] = new byte[] {35,1,0,127,0,127}; //0x23
    byte init8[] = new byte[] {37,1,1,127,0,127}; //0x25
    byte init9[] = new byte[] {36,1,1,127,0,127}; //0x24
    byte stop[] = new byte[] {3,0,127,0,127};
    byte heartbeat[] = new byte[] {17,0,127,0,127};
    byte fdw1[] = new byte[] {1,1,63,127,0,127};
    byte back[] = new byte[] {2,1,63,127,0,127};
    byte rightturn[] = new byte[] {9,1,0,127,0,127};
    byte leftturn[] = new byte[] {8,1,0,127,0,127};
    byte righttraverse[] = new byte[] {1,1,0,127,0,127};
    byte lefttraverse[] = new byte[] {1,1,127,127,0,127};
    byte some1[] = new byte[] {1,1,46,127,0,127};


    //                                                      0       1       2       3       4       5       6       7       8       9       10          11      12,    13           14        15              16            17
    List<byte[]> btcommandsList = Arrays.asList         (   init1,  init2,  init3,  init4,  init5,  init6,  init7,  init8,  init9,  stop,   heartbeat,  fdw1,   back,  rightturn,  leftturn,  righttraverse,  lefttraverse, some1 );
    List<Integer> btconnadsControlList = Arrays.asList  (   1,      2,      3,      4,      5,      6,      7,      8,      9,      9,      10,         10,     10,    10,          10,       10,             10,           10);

    public int cmdStop=9;
    public int cmdFwd=11;
    public int cmdBack=12;
    public int cmdrightturn=13;
    public int cmdleftturn=14;
    public int cmdrighttra=15;
    public int cmdlefttra=16;
    public int cmdsome1=17;

    private int indexToSend;

    public sendAutomat(){
        indexToSend=cmdStop;
    }

    void setNewCommand(int index){
        indexToSend=index;
    }

    byte[] getNextSequence(){
        int currentIndex;
        currentIndex = indexToSend;
        indexToSend = btconnadsControlList.get(indexToSend);     //define new indexToSend
        return btcommandsList.get(currentIndex);

    }
}
