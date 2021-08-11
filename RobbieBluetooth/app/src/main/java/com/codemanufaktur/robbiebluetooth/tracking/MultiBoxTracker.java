package com.codemanufaktur.robbiebluetooth.tracking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Display;
import android.widget.Toast;

import com.codemanufaktur.robbiebluetooth.MainActivity;
import com.codemanufaktur.robbiebluetooth.env.BorderedText;
import com.codemanufaktur.robbiebluetooth.env.ImageUtils;
import com.codemanufaktur.robbiebluetooth.env.Logger;
import com.codemanufaktur.robbiebluetooth.sendAutomat;

import org.tensorflow.lite.examples.detection.tflite.Detector;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MultiBoxTracker {
    private static final float TEXT_SIZE_DIP = 18;
    private static final float MIN_SIZE = 16.0f;
    private static final int[] COLORS = {
            Color.BLUE,
            Color.RED,
            Color.GREEN,
            Color.YELLOW,
            Color.CYAN,
            Color.MAGENTA,
            Color.WHITE,
            Color.parseColor("#55FF55"),
            Color.parseColor("#FFA500"),
            Color.parseColor("#FF8888"),
            Color.parseColor("#AAAAFF"),
            Color.parseColor("#FFFFAA"),
            Color.parseColor("#55AAAA"),
            Color.parseColor("#AA33AA"),
            Color.parseColor("#0D0068")
    };
    final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();
    private final Logger logger = new Logger();
    private final Queue<Integer> availableColors = new LinkedList<Integer>();
    private final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();
    private final Paint boxPaint = new Paint();
    private final float textSizePx;
    private final BorderedText borderedText;
    private Matrix frameToCanvasMatrix;
    private int frameWidth;
    private int frameHeight;
    private int sensorOrientation;
    public String commandMessage = "";
    public int maxHeight = 0;
    public int maxWidth = 0;

    public MultiBoxTracker(final Context context) {
        for (final int color : COLORS) {
            availableColors.add(color);
        }

        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(10.0f);
        boxPaint.setStrokeCap(Paint.Cap.ROUND);
        boxPaint.setStrokeJoin(Paint.Join.ROUND);
        boxPaint.setStrokeMiter(100);

        textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
    }

    public synchronized void setFrameConfiguration(
            final int width, final int height, final int sensorOrientation) {
        frameWidth = width;
        frameHeight = height;
        this.sensorOrientation = sensorOrientation;
    }

    public synchronized void drawDebug(final Canvas canvas) {
        final Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(60.0f);

        final Paint boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setAlpha(200);
        boxPaint.setStyle(Paint.Style.STROKE);

        for (final Pair<Float, RectF> detection : screenRects) {
            final RectF rect = detection.second;
            canvas.drawRect(rect, boxPaint);
            canvas.drawText("" + detection.first, rect.left, rect.top, textPaint);
            borderedText.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first);
        }
    }

    public synchronized void trackResults(final List<Detector.Recognition> results, final long timestamp) {
        logger.i("Processing %d results from %d", results.size(), timestamp);
        processResults(results);
    }

    private Matrix getFrameToCanvasMatrix() {
        return frameToCanvasMatrix;
    }

    public synchronized void draw(final Canvas canvas) {
        final boolean rotated = sensorOrientation % 180 == 90;
        final float multiplier =
                Math.min(
                        canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                        canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
        maxHeight = canvas.getHeight();
        maxWidth = canvas.getWidth();
        frameToCanvasMatrix =
                ImageUtils.getTransformationMatrix(
                        frameWidth,
                        frameHeight,
                        (int) (multiplier * (rotated ? frameHeight : frameWidth)),
                        (int) (multiplier * (rotated ? frameWidth : frameHeight)),
                        sensorOrientation,
                        false);
        for (final TrackedRecognition recognition : trackedObjects) {
            final RectF trackedPos = new RectF(recognition.location);

            getFrameToCanvasMatrix().mapRect(trackedPos);
            boxPaint.setColor(recognition.color);

            float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
            canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);

            final String labelString =
                    !TextUtils.isEmpty(recognition.title)
                            ? String.format("%s %.2f", recognition.title, (100 * recognition.detectionConfidence))
                            : String.format("%.2f", (100 * recognition.detectionConfidence));
            //            borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.top,
            // labelString);
            borderedText.drawText(
                    canvas, trackedPos.left + cornerSize, trackedPos.top, labelString + "%", boxPaint);
        }
    }

    private void processResults(final List<Detector.Recognition> results) {
        final List<Pair<Float, Detector.Recognition>> rectsToTrack = new LinkedList<Pair<Float, Detector.Recognition>>();

        screenRects.clear();
        final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

        for (final Detector.Recognition result : results) {
            if (result.getLocation() == null) {
                continue;
            }
            final RectF detectionFrameRect = new RectF(result.getLocation());

            final RectF detectionScreenRect = new RectF();
            rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);
            Log.d("Rect coordinates Track", "DetectionFrameRect: " + detectionFrameRect + " DetectionRect: " + detectionScreenRect);
            logger.v(
                    "Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect);
            screenRects.add(new Pair<Float, RectF>(result.getConfidence(), detectionScreenRect));

            if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
                logger.w("Degenerate rectangle! " + detectionFrameRect);
                continue;
            }

            rectsToTrack.add(new Pair<Float, Detector.Recognition>(result.getConfidence(), result));
        }

        trackedObjects.clear();
        if (rectsToTrack.isEmpty()) {
            logger.v("Nothing to track, aborting.");
            commandMessage = "Left turn";

            return;
        }

        for (final Pair<Float, Detector.Recognition> potential : rectsToTrack) {
            final TrackedRecognition trackedRecognition = new TrackedRecognition();
            trackedRecognition.detectionConfidence = potential.first;
            trackedRecognition.location = new RectF(potential.second.getLocation());
            trackedRecognition.title = potential.second.getTitle();
            trackedRecognition.color = COLORS[trackedObjects.size()];
            trackedObjects.add(trackedRecognition);
            Log.d("trackedObjects", "trackedObjects title: " + trackedRecognition.title + " trackedObjects location: " + trackedRecognition.location);

            if (trackedObjects.size() >= COLORS.length) {
                break;
            }
        }

        for (TrackedRecognition recogElement : trackedObjects) {
            Log.d("Maxwidth", "maxwidth: " + maxWidth + " maxHeight: " + maxHeight);
            if(recogElement.title.equals("Door")) {
                final RectF detectionScreenRectNav = new RectF();
                rgbFrameToScreen.mapRect(detectionScreenRectNav, recogElement.location);
                Log.d("Screen width", "Screen width: " + detectionScreenRectNav.width());
                if(detectionScreenRectNav.width() < (maxWidth * 0.6)) {
                    commandMessage = "Forward";
                } else if(detectionScreenRectNav.width() > (maxWidth * 0.6)) {
                    commandMessage = "Stop";
                }
                if ((detectionScreenRectNav.centerX() < (maxWidth / 4)) & (detectionScreenRectNav.width() < (maxWidth * 0.5))) {
                    commandMessage = "Left turn";
                } else if (detectionScreenRectNav.centerX() > (maxWidth * 0.75) & (detectionScreenRectNav.width() < (maxWidth * 0.5))) {
                    commandMessage = "Right turn";
                }
                //For horizontal orientation with frame:
                /*
                if(recogElement.location.width() < (frameWidth * 0.3)) {
                    commandMessage = "Forward";
                } else if(recogElement.location.width() > (frameWidth * 0.3)) {
                    commandMessage = "Stop";
                }*/
            }
            if(recogElement.title.equals("banana")) {
                final RectF detectionScreenRectNav = new RectF();
                rgbFrameToScreen.mapRect(detectionScreenRectNav, recogElement.location);
                if ((detectionScreenRectNav.centerX() < (maxWidth / 2)) & (detectionScreenRectNav.width() < (maxWidth * 0.5))) {
                    commandMessage = "Right turn";
                } else if (detectionScreenRectNav.centerX() > (maxWidth / 2) & (detectionScreenRectNav.width() < (maxWidth * 0.5))) {
                    commandMessage = "Left turn";
                } else if (detectionScreenRectNav.width() > (maxWidth * 0.5)) {
                    commandMessage = "Stop";
                }
                //For horizontal orientation with frame:
                /*
                if ((recogElement.location.centerX() < (frameWidth / 2)) & (recogElement.location.width() < (frameWidth * 0.3))) {
                    commandMessage = "Right turn";
                } else if (recogElement.location.centerX() > (frameWidth / 2) & (recogElement.location.width() < (frameWidth * 0.3))) {
                    commandMessage = "Left turn";
                } else if (recogElement.location.width() > (frameWidth * 0.3)) {
                    commandMessage = "Stop";
                }*/
            }
        }
    }

    private static class TrackedRecognition {
        RectF location;
        float detectionConfidence;
        int color;
        String title;
    }
}
