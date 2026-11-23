package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.formatBytes
import com.example.picker.BrowserEntry
import com.example.picker.BrowserPathCrumb
import com.example.picker.BrowserStorageRoot
import com.example.picker.DeviceBrowser
import com.example.ui.components.AppIcons
import com.example.ui.components.getFileIcon
import com.example.ui.theme.AmberGold
import com.example.ui.theme.DeepAmber

@Composable
fun FilesBrowserSection(
    hasMediaPermission: Boolean,
    selectedEntries: SnapshotStateMap<String, BrowserEntry>,
    bottomPadding: Dp
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    var storageRoots by remember { mutableStateOf<List<BrowserStorageRoot>>(emptyList()) }
    var activeRootId by remember { mutableStateOf<String?>(null) }
    val breadcrumbs = remember { mutableStateListOf<BrowserPathCrumb>() }
    var entries by remember { mutableStateOf<List<BrowserEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun openRoot(root: BrowserStorageRoot) {
        val crumb = DeviceBrowser.initialCrumbFor(root) ?: return
        activeRootId = root.id
        breadcrumbs.clear()
        breadcrumbs.add(crumb)
    }

    LaunchedEffect(hasMediaPermission) {
        val discovered = DeviceBrowser.discoverStorageRoots(context).filter { it.isDirectlyReadable }
        storageRoots = discovered
        if (breadcrumbs.isEmpty()) {
            val defaultRoot = discovered.firstOrNull()
            if (defaultRoot != null) {
                openRoot(defaultRoot)
            } else {
                isLoading = false
            }
        }
    }

    val currentCrumb = breadcrumbs.lastOrNull()
    LaunchedEffect(currentCrumb, hasMediaPermission) {
        if (currentCrumb == null) {
            entries = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        entries = DeviceBrowser.listDirectory(context, currentCrumb)
        isLoading = false
    }

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
        // Storage switcher (shown only when multiple readable storage volumes exist, without USB/Folder icons)
        if (storageRoots.size > 1) {
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
                    StorageRootChip(
                        label = root.label,
                        selected = isSelected,
                        modifier = Modifier.testTag("storage_root_${root.kind.name.lowercase()}"),
                        onClick = { openRoot(root) }
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
    val bgColor = if (selected) DeepAmber else colors.surfaceVariant
    val borderColor = if (selected) AmberGold else colors.outline
    val contentColor = if (selected) Color.White else colors.onSurface

    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
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
