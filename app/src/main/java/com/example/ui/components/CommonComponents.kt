package com.example.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalUseMaterialDesignUi
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
): Modifier {
    val isMaterialUi = LocalUseMaterialDesignUi.current
    val primaryAlpha = if (isMaterialUi) 0.06f else 0.14f
    val secondaryAlpha = if (isMaterialUi) 0.05f else 0.12f
    return this.drawBehind {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryGlow.copy(alpha = primaryAlpha), Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.12f),
                radius = size.width * 0.85f
            ),
            radius = size.width * 0.85f,
            center = Offset(size.width * 0.15f, size.height * 0.12f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryGlow.copy(alpha = secondaryAlpha), Color.Transparent),
                center = Offset(size.width * 0.9f, size.height * 0.82f),
                radius = size.width * 0.75f
            ),
            radius = size.width * 0.75f,
            center = Offset(size.width * 0.9f, size.height * 0.82f)
        )
    }
}

@Composable
fun BackIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val isMaterialUi = LocalUseMaterialDesignUi.current
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(if (isMaterialUi) CircleShape else RoundedCornerShape(14.dp))
            .background(if (isMaterialUi) colors.secondaryContainer else colors.surfaceVariant)
            .then(
                if (isMaterialUi) {
                    Modifier
                } else {
                    Modifier.border(1.dp, colors.outline, RoundedCornerShape(14.dp))
                }
            )
            .clickable { onClick() }
            .testTag("top_bar_back_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppIcons.ArrowBack,
            contentDescription = "Back",
            tint = if (isMaterialUi) colors.onSecondaryContainer else colors.onSurface,
            modifier = Modifier.size(20.dp)
        )
    }
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
                BackIconButton(onClick = onBackClick)
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
fun PulseAnimation(
    modifier: Modifier = Modifier,
    pulseColor: Color = MaterialTheme.colorScheme.primary,
    @DrawableRes avatarDrawableRes: Int? = null,
    avatarSize: Dp = 112.dp,
    isActive: Boolean = true
) {
    val activeFactor by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "ActivePulseFactor"
    )

    val transition = rememberInfiniteTransition(label = "PulseTransition")

    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseWavePhase"
    )

    val pulseBreath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseBreath"
    )

    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = min(size.width, size.height) / 2f - 8.dp.toPx()
            if (maxRadius <= 0f) return@Canvas

            val startRadius = if (avatarDrawableRes != null) {
                (avatarSize.toPx() / 2f).coerceAtMost(maxRadius * 0.72f)
            } else {
                0f
            }

            // Soft breathing ambient glow radiating from the user photo
            val glowAlpha = 0.10f + 0.16f * pulseBreath * activeFactor
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(pulseColor.copy(alpha = glowAlpha), Color.Transparent),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            // Soft pulse halo hugging the user profile photo when active
            if (avatarDrawableRes != null && activeFactor > 0.01f) {
                val haloRadius = startRadius + (10.dp.toPx() + 14.dp.toPx() * pulseBreath)
                val haloAlpha = (0.36f - 0.16f * pulseBreath) * activeFactor
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.60f to pulseColor.copy(alpha = haloAlpha),
                            1.0f to Color.Transparent
                        ),
                        center = center,
                        radius = haloRadius.coerceAtMost(maxRadius)
                    ),
                    radius = haloRadius.coerceAtMost(maxRadius),
                    center = center
                )
            }

            // Concentric expanding pulse waves (no radar sweep or target reticle)
            if (activeFactor > 0.01f) {
                val waveCount = 3
                for (i in 0 until waveCount) {
                    val p = (wavePhase + i.toFloat() / waveCount) % 1f
                    val easedP = 1f - (1f - p) * (1f - p)
                    val waveRadius = startRadius + (maxRadius - startRadius) * easedP
                    val fade = sin(p * Math.PI).toFloat().coerceIn(0f, 1f)
                    val fillAlpha = fade * 0.12f * activeFactor
                    val strokeAlpha = fade * 0.52f * activeFactor
                    val strokeWidth = (2.5.dp - 1.2.dp * easedP).toPx().coerceAtLeast(1.dp.toPx())

                    drawCircle(
                        color = pulseColor.copy(alpha = fillAlpha),
                        radius = waveRadius,
                        center = center
                    )
                    drawCircle(
                        color = pulseColor.copy(alpha = strokeAlpha),
                        radius = waveRadius,
                        center = center,
                        style = Stroke(width = strokeWidth)
                    )
                }
            }

            if (avatarDrawableRes == null) {
                drawCircle(
                    color = pulseColor.copy(alpha = 0.25f),
                    radius = 12.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = pulseColor,
                    radius = 5.5.dp.toPx(),
                    center = center
                )
            }
        }

        if (avatarDrawableRes != null) {
            val avatarScale = 1f + 0.035f * pulseBreath * activeFactor
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .graphicsLayer {
                        scaleX = avatarScale
                        scaleY = avatarScale
                    }
                    .shadow(
                        elevation = (12f + 8f * pulseBreath * activeFactor).dp,
                        shape = CircleShape,
                        ambientColor = pulseColor.copy(alpha = 0.45f),
                        spotColor = pulseColor.copy(alpha = 0.55f)
                    )
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(
                        width = 3.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                pulseColor,
                                pulseColor.copy(alpha = 0.50f + 0.35f * pulseBreath * activeFactor)
                            )
                        ),
                        shape = CircleShape
                    )
                    .testTag("radar_user_avatar"),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = avatarDrawableRes),
                    contentDescription = "User photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
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
