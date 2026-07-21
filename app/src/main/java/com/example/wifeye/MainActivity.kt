package com.example.wifeye

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import kotlin.math.pow

class MainActivity : AppCompatActivity() {
    enum class RoomState { ABSENT, PRESENT_STILL, ACTIVE }
    
    private lateinit var wifiManager: WifiManager
    private lateinit var statusText: TextView
    private lateinit var rssiText: TextView
    private lateinit var movementText: TextView
    private lateinit var visualizer: SignalVisualizerView
    private lateinit var recalibrateButton: Button
    private lateinit var modeSpinner: Spinner
    
    private val handler = Handler(Looper.getMainLooper())
    private val rssiHistory = mutableListOf<Int>()
    private val windowSize = 20 // Increased for better statistical significance
    
    private var baselineVariance = 0.4
    private var baselineRssi = 0.0
    private var isCalibrating = true
    private var calibrationCount = 0
    private val movementMultiplier = 2.4
    
    private var smoothedRssi = 0.0
    private val emaAlpha = 0.25

    // CUSUM parameters for change detection
    private var cusumS = 0.0
    private var cusumSlack = 0.15
    private var cusumThreshold = 2.5
    private var pathLossExponent = 2.2
    private var currentState = RoomState.ABSENT
    private var currentModeStr = "Auto"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        statusText = findViewById(R.id.statusText)
        rssiText = findViewById(R.id.rssiText)
        movementText = findViewById(R.id.movementText)
        visualizer = findViewById(R.id.visualizer)
        recalibrateButton = findViewById(R.id.recalibrateButton)
        modeSpinner = findViewById(R.id.modeSpinner)
        
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupModeSpinner()
        recalibrateButton.setOnClickListener { recalibrate() }
        checkPermissions()
    }

    private fun setupModeSpinner() {
        val modes = arrayOf(
            "Auto", "Through-Wall", "Empty Room", "Vital Signs", "Multi-Person", "Gesture Ctrl"
        )
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
        when(mode) {
            "Through-Wall" -> {
                cusumThreshold = 1.2
                cusumSlack = 0.08
                pathLossExponent = 3.2
            }
            else -> {
                cusumThreshold = 2.5
                cusumSlack = 0.15
                pathLossExponent = 2.2
            }
        }
    }

    private fun recalibrate() {
        isCalibrating = true
        calibrationCount = 0
        baselineVariance = 0.4
        baselineRssi = 0.0
        cusumS = 0.0
        currentState = RoomState.ABSENT
        rssiHistory.clear()
        visualizer.reset()
        statusText.text = "Calibrating..."
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
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
            statusText.text = "Error: Location permission required."
        }
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                updateSignalAnalysis()
                handler.postDelayed(this, 200)
            }
        })
    }

    private fun updateSignalAnalysis() {
        if (!wifiManager.isWifiEnabled) {
            statusText.text = "WiFi Disabled"
            return
        }

        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo
        val rssi = info.rssi
        
        if (rssi == -127 || rssi == 0) {
            rssiText.text = "No Link..."
            return
        }

        // Apply EMA smoothing to raw signal
        if (smoothedRssi == 0.0) smoothedRssi = rssi.toDouble()
        smoothedRssi = (emaAlpha * rssi) + (1 - emaAlpha) * smoothedRssi

        rssiHistory.add(rssi)
        if (rssiHistory.size > windowSize) rssiHistory.removeAt(0)
        val currentVariance = calculateVariance(rssiHistory)
        
        // Distance estimation
        val rssiAtOneMeter = -32.0 
        val distanceMeters = 10.0.pow((rssiAtOneMeter - smoothedRssi) / (10.0 * pathLossExponent))
        val distanceFeet = distanceMeters * 3.28084
        
        if (isCalibrating) {
            statusText.text = String.format(Locale.US, "Baseline: %d/25", calibrationCount)
            baselineVariance = (baselineVariance * calibrationCount + currentVariance) / (calibrationCount + 1)
            baselineRssi = (baselineRssi * calibrationCount + smoothedRssi) / (calibrationCount + 1)
            calibrationCount++
            if (calibrationCount >= 25) {
                isCalibrating = false
                statusText.text = "SCANNER: ACTIVE"
            }
            visualizer.updateData(rssi, currentVariance, 0f, distanceFeet.toFloat(), "ABSENT")
        } else {
            // Adaptive baseline drift correction
            baselineVariance = (baselineVariance * 0.998) + (currentVariance * 0.002)
            
            // CUSUM Logic for State Transition
            val diff = currentVariance - (baselineVariance + cusumSlack)
            cusumS = (cusumS + diff).coerceAtLeast(0.0)

            // Presence detection based on baseline RSSI shift (Static Presence)
            val rssiShift = Math.abs(smoothedRssi - baselineRssi)
            val shiftThreshold = if (currentModeStr == "Through-Wall") 0.7 else 1.5
            
            if (cusumS > cusumThreshold || currentVariance > baselineVariance * 4.0) {
                currentState = RoomState.ACTIVE
                cusumS = cusumS * 0.5 // Partial reset to maintain state while active
            } else if (rssiShift > shiftThreshold) {
                currentState = RoomState.PRESENT_STILL
            } else if (currentVariance < baselineVariance * 1.2 && cusumS < 0.5) {
                currentState = RoomState.ABSENT
            }

            val motionIntensity = when(currentState) {
                RoomState.ACTIVE -> ((currentVariance / (baselineVariance * movementMultiplier + 0.05)).toFloat()).coerceIn(1.1f, 2f)
                RoomState.PRESENT_STILL -> 1.05f
                RoomState.ABSENT -> 0f
            }

            visualizer.updateData(rssi, currentVariance, motionIntensity, distanceFeet.toFloat(), currentState.name)

            when (currentState) {
                RoomState.ACTIVE -> {
                    movementText.text = "!!! MOTION DETECTED !!!"
                    movementText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                }
                RoomState.PRESENT_STILL -> {
                    movementText.text = "STILL PRESENCE DETECTED"
                    movementText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                }
                RoomState.ABSENT -> {
                    movementText.text = "AREA CLEAR"
                    movementText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                }
            }
        }

        rssiText.text = String.format(Locale.US, "RSSI: %d dBm | SNR: %.2f", rssi, currentVariance)
    }

    private fun calculateVariance(window: List<Int>): Double {
        if (window.size < 2) return 0.0
        val mean = window.average()
        return window.sumOf { (it - mean) * (it - mean) } / window.size
    }
}