package com.codemanufaktur.robbiebluetooth;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;


import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MY_APP_DEBUG_TAG";
    static int REQUEST_ENABLE_BT = 1;
    BluetoothAdapter myBluetoothAdapter;
    String RobotBTMacAdress="20:15:07:29:61:13"; //This is the Robots Bluetooth MacAdress
    UUID MY_UUID = new UUID(285278208, 64532);

    private Handler timeHandler = new Handler();
    private sendAutomat mysendAutomat = new sendAutomat();

    ConnectThread btConnectThread;
    ConnectedThread myBTconnectedThread;



    StringBuilder mystrb = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prepareBluetooth();


        //prepareBluetoothServer();


        setButtonFunctions();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(receiver);
        myBTconnectedThread.cancel();
        btConnectThread.cancel();
    }

    @Override
    protected void onActivityResult (int requestCode,
                                     int resultCode,
                                     Intent data){
        if (requestCode != REQUEST_ENABLE_BT && resultCode != RESULT_OK){
            return; //TODO inform user and close App.
        }
        establishBTConnectionToRobot();
    }

    void startSendTimer(){
        timeHandler.postDelayed(runnable, 100);
    }

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            /* do what you need to do */
            byte[] cmd = mysendAutomat.getNextSequence();
            myBTconnectedThread.write(cmd);

            /* and here comes the "trick" */
            timeHandler.postDelayed(this, 100);
        }
    };


    /*
        const byte END_OF_FRAME_1 = 0x7F;
        const byte END_OF_FRAME_2 = 0x00;
        const byte END_OF_FRAME_3 = 0x7F;

        const int GO_FORWARD = 0x01;
        const int STOP = 0x03;
     */

    void setButtonFunctions(){
        findViewById(R.id.btnSome1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TextView tv = findViewById(R.id.messagetextfield);
                //tv.setText(mystrb.toString());
                mysendAutomat.setNewCommand(mysendAutomat.cmdsome1);
            }
        });


        findViewById(R.id.btnlefttra).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mysendAutomat.setNewCommand(mysendAutomat.cmdlefttra);
            }
        });

        findViewById(R.id.btnrighttra).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mysendAutomat.setNewCommand(mysendAutomat.cmdrighttra);
            }
        });

        findViewById(R.id.btnleftturn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mysendAutomat.setNewCommand(mysendAutomat.cmdleftturn);
            }
        });

        findViewById(R.id.btnrightturn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mysendAutomat.setNewCommand(mysendAutomat.cmdrightturn);
            }
        });

        findViewById(R.id.btnback).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mysendAutomat.setNewCommand(mysendAutomat.cmdBack);
            }
        });



        findViewById(R.id.btnstop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mysendAutomat.setNewCommand(mysendAutomat.cmdStop);
            }
        });

        findViewById(R.id.btnfwd).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mysendAutomat.setNewCommand(mysendAutomat.cmdFwd);
            }
        });

       /*Button btn = findViewById(R.id.btnstop);
        btn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // PRESSED
                        TextView tv = findViewById(R.id.messagetextfield);
                        tv.append ("INaction");
                        return true; // if you want to handle the touch event
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // RELEASED
                        TextView tv1 = findViewById(R.id.messagetextfield);
                        tv1.append ("OUTaction");
                        return true; // if you want to handle the touch event
                }
                return false;
            }
        });*/




    }


    // ################## Bluetooth

    // ################### SERVER TEST

    void prepareBluetoothServer(){
        myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (myBluetoothAdapter == null) {
            TextView TV = findViewById(R.id.messagetextfield);
            TV.append("error getting default bt adapter");
            return; //TODO: inform User, close App. However this is quite unusual for our devices
        }
        if (!myBluetoothAdapter.isEnabled()) {
            return;
        }else{
            establishServer();
        }
    }

    void establishServer(){
        AcceptThread myAcceptThread = new AcceptThread();
        new Thread(myAcceptThread).start();

    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
                tmp = myBluetoothAdapter.listenUsingRfcommWithServiceRecord("myServerName", MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    manageMyConnectedSocket(socket);
                    try {
                        mmServerSocket.close();
                    }
                    catch(IOException e){
                        Log.e(TAG, "server close not possible", e);
                    }
                    break;
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }



    //######################

    void prepareBluetooth(){
        myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (myBluetoothAdapter == null) {
            TextView TV = findViewById(R.id.messagetextfield);
            TV.append("error getting default bt adapter");
            return; //TODO: inform User, close App. However this is quite unusual for our devices
        }
        if (!myBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }else{
            establishBTConnectionToRobot();
        }
    }

    void establishBTConnectionToRobot(){
        BluetoothDevice robotMac = queryPairedDevicesForRobot();
        if (robotMac == null){
            startBTDiscovery();
        }else{
            connectBTToClientMacaddress(robotMac);
        }
    }




    //#################### BT Discovery
    void startBTDiscovery(){
        // Register for broadcasts when a device is discovered.
        IntentFilter filter_found = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter filter_bondstatechanged = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(receiver, filter_found);
        registerReceiver(receiver, filter_bondstatechanged);
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                if (checkIfMacAddressIsRobotsAddress(deviceHardwareAddress)){
                    connectBTToClientMacaddress(device);
                }
            }
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                if (checkIfMacAddressIsRobotsAddress(deviceHardwareAddress)){
                    connectBTToClientMacaddress(device);
                }
            }
        }
    };

    //#################### END BT Discovery

    boolean checkIfMacAddressIsRobotsAddress(String btAdress){
        return RobotBTMacAdress.equals(btAdress);
    }

    BluetoothDevice queryPairedDevicesForRobot(){
        Set<BluetoothDevice> pairedDevices = myBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                if (checkIfMacAddressIsRobotsAddress(deviceHardwareAddress)){
                    return device;
                }
            }
        }
        return null;
    }







    // #############################################
    //          CLIENT
    // #############################################
    // #############################################
    // #############################################
    // #############################################
    // #############################################
    // #############################################

    void connectBTToClientMacaddress(BluetoothDevice device){
        boolean a = device.fetchUuidsWithSdp();
        ParcelUuid[] uuds = device.getUuids();
        String myuuid=  MY_UUID.toString();
        String asd = uuds[0].getUuid().toString();
        //MY_UUID.fromString( );
        MY_UUID = uuds[0].getUuid();
        String myuuid2=  MY_UUID.toString();
        btConnectThread = new ConnectThread(device);
        btConnectThread.run();

    }





    // ################ Bluetooth connect Thread Start
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);


            } catch (IOException e) {
                TextView tv = findViewById(R.id.messagetextfield);
                tv.append("Socket's create() method failed: " + e.getMessage() + "\n\n");

            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            myBluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.

                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                TextView tv1 = findViewById(R.id.messagetextfield);
                tv1.append ("could not open bt socket. Is the robot turned on?\n");
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    tv1.append("Could not close the client socket: " + closeException.getMessage() + "\n\n");
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            manageMyConnectedSocket(mmSocket);
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                TextView tv = findViewById(R.id.messagetextfield);
                tv.append("Could not close the client socket: " + e.getMessage() + "\n\n");
                //Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }
    // ################ Bluetooth connect Thread Start END

    //private Handler handler; // handler that gets info from Bluetooth service
    private interface MessageConstants {
        public static final int MESSAGE_READ = 0;
        public static final int MESSAGE_WRITE = 1;
        public static final int MESSAGE_TOAST = 2;

        // ... (Add other message types here as needed.)
    }

    void manageMyConnectedSocket(BluetoothSocket mmSocket){
        myBTconnectedThread = new ConnectedThread(mmSocket);

        new Thread(myBTconnectedThread).start();

        //myBTconnectedThread.run();
    }



    // ######################## Bluetooth connected Thread start
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            startSendTimer();
        }

        public void run() {
            mmBuffer = new byte[1024];
            int numBytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer);
                    if (numBytes != 0){
                        //TextView tv = findViewById(R.id.messagetextfield);

                        for (int i=0; i<numBytes; i++){
                            mystrb.append(mmBuffer[i]);
                            mystrb.append("-");
                        }
                        mystrb.append("#");
                        //tv.append(mystrb.toString());
                        if (mmBuffer[0] != 66 && mmBuffer[0]!=97){
                            int a = 0;
                        }

                    }
                        //TextView tv = findViewById(R.id.messagetextfield);
                        //tv.append (mmBuffer.toString()+"\n");
                        //tv.append ("Test\n");
                    //}
                    // Send the obtained bytes to the UI activity.

                    //Message readMsg = handler.obtainMessage(
                      //      MessageConstants.MESSAGE_READ, numBytes, -1,
                      //      mmBuffer);
                    //readMsg.sendToTarget();
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);

                // Share the sent message with the UI activity.
                //Message writtenMsg = handler.obtainMessage(
                //        MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
                //writtenMsg.sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);

                // Send a failure message back to the activity.
                //Message writeErrorMsg =
                //        handler.obtainMessage(MessageConstants.MESSAGE_TOAST);
                //Bundle bundle = new Bundle();
                //bundle.putString("toast",
                //        "Couldn't send data to the other device");
                //writeErrorMsg.setData(bundle);
                //handler.sendMessage(writeErrorMsg);
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    // ######################## Bluetooth connected Thread end
}
