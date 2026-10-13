package com.advancedplayer.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.advancedplayer.ui.theme.LocalPlayerColors

/**
 * App-wide slider: a thin rounded track and a ring thumb ("a circle with a hole") whose
 * centre shows the background through it. The thumb grows slightly while dragged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    accent: Color = LocalPlayerColors.current.accent,
    holeColor: Color = MaterialTheme.colorScheme.background,
) {
    val interaction = remember { MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = dragged || pressed
    val thumbSize by animateDpAsState(if (active) 24.dp else 18.dp, label = "thumb")
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interaction,
        thumb = {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(thumbSize)
                        .background(holeColor, CircleShape)
                        .border(if (active) 6.dp else 5.dp, accent, CircleShape),
                )
            }
        },
        track = {
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val y = size.height / 2
                val h = size.height
                drawLine(inactive, Offset(h / 2, y), Offset(size.width - h / 2, y), h, StrokeCap.Round)
                val x = h / 2 + (size.width - h) * fraction
                drawLine(accent, Offset(h / 2, y), Offset(x, y), h, StrokeCap.Round)
            }
        },
    )
}
