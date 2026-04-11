package com.d22127059.timekeeperproto.ui.screens.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d22127059.timekeeperproto.ui.components.TrafficLightIndicator
import com.d22127059.timekeeperproto.domain.model.AccuracyCategory

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var page by remember { mutableStateOf(0) }

    val pages = listOf(
        OnboardingPage(
            title = "Welcome to TimeKeeper",
            body = "TimeKeeper listens to you play and tells you how accurately you are hitting in time with the metronome.\n\nNo musical experience needed — if you can tap a surface, you can use this app."
        ),
        OnboardingPage(
            title = "Your feedback shapes",
            body = null,
            isShapePage = true
        ),
        OnboardingPage(
            title = "One quick tip",
            body = "For the most accurate results, use headphones so the metronome click does not get picked up by the microphone.\n\nYou can still use the app without headphones — it works best in a quiet room."
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            // Page indicator dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == page) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (i == page) colors.primary
                                else colors.surfaceVariant
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            val currentPage = pages[page]

            if (currentPage.isShapePage) {
                ShapePage()
            } else {
                Text(
                    text = currentPage.title,
                    color = colors.onBackground,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = currentPage.body ?: "",
                    color = colors.onSurfaceVariant,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Next / Get Started button
            Button(
                onClick = {
                    if (page < pages.size - 1) page++
                    else onComplete()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (page < pages.size - 1) "Next" else "Get Started",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (page < pages.size - 1) {
                TextButton(onClick = onComplete) {
                    Text("Skip", color = colors.onSurfaceVariant, fontSize = 14.sp)
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShapePage() {
    val colors = MaterialTheme.colorScheme

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Your Feedback Shapes",
            color = colors.onBackground,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "After each hit, one of these shapes will appear. The shape and colour tell you how well you timed it.",
            color = colors.onSurfaceVariant,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        ShapeExplanationRow(
            category = AccuracyCategory.GREEN,
            title = "Great!",
            description = "You were within 50ms of the beat — essentially perfect timing.",
            color = Color(0xFF10B981)
        )
        Spacer(modifier = Modifier.height(16.dp))
        ShapeExplanationRow(
            category = AccuracyCategory.YELLOW,
            title = "Okay",
            description = "You were a little early or late (50–150ms). Close, but room to improve.",
            color = Color(0xFFF59E0B)
        )
        Spacer(modifier = Modifier.height(16.dp))
        ShapeExplanationRow(
            category = AccuracyCategory.RED,
            title = "Off Beat",
            description = "You were more than 150ms away from the beat. Keep practising!",
            color = Color(0xFFEF4444)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.surfaceVariant,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "The different shapes (circle, diamond, triangle) mean the same thing as the colours — they're there so colour-blind users can still read the feedback clearly.",
                color = colors.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

@Composable
private fun ShapeExplanationRow(
    category: AccuracyCategory,
    title: String,
    description: String,
    color: Color
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TrafficLightIndicator(category = category, size = 64f)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(description, color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

private data class OnboardingPage(
    val title: String,
    val body: String? = null,
    val isShapePage: Boolean = false
)