package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.example.ui.theme.LocalUseMaterialDesignUi

@Composable
fun FilesBrowserSection(
    hasMediaPermission: Boolean,
    showHiddenFolders: Boolean = false,
    selectedEntries: SnapshotStateMap<String, BrowserEntry>,
    bottomPadding: Dp,
    onExternalPermissionLaunch: () -> Unit = {},
    onAllFilesPermissionChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = MaterialTheme.colorScheme

    var hasAllFilesAccess by remember { mutableStateOf(DeviceBrowser.hasAllFilesAccess(context)) }
    var permissionReloadTick by remember { mutableIntStateOf(0) }
    var pendingOpenExternal by remember { mutableStateOf(false) }

    val sessionTreeUris = remember { mutableStateListOf<Uri>() }
    var storageRoots by remember { mutableStateOf<List<BrowserStorageRoot>>(emptyList()) }
    var activeRootId by remember { mutableStateOf<String?>(null) }
    val breadcrumbs = remember { mutableStateListOf<BrowserPathCrumb>() }
    var entries by remember { mutableStateOf<List<BrowserEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun openRoot(root: BrowserStorageRoot): Boolean {
        val crumb = DeviceBrowser.initialCrumbFor(root) ?: return false
        activeRootId = root.id
        breadcrumbs.clear()
        breadcrumbs.add(crumb)
        return true
    }

    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = DeviceBrowser.hasAllFilesAccess(context)
        val changed = granted != hasAllFilesAccess
        hasAllFilesAccess = granted
        permissionReloadTick++
        if (changed) {
            onAllFilesPermissionChanged()
        }
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        val granted = DeviceBrowser.hasAllFilesAccess(context)
        val changed = granted != hasAllFilesAccess
        hasAllFilesAccess = granted
        permissionReloadTick++
        if (changed || granted) {
            onAllFilesPermissionChanged()
        }
    }

    val safVolumeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val treeUri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            if (!sessionTreeUris.contains(treeUri)) {
                sessionTreeUris.add(treeUri)
            }
            activeRootId = "saf:$treeUri"
            breadcrumbs.clear()
            permissionReloadTick++
        }
    }

    fun launchAllFilesPermission() {
        onExternalPermissionLaunch()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", context.packageName, null)
            )
            val launched = try {
                allFilesSettingsLauncher.launch(appIntent)
                true
            } catch (_: Exception) {
                false
            }
            if (!launched) {
                try {
                    allFilesSettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                    Toast.makeText(context, "Unable to open All Files Access settings", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            legacyStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = DeviceBrowser.hasAllFilesAccess(context)
                if (nowGranted != hasAllFilesAccess) {
                    hasAllFilesAccess = nowGranted
                    permissionReloadTick++
                    onAllFilesPermissionChanged()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasMediaPermission, hasAllFilesAccess, permissionReloadTick, sessionTreeUris.size) {
        val discovered = DeviceBrowser.discoverStorageRoots(context, sessionTreeUris.toList())
        storageRoots = discovered

        if (pendingOpenExternal) {
            val readableExternal = discovered.firstOrNull {
                it.kind != StorageKind.INTERNAL && it.isDirectlyReadable
            }
            if (readableExternal != null && openRoot(readableExternal)) {
                pendingOpenExternal = false
                return@LaunchedEffect
            }
            pendingOpenExternal = false
        }

        val safRequestedRoot = activeRootId?.takeIf { it.startsWith("saf:") }
            ?.let { targetId -> discovered.firstOrNull { it.id == targetId && it.isDirectlyReadable } }
        if (safRequestedRoot != null && breadcrumbs.isEmpty()) {
            openRoot(safRequestedRoot)
            return@LaunchedEffect
        }

        val currentRoot = activeRootId?.let { id -> discovered.firstOrNull { it.id == id } }
        if (breadcrumbs.isEmpty()) {
            val defaultRoot = discovered.firstOrNull { it.kind == StorageKind.INTERNAL && it.isDirectlyReadable }
                ?: discovered.firstOrNull { it.isDirectlyReadable }
            if (defaultRoot != null) {
                openRoot(defaultRoot)
            } else {
                isLoading = false
            }
        } else if (currentRoot != null && currentRoot.isDirectlyReadable && breadcrumbs.size == 1) {
            // Re-seed root crumb if root became directly readable after permission grant
            val currentCrumb = breadcrumbs.firstOrNull()
            val refreshedCrumb = DeviceBrowser.initialCrumbFor(currentRoot)
            if (refreshedCrumb != null && refreshedCrumb != currentCrumb) {
                breadcrumbs[0] = refreshedCrumb
            }
        }
    }

    val currentCrumb = breadcrumbs.lastOrNull()
    LaunchedEffect(currentCrumb, hasMediaPermission, hasAllFilesAccess, showHiddenFolders, permissionReloadTick) {
        if (currentCrumb == null) {
            entries = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        entries = DeviceBrowser.listDirectory(context, currentCrumb, showHidden = showHiddenFolders)
        isLoading = false
    }

    BackHandler(enabled = breadcrumbs.size > 1) {
        if (breadcrumbs.size > 1) {
            breadcrumbs.removeAt(breadcrumbs.lastIndex)
        }
    }

    val internalRoot = storageRoots.firstOrNull { it.kind == StorageKind.INTERNAL }
    val externalRoots = storageRoots.filter { it.kind != StorageKind.INTERNAL }
    val primaryExternalRoot = externalRoots.firstOrNull { it.isDirectlyReadable } ?: externalRoots.firstOrNull()
    val extraExternalRoots = if (primaryExternalRoot != null) {
        externalRoots.filter { it.id != primaryExternalRoot.id && it.isDirectlyReadable }
    } else {
        emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("files_browser_section")
    ) {
        // Equal-distance Internal Storage & External Storage buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StorageRootChip(
                label = "Internal Storage",
                selected = internalRoot != null && activeRootId == internalRoot.id,
                modifier = Modifier
                    .weight(1f)
                    .testTag("storage_root_internal"),
                onClick = {
                    if (internalRoot != null && openRoot(internalRoot)) {
                        pendingOpenExternal = false
                    } else if (!hasAllFilesAccess) {
                        launchAllFilesPermission()
                    }
                }
            )
            StorageRootChip(
                label = primaryExternalRoot?.label ?: "External Storage",
                selected = primaryExternalRoot != null && activeRootId == primaryExternalRoot.id,
                modifier = Modifier
                    .weight(1f)
                    .testTag("storage_root_external"),
                onClick = {
                    when {
                        primaryExternalRoot != null && primaryExternalRoot.isDirectlyReadable -> {
                            openRoot(primaryExternalRoot)
                        }
                        primaryExternalRoot != null && !hasAllFilesAccess -> {
                            pendingOpenExternal = true
                            launchAllFilesPermission()
                        }
                        primaryExternalRoot?.storageVolume != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                            onExternalPermissionLaunch()
                            safVolumeLauncher.launch(primaryExternalRoot.storageVolume.createOpenDocumentTreeIntent())
                        }
                        else -> {
                            Toast.makeText(context, "No external storage connected", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        if (extraExternalRoots.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .testTag("extra_storage_roots_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                extraExternalRoots.forEach { extraRoot ->
                    StorageRootChip(
                        label = extraRoot.label,
                        selected = activeRootId == extraRoot.id,
                        modifier = Modifier.testTag("storage_root_${extraRoot.kind.name.lowercase()}"),
                        onClick = { openRoot(extraRoot) }
                    )
                }
            }
        }

        if (!hasAllFilesAccess) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AmberGold.copy(alpha = 0.14f))
                    .border(1.dp, AmberGold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("all_files_permission_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Allow All Files Permission",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Browse videos, music, APKs & all file types",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = { launchAllFilesPermission() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberGold,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("grant_all_files_button")
                ) {
                    Text(
                        text = "Allow",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Breadcrumbs + Select All toggle for all visible files and folders
        if (breadcrumbs.isNotEmpty()) {
            val allSelectedInDir = entries.isNotEmpty() && entries.all { selectedEntries.containsKey(it.id) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (breadcrumbs.size > 1) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 36.dp)
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

                if (entries.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (allSelectedInDir) colors.primary.copy(alpha = 0.16f) else colors.surfaceVariant)
                            .border(
                                width = 1.dp,
                                color = if (allSelectedInDir) colors.primary else colors.outline,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                if (allSelectedInDir) {
                                    entries.forEach { selectedEntries.remove(it.id) }
                                } else {
                                    entries.forEach { selectedEntries[it.id] = it }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("browser_select_all_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (allSelectedInDir) "Unselect" else "Select all",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (allSelectedInDir) colors.primary else colors.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Directory contents: all folders and files, every item selectable
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = AmberGold,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }

                entries.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No files",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
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
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isMaterialUi = LocalUseMaterialDesignUi.current
    val bgColor = when {
        isMaterialUi && selected -> colors.primaryContainer
        isMaterialUi -> colors.surfaceVariant
        selected -> DeepAmber
        else -> colors.surfaceVariant
    }
    val borderColor = when {
        isMaterialUi && selected -> colors.primary
        isMaterialUi -> colors.outline.copy(alpha = 0.4f)
        selected -> AmberGold
        else -> colors.outline
    }
    val contentColor = when {
        isMaterialUi && selected -> colors.onPrimaryContainer
        isMaterialUi -> colors.onSurface
        selected -> Color.White
        else -> colors.onSurface
    }

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(if (isMaterialUi) 16.dp else 14.dp))
            .background(bgColor)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(if (isMaterialUi) 16.dp else 14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
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
    val isMaterialUi = LocalUseMaterialDesignUi.current
    val accentColor = when {
        isMaterialUi && entry.isDirectory -> colors.tertiary
        isMaterialUi -> colors.primary
        entry.isDirectory -> AmberGold
        else -> colors.primary
    }
    val icon = if (entry.isDirectory) {
        AppIcons.FolderOpen
    } else {
        getFileIcon(entry.mimeType, entry.name)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (isMaterialUi) 18.dp else 16.dp))
            .background(
                if (isSelected) accentColor.copy(alpha = 0.14f) else colors.surfaceVariant
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else colors.outline.copy(alpha = if (isMaterialUi) 0.35f else 1f),
                shape = RoundedCornerShape(if (isMaterialUi) 18.dp else 16.dp)
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
                contentDescription = if (entry.isDirectory) "Folder" else "File",
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
            val isHiddenEntry = entry.name.startsWith(".")
            val hiddenPrefix = if (isHiddenEntry) "Hidden • " else ""
            Text(
                text = if (entry.isDirectory) {
                    val countLabel = entry.childCount?.let { "$it items" }.orEmpty()
                    if (isSelected) {
                        if (countLabel.isNotEmpty()) "${hiddenPrefix}${countLabel} • Selected" else "${hiddenPrefix}Selected"
                    } else {
                        if (countLabel.isNotEmpty()) "${hiddenPrefix}${countLabel}" else "${hiddenPrefix}Folder"
                    }
                } else {
                    "${hiddenPrefix}${formatBytes(entry.sizeBytes)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected && entry.isDirectory) accentColor else colors.onSurfaceVariant,
                maxLines = 1
            )
        }

        // Dedicated 48dp selection target so every folder and file is directly selectable
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
                    .background(if (isSelected) accentColor else colors.surface)
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) accentColor else colors.outline,
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
