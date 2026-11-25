package com.example.ui.screens

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppThemeMode
import com.example.ui.components.AppIcons
import com.example.ui.components.AppTopBar
import com.example.ui.components.UserAvatarCatalog
import com.example.ui.components.atmosphericGlow

@Composable
fun ProfileScreen(
    initialName: String,
    initialAvatarIndex: Int,
    isFirstLaunch: Boolean,
    themeMode: AppThemeMode = AppThemeMode.DEFAULT,
    receiveLocationLabel: String = "Downloads/Max Share",
    hasCustomReceiveLocation: Boolean = false,
    runInBackground: Boolean = true,
    onRunInBackgroundChange: (Boolean) -> Unit = {},
    onThemeModeSelect: (AppThemeMode) -> Unit = {},
    onReceiveLocationSelected: (Uri?) -> Unit = {},
    onExternalModalLaunch: () -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    onSaveProfile: (name: String, avatarIndex: Int) -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var nameInput by remember(initialName) { mutableStateOf(initialName) }
    var selectedAvatarIndex by remember(initialAvatarIndex) { mutableIntStateOf(initialAvatarIndex) }

    var themeFolderExpanded by remember { mutableStateOf(false) }
    var storageFolderExpanded by remember { mutableStateOf(false) }

    val appVersionLabel = remember(context) {
        try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            "Version ${info.versionName ?: "1.0"}"
        } catch (_: Exception) {
            "Version 1.0"
        }
    }

    val storageTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            onReceiveLocationSelected(uri)
        }
    }

    val selectedAvatar = UserAvatarCatalog.avatars.getOrElse(selectedAvatarIndex) {
        UserAvatarCatalog.avatars.first()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .atmosphericGlow(colors.primary, colors.secondary)
            .testTag("profile_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppTopBar(
                title = if (isFirstLaunch) "Create Profile" else "Me",
                subtitle = if (isFirstLaunch) {
                    "Choose your photo and name"
                } else {
                    "Profile & settings"
                },
                onBackClick = if (isFirstLaunch) null else onBackClick
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Selected Avatar Preview
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .shadow(
                            elevation = 18.dp,
                            shape = CircleShape,
                            ambientColor = colors.primary.copy(alpha = 0.35f),
                            spotColor = colors.primary.copy(alpha = 0.45f)
                        )
                        .clip(CircleShape)
                        .border(
                            width = 3.dp,
                            brush = Brush.linearGradient(listOf(colors.primary, colors.secondary)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = selectedAvatar.drawableRes),
                        contentDescription = selectedAvatar.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // User Name Input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "YOUR NAME",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "Enter your name",
                                color = colors.outlineVariant
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.onSurface,
                            unfocusedTextColor = colors.onSurface,
                            focusedContainerColor = colors.surfaceVariant,
                            unfocusedContainerColor = colors.surfaceVariant,
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.outline,
                            cursorColor = colors.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_input")
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // 10 Avatar Photos Grid (2 rows of 5)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECT PHOTO",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = selectedAvatar.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val rows = UserAvatarCatalog.avatars.chunked(5)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rows.forEach { rowAvatars ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowAvatars.forEach { option ->
                                val isSelected = option.index == selectedAvatarIndex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.5.dp,
                                            color = if (isSelected) colors.primary else colors.outline,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedAvatarIndex = option.index }
                                        .testTag("profile_avatar_${option.index}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = option.drawableRes),
                                        contentDescription = option.label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(2.dp)
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(colors.primary)
                                                .border(1.5.dp, colors.background, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.Check,
                                                contentDescription = "Selected",
                                                tint = colors.onPrimary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Settings
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "SETTINGS",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    // Run in Background Toggle (outside folders; hidden on first launch)
                    if (!isFirstLaunch) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceVariant)
                                .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                                .heightIn(min = 64.dp)
                                .clickable { onRunInBackgroundChange(!runInBackground) }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("run_in_background_card"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.primary.copy(alpha = 0.14f))
                                    .border(1.dp, colors.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Settings,
                                    contentDescription = "Run in background",
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Text(
                                    text = "Run in background",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = if (runInBackground) {
                                        "Runs up to 2 min after minimizing"
                                    } else {
                                        "Stops immediately when minimized"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = runInBackground,
                                onCheckedChange = onRunInBackgroundChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onPrimary,
                                    checkedTrackColor = colors.primary
                                ),
                                modifier = Modifier.testTag("run_in_background_switch")
                            )
                        }
                    }

                    // Folder 1: Theme
                    SettingFolderCard(
                        title = "Theme",
                        summary = themeMode.label,
                        icon = AppIcons.Palette,
                        expanded = themeFolderExpanded,
                        folderTag = "setting_folder_theme",
                        onToggle = { themeFolderExpanded = !themeFolderExpanded }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.outline.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                .padding(5.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AppThemeMode.entries.forEach { mode ->
                                val isSelected = mode == themeMode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) colors.primaryContainer else Color.Transparent
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) colors.primary else colors.outline.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable(
                                            role = Role.RadioButton,
                                            onClickLabel = "Select ${mode.label} theme"
                                        ) {
                                            onThemeModeSelect(mode)
                                        }
                                        .semantics {
                                            selected = isSelected
                                        }
                                        .testTag("theme_option_${mode.key}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = AppIcons.Check,
                                                contentDescription = null,
                                                tint = colors.onPrimaryContainer,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                        }
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) {
                                                colors.onPrimaryContainer
                                            } else {
                                                colors.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Folder 2: Storage Location (hidden on first launch)
                    if (!isFirstLaunch) {
                        SettingFolderCard(
                            title = "Storage Location",
                            summary = receiveLocationLabel,
                            icon = AppIcons.FolderOpen,
                            expanded = storageFolderExpanded,
                            folderTag = "setting_folder_storage",
                            onToggle = { storageFolderExpanded = !storageFolderExpanded }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Received files save to",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                                Text(
                                    text = receiveLocationLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("storage_location_label")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            onExternalModalLaunch()
                                            storageTreeLauncher.launch(null)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.primaryContainer,
                                            contentColor = colors.onPrimaryContainer
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("choose_storage_location_button")
                                    ) {
                                        Text(
                                            text = if (hasCustomReceiveLocation) "Change Folder" else "Choose Folder",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    if (hasCustomReceiveLocation) {
                                        OutlinedButton(
                                            onClick = { onReceiveLocationSelected(null) },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = colors.onSurfaceVariant
                                            ),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("reset_storage_location_button")
                                        ) {
                                            Text(
                                                text = "Default",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Save / Continue Button
                Button(
                    onClick = {
                        val finalName = nameInput.trim().ifEmpty { initialName }
                        onSaveProfile(finalName, selectedAvatarIndex)
                    },
                    enabled = nameInput.trim().isNotEmpty(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                        disabledContainerColor = colors.surfaceVariant,
                        disabledContentColor = colors.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("profile_save_button")
                ) {
                    Text(
                        text = if (isFirstLaunch) "CONTINUE" else "SAVE PROFILE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // App Icon, App Name, App Version, and Developer Name last below inside Me (hidden on first launch)
                if (!isFirstLaunch) {
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceVariant.copy(alpha = 0.72f))
                            .border(1.dp, colors.outline.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("me_app_info_footer"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF0B1120), Color(0xFF0F172A))
                                    )
                                )
                                .border(1.dp, colors.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_art),
                                contentDescription = stringResource(R.string.app_name),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("me_app_name")
                            )
                            Text(
                                text = appVersionLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("me_app_version")
                            )
                            Text(
                                text = stringResource(R.string.developer_credit),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.testTag("developer_name_text")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SettingFolderCard(
    title: String,
    summary: String,
    icon: ImageVector,
    expanded: Boolean,
    folderTag: String,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { onToggle() }
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag(folderTag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primary.copy(alpha = 0.14f))
                    .border(1.dp, colors.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 90f else 0f)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}
