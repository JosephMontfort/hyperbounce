package dev.joseph.hyperbounce.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

val XiaomiSquircleShape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val sx = size.width / 28f
    val sy = size.height / 28f
    moveTo(0f * sx, 11.952f * sy)
    cubicTo(0f * sx, 7.768f * sy, 0f * sx, 5.677f * sy, 0.813f * sx, 4.079f * sy)
    cubicTo(1.529f * sx, 2.673f * sy, 2.672f * sx, 1.53f * sy, 4.078f * sx, 0.814f * sy)
    cubicTo(5.676f * sx, 0f * sy, 7.767f * sx, 0f * sy, 11.951f * sx, 0f * sy)
    lineTo(16.047f * sx, 0f * sy)
    cubicTo(20.231f * sx, 0f * sy, 22.322f * sx, 0f * sy, 23.92f * sx, 0.814f * sy)
    cubicTo(25.326f * sx, 1.53f * sy, 26.469f * sx, 2.673f * sy, 27.185f * sx, 4.079f * sy)
    cubicTo(28f * sx, 5.677f * sy, 28f * sx, 7.768f * sy, 28f * sx, 11.952f * sy)
    lineTo(28f * sx, 16.048f * sy)
    cubicTo(28f * sx, 20.232f * sy, 28f * sx, 22.323f * sy, 27.185f * sx, 23.921f * sy)
    cubicTo(26.469f * sx, 25.327f * sy, 25.326f * sx, 26.47f * sy, 23.92f * sx, 27.186f * sy)
    cubicTo(22.322f * sx, 28f * sy, 20.231f * sx, 28f * sy, 16.047f * sx, 28f * sy)
    lineTo(11.951f * sx, 28f * sy)
    cubicTo(7.767f * sx, 28f * sy, 5.676f * sx, 28f * sy, 4.078f * sx, 27.186f * sy)
    cubicTo(2.672f * sx, 26.47f * sy, 1.529f * sx, 25.327f * sy, 0.813f * sx, 23.921f * sy)
    cubicTo(0f * sx, 22.323f * sy, 0f * sx, 20.232f * sy, 0f * sx, 16.048f * sy)
    close()
}

/**
 * Premium feature row with uniform vertical height across all categories,
 * an icon on the left, bold title, two-line subtitle description, and trailing chevron.
 */
@Composable
fun FeatureCategoryRow(
    title: String,
    description: String,
    colors: HyperBounceColors,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgAlpha by animateFloatAsState(if (isPressed) 0.12f else 0.0f, label = "rowPress")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .background(colors.label.copy(alpha = bgAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(XiaomiSquircleShape),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            BasicText(
                title,
                style = HyperBounceType.headline.copy(
                    color = colors.label,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                description,
                style = HyperBounceType.footnote.copy(
                    color = colors.secondaryLabel,
                    lineHeight = 16.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        ChevronIcon(
            color = colors.tertiaryLabel,
            size = 12.dp,
            strokeWidth = 2.dp,
            pointingLeft = false
        )
    }
}
