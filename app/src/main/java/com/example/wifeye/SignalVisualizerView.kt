package com.example.wifeye

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.util.Locale
import kotlin.math.*

class SignalVisualizerView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    // Colors
    private val colorBg = Color.parseColor("#080A0C")
    private val colorGrid = Color.parseColor("#0A1A0A")
    private val colorGridActive = Color.parseColor("#33FF33")
    private val colorPanelBg = Color.argb(200, 10, 15, 25)
    private val colorPanelBorder = Color.argb(80, 68, 255, 68)
    private val colorCyan = Color.parseColor("#44AAFF")

    // Paints
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // State
    private var rssi = 0
    private var variance = 0.0
    private var intensity = 0f
    private var distance = 0f
    private var frame = 0f
    private var currentMode = "Auto"
    private var lastState = "ABSENT"
    
    private val routerZ = 0.5f 
    private var smoothedOccupancy = 0f
    private val graphHistory = mutableListOf<Int>()
    private val maxGraphPoints = 60

    class PersonState(val id: Int) {
        var x = 0f
        var z = 0.8f
        var targetX = 0f
        var targetZ = 0.8f
        var angle = (id * PI.toFloat() / 2f) - (PI.toFloat() / 4f)
        var heartRate = 72
        var respiration = 16
        var confidence = 0
        var isActive = false
        var gestureIntensity = 0f
        var jitterX = 0f
        var jitterY = 0f
    }

    private val personsList = List(4) { PersonState(it) }

    fun setMode(mode: String) {
        currentMode = mode
        reset()
    }

    fun reset() {
        rssi = 0
        variance = 0.0
        intensity = 0f
        distance = 0f
        graphHistory.clear()
        smoothedOccupancy = 0f
        personsList.forEach { 
            it.isActive = false
            it.confidence = 0
            it.angle = (it.id * PI.toFloat() / 2f) - (PI.toFloat() / 4f)
        }
        invalidate()
    }

    fun updateData(rssi: Int, variance: Double, intensity: Float, distance: Float, roomState: String) {
        this.rssi = rssi
        this.variance = variance
        this.intensity = intensity
        this.distance = distance
        this.lastState = roomState
        
        graphHistory.add(rssi)
        if (graphHistory.size > maxGraphPoints) graphHistory.removeAt(0)
        
        val instantOccupancy = when (currentMode) {
            "Empty Room" -> 0f
            "Through-Wall" -> if (roomState != "ABSENT") 1f else 0f
            "Multi-Person", "Auto" -> {
                if (roomState == "ACTIVE") {
                    if (intensity > 1.85f) 4f
                    else if (intensity > 1.6f) 3f
                    else if (intensity > 1.35f) 2f
                    else 1f
                } else if (roomState == "PRESENT_STILL") {
                    1f
                } else 0f
            }
            else -> if (roomState != "ABSENT") 1f else 0f
        }
        
        smoothedOccupancy = (smoothedOccupancy * 0.95f) + (instantOccupancy * 0.05f)
        val activeCount = round(smoothedOccupancy).toInt().coerceIn(0, 4)

        if (activeCount > 0 && currentMode != "Empty Room") {
            for (i in 0 until activeCount) {
                val p = personsList[i]
                p.isActive = true
                if (i == 0) {
                    p.confidence = if (roomState == "ACTIVE") (intensity * 50).toInt().coerceIn(60, 99) else 75
                    p.heartRate = 65 + (intensity * 30).toInt() + (Math.random() * 5).toInt()
                    p.respiration = 12 + (intensity * 8).toInt()
                    
                    // Stationary position with intensity-based jitter instead of orbit
                    val jitterMagnitude = (intensity - 1.0f).coerceAtLeast(0f) * 15f
                    p.jitterX = (Math.random().toFloat() - 0.5f) * jitterMagnitude
                    p.jitterY = (Math.random().toFloat() - 0.5f) * jitterMagnitude

                    val cappedDist = distance.coerceIn(3f, 25f)
                    val normalizedRadius = (cappedDist / 25f) * 0.45f
                    p.targetX = normalizedRadius * cos(p.angle)
                    p.targetZ = routerZ + normalizedRadius * sin(p.angle)
                    p.gestureIntensity = if (currentMode == "Gesture Ctrl" && roomState == "ACTIVE") (intensity - 1.0f).coerceIn(0f, 1f) else 0f
                } else {
                    p.confidence = (personsList[0].confidence - (i * 10)).coerceAtLeast(30)
                    p.heartRate = personsList[0].heartRate + (Math.random() * 8 - 4).toInt()
                    p.respiration = personsList[0].respiration + (Math.random() * 2 - 1).toInt()
                    
                    val r = (distance.coerceIn(5f, 25f) / 25f) * 0.45f
                    p.targetX = r * cos(p.angle)
                    p.targetZ = routerZ + r * sin(p.angle)
                }
            }
            for (i in activeCount until 4) personsList[i].isActive = false
        } else {
            personsList.forEach { it.confidence = (it.confidence * 0.8f).toInt(); if (it.confidence < 15) it.isActive = false }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(colorBg)
        frame += 1f
        
        val w = width.toFloat()
        val h = height.toFloat()
        val startY = h * 0.42f
        val endY = h
        val rcx = w / 2
        val rcy = startY + routerZ * (endY - startY)

        drawFloor(canvas, w, h, startY, endY)
        
        // Sorting for depth rendering
        val sortedPersons = personsList.filter { it.isActive }.sortedBy { it.z }
        
        // Draw things behind router
        sortedPersons.filter { it.z <= routerZ }.forEach { drawPerson(canvas, it, w, h, startY, endY) }
        
        drawRouter(canvas, rcx, rcy)
        drawRadarWaves(canvas, rcx, rcy)
        
        // Draw things in front of router
        sortedPersons.filter { it.z > routerZ }.forEach { drawPerson(canvas, it, w, h, startY, endY) }

        drawPanels(canvas, w, h)
        drawTacticalOverlay(canvas, w, h)
        invalidate() 
    }

    private fun drawFloor(canvas: Canvas, w: Float, h: Float, startY: Float, endY: Float) {
        val rows = 18
        val cols = 18
        val scanPos = (frame * 0.02f) % 1.0f
        
        for (r in 0 until rows) {
            val z = r.toFloat() / rows
            for (c in 0 until cols) {
                val x = (c.toFloat() / cols) - 0.5f
                val px = w / 2 + x * w * (z * 2.0f + 0.5f)
                val py = startY + z * (endY - startY)
                val size = 5f * (z + 0.5f)
                
                gridPaint.color = colorGrid
                // Person highlighting
                personsList.forEach { if (it.isActive && abs(it.x - x) < 0.08 && abs(it.z - z) < 0.08) gridPaint.color = colorGridActive }
                // Scan wave effect
                if (abs(z - scanPos) < 0.05) gridPaint.color = Color.argb(100, 0, 100, 0)
                
                canvas.drawRect(px - size, py - size, px + size, py + size, gridPaint)
            }
        }
    }

    private fun drawRouter(canvas: Canvas, cx: Float, cy: Float) {
        panelPaint.color = Color.parseColor("#151A20")
        canvas.drawRect(cx - 40f, cy, cx + 40f, cy + 30f, panelPaint)
        linePaint.color = colorPanelBorder
        linePaint.strokeWidth = 2f
        canvas.drawRect(cx - 40f, cy, cx + 40f, cy + 30f, linePaint)
        glowPaint.color = Color.parseColor("#CCFF00")
        canvas.drawCircle(cx, cy - 8, 5f, glowPaint)
    }

    private fun drawRadarWaves(canvas: Canvas, cx: Float, cy: Float) {
        linePaint.style = Paint.Style.STROKE
        for (i in 0..2) {
            val radius = ((frame * 1.5f + i * 250) % 800)
            val alpha = (100 * (1 - radius / 800)).toInt().coerceIn(0, 100)
            linePaint.color = Color.argb(alpha, 68, 136, 255)
            linePaint.strokeWidth = 2f
            canvas.drawCircle(cx, cy, radius, linePaint)
            val oval = RectF(cx - radius, cy - radius/4, cx + radius, cy + radius/4)
            canvas.drawOval(oval, linePaint)
        }
    }

    private fun drawPerson(canvas: Canvas, p: PersonState, w: Float, h: Float, startY: Float, endY: Float) {
        // Update smooth position
        p.x = p.x * 0.85f + p.targetX * 0.15f
        p.z = p.z * 0.85f + p.targetZ * 0.15f
        
        val px = w / 2 + p.x * w * (p.z * 2.0f + 0.5f) + p.jitterX
        val py = startY + p.z * (endY - startY) + p.jitterY
        val scale = 0.4f + p.z * 1.2f
        
        drawSkeleton(canvas, px, py, scale, p)
    }

    private fun drawSkeleton(canvas: Canvas, x: Float, y: Float, scale: Float, p: PersonState) {
        val sH = 260f * scale
        val sW = 90f * scale
        val breath = sin(frame * 0.1f) * 4f * scale
        val sw = sin(frame * (0.15f + p.gestureIntensity * 0.2f)) * (15f * scale + p.gestureIntensity * 60f)
        
        // Glow
        glowPaint.color = colorGridActive
        glowPaint.alpha = 40
        canvas.drawCircle(x, y - sH/2, sH * 0.7f, glowPaint)

        linePaint.color = colorGridActive
        linePaint.strokeWidth = 4f * scale
        linePaint.alpha = 200

        val hY = y - sH
        val nY = y - sH * 0.88f
        val cY = y - sH * 0.75f
        val hipY = y - sH * 0.4f

        // Skeleton segments
        canvas.drawLine(x, hY + 15*scale, x, hipY, linePaint) // Spine
        canvas.drawLine(x - sW/2, nY, x + sW/2, nY, linePaint) // Shoulders
        canvas.drawCircle(x, cY, (18f + breath) * scale, linePaint) // Chest
        
        // Arms
        val aY = if (currentMode == "Gesture Ctrl") nY + 50*scale - p.gestureIntensity * 80f else nY + 50*scale
        canvas.drawLine(x - sW/2, nY, x - sW/2 - 20*scale - sw*0.5f, aY, linePaint)
        canvas.drawLine(x + sW/2, nY, x + sW/2 + 20*scale + sw*0.5f, aY, linePaint)

        // Legs
        canvas.drawLine(x - 25*scale, hipY, x - 35*scale - sw*0.3f, y, linePaint)
        canvas.drawLine(x + 25*scale, hipY, x + 35*scale + sw*0.3f, y, linePaint)

        canvas.drawCircle(x, hY, 20f * scale, linePaint) // Head
        
        // Joint markers
        glowPaint.color = Color.RED
        glowPaint.alpha = 255
        val jDist = 6f * scale
        canvas.drawCircle(x, hY, jDist, glowPaint)
        canvas.drawCircle(x - sW/2, nY, jDist, glowPaint)
        canvas.drawCircle(x + sW/2, nY, jDist, glowPaint)
        canvas.drawCircle(x, hipY, jDist, glowPaint)
    }

    private fun drawPanels(canvas: Canvas, w: Float, h: Float) {
        val pW = 380f
        val pH = 500f
        val m = 30f
        
        val p = if (personsList[0].isActive) personsList[0] else personsList.find { it.isActive } ?: personsList[0]
        val count = personsList.count { it.isActive }

        // --- Left: Analysis ---
        val lRect = RectF(m, m, m + pW, pH)
        drawTacticalPanel(canvas, lRect, "SENSING ANALYSIS")
        
        drawStat(canvas, "CONF", "${p.confidence}", "% ACCURACY", m + 25, m + 110, colorGridActive)
        drawStat(canvas, "RANGE", String.format(Locale.US, "%.1f", distance), "FT TO SRC", m + 25, m + 200, Color.YELLOW)
        
        if (p.isActive) {
            textPaint.textSize = 18f; textPaint.color = Color.GRAY
            canvas.drawText("BIOMETRICS P1", m + 25, m + 290, textPaint)
            textPaint.textSize = 32f; textPaint.color = Color.WHITE
            canvas.drawText("HR: ${p.heartRate} BPM", m + 25, m + 335, textPaint)
            canvas.drawText("RESP: ${p.respiration} RPM", m + 25, m + 380, textPaint)
        }

        // --- Right: Signal ---
        val rRect = RectF(w - pW - m, m, w - m, pH)
        drawTacticalPanel(canvas, rRect, "WIFI ENVIRONMENT")
        
        val sY = m + 105
        val rX = w - m - 25
        val lX = w - pW - m + 25
        
        drawRow(canvas, "RSSI", "$rssi dBm", lX, rX, sY, colorCyan)
        drawRow(canvas, "SNR", String.format(Locale.US, "%.2f", variance), lX, rX, sY + 45, Color.WHITE)
        drawRow(canvas, "MOTION", String.format(Locale.US, "%.3f", intensity), lX, rX, sY + 90, Color.WHITE)
        
        textPaint.color = Color.GRAY; canvas.drawText("Occupants", lX, sY + 140, textPaint)
        textPaint.color = colorCyan; canvas.drawText("$count", lX + 130, sY + 140, textPaint)
        for (i in 0..7) { glowPaint.color = if (i < count) colorGridActive else Color.DKGRAY; canvas.drawCircle(lX + 190 + i * 22, sY + 132, 6f, glowPaint) }

        val gRect = RectF(lX, sY + 170, rX, sY + 270)
        panelPaint.color = Color.BLACK; canvas.drawRoundRect(gRect, 10f, 10f, panelPaint)
        drawGraph(canvas, gRect)
        
        textPaint.color = Color.GRAY; canvas.drawText("ZONE STATUS", lX, sY + 310, textPaint)
        val bRect = RectF(lX, sY + 340, rX, sY + 410)
        
        val (statusTextStr, statusColor) = when(lastState) {
            "ACTIVE" -> "ACTIVE MOTION" to colorGridActive
            "PRESENT_STILL" -> "STILL PRESENCE" to Color.YELLOW
            else -> "CLEAR" to Color.GRAY
        }
        
        panelPaint.color = if (lastState != "ABSENT") Color.parseColor("#112211") else Color.parseColor("#1A1A1A")
        canvas.drawRoundRect(bRect, 12f, 12f, panelPaint)
        textPaint.color = statusColor
        textPaint.textSize = 24f; textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(statusTextStr, bRect.centerX(), bRect.centerY() + 10, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawTacticalPanel(canvas: Canvas, r: RectF, title: String) {
        panelPaint.color = colorPanelBg; canvas.drawRoundRect(r, 15f, 15f, panelPaint)
        linePaint.color = colorPanelBorder; linePaint.strokeWidth = 1f; canvas.drawRoundRect(r, 15f, 15f, linePaint)
        textPaint.color = Color.GRAY; textPaint.textSize = 18f; canvas.drawText(title, r.left + 25, r.top + 45, textPaint)
        canvas.drawLine(r.left + 25, r.top + 65, r.right - 25, r.top + 65, linePaint)
    }

    private fun drawStat(canvas: Canvas, lbl: String, v: String, u: String, x: Float, y: Float, c: Int) {
        textPaint.textSize = 16f; textPaint.color = Color.GRAY; canvas.drawText(lbl, x, y, textPaint)
        textPaint.textSize = 38f; textPaint.color = Color.WHITE; canvas.drawText(v, x + 60, y + 5, textPaint)
        textPaint.textSize = 14f; textPaint.color = Color.GRAY; canvas.drawText(u, x + 160, y + 5, textPaint)
        gridPaint.color = Color.parseColor("#112233"); canvas.drawRect(x, y + 20, x + 310, y + 26, gridPaint)
        gridPaint.color = c; val prog = if (lbl == "CONF") (v.toFloatOrNull() ?: 0f) / 100f else (distance / 25f).coerceIn(0.1f, 1f)
        canvas.drawRect(x, y + 20, x + 310 * prog.coerceIn(0.05f, 1f), y + 26, gridPaint)
    }

    private fun drawRow(canvas: Canvas, l: String, v: String, x: Float, rx: Float, y: Float, c: Int) {
        textPaint.textSize = 19f; textPaint.color = Color.GRAY; canvas.drawText(l, x, y, textPaint)
        textPaint.color = c; val vW = textPaint.measureText(v); canvas.drawText(v, rx - vW, y, textPaint)
    }

    private fun drawGraph(canvas: Canvas, r: RectF) {
        if (graphHistory.isEmpty()) return
        linePaint.color = colorCyan; linePaint.strokeWidth = 3f
        val path = Path(); val step = r.width() / maxGraphPoints
        for (i in graphHistory.indices) {
            val x = r.left + i * step
            val y = r.bottom - (graphHistory[i] + 100) / 70f * r.height()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
    }

    private fun drawTacticalOverlay(canvas: Canvas, w: Float, h: Float) {
        linePaint.color = Color.argb(100, 68, 255, 68); linePaint.strokeWidth = 2f
        val len = 40f
        // Top Left
        canvas.drawLine(10f, 10f, 10f + len, 10f, linePaint); canvas.drawLine(10f, 10f, 10f, 10f + len, linePaint)
        // Top Right
        canvas.drawLine(w - 10f, 10f, w - 10f - len, 10f, linePaint); canvas.drawLine(w - 10f, 10f, w - 10f, 10f + len, linePaint)
        // Bottom Left
        canvas.drawLine(10f, h - 10f, 10f + len, h - 10f, linePaint); canvas.drawLine(10f, h - 10f, 10f, h - 10f - len, linePaint)
        // Bottom Right
        canvas.drawLine(w - 10f, h - 10f, w - 10f - len, h - 10f, linePaint); canvas.drawLine(w - 10f, h - 10f, w - 10f, h - 10f - len, linePaint)
        
        textPaint.textSize = 14f; textPaint.color = Color.argb(150, 0, 255, 0)
        canvas.drawText("WIE-EYES V2.4 // LINK_STABLE", 60f, h - 30f, textPaint)
    }
}