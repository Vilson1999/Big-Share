package com.example.ui.screens

import android.net.wifi.p2p.WifiP2pDevice
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileItem
import com.example.model.formatBytes
import com.example.ui.components.AppIcons
import com.example.ui.components.AppTopBar
import com.example.ui.components.RadarAnimation
import com.example.ui.components.atmosphericGlow

@Composable
fun RadarScreen(
    peers: List<WifiP2pDevice>,
    selectedFiles: List<FileItem>,
    isScanning: Boolean,
    connectingDeviceAddress: String? = null,
    onBackClick: () -> Unit,
    onRescanClick: () -> Unit,
    onDeviceClick: (WifiP2pDevice) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val totalSizeBytes = remember(selectedFiles) {
        selectedFiles.sumOf { it.sizeBytes }
    }
    val totalFormattedSize = remember(totalSizeBytes) {
        formatBytes(totalSizeBytes)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .atmosphericGlow(colors.primary, colors.secondary)
            .testTag("radar_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = "Nearby Devices",
                subtitle = "${selectedFiles.size} FILES • $totalFormattedSize",
                onBackClick = onBackClick,
                actions = {
                    IconButton(
                        onClick = onRescanClick,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceVariant)
                            .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
                            .testTag("rescan_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.Refresh,
                            contentDescription = "Rescan",
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )

            // Radar Console Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .height(225.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.65f))
                    .border(1.dp, colors.outline, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                RadarAnimation(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    radarColor = colors.primary
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
                    text = "DISCOVERED DEVICES",
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
                                text = if (isScanning) "Searching for nearby receivers..." else "No devices found yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Ask the other device to tap Receive.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    itemsIndexed(peers) { index, device ->
                        DeviceItemCard(
                            device = device,
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
    device: WifiP2pDevice,
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
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primary.copy(alpha = 0.14f))
                    .border(1.dp, colors.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Devices,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = if (device.deviceName.isNullOrBlank()) "Android Device" else device.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = device.deviceAddress,
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
