package com.youngwhite.gametranslate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var captureManager: ScreenCaptureManager? = null
    private var overlayView: TranslationOverlayView? = null
    private var regionSelectorView: RegionSelectorView? = null
    private var floatingBubble: TextView? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoLoopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(101, buildNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("data")
        }

        if (resultCode != -1 && data != null) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mpManager.getMediaProjection(resultCode, data)
            val metrics = resources.displayMetrics
            captureManager = ScreenCaptureManager(
                this,
                projection,
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi
            )
            setupOverlays()
        }

        return START_NOT_STICKY
    }

    private fun setupOverlays() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        // 1. Capa de traducciones (transparente al tacto: FLAG_NOT_TOUCHABLE)
        overlayView = TranslationOverlayView(this)
        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(overlayView, overlayParams)

        // 2. Marco de zona redimensionable (solo si no está en modo pantalla completa)
        if (!PrefsManager.isFullscreen(this)) {
            regionSelectorView = RegionSelectorView(this)
            val regionParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            windowManager.addView(regionSelectorView, regionParams)
        }

        // 3. Burbuja flotante para disparar traducción manual o limpiar
        floatingBubble = TextView(this).apply {
            text = "GT"
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC6366F1"))
            setPadding(28, 22, 28, 22)
            setOnClickListener {
                triggerSingleTranslation()
            }
            setOnLongClickListener {
                overlayView?.clear()
                true
            }
        }
        val bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 240
        }
        windowManager.addView(floatingBubble, bubbleParams)

        if (PrefsManager.isAutoMode(this)) {
            startAutoTranslationLoop()
        }
    }

    private fun triggerSingleTranslation() {
        serviceScope.launch {
            // Ocultamos la traducción un instante para no traducir nuestro propio texto
            overlayView?.clear()
            delay(40)
            val bitmap = withContext(Dispatchers.Default) { captureManager?.captureFrameInRam() } ?: return@launch
            val roi = if (PrefsManager.isFullscreen(this@OverlayService)) null else regionSelectorView?.selectedRect
            val blocks = withContext(Dispatchers.Default) {
                captureManager?.recognizeAndTranslate(bitmap, roi) ?: emptyList()
            }
            overlayView?.updateBlocks(blocks)
        }
    }

    private fun startAutoTranslationLoop() {
        autoLoopJob?.cancel()
        autoLoopJob = serviceScope.launch {
            while (isActive) {
                val roi = if (PrefsManager.isFullscreen(this@OverlayService)) null else regionSelectorView?.selectedRect
                val bitmap = withContext(Dispatchers.Default) { captureManager?.captureFrameInRam() }
                if (bitmap != null) {
                    val changed = withContext(Dispatchers.Default) {
                        captureManager?.hasRegionChanged(bitmap, roi) ?: false
                    }
                    if (changed) {
                        // Limpieza inmediata anti texto fantasma + debounce 250ms
                        overlayView?.clear()
                        delay(250)
                        val stableBitmap = withContext(Dispatchers.Default) { captureManager?.captureFrameInRam() }
                        if (stableBitmap != null) {
                            val blocks = withContext(Dispatchers.Default) {
                                captureManager?.recognizeAndTranslate(stableBitmap, roi) ?: emptyList()
                            }
                            overlayView?.updateBlocks(blocks)
                        }
                    }
                }
                delay(800)
            }
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "gametranslate_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "GameTranslate Activo",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("GameTranslate en ejecución")
            .setContentText("Toca la burbuja GT para traducir o mantenla presionada para limpiar.")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoLoopJob?.cancel()
        overlayView?.let { windowManager.removeView(it) }
        regionSelectorView?.let { windowManager.removeView(it) }
        floatingBubble?.let { windowManager.removeView(it) }
        captureManager?.release()
    }
}
