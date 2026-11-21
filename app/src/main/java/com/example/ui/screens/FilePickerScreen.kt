package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.FileItem
import com.example.model.formatBytes
import com.example.model.formatSeconds
import com.example.picker.DeviceMediaScanner
import com.example.picker.PickerAppItem
import com.example.picker.PickerCategory
import com.example.picker.PickerMediaItem
import com.example.picker.PickerThumbnailCache
import com.example.picker.StorageLocation
import com.example.picker.toFileItem
import com.example.ui.components.AppIcons
import com.example.ui.components.AppTopBar
import com.example.ui.components.atmosphericGlow

fun getMediaScanPermissions(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun hasAnyMediaReadPermission(context: Context): Boolean {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            val fullImages = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val partialVisual = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            val fullVideo = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val fullAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            fullImages || partialVisual || fullVideo || fullAudio
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        }
        else -> {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
}

@Composable
fun FilePickerScreen(
    onBackClick: () -> Unit,
    onSystemBrowseClick: () -> Unit,
    onExternalPermissionLaunch: () -> Unit,
    onConfirmSelection: (List<FileItem>) -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    var activeCategory by remember { mutableStateOf(PickerCategory.IMAGE) }
    var isScanningMedia by remember { mutableStateOf(false) }
    var isScanningApps by remember { mutableStateOf(false) }
    var hasSdCard by remember { mutableStateOf(false) }

    var images by remember { mutableStateOf<List<PickerMediaItem>>(emptyList()) }
    var videos by remember { mutableStateOf<List<PickerMediaItem>>(emptyList()) }
    var music by remember { mutableStateOf<List<PickerMediaItem>>(emptyList()) }
    var apps by remember { mutableStateOf<List<PickerAppItem>>(emptyList()) }

    var hasMediaPermission by remember { mutableStateOf(hasAnyMediaReadPermission(context)) }
    var permissionPromptedOnce by remember { mutableStateOf(false) }
    var scanNonce by remember { mutableStateOf(0) }

    val selectedMedia = remember { mutableStateMapOf<String, PickerMediaItem>() }
    val selectedApps = remember { mutableStateMapOf<String, PickerAppItem>() }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasMediaPermission = hasAnyMediaReadPermission(context)
        if (hasMediaPermission) {
            scanNonce++
        }
    }

    // Request storage permissions once if not already granted
    LaunchedEffect(Unit) {
        hasSdCard = DeviceMediaScanner.detectHasSdCard(context)
        if (!hasMediaPermission && !permissionPromptedOnce) {
            permissionPromptedOnce = true
            onExternalPermissionLaunch()
            mediaPermissionLauncher.launch(getMediaScanPermissions())
        }
    }

    // Scan installed apps (no runtime permission needed with <queries>)
    LaunchedEffect(scanNonce) {
        isScanningApps = true
        apps = DeviceMediaScanner.scanInstalledApps(context)
        isScanningApps = false
    }

    // Scan Internal + SD Card media files
    LaunchedEffect(hasMediaPermission, scanNonce) {
        hasSdCard = DeviceMediaScanner.detectHasSdCard(context)
        if (hasMediaPermission) {
            isScanningMedia = true
            val (scannedImages, scannedVideos, scannedMusic) = DeviceMediaScanner.scanMediaFiles(context)
            images = scannedImages
            videos = scannedVideos
            music = scannedMusic
            if (scannedImages.any { it.storageLocation == StorageLocation.SD_CARD } ||
                scannedVideos.any { it.storageLocation == StorageLocation.SD_CARD } ||
                scannedMusic.any { it.storageLocation == StorageLocation.SD_CARD }
            ) {
                hasSdCard = true
            }
            isScanningMedia = false
        }
    }

    val totalSelectedCount = selectedMedia.size + selectedApps.size
    val totalSelectedBytes = selectedMedia.values.sumOf { it.sizeBytes } +
        selectedApps.values.sumOf { it.sizeBytes }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .atmosphericGlow(colors.primary, colors.secondary)
            .testTag("file_picker_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = "Select Files",
                subtitle = if (hasSdCard) "Internal & SD Card" else "Internal Storage",
                onBackClick = onBackClick,
                actions = {
                    // System file browser button in top-right corner
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.primaryContainer)
                            .border(1.dp, colors.primary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .clickable { onSystemBrowseClick() }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("browse_files_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppIcons.FolderOpen,
                            contentDescription = "Browse system files",
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Browse",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary
                        )
                    }
                }
            )

            // 4-Category Tab Strip (Image, Video, Music, Apps)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PickerCategory.entries.forEach { category ->
                    val isSelected = activeCategory == category
                    val count = when (category) {
                        PickerCategory.IMAGE -> images.size
                        PickerCategory.VIDEO -> videos.size
                        PickerCategory.MUSIC -> music.size
                        PickerCategory.APPS -> apps.size
                    }
                    val icon = when (category) {
                        PickerCategory.IMAGE -> AppIcons.Image
                        PickerCategory.VIDEO -> AppIcons.Movie
                        PickerCategory.MUSIC -> AppIcons.MusicNote
                        PickerCategory.APPS -> AppIcons.Apps
                    }

                    CategoryTabChip(
                        label = category.label,
                        count = count,
                        icon = icon,
                        selected = isSelected,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("picker_tab_${category.name.lowercase()}"),
                        onClick = { activeCategory = category }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Main content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeCategory) {
                    PickerCategory.IMAGE -> {
                        if (!hasMediaPermission) {
                            MediaPermissionPrompt(
                                categoryLabel = "Images",
                                onGrantClick = {
                                    onExternalPermissionLaunch()
                                    mediaPermissionLauncher.launch(getMediaScanPermissions())
                                },
                                onBrowseClick = onSystemBrowseClick
                            )
                        } else if (isScanningMedia && images.isEmpty()) {
                            ScanningIndicator("Scanning Internal & SD Card images...")
                        } else if (images.isEmpty()) {
                            EmptyPickerState(
                                icon = AppIcons.Image,
                                title = "No images found",
                                subtitle = "Use Browse in the top right to pick files manually."
                            )
                        } else {
                            // 1. Image Grid View (4 columns), 250px x 250px thumbnails
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("picker_image_grid"),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 4.dp,
                                    bottom = if (totalSelectedCount > 0) 96.dp else 16.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(images, key = { it.id }) { item ->
                                    val isItemSelected = selectedMedia.containsKey(item.id)
                                    ImageGridCell(
                                        item = item,
                                        isSelected = isItemSelected,
                                        onClick = {
                                            if (isItemSelected) {
                                                selectedMedia.remove(item.id)
                                            } else {
                                                selectedMedia[item.id] = item
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    PickerCategory.VIDEO -> {
                        if (!hasMediaPermission) {
                            MediaPermissionPrompt(
                                categoryLabel = "Videos",
                                onGrantClick = {
                                    onExternalPermissionLaunch()
                                    mediaPermissionLauncher.launch(getMediaScanPermissions())
                                },
                                onBrowseClick = onSystemBrowseClick
                            )
                        } else if (isScanningMedia && videos.isEmpty()) {
                            ScanningIndicator("Scanning Internal & SD Card videos...")
                        } else if (videos.isEmpty()) {
                            EmptyPickerState(
                                icon = AppIcons.Movie,
                                title = "No videos found",
                                subtitle = "Use Browse in the top right to pick videos manually."
                            )
                        } else {
                            // 2. Video List View
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("picker_video_list"),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = if (totalSelectedCount > 0) 96.dp else 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(videos, key = { it.id }) { item ->
                                    val isItemSelected = selectedMedia.containsKey(item.id)
                                    VideoListRow(
                                        item = item,
                                        isSelected = isItemSelected,
                                        onClick = {
                                            if (isItemSelected) {
                                                selectedMedia.remove(item.id)
                                            } else {
                                                selectedMedia[item.id] = item
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    PickerCategory.MUSIC -> {
                        if (!hasMediaPermission) {
                            MediaPermissionPrompt(
                                categoryLabel = "Music",
                                onGrantClick = {
                                    onExternalPermissionLaunch()
                                    mediaPermissionLauncher.launch(getMediaScanPermissions())
                                },
                                onBrowseClick = onSystemBrowseClick
                            )
                        } else if (isScanningMedia && music.isEmpty()) {
                            ScanningIndicator("Scanning Internal & SD Card audio...")
                        } else if (music.isEmpty()) {
                            EmptyPickerState(
                                icon = AppIcons.MusicNote,
                                title = "No audio tracks found",
                                subtitle = "Use Browse in the top right to pick audio files manually."
                            )
                        } else {
                            // 3. Music List View
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("picker_music_list"),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = if (totalSelectedCount > 0) 96.dp else 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(music, key = { it.id }) { item ->
                                    val isItemSelected = selectedMedia.containsKey(item.id)
                                    MusicListRow(
                                        item = item,
                                        isSelected = isItemSelected,
                                        onClick = {
                                            if (isItemSelected) {
                                                selectedMedia.remove(item.id)
                                            } else {
                                                selectedMedia[item.id] = item
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    PickerCategory.APPS -> {
                        if (isScanningApps && apps.isEmpty()) {
                            ScanningIndicator("Loading installed apps...")
                        } else if (apps.isEmpty()) {
                            EmptyPickerState(
                                icon = AppIcons.Apps,
                                title = "No apps available",
                                subtitle = "Installed apps with readable APKs appear here."
                            )
                        } else {
                            // 4. Apps Grid View (5 columns), app icons as thumbnails
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(5),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("picker_apps_grid"),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 4.dp,
                                    bottom = if (totalSelectedCount > 0) 96.dp else 16.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(apps, key = { it.packageName }) { app ->
                                    val isAppSelected = selectedApps.containsKey(app.packageName)
                                    AppGridCell(
                                        app = app,
                                        isSelected = isAppSelected,
                                        onClick = {
                                            if (isAppSelected) {
                                                selectedApps.remove(app.packageName)
                                            } else {
                                                selectedApps[app.packageName] = app
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Bottom Send Bar
        AnimatedVisibility(
            visible = totalSelectedCount > 0,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(colors.surfaceVariant, colors.surface)
                        )
                    )
                    .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$totalSelectedCount SELECTED",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurface
                    )
                    Text(
                        text = formatBytes(totalSelectedBytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary
                    )
                }

                TextButton(
                    onClick = {
                        selectedMedia.clear()
                        selectedApps.clear()
                    },
                    modifier = Modifier.testTag("picker_clear_button")
                ) {
                    Text("Clear", color = colors.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = {
                        val items = buildList {
                            selectedMedia.values.forEach { add(it.toFileItem()) }
                            selectedApps.values.forEach { add(it.toFileItem()) }
                        }
                        onConfirmSelection(items)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("picker_send_button")
                ) {
                    Icon(
                        imageVector = AppIcons.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SEND",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTabChip(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val bgColor = if (selected) colors.primary.copy(alpha = 0.16f) else colors.surfaceVariant
    val borderColor = if (selected) colors.primary else colors.outline
    val contentColor = if (selected) colors.primary else colors.onSurfaceVariant

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun ImageGridCell(
    item: PickerMediaItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val thumbnail by produceState<ImageBitmap?>(
        initialValue = PickerThumbnailCache.getCached("img:${item.uriString}"),
        key1 = item.uriString
    ) {
        if (value == null) {
            value = PickerThumbnailCache.loadImageThumbnail(context, item.uriString)
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceVariant)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) colors.primary else colors.outline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
    ) {
        val bmp = thumbnail
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Image,
                    contentDescription = item.name,
                    tint = colors.outlineVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.primary.copy(alpha = 0.22f))
            )
        }

        // SD Card badge
        if (item.storageLocation == StorageLocation.SD_CARD) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "SD",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Selection circle indicator
        SelectionDot(
            selected = isSelected,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
        )
    }
}

@Composable
private fun VideoListRow(
    item: PickerMediaItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val thumbnail by produceState<ImageBitmap?>(
        initialValue = PickerThumbnailCache.getCached("vid:${item.uriString}"),
        key1 = item.uriString
    ) {
        if (value == null) {
            value = PickerThumbnailCache.loadVideoThumbnail(context, item.uriString)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.surfaceVariant)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) colors.primary else colors.outline,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Video thumbnail box
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumbnail
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = AppIcons.Movie,
                    contentDescription = null,
                    tint = colors.outlineVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 14.sp,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.durationMs > 0L) {
                    Text(
                        text = formatSeconds(item.durationMs / 1000L),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.outlineVariant
                    )
                }
                Text(
                    text = formatBytes(item.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                StorageChip(item.storageLocation)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        SelectionDot(selected = isSelected)
    }
}

@Composable
private fun MusicListRow(
    item: PickerMediaItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) colors.secondary.copy(alpha = 0.14f) else colors.surfaceVariant)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) colors.secondary else colors.outline,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.secondary.copy(alpha = 0.16f))
                .border(1.dp, colors.secondary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.MusicNote,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 14.sp,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.artist.isNotBlank()) {
                    Text(
                        text = item.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(90.dp)
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.outlineVariant
                    )
                }
                if (item.durationMs > 0L) {
                    Text(
                        text = formatSeconds(item.durationMs / 1000L),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondary
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.outlineVariant
                    )
                }
                Text(
                    text = formatBytes(item.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                StorageChip(item.storageLocation)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        SelectionDot(
            selected = isSelected,
            activeColor = colors.secondary
        )
    }
}

@Composable
private fun AppGridCell(
    app: PickerAppItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val appIcon by produceState<ImageBitmap?>(
        initialValue = PickerThumbnailCache.getCached("app:${app.packageName}"),
        key1 = app.packageName
    ) {
        if (value == null) {
            value = PickerThumbnailCache.loadAppIcon(context, app.packageName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) colors.primary.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) colors.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = appIcon
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = app.appName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(11.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Apps,
                        contentDescription = app.appName,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (isSelected) {
                SelectionDot(
                    selected = true,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = app.appName,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = formatBytes(app.sizeBytes),
            fontSize = 9.sp,
            color = colors.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun StorageChip(location: StorageLocation) {
    val colors = MaterialTheme.colorScheme
    val isSd = location == StorageLocation.SD_CARD
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSd) colors.tertiary.copy(alpha = 0.16f) else colors.outline.copy(alpha = 0.35f)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = location.label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSd) colors.tertiary else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectionDot(
    selected: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) activeColor else Color.Black.copy(alpha = 0.38f))
            .border(
                width = 1.5.dp,
                color = if (selected) activeColor else Color.White.copy(alpha = 0.85f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = "Selected",
                tint = colors.onPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun ScanningIndicator(message: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = colors.primary,
            modifier = Modifier.size(36.dp),
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyPickerState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant)
                .border(1.dp, colors.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.outlineVariant,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MediaPermissionPrompt(
    categoryLabel: String,
    onGrantClick: () -> Unit,
    onBrowseClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.14f))
                .border(1.dp, colors.primary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.SdCard,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Allow Storage Access",
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Grant media access to scan $categoryLabel from both Internal Storage and SD Card, or use Browse in the top right.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onGrantClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("grant_media_permission_button")
            ) {
                Text("Allow Access", style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = onBrowseClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surfaceVariant,
                    contentColor = colors.onSurface
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Browse", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
