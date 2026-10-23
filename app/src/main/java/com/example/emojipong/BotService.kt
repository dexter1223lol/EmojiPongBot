package com.example.emojipong

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import kotlin.math.abs

class BotService : Service() {

    // ---- tune these for your phone / game layout ----
    private val SCALE = 4                 // downscale factor for speed
    private val PADDLE_Y_FRAC = 0.86f     // paddle row as fraction of screen height
    private val TOP_FRAC = 0.12f          // ignore status bar / header area above this
    private val WALL_MARGIN_FRAC = 0.05f  // emoji radius ≈ distance from wall at bounce
    private val DIFF_THRESHOLD = 40       // pixel change counted as motion
    private val MIN_PIXELS = 12           // min changed pixels to accept as the emoji
    private val SMOOTH = 0.5f             // velocity smoothing (0..1)

    private var projection: MediaProjection? = null
    private lateinit var reader: ImageReader
    private lateinit var thread: HandlerThread

    private var prev: ByteArray? = null
    private var lastX = 0f; private var lastY = 0f; private var lastT = 0L
    private var vx = 0f; private var vy = 0f; private var haveTrack = false
    private var fullW = 0; private var fullH = 0; private var w = 0; private var h = 0

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("bot", "Bot", NotificationManager.IMPORTANCE_LOW))
        val note = Notification.Builder(this, "bot").setContentTitle("Emoji Pong bot running")
            .setSmallIcon(android.R.drawable.ic_media_play).build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, note)

        val code = i?.getIntExtra("code", 0) ?: return START_NOT_STICKY
        val data: Intent = (if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra("data", Intent::class.java)
                            else @Suppress("DEPRECATION") i.getParcelableExtra("data")) ?: return START_NOT_STICKY

        projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
        projection!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, Handler(Looper.getMainLooper()))

        val dm = resources.displayMetrics
        fullW = dm.widthPixels; fullH = dm.heightPixels
        w = fullW / SCALE; h = fullH / SCALE

        thread = HandlerThread("vision").also { it.start() }
        reader = ImageReader.newInstance(fullW, fullH, PixelFormat.RGBA_8888, 2)
        projection!!.createVirtualDisplay("cap", fullW, fullH, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null)
        reader.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.use { onFrame(it) } }, Handler(thread.looper))
        return START_NOT_STICKY
    }

    // Step 1-4 per frame
    private fun onFrame(img: android.media.Image) {
        val now = SystemClock.elapsedRealtimeNanos()
        val plane = img.planes[0]
        val buf = plane.buffer; val rs = plane.rowStride; val ps = plane.pixelStride

        // grayscale, downscaled
        val cur = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val o = y * SCALE * rs + x * SCALE * ps
            val g = ((buf.get(o).toInt() and 255) + (buf.get(o + 1).toInt() and 255) + (buf.get(o + 2).toInt() and 255)) / 3
            cur[y * w + x] = g.toByte()
        }
        val p = prev; prev = cur
        if (p == null) return

        val paddleY = fullH * PADDLE_Y_FRAC
        val yMin = (fullH * TOP_FRAC / SCALE).toInt()
        val yMax = ((paddleY - 0.04f * fullH) / SCALE).toInt()   // exclude paddle band from detection

        // Step 1: detect emoji = centroid of changed pixels (frame differencing)
        var sx = 0L; var sy = 0L; var n = 0
        for (y in yMin until yMax) for (x in 0 until w) {
            val d = abs((cur[y * w + x].toInt() and 255) - (p[y * w + x].toInt() and 255))
            if (d > DIFF_THRESHOLD) { sx += x; sy += y; n++ }
        }
        if (n < MIN_PIXELS) return
        val x = sx.toFloat() / n * SCALE
        val y = sy.toFloat() / n * SCALE

        // Step 2: velocity (px/sec) from consecutive detections
        if (haveTrack) {
            val dt = (now - lastT) / 1e9f
            if (dt > 0.001f && dt < 0.2f) {
                val nvx = (x - lastX) / dt; val nvy = (y - lastY) / dt
                vx = if (vx * nvx < 0) nvx else vx + SMOOTH * (nvx - vx)   // snap on bounce
                vy = if (vy * nvy < 0) nvy else vy + SMOOTH * (nvy - vy)
            }
        }
        lastX = x; lastY = y; lastT = now; haveTrack = true

        // Step 3: predict landing x, unfolding wall bounces
        val lo = fullW * WALL_MARGIN_FRAC; val hi = fullW - lo
        val target = if (vy > 50f) {
            val t = (paddleY - y) / vy
            fold(x + vx * t, lo, hi)
        } else fullW / 2f      // ball moving away: drift to centre

        // Step 4: move paddle
        PaddleService.instance?.dragTo(target, paddleY)
    }

    /** Reflect a free-flight x position back into [lo, hi] (triangle wave). */
    private fun fold(x: Float, lo: Float, hi: Float): Float {
        val span = hi - lo
        var u = (x - lo) % (2 * span)
        if (u < 0) u += 2 * span
        return lo + if (u > span) 2 * span - u else u
    }

    override fun onDestroy() {
        projection?.stop(); thread.quitSafely(); super.onDestroy()
    }
}
