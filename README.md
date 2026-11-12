<p align="center">
  <a href="https://postimg.cc/jCgcV7mk">
    <img src="https://i.postimg.cc/DyFMXQgF/Wif-EYE-logo.png" alt="WifEYE Logo" width="600"/>
  </a>
</p>

<h1 align="center">📡 WifEYE (Extreme Performance Edition)</h1>

<p align="center">
  <strong>Turn standard smartphone hardware into a tactical through-wall occupancy and motion scanner.</strong>
</p>

<p align="center">
  <a href="https://android.com"><img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android" alt="Android Platform"/></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin" alt="Kotlin Language"/></a>
  <a href="https://opensource.org/licenses/MIT"><img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="MIT License"/></a>
  <a href="https://github.com/K4N3CO-LABS/WifEYE/releases"><img src="https://img.shields.io/badge/Release-v1.0.0--beta-blue?style=for-the-badge" alt="Version"/></a>
</p>

---

## 📋 Overview

**WifEYE** is a high-fidelity sensing suite that transforms commodity smartphone hardware into a tactical RF radar system. By processing micro-fluctuations in ambient Electromagnetic (EM) field propagation (Wi-Fi Received Signal Strength Indicator / RSSI), WifEYE enables real-time through-wall occupant detection, spatial tracking, and biological presence estimation—without dedicated radar hardware.

---

## ⚡ Core Capabilities

- 👁️ **Through-Wall Vision (60ft Effective Range)**  
  Detect occupancy through physical obstacles (drywall, wood, brick). An integrated log-distance path loss model ($n = 2.2 \text{ to } 3.2$) compensates for barrier attenuation to accurately localize targets on a 3D floor grid.

- 🧠 **Hardened Signal Processing Kernel**  
  Combines **Welford's Algorithm** for real-time rolling statistics with a **High-Confidence Cumulative Sum (CUSUM)** change-detection algorithm ($k = 0.30, h = 5.5$) and **Dual-EMA Band-Pass Spectral Filtering** to eliminate ambient signal drift and eliminate false positives.

- 🧘 **Stationary & Motion Presence Tracking**  
  Unlike conventional Doppler sensors that lose targets when movement stops, WifEYE monitors baseline field shifts (`PRESENT_STILL` state) to maintain active lock on static occupants.

- 🖥️ **Tactical Visualizer HUD 2.0**  
  A zero-allocation, hardware-accelerated custom Canvas engine delivering high-FPS rendering:
  - **Holographic Humanoids**: Skeletal target geometry with articulated joints, azimuth alignment, and gesture states.
  - **Motion Echo Vectors**: Dynamic position history trails depicting target speed and direction.
  - **Atmospheric Horizon Fading**: Volumetric depth projection with perspective grid warping.
  - **Scanning Blade**: Real-time azimuth tracking beam with high-contrast sweep indication.
  - **Interactive Tactical Terminal**: Scrollable real-time event log with deterministic verification hashes (`SIG_VERIFY`).

---

## 🎛️ Operational Modes

| Mode | Target Sensitivity | Path Loss ($n$) | Primary Use Case |
| :--- | :--- | :--- | :--- |
| **Auto** | Balanced baseline | $2.2$ | General area monitoring with maximum false-positive suppression. |
| **Through-Wall** | Max CUSUM sensitivity | $3.2$ | High-attenuation environments behind dense physical barriers. |
| **Multi-Person** | Fast EMA resolution | $2.2$ | Multi-occupant spatial separation and independent tracking (up to 4 slots). |

---

## 🔬 Mathematical & Physics Engine

WifEYE translates RF propagation metrics into spatial telemetry using heuristic statistical modeling:

1. **Log-Distance Path Loss Distance Estimation**:
   $$d = 10^{\frac{\text{RSSI}_{\text{1m}} - \text{RSSI}_{\text{smoothed}}}{10 \cdot n}} \times 3.28084 \quad [\text{feet}]$$

2. **CUSUM Statistic Update**:
   $$S_t = \max\left(0, S_{t-1} + (\sigma^2_t - (\sigma^2_{\text{baseline}} + k))\right)$$

3. **Dual-EMA Band-Pass Spectral Power**:
   $$\text{BandPower} = 0.9 \cdot \text{BandPower} + 0.1 \cdot |\text{EMA}_{\text{fast}} - \text{EMA}_{\text{slow}}|$$

4. **Welford's Algorithm (Rolling Variance)**:
   Computes exact sample mean ($\mu$) and sum of squared differences ($M_2$) across a 30-sample sliding window without floating-point drift or memory allocation overhead.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 1.9+
- **Min SDK**: API 31 (Android 12) | **Target SDK**: API 36 (Android 16)
- **UI Framework**: Android Jetpack (AppCompat, ConstraintLayout, Material Components)
- **Graphics Engine**: Custom Hardware-Accelerated `View` (`SignalVisualizerView`) using zero-allocation `Canvas` paths and pre-calculated trigonometric lookup tables.
- **System Services**: `WifiManager` (20Hz high-frequency hardware polling loop), `VibratorManager` / `Vibrator` (tactical haptics), `ToneGenerator` (Geiger audio feedback).

---

## 🚀 Getting Started

### Prerequisites

- An Android device running **Android 12 (API level 31)** or higher.
- Active Wi-Fi interface connected to an Access Point (or scanning ambient RF fields).
- Location permission granted (`ACCESS_FINE_LOCATION`) as mandated by the Android OS for Wi-Fi RSSI telemetry access.

### Installation

1. Download the latest release `.apk` from the [Releases](https://github.com/K4N3CO-LABS/WifEYE/releases) page.
2. Sideload and install the `.apk` on your target Android device.
3. Launch **WifEYE** and grant the required Location permission when prompted.

### Operating Procedure

1. **Calibration**: Upon launch or environment shift, place the device in a static location and tap **RECALIBRATE**. Allow 40 samples (~3 seconds) to lock the background EM baseline.
2. **Monitoring**: Select your desired operational mode (**Auto**, **Through-Wall**, or **Multi-Person**) using the HUD mode selector.
3. **Tactical Overrides**: Tap the gear icon to adjust CUSUM sensitivity, toggle the Geiger audio feed, tactile haptics, or CRT scanlines.

---

## 🏛️ Reference Implementation

WifEYE implements the tactical sensing specification outlined in [RuView Issue #36](https://github.com/ruvnet/RuView/issues/36), incorporating Pose Approximation, Biological Pulse Modulation, and `SIG_VERIFY` signal integrity verification hashing.

---

## ⚖️ Disclaimer

*WifEYE is developed for technical research, educational demonstration, and RF propagation analysis. Performance is dependent on local electromagnetic interference, device Wi-Fi chipset sensitivity, and structural barrier composition. Virtual Android emulators do not support physical Wi-Fi hardware polling.*

---

<p align="center">
  Licensed under the <a href="LICENSE">MIT License</a>. Developed by <a href="https://github.com/K4N3CO-LABS">K4N3CO-LABS</a>.
</p>
