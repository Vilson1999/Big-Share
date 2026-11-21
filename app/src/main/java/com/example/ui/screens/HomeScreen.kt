package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppIcons
import com.example.ui.components.UserAvatarCatalog
import com.example.ui.components.atmosphericGlow
import com.example.ui.theme.DeepCyan
import com.example.ui.theme.DeepViolet
import com.example.ui.theme.ElectricBlue

@Composable
fun HomeScreen(
    userName: String,
    userAvatarIndex: Int,
    onProfileClick: () -> Unit,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val avatarRes = UserAvatarCatalog.getDrawableRes(userAvatarIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .atmosphericGlow(colors.primary, colors.secondary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top-left user photo and name (no pencil icon)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.surfaceVariant)
                        .border(1.dp, colors.outline, RoundedCornerShape(24.dp))
                        .clickable { onProfileClick() }
                        .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
                        .testTag("user_name_chip")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = avatarRes),
                            contentDescription = "User photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, colors.primary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp)
                        )
                    }
                }
            }

            // Centered big circular Send and Receive buttons (both with white icon & white label)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(34.dp, Alignment.CenterVertically)
                ) {
                    GradientCircleActionButton(
                        label = "SEND",
                        icon = AppIcons.Send,
                        gradientColors = listOf(Color(0xFF00B4D8), ElectricBlue, DeepCyan),
                        contentColor = Color.White,
                        glowColor = colors.primary,
                        testTag = "send_button",
                        onClick = onSendClick
                    )

                    GradientCircleActionButton(
                        label = "RECEIVE",
                        icon = AppIcons.FileDownload,
                        gradientColors = listOf(colors.secondary, DeepViolet),
                        contentColor = Color.White,
                        glowColor = colors.secondary,
                        testTag = "receive_button",
                        onClick = onReceiveClick
                    )
                }
            }
        }
    }
}

@Composable
private fun GradientCircleActionButton(
    label: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    contentColor: Color,
    glowColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(208.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.22f),
                        Color.Transparent
                    )
                )
            )
            .border(
                width = 1.dp,
                color = glowColor.copy(alpha = 0.28f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = CircleShape,
                    ambientColor = glowColor.copy(alpha = 0.45f),
                    spotColor = glowColor.copy(alpha = 0.55f)
                )
                .clip(CircleShape)
                .background(Brush.linearGradient(colors = gradientColors))
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.35f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.White, bounded = true),
                    onClick = onClick
                )
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(62.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    color = contentColor
                )
            }
        }
    }
}
