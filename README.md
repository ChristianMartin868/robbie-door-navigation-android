# Robbie – Door Navigation with a Single Phone Camera (Android)

Android app from my master's thesis *"Autonomous operation of a mobile robot using computer vision –
machine learning"* (Technische Hochschule Nürnberg, Dec 2021, industry partner: codemanufaktur GmbH).

A mid-range Android phone is mounted on a small Arduino robot. Using only its camera, the phone

1. **detects a door** with an SSD MobileNet V2 FPN-Lite 320 model (TensorFlow Lite, int8, ~50 ms),
2. **estimates depth** from the same frame with MiDaS v2.1 small (~160–180 ms on GPU),
3. turns both into navigation signals, runs a **state machine every 100 ms**, and
4. sends drive commands to the robot via **Bluetooth**.

Training pipeline for the door detectors: [`robbie-door-detection-training`](https://github.com/ChristianMartin868/robbie-door-detection-training).

```
Camera frame ──► Door detector (SSD, TFLite) ──► DoorSignal ─────┐
             └─► Depth (MiDaS, TFLite)       ──► ObstacleSignal ─┤
                                                                  ▼
                               NavigationStateMachine (every 100 ms)
                                                                  ▼
                               DriveCommand ──► Bluetooth ──► Arduino Mega
```

## Where to look

| What | File |
|---|---|
| Frame processing, detection + depth inference, obstacle heuristic | `app/.../MainActivity.java` → `processImage()` |
| State machine loop (100 ms) | `app/.../MainActivity.java` → `loop()` |
| Door position → navigation signal | `app/.../tracking/MultiBoxTracker.java` → `processResults()` |
| MiDaS integration (TFLite Interpreter, CPU/GPU/NNAPI delegates) | `lib_interpreter/.../DepthEstimator*.java` |
| **Refactored, unit-tested navigation logic** (2026) | `app/.../navigation/` + `app/src/test/.../navigation/` |

## History

* **`thesis-2021`** (tag) – the app exactly as used for the thesis evaluation.
* Commits after the tag – review fixes from 2026:
  * obstacle heuristic summed with `=+` instead of `+=` (left/right decision was based on a single pixel),
  * navigation logic extracted into plain-Java classes with enums instead of `String ==` comparisons,
    explicit state transitions, and JUnit tests,
  * signals shared between the inference thread and the UI-thread loop are now `volatile`.

## Building

Open `RobbieBluetooth/` in Android Studio (product flavours `taskApi` / `interpreter`).
Run the unit tests with `./gradlew :app:testTaskApiDebugUnitTest`.

Model files larger than 15 MB were removed from the history before publishing (GitHub size limits).
The MiDaS model (`model_opt.tflite`, MiDaS v2.1 small) has to be placed in `app/src/main/assets/` – see the
[MiDaS mobile example](https://github.com/isl-org/MiDaS/tree/master/mobile/android).

## Credits

* App skeleton (camera handling, `MultiBoxTracker`, `TFLiteObjectDetectionAPIModel`, custom views):
  [TensorFlow Lite object detection example](https://github.com/tensorflow/examples), Apache License 2.0.
* Bluetooth remote-control app this project started from (robot command protocol in `sendAutomat`,
  Bluetooth connection handling): colleagues at codemanufaktur (2019).
* Depth model: [MiDaS](https://github.com/isl-org/MiDaS) (MIT).
* Door navigation, depth-based obstacle avoidance, state machine, model integration and evaluation: Christian Martin.
