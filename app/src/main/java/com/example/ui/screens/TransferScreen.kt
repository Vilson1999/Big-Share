package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileItem
import com.example.model.TransferDirection
import com.example.model.TransferStats
import com.example.model.TransferStatus
import com.example.ui.components.AppIcons
import com.example.ui.components.AppTopBar
import com.example.ui.components.atmosphericGlow
import com.example.ui.components.getFileIcon

@Composable
fun TransferScreen(
    files: List<FileItem>,
    stats: TransferStats,
    direction: TransferDirection,
    isActive: Boolean,
    isComplete: Boolean,
    onCancelFile: (String) -> Unit,
    onCancelAll: () -> Unit,
    onDone: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val primaryAccent = if (direction == TransferDirection.SEND) colors.primary else colors.secondary
    val animatedProgress by animateFloatAsState(
        targetValue = stats.overallProgress,
        label = "OverallProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .atmosphericGlow(primaryAccent, colors.tertiary)
            .testTag("transfer_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = if (isComplete) {
                    "Transfer Complete"
                } else if (direction == TransferDirection.SEND) {
                    "Sending Files"
                } else {
                    "Receiving Files"
                },
                subtitle = "${files.count { it.status == TransferStatus.COMPLETED }} / ${files.size} COMPLETED",
                onBackClick = if (isComplete) onDone else onCancelAll
            )

            // Hero Progress Dashboard Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.outline, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                (if (isComplete) colors.tertiary else primaryAccent).copy(alpha = 0.16f)
                            )
                            .border(
                                1.dp,
                                (if (isComplete) colors.tertiary else primaryAccent).copy(alpha = 0.45f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isComplete) "FINISHED" else if (direction == TransferDirection.SEND) "SENDING" else "RECEIVING",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isComplete) colors.tertiary else primaryAccent
                        )
                    }

                    Text(
                        text = "${(stats.overallProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .testTag("overall_progress_bar"),
                    color = if (isComplete) colors.tertiary else primaryAccent,
                    trackColor = colors.outline,
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        label = "TIME LEFT",
                        value = if (isComplete) "00:00" else stats.formattedRemainingTime,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "TRANSFERRED",
                        value = stats.formattedSizeProgress,
                        modifier = Modifier.weight(1.35f)
                    )
                    MetricTile(
                        label = "SPEED",
                        value = if (isComplete) "0 B/s" else stats.formattedSpeed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QUEUE (${files.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(files, key = { it.id }) { file ->
                    TransferFileItem(
                        file = file,
                        primaryColor = primaryAccent,
                        onCancelClick = { onCancelFile(file.id) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                if (isComplete) {
                    Button(
                        onClick = onDone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("done_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.tertiary,
                            contentColor = colors.onTertiary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "DONE",
                            style = MaterialTheme.typography.labelLarge,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Button(
                        onClick = onCancelAll,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("cancel_all_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceVariant,
                            contentColor = colors.error
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = null,
                                tint = colors.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CANCEL ALL",
                                style = MaterialTheme.typography.labelLarge,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.background.copy(alpha = 0.6f))
            .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TransferFileItem(
    file: FileItem,
    primaryColor: Color,
    onCancelClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val fileIcon = getFileIcon(file.mimeType, file.name)
    val accentColor = when (file.status) {
        TransferStatus.COMPLETED -> colors.tertiary
        TransferStatus.CANCELLED, TransferStatus.FAILED -> colors.error
        else -> primaryColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outline, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag("file_item_${file.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.14f))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (file.status) {
                        TransferStatus.COMPLETED -> AppIcons.Check
                        TransferStatus.CANCELLED -> AppIcons.Cancel
                        else -> fileIcon
                    },
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 14.sp,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when (file.status) {
                            TransferStatus.COMPLETED -> "${file.formattedSize} • DONE"
                            TransferStatus.CANCELLED -> "CANCELLED"
                            TransferStatus.FAILED -> "FAILED"
                            TransferStatus.TRANSFERRING -> "${file.formattedTransferred} / ${file.formattedSize}"
                            TransferStatus.PENDING -> "${file.formattedSize} • QUEUED"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (file.status == TransferStatus.PENDING) colors.onSurfaceVariant else accentColor
                    )

                    if (file.status == TransferStatus.TRANSFERRING) {
                        Text(
                            text = "${(file.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor
                        )
                    }
                }
            }

            if (file.status == TransferStatus.TRANSFERRING || file.status == TransferStatus.PENDING) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onCancelClick,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.outline.copy(alpha = 0.35f))
                        .testTag("cancel_file_button_${file.id}")
                ) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = "Cancel File",
                        tint = colors.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (file.status == TransferStatus.TRANSFERRING) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { file.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = primaryColor,
                trackColor = colors.outline,
                strokeCap = StrokeCap.Round
            )
        }
    }
}
