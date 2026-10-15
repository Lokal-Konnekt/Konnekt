package com.music.konnekt.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/** True only when the opt-in visual refresh is active for the current window. */
val LocalNewDesignEnabled = staticCompositionLocalOf { false }

/**
 * The ambient layer for the new design.
 *
 * It is intentionally a sibling behind the existing navigation tree rather
 * than a replacement for it. Every existing page, player action and overlay
 * therefore stays reachable; switching the setting simply gives them a new
 * surface to live on. When disabled the function returns the content directly
 * so the legacy path does not pay for a canvas, animation or extra layer.
 */
@Composable
fun NewDesignBackdrop(
    enabled: Boolean,
    reduceAnimation: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier) {
            content()
        }
        return
    }

    val phase = if (reduceAnimation) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "newDesignAmbient")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 18_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "newDesignAmbientPhase",
        ).value
    }
    val motionAlpha by animateFloatAsState(
        targetValue = if (reduceAnimation) 0.55f else 1f,
        animationSpec = tween(480),
        label = "newDesignMotionAlpha",
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = if (reduceAnimation) 0f else phase
            val width = size.width
            val height = size.height
            val max = size.maxDimension
            val angle = t.toDouble() * 2.0 * Math.PI

            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        background,
                        primary.copy(alpha = 0.08f * motionAlpha),
                        secondary.copy(alpha = 0.06f * motionAlpha),
                        background,
                    ),
                    start = Offset(width * (t - 0.25f), 0f),
                    end = Offset(width * (t + 1.1f), height),
                ),
            )

            val leftGlow = Offset(
                x = width * (0.16f + 0.08f * kotlin.math.sin(angle).toFloat()),
                y = height * (0.24f + 0.07f * kotlin.math.cos(angle).toFloat()),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = 0.28f * motionAlpha), Color.Transparent),
                    center = leftGlow,
                    radius = max * 0.72f,
                ),
                radius = max * 0.72f,
                center = leftGlow,
            )

            val rightGlow = Offset(
                x = width * (0.86f - 0.09f * kotlin.math.sin(angle).toFloat()),
                y = height * (0.66f + 0.08f * kotlin.math.cos(angle).toFloat()),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondary.copy(alpha = 0.22f * motionAlpha), Color.Transparent),
                    center = rightGlow,
                    radius = max * 0.68f,
                ),
                radius = max * 0.68f,
                center = rightGlow,
            )

            // A quiet orbit line makes the layer feel dimensional without
            // competing with album artwork or text placed above it.
            drawArc(
                color = tertiary.copy(alpha = 0.10f * motionAlpha),
                startAngle = -28f + t * 36f,
                sweepAngle = 118f,
                useCenter = false,
                topLeft = Offset(width * 0.56f, height * 0.08f),
                size = androidx.compose.ui.geometry.Size(width * 0.64f, height * 0.54f),
                style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // The vignette is above the ambient colour but below the real app. It
        // keeps the new background calm behind dense lists and readable behind
        // translucent chrome.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.08f),
                        0.52f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.18f),
                    ),
                ),
        )
        content()
    }
}
