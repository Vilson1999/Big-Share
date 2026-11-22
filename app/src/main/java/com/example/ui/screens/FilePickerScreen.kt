package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
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
import android.widget.Toast
import com.example.model.FileItem
import com.example.model.formatBytes
import com.example.model.formatSeconds
import com.example.picker.BrowserEntry
import com.example.picker.BrowserStorageRoot
import com.example.picker.DeviceBrowser
import com.example.picker.DeviceMediaScanner
import com.example.picker.PickerAppItem
import com.example.picker.PickerCategory
import com.example.picker.PickerMediaItem
import com.example.picker.PickerThumbnailCache
import com.example.picker.StorageKind
import com.example.picker.toFileItem
import kotlinx.coroutines.launch
import com.example.ui.components.AppIcons
import com.example.ui.components.AppTopBar
import com.example.ui.components.atmosphericGlow
import com.example.ui.theme.AmberGold
import com.example.ui.theme.DeepAmber
import com.example.ui.theme.DeepCyan
import com.example.ui.theme.DeepViolet
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.RoseLight
import com.example.ui.theme.RoseRed

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

@Composable
fun FilePickerScreen(
    images: List<PickerMediaItem>,
    videos: List<PickerMediaItem>,
    music: List<PickerMediaItem>,
    files: List<PickerMediaItem> = emptyList(),
    installedApps: List<PickerAppItem>,
    apkFiles: List<PickerAppItem>,
    isScanningMedia: Boolean,
    isScanningFiles: Boolean = false,
    isScanningApps: Boolean,
    hasMediaPermission: Boolean,
    onEnsureLoaded: () -> Unit,
    onMediaPermissionResult: () -> Unit,
    onBackClick: () -> Unit,
    onSystemBrowseClick: () -> Unit,
    onExternalPermissionLaunch: () -> Unit,
    onRescanApps: () -> Unit = {},
    onConfirmSelection: (List<FileItem>) -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var activeCategory by remember { mutableStateOf(PickerCategory.IMAGE) }
    var permissionPromptedOnce by remember { mutableStateOf(false) }
    var isPreparingSend by remember { mutableStateOf(false) }
    var appStorageRoots by remember { mutableStateOf<List<BrowserStorageRoot>>(emptyList()) }

    val selectedMedia = remember { mutableStateMapOf<String, PickerMediaItem>() }
    val selectedApps = remember { mutableStateMapOf<String, PickerAppItem>() }
    val selectedBrowserEntries = remember { mutableStateMapOf<String, BrowserEntry>() }

    fun handleAppTreeUriPicked(uri: Uri?) {
        if (uri == null) return
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
        onRescanApps()
        scope.launch {
            appStorageRoots = DeviceBrowser.discoverStorageRoots(context)
        }
    }

    val appTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        handleAppTreeUriPicked(uri)
    }

    val appVolumeAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleAppTreeUriPicked(result.data?.data)
        }
    }

    fun launchApkStoragePicker(root: BrowserStorageRoot? = null) {
        onExternalPermissionLaunch()
        val vol = root?.storageVolume
        if (vol != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                @Suppress("DEPRECATION")
                val intent = vol.createOpenDocumentTreeIntent()
                appVolumeAccessLauncher.launch(intent)
                return
            } catch (_: Exception) {}
        }
        appTreeLauncher.launch(null)
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onMediaPermissionResult()
    }

    LaunchedEffect(activeCategory) {
        if (activeCategory == PickerCategory.APPS) {
            appStorageRoots = DeviceBrowser.discoverStorageRoots(context)
        }
    }

    // Ensure preload is active and request storage permissions once if not already granted
    LaunchedEffect(Unit) {
        onEnsureLoaded()
        if (!hasMediaPermission && !permissionPromptedOnce) {
            permissionPromptedOnce = true
            onExternalPermissionLaunch()
            mediaPermissionLauncher.launch(getMediaScanPermissions())
        }
    }

    val selectedFoldersCount = selectedBrowserEntries.values.count { it.isDirectory }
    val totalSelectedCount = selectedMedia.size + selectedApps.size + selectedBrowserEntries.size
    val totalSelectedBytes = selectedMedia.values.sumOf { it.sizeBytes } +
        selectedApps.values.sumOf { it.sizeBytes } +
        selectedBrowserEntries.values.filter { !it.isDirectory }.sumOf { it.sizeBytes }

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

            // 5-Category Pill Icon Menu (Image, Video, Music, Files, Apps)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PickerCategory.entries.forEach { category ->
                    val isSelected = activeCategory == category
                    val count = when (category) {
                        PickerCategory.IMAGE -> images.size
                        PickerCategory.VIDEO -> videos.size
                        PickerCategory.MUSIC -> music.size
                        PickerCategory.FILES -> files.size
                        PickerCategory.APPS -> installedApps.size + apkFiles.size
                    }
                    val icon = when (category) {
                        PickerCategory.IMAGE -> AppIcons.Image
                        PickerCategory.VIDEO -> AppIcons.Movie
                        PickerCategory.MUSIC -> AppIcons.MusicNote
                        PickerCategory.FILES -> AppIcons.Description
                        PickerCategory.APPS -> AppIcons.Apps
                    }

                    val (pillBg, pillAccent) = when (category) {
                        PickerCategory.IMAGE -> DeepCyan to ElectricBlue
                        PickerCategory.VIDEO -> RoseLight to RoseRed
                        PickerCategory.MUSIC -> DeepViolet to ElectricViolet
                        PickerCategory.FILES -> DeepAmber to AmberGold
                        PickerCategory.APPS -> EmeraldLight to EmeraldGreen
                    }

                    CategoryPillChip(
                        label = category.label,
                        count = count,
                        icon = icon,
                        selectedColor = pillBg,
                        accentColor = pillAccent,
                        selected = isSelected,
                        modifier = Modifier.testTag("picker_tab_${category.name.lowercase()}"),
                        onClick = { activeCategory = category }
                    )
                }
            }

            val showStreamingBar = when (activeCategory) {
                PickerCategory.IMAGE -> isScanningMedia && images.isNotEmpty()
                PickerCategory.VIDEO -> isScanningMedia && videos.isNotEmpty()
                PickerCategory.MUSIC -> isScanningMedia && music.isNotEmpty()
                PickerCategory.FILES -> isScanningFiles && files.isNotEmpty()
                PickerCategory.APPS -> isScanningApps && (installedApps.isNotEmpty() || apkFiles.isNotEmpty())
            }
            if (showStreamingBar) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = colors.primary,
                    trackColor = colors.surfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Main content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeCategory) {
                    PickerCategory.IMAGE -> {
                        MediaCategoryBody(
                            categoryLabel = "Images",
                            items = images,
                            hasMediaPermission = hasMediaPermission,
                            isScanningMedia = isScanningMedia,
                            scanningText = "Scanning images...",
                            emptyIcon = AppIcons.Image,
                            emptyTitle = "No images found",
                            emptySubtitle = "Use Browse in the top right to pick files manually.",
                            onGrantClick = {
                                onExternalPermissionLaunch()
                                mediaPermissionLauncher.launch(getMediaScanPermissions())
                            },
                            onBrowseClick = onSystemBrowseClick
                        ) {
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
                                            if (isItemSelected) selectedMedia.remove(item.id) else selectedMedia[item.id] = item
                                        }
                                    )
                                }
                            }
                        }
                    }

                    PickerCategory.VIDEO -> {
                        MediaCategoryBody(
                            categoryLabel = "Videos",
                            items = videos,
                            hasMediaPermission = hasMediaPermission,
                            isScanningMedia = isScanningMedia,
                            scanningText = "Scanning videos...",
                            emptyIcon = AppIcons.Movie,
                            emptyTitle = "No videos found",
                            emptySubtitle = "Use Browse in the top right to pick videos manually.",
                            onGrantClick = {
                                onExternalPermissionLaunch()
                                mediaPermissionLauncher.launch(getMediaScanPermissions())
                            },
                            onBrowseClick = onSystemBrowseClick
                        ) {
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
                                    MediaListRow(
                                        item = item,
                                        icon = AppIcons.Movie,
                                        accentColor = colors.primary,
                                        showArtist = false,
                                        isSelected = isItemSelected,
                                        onClick = {
                                            if (isItemSelected) selectedMedia.remove(item.id) else selectedMedia[item.id] = item
                                        }
                                    )
                                }
                            }
                        }
                    }

                    PickerCategory.MUSIC -> {
                        MediaCategoryBody(
                            categoryLabel = "Music",
                            items = music,
                            hasMediaPermission = hasMediaPermission,
                            isScanningMedia = isScanningMedia,
                            scanningText = "Scanning audio...",
                            emptyIcon = AppIcons.MusicNote,
                            emptyTitle = "No audio tracks found",
                            emptySubtitle = "Use Browse in the top right to pick audio files manually.",
                            onGrantClick = {
                                onExternalPermissionLaunch()
                                mediaPermissionLauncher.launch(getMediaScanPermissions())
                            },
                            onBrowseClick = onSystemBrowseClick
                        ) {
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
                                    MediaListRow(
                                        item = item,
                                        icon = AppIcons.MusicNote,
                                        accentColor = colors.secondary,
                                        showArtist = true,
                                        isSelected = isItemSelected,
                                        onClick = {
                                            if (isItemSelected) selectedMedia.remove(item.id) else selectedMedia[item.id] = item
                                        }
                                    )
                                }
                            }
                        }
                    }

                    PickerCategory.FILES -> {
                        FilesBrowserSection(
                            hasMediaPermission = hasMediaPermission,
                            selectedEntries = selectedBrowserEntries,
                            bottomPadding = if (totalSelectedCount > 0) 96.dp else 16.dp,
                            onExternalPickerLaunch = onExternalPermissionLaunch,
                            onGrantMediaPermissionClick = {
                                onExternalPermissionLaunch()
                                mediaPermissionLauncher.launch(getMediaScanPermissions())
                            },
                            onBrowseSystemFilesClick = onSystemBrowseClick,
                            onStorageAccessGranted = onRescanApps
                        )
                    }

                    PickerCategory.APPS -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isScanningApps && installedApps.isEmpty() && apkFiles.isEmpty()) {
                                ScanningIndicator("Loading apps & APKs...")
                            } else {
                                // Apps Grid View (5 columns): Installed apps first, then APK FILES section below
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(5),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("picker_apps_grid"),
                                    contentPadding = PaddingValues(
                                        start = 12.dp,
                                        end = 12.dp,
                                        top = 6.dp,
                                        bottom = if (totalSelectedCount > 0) 96.dp else 16.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(installedApps, key = { it.id }) { app ->
                                        val isAppSelected = selectedApps.containsKey(app.id)
                                        AppGridCell(
                                            app = app,
                                            isSelected = isAppSelected,
                                            onClick = {
                                                if (isAppSelected) {
                                                    selectedApps.remove(app.id)
                                                } else {
                                                    selectedApps[app.id] = app
                                                }
                                            }
                                        )
                                    }

                                    item(
                                        key = "apk_section_header",
                                        span = { GridItemSpan(maxLineSpan) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(EmeraldLight.copy(alpha = 0.25f))
                                                    .border(1.dp, EmeraldGreen.copy(alpha = 0.6f), CircleShape)
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "APK FILES (${apkFiles.size})",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.onSurface
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(1.dp)
                                                    .background(colors.outline.copy(alpha = 0.5f))
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(colors.surfaceVariant)
                                                    .border(1.dp, colors.outline, CircleShape)
                                                    .clickable(enabled = !isScanningApps) { onRescanApps() }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                                    .testTag("rescan_apks_button"),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = if (isScanningApps) "Scanning..." else "Rescan",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.primary
                                                )
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(colors.primaryContainer)
                                                    .border(1.dp, colors.primary.copy(alpha = 0.5f), CircleShape)
                                                    .clickable { launchApkStoragePicker(null) }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                                    .testTag("scan_apk_folder_button"),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = AppIcons.FolderOpen,
                                                    contentDescription = "Scan folder for APKs",
                                                    tint = colors.primary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = "+ Folder",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.primary
                                                )
                                            }
                                        }
                                    }

                                    if (apkFiles.isEmpty()) {
                                        item(
                                            key = "apk_empty_scan_card",
                                            span = { GridItemSpan(maxLineSpan) }
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(colors.surfaceVariant)
                                                    .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                                                    .padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text(
                                                    text = if (isScanningApps) {
                                                        "Scanning internal & external storage for .apk files..."
                                                    } else {
                                                        "No .apk files found yet. On Android 11+, grant access to your Internal Storage, SD Card, or USB folder to scan all .apk files inside."
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.onSurfaceVariant
                                                )
                                                val volumeRoots = appStorageRoots.filter { it.storageVolume != null }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    volumeRoots.forEach { root ->
                                                        val chipIcon = when (root.kind) {
                                                            StorageKind.INTERNAL -> AppIcons.PhoneAndroid
                                                            StorageKind.SD_CARD -> AppIcons.SdCard
                                                            StorageKind.USB_DRIVE -> AppIcons.Usb
                                                            StorageKind.FOLDER -> AppIcons.FolderOpen
                                                        }
                                                        Row(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(colors.primaryContainer)
                                                                .border(1.dp, colors.primary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                                                .clickable { launchApkStoragePicker(root) }
                                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = chipIcon,
                                                                contentDescription = root.label,
                                                                tint = colors.primary,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                            Text(
                                                                text = "Scan ${root.label}",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = colors.primary
                                                            )
                                                        }
                                                    }
                                                    Row(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(colors.surface)
                                                            .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                                                            .clickable { launchApkStoragePicker(null) }
                                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = AppIcons.FolderOpen,
                                                            contentDescription = "Pick Folder or Drive",
                                                            tint = colors.onSurface,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                        Text(
                                                            text = "Pick Folder / Drive",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = colors.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        items(apkFiles, key = { it.id }) { apk ->
                                            val isApkSelected = selectedApps.containsKey(apk.id)
                                            AppGridCell(
                                                app = apk,
                                                isSelected = isApkSelected,
                                                onClick = {
                                                    if (isApkSelected) {
                                                        selectedApps.remove(apk.id)
                                                    } else {
                                                        selectedApps[apk.id] = apk
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
                        text = when {
                            selectedFoldersCount > 0 && totalSelectedBytes > 0L ->
                                "$selectedFoldersCount folder(s) + ${formatBytes(totalSelectedBytes)}"
                            selectedFoldersCount > 0 ->
                                "$selectedFoldersCount folder(s) selected"
                            else -> formatBytes(totalSelectedBytes)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary
                    )
                }

                TextButton(
                    onClick = {
                        selectedMedia.clear()
                        selectedApps.clear()
                        selectedBrowserEntries.clear()
                    },
                    enabled = !isPreparingSend,
                    modifier = Modifier.testTag("picker_clear_button")
                ) {
                    Text("Clear", color = colors.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = {
                        if (!isPreparingSend) {
                            isPreparingSend = true
                            scope.launch {
                                val browserFileItems = if (selectedBrowserEntries.isNotEmpty()) {
                                    DeviceBrowser.collectFileItemsForSelection(
                                        context,
                                        selectedBrowserEntries.values.toList()
                                    )
                                } else {
                                    emptyList()
                                }
                                val items = buildList {
                                    selectedMedia.values.forEach { add(it.toFileItem()) }
                                    selectedApps.values.forEach { add(it.toFileItem()) }
                                    addAll(browserFileItems)
                                }
                                isPreparingSend = false
                                if (items.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        "Selected folder has no files to send",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    onConfirmSelection(items)
                                }
                            }
                        }
                    },
                    enabled = !isPreparingSend,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("picker_send_button")
                ) {
                    if (isPreparingSend) {
                        CircularProgressIndicator(
                            color = colors.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = AppIcons.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPreparingSend) "LOADING" else "SEND",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPillChip(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedColor: Color,
    accentColor: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val bgColor = if (selected) selectedColor else selectedColor.copy(alpha = 0.20f)
    val borderColor = if (selected) Color.White.copy(alpha = 0.50f) else accentColor.copy(alpha = 0.78f)
    val iconColor = if (selected) Color.White else accentColor
    val textColor = if (selected) Color.White else colors.onSurface

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(if (selected) 2.dp else 1.5.dp, borderColor, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (selected) Color.White.copy(alpha = 0.24f)
                        else selectedColor.copy(alpha = 0.34f)
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1
                )
            }
        }
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
private fun MediaCategoryBody(
    categoryLabel: String,
    items: List<PickerMediaItem>,
    hasMediaPermission: Boolean,
    isScanningMedia: Boolean,
    scanningText: String,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
    onGrantClick: () -> Unit,
    onBrowseClick: () -> Unit,
    content: @Composable () -> Unit
) {
    when {
        !hasMediaPermission -> MediaPermissionPrompt(
            categoryLabel = categoryLabel,
            onGrantClick = onGrantClick,
            onBrowseClick = onBrowseClick
        )
        isScanningMedia && items.isEmpty() -> ScanningIndicator(scanningText)
        items.isEmpty() -> EmptyPickerState(
            icon = emptyIcon,
            title = emptyTitle,
            subtitle = emptySubtitle
        )
        else -> content()
    }
}

@Composable
private fun MediaListRow(
    item: PickerMediaItem,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    showArtist: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.14f) else colors.surfaceVariant)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else colors.outline,
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
                .background(accentColor.copy(alpha = 0.15f))
                .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
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
                if (showArtist && item.artist.isNotBlank()) {
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
                        color = accentColor
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
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        SelectionDot(
            selected = isSelected,
            activeColor = accentColor
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
        initialValue = PickerThumbnailCache.getCached("app:${app.id}"),
        key1 = app.id
    ) {
        if (value == null) {
            value = PickerThumbnailCache.loadAppIcon(context, app)
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

            if (app.isStandaloneApk) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.secondary)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "APK",
                        color = colors.onSecondary,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
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
            text = if (app.isStandaloneApk && !app.fileNameOverride.isNullOrBlank()) {
                app.fileNameOverride
            } else {
                app.appName
            },
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
            text = "Grant media access to scan $categoryLabel on your device, or use Browse in the top right.",
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
