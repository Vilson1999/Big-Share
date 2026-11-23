package com.example.ui.screens

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileItem
import com.example.model.formatBytes
import com.example.ui.components.AppIcons
import com.example.ui.components.BackIconButton
import com.example.ui.components.PulseAnimation
import com.example.ui.components.UserAvatarCatalog
import com.example.ui.components.atmosphericGlow

@Composable
fun RadarScreen(
    peers: List<WifiP2pDevice>,
    peerUserNames: Map<String, String> = emptyMap(),
    peerAvatars: Map<String, Int> = emptyMap(),
    selectedFiles: List<FileItem>,
    isScanning: Boolean,
    userName: String = "",
    userAvatarIndex: Int = 0,
    connectingDeviceAddress: String? = null,
    onBackClick: () -> Unit,
    onDeviceClick: (WifiP2pDevice) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val totalSizeBytes = remember(selectedFiles) {
        selectedFiles.sumOf { it.sizeBytes }
    }
    val totalFormattedSize = remember(totalSizeBytes) {
        formatBytes(totalSizeBytes)
    }
    val fileCountLabel = if (selectedFiles.size == 1) "1 FILE" else "${selectedFiles.size} FILES"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .atmosphericGlow(colors.primary, colors.secondary)
            .testTag("radar_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                BackIconButton(
                    onClick = onBackClick,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = userName.ifBlank { "Max Share User" },
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.16f))
                            .border(1.dp, colors.primary.copy(alpha = 0.45f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                            .testTag("radar_files_summary_pill"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$fileCountLabel • $totalFormattedSize",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            maxLines = 1
                        )
                    }
                }
            }

            // Pulse Console Container with user photo & expanding waves
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .height(264.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.65f))
                    .border(1.dp, colors.outline, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                PulseAnimation(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    pulseColor = colors.primary,
                    avatarDrawableRes = UserAvatarCatalog.getDrawableRes(userAvatarIndex),
                    avatarSize = 112.dp,
                    isActive = isScanning
                )

                // Top-left live status badge inside radar panel
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.background.copy(alpha = 0.75f))
                        .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isScanning) colors.primary else colors.outlineVariant)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isScanning) "SCANNING" else "IDLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurface
                    )
                }
            }

            // Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEARBY USERS",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primary.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${peers.size} FOUND",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                if (peers.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.surfaceVariant.copy(alpha = 0.55f))
                                .border(1.dp, colors.outline, RoundedCornerShape(18.dp))
                                .padding(vertical = 28.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isScanning) "Searching for nearby receivers..." else "No users found yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Ask the other person to tap Receive.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    itemsIndexed(peers) { index, device ->
                        val resolvedUserName = peerUserNames[device.deviceAddress]
                            ?.takeIf { it.isNotBlank() }
                            ?: "Max Share User"
                        val resolvedAvatarIndex = peerAvatars[device.deviceAddress]
                        DeviceItemCard(
                            displayName = resolvedUserName,
                            avatarIndex = resolvedAvatarIndex,
                            isConnecting = connectingDeviceAddress == device.deviceAddress,
                            testTag = "connect_device_button_$index",
                            onConnectClick = { onDeviceClick(device) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceItemCard(
    displayName: String,
    avatarIndex: Int?,
    isConnecting: Boolean,
    testTag: String,
    onConnectClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceVariant)
            .border(
                1.dp,
                if (isConnecting) colors.primary else colors.outline,
                RoundedCornerShape(18.dp)
            )
            .clickable(enabled = !isConnecting) { onConnectClick() }
            .padding(14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.14f))
                    .border(1.5.dp, colors.primary.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (avatarIndex != null) {
                    Image(
                        painter = painterResource(id = UserAvatarCatalog.getDrawableRes(avatarIndex)),
                        contentDescription = displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = AppIcons.Person,
                        contentDescription = displayName,
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tap to send files",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isConnecting) colors.surface else colors.primary)
                .border(
                    1.dp,
                    if (isConnecting) colors.primary else colors.primary,
                    RoundedCornerShape(12.dp)
                )
                .clickable(enabled = !isConnecting) { onConnectClick() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isConnecting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = colors.primary,
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "WAIT",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary
                    )
                }
            } else {
                Text(
                    text = "SEND",
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 12.sp,
                    color = colors.onPrimary
                )
            }
        }
    }
}
