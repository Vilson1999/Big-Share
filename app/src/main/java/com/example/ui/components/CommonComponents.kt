package com.example.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun KeepScreenOnEffect() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
fun Modifier.atmosphericGlow(
    primaryGlow: Color,
    secondaryGlow: Color
): Modifier = this.drawBehind {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(primaryGlow.copy(alpha = 0.14f), Color.Transparent),
            center = Offset(size.width * 0.15f, size.height * 0.12f),
            radius = size.width * 0.85f
        ),
        radius = size.width * 0.85f,
        center = Offset(size.width * 0.15f, size.height * 0.12f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(secondaryGlow.copy(alpha = 0.12f), Color.Transparent),
            center = Offset(size.width * 0.9f, size.height * 0.82f),
            radius = size.width * 0.75f
        ),
        radius = size.width * 0.75f,
        center = Offset(size.width * 0.9f, size.height * 0.82f)
    )
}

@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (onBackClick != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceVariant)
                        .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
                        .clickable { onBackClick() }
                        .testTag("top_bar_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column {
                if (title.isNotEmpty()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (actions != null) {
            Spacer(modifier = Modifier.width(8.dp))
            actions()
        }
    }
}

@Composable
fun RadarAnimation(
    modifier: Modifier = Modifier,
    radarColor: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "RadarTransition")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    val pulseScale by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseScale"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) / 2f - 12.dp.toPx()
        if (maxRadius <= 0f) return@Canvas

        // Ambient glow behind radar
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(radarColor.copy(alpha = 0.14f), Color.Transparent),
                center = center,
                radius = maxRadius
            ),
            radius = maxRadius,
            center = center
        )

        // Concentric rings
        val rings = 4
        for (i in 1..rings) {
            val r = maxRadius * (i.toFloat() / rings)
            drawCircle(
                color = radarColor.copy(alpha = if (i == rings) 0.30f else 0.14f),
                radius = r,
                center = center,
                style = Stroke(width = if (i == rings) 1.5.dp.toPx() else 1.dp.toPx())
            )
        }

        // Crosshairs
        drawLine(
            color = radarColor.copy(alpha = 0.14f),
            start = Offset(center.x, center.y - maxRadius),
            end = Offset(center.x, center.y + maxRadius),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = radarColor.copy(alpha = 0.14f),
            start = Offset(center.x - maxRadius, center.y),
            end = Offset(center.x + maxRadius, center.y),
            strokeWidth = 1.dp.toPx()
        )

        // Sweeping sector cone
        drawArc(
            brush = Brush.sweepGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.75f to Color.Transparent,
                    0.95f to radarColor.copy(alpha = 0.16f),
                    1.0f to radarColor.copy(alpha = 0.35f)
                ),
                center = center
            ),
            startAngle = sweepAngle - 75f,
            sweepAngle = 75f,
            useCenter = true,
            topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
            size = Size(maxRadius * 2f, maxRadius * 2f),
            alpha = 0.65f
        )

        // Pulsing sonar ring
        drawCircle(
            color = radarColor.copy(alpha = (1f - pulseScale) * 0.38f),
            radius = maxRadius * pulseScale,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Leading sweep beam
        val rad = Math.toRadians(sweepAngle.toDouble())
        val endX = center.x + maxRadius * cos(rad).toFloat()
        val endY = center.y + maxRadius * sin(rad).toFloat()

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(radarColor.copy(alpha = 0.95f), radarColor.copy(alpha = 0.05f)),
                start = center,
                end = Offset(endX, endY)
            ),
            start = center,
            end = Offset(endX, endY),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Core center beacon
        drawCircle(
            color = radarColor.copy(alpha = 0.25f),
            radius = 12.dp.toPx(),
            center = center
        )
        drawCircle(
            color = radarColor,
            radius = 5.5.dp.toPx(),
            center = center
        )
    }
}

fun getFileIcon(mimeType: String, fileName: String): ImageVector {
    val lowerName = fileName.lowercase()
    return when {
        lowerName.endsWith(".apk") || mimeType.contains("package-archive") -> AppIcons.Apps
        mimeType.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp") -> AppIcons.Image
        mimeType.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".mov") -> AppIcons.Movie
        mimeType.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".flac") || lowerName.endsWith(".m4a") -> AppIcons.MusicNote
        lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") || lowerName.endsWith(".tar") -> AppIcons.FolderZip
        mimeType.contains("pdf") || lowerName.endsWith(".pdf") || lowerName.endsWith(".doc") || lowerName.endsWith(".docx") || lowerName.endsWith(".txt") -> AppIcons.Description
        else -> AppIcons.InsertDriveFile
    }
}
