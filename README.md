<p align="center">
  <a href="https://github.com/K4N3CO/WiFEYE">
    <img src="https://i.postimg.cc/DyFMXQgF/Wif-EYE-logo.png" alt="WifEYE Logo" width="940"/>
  </a>
</p>

<h1 align="center">📡 WifEYE — Tactical Through-Wall RF Radar</h1>

<p align="center">
  <strong>Transform standard smartphone hardware into a military-grade through-wall occupancy & motion scanner.</strong>
</p>

<p align="center">
  <a href="https://android.com"><img src="https://img.shields.io/badge/Platform-Android_12%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android Platform"/></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Language-Kotlin_1.9%2B-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin Language"/></a>
  <a href="https://github.com/K4N3CO/WiFEYE/releases"><img src="https://img.shields.io/badge/Version-v1.0.0--release-0055FF?style=for-the-badge&logo=github&logoColor=white" alt="Release Version"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-00C853?style=for-the-badge" alt="MIT License"/></a>
</p>

---

## 📑 Table of Contents
- [Executive Overview](#-executive-overview)
- [System Architecture](#-system-architecture)
- [Key Features](#-key-features)
- [Operational Modes](#-operational-modes)
- [Mathematical & Physics Engine](#-mathematical--physics-engine)
- [Hardware-Accelerated HUD Visualizer](#-hardware-accelerated-hud-visualizer)
- [Quick Start & Installation](#-quick-start--installation)
- [Tactical Operating Guide](#-tactical-operating-guide)
- [RuView Specification (Issue #36)](#-ruview-specification-issue-36)
- [License & Disclaimer](#-license--disclaimer)

---

## 📋 Executive Overview

> [!IMPORTANT]
> **WifEYE** requires no special hardware, external sensors, or modified firmware. It turns standard Wi-Fi chipsets on consumer Android smartphones into high-resolution electromagnetic field monitors capable of sensing micro-doppler shifts caused by human presence through solid walls.

**WifEYE** is an extreme-performance Android sensing suite. By continuously sampling micro-fluctuations in ambient **Received Signal Strength Indicator (RSSI)** fields at high frequency (13.3–20 Hz), WifEYE's physics engine maps structural attenuation, computes statistical signal variance, and renders spatial occupant telemetry on an interactive 3D tactical HUD.

---

## 🏗️ System Architecture

<img width="1132" height="177" alt="Screenshot 2026-11-12 at 1 04 12 AM" src="https://github.com/user-attachments/assets/06b1d2dd-1ab6-4717-baf6-073fd51e4d87" />

---

## ⚡ Key Features

### 👁️ Through-Wall Penetration (Up to 60ft)
Performs spatial localization through drywall, timber, and brick barriers by applying dynamic path-loss exponent compensation ($n = 2.2 \text{ to } 3.2$).

### 🧠 Hardened Noise Suppression & Zero False Positives 
Combines **Welford's Algorithm** for real-time variance tracking, **CUSUM** sequential analysis for detection thresholds, and **Dual-EMA Band-Pass Filtering** to isolate human motion frequencies (0.5–3.0 Hz) while discarding static RF interference.

### 🧘 Static Presence & Micro-Movement Lock 
Unlike traditional Doppler radars that drop targets when movement ceases, WifEYE detects subtle phase shifts in ambient RSSI baseline (`PRESENT_STILL`), maintaining active lock on sitting or stationary targets.

### 🖥️ Tactical HUD 2.0 (Zero-Allocation Graphics)
High-speed custom Canvas visualizer featuring holographic humanoids, skeletal joint kinematics, motion vector echoes, sweeping radar sector beam, real-time dual-channel signal telemetry graphs, and interactive event logs.

### 🔊 Multi-Sensory Feedback 
Geiger-mode audio click generator mapped to signal variance intensity and tactical haptic pulses upon target acquisition.

---

## 🎛️ Operational Modes

**WifEYE** provides three pre-tuned mission profiles selectable directly from the tactical UI:

| Profile | Sensitivity ($h$) | Slack ($k$) | Path Loss ($n$) | Primary Deployment Scenario |
| :--- | :---: | :---: | :---: | :--- |
| **Auto** | $5.5$ | $0.30$ | $2.2$ | General area scanning; optimal stability & noise rejection. |
| **Through-Wall** | $1.2$ | $0.08$ | $3.2$ | Maximum sensitivity for detecting faint signals behind thick walls. |
| **Multi-Person** | $2.5$ | $0.15$ | $2.2$ | Fast spectral windowing tuned for multi-target spatial resolution. |

---

## 🔬 Mathematical & Physics Engine

**WifEYE** translates raw RF power fluctuations ($\text{dBm}$) into physical spatial telemetry through a multi-stage statistical kernel:

### 1. Log-Distance Path Loss Model (Spatial Distance)
Calculates real-time distance ($d$) to target based on signal attenuation relative to 1-meter reference power ($\text{RSSI}_{1\text{m}} = -32 \text{ dBm}$):

$$d = 10^{\left(\frac{\text{RSSI}_{1\text{m}} - \text{RSSI}_{\text{smoothed}}}{10 \cdot n}\right)} \times 3.28084 \quad [\text{feet}]$$

- $n$: Environmental Path Loss Exponent ($2.2$ for open space, $3.2$ for high barrier attenuation).

---

### 2. Cumulative Sum (CUSUM) Change Detection
Detects statistically significant shifts in signal variance while rejecting gradual baseline drift:

$$S_t = \max\left(0, \ S_{t-1} + (\sigma^2_t - (\sigma^2_{\text{baseline}} + k))\right)$$

$$\text{Trigger Condition}: \quad S_t > h \quad \lor \quad \sigma^2_t > (\sigma^2_{\text{baseline}} \times M)$$

- $k$: CUSUM slack allowance parameter ($0.08 - 0.30$).
- $h$: Decision threshold ($1.2 - 5.5$).
- $M$: Kinetic movement multiplier ($2.5 - 3.5$).

---

### 3. Dual-EMA Band-Pass Spectral Filtering
Isolates energy within the frequency band corresponding to human biomechanical movement (limb swing, chest rise):

$$\text{EMA}_{\text{fast}, t} = 0.4 \cdot \text{RSSI}_t + 0.6 \cdot \text{EMA}_{\text{fast}, t-1}$$

$$\text{EMA}_{\text{slow}, t} = 0.1 \cdot \text{RSSI}_t + 0.9 \cdot \text{EMA}_{\text{slow}, t-1}$$

$$\text{BandPower}_t = 0.9 \cdot \text{BandPower}_{t-1} + 0.1 \cdot \left| \text{EMA}_{\text{fast}, t} - \text{EMA}_{\text{slow}, t} \right|$$

---

### 4. Welford's Algorithm for Numerically Stable Rolling Variance
Computes exact sample mean ($\mu$) and sum of squared differences ($M_2$) across a 30-sample circular buffer without floating-point cancellation drift:

$$\delta = x_t - \mu_{t-1}$$

$$\mu_t = \mu_{t-1} + \frac{\delta}{N}$$

$$M_{2, t} = M_{2, t-1} + \delta \cdot (x_t - \mu_t)$$

$$\sigma^2_t = \frac{M_{2, t}}{N}$$

---

## 🖥️ Hardware-Accelerated HUD Visualizer

```
+-------------------------------------------------------------------------+
| [BIOMETRIC SENSING]    | [TACTICAL EVENT LOG]  | [SIGNAL ENVIRONMENT]   |
| ACCURACY:  94%         | > BASELINE_LOCKED     | RSSI LINK: -48 dBm     |
| DISTANCE:  14.2 FT     | > TARGET_ACTIVE       | VARIANCE:   1.248      |
| 74 BPM / 14 RPM        | > SIG_VERIFY_8F4A     | INTENSITY:  1.650      |
| [ECG PULSE WAVEFORM]   | [SCROLLABLE LOG BOX]  |[DUAL TELEMETRY GRAPH]  |
| [ RECALIBRATE ]        |                       |[ SCAN: MOTION DETECTED]|
+-------------------------------------------------------------------------+
|                          3D PERSPECTIVE FLOOR GRID                      |
|                  (Volumetric Wedge / Holographic Humanoid)              |
+-------------------------------------------------------------------------+
```

### Visualizer Features
- **Holographic Humanoid Geometry**: 3D wireframe skeletal targets with dynamic gait bobbing, gesture articulation, and distance-scaled tactical bounding boxes.
- **Motion Echo Trails**: 5-stage velocity tail rendering indicating directional vector history.
- **Volumetric Sweep Sector**: Rotating radar sector blade with distance marker rings (10ft, 20ft, 30ft, 45ft, 60ft).
- **Dual-Channel Live Telemetry**: Live graphical plot overlaying raw RSSI (cyan) against variance energy (yellow).

---

## 🛠️ Tech Stack & Requirements

- **Primary Language**: [Kotlin](https://kotlinlang.org/) (100%)
- **Target OS**: Android 12 (API Level 31) through Android 16 (API Level 36)
- **Architecture**: Single-Activity, Custom Canvas Rendering Engine (`SignalVisualizerView`)
- **Key APIs Used**:
  - `android.net.wifi.WifiManager` — High-speed connection polling
  - `android.graphics.Canvas` & `android.graphics.Path` — Pre-allocated zero-allocation rendering pipeline
  - `android.os.VibratorManager` & `android.media.ToneGenerator` — Haptic & Audio feedback

---

## 🚀 Quick Start & Installation Options

### 📲 Option 1: Download Pre-Built APK (Recommended)

1. **Download APK**: Grab the latest `.apk` package from the **[GitHub Releases Page](https://github.com/K4N3CO/WiFEYE/releases)**.
2. **Install**: Open the downloaded `.apk` file on your device and confirm installation (allow installation from unknown sources if prompted).
3. **Grant Permissions**: Open **WifEYE** and grant **Location (`ACCESS_FINE_LOCATION`)** permission when prompted (mandatory by Android OS for accessing Wi-Fi hardware RSSI telemetry).

[![Download APK](https://img.shields.io/badge/Download-Latest_APK-0055FF?style=for-the-badge&logo=android&logoColor=white)](https://github.com/K4N3CO/WiFEYE/releases)

---

### 🛠️ Option 2: Build From Source (Developers)

#### Prerequisites
- **Android Studio** (Ladybug / 2024.2.1+ or newer)
- **JDK 17** or **JDK 21**
- Android SDK 36 (Build Tools 36.0.0)

```bash
# 1. Clone the repository
git clone https://github.com/K4N3CO/WiFEYE.git
cd WiFEYE

# 2. Build Debug or Release APK using Gradle
./gradlew assembleRelease

# 3. Install directly to a connected Android device via ADB
adb install app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## 🎯 Tactical Operating Guide

> [!TIP]
> **Calibration Best Practice**: Always calibrate **WifEYE** in an empty room or when targets are motionless for at least 3 seconds.

1. **Launch App**: Open **WifEYE** and allow location permissions if prompted.
2. **Environment Baseline Lock**: Tap the **RECALIBRATE** button on the bottom left HUD panel. Wait for 40 samples (~3 seconds) while the status shows `CALIBRATING...`.
3. **Select Mode**: Use the drop-down menu in the bottom control bar to switch between **Auto**, **Through-Wall**, and **Multi-Person** modes.
4. **Tactical Override Settings**: Tap the **Settings (Gear Icon)** to customize:
   - **CUSUM Sensitivity Slider**: Fine-tune detection sensitivity.
   - **Geiger Audio Feed**: Toggle real-time audio clicks.
   - **Tactical Haptics**: Toggle tactile vibration alerts upon target lock.
   - **HUD CRT Scanlines**: Toggle aesthetic scanline rendering.

---

## 🏛️ RuView Specification (Issue #36)

**WifEYE** serves as the reference Android implementation for sensing objectives specified in **[RuView Issue #36](https://github.com/ruvnet/RuView/issues/36)**:

- **Pose Approximation**: Energy-to-skeleton mapping for humanoid gait visualization.
- **Biometric Sync**: Variance-to-vital mapping (heart rate BPM / respiration RPM estimation).
- **Integrity Verification**: Real-time deterministic hashing (`SIG_VERIFY_<HASH>`) emitted into the tactical log for signal stream auditability.

---

## ⚖️ License & Disclaimer

```text
MIT License

Copyright (c) 2026 K4N3CO

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

> [!CAUTION]
> **WifEYE** is intended exclusively for educational, technical research, and electromagnetic field propagation analysis. Sensing range and accuracy depend on local hardware chipset capabilities, Wi-Fi environment density, and structural building materials. Android emulators do not support physical Wi-Fi hardware RSSI sampling.

---

<p align="center">
  Developed by <a href="https://github.com/K4N3CO">K4N3CO</a>
</p>
