package com.youngwhite.gametranslate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View

data class TranslatedBlock(
    val rect: Rect,
    val translatedText: String,
    val style: SampledStyle
)

/**
 * Capa de dibujo 100% transparente al tacto (FLAG_NOT_TOUCHABLE)
 * que dibuja las traducciones exactamente sobre los textos originales.
 */
class TranslationOverlayView(context: Context) : View(context) {

    private var blocks: List<TranslatedBlock> = emptyList()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    fun updateBlocks(newBlocks: List<TranslatedBlock>) {
        blocks = newBlocks
        invalidate()
    }

    fun clear() {
        if (blocks.isNotEmpty()) {
            blocks = emptyList()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val fontStyle = PrefsManager.getFontStyle(context)
        val fontScale = PrefsManager.getFontScale(context)
        val useCamouflage = PrefsManager.isCamouflageEnabled(context)

        val typeface = when (fontStyle) {
            PrefsManager.FONT_RETRO -> Typeface.MONOSPACE
            PrefsManager.FONT_SERIF -> Typeface.SERIF
            else -> Typeface.SANS_SERIF
        }

        for (block in blocks) {
            val r = RectF(
                (block.rect.left - 6).toFloat(),
                (block.rect.top - 4).toFloat(),
                (block.rect.right + 6).toFloat(),
                (block.rect.bottom + 4).toFloat()
            )

            bgPaint.color = if (useCamouflage) block.style.backgroundColor else Color.argb(220, 15, 23, 42)
            canvas.drawRoundRect(r, 8f, 8f, bgPaint)

            val baseTextSize = (block.rect.height() * 0.52f).coerceIn(26f, 64f) * fontScale
            textPaint.typeface = typeface
            textPaint.textSize = baseTextSize
            textPaint.color = if (useCamouflage) block.style.textColor else Color.WHITE

            val width = block.rect.width().coerceAtLeast(80)
            val layout = StaticLayout.Builder
                .obtain(block.translatedText, 0, block.translatedText.length, textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()

            canvas.save()
            canvas.translate(block.rect.left.toFloat(), block.rect.top.toFloat())

            if (block.style.needsStroke) {
                strokePaint.set(textPaint)
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 3f
                strokePaint.color = Color.BLACK
                val strokeLayout = StaticLayout.Builder
                    .obtain(block.translatedText, 0, block.translatedText.length, strokePaint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build()
                strokeLayout.draw(canvas)
            }

            layout.draw(canvas)
            canvas.restore()
        }
    }
}
