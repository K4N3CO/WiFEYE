package com.example.wifeye

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.MotionEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

/**
 * WifEYE Tactical Signal Visualizer
 * Professional Grade Optimization (V5 - Liquid Topology & Holographic Humanoids)
 * High-fidelity, hardware-accelerated 3D environment visualization.
 */
class SignalVisualizerView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    
    // --- PRE-ALLOCATED ASSETS ---
    private val colorBg = 0xFF06090B.toInt()
    private val colorGrid = 0xFF081408.toInt()
    private val colorGridActive = 0xFF44FF44.toInt()
    private val colorPanelBgStart = Color.argb(240, 10, 15, 20)
    private val colorPanelBgEnd = Color.argb(220, 5, 8, 12)
    private val colorPanelBorder = Color.argb(140, 68, 255, 68)
    private val colorCyan = 0xFF44AAFF.toInt()
    private val colorAlert = 0xFFFF3300.toInt()

    private val gridPaint = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val calibrationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    
    private val tempRect = RectF()
    private val renderPath = Path()
    private val tempPath = Path()
    private val biometricPath = Path()
    
    private val graphSize = 80
    private val rssiHistory = IntArray(graphSize)
    private val varHistory = FloatArray(graphSize)
    private var historyPtr = 0
    
    private val ecgSize = 60
    private val ecgBuffer = FloatArray(ecgSize)
    private var ecgPtr = 0

    // --- SHARED ENGINE STATE ---
    private var rssi = 0
    private var variance = 0.0
    private var intensity = 0f
    private var distance = 0f
    private var frame = 0f
    private var currentMode = "Auto"
    private var lastState = "ABSENT"
    private var isScanlineEnabled = true
    private val routerZ = 0.28f 
    private var smoothedOccupancy = 0f
    private var occupancyLockCounter = 0
    
    private val eventLog = ArrayList<String>(50)
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private var logScrollOffset = 0f
    private var lastTouchY = 0f
    private val logPanelRect = RectF()
    private val recalButtonRect = RectF()
    private var depthFadeShader: LinearGradient? = null
    
    var onRecalibrateListener: (() -> Unit)? = null

    private val rows = 24 
    private val cols = 20
    private val gridAngles = FloatArray(rows * cols)
    private val gridHeights = FloatArray(rows * cols)

    class Occupant(val id: Int) {
        var x = 0f; var z = 0.8f
        var tx = 0f; var tz = 0.8f
        var hr = 72; var resp = 16
        var conf = 0; var active = false
        var gest = 0f; var phase = 0f
        var walkSpeed = 0f
        var hFactor = 0.95f + (Math.random() * 0.15f).toFloat()
        
        // Trail support for professional motion feel
        val trailX = FloatArray(5)
        val trailZ = FloatArray(5)
        var trailPtr = 0
    }

    private val occupants = List(4) { Occupant(it) }

    fun setMode(mode: String) { currentMode = mode; reset() }
    fun setScanlineEnabled(enabled: Boolean) { isScanlineEnabled = enabled; invalidate() }
    fun getAzimuth(): Int = (frame * 0.35f % 360).toInt()

    fun addLogEvent(event: String) {
        if (eventLog.size >= 50) eventLog.removeAt(eventLog.size - 1)
        eventLog.add(0, "[${dateFormat.format(Date())}] $event")
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                if (recalButtonRect.contains(event.x, event.y)) {
                    onRecalibrateListener?.invoke()
                    invalidate()
                    return true
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (logPanelRect.contains(event.x, event.y)) {
                    val deltaY = event.y - lastTouchY
                    val maxScroll = max(0f, (eventLog.size * 22f) - (logPanelRect.height() - 60f))
                    logScrollOffset = (logScrollOffset + deltaY).coerceIn(-maxScroll, 0f)
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun reset() {
        rssiHistory.fill(0); varHistory.fill(0f)
        eventLog.clear()
        logScrollOffset = 0f
        occupants.forEach { it.active = false; it.conf = 0 }
        invalidate()
    }

    fun updateData(rssi: Int, variance: Double, intensity: Float, distance: Float, state: String, gest: Float) {
        this.rssi = rssi; this.variance = variance
        this.intensity = intensity; this.distance = distance
        this.lastState = state

        rssiHistory[historyPtr] = rssi
        varHistory[historyPtr] = variance.toFloat()
        historyPtr = (historyPtr + 1) % graphSize

        val instOcc = when(state) {
            "ACTIVE" -> {
                when {
                    intensity > 2.6f -> 4f
                    intensity > 2.2f -> 3f
                    intensity > 1.8f -> 2f
                    else -> 1f
                }
            }
            "PRESENT_STILL" -> 1f
            else -> 0f
        }

        val targetCount = round(instOcc).toInt()
        val currentDisplayCount = round(smoothedOccupancy).toInt()

        if (targetCount > currentDisplayCount) {
            occupancyLockCounter++
            if (occupancyLockCounter > 20) { 
                smoothedOccupancy = targetCount.toFloat()
                occupancyLockCounter = 0
            }
        } else {
            smoothedOccupancy = smoothedOccupancy * 0.92f + instOcc * 0.08f
            occupancyLockCounter = 0
        }

        val count = round(smoothedOccupancy).toInt().coerceIn(0, 4)

        for (i in 0 until 4) {
            val o = occupants[i]
            if (i < count) {
                o.active = true
                o.conf = if (state == "ACTIVE") (intensity * 40 + 20).toInt().coerceIn(60, 99) else 85
                o.hr = 68 + (intensity * 12).toInt()
                o.resp = 12 + (intensity * 4).toInt()
                
                val dNorm = (distance.coerceIn(3f, 60f) / 60f) * 0.65f 
                val ang = (i * PI.toFloat() / 2f) - (PI.toFloat() / 4f)
                o.tx = dNorm * cos(ang)
                o.tz = routerZ + dNorm * sin(ang)
                o.gest = gest
                
                // Update trails
                o.trailX[o.trailPtr] = o.x
                o.trailZ[o.trailPtr] = o.z
                o.trailPtr = (o.trailPtr + 1) % 5
            } else {
                o.conf = (o.conf - 8).coerceAtLeast(0)
                if (o.conf == 0) o.active = false
            }
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            precalculateGrid(w.toFloat(), h.toFloat())
            val sy = h.toFloat() * 0.42f
            depthFadeShader = LinearGradient(0f, sy, 0f, sy + 150f, Color.TRANSPARENT, colorBg, Shader.TileMode.CLAMP)
        }
    }

    private fun precalculateGrid(w: Float, h: Float) {
        val sy = h * 0.42f; val ey = h
        val rx = w * 0.5f; val ry = sy + routerZ * (ey - sy)
        for (r in 0 until rows) {
            val z = r.toFloat() / rows
            val py = sy + z * (ey - sy)
            val sc = z * 2.0f + 0.5f
            for (c in 0 until cols) {
                val x = (c.toFloat() / cols) - 0.5f
                val px = rx + x * w * sc
                gridAngles[r * cols + c] = (Math.toDegrees(atan2(py - ry, px - rx).toDouble()).toFloat() + 180f)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        frame += 1f
        val w = width.toFloat(); val h = height.toFloat()
        if (w < 10 || h < 10) return

        canvas.drawColor(colorBg)
        
        val isAlert = occupants.any { it.active && (it.z < 0.2f) }
        val hudColor = if (isAlert) colorAlert else colorGridActive
        
        val sy = h * 0.42f; val ey = h
        val rx = w * 0.5f; val ry = sy + routerZ * (ey - sy)

        // Atmosphere: Depth Fade
        fillPaint.shader = depthFadeShader
        canvas.drawRect(0f, 0f, w, sy + 150f, fillPaint)
        fillPaint.shader = null

        drawTacticalGrid(canvas, w, h, sy, ey, rx, ry, hudColor)
        
        // Render People (Layered)
        for (o in occupants) if (o.active && o.z <= routerZ) renderOccupant(canvas, o, w, sy, ey, hudColor)
        drawRouter(canvas, rx, ry, hudColor)
        for (o in occupants) if (o.active && o.z > routerZ) renderOccupant(canvas, o, w, sy, ey, hudColor)
        
        if (lastState == "CALIBRATING") drawCalibrationRing(canvas, w, h, hudColor)
        
        drawTacticalHUD(canvas, w, h, hudColor)
        invalidate()
    }

    private fun drawTacticalGrid(canvas: Canvas, w: Float, h: Float, sy: Float, ey: Float, rx: Float, ry: Float, color: Int) {
        val sweep = (frame * 1.5f) % 360f 
        val rB = Color.red(color); val gB = Color.green(color); val bB = Color.blue(color)
        
        // 1. Update Topology
        for (r in 0 until rows) {
            val z = r.toFloat() / rows
            for (c in 0 until cols) {
                val idx = r * cols + c
                val x = (c.toFloat() / cols) - 0.5f
                var targetH = 0f
                for (o in occupants) {
                    if (o.active) {
                        val dx = o.x - x; val dz = o.z - z
                        val distSq = dx*dx + dz*dz
                        if (distSq < 0.05f) targetH += (1f - distSq / 0.05f) * 45f * intensity
                    }
                }
                gridHeights[idx] = gridHeights[idx] * 0.85f + targetH * 0.15f
            }
        }

        // 2. Render Grid with Professional Liquid Feel
        for (r in 0 until rows) {
            val z = r.toFloat() / rows
            val rowY = sy + z * (ey - sy)
            val sc = z * 2.0f + 0.5f
            val sz = 3.5f * (z + 0.6f)
            
            for (c in 0 until cols) {
                val ang = gridAngles[r * cols + c]
                val diff = (sweep - ang + 360) % 360
                val height = gridHeights[r * cols + c]
                
                if (diff < 35) {
                    val a = (180 * (1 - diff / 35)).toInt()
                    gridPaint.color = Color.argb(a, rB, gB, bB)
                } else {
                    gridPaint.color = if (height > 5f) Color.argb(80, rB, gB, bB) else colorGrid
                }
                
                val x = (c.toFloat() / cols) - 0.5f
                val px = rx + x * w * sc
                // Render nodes flat on floor (height removed) but keep glow logic around occupants
                canvas.drawRect(px - sz, rowY - sz, px + sz, rowY + sz, gridPaint)
            }
        }
        
        // 3. Scanning Blade (Volumetric Wedge)
        renderPath.reset()
        renderPath.moveTo(rx, ry)
        val wedgeAngle = 20f // Increased for a thicker, more "filled" look
        for (step in 0..wedgeAngle.toInt()) {
            val rad = Math.toRadians((sweep - 180 - step).toDouble())
            val bx = rx + cos(rad).toFloat() * w * 0.7f
            val by = ry + sin(rad).toFloat() * (ey - sy) * 0.7f
            renderPath.lineTo(bx, by)
        }
        renderPath.close()
        fillPaint.color = color; fillPaint.alpha = 45 // Increased opacity
        canvas.drawPath(renderPath, fillPaint)
        
        linePaint.color = color; linePaint.strokeWidth = 4f; linePaint.alpha = 130 // Thicker leading edge
        val bladeRad = Math.toRadians((sweep - 180).toDouble())
        canvas.drawLine(rx, ry, rx + cos(bladeRad).toFloat() * w * 0.7f, ry + sin(bladeRad).toFloat() * (ey - sy) * 0.7f, linePaint)
        
        // 4. Distance Rings
        linePaint.style = Paint.Style.STROKE; linePaint.strokeWidth = 1f
        for (d in intArrayOf(10, 20, 30, 45, 60)) {
            val z = (d / 60f) * 0.65f
            val dx = w * (z * 2.0f + 0.5f) * 0.5f
            val dy = z * (ey - sy)
            tempRect.set(rx - dx, ry - dy, rx + dx, ry + dy)
            linePaint.color = Color.argb(55, rB, gB, bB)
            canvas.drawOval(tempRect, linePaint)
            textPaint.textSize = 14f; textPaint.color = Color.argb(90, 0, 255, 0)
            canvas.drawText("${d}FT", rx + dx + 5, ry, textPaint)
        }
    }

    private fun renderOccupant(canvas: Canvas, o: Occupant, w: Float, sy: Float, ey: Float, color: Int) {
        o.x = o.x * 0.90f + o.tx * 0.10f 
        o.z = o.z * 0.90f + o.tz * 0.10f
        
        val px = w * 0.5f + o.x * w * (o.z * 2.0f + 0.5f)
        val py = sy + o.z * (ey - sy)
        val sc = (0.55f + o.z * 1.1f) * o.hFactor
        
        // Professional Motion Trails
        linePaint.style = Paint.Style.STROKE; linePaint.strokeWidth = 2f
        for (i in 0 until 5) {
            val tx = w * 0.5f + o.trailX[i] * w * (o.trailZ[i] * 2.0f + 0.5f)
            val ty = sy + o.trailZ[i] * (ey - sy)
            linePaint.color = color; linePaint.alpha = (20 * (i + 1) / 5).toInt()
            canvas.drawCircle(tx, ty, 10f * sc, linePaint)
        }

        fillPaint.color = Color.BLACK; fillPaint.alpha = 80
        canvas.drawOval(px - 45 * sc, py - 12 * sc, px + 45 * sc, py + 12 * sc, fillPaint)
        
        val sh = 260f * sc; val sw = 100f * sc
        val rawSpeed = if (intensity > 1.1f) (intensity - 1.1f) * 0.05f else 0f
        val cappedSpeed = rawSpeed.coerceAtMost(0.04f) 
        o.walkSpeed = o.walkSpeed * 0.92f + cappedSpeed * 0.08f 
        o.phase += o.walkSpeed
        val bob = abs(sin(o.phase)) * 8f * sc
        val cY = py - bob
        
        val neckY = cY - sh * 0.82f; val headY = cY - sh * 0.94f
        val shoulderY = cY - sh * 0.78f; val midTorsoY = cY - sh * 0.58f; val hipY = cY - sh * 0.35f
        
        // 1. Holographic Torso
        fillPaint.color = color; fillPaint.alpha = 50
        renderPath.reset()
        renderPath.moveTo(px - sw * 0.45f, shoulderY)
        renderPath.lineTo(px + sw * 0.45f, shoulderY)
        renderPath.lineTo(px + sw * 0.35f, midTorsoY)
        renderPath.lineTo(px - sw * 0.35f, midTorsoY)
        renderPath.close()
        canvas.drawPath(renderPath, fillPaint)
        
        // Scanline effect on torso
        val scanY = shoulderY + (frame * 3f) % (midTorsoY - shoulderY)
        linePaint.color = Color.WHITE; linePaint.alpha = 80; linePaint.strokeWidth = 1f
        canvas.drawLine(px - sw*0.4f, scanY, px + sw*0.4f, scanY, linePaint)
        
        fillPaint.alpha = 40
        renderPath.reset()
        renderPath.moveTo(px - sw * 0.35f, midTorsoY)
        renderPath.lineTo(px + sw * 0.35f, midTorsoY)
        renderPath.lineTo(px + sw * 0.40f, hipY)
        renderPath.lineTo(px - sw * 0.40f, hipY)
        renderPath.close()
        canvas.drawPath(renderPath, fillPaint)
        
        // 2. Head & Facial Geometry
        linePaint.color = color; linePaint.strokeWidth = 3.5f * sc; linePaint.alpha = 255
        canvas.drawCircle(px, headY, 24f * sc, linePaint)
        fillPaint.alpha = 35; canvas.drawCircle(px, headY, 24f * sc, fillPaint)
        glowPaint.color = Color.WHITE; glowPaint.alpha = 200
        canvas.drawCircle(px - 8 * sc, headY, 3.5f * sc, glowPaint)
        canvas.drawCircle(px + 8 * sc, headY, 3.5f * sc, glowPaint)
        
        // 3. Volumetric Limbs
        val gE = o.gest.coerceIn(0f, 0.7f) * 135f * sc
        val swing = sin(o.phase) * 30f * sc
        
        if (o.gest > 0.08f) {
            drawVolumetricLimb(canvas, px - sw*0.42f, shoulderY, px - sw*0.65f, shoulderY - gE * 0.6f, px - sw*0.75f, shoulderY - gE, sc, color)
            drawVolumetricLimb(canvas, px + sw*0.42f, shoulderY, px + sw*0.65f, shoulderY - gE * 0.6f, px + sw*0.75f, shoulderY - gE, sc, color)
        } else {
            drawVolumetricLimb(canvas, px - sw*0.42f, shoulderY, px - sw*0.45f + swing*0.4f, shoulderY + sh*0.2f, px - sw*0.5f + swing, shoulderY + sh*0.4f, sc, color)
            drawVolumetricLimb(canvas, px + sw*0.42f, shoulderY, px + sw*0.45f - swing*0.4f, shoulderY + sh*0.2f, px + sw*0.5f - swing, shoulderY + sh*0.4f, sc, color)
        }
        drawVolumetricLimb(canvas, px - sw*0.25f, hipY, px - sw*0.35f + swing*0.5f, hipY + sh*0.3f, px - sw*0.25f + swing, py, sc, color)
        drawVolumetricLimb(canvas, px + sw*0.25f, hipY, px + sw*0.35f - swing*0.5f, hipY + sh*0.3f, px + sw*0.25f - swing, py, sc, color)
        
        // 4. Tactical Brackets & ID
        linePaint.strokeWidth = 2f * sc; linePaint.alpha = 150
        val p = 16f * sc; val cs = 22f * sc
        val bl = px - sw*0.7f - p; val br = px + sw*0.7f + p
        val bt = headY - 26f*sc - p; val bb = py + p
        canvas.drawLine(bl, bt, bl + cs, bt, linePaint); canvas.drawLine(bl, bt, bl, bt + cs, linePaint)
        canvas.drawLine(br, bt, br - cs, bt, linePaint); canvas.drawLine(br, bt, br, bt + cs, linePaint)
        canvas.drawLine(bl, bb, bl + cs, bb, linePaint); canvas.drawLine(bl, bb, bl, bb - cs, linePaint)
        canvas.drawLine(br, bb, br - cs, bb, linePaint); canvas.drawLine(br, bb, br, bb - cs, linePaint)
        
        textPaint.textSize = 15f * sc; textPaint.color = color; textPaint.alpha = 240
        canvas.drawText("TRK_0${o.id+1}", br + 8, bt + 18, textPaint)
        canvas.drawText("${String.format(Locale.US, "%.1f", distance)}FT", br + 8, bt + 36, textPaint)
    }

    private fun drawVolumetricLimb(canvas: Canvas, x1: Float, y1: Float, xm: Float, ym: Float, x2: Float, y2: Float, sc: Float, color: Int) {
        fillPaint.color = color; fillPaint.alpha = 45
        renderPath.reset()
        renderPath.moveTo(x1 - 6f*sc, y1); renderPath.lineTo(x1 + 6f*sc, y1)
        renderPath.lineTo(xm + 5f*sc, ym); renderPath.lineTo(x2 + 4f*sc, y2)
        renderPath.lineTo(x2 - 4f*sc, y2); renderPath.lineTo(xm - 5f*sc, ym); renderPath.close()
        canvas.drawPath(renderPath, fillPaint)
        
        linePaint.color = color; linePaint.strokeWidth = 3f * sc; linePaint.alpha = 220
        tempPath.reset(); tempPath.moveTo(x1, y1); tempPath.lineTo(xm, ym); tempPath.lineTo(x2, y2)
        canvas.drawPath(tempPath, linePaint)
        
        glowPaint.color = Color.WHITE; glowPaint.alpha = 180
        canvas.drawCircle(xm, ym, 4f * sc, glowPaint)
        canvas.drawCircle(x2, y2, 3.5f * sc, glowPaint)
    }

    private fun drawRouter(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        fillPaint.color = 0xFF151A20.toInt(); fillPaint.alpha = 255
        canvas.drawRect(cx - 30, cy, cx + 30, cy + 18, fillPaint)
        linePaint.color = colorPanelBorder; linePaint.strokeWidth = 2f; linePaint.alpha = 200
        canvas.drawRect(cx - 30, cy, cx + 30, cy + 18, linePaint)
        
        // Antennas
        linePaint.strokeWidth = 2f
        canvas.drawLine(cx - 15f, cy, cx - 18f, cy - 15f, linePaint)
        canvas.drawLine(cx + 15f, cy, cx + 18f, cy - 15f, linePaint)
        glowPaint.color = color; glowPaint.alpha = 180
        canvas.drawCircle(cx - 18f, cy - 15f, 2f, glowPaint)
        canvas.drawCircle(cx + 18f, cy - 15f, 2f, glowPaint)

        glowPaint.color = color; glowPaint.alpha = (abs(sin(frame * 0.15f)) * 255).toInt()
        canvas.drawCircle(cx, cy + 12, 4.5f, glowPaint)
        textPaint.textSize = 18f; textPaint.color = Color.argb(160, 255, 255, 255)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("ROUTER FRONT ▼", cx, cy + 55, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawTacticalHUD(canvas: Canvas, w: Float, h: Float, color: Int) {
        val m = 25f; val pw = (w - (m * 4f)) / 3f; val ph = 520f
        val o = occupants.find { it.active } ?: occupants[0]
        
        drawPanel(canvas, m, m, pw, ph, "BIOMETRIC SENSING")
        drawStat(canvas, "ACCURACY", "${o.conf}%", m+25, m+100, colorGridActive, pw)
        drawStat(canvas, "DISTANCE", "${String.format(Locale.US, "%.1f", distance)} FT", m+25, m+180, colorCyan, pw)
        
        if (o.active) {
            textPaint.textSize = 24f; textPaint.color = Color.WHITE
            canvas.drawText("${o.hr} BPM / ${o.resp} RPM", m + 25, m + 295, textPaint)
            drawECG(canvas, m + 25, m + 320, pw - 50, 90f, color)
        }
        
        val btnW = pw * 0.75f; val btnH = 45f
        val btnX = m + (pw - btnW) / 2f; val btnY = m + 440f
        recalButtonRect.set(btnX, btnY, btnX + btnW, btnY + btnH)
        fillPaint.color = Color.argb(120, 0, 60, 0); canvas.drawRoundRect(recalButtonRect, 8f, 8f, fillPaint)
        linePaint.color = colorPanelBorder; linePaint.strokeWidth = 2f; canvas.drawRoundRect(recalButtonRect, 8f, 8f, linePaint)
        textPaint.color = Color.WHITE; textPaint.textSize = 18f; textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("RECALIBRATE", recalButtonRect.centerX(), recalButtonRect.centerY() + 7f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        drawLogPanel(canvas, m * 2f + pw, m, pw, ph, color)

        val rx = w - pw - m
        drawPanel(canvas, rx, m, pw, ph, "SIGNAL ENVIRONMENT")
        val lx = rx + 25; val rr = w - m - 25; var py = m + 100
        drawRow(canvas, "RSSI LINK", "$rssi dBm", lx, rr, py, colorCyan); py += 45
        drawRow(canvas, "VARIANCE", String.format(Locale.US, "%.3f", variance), lx, rr, py, Color.WHITE); py += 45
        drawRow(canvas, "INTENSITY", String.format(Locale.US, "%.3f", intensity), lx, rr, py, Color.WHITE); py += 45
        
        val legendY = py + 12f
        textPaint.textSize = 12f; textPaint.color = Color.GRAY
        glowPaint.color = colorCyan; glowPaint.alpha = 255; canvas.drawCircle(lx + 5f, legendY - 4f, 3.5f, glowPaint)
        canvas.drawText("RSSI", lx + 15f, legendY, textPaint)
        val secondX = lx + 15f + textPaint.measureText("RSSI") + 25f
        glowPaint.color = Color.YELLOW; canvas.drawCircle(secondX + 5f, legendY - 4f, 3.5f, glowPaint)
        canvas.drawText("MOVEMENT", secondX + 15f, legendY, textPaint)

        drawDualGraph(canvas, lx, py + 25, pw - 50, 125f)
        
        val status = when(lastState) { "ACTIVE" -> "SCAN: MOTION" to colorAlert; "PRESENT_STILL" -> "SCAN: STILL" to Color.YELLOW; else -> "SCAN: CLEAR" to Color.GRAY }
        fillPaint.color = 0xFF1A2025.toInt(); canvas.drawRoundRect(lx, m + 420, rr, m + 490, 10f, 10f, fillPaint)
        textPaint.color = status.second; textPaint.textSize = 24f; textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(status.first, (lx + rr) * 0.5f, m + 465, textPaint); textPaint.textAlign = Paint.Align.LEFT
        
        if (isScanlineEnabled) {
            linePaint.color = color; linePaint.alpha = 50; linePaint.strokeWidth = 2f
            canvas.drawLine(0f, (frame * 4.5f) % h, w, (frame * 4.5f) % h, linePaint)
        }
    }

    private fun drawLogPanel(canvas: Canvas, x: Float, y: Float, pw: Float, ph: Float, color: Int) {
        logPanelRect.set(x, y, x + pw, y + ph)
        drawPanel(canvas, x, y, pw, ph, "TACTICAL EVENT LOG")
        canvas.save(); canvas.clipRect(x + 10, y + 55, x + pw - 10, y + ph - 10)
        val lineH = 22f; textPaint.textSize = 13f; textPaint.textAlign = Paint.Align.LEFT
        for ((i, logEntry) in eventLog.withIndex()) {
            val ly = y + 75f + i * lineH + logScrollOffset
            if (ly < (y + 45f) || ly > (y + ph + 20f)) continue
            textPaint.color = if (i == 0) Color.WHITE else color
            textPaint.alpha = if (i == 0) 255 else (255 * (1f - i.toFloat() / 25f)).toInt().coerceIn(40, 200)
            val prefix = if (i == 0 && (frame % 30 < 15)) "> " else "  "
            canvas.drawText(prefix + logEntry, x + 25, ly, textPaint)
        }
        canvas.restore()
        if (eventLog.size * lineH > ph - 60f) {
            fillPaint.color = color; fillPaint.alpha = 80; val scrollAreaH = ph - 70f
            val scrollH = scrollAreaH * (scrollAreaH / (eventLog.size * lineH))
            val scrollY = y + 60f + (-logScrollOffset / (eventLog.size * lineH)) * scrollAreaH
            canvas.drawRect(x + pw - 8f, scrollY, x + pw - 4f, scrollY + scrollH, fillPaint)
        }
    }

    private fun drawPanel(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, title: String) {
        val gradient = LinearGradient(x, y, x, y + h, colorPanelBgStart, colorPanelBgEnd, Shader.TileMode.CLAMP)
        fillPaint.shader = gradient; canvas.drawRoundRect(x, y, x + w, y + h, 15f, 15f, fillPaint); fillPaint.shader = null
        linePaint.color = colorPanelBorder; linePaint.strokeWidth = 1.5f; linePaint.alpha = 120
        canvas.drawRoundRect(x, y, x + w, y + h, 15f, 15f, linePaint)
        textPaint.color = Color.GRAY; textPaint.textSize = 17f; canvas.drawText(title, x + 25, y + 45, textPaint)
    }

    private fun drawStat(canvas: Canvas, lbl: String, valStr: String, x: Float, y: Float, color: Int, pw: Float) {
        textPaint.textSize = 14f; textPaint.color = Color.GRAY; canvas.drawText(lbl, x, y, textPaint)
        textPaint.textSize = 38f; textPaint.color = Color.WHITE; canvas.drawText(valStr, x, y + 42f, textPaint)
        val barW = pw - 50f; val barY = y + 58f; fillPaint.color = 0xFF112233.toInt()
        canvas.drawRect(x, barY, x + barW, barY + 7f, fillPaint); fillPaint.color = color
        val p = if (lbl == "ACCURACY") (valStr.filter { it.isDigit() }.toFloatOrNull() ?: 0f) / 100f else (distance / 60f).coerceIn(0.1f, 1f)
        canvas.drawRect(x, barY, x + barW * p, barY + 7f, fillPaint)
    }

    private fun drawRow(canvas: Canvas, l: String, v: String, lx: Float, rx: Float, y: Float, c: Int) {
        textPaint.textSize = 19f; textPaint.color = Color.GRAY; canvas.drawText(l, lx, y, textPaint)
        textPaint.color = c; canvas.drawText(v, rx - textPaint.measureText(v), y, textPaint)
    }

    private fun drawECG(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        val pulse = (frame * 0.15f) % (2 * PI.toFloat())
        ecgBuffer[ecgPtr] = if (abs(pulse - PI.toFloat()) < 0.2f) sin((pulse - PI.toFloat()) * 5f) * 0.8f else (Math.random() * 0.05f).toFloat()
        ecgPtr = (ecgPtr + 1) % ecgSize
        biometricPath.reset(); val step = w / (ecgSize - 1)
        for (i in 0 until ecgSize) {
            val px = x + i * step; val py = y + h * 0.5f - ecgBuffer[(ecgPtr + i) % ecgSize] * h * 0.45f
            if (i == 0) biometricPath.moveTo(px, py) else biometricPath.lineTo(px, py)
        }
        linePaint.color = color; linePaint.strokeWidth = 3.5f; canvas.drawPath(biometricPath, linePaint)
    }

    private fun drawDualGraph(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        fillPaint.color = Color.BLACK; canvas.drawRect(x, y, x + w, y + h, fillPaint)
        canvas.save(); canvas.clipRect(x, y, x + w, y + h)
        val step = w / (graphSize - 1); biometricPath.reset()
        for (i in 0 until graphSize) {
            val idx = (historyPtr + i) % graphSize
            val px = x + i * step; val py = y + h - (rssiHistory[idx] + 100).coerceIn(0, 75) / 75f * h
            if (i == 0) biometricPath.moveTo(px, py) else biometricPath.lineTo(px, py)
        }
        linePaint.color = colorCyan; linePaint.strokeWidth = 2.5f; canvas.drawPath(biometricPath, linePaint)
        biometricPath.reset()
        for (i in 0 until graphSize) {
            val idx = (historyPtr + i) % graphSize
            val px = x + i * step; val py = y + h - (varHistory[idx].coerceIn(0f, 5f) / 5f) * h
            if (i == 0) biometricPath.moveTo(px, py) else biometricPath.lineTo(px, py)
        }
        linePaint.color = Color.YELLOW; linePaint.alpha = 160; canvas.drawPath(biometricPath, linePaint); canvas.restore()
    }

    private fun drawCalibrationRing(canvas: Canvas, w: Float, h: Float, color: Int) {
        val cx = w*0.5f; val cy = h*0.5f; val r = 120f
        calibrationPaint.color = color; calibrationPaint.alpha = 60; canvas.drawCircle(cx, cy, r, calibrationPaint); calibrationPaint.alpha = 255
        canvas.drawArc(cx-r, cy-r, cx+r, cy+r, (frame * 2.0f) % 360f, 70f, false, calibrationPaint)
        textPaint.textAlign = Paint.Align.CENTER; textPaint.textSize = 28f; textPaint.color = color
        canvas.drawText("CALIBRATING...", cx, cy + 12, textPaint); textPaint.textAlign = Paint.Align.LEFT
    }
}