package com.codemanufaktur.robbiebluetooth;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;

import com.codemanufaktur.robbiebluetooth.customview.OverlayView;
import com.codemanufaktur.robbiebluetooth.env.BorderedText;
import com.codemanufaktur.robbiebluetooth.env.ImageUtils;
import com.codemanufaktur.robbiebluetooth.env.Logger;
import com.codemanufaktur.robbiebluetooth.navigation.DoorSignal;
import com.codemanufaktur.robbiebluetooth.navigation.DriveCommand;
import com.codemanufaktur.robbiebluetooth.navigation.ObstacleDetector;
import com.codemanufaktur.robbiebluetooth.navigation.ObstacleSignal;
import com.codemanufaktur.robbiebluetooth.tracking.MultiBoxTracker;
import com.google.android.material.snackbar.Snackbar;

import org.tensorflow.lite.examples.detection.tflite.DepthEstimator;
import org.tensorflow.lite.examples.detection.tflite.DepthEstimator.Device;
import org.tensorflow.lite.examples.detection.tflite.DepthEstimator.Model;
import org.tensorflow.lite.examples.detection.tflite.Detector;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends CameraBluetoothActivity implements ImageReader.OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();
    /**
     * Latest obstacle signal. Written by the inference thread, read by the 100 ms control loop
     * on the UI thread, hence volatile. Null until the first depth map has been processed.
     */
    private volatile ObstacleSignal obstacleSignal = null;
    private final ObstacleDetector obstacleDetector = new ObstacleDetector();

    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 320;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "mobilenetV2FpnLiteQuantized.tflite";
    private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Detector detector;
    private DepthEstimator depthEstimator;

    /** Input image size of the model along x axis. */
    private int imageSizeX;
    /** Input image size of the model along y axis. */
    private int imageSizeY;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap rgbFrameDepthBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;
    private boolean computingDepth = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        recreateDepthEstimator(getModel(), getDevice(), getNumThreads());

        if (depthEstimator == null) {
            LOGGER.e("No depthEstimator on preview!");
            return;
        }

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            this,
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing Detector!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        rgbFrameDepthBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new OverlayView.DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final int cropSizeDepthmap = Math.min(previewWidth, previewHeight);

        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }

        if (computingDepth) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        computingDepth = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
        rgbFrameDepthBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Detector.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }

                        final List<Detector.Recognition> mappedRecognitions =
                                new ArrayList<Detector.Recognition>();

                        for (final Detector.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                canvas.drawRect(location, paint);

                                cropToFrameTransform.mapRect(location);

                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }

                        tracker.trackResults(mappedRecognitions, currTimestamp);

                        trackingOverlay.postInvalidate();

                        //computingDetection = false;

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showFrameInfo(previewWidth + "x" + previewHeight);
                                        showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                        showInference(lastProcessingTimeMs + "ms");
                                    }
                                });


                        if (depthEstimator != null) {
                            final long startTimeDepthEstimation = SystemClock.uptimeMillis();
                            float [] img_array = depthEstimator.recognizeImage(rgbFrameDepthBitmap, sensorOrientation);
                            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTimeDepthEstimation;

                            final int width = imageSizeX;
                            final int height = imageSizeY;
                            // The MiDaS output is rotated by 180 degrees relative to the preview.
                            final float[] disparity = ObstacleDetector.rotate180(img_array);
                            obstacleSignal = obstacleDetector.detect(disparity, width, height);

                            // Grey-scale preview of the depth map (visualisation only).
                            final Bitmap bitmap = depthPreview(disparity, width, height);


                            //computingDetection = false;
                            //computingDepth = false;

                            runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            showResultsInTexture(bitmap);
                                            showFrameDepthInfo(previewWidth + "x" + previewHeight);
                                            showCropDepthInfo(imageSizeX + "x" + imageSizeY);
                                            showCameraResolutionOfDepthEstimation(cropSizeDepthmap + "x" + cropSizeDepthmap);
                                            showRotationInfo(String.valueOf(sensorOrientation));
                                            showInferenceOfDepthEstimation(lastProcessingTimeMs + "ms");
                                            Snackbar.make(findViewById(R.id.myCoordinatorLayout), "State: " + navigation.getState() + "          End time in ms: " + navigation.getRunDurationMs(), Snackbar.LENGTH_SHORT).show();
                                        }
                                    });

                            //Toast.makeText(getApplicationContext(), "Counter + r*numC: " + String.valueOf(counter) + " < " + String.valueOf((r * numC)), Toast.LENGTH_LONG).show();

                        }
                        computingDetection = false;
                        computingDepth = false;
                    }
                });
    }

    @Override
    protected void loop() {
        timerCounterLoop++;
        final DoorSignal door = tracker != null ? tracker.doorSignal : null;
        final ObstacleSignal obstacle = obstacleSignal;
        if (door == null || obstacle == null) {
            return; // no perception result yet
        }
        final DriveCommand command = navigation.step(door, obstacle, SystemClock.uptimeMillis());
        switch (command) {
            case LEFT_TURN:
                mysendAutomat.setNewCommand(mysendAutomat.cmdleftturn);
                break;
            case RIGHT_TURN:
                mysendAutomat.setNewCommand(mysendAutomat.cmdrightturn);
                break;
            case FORWARD:
                mysendAutomat.setNewCommand(mysendAutomat.cmdFwd);
                break;
            case STOP:
            default:
                mysendAutomat.setNewCommand(mysendAutomat.cmdStop);
                break;
        }
    }

    /** Min-max normalised grey-scale bitmap of a disparity map (for the on-screen preview). */
    private static Bitmap depthPreview(float[] disparity, int width, int height) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < width * height; i++) {
            min = Math.min(min, disparity[i]);
            max = Math.max(max, disparity[i]);
        }
        final float scale = max > min ? 255f / (max - min) : 0f;
        final int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            final int v = (int) ((disparity[i] - min) * scale);
            pixels[i] = Color.rgb(v, v, v);
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.RGB_565);
    }

    @Override
    protected void onInferenceConfigurationChanged() {
        if (rgbFrameBitmap == null) {
            // Defer creation until we're getting camera frames.
            return;
        }
        final Device device = getDevice();
        final Model model = getModel();
        final int numThreads = getNumThreads();
        runInBackground(() -> recreateDepthEstimator(model, device, numThreads));
    }

    private void recreateDepthEstimator(Model model, Device device, int numThreads) {
        if (depthEstimator != null) {
            LOGGER.d("Closing depthEsimator.");
            depthEstimator.close();
            depthEstimator = null;
        }

        try {
            LOGGER.d(
                    "Creating depthEstimator (device=%s, numThreads=%d)", device, numThreads);
            depthEstimator = DepthEstimator.create(this, model, device, numThreads);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.e(e, "Failed to create depthEstimator.");
            runOnUiThread(
                    () -> {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    });
            return;
        }

        // Updates the input image size.
        imageSizeX = depthEstimator.getImageSizeX();
        imageSizeY = depthEstimator.getImageSizeY();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(
                () -> {
                    try {
                        detector.setUseNNAPI(isChecked);
                    } catch (UnsupportedOperationException e) {
                        LOGGER.e(e, "Failed to set \"Use NNAPI\".");
                        runOnUiThread(
                                () -> {
                                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    }
                });
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }
}
