package com.d22127059.timekeeperproto.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.d22127059.timekeeperproto.domain.model.AccuracyCategory
import com.d22127059.timekeeperproto.ui.theme.ErrorRed
import com.d22127059.timekeeperproto.ui.theme.SuccessGreen
import com.d22127059.timekeeperproto.ui.theme.WarningAmber

@Composable
fun TrafficLightIndicator(
    category: AccuracyCategory,
    modifier: Modifier = Modifier,
    size: Float = 200f
) {
    // Pulse ring animation triggered on each new hit
    val pulseAnim = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }

    LaunchedEffect(category) {
        pulseAnim.snapTo(0f)
        glowAlpha.snapTo(0.6f)
        pulseAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
        glowAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 800, easing = LinearEasing)
        )
    }

    // Idle breathe
    val breathe = rememberInfiniteTransition(label = "breathe")
    val breatheScale by breathe.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheScale"
    )

    val shapeColor = when (category) {
        AccuracyCategory.GREEN -> SuccessGreen
        AccuracyCategory.YELLOW -> WarningAmber
        AccuracyCategory.RED -> ErrorRed
    }

    val label = when (category) {
        AccuracyCategory.GREEN -> "Great!"
        AccuracyCategory.YELLOW -> "Okay"
        AccuracyCategory.RED -> "Off Beat"
    }

    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = this.size.minDimension
            val centerX = this.size.width / 2f
            val centerY = this.size.height / 2f

            // Expanding pulse ring
            val pulseRadius = (canvasSize / 2f) * (0.85f + pulseAnim.value * 0.45f)
            drawCircle(
                color = shapeColor.copy(alpha = glowAlpha.value),
                radius = pulseRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = canvasSize * 0.04f * (1f - pulseAnim.value))
            )

            // Outer soft glow layers
            drawCircle(
                color = shapeColor.copy(alpha = 0.12f * breatheScale),
                radius = canvasSize * 0.48f * breatheScale,
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = shapeColor.copy(alpha = 0.22f * breatheScale),
                radius = canvasSize * 0.41f * breatheScale,
                center = Offset(centerX, centerY)
            )

            val shapeSize = canvasSize * 0.86f * breatheScale

            when (category) {
                AccuracyCategory.GREEN -> {
                    // Shadow
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.3f),
                        radius = shapeSize / 2f + canvasSize * 0.015f,
                        center = Offset(centerX + canvasSize * 0.01f, centerY + canvasSize * 0.025f)
                    )
                    // Shape
                    drawCircle(
                        color = shapeColor,
                        radius = shapeSize / 2f,
                        center = Offset(centerX, centerY)
                    )
                }

                AccuracyCategory.YELLOW -> {
                    val halfSize = shapeSize / 2f
                    // Shadow
                    val shadowPath = Path().apply {
                        val ox = canvasSize * 0.01f; val oy = canvasSize * 0.025f
                        moveTo(centerX + ox, centerY - halfSize + oy)
                        lineTo(centerX + halfSize + ox, centerY + oy)
                        lineTo(centerX + ox, centerY + halfSize + oy)
                        lineTo(centerX - halfSize + ox, centerY + oy)
                        close()
                    }
                    drawPath(path = shadowPath, color = Color.Black.copy(alpha = 0.3f))
                    // Shape
                    val path = Path().apply {
                        moveTo(centerX, centerY - halfSize)
                        lineTo(centerX + halfSize, centerY)
                        lineTo(centerX, centerY + halfSize)
                        lineTo(centerX - halfSize, centerY)
                        close()
                    }
                    drawPath(path = path, color = shapeColor, style = Fill)
                }

                AccuracyCategory.RED -> {
                    val halfSize = shapeSize / 2f
                    val height = shapeSize * 0.866f
                    // Shadow
                    val shadowPath = Path().apply {
                        val ox = canvasSize * 0.01f; val oy = canvasSize * 0.025f
                        moveTo(centerX + ox, centerY - height / 2f + oy)
                        lineTo(centerX + halfSize + ox, centerY + height / 2f + oy)
                        lineTo(centerX - halfSize + ox, centerY + height / 2f + oy)
                        close()
                    }
                    drawPath(path = shadowPath, color = Color.Black.copy(alpha = 0.3f))
                    // Shape
                    val path = Path().apply {
                        moveTo(centerX, centerY - height / 2f)
                        lineTo(centerX + halfSize, centerY + height / 2f)
                        lineTo(centerX - halfSize, centerY + height / 2f)
                        close()
                    }
                    drawPath(path = path, color = shapeColor, style = Fill)
                }
            }

            // Text label drawn natively for performance
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = canvasSize * 0.165f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                    isAntiAlias = true
                    setShadowLayer(
                        canvasSize * 0.03f, 0f, canvasSize * 0.015f,
                        android.graphics.Color.argb(120, 0, 0, 0)
                    )
                }
                val textY = when (category) {
                    AccuracyCategory.RED -> centerY + canvasSize * 0.07f + (paint.textSize / 3f)
                    else -> centerY + (paint.textSize / 3f)
                }
                canvas.nativeCanvas.drawText(label, centerX, textY, paint)
            }
        }
    }
}