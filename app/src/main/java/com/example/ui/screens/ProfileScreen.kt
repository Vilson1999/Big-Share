package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppIcons
import com.example.ui.components.AppTopBar
import com.example.ui.components.UserAvatarCatalog
import com.example.ui.components.atmosphericGlow

@Composable
fun ProfileScreen(
    initialName: String,
    initialAvatarIndex: Int,
    isFirstLaunch: Boolean,
    onBackClick: (() -> Unit)? = null,
    onSaveProfile: (name: String, avatarIndex: Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var nameInput by remember(initialName) { mutableStateOf(initialName) }
    var selectedAvatarIndex by remember(initialAvatarIndex) { mutableIntStateOf(initialAvatarIndex) }

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
                title = if (isFirstLaunch) "Create Profile" else "Profile",
                subtitle = "Choose your photo and name",
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

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Bottom Save / Continue Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
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
        }
    }
}
