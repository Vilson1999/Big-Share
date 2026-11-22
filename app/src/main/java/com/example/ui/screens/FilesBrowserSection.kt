package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.formatBytes
import com.example.picker.BrowserEntry
import com.example.picker.BrowserPathCrumb
import com.example.picker.BrowserStorageRoot
import com.example.picker.DeviceBrowser
import com.example.picker.StorageKind
import com.example.ui.components.AppIcons
import com.example.ui.components.getFileIcon
import com.example.ui.theme.AmberGold
import com.example.ui.theme.DeepAmber
import kotlinx.coroutines.launch

@Composable
fun FilesBrowserSection(
    hasMediaPermission: Boolean,
    selectedEntries: SnapshotStateMap<String, BrowserEntry>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onExternalPickerLaunch: () -> Unit,
    onGrantMediaPermissionClick: () -> Unit,
    onBrowseSystemFilesClick: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var storageRoots by remember { mutableStateOf<List<BrowserStorageRoot>>(emptyList()) }
    val sessionTreeUris = remember { mutableStateListOf<Uri>() }
    var activeRootId by remember { mutableStateOf<String?>(null) }
    val breadcrumbs = remember { mutableStateListOf<BrowserPathCrumb>() }
    var entries by remember { mutableStateOf<List<BrowserEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }

    fun openRoot(root: BrowserStorageRoot) {
        val crumb = DeviceBrowser.initialCrumbFor(root) ?: return
        activeRootId = root.id
        breadcrumbs.clear()
        breadcrumbs.add(crumb)
    }

    fun handlePickedTreeUri(uri: Uri?) {
        if (uri == null) return
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
        if (!sessionTreeUris.contains(uri)) {
            sessionTreeUris.add(uri)
        }
        scope.launch {
            val updated = DeviceBrowser.discoverStorageRoots(context, sessionTreeUris.toList())
            storageRoots = updated
            val matched = updated.firstOrNull { it.treeUri == uri }
            if (matched != null) {
                openRoot(matched)
            }
        }
    }

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        handlePickedTreeUri(uri)
    }

    val volumeAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handlePickedTreeUri(result.data?.data)
        }
    }

    fun launchTreePickerForRoot(root: BrowserStorageRoot?) {
        onExternalPickerLaunch()
        val vol = root?.storageVolume
        if (vol != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                @Suppress("DEPRECATION")
                val intent = vol.createOpenDocumentTreeIntent()
                volumeAccessLauncher.launch(intent)
                return
            } catch (_: Exception) {}
        }
        openDocumentTreeLauncher.launch(null)
    }

    // Discover internal, SD card, USB OTG, and persisted SAF storage roots
    LaunchedEffect(hasMediaPermission, refreshTrigger, sessionTreeUris.size) {
        val discovered = DeviceBrowser.discoverStorageRoots(context, sessionTreeUris.toList())
        storageRoots = discovered
        if (breadcrumbs.isEmpty()) {
            val defaultRoot = discovered.firstOrNull { it.isDirectlyReadable } ?: discovered.firstOrNull()
            if (defaultRoot != null && defaultRoot.isDirectlyReadable) {
                openRoot(defaultRoot)
            } else {
                activeRootId = defaultRoot?.id
                isLoading = false
            }
        }
    }

    // List current folder contents whenever active breadcrumb changes
    val currentCrumb = breadcrumbs.lastOrNull()
    LaunchedEffect(currentCrumb, refreshTrigger) {
        if (currentCrumb == null) {
            entries = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        entries = DeviceBrowser.listDirectory(context, currentCrumb)
        isLoading = false
    }

    // Pressing system Back inside subfolders navigates up to parent folder
    BackHandler(enabled = breadcrumbs.size > 1) {
        if (breadcrumbs.size > 1) {
            breadcrumbs.removeAt(breadcrumbs.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("files_browser_section")
    ) {
        // Storage volumes bar: Internal Storage, SD Card, USB / Pendrive, Add Storage
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            storageRoots.forEach { root ->
                val isSelected = activeRootId == root.id
                val icon = when (root.kind) {
                    StorageKind.INTERNAL -> AppIcons.PhoneAndroid
                    StorageKind.SD_CARD -> AppIcons.SdCard
                    StorageKind.USB_DRIVE -> AppIcons.Usb
                    StorageKind.FOLDER -> AppIcons.FolderOpen
                }
                StorageRootChip(
                    label = if (root.isDirectlyReadable) root.label else "${root.label} (Tap to allow)",
                    icon = icon,
                    selected = isSelected,
                    modifier = Modifier.testTag("storage_root_${root.kind.name.lowercase()}"),
                    onClick = {
                        if (root.isDirectlyReadable) {
                            openRoot(root)
                        } else {
                            launchTreePickerForRoot(root)
                        }
                    }
                )
            }

            // Button to mount any USB Pendrive, SD card, or folder via system tree picker
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
                    .clickable { launchTreePickerForRoot(null) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("add_external_storage_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Usb,
                    contentDescription = "Add USB or SD storage",
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "+ USB / Folder",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    maxLines = 1
                )
            }
        }

        // Clickable Breadcrumbs + Up button
        if (breadcrumbs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (breadcrumbs.size > 1) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceVariant)
                            .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                            .clickable {
                                if (breadcrumbs.size > 1) {
                                    breadcrumbs.removeAt(breadcrumbs.lastIndex)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("browser_up_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = "Parent folder",
                            tint = colors.onSurface,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Up",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    breadcrumbs.forEachIndexed { index, crumb ->
                        val isLast = index == breadcrumbs.lastIndex
                        Text(
                            text = crumb.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                            color = if (isLast) AmberGold else colors.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isLast) {
                                    while (breadcrumbs.size > index + 1) {
                                        breadcrumbs.removeAt(breadcrumbs.lastIndex)
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                        if (!isLast) {
                            Icon(
                                imageVector = AppIcons.ChevronRight,
                                contentDescription = null,
                                tint = colors.outlineVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Directory contents
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val activeRoot = storageRoots.firstOrNull { it.id == activeRootId }
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = AmberGold,
                            modifier = Modifier.size(34.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading folder...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                activeRoot != null && !activeRoot.isDirectlyReadable -> {
                    BrowserActionState(
                        icon = if (activeRoot.kind == StorageKind.USB_DRIVE) AppIcons.Usb else AppIcons.SdCard,
                        title = "Allow Access to ${activeRoot.label}",
                        subtitle = "Android requires folder permission to browse this external drive and its folders.",
                        primaryLabel = "Grant Access",
                        onPrimaryClick = { launchTreePickerForRoot(activeRoot) },
                        secondaryLabel = "Browse",
                        onSecondaryClick = onBrowseSystemFilesClick
                    )
                }

                entries.isEmpty() -> {
                    if (!hasMediaPermission && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        BrowserActionState(
                            icon = AppIcons.FolderOpen,
                            title = "No files visible here",
                            subtitle = "Allow storage permission or open this folder with the system picker.",
                            primaryLabel = "Allow Storage",
                            onPrimaryClick = {
                                onGrantMediaPermissionClick()
                                refreshTrigger++
                            },
                            secondaryLabel = "Open Folder / USB",
                            onSecondaryClick = { launchTreePickerForRoot(activeRoot) }
                        )
                    } else {
                        BrowserActionState(
                            icon = AppIcons.FolderOpen,
                            title = "Folder is empty",
                            subtitle = "No readable files or subfolders here. Open another storage or pick a folder with USB / Folder.",
                            primaryLabel = "Open Folder / USB",
                            onPrimaryClick = { launchTreePickerForRoot(activeRoot) },
                            secondaryLabel = "Browse Files",
                            onSecondaryClick = onBrowseSystemFilesClick
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("picker_files_list"),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = bottomPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            val isSelected = selectedEntries.containsKey(entry.id)
                            BrowserEntryRow(
                                entry = entry,
                                isSelected = isSelected,
                                onRowClick = {
                                    if (entry.isDirectory) {
                                        val nextCrumb = when {
                                            entry.file != null -> BrowserPathCrumb(
                                                label = entry.name,
                                                fileDir = entry.file
                                            )
                                            entry.treeUri != null && entry.documentId != null -> BrowserPathCrumb(
                                                label = entry.name,
                                                treeUri = entry.treeUri,
                                                documentId = entry.documentId
                                            )
                                            else -> null
                                        }
                                        if (nextCrumb != null) {
                                            breadcrumbs.add(nextCrumb)
                                        }
                                    } else {
                                        if (isSelected) {
                                            selectedEntries.remove(entry.id)
                                        } else {
                                            selectedEntries[entry.id] = entry
                                        }
                                    }
                                },
                                onToggleSelect = {
                                    if (isSelected) {
                                        selectedEntries.remove(entry.id)
                                    } else {
                                        selectedEntries[entry.id] = entry
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

@Composable
private fun StorageRootChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val bgColor = if (selected) DeepAmber else colors.surfaceVariant
    val borderColor = if (selected) AmberGold else colors.outline
    val contentColor = if (selected) Color.White else colors.onSurface

    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Color.White else AmberGold,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun BrowserEntryRow(
    entry: BrowserEntry,
    isSelected: Boolean,
    onRowClick: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val accentColor = if (entry.isDirectory) AmberGold else colors.primary
    val icon = if (entry.isDirectory) {
        AppIcons.FolderOpen
    } else {
        getFileIcon(entry.mimeType, entry.name)
    }

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
            .clickable { onRowClick() }
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
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
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 14.sp,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (entry.isDirectory) {
                    val countLabel = entry.childCount?.let { "$it items • " }.orEmpty()
                    if (isSelected) "${countLabel}Folder selected" else "${countLabel}Tap to open"
                } else {
                    formatBytes(entry.sizeBytes)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected && entry.isDirectory) accentColor else colors.onSurfaceVariant,
                maxLines = 1
            )
        }

        // Dedicated 48dp selection target so users can select an entire folder or file cleanly
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable { onToggleSelect() }
                .testTag("select_entry_${entry.name}"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accentColor else Color.Black.copy(alpha = 0.38f))
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) accentColor else Color.White.copy(alpha = 0.85f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = AppIcons.Check,
                        contentDescription = "Selected",
                        tint = colors.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserActionState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String,
    onSecondaryClick: () -> Unit
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
                .size(64.dp)
                .clip(CircleShape)
                .background(AmberGold.copy(alpha = 0.14f))
                .border(1.dp, AmberGold.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AmberGold,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onPrimaryClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Text(primaryLabel, style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = onSecondaryClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surfaceVariant,
                    contentColor = colors.onSurface
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Text(secondaryLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
