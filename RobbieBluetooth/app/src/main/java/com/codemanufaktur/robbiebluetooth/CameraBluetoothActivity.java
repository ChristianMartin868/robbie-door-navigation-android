package com.codemanufaktur.robbiebluetooth;

import android.Manifest;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelUuid;
import android.os.Trace;
import static android.os.Looper.loop;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.codemanufaktur.robbiebluetooth.env.ImageUtils;
import com.codemanufaktur.robbiebluetooth.env.Logger;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.tensorflow.lite.examples.detection.tflite.DepthEstimator;
import org.tensorflow.lite.examples.detection.tflite.DepthEstimator.Model;
import org.tensorflow.lite.examples.detection.tflite.DepthEstimator.Device;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public abstract class CameraBluetoothActivity extends AppCompatActivity
        implements ImageReader.OnImageAvailableListener,
        Camera.PreviewCallback,
        CompoundButton.OnCheckedChangeListener,
        View.OnClickListener, AdapterView.OnItemSelectedListener {
    private static final Logger LOGGER = new Logger();

    private static final int PERMISSIONS_REQUEST = 1;

    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    protected int previewWidth = 0;
    protected int previewHeight = 0;
    private boolean debug = false;
    private Handler handler;
    private Timer mTimer;
    public static final int LOOP_INTERVAL_MS = 100;
    private HandlerThread handlerThread;
    private boolean useCamera2API;
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private Spinner modelSpinner;
    private Spinner deviceSpinner;

    //Von MainActivity
    private static final String TAG = "MY_APP_DEBUG_TAG";
    static int REQUEST_ENABLE_BT = 1;
    BluetoothAdapter myBluetoothAdapter;
    String RobotBTMacAdress="20:15:07:29:61:13"; //This is the Robots Bluetooth MacAdress
    UUID MY_UUID = new UUID(285278208, 64532);

    private Handler timeHandler = new Handler();
    public Automat automat = new Automat();
    public sendAutomat mysendAutomat = new sendAutomat();

    CameraBluetoothActivity.ConnectThread btConnectThread;
    CameraBluetoothActivity.ConnectedThread myBTconnectedThread;

    //int speedDefinedBySeekbar = 0;



    StringBuilder mystrb = new StringBuilder();
    /////////////////////////Ende MainActivity

    private LinearLayout bottomSheetLayout;
    private LinearLayout gestureLayout;
    private BottomSheetBehavior<LinearLayout> sheetBehavior;

    protected TextView frameValueTextView, frameDepthValueTextView, cropValueTextView, cropDepthValueTextView, cameraResolutionDepthTextView,
            rotationTextView, inferenceTimeTextView, inferenceDepthTimeTextView;
    protected ImageView bottomSheetArrowImageView;
    private ImageView plusImageView, minusImageView, plusDepthImageView, minusDepthImageView;
    private SwitchCompat apiSwitchCompat;
    private Switch previewSwitch;
    private TextView threadsTextView, threadsDepthTextView;
    TextureView textureView;

    private Model model = Model.MIDAS;
    private Device device = Device.CPU;
    private int numThreads = -1;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        LOGGER.d("onCreate " + this);
        super.onCreate(null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        automat.setState(State.START);

        setContentView(R.layout.activity_camera);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        if (hasPermission()) {
            setFragment();
        } else {
            requestPermission();
        }

        ///Von Main Activity
        prepareBluetooth();

        //Ende MainActivity

        threadsTextView = findViewById(R.id.threads);
        threadsDepthTextView = findViewById(R.id.threadsDepth);
        plusImageView = findViewById(R.id.plus);
        plusDepthImageView = findViewById(R.id.plusDepth);
        minusImageView = findViewById(R.id.minus);
        minusDepthImageView = findViewById(R.id.minusDepth);
        modelSpinner = findViewById(R.id.model_spinner);
        deviceSpinner = findViewById(R.id.device_spinner);
        apiSwitchCompat = findViewById(R.id.api_info_switch);
        previewSwitch = findViewById(R.id.previewSwitch);
        bottomSheetLayout = findViewById(R.id.bottom_sheet_layout);
        gestureLayout = findViewById(R.id.gesture_layout);
        sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
        bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow);

        ViewTreeObserver vto = gestureLayout.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                            gestureLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        } else {
                            gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                        //                int width = bottomSheetLayout.getMeasuredWidth();
                        int height = gestureLayout.getMeasuredHeight();

                        sheetBehavior.setPeekHeight(height);
                    }
                });
        sheetBehavior.setHideable(false);

        sheetBehavior.setBottomSheetCallback(
                new BottomSheetBehavior.BottomSheetCallback() {
                    @Override
                    public void onStateChanged(@NonNull View bottomSheet, int newState) {
                        switch (newState) {
                            case BottomSheetBehavior.STATE_HIDDEN:
                                break;
                            case BottomSheetBehavior.STATE_EXPANDED:
                            {
                                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down);
                            }
                            break;
                            case BottomSheetBehavior.STATE_COLLAPSED:
                            {
                                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                            }
                            break;
                            case BottomSheetBehavior.STATE_DRAGGING:
                                break;
                            case BottomSheetBehavior.STATE_SETTLING:
                                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                                break;
                        }
                    }

                    @Override
                    public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
                });

        frameValueTextView = findViewById(R.id.frame_info);
        frameDepthValueTextView = findViewById(R.id.frame_info_depth);
        cropValueTextView = findViewById(R.id.crop_info);
        cropDepthValueTextView = findViewById(R.id.crop_info_depth);
        cameraResolutionDepthTextView = findViewById(R.id.view_info_depth);
        rotationTextView = findViewById(R.id.rotation_info_depth);
        inferenceTimeTextView = findViewById(R.id.inference_info);
        inferenceDepthTimeTextView = findViewById(R.id.inference_info_depth);


        apiSwitchCompat.setOnCheckedChangeListener(this);
        previewSwitch.setOnCheckedChangeListener(this);

        modelSpinner.setOnItemSelectedListener(this);
        deviceSpinner.setOnItemSelectedListener(this);

        plusImageView.setOnClickListener(this);
        minusImageView.setOnClickListener(this);
        plusDepthImageView.setOnClickListener(this);
        minusDepthImageView.setOnClickListener(this);

        model = Model.valueOf(modelSpinner.getSelectedItem().toString().toUpperCase());
        device = Device.valueOf(deviceSpinner.getSelectedItem().toString());
        numThreads = Integer.parseInt(threadsDepthTextView.getText().toString().trim());

        previewSwitch.setChecked(false);
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected int getLuminanceStride() {
        return yRowStride;
    }

    protected byte[] getLuminance() {
        return yuvBytes[0];
    }

    /** Callback for android.hardware.Camera API */
    @Override
    public void onPreviewFrame(final byte[] bytes, final Camera camera) {
        if (isProcessingFrame) {
            LOGGER.w("Dropping frame!");
            return;
        }

        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                Camera.Size previewSize = camera.getParameters().getPreviewSize();
                previewHeight = previewSize.height;
                previewWidth = previewSize.width;
                rgbBytes = new int[previewWidth * previewHeight];
                onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
            }
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            return;
        }

        isProcessingFrame = true;
        yuvBytes[0] = bytes;
        yRowStride = previewWidth;

        imageConverter =
                new Runnable() {
                    @Override
                    public void run() {
                        ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
                    }
                };

        postInferenceCallback =
                new Runnable() {
                    @Override
                    public void run() {
                        camera.addCallbackBuffer(bytes);
                        isProcessingFrame = false;
                    }
                };
        processImage();
    }

    /** Callback for Camera2 API */
    @Override
    public void onImageAvailable(final ImageReader reader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }
        try {
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (isProcessingFrame) {
                image.close();
                return;
            }
            isProcessingFrame = true;
            Trace.beginSection("imageAvailable");
            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageUtils.convertYUV420ToARGB8888(
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    previewWidth,
                                    previewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                        }
                    };

            postInferenceCallback =
                    new Runnable() {
                        @Override
                        public void run() {
                            image.close();
                            isProcessingFrame = false;
                        }
                    };

            processImage();
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            Trace.endSection();
            return;
        }
        Trace.endSection();
    }

    @Override
    public synchronized void onStart() {
        LOGGER.d("onStart " + this);
        super.onStart();
        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loop();
                    }
                });
            }
        }, 0, LOOP_INTERVAL_MS);
    }

    @Override
    public synchronized void onResume() {
        LOGGER.d("onResume " + this);
        super.onResume();

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        LOGGER.d("onPause " + this);

        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }

        super.onPause();
    }

    @Override
    public synchronized void onStop() {
        LOGGER.d("onStop " + this);
        super.onStop();
        mTimer.cancel();
        mTimer = null;
    }

    @Override
    public synchronized void onDestroy() {
        LOGGER.d("onDestroy " + this);
        super.onDestroy();
        //Von MainActivity
        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(receiver);
        myBTconnectedThread.cancel();
        btConnectThread.cancel();
        //Ende MainActivity
    }

    //Von MainActivity
    @Override
    protected void onActivityResult (int requestCode,
                                     int resultCode,
                                     Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_ENABLE_BT && resultCode != RESULT_OK) {
            return; //TODO inform user and close App.
        }
        establishBTConnectionToRobot();
    }
    //Ende MainActivity

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, final String[] permissions, final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST) {
            if (allPermissionsGranted(grantResults)) {
                setFragment();
            } else {
                requestPermission();
            }
        }
    }

    private static boolean allPermissionsGranted(final int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(
                        CameraBluetoothActivity.this,
                        "Camera permission is required for this demo",
                        Toast.LENGTH_LONG)
                        .show();
            }
            requestPermissions(new String[] {PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
        }
    }

    // Returns true if the device supports the required hardware level, or better.
    private boolean isHardwareLevelSupported(
            CameraCharacteristics characteristics, int requiredLevel) {
        int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return requiredLevel == deviceLevel;
        }
        // deviceLevel is not LEGACY, can use numerical sort
        return requiredLevel <= deviceLevel;
    }

    private String chooseCamera() {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                final StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map == null) {
                    continue;
                }

                // Fallback to camera1 API for internal cameras that don't have full support.
                // This should help with legacy situations where using the camera2 API causes
                // distorted or otherwise broken previews.
                useCamera2API =
                        (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                                || isHardwareLevelSupported(
                                characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
                LOGGER.i("Camera API lv2?: %s", useCamera2API);
                return cameraId;
            }
        } catch (CameraAccessException e) {
            LOGGER.e(e, "Not allowed to access camera");
        }

        return null;
    }

    protected void setFragment() {
        String cameraId = chooseCamera();

        Fragment fragment;
        if (useCamera2API) {
            CameraConnectionFragment camera2Fragment =
                    CameraConnectionFragment.newInstance(
                            new CameraConnectionFragment.ConnectionCallback() {
                                @Override
                                public void onPreviewSizeChosen(final Size size, final int rotation) {
                                    previewHeight = size.getHeight();
                                    previewWidth = size.getWidth();
                                    CameraBluetoothActivity.this.onPreviewSizeChosen(size, rotation);
                                }
                            },
                            this,
                            getLayoutId(),
                            getDesiredPreviewFrameSize());

            camera2Fragment.setCamera(cameraId);
            fragment = camera2Fragment;
        } else {
            fragment =
                    new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
        }

        getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    public boolean isDebug() {
        return debug;
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    @UiThread
    protected void showResultsInTexture(Bitmap bitmap) {

        textureView = findViewById(R.id.textureView3);
        //AutoFitTextureView textureView = (AutoFitTextureView) findViewById(R.id.texture);

        if(textureView.isAvailable()) {
            if(previewSwitch.isChecked()) {
                textureView.setVisibility(View.VISIBLE);
                Canvas canvas = textureView.lockCanvas();
                canvas.drawColor(Color.BLUE);
                Paint paint = new Paint();
                paint.setStyle(Paint.Style.FILL);
                paint.setARGB(255, 150, 150, 150);

                int canvas_size = Math.min(canvas.getWidth(), canvas.getHeight());
                canvas.drawBitmap(bitmap, null, new RectF(0, 0, canvas_size, canvas_size), null);
                textureView.unlockCanvasAndPost(canvas);
            }
            if(!previewSwitch.isChecked()) {
                textureView.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        setUseNNAPI(isChecked);
        if (isChecked) apiSwitchCompat.setText("NNAPI");
        else apiSwitchCompat.setText("TFLITE");
        if (buttonView.getId() == R.id.previewSwitch) {
            if (isChecked) {
                textureView.setVisibility(View.VISIBLE);
                Toast.makeText(getApplicationContext(), "Toggle is enabled", Toast.LENGTH_LONG).show();
            } else {
                textureView.setVisibility(View.INVISIBLE);
                Toast.makeText(getApplicationContext(), "Toggle is disabled", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.plus) {
            String threads = threadsTextView.getText().toString().trim();
            int numThreads = Integer.parseInt(threads);
            if (numThreads >= 9) return;
            numThreads++;
            threadsTextView.setText(String.valueOf(numThreads));
            setNumThreads(numThreads);
        } else if (v.getId() == R.id.minus) {
            String threads = threadsTextView.getText().toString().trim();
            int numThreads = Integer.parseInt(threads);
            if (numThreads == 1) {
                return;
            }
            numThreads--;
            threadsTextView.setText(String.valueOf(numThreads));
            setNumThreads(numThreads);
        }

        if (v.getId() == R.id.plusDepth) {
            String threads = threadsDepthTextView.getText().toString().trim();
            int numThreads = Integer.parseInt(threads);
            if (numThreads >= 9) return;
            numThreads++;
            threadsDepthTextView.setText(String.valueOf(numThreads));
            setNumThreadsForDepthEstimation(numThreads);
        } else if (v.getId() == R.id.minusDepth) {
            String threads = threadsDepthTextView.getText().toString().trim();
            int numThreads = Integer.parseInt(threads);
            if (numThreads == 1) {
                return;
            }
            numThreads--;
            threadsDepthTextView.setText(String.valueOf(numThreads));
            setNumThreadsForDepthEstimation(numThreads);
        }
    }

    protected void showFrameInfo(String frameInfo) {
        frameValueTextView.setText(frameInfo);
    }

    protected void showFrameDepthInfo(String frameInfo) {
        frameDepthValueTextView.setText(frameInfo);
    }

    protected void showCropInfo(String cropInfo) {
        cropValueTextView.setText(cropInfo);
    }

    protected void showCropDepthInfo(String cropInfo) {
        cropDepthValueTextView.setText(cropInfo);
    }

    protected void showCameraResolutionOfDepthEstimation(String cameraInfo) {
        cameraResolutionDepthTextView.setText(cameraInfo);
    }

    protected void showRotationInfo(String rotation) {
        rotationTextView.setText(rotation);
    }

    protected void showInference(String inferenceTime) {
        inferenceTimeTextView.setText(inferenceTime);
    }

    protected void showInferenceOfDepthEstimation(String inferenceTime) {
        inferenceDepthTimeTextView.setText(inferenceTime);
    }

    protected Model getModel() {
        return model;
    }

    private void setModel(Model model) {
        if (this.model != model) {
            LOGGER.d("Updating  model: " + model);
            this.model = model;
            onInferenceConfigurationChanged();
        }
    }

    protected Device getDevice() {
        return device;
    }

    private void setDevice(Device device) {
        if (this.device != device) {
            LOGGER.d("Updating  device: " + device);
            this.device = device;
            final boolean threadsEnabled = device == Device.CPU;
            plusDepthImageView.setEnabled(threadsEnabled);
            minusDepthImageView.setEnabled(threadsEnabled);
            threadsDepthTextView.setText(threadsEnabled ? String.valueOf(numThreads) : "N/A");
            onInferenceConfigurationChanged();
        }
    }

    protected int getNumThreads() {
        return numThreads;
    }

    private void setNumThreadsForDepthEstimation(int numThreads) {
        if (this.numThreads != numThreads) {
            LOGGER.d("Updating  numThreads: " + numThreads);
            this.numThreads = numThreads;
            onInferenceConfigurationChanged();
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        if (parent == modelSpinner) {
            setModel(Model.valueOf(parent.getItemAtPosition(pos).toString().toUpperCase()));
        } else if (parent == deviceSpinner) {
            setDevice(Device.valueOf(parent.getItemAtPosition(pos).toString()));
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing.
    }

    protected abstract void loop();

    protected abstract void processImage();

    protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

    protected abstract int getLayoutId();

    protected abstract Size getDesiredPreviewFrameSize();

    protected abstract void onInferenceConfigurationChanged();

    protected abstract void setNumThreads(int numThreads);

    protected abstract void setUseNNAPI(boolean isChecked);

    //Von MainActivity

    void startSendTimer(){
        timeHandler.postDelayed(runnable, 100);
    }

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            /* do what you need to do */
            byte[] cmd = mysendAutomat.getNextSequence();
            myBTconnectedThread.write(cmd);


            if (cmd[0] == 81) {
                //TextView tv = findViewById(R.id.messagetextfield);
                StringBuilder strb = new StringBuilder();
                strb.append(cmd[0]);
                strb.append("-");
                strb.append(cmd[1]);
                strb.append("-");
                strb.append(cmd[2]);
                strb.append("-");
                strb.append(cmd[3]);
                strb.append("__");
                //tv.append(strb.toString());
            }

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

    // ################## Bluetooth

    // ################### SERVER TEST

    void prepareBluetoothServer(){
        myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (myBluetoothAdapter == null) {
            //TextView TV = findViewById(R.id.messagetextfield);
            //TV.append("error getting default bt adapter");
            return; //TODO: inform User, close App. However this is quite unusual for our devices
        }
        if (!myBluetoothAdapter.isEnabled()) {
            return;
        }else{
            establishServer();
        }
    }

    void establishServer(){
        CameraBluetoothActivity.AcceptThread myAcceptThread = new CameraBluetoothActivity.AcceptThread();
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
            //TextView TV = findViewById(R.id.messagetextfield);
            //TV.append("error getting default bt adapter");
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
        btConnectThread = new CameraBluetoothActivity.ConnectThread(device);
        new Thread(btConnectThread).start();
        //btConnectThread.run();

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
                //TextView tv = findViewById(R.id.messagetextfield);
                //tv.append("Socket's create() method failed: " + e.getMessage() + "\n\n");

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
                //TextView tv1 = findViewById(R.id.messagetextfield);
                //tv1.append ("could not open bt socket. Is the robot turned on?\n");
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    //tv1.append("Could not close the client socket: " + closeException.getMessage() + "\n\n");
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
                //TextView tv = findViewById(R.id.messagetextfield);
                //tv.append("Could not close the client socket: " + e.getMessage() + "\n\n");
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
        myBTconnectedThread = new CameraBluetoothActivity.ConnectedThread(mmSocket);

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

    //Ende MainActivity
}
