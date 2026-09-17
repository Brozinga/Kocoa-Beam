package ru.ytkab0bp.beamklipper.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.beamklipper.ui.theme.Ink

// Plain Modifier.verticalScroll() draws no scroll indicator at all — on a
// short viewport (landscape especially: the screen is only ~1080px tall
// instead of ~2280px in portrait, so far more content sits below the fold)
// there was no visual cue that there was more to scroll to, and some
// settings rows were effectively undiscoverable. This is a minimal
// always-on thumb, not a fading Material scrollbar, so it stays visible.
//
// Must go BEFORE .verticalScroll(state) in the modifier chain (i.e. applied
// to the un-scrolled container), so `size` here reflects the viewport, not
// the full (unbounded) scrolled content height.
fun Modifier.brutalScrollbar(
    state: ScrollState,
    width: Dp = 4.dp,
    color: Color = Ink.copy(alpha = 0.35f)
): Modifier = drawWithContent {
    drawContent()
    val viewportHeight = size.height
    val contentHeight = viewportHeight + state.maxValue
    if (state.maxValue <= 0 || contentHeight <= 0f) return@drawWithContent
    val thumbHeight = (viewportHeight / contentHeight * viewportHeight).coerceAtLeast(width.toPx() * 4)
    val maxThumbOffset = viewportHeight - thumbHeight
    val thumbOffset = (state.value.toFloat() / state.maxValue) * maxThumbOffset
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - width.toPx(), thumbOffset),
        size = Size(width.toPx(), thumbHeight),
        cornerRadius = CornerRadius(width.toPx() / 2)
    )
}
