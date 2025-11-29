package com.d22127059.timekeeperproto.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DebugEvent(
    val timestamp: Long,
    val type: EventType,
    val label: String,
    val details: String = ""
)

enum class EventType {
    METRONOME_CLICK,
    HIT_DETECTED,
    HIT_FILTERED,
    ANALYSIS_RESULT
}

@Composable
fun DebugTimingVisualization(
    events: List<DebugEvent>,
    sessionStartTime: Long,
    currentTime: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E27))
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            text = "🔍 DEBUG MODE",
            color = Color(0xFFFF6B35),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Text(
            text = "Session start: $sessionStartTime",
            color = Color.Gray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Timeline visualization
        TimelineVisualization(
            events = events,
            sessionStartTime = sessionStartTime,
            currentTime = currentTime,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Event log
        Text(
            text = "EVENT LOG (${events.size} events)",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Show last 30 events
        events.takeLast(30).reversed().forEach { event ->
            EventLogItem(event, sessionStartTime)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TimelineVisualization(
    events: List<DebugEvent>,
    sessionStartTime: Long,
    currentTime: Long,
    modifier: Modifier = Modifier
) {
    val timeWindowMs = 5000L // Show last 5 seconds

    Canvas(modifier = modifier.background(Color(0xFF1A1F3A))) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        // Draw time axis
        drawLine(
            color = Color.Gray,
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 2f
        )

        // Draw time markers every second
        for (i in 0..5) {
            val x = (i / 5f) * width
            drawLine(
                color = Color.DarkGray,
                start = Offset(x, centerY - 10f),
                end = Offset(x, centerY + 10f),
                strokeWidth = 1f
            )
        }

        // Filter events to visible time window
        val visibleEvents = events.filter { event ->
            (currentTime - event.timestamp) <= timeWindowMs && event.timestamp >= sessionStartTime
        }

        // Draw events
        visibleEvents.forEach { event ->
            val timeAgo = currentTime - event.timestamp
            val position = 1f - (timeAgo.toFloat() / timeWindowMs)
            val x = position * width

            if (x in 0f..width) {
                when (event.type) {
                    EventType.METRONOME_CLICK -> {
                        // Blue vertical line for metronome
                        drawLine(
                            color = Color(0xFF3B82F6),
                            start = Offset(x, centerY - 40f),
                            end = Offset(x, centerY + 40f),
                            strokeWidth = 3f
                        )
                        // Blue dot
                        drawCircle(
                            color = Color(0xFF3B82F6),
                            radius = 6f,
                            center = Offset(x, centerY - 50f)
                        )
                    }
                    EventType.HIT_DETECTED -> {
                        // Green circle for detected hit
                        drawCircle(
                            color = Color(0xFF10B981),
                            radius = 8f,
                            center = Offset(x, centerY + 50f)
                        )
                    }
                    EventType.HIT_FILTERED -> {
                        // Orange X for filtered hit
                        drawLine(
                            color = Color(0xFFF59E0B),
                            start = Offset(x - 8f, centerY + 42f),
                            end = Offset(x + 8f, centerY + 58f),
                            strokeWidth = 3f
                        )
                        drawLine(
                            color = Color(0xFFF59E0B),
                            start = Offset(x - 8f, centerY + 58f),
                            end = Offset(x + 8f, centerY + 42f),
                            strokeWidth = 3f
                        )
                    }
                    EventType.ANALYSIS_RESULT -> {
                        // Small dot for analysis
                        drawCircle(
                            color = Color.White,
                            radius = 4f,
                            center = Offset(x, centerY)
                        )
                    }
                }
            }
        }

        // Draw "NOW" indicator
        val nowX = width
        drawLine(
            color = Color.Red,
            start = Offset(nowX, 0f),
            end = Offset(nowX, height),
            strokeWidth = 2f
        )
    }

    // Legend
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LegendItem("Metronome", Color(0xFF3B82F6))
        LegendItem("Hit", Color(0xFF10B981))
        LegendItem("Filtered", Color(0xFFF59E0B))
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(color = color)
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun EventLogItem(event: DebugEvent, sessionStartTime: Long) {
    val relativeTime = event.timestamp - sessionStartTime

    val backgroundColor = when (event.type) {
        EventType.METRONOME_CLICK -> Color(0xFF1E3A8A)
        EventType.HIT_DETECTED -> Color(0xFF065F46)
        EventType.HIT_FILTERED -> Color(0xFF92400E)
        EventType.ANALYSIS_RESULT -> Color(0xFF1F2937)
    }

    val textColor = when (event.type) {
        EventType.METRONOME_CLICK -> Color(0xFF93C5FD)
        EventType.HIT_DETECTED -> Color(0xFF6EE7B7)
        EventType.HIT_FILTERED -> Color(0xFFFBBF24)
        EventType.ANALYSIS_RESULT -> Color.White
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = event.label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "+${relativeTime}ms",
                color = textColor.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        if (event.details.isNotEmpty()) {
            Text(
                text = event.details,
                color = textColor.copy(alpha = 0.9f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}