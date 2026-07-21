package com.example.wifeye

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/**
 * WifEYE MainActivity - Core Signal Processing Engine
 * Extreme Performance Edition (V4 - Full System Hardening)
 */
class MainActivity : AppCompatActivity() {
    enum class RoomState { ABSENT, PRESENT_STILL, ACTIVE }
    
    // UI Components
    private lateinit var wifiManager: WifiManager
    private lateinit var statusText: TextView
    private lateinit var azimuthText: TextView
    private lateinit var rssiText: TextView
    private lateinit var movementText: TextView
    private lateinit var visualizer: SignalVisualizerView
    private lateinit var modeSpinner: Spinner
    private lateinit var settingsButton: View
    private lateinit var prefs: SharedPreferences
    
    private val handler = Handler(Looper.getMainLooper())
    
    // --- SIGNAL PROCESSING KERNEL ---
    private val windowSize = 30 
    private val rssiHistory = IntArray(windowSize)
    private var rssiPointer = 0
    private var rssiCount = 0
    
    private var baselineVariance = 0.5
    private var baselineRssi = -45.0
    private var isCalibrating = true
    private var calibrationCount = 0
    private var movementMultiplier = 3.5 
    
    private var smoothedRssi = -45.0
    private var smoothedVariance = 0.5 
    private var smoothedIntensity = 0f
    private var smoothedDistance = 0f
    private var emaAlpha = 0.15 
    
    // Spectral Analysis (Dual-EMA Band-Pass)
    private var fastEma = -45.0
    private var slowEma = -45.0
    private var bandPower = 0.0 

    private lateinit var toneGenerator: ToneGenerator
    private var lastClickTime = 0L
    private var ppsCounter = 0
    private var lastPpsUpdate = 0L
    private var currentPps = 0

    // Change Detection (CUSUM)
    private var cusumS = 0.0
    private var cusumSlack = 0.30 
    private var cusumThreshold = 5.5 
    private var pathLossExponent = 2.2
    private var currentState = RoomState.ABSENT
    private var currentModeStr = "Auto"
    private var lastMotionTime = System.currentTimeMillis()

    // Integrity & Persistence
    private var activeCounter = 0
    private var activeThreshold = 6 
    private var absentCounter = 0
    private var absentThreshold = 10 

    // Settings
    private var isAudioEnabled = true
    private var isHapticsEnabled = true
    private var isScanlineEnabled = true
    private var sensitivityLevel = 50

    // Statistical Engine (Welford's)
    private var rollingCount = 0
    private var rollingMean = 0.0
    private var rollingM2 = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Resource Mapping
        statusText = findViewById(R.id.statusText)
        azimuthText = findViewById(R.id.azimuthText)
        rssiText = findViewById(R.id.rssiText)
        movementText = findViewById(R.id.movementText)
        visualizer = findViewById(R.id.visualizer)
        modeSpinner = findViewById(R.id.modeSpinner)
        settingsButton = findViewById(R.id.settingsButton)
        
        // System Services
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        prefs = getSharedPreferences("wifeye_prefs", MODE_PRIVATE)
        loadSettings()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupModeSpinner()
        visualizer.onRecalibrateListener = { recalibrate() }
        settingsButton.setOnClickListener { showSettingsDialog() }
        checkPermissions()
        
        visualizer.addLogEvent("SYSTEM_INIT_SUCCESS")
        visualizer.addLogEvent("HARDWARE_POLLING_20HZ")
    }

    private fun loadSettings() {
        sensitivityLevel = prefs.getInt("sensitivity", 50)
        isAudioEnabled = prefs.getBoolean("audio", true)
        isHapticsEnabled = prefs.getBoolean("haptics", true)
        isScanlineEnabled = prefs.getBoolean("scanlines", true)
        visualizer.setScanlineEnabled(isScanlineEnabled)
        applyModeSettings(currentModeStr)
    }

    private fun saveSettings() {
        prefs.edit().apply {
            putInt("sensitivity", sensitivityLevel)
            putBoolean("audio", isAudioEnabled)
            putBoolean("haptics", isHapticsEnabled)
            putBoolean("scanlines", isScanlineEnabled)
            apply()
        }
    }

    private fun showSettingsDialog() {
        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .create()
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        dialog.setView(view)

        val sensitivitySeekBar = view.findViewById<android.widget.SeekBar>(R.id.sensitivitySeekBar)
        val audioToggle = view.findViewById<android.widget.CheckBox>(R.id.audioToggle)
        val hapticToggle = view.findViewById<android.widget.CheckBox>(R.id.hapticToggle)
        val scanlineToggle = view.findViewById<android.widget.CheckBox>(R.id.scanlineToggle)
        val closeButton = view.findViewById<Button>(R.id.closeSettingsButton)

        sensitivitySeekBar.progress = sensitivityLevel
        audioToggle.isChecked = isAudioEnabled
        hapticToggle.isChecked = isHapticsEnabled
        scanlineToggle.isChecked = isScanlineEnabled

        closeButton.setOnClickListener {
            sensitivityLevel = sensitivitySeekBar.progress
            isAudioEnabled = audioToggle.isChecked
            isHapticsEnabled = hapticToggle.isChecked
            isScanlineEnabled = scanlineToggle.isChecked
            applyModeSettings(currentModeStr)
            visualizer.setScanlineEnabled(isScanlineEnabled)
            saveSettings()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun setupModeSpinner() {
        val modes = arrayOf("Auto", "Through-Wall", "Multi-Person")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modeSpinner.adapter = adapter
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentModeStr = modes[position]
                applyModeSettings(currentModeStr)
                visualizer.setMode(currentModeStr)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applyModeSettings(mode: String) {
        val sensMod = (100 - sensitivityLevel) / 50.0 
        when(mode) {
            "Through-Wall" -> {
                // Optimized for highly attenuated signals (behind drywall/brick)
                cusumThreshold = 1.2 * sensMod
                cusumSlack = 0.08
                pathLossExponent = 3.2 // High compensation for material density
                emaAlpha = 0.40 // Aggressive reaction to tiny signal shifts
                movementMultiplier = 2.5
                activeThreshold = 4 // Quick trigger
                absentThreshold = 15 // High persistence (don't lose target through walls)
            }
            "Multi-Person" -> {
                // Optimized for high-resolution energy variance to separate targets
                cusumThreshold = 2.5 * sensMod
                cusumSlack = 0.15
                pathLossExponent = 2.2
                emaAlpha = 0.25 // Balanced smoothing for better resolution
                movementMultiplier = 3.0
                activeThreshold = 5
                absentThreshold = 8 // Quicker area clearing
            }
            else -> { // Auto (Balanced/General Area Scanning)
                cusumThreshold = 5.5 * sensMod
                cusumSlack = 0.30
                pathLossExponent = 2.2
                emaAlpha = 0.15 // Stable baseline smoothing
                movementMultiplier = 3.5
                activeThreshold = 6 
                absentThreshold = 10
            }
        }
    }

    private fun recalibrate() {
        isCalibrating = true
        calibrationCount = 0
        baselineVariance = 0.5
        baselineRssi = -45.0
        smoothedVariance = 0.5
        cusumS = 0.0
        currentState = RoomState.ABSENT
        rssiPointer = 0; rssiCount = 0
        activeCounter = 0; absentCounter = 0
        rssiHistory.fill(0)
        rollingCount = 0; rollingMean = 0.0; rollingM2 = 0.0
        visualizer.reset()
        visualizer.addLogEvent("RECALIBRATION_INIT")
        statusText.text = getString(R.string.calibrating_label, 0)
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            startMonitoring()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startMonitoring()
        } else {
            statusText.text = getString(R.string.error_permission_denied)
        }
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                updateSignalAnalysis()
                handler.postDelayed(this, 75) // 75ms = ~13.3Hz balanced rate
            }
        })
    }

    private fun updateSignalAnalysis() {
        if (!wifiManager.isWifiEnabled) {
            statusText.text = getString(R.string.wifi_disabled_label)
            return
        }
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo
        val rssi = info.rssi
        if (rssi == -127 || rssi == 0) {
            rssiText.text = getString(R.string.no_link_label)
            return
        }
        processSignalData(rssi)
    }

    private fun processSignalData(rssi: Int) {
        val now = System.currentTimeMillis()
        ppsCounter++
        if (now - lastPpsUpdate > 1000) {
            currentPps = ppsCounter; ppsCounter = 0; lastPpsUpdate = now
        }

        // 1. Primary Smoothing
        smoothedRssi = (emaAlpha * rssi) + (1 - emaAlpha) * smoothedRssi
        
        // 2. Spectral Band-Pass (Human frequency focus)
        fastEma = (0.4 * rssi) + (0.6 * fastEma)
        slowEma = (0.1 * rssi) + (0.9 * slowEma)
        bandPower = (bandPower * 0.9) + (abs(fastEma - slowEma) * 0.1)

        // 3. Rolling Variance (Circular Buffer)
        val oldVal = if (rssiCount >= windowSize) rssiHistory[rssiPointer] else null
        rssiHistory[rssiPointer] = rssi
        rssiPointer = (rssiPointer + 1) % windowSize
        if (rssiCount < windowSize) rssiCount++
        updateRollingVariance(rssi.toDouble(), oldVal?.toDouble())
        
        val rawVar = if (rollingCount > 1) rollingM2 / rollingCount else 0.0
        smoothedVariance = (smoothedVariance * 0.7) + (rawVar * 0.3)
        val currentVar = smoothedVariance

        if (now % 10000 < 100) verifySignalIntegrity(rssi, currentVar)
        
        val rssiAt1m = -32.0 
        val distFt = (10.0.pow((rssiAt1m - smoothedRssi) / (10.0 * pathLossExponent)) * 3.28084).coerceAtMost(60.0)
        
        if (isCalibrating) {
            val progress = (calibrationCount * 100) / 40
            statusText.text = getString(R.string.calibrating_label, progress)
            baselineVariance = (baselineVariance * calibrationCount + currentVar) / (calibrationCount + 1)
            baselineRssi = (baselineRssi * calibrationCount + smoothedRssi) / (calibrationCount + 1)
            calibrationCount++
            visualizer.updateData(rssi, currentVar, 0f, distFt.toFloat(), "CALIBRATING", 0f)
            if (calibrationCount >= 40) {
                isCalibrating = false; statusText.text = getString(R.string.scanner_online_label)
                visualizer.addLogEvent("BASELINE_LOCKED_SNR_${String.format(Locale.US, "%.1f", currentVar)}")
            }
        } else {
            // 4. Adaptive Learning (Background Drift)
            baselineVariance = (baselineVariance * 0.9998) + (currentVar * 0.0002)
            
            // 5. Detection Kernel (CUSUM + Persistence)
            val diff = currentVar - (baselineVariance + cusumSlack)
            cusumS = (cusumS + diff).coerceAtLeast(0.0)
            val rssiShift = abs(smoothedRssi - baselineRssi)
            val shiftThresh = if (currentModeStr == "Through-Wall") 1.2 else 2.5
            
            val trigger = (cusumS > cusumThreshold || currentVar > baselineVariance * movementMultiplier) && bandPower > 0.8
            if (trigger) { activeCounter++; absentCounter = 0 } else { activeCounter = (activeCounter - 1).coerceAtLeast(0) }

            if (activeCounter >= activeThreshold) {
                currentState = RoomState.ACTIVE; cusumS *= 0.2
            } else if (rssiShift > shiftThresh) {
                currentState = RoomState.PRESENT_STILL; absentCounter = 0
            } else if (currentVar < baselineVariance * 1.05 && cusumS < 0.15) {
                absentCounter++
                if (absentCounter >= absentThreshold) { currentState = RoomState.ABSENT; activeCounter = 0 }
            }

            // 6. Output Integration
            val rawInt = when(currentState) {
                RoomState.ACTIVE -> ((currentVar / (baselineVariance * movementMultiplier + 0.1)).toFloat()).coerceIn(1.1f, 2.5f)
                RoomState.PRESENT_STILL -> 1.05f
                else -> 0f
            }
            smoothedIntensity = (smoothedIntensity * 0.7f) + (rawInt * 0.3f)
            if (smoothedDistance == 0f) smoothedDistance = distFt.toFloat()
            val distAlpha = if (currentState == RoomState.ACTIVE) 0.15f else 0.03f
            smoothedDistance = (smoothedDistance * (1f - distAlpha)) + (distFt.toFloat() * distAlpha)
            val gInt = if (currentState == RoomState.ACTIVE) (smoothedIntensity - 1.0f).coerceIn(0f, 1f) else 0f
            
            visualizer.updateData(rssi, currentVar, smoothedIntensity, smoothedDistance, currentState.name, gInt)
            azimuthText.text = String.format(Locale.US, "AZIMUTH: %d°", visualizer.getAzimuth())
            if (currentState == RoomState.ACTIVE) {
                playGeigerClick(currentVar); lastMotionTime = now
            } else if (currentState == RoomState.ABSENT && now - lastMotionTime > 60000) {
                baselineVariance = (baselineVariance * 0.99) + (currentVar * 0.01)
                baselineRssi = (baselineRssi * 0.99) + (smoothedRssi * 0.01)
            }
            updateUI(currentState)
        }
        rssiText.text = String.format(Locale.US, "RSSI: %d dBm | VAR: %.3f | %d PPS", rssi, currentVar, currentPps)
    }

    private fun verifySignalIntegrity(rssi: Int, currentVar: Double) {
        val seed = "${rssi}_${smoothedRssi}_${currentVar}"
        val hash = seed.hashCode().toString(16).uppercase()
        visualizer.addLogEvent("SIG_VERIFY_$hash")
    }

    private fun playGeigerClick(variance: Double) {
        if (!isAudioEnabled) return
        val now = System.currentTimeMillis()
        val interval = (300 / (variance + 0.1)).toLong().coerceIn(50, 800)
        if (now - lastClickTime > interval) {
            toneGenerator.startTone(ToneGenerator.TONE_DTMF_S, 50); lastClickTime = now
        }
    }

    private fun triggerHapticAlert() {
        if (!isHapticsEnabled) return
        val v = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun updateUI(state: RoomState) {
        val oldStr = movementText.text.toString()
        if (state == RoomState.ACTIVE && oldStr == getString(R.string.area_clear_label)) {
            triggerHapticAlert(); visualizer.addLogEvent("TARGET_ACTIVE")
        } else if (state == RoomState.PRESENT_STILL && oldStr == getString(R.string.area_clear_label)) {
            visualizer.addLogEvent("TARGET_STILL")
        } else if (state == RoomState.ABSENT && oldStr != getString(R.string.area_clear_label)) {
            visualizer.addLogEvent("TARGET_LOST")
        }

        when (state) {
            RoomState.ACTIVE -> {
                movementText.text = getString(R.string.motion_detected_label)
                movementText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
            RoomState.PRESENT_STILL -> {
                movementText.text = getString(R.string.still_presence_label)
                movementText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            }
            RoomState.ABSENT -> {
                movementText.text = getString(R.string.area_clear_label)
                movementText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
        }
    }

    private fun updateRollingVariance(new: Double, old: Double?) {
        if (old == null) {
            rollingCount++
            val d = new - rollingMean; rollingMean += d / rollingCount
            rollingM2 += d * (new - rollingMean)
        } else {
            val d = new - old; val oldM = rollingMean; rollingMean += d / windowSize
            rollingM2 += (new - oldM) * (new - rollingMean) - (old - oldM) * (old - rollingMean)
        }
    }
}