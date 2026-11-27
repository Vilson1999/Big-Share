package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalUseMaterialIcons

object AppIcons {
    val Send: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialSend else ClassicSend

    val Share: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialShare else ClassicShare

    val FileDownload: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialFileDownload else ClassicFileDownload

    val ArrowBack: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialArrowBack else ClassicArrowBack

    val Person: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialPerson else ClassicPerson

    val Close: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialClose else ClassicClose

    val Check: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialCheck else ClassicCheck

    val Cancel: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialCancel else ClassicCancel

    val Image: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialImage else ClassicImage

    val Movie: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialMovie else ClassicMovie

    val MusicNote: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialMusicNote else ClassicMusicNote

    val Apps: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialApps else ClassicApps

    val FolderOpen: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialFolderOpen else ClassicFolderOpen

    val FolderZip: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialFolderZip else ClassicFolderZip

    val Description: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialDescription else ClassicDescription

    val InsertDriveFile: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialInsertDriveFile else ClassicInsertDriveFile

    val SdCard: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialSdCard else ClassicSdCard

    val ChevronRight: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialChevronRight else ClassicChevronRight

    val Palette: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialPalette else ClassicPalette

    val Visibility: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialVisibility else ClassicVisibility

    val Style: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialStyle else ClassicStyle

    val Widgets: ImageVector
        @Composable get() = if (LocalUseMaterialIcons.current) MaterialWidgets else ClassicWidgets

    // Material Design Rounded Icons (M3 style)
    private val MaterialSend: ImageVector by lazy {
        vectorIcon(
            name = "MaterialSend",
            pathData = "M3.4,20.4l17.45,-7.48c0.81,-0.35 0.81,-1.49 0,-1.84L3.4,3.6c-0.66,-0.29 -1.39,0.2 -1.39,0.91L2,9.12c0,0.5 0.37,0.93 0.87,0.99L17,12 2.87,13.88c-0.5,0.07 -0.87,0.5 -0.87,1l0.01,4.61c0,0.71 0.73,1.2 1.39,0.91z",
            autoMirror = true
        )
    }

    private val MaterialShare: ImageVector by lazy {
        vectorIcon(
            name = "MaterialShare",
            pathData = "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92 1.61,0 2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z"
        )
    }

    private val MaterialFileDownload: ImageVector by lazy {
        vectorIcon(
            name = "MaterialFileDownload",
            pathData = "M16.59,9H15V4c0,-0.55 -0.45,-1 -1,-1h-4c-0.55,0 -1,0.45 -1,1v5H7.41c-0.89,0 -1.34,1.08 -0.71,1.71l4.59,4.59c0.39,0.39 1.02,0.39 1.41,0l4.59,-4.59c0.63,-0.63 0.19,-1.71 -0.7,-1.71zM5,19c0,0.55 0.45,1 1,1h12c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H6c-0.55,0 -1,0.45 -1,1z"
        )
    }

    private val MaterialArrowBack: ImageVector by lazy {
        vectorIcon(
            name = "MaterialArrowBack",
            pathData = "M19,11H7.83l4.88,-4.88c0.39,-0.39 0.39,-1.03 0,-1.42 -0.39,-0.39 -1.02,-0.39 -1.41,0l-6.59,6.59c-0.39,0.39 -0.39,1.02 0,1.41l6.59,6.59c0.39,0.39 1.02,0.39 1.41,0 0.39,-0.39 0.39,-1.02 0,-1.41L7.83,13H19c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1z",
            autoMirror = true
        )
    }

    private val MaterialPerson: ImageVector by lazy {
        vectorIcon(
            name = "MaterialPerson",
            pathData = "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,6c1.93,0 3.5,1.57 3.5,3.5S13.93,13 12,13s-3.5,-1.57 -3.5,-3.5S10.07,6 12,6zM12,20c-2.03,0 -4.43,-0.82 -6.14,-2.88C7.55,15.8 9.68,15 12,15s4.45,0.8 6.14,2.12C16.43,19.18 14.03,20 12,20z"
        )
    }

