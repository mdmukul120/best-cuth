@file:OptIn(UnstableApi::class)

package com.bestcut.app

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList

/** প্রিভিউ এবং এক্সপোর্ট — দুই জায়গাতেই একই ইফেক্ট ব্যবহার হয়, তাই যা দেখবেন তাই এক্সপোর্ট হবে। */
object ClipEffects {

    // column-major 4x4 গ্রেস্কেল ম্যাট্রিক্স
    private val grayMatrix = floatArrayOf(
        0.299f, 0.299f, 0.299f, 0f,
        0.587f, 0.587f, 0.587f, 0f,
        0.114f, 0.114f, 0.114f, 0f,
        0f, 0f, 0f, 1f
    )

    fun videoEffects(clip: Clip, includeSpeed: Boolean, targetHeight: Int?): List<Effect> {
        val fx = mutableListOf<Effect>()

        if (includeSpeed && clip.speed != 1f) fx.add(SpeedChangeEffect(clip.speed))
        if (targetHeight != null) fx.add(Presentation.createForHeight(targetHeight))

        when (clip.filter) {
            FilterType.NONE -> Unit
            FilterType.BW -> fx.add(RgbMatrix { _, _ -> grayMatrix })
            FilterType.WARM -> fx.add(
                RgbAdjustment.Builder().setRedScale(1.12f).setGreenScale(1.0f).setBlueScale(0.88f).build()
            )
            FilterType.COOL -> fx.add(
                RgbAdjustment.Builder().setRedScale(0.88f).setGreenScale(1.0f).setBlueScale(1.12f).build()
            )
            FilterType.VIVID -> {
                fx.add(Contrast(0.2f))
                fx.add(HslAdjustment.Builder().adjustSaturation(30f).build())
            }
        }

        if (clip.brightness != 0f) fx.add(Brightness(clip.brightness))
        if (clip.text.isNotBlank()) fx.add(textOverlay(clip.text, (targetHeight ?: 720) / 14))

        return fx
    }

    private fun textOverlay(text: String, sizePx: Int): OverlayEffect {
        val span = SpannableString(text).apply {
            setSpan(ForegroundColorSpan(Color.WHITE), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(sizePx), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val settings = OverlaySettings.Builder()
            .setBackgroundFrameAnchor(0f, -0.7f)
            .build()
        return OverlayEffect(
            ImmutableList.of<TextureOverlay>(TextOverlay.createStaticTextOverlay(span, settings))
        )
    }
}
