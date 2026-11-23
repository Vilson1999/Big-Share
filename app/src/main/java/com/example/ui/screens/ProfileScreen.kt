package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onThemeModeSelect: (AppThemeMode) -> Unit = {},
    onReceiveLocationSelected: (Uri?) -> Unit = {},
    onExternalModalLaunch: () -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    onSaveProfile: (name: String, avatarIndex: Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var nameInput by remember(initialName) { mutableStateOf(initialName) }
    var selectedAvatarIndex by remember(initialAvatarIndex) { mutableIntStateOf(initialAvatarIndex) }

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
                title = if (isFirstLaunch) "Create Profile" else "Profile & Settings",
                subtitle = if (isFirstLaunch) {
                    "Choose your photo, name, and preferences"
                } else {
                    "Profile, storage & theme settings"
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
                        .size(116.dp)
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

                Spacer(modifier = Modifier.height(22.dp))

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

                Spacer(modifier = Modifier.height(24.dp))

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

                Spacer(modifier = Modifier.height(26.dp))

                // Storage Location Setting
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "RECEIVE STORAGE LOCATION",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceVariant)
                            .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.primary.copy(alpha = 0.14f))
                                    .border(
                                        1.dp,
                                        colors.primary.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.FolderOpen,
                                    contentDescription = "Receive storage folder",
                                    tint = colors.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Received files save to",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = receiveLocationLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("storage_location_label")
                                )
                            }
                        }

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

                Spacer(modifier = Modifier.height(22.dp))

                // Theme Setting (Default, Dark, Light)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "THEME",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceVariant)
                            .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            val isSelected = mode == themeMode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) colors.primaryContainer else colors.surface
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) colors.primary else colors.outline.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
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
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = AppIcons.Check,
                                            contentDescription = null,
                                            tint = colors.onPrimaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
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

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Bottom Save / Continue Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
            ) {
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
            }

            // Developer name at bottom last of user menu
            Text(
                text = androidx.compose.ui.res.stringResource(com.example.R.string.developer_credit),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .testTag("developer_name_text")
            )
        }
    }
}

