package com.example.ui.components

import androidx.annotation.DrawableRes
import com.example.R

data class UserAvatarOption(
    val index: Int,
    @DrawableRes val drawableRes: Int,
    val label: String
)

object UserAvatarCatalog {
    val avatars: List<UserAvatarOption> = listOf(
        UserAvatarOption(0, R.drawable.img_avatar_01_1795273338577, "Cyber Fox"),
        UserAvatarOption(1, R.drawable.img_avatar_02_1795273340504, "Astro Visor"),
        UserAvatarOption(2, R.drawable.img_avatar_03_1795273339509, "Emerald Owl"),
        UserAvatarOption(3, R.drawable.img_avatar_04_1795273339997, "Shiba Tech"),
        UserAvatarOption(4, R.drawable.img_avatar_05_1795273340360, "DJ Panda"),
        UserAvatarOption(5, R.drawable.img_avatar_06_1795273356658, "Aero Bot"),
        UserAvatarOption(6, R.drawable.img_avatar_07_1795273356817, "Night Panther"),
        UserAvatarOption(7, R.drawable.img_avatar_08_1795273355581, "Red Panda"),
        UserAvatarOption(8, R.drawable.img_avatar_09_1795273356716, "Prism Phoenix"),
        UserAvatarOption(9, R.drawable.img_avatar_10_1795273358963, "Pilot Cat")
    )

    fun getDrawableRes(index: Int): Int {
        return avatars.getOrElse(index) { avatars.first() }.drawableRes
    }
}