    private val MaterialClose: ImageVector by lazy {
        vectorIcon(
            name = "MaterialClose",
            pathData = "M18.3,5.71c-0.39,-0.39 -1.02,-0.39 -1.41,0L12,10.59 7.11,5.7c-0.39,-0.39 -1.02,-0.39 -1.41,0 -0.39,0.39 -0.39,1.02 0,1.41L10.59,12 5.7,16.89c-0.39,0.39 -0.39,1.02 0,1.41 0.39,0.39 1.02,0.39 1.41,0L12,13.41l4.89,4.89c0.39,0.39 1.02,0.39 1.41,0 0.39,-0.39 0.39,-1.02 0,-1.41L13.41,12l4.89,-4.89c0.38,-0.38 0.38,-1.02 0,-1.4z"
        )
    }

    private val MaterialCheck: ImageVector by lazy {
        vectorIcon(
            name = "MaterialCheck",
            pathData = "M9,16.17L5.53,12.7c-0.39,-0.39 -1.02,-0.39 -1.41,0 -0.39,0.39 -0.39,1.02 0,1.41l4.18,4.18c0.39,0.39 1.02,0.39 1.41,0L20.29,7.71c0.39,-0.39 0.39,-1.02 0,-1.41 -0.39,-0.39 -1.02,-0.39 -1.41,0L9,16.17z"
        )
    }

    private val MaterialCancel: ImageVector by lazy {
        vectorIcon(
            name = "MaterialCancel",
            pathData = "M12,2C6.47,2 2,6.47 2,12s4.47,10 10,10 10,-4.47 10,-10S17.53,2 12,2zM16.3,16.3c-0.39,0.39 -1.02,0.39 -1.41,0L12,13.41 9.11,16.3c-0.39,0.39 -1.02,0.39 -1.41,0 -0.39,-0.39 -0.39,-1.02 0,-1.41L10.59,12 7.7,9.11c-0.39,-0.39 -0.39,-1.02 0,-1.41 0.39,-0.39 1.02,-0.39 1.41,0L12,10.59l2.89,-2.89c0.39,-0.39 1.02,-0.39 1.41,0 0.39,0.39 0.39,1.02 0,1.41L13.41,12l2.89,2.89c0.38,0.38 0.38,1.02 0,1.41z"
        )
    }

    private val MaterialImage: ImageVector by lazy {
        vectorIcon(
            name = "MaterialImage",
            pathData = "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.9,13.98l2.1,2.53 3.1,-3.99c0.2,-0.26 0.6,-0.26 0.8,0.01l3.51,4.68c0.25,0.33 0.01,0.8 -0.4,0.8H6.02c-0.42,0 -0.65,-0.48 -0.39,-0.81L8.12,14c0.19,-0.26 0.57,-0.27 0.78,-0.02z"
        )
    }

    private val MaterialMovie: ImageVector by lazy {
        vectorIcon(
            name = "MaterialMovie",
            pathData = "M18,4l1.82,3.64c0.08,0.16 -0.04,0.36 -0.22,0.36h-1.98c-0.38,0 -0.73,-0.21 -0.89,-0.55L15,4h-2l1.82,3.64c0.08,0.16 -0.04,0.36 -0.22,0.36h-1.98c-0.38,0 -0.73,-0.21 -0.89,-0.55L10,4H8l1.82,3.64c0.08,0.16 -0.04,0.36 -0.22,0.36H7.62c-0.38,0 -0.73,-0.21 -0.9,-0.55L5,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V5c0,-0.55 -0.45,-1 -1,-1h-3z"
        )
    }

    private val MaterialMusicNote: ImageVector by lazy {
        vectorIcon(
            name = "MaterialMusicNote",
            pathData = "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h3c0.55,0 1,-0.45 1,-1V4c0,-0.55 -0.45,-1 -1,-1h-5z"
        )
    }

