package com.youngwhite.gametranslate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Captura pantalla exclusivamente en memoria RAM (cero archivos en disco)
 * y ejecuta OCR + traducción offline con detección de cambio de escena.
 */
class ScreenCaptureManager(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val width: Int,
    private val height: Int,
    private val density: Int
) {
    private val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    private val virtualDisplay: VirtualDisplay = mediaProjection.createVirtualDisplay(
        "GameTranslateDisplay",
        width,
        height,
        density,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        imageReader.surface,
        null,
        null
    )

    private var lastFrameSignature: Long = -1L
    private var translator: Translator? = null

    fun captureFrameInRam(): Bitmap? {
        val image = imageReader.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val bmp = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)
            Bitmap.createBitmap(bmp, 0, 0, width, height)
        } finally {
            image.close()
        }
    }

    /**
     * Detecta si el área de interés cambió respecto al fotograma anterior
     * para borrar instantáneamente el texto fantasma.
     */
    fun hasRegionChanged(bitmap: Bitmap, roi: Rect?): Boolean {
        val r = roi ?: Rect(0, 0, bitmap.width, bitmap.height)
        val left = r.left.coerceIn(0, bitmap.width - 1)
        val top = r.top.coerceIn(0, bitmap.height - 1)
        val right = r.right.coerceIn(left + 1, bitmap.width)
        val bottom = r.bottom.coerceIn(top + 1, bitmap.height)

        var sig = 0L
        val stepX = ((right - left) / 12).coerceAtLeast(1)
        val stepY = ((bottom - top) / 12).coerceAtLeast(1)
        for (y in top until bottom step stepY) {
            for (x in left until right step stepX) {
                sig += (bitmap.getPixel(x, y) and 0x00FFFFFF)
            }
        }
        val changed = lastFrameSignature == -1L || abs(sig - lastFrameSignature) > 15000L
        lastFrameSignature = sig
        return changed
    }

    suspend fun recognizeAndTranslate(bitmap: Bitmap, roi: Rect?): List<TranslatedBlock> {
        val cropRect = roi ?: Rect(0, 0, bitmap.width, bitmap.height)
        val safeLeft = cropRect.left.coerceIn(0, bitmap.width - 1)
        val safeTop = cropRect.top.coerceIn(0, bitmap.height - 1)
        val safeWidth = (cropRect.width()).coerceIn(1, bitmap.width - safeLeft)
        val safeHeight = (cropRect.height()).coerceIn(1, bitmap.height - safeTop)

        val cropped = Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)
        val sourceLang = PrefsManager.getSourceLang(context)

        val recognizer = when (sourceLang) {
            PrefsManager.LANG_JA -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            PrefsManager.LANG_ZH -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }

        val inputImage = InputImage.fromBitmap(cropped, 0)
        val visionText = suspendCancellableCoroutine { cont ->
            recognizer.process(inputImage)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        } ?: return emptyList()

        val activeTranslator = getOrCreateTranslator(sourceLang)
        val results = mutableListOf<TranslatedBlock>()

        for (block in visionText.textBlocks) {
            val box = block.boundingBox ?: continue
            val rawText = block.text.trim()
            if (rawText.length < 2) continue

            val translated = suspendCancellableCoroutine { cont ->
                activeTranslator.translate(rawText)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(rawText) }
            }

            val globalRect = Rect(
                box.left + safeLeft,
                box.top + safeTop,
                box.right + safeLeft,
                box.bottom + safeTop
            )
            val sampledStyle = ColorSampler.sample(bitmap, globalRect)
            results.add(TranslatedBlock(globalRect, translated, sampledStyle))
        }

        return results
    }

    private suspend fun getOrCreateTranslator(sourceLang: String): Translator {
        translator?.let { return it }
        val mlSource = when (sourceLang) {
            PrefsManager.LANG_JA -> TranslateLanguage.JAPANESE
            PrefsManager.LANG_ZH -> TranslateLanguage.CHINESE
            else -> TranslateLanguage.ENGLISH
        }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(mlSource)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
        val client = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build()

        suspendCancellableCoroutine { cont ->
            client.downloadModelIfNeeded(conditions)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) }
        }
        translator = client
        return client
    }

    fun release() {
        virtualDisplay.release()
        imageReader.close()
        translator?.close()
    }
}
