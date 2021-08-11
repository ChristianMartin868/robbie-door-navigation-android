package org.tensorflow.lite.examples.detection.tflite;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.MappedByteBuffer;

import static java.lang.Math.min;

public abstract class DepthEstimator {
    public static final String TAG = "ClassifierWithSupport";

    /** The model type used for classification. */
    public enum Model {
        PYDNET,
        MIDAS
    }

    /** The runtime device type used for executing classification. */
    public enum Device {
        CPU,
        NNAPI,
        GPU
    }

    /** The loaded TensorFlow Lite model. */

    /** Image size along the x axis. */
    private final int imageSizeX;

    /** Image size along the y axis. */
    private final int imageSizeY;

    /** Optional GPU delegate for accleration. */
    private GpuDelegate gpuDelegate = null;

    /** Optional NNAPI delegate for accleration. */
    private NnApiDelegate nnApiDelegate = null;

    /** An instance of the driver class to run model inference with Tensorflow Lite. */
    protected Interpreter tflite;

    /** Options for configuring the Interpreter. */
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();

    /** Input image TensorBuffer. */
    private TensorImage inputImageBuffer;

    /** Output TensorBuffer. */
    private final TensorBuffer outputByteBuffer;

    /** Processer to apply post processing of the output . */
    private final TensorProcessor outputByteProcessor;

    /**
     * Creates a classifier with the provided configuration.
     *
     * @param activity The current Activity.
     * @param device The device to use for classification.
     * @param numThreads The number of threads to use for classification.
     * @return A classifier with the desired configuration.
     */
    public static DepthEstimator create(Activity activity, Model model, DepthEstimator.Device device, int numThreads)
            throws IOException {
        if (model == Model.PYDNET) {
            return new DepthEstimatorPyDNet(activity, device, numThreads);
        } else if (model == Model.MIDAS) {
            return new DepthEstimatorMidas(activity, device, numThreads);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /** Initializes a {@code Classifier}. */
    protected DepthEstimator(Activity activity, DepthEstimator.Device device, int numThreads) throws IOException {
        MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(activity, getModelPath());
        switch (device) {
            case NNAPI:
                nnApiDelegate = new NnApiDelegate();
                tfliteOptions.addDelegate(nnApiDelegate);
                break;
            case GPU:
                gpuDelegate = new GpuDelegate();
                tfliteOptions.addDelegate(gpuDelegate);
                break;
            case CPU:
                tfliteOptions.setUseXNNPACK(true);
                break;
        }
        tfliteOptions.setNumThreads(numThreads);
        tflite = new Interpreter(tfliteModel, tfliteOptions);

        // Reads type and shape of input and output tensors, respectively.
        int imageTensorIndex = 0;
        int[] imageShape = tflite.getInputTensor(imageTensorIndex).shape(); // {1, height, width, 3}
        imageSizeY = imageShape[1];
        imageSizeX = imageShape[2];
        DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();
        int probabilityTensorIndex = 0;
        int[] probabilityShape =
                tflite.getOutputTensor(probabilityTensorIndex).shape(); // {1, NUM_CLASSES}
        DataType probabilityDataType = tflite.getOutputTensor(probabilityTensorIndex).dataType();

        // Creates the input tensor.
        inputImageBuffer = new TensorImage(imageDataType);

        // Creates the output tensor and its processor.
        outputByteBuffer = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);

        // Creates the post processor for the output probability.
        outputByteProcessor = new TensorProcessor.Builder().add(getPostprocessNormalizeOp()).build();

        Log.d(TAG, "Created a Tensorflow Lite Image Classifier.");
    }

    /** Runs inference and returns the classification results.
     * @return*/
    public float[] recognizeImage(final Bitmap bitmap, int sensorOrientation) {
        // Logs this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage");

        Trace.beginSection("loadImage");
        long startTimeForLoadImage = SystemClock.uptimeMillis();
        inputImageBuffer = loadImage(bitmap, sensorOrientation);
        long endTimeForLoadImage = SystemClock.uptimeMillis();
        Trace.endSection();
        Log.v(TAG, "Timecost to load the image: " + (endTimeForLoadImage - startTimeForLoadImage));

        // Runs the inference call.
        Trace.beginSection("runInference");
        long startTimeForReference = SystemClock.uptimeMillis();
        tflite.run(inputImageBuffer.getBuffer(), outputByteBuffer.getBuffer().rewind());
        long endTimeForReference = SystemClock.uptimeMillis();
        Trace.endSection();
        Log.v(TAG, "Timecost to run model inference: " + (endTimeForReference - startTimeForReference));
        float[] img_array = outputByteBuffer.getFloatArray();
        Trace.endSection();
        return img_array;
    }

    /** Closes the interpreter and model to release resources. */
    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
        if (gpuDelegate != null) {
            gpuDelegate.close();
            gpuDelegate = null;
        }
        if (nnApiDelegate != null) {
            nnApiDelegate.close();
            nnApiDelegate = null;
        }
    }

    /** Get the image size along the x axis. */
    public int getImageSizeX() {
        return imageSizeX;
    }

    /** Get the image size along the y axis. */
    public int getImageSizeY() {
        return imageSizeY;
    }

    /** Loads input image, and applies preprocessing. */
    private TensorImage loadImage(final Bitmap bitmap, int sensorOrientation) {
        // Loads bitmap into a TensorImage.
        inputImageBuffer.load(bitmap);

        // Creates processor for the TensorImage.
        int cropSize = min(bitmap.getWidth(), bitmap.getHeight());
        int numRotation = sensorOrientation / 90;
        // TODO(b/143564309): Fuse ops inside ImageProcessor.
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeWithCropOrPadOp(cropSize, cropSize))
                        // TODO(b/169379396): investigate the impact of the resize algorithm on accuracy.
                        // To get the same inference results as lib_task_api, which is built on top of the Task
                        // Library, use ResizeMethod.BILINEAR.
                        .add(new ResizeOp(imageSizeX, imageSizeY, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                        .add(new Rot90Op(numRotation))
                        .add(getPreprocessNormalizeOp())
                        .build();
        return imageProcessor.process(inputImageBuffer);
    }

    /** Gets the name of the model file stored in Assets. */
    protected abstract String getModelPath();

    /** Gets the TensorOperator to nomalize the input image in preprocessing. */
    protected abstract TensorOperator getPreprocessNormalizeOp();

    /**
     * Gets the TensorOperator to dequantize the output probability in post processing.
     *
     * <p>For quantized model, we need de-quantize the prediction with NormalizeOp (as they are all
     * essentially linear transformation). For float model, de-quantize is not required. But to
     * uniform the API, de-quantize is added to float model too. Mean and std are set to 0.0f and
     * 1.0f, respectively.
     */
    protected abstract TensorOperator getPostprocessNormalizeOp();
}

