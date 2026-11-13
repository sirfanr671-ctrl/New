package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.voice.OrbState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedAiOrb(
    orbState: OrbState,
    rmsLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 190.dp
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val surfaceColor = MaterialTheme.colorScheme.surface

    val activeTint = when (orbState) {
        OrbState.IDLE -> secondaryColor
        OrbState.LISTENING -> primaryColor
        OrbState.THINKING -> tertiaryColor
        OrbState.SPEAKING -> primaryColor
        OrbState.ERROR -> errorColor
    }

    val infiniteTransition = rememberInfiniteTransition(label = "orb_loop")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (orbState) {
                    OrbState.THINKING -> 1600
                    OrbState.SPEAKING -> 2800
                    OrbState.LISTENING -> 3800
                    else -> 8000
                },
                easing = LinearEasing
            )
        ),
        label = "orb_rotation"
    )

    val breathing by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breathing"
    )

    val animatedRms by animateFloatAsState(
        targetValue = when (orbState) {
            OrbState.LISTENING -> rmsLevel.coerceIn(0.15f, 1.0f)
            OrbState.SPEAKING -> 0.55f + (breathing - 0.92f) * 2.2f
            OrbState.THINKING -> 0.4f
            else -> 0.1f
        },
        animationSpec = spring(stiffness = 380f),
        label = "orb_rms"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .semantics {
                contentDescription = "CSI AI Orb, status ${orbState.name.lowercase()}"
            }
            .testTag("ai_orb_button"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = this.size.minDimension
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = minDim * 0.28f

            // Ambient outer radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        activeTint.copy(alpha = 0.28f + animatedRms * 0.22f),
                        secondaryColor.copy(alpha = 0.09f),
                        activeTint.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = minDim * (0.45f + animatedRms * 0.05f)
                ),
                radius = minDim * 0.49f,
                center = center
            )

            // Frequency bars when listening or speaking
            if (orbState == OrbState.LISTENING || orbState == OrbState.SPEAKING) {
                val barCount = 24
                val innerR = baseRadius * 1.18f
                for (i in 0 until barCount) {
                    val angleRad = (i.toDouble() / barCount) * 2.0 * PI + (rotation * PI / 180.0) * 0.3
                    val harmonic = 0.5 + 0.5 * sin(i * 1.4 + rotation * 0.08)
                    val barLen = minDim * 0.03f + (minDim * 0.12f * animatedRms * harmonic.toFloat())
                    val start = Offset(
                        x = center.x + (innerR * cos(angleRad)).toFloat(),
                        y = center.y + (innerR * sin(angleRad)).toFloat()
                    )
                    val end = Offset(
                        x = center.x + ((innerR + barLen) * cos(angleRad)).toFloat(),
                        y = center.y + ((innerR + barLen) * sin(angleRad)).toFloat()
                    )
                    drawLine(
                        color = if (i % 2 == 0) activeTint else tertiaryColor,
                        start = start,
                        end = end,
                        strokeWidth = minDim * 0.015f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Counter-rotating neural orbital arcs
            rotate(degrees = rotation, pivot = center) {
                val arcRadius = baseRadius * 1.14f
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(activeTint, tertiaryColor, activeTint.copy(alpha = 0.1f))
                    ),
                    startAngle = 0f,
                    sweepAngle = if (orbState == OrbState.THINKING) 220f else 140f,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = minDim * 0.022f, cap = StrokeCap.Round)
                )
            }

            rotate(degrees = -rotation * 1.4f, pivot = center) {
                val arcRadius2 = baseRadius * 1.32f
                drawArc(
                    color = secondaryColor.copy(alpha = 0.65f),
                    startAngle = 90f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius2, center.y - arcRadius2),
                    size = Size(arcRadius2 * 2f, arcRadius2 * 2f),
                    style = Stroke(width = minDim * 0.014f, cap = StrokeCap.Round)
                )
            }

            // Core glassmorphic orb sphere
            val coreScale = if (orbState == OrbState.IDLE) breathing else (1f + animatedRms * 0.08f)
            val coreRadius = baseRadius * coreScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        activeTint.copy(alpha = 0.92f),
                        secondaryColor.copy(alpha = 0.85f),
                        surfaceColor
                    ),
                    center = Offset(center.x - coreRadius * 0.25f, center.y - coreRadius * 0.25f),
                    radius = coreRadius * 1.35f
                ),
                radius = coreRadius,
                center = center
            )
        }

        val icon = when (orbState) {
            OrbState.IDLE -> Icons.Rounded.Mic
            OrbState.LISTENING -> Icons.Rounded.GraphicEq
            OrbState.THINKING -> Icons.Rounded.Psychology
            OrbState.SPEAKING -> Icons.Rounded.VolumeUp
            OrbState.ERROR -> Icons.Rounded.WarningAmber
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(size * 0.22f)
        )
    }
}
