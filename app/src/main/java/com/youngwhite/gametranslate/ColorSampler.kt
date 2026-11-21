package com.youngwhite.gametranslate

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs

data class SampledStyle(
    val backgroundColor: Int,
    val textColor: Int,
    val needsStroke: Boolean
)

/**
 * Muestrea los píxeles alrededor de un bloque de texto para imitar el fondo
 * y el color de letra del videojuego sin consumir demasiada CPU.
 */
object ColorSampler {

    fun sample(bitmap: Bitmap, box: Rect): SampledStyle {
        val left = box.left.coerceIn(0, bitmap.width - 1)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val right = box.right.coerceIn(left + 1, bitmap.width)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height)

        var bgR = 0L
        var bgG = 0L
        var bgB = 0L
        var bgCount = 0

        // Muestreamos el borde exterior inmediato para obtener el color de fondo
        val stepX = ((right - left) / 6).coerceAtLeast(1)
        for (x in left until right step stepX) {
            val topPixel = bitmap.getPixel(x, top)
            val botPixel = bitmap.getPixel(x, (bottom - 1).coerceAtLeast(top))
            bgR += Color.red(topPixel) + Color.red(botPixel)
            bgG += Color.green(topPixel) + Color.green(botPixel)
            bgB += Color.blue(topPixel) + Color.blue(botPixel)
            bgCount += 2
        }

        val avgR = if (bgCount > 0) (bgR / bgCount).toInt() else 15
        val avgG = if (bgCount > 0) (bgG / bgCount).toInt() else 23
        val avgB = if (bgCount > 0) (bgB / bgCount).toInt() else 42

        val bgColor = Color.argb(235, avgR, avgG, avgB)
        val bgLuminance = (0.299 * avgR + 0.587 * avgG + 0.114 * avgB)

        // Buscamos el píxel con mayor contraste dentro de la caja para estimar el color de letra
        var bestTextColor = if (bgLuminance < 128) Color.WHITE else Color.BLACK
        var maxContrast = 0.0
        val midY = (top + bottom) / 2
        for (x in left until right step stepX) {
            val p = bitmap.getPixel(x, midY)
            val lum = 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
            val contrast = abs(lum - bgLuminance)
            if (contrast > maxContrast && contrast > 60) {
                maxContrast = contrast
                bestTextColor = Color.rgb(Color.red(p), Color.green(p), Color.blue(p))
            }
        }

        return SampledStyle(
            backgroundColor = bgColor,
            textColor = bestTextColor,
            needsStroke = maxContrast < 90
        )
    }
}
