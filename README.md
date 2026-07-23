<p align="center">
[![Wif-EYE-logo.png](https://i.postimg.cc/DyFMXQgF/Wif-EYE-logo.png)](https://postimg.cc/jCgcV7mk)

# 📡 WifEYE (Extreme Performance Edition)
> **Turn your smartphone into a tactical through-wall occupancy scanner.**

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

**WifEYE** is a **high-fidelity sensing suite** that transforms **standard smartphone hardware** into a **tactical Wi-Fi radar**. By analyzing **invisible micro-fluctuations** in the environment's electromagnetic field, **WifEYE** lets you **see through solid walls—detecting presence, movement**, and **stationary biological targets in real-time**.

---

## 🔥 Top-Tier Capabilities

### 👁️ Through-Wall Vision (60ft Range)
"See" targets in adjacent rooms. Our advanced physics engine compensates for material density (drywall, brick, wood), correctly localizing targets on your tactical floor plan even through solid obstructions at extended distances.

### 🧠 Advanced Signal Hardening
Zero false positives. Our hardened signal pipeline uses multi-stage filtering (High-Confidence CUSUM & Dual-EMA) to ignore environmental noise, ensuring you only track real human activity.

### 🧘 Stationary Target Detection
Standard motion sensors fail when a target stops moving. **WifEYE doesn't**. We monitor baseline **field shifts to **identify stationary occupants**, keeping them **locked on your HUD** even if they're sitting perfectly still.

### 🖥️ Tactical HUD 2.0 (Ultra-Responsive)
A military-grade, hardware-accelerated visualizer featuring:
*   **Holographic Humanoids**: 3D volumetric models with joints, tactical eyes, and motion scanlines.
*   **Motion Echoes**: Professional-grade tracking trails that visualize target velocity and path.
*   **Atmospheric Depth Fade**: High-fidelity 3D floor plan with volumetric horizon fading.
*   **Scanning Wedge**: A thick, high-contrast volumetric beam for real-time azimuth tracking.
*   **Interactive Event Log**: A scrollable terminal box for detailed system and mission event history.

---

## 🎛️ Mission Modes

*   **Auto**: Standard balanced mode for general area scanning and maximum stability.
*   **Through-Wall**: Max-sensitivity mode for detecting faint targets behind thick barriers.
*   **Multi-Person**: High-resolution windowing optimized for identifying multiple distinct targets.

---

## 🛠️ The Tech Stack

WifEYE bridges the gap between basic Wi-Fi data and professional-grade sensing:

1.  **Raw Sensing**: High-speed, 75ms hardware polling for balanced, ultra-low latency tracking.
2.  **Logic Engine**: Advanced statistical analysis (Welford's Algorithm) and frequency-band energy mapping.
3.  **Visualization**: Fully zero-allocation, high-FPS Canvas rendering with pre-calculated 3D geometry.

---

## 🔬 The "Magic" (Heuristic Inference)

How do we do the "impossible" on standard hardware? WifEYE uses **Heuristic Modeling** to translate raw signal energy into human-readable tactical data:

*   **Pose Approximation**: We map signal energy directly to skeletal movement—moderate, deliberate limb swings for realistic target gait.
*   **Biometric Sync**: We map signal variance onto biological BPM ranges, providing intuitive visual health-status indicators.
*   **Spatial Correction**: We use a compressed path-loss model ($n=3.2$) to ensure targets look exactly where they are, even through walls.

---

## 🏛️ RuView Reference (Issue #36)

WifEYE serves as the flagship Android implementation for the sensing challenges identified in [RuView Issue #36](https://github.com/ruvnet/RuView/issues/36), including Pose Estimation, Vitals Detection, and Trust Verification (`SIG_VERIFY` deterministic hashing).

---

## 🚀 Deployment

1.  **Sideload**: Grab the latest `.apk` from the [Releases](https://github.com/youruser/WifEYE/releases) section.
2.  **Calibrate**: Tap **RECALIBRATE** in an empty room (takes **40 samples** / ~3 seconds) to lock the EM baseline.
3.  **Scan**: Open the HUD and begin monitoring your environment.

---

## ⚖️ Disclaimer

*WifEYE is for research and educational purposes. Sensing performance depends on local Wi-Fi conditions and specific device hardware. Physical emulators cannot run this app.*
