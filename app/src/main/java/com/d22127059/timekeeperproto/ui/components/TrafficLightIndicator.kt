package com.d22127059.timekeeperproto.ui.components


import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp
import com.d22127059.timekeeperproto.domain.model.AccuracyCategory
import com.d22127059.timekeeperproto.ui.theme.ErrorRed
import com.d22127059.timekeeperproto.ui.theme.SuccessGreen
import com.d22127059.timekeeperproto.ui.theme.WarningAmber

/**
 * Traffic light feedback indicator component.
 * Displays different shapes and colors based on accuracy category:
 * - GREEN: Circle (perfect timing)
 * - YELLOW: Diamond (acceptable timing)
 * - RED: Triangle (off-time)
 *
 * Includes animation for visual emphasis when category changes.
 */
@Composable
fun TrafficLightIndicator(
    category: AccuracyCategory,
    modifier: Modifier = Modifier,
    size: Float = 200f
) {
    // Animate scale when category changes
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasSize = this.size.minDimension
            val centerX = this.size.width / 2f
            val centerY = this.size.height / 2f
            val shapeSize = canvasSize * 0.8f * scale

            when (category) {
                AccuracyCategory.GREEN -> {
                    // Draw circle
                    drawCircle(
                        color = SuccessGreen,
                        radius = shapeSize / 2f,
                        center = Offset(centerX, centerY),
                        style = Fill
                    )
                }

                AccuracyCategory.YELLOW -> {
                    // Draw square
                    val halfSize = shapeSize / 2f
                    val path = Path().apply {
                        moveTo(centerX, centerY - halfSize) // Top
                        lineTo(centerX + halfSize, centerY) // Right
                        lineTo(centerX, centerY + halfSize) // Bottom
                        lineTo(centerX - halfSize, centerY) // Left
                        close()
                    }
                    drawPath(
                        path = path,
                        color = WarningAmber,
                        style = Fill
                    )
                }

                AccuracyCategory.RED -> {
                    // Draw triangle
                    val halfSize = shapeSize / 2f
                    val height = (shapeSize * 0.866f) // sqrt(3)/2 for equilateral triangle
                    val path = Path().apply {
                        moveTo(centerX, centerY - height / 2f) // Top
                        lineTo(centerX + halfSize, centerY + height / 2f) // Bottom right
                        lineTo(centerX - halfSize, centerY + height / 2f) // Bottom left
                        close()
                    }
                    drawPath(
                        path = path,
                        color = ErrorRed,
                        style = Fill
                    )
                }
            }
        }
    }
}