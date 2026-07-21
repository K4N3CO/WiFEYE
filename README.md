<p align="center">
[![Wif-EYE-logo.png](https://i.postimg.cc/DyFMXQgF/Wif-EYE-logo.png)](https://postimg.cc/jCgcV7mk)

# 📡 WifEYE
> **Tactical Wi-Fi Sensing & Through-Wall Occupancy Detection**

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

**WifEYE** is a high-fidelity Wi-Fi sensing application that transforms standard smartphone hardware into a tactical occupancy scanner. By analyzing micro-fluctuations in the electromagnetic field (RSSI Variance), WifEYE can detect human presence, movement, and even stationary biometrics—even through solid walls.

---

## ⚡ Core Features

### 👁️ Through-Wall Vision
Utilizes a specialized physics model with an adjusted Path Loss Exponent ($n=3.2$) to compensate for material density, allowing for the detection of individuals in adjacent rooms.

### 🧠 CUSUM Signal Processing
Implements **Cumulative Sum (CUSUM)** change-point detection algorithms to filter out environmental noise. This ensures the scanner only triggers on sustained human activity, preventing "ghost" detections.

### 🧘 Still Presence Detection
Beyond simple motion, WifEYE monitors **Baseline RSSI Shifts**. If a person enters a room and sits perfectly still, the system identifies the shift in the multi-path environment and maintains a "PRESENT_STILL" state.

### 🖥️ Tactical HUD Visualization
A custom-engineered Canvas-based visualizer featuring:
* **Micro-Motion Jitter**: Skeletons vibrate/pulse based on limb-movement intensity.
* **Biometric Estimation**: Simulated heart rate and respiration tracking based on signal fidelity.
* **Depth Sorting**: Real-time rendering of occupants relative to the Wi-Fi router.

---

## 🎛️ Operational Modes

* **Auto**: The default balanced mode for general occupancy detection.
* **Through-Wall**: High-sensitivity mode with adjusted $n=3.2$ path-loss compensation for detecting presence through solid obstructions.
* **Empty Room**: Strict baseline enforcement for security and area clearing scenarios.
* **Vital Signs**: Focused analysis on high-frequency micro-fluctuations corresponding to human biometrics.
* **Multi-Person**: Optimized windowing for distinguishing between multiple distinct signal reflectors.
* **Gesture Ctrl**: High-frequency variance tracking to detect limb-based movement and gesturing.

---

## 🛠️ How it Works

The system operates on three primary signal layers:

1. **The Raw Layer**: Continuous polling of Wi-Fi RSSI via `WifiManager`.
2. **The Processing Layer**: 
    * **EMA Smoothing**: Exponential Moving Averages remove high-frequency hardware jitter.
    * **Variance Analysis**: Statistical windowing identifies the "energy" in the room.
    * **CUSUM Filter**: An accumulator that triggers state transitions only when confidence is high.
3. **The Presentation Layer**: The `SignalVisualizerView` maps these data points to a 3D-perspective tactical floor plan.

---

## 🚀 Getting Started

### Quick Install
1. **Download**: Grab the latest `.apk` from the [Releases](https://github.com/youruser/WifEYE/releases) section.
2. **Install**: Sideload onto your Android device (requires "Install from Unknown Sources").
3. **Calibrate**: Tap **RECALIBRATE** in an empty room for 5 seconds to establish the EM baseline.

### Developer Setup
1. **Clone**: `git clone https://github.com/youruser/WifEYE.git`
2. **Build**: Open in Android Studio (Iguana or later).
3. **Run**: Deploy to a physical device (Emulators lack Wi-Fi hardware access).

---

## ⚖️ Disclaimer

*WifEYE is intended for research and educational purposes. Wi-Fi sensing capabilities are limited by the polling rate and resolution of standard Android hardware. Environmental factors like heavy metal shielding or 2.4GHz interference may affect accuracy.*

---
*Developed as part of the RuView research initiative.*