    private val MaterialApps: ImageVector by lazy {
        vectorIcon(
            name = "MaterialApps",
            pathData = "M4,8h4L8,4L4,4v4zM10,20h4v-4h-4v4zM4,20h4v-4L4,16v4zM4,14h4v-4L4,10v4zM10,14h4v-4h-4v4zM16,4v4h4L20,4h-4zM10,8h4L14,4h-4v4zM16,14h4v-4h-4v4zM16,20h4v-4h-4v4z"
        )
    }

    private val MaterialFolderOpen: ImageVector by lazy {
        vectorIcon(
            name = "MaterialFolderOpen",
            pathData = "M20,6h-8l-1.41,-1.41C10.21,4.21 9.7,4 9.17,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2zM19,18H5c-0.55,0 -1,-0.45 -1,-1V9c0,-0.55 0.45,-1 1,-1h14c0.55,0 1,0.45 1,1v8c0,0.55 -0.45,1 -1,1z"
        )
    }

    private val MaterialFolderZip: ImageVector by lazy {
        vectorIcon(
            name = "MaterialFolderZip",
            pathData = "M20,6h-8l-1.41,-1.41C10.21,4.21 9.7,4 9.17,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2zM18,12h-2v2h2v2h-2v2h-2v-2h2v-2h-2v-2h2v-2h-2V8h2v2h2v2z"
        )
    }

    private val MaterialDescription: ImageVector by lazy {
        vectorIcon(
            name = "MaterialDescription",
            pathData = "M14.59,2.59C14.21,2.21 13.7,2 13.17,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8.83c0,-0.53 -0.21,-1.04 -0.59,-1.41l-4.82,-4.83zM15,18H9c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1h6c0.55,0 1,0.45 1,1s-0.45,1 -1,1zM15,14H9c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1h6c0.55,0 1,0.45 1,1s-0.45,1 -1,1zM13,8V3.5L18.5,9H14c-0.55,0 -1,-0.45 -1,-1z"
        )
    }

    private val MaterialInsertDriveFile: ImageVector by lazy {
        vectorIcon(
            name = "MaterialInsertDriveFile",
            pathData = "M6,2c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8.83c0,-0.53 -0.21,-1.04 -0.59,-1.41l-4.83,-4.83C14.21,2.21 13.7,2 13.17,2H6zM13,8V3.5L18.5,9H14c-0.55,0 -1,-0.45 -1,-1z"
        )
    }

    private val MaterialSdCard: ImageVector by lazy {
        vectorIcon(
            name = "MaterialSdCard",
            pathData = "M18,2h-7.17c-0.53,0 -1.04,0.21 -1.42,0.59L4.6,7.42c-0.37,0.37 -0.58,0.88 -0.58,1.4L4,20c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM11,8c-0.55,0 -1,-0.45 -1,-1V5c0,-0.55 0.45,-1 1,-1s1,0.45 1,1v2c0,0.55 -0.45,1 -1,1zM14,8c-0.55,0 -1,-0.45 -1,-1V5c0,-0.55 0.45,-1 1,-1s1,0.45 1,1v2c0,0.55 -0.45,1 -1,1zM17,8c-0.55,0 -1,-0.45 -1,-1V5c0,-0.55 0.45,-1 1,-1s1,0.45 1,1v2c0,0.55 -0.45,1 -1,1z"
        )
    }

    private val MaterialChevronRight: ImageVector by lazy {
        vectorIcon(
            name = "MaterialChevronRight",
            pathData = "M9.29,6.71c-0.39,0.39 -0.39,1.02 0,1.41L13.17,12l-3.88,3.88c-0.39,0.39 -0.39,1.02 0,1.41 0.39,0.39 1.02,0.39 1.41,0l4.59,-4.59c0.39,-0.39 0.39,-1.02 0,-1.41L10.7,6.7c-0.38,-0.38 -1.02,-0.38 -1.41,0.01z",
            autoMirror = true
        )
    }

    private val MaterialPalette: ImageVector by lazy {
        vectorIcon(
            name = "MaterialPalette",
            pathData = "M12,2C6.49,2 2,6.49 2,12s4.49,10 10,10c1.38,0 2.5,-1.12 2.5,-2.5 0,-0.61 -0.23,-1.2 -0.64,-1.67 -0.08,-0.1 -0.13,-0.21 -0.13,-0.33 0,-0.28 0.22,-0.5 0.5,-0.5H16c3.31,0 6,-2.69 6,-6 0,-4.96 -4.49,-9 -10,-9zM5.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S4.67,9 5.5,9 7,9.67 7,10.5 6.33,12 5.5,12zM8.5,8C7.67,8 7,7.33 7,6.5S7.67,5 8.5,5s1.5,0.67 1.5,1.5S9.33,8 8.5,8zM15.5,8C14.67,8 14,7.33 14,6.5S14.67,5 15.5,5s1.5,0.67 1.5,1.5S16.33,8 15.5,8zM18.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S17.67,9 18.5,9s1.5,0.67 1.5,1.5S19.33,12 18.5,12z"
        )
    }

    private val MaterialVisibility: ImageVector by lazy {
        vectorIcon(
            name = "MaterialVisibility",
            pathData = "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z"
        )
    }

    private val MaterialStyle: ImageVector by lazy {
        vectorIcon(
            name = "MaterialStyle",
            pathData = "M2.53,19.65l1.34,0.56v-9.03l-2.43,5.86c-0.41,1.02 0.08,2.19 1.09,2.61zM22.03,15.95L17.07,3.98c-0.31,-0.75 -1.04,-1.21 -1.81,-1.23 -0.26,0 -0.53,0.04 -0.79,0.15L7.1,5.95c-0.75,0.31 -1.21,1.03 -1.23,1.8 -0.01,0.27 0.04,0.54 0.15,0.8l4.96,11.97c0.31,0.76 1.05,1.22 1.83,1.23 0.26,0 0.52,-0.05 0.77,-0.15l7.36,-3.05c1.02,-0.42 1.51,-1.59 1.09,-2.6zM7.88,8.75c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1 1,0.45 1,1 -0.45,1 -1,1zM5.88,19.75c0,1.1 0.9,2 2,2h1.45l-3.45,-8.34v6.34z"
        )
    }

    private val MaterialWidgets: ImageVector by lazy {
        vectorIcon(
            name = "MaterialWidgets",
            pathData = "M13,13v8h8v-8h-8zM3,21h8v-8L3,13v8zM3,3v8h8L11,3L3,3zM16.66,1.69L11,7.34 16.66,13l5.66,-5.66 -5.66,-5.65z"
        )
    }

    // Classic Icons
    private val ClassicSend: ImageVector by lazy {
        vectorIcon(
            name = "ClassicSend",
            pathData = "M2.01,21L23,12 2.01,3 2,10l15,2 -15,2z",
            autoMirror = true
        )
    }

    private val ClassicShare: ImageVector by lazy { MaterialShare }

    private val ClassicFileDownload: ImageVector by lazy {
        vectorIcon(
            name = "ClassicFileDownload",
            pathData = "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z"
        )
    }

    private val ClassicArrowBack: ImageVector by lazy {
        vectorIcon(
            name = "ClassicArrowBack",
            pathData = "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z",
            autoMirror = true
        )
    }

    private val ClassicPerson: ImageVector by lazy {
        vectorIcon(
            name = "ClassicPerson",
            pathData = "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z"
        )
    }

    private val ClassicClose: ImageVector by lazy {
        vectorIcon(
            name = "ClassicClose",
            pathData = "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"
        )
    }

    private val ClassicCheck: ImageVector by lazy {
        vectorIcon(
            name = "ClassicCheck",
            pathData = "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z"
        )
    }

    private val ClassicCancel: ImageVector by lazy {
        vectorIcon(
            name = "ClassicCancel",
            pathData = "M12,2C6.47,2 2,6.47 2,12s4.47,10 10,10 10,-4.47 10,-10S17.53,2 12,2zM17,15.59L15.59,17 12,13.41 8.41,17 7,15.59 10.59,12 7,8.41 8.41,7 12,10.59 15.59,7 17,8.41 13.41,12 17,15.59z"
        )
    }

    private val ClassicImage: ImageVector by lazy {
        vectorIcon(
            name = "ClassicImage",
            pathData = "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z"
        )
    }

    private val ClassicMovie: ImageVector by lazy {
        vectorIcon(
            name = "ClassicMovie",
            pathData = "M18,4l2,4h-3l-2,-4h-2l2,4h-3l-2,-4H8l2,4H7L5,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4h-4z"
        )
    }

    private val ClassicMusicNote: ImageVector by lazy {
        vectorIcon(
            name = "ClassicMusicNote",
            pathData = "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"
        )
    }

    private val ClassicApps: ImageVector by lazy { MaterialApps }

    private val ClassicFolderOpen: ImageVector by lazy {
        vectorIcon(
            name = "ClassicFolderOpen",
            pathData = "M20,6h-8l-2,-2H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2zM20,18H4V8h16v10z"
        )
    }

    private val ClassicFolderZip: ImageVector by lazy {
        vectorIcon(
            name = "ClassicFolderZip",
            pathData = "M20,6h-8l-2,-2H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2zM18,12h-2v2h2v2h-2v2h-2v-2h2v-2h-2v-2h2v-2h-2V8h2v2h2v2z"
        )
    }

    private val ClassicDescription: ImageVector by lazy {
        vectorIcon(
            name = "ClassicDescription",
            pathData = "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6zM16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z"
        )
    }

    private val ClassicInsertDriveFile: ImageVector by lazy {
        vectorIcon(
            name = "ClassicInsertDriveFile",
            pathData = "M6,2c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6H6zM13,9V3.5L18.5,9H13z"
        )
    }

    private val ClassicSdCard: ImageVector by lazy {
        vectorIcon(
            name = "ClassicSdCard",
            pathData = "M18,2h-8L4.02,8 4,20c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM12,8h-2V4h2v4zM15,8h-2V4h2v4zM18,8h-2V4h2v4z"
        )
    }

    private val ClassicChevronRight: ImageVector by lazy {
        vectorIcon(
            name = "ClassicChevronRight",
            pathData = "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z",
            autoMirror = true
        )
    }

    private val ClassicPalette: ImageVector by lazy {
        vectorIcon(
            name = "ClassicPalette",
            pathData = "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9c0.83,0 1.5,-0.67 1.5,-1.5 0,-0.39 -0.15,-0.74 -0.39,-1.01 -0.23,-0.26 -0.38,-0.61 -0.38,-0.99 0,-0.83 0.67,-1.5 1.5,-1.5L16,16c2.76,0 5,-2.24 5,-5 0,-4.42 -4.03,-8 -9,-8zM6.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,9 6.5,9 8,9.67 8,10.5 7.33,12 6.5,12zM9.5,8C8.67,8 8,7.33 8,6.5S8.67,5 9.5,5s1.5,0.67 1.5,1.5S10.33,8 9.5,8zM14.5,8c-0.83,0 -1.5,-0.67 -1.5,-1.5S13.67,5 14.5,5s1.5,0.67 1.5,1.5S15.33,8 14.5,8zM17.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S16.67,9 17.5,9s1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z"
        )
    }

    private val ClassicVisibility: ImageVector by lazy { MaterialVisibility }
    private val ClassicStyle: ImageVector by lazy { MaterialStyle }
    private val ClassicWidgets: ImageVector by lazy { MaterialWidgets }

    private fun vectorIcon(
        name: String,
        pathData: String,
        autoMirror: Boolean = false
    ): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black)
        ).build()
    }
}
