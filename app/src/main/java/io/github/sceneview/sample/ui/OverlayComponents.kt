package io.github.sceneview.sample.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sceneview.sample.model.AppMode
import io.github.sceneview.sample.model.DefaultPresets
import io.github.sceneview.sample.model.SpatialModel

/**
 * Top floating pill selector for switching between MR, AR, and Object modes.
 * Precisely matches the design from IMG-20260824-WA0008.jpg
 */
@Composable
fun TopModeSelector(
    selectedMode: AppMode,
    onModeSelected: (AppMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = Color(0xFF44464D),
                shape = CircleShape
            )
            .testTag("top_mode_selector"),
        shape = CircleShape,
        color = Color(0xFF2C2D32).copy(alpha = 0.94f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            AppMode.values().forEach { mode ->
                val isSelected = mode == selectedMode
                val animatedBg by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF9FA2A9) else Color.Transparent,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "pill_bg"
                )
                val animatedTextColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF101114) else Color(0xFF7E8086),
                    animationSpec = tween(durationMillis = 200),
                    label = "pill_text"
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(animatedBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onModeSelected(mode) }
                        .padding(horizontal = 22.dp, vertical = 7.dp)
                        .testTag("tab_mode_${mode.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.displayName,
                        color = animatedTextColor,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Bottom floating capsule action bar.
 * Contains PHOTO, circular red REC, Open, and Clear actions.
 * Matches IMG-20260824-WA0008.jpg
 */
@Composable
fun BottomActionBar(
    isRecording: Boolean,
    onPhotoClick: () -> Unit,
    onRecClick: () -> Unit,
    onOpenClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .testTag("bottom_action_bar"),
        shape = CircleShape,
        color = Color(0xFFB4B7BE),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // PHOTO button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onPhotoClick() }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("button_photo"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PHOTO",
                    color = Color(0xFF1C1D21),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // Circular Red REC button
            val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isRecording) 1.12f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color(0xFFEE3524))
                    .clickable { onRecClick() }
                    .testTag("button_rec"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRecording) "STOP" else "REC",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.4.sp
                    )
                }
            }

            // Open button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onOpenClick() }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("button_open"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Open",
                    color = Color(0xFF1C1D21),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Clear button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onClearClick() }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("button_clear"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Clear",
                    color = Color(0xFF1C1D21),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Camera shutter flash overlay when user taps PHOTO.
 */
@Composable
fun ShutterFlashOverlay(
    isFlashing: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isFlashing,
        enter = fadeIn(animationSpec = tween(60)),
        exit = fadeOut(animationSpec = tween(280)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.9f))
        )
    }
}

/**
 * Active recording badge showing live elapsed time.
 */
@Composable
fun RecordingStatusBadge(
    isRecording: Boolean,
    elapsedSeconds: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isRecording,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF1A1A1E).copy(alpha = 0.9f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEE3524)),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEE3524))
                )
                Spacer(modifier = Modifier.width(6.dp))
                val minutes = elapsedSeconds / 60
                val seconds = elapsedSeconds % 60
                Text(
                    text = "REC %02d:%02d".format(minutes, seconds),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

