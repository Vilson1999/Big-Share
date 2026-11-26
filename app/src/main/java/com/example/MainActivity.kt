package com.example

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cache.TempFileCacheManager
import com.example.model.AppScreen
import com.example.model.AppThemeMode
import com.example.picker.DeviceBrowser
import com.example.picker.PickerThumbnailCache
import com.example.ui.screens.FilePickerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.ReceiveScreen
import com.example.ui.screens.TransferScreen
import com.example.transfer.AppApkSharer
import com.example.ui.components.KeepScreenOnEffect
import com.example.ui.theme.AppTheme
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val incomingSharedUris = MutableStateFlow<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            handleShareIntent(intent)
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                AppThemeMode.DEFAULT -> systemDark
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
            }
            val pendingSharedUris by incomingSharedUris.collectAsStateWithLifecycle()

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
                onDispose {}
            }

            AppTheme(darkTheme = darkTheme) {
                MainAppContent(
                    viewModel = viewModel,
                    incomingSharedUris = pendingSharedUris,
                    onConsumeSharedUris = { incomingSharedUris.value = emptyList() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            PickerThumbnailCache.clearMemoryCache()
            if (!viewModel.isTransferActive.value) {
                TempFileCacheManager.closeSessionBlocking(applicationContext)
            }
        }
        super.onDestroy()
    }

    private fun handleShareIntent(intent: Intent?) {
        val uris = extractSharedUris(intent)
        if (uris.isNotEmpty()) {
            incomingSharedUris.value = uris
            intent?.action = Intent.ACTION_MAIN
        }
    }
}

private enum class RequiredSetting {
    WIFI,
    BLUETOOTH,
    LOCATION
}

private fun getMissingRequiredSetting(context: Context): RequiredSetting? {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val isWifiOn = wifiManager?.isWifiEnabled ?: true
    if (!isWifiOn) {
        return RequiredSetting.WIFI
    }

    val btManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val btAdapter = btManager?.adapter
    if (btAdapter != null && !btAdapter.isEnabled) {
        return RequiredSetting.BLUETOOTH
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isLocationOn = locationManager?.let { LocationManagerCompat.isLocationEnabled(it) } ?: true
        if (!isLocationOn) {
            return RequiredSetting.LOCATION
        }
    }

    return null
}

private fun createSettingIntent(context: Context, setting: RequiredSetting): Intent {
    return when (setting) {
        RequiredSetting.WIFI -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                if (panelIntent.resolveActivity(context.packageManager) != null) {
                    panelIntent
                } else {
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                }
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
        }
        RequiredSetting.BLUETOOTH -> {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (enableIntent.resolveActivity(context.packageManager) != null) {
                enableIntent
            } else {
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            }
        }
        RequiredSetting.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }
}

private fun extractSharedUris(intent: Intent?): List<Uri> {
    if (intent == null) return emptyList()
    if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) {
        return emptyList()
    }
    val result = LinkedHashSet<Uri>()

    when (intent.action) {
        Intent.ACTION_SEND -> {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let {
                result.add(it)
            }
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let {
                result.addAll(it)
            }
        }
    }

    intent.clipData?.let { clipData ->
        for (i in 0 until clipData.itemCount) {
            clipData.getItemAt(i).uri?.let { result.add(it) }
        }
    }

    return result.toList()
}

internal fun getRequiredPermissions(): Array<String> {
    return when {
        // Android 13 - 17 (API 33+): Wi-Fi Nearby + Bluetooth Nearby + Notifications for background transfer
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS
        )
        // Android 12 / 12L (API 31-32): Bluetooth Nearby + Location for Wi-Fi P2P
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        // Android 10 & 11 (API 29-30): Location runtime permission (BLUETOOTH is install-time, Scoped Storage for Downloads)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        // Android 9 (API 28): Location + WRITE_EXTERNAL_STORAGE for Downloads
        else -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}

private fun hasAllPermissions(context: Context): Boolean {
    val requiredPerms = getRequiredPermissions().filter {
        it != Manifest.permission.POST_NOTIFICATIONS
    }
    return requiredPerms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun MainAppContent(
    viewModel: MainViewModel,
    incomingSharedUris: List<Uri> = emptyList(),
    onConsumeSharedUris: () -> Unit = {}
) {
    KeepScreenOnEffect()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val peerUserNames by viewModel.peerUserNames.collectAsStateWithLifecycle()
    val peerAvatars by viewModel.peerAvatars.collectAsStateWithLifecycle()
    val isScanning by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val transferFiles by viewModel.transferFiles.collectAsStateWithLifecycle()
    val transferStats by viewModel.transferStats.collectAsStateWithLifecycle()
    val transferDirection by viewModel.transferDirection.collectAsStateWithLifecycle()
    val isTransferActive by viewModel.isTransferActive.collectAsStateWithLifecycle()
    val isTransferComplete by viewModel.isTransferComplete.collectAsStateWithLifecycle()
    val transferError by viewModel.transferError.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userAvatarIndex by viewModel.userAvatarIndex.collectAsStateWithLifecycle()
    val isProfileCompleted by viewModel.isProfileCompleted.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val connectingDeviceAddress by viewModel.connectingDeviceAddress.collectAsStateWithLifecycle()
    val pickerImages by viewModel.pickerImages.collectAsStateWithLifecycle()
    val pickerVideos by viewModel.pickerVideos.collectAsStateWithLifecycle()
    val pickerMusic by viewModel.pickerMusic.collectAsStateWithLifecycle()
    val pickerFiles by viewModel.pickerFiles.collectAsStateWithLifecycle()
    val pickerInstalledApps by viewModel.pickerInstalledApps.collectAsStateWithLifecycle()
    val pickerApkFiles by viewModel.pickerApkFiles.collectAsStateWithLifecycle()
    val isScanningMedia by viewModel.isScanningMedia.collectAsStateWithLifecycle()
    val isScanningApps by viewModel.isScanningApps.collectAsStateWithLifecycle()
    val hasMediaPermission by viewModel.hasMediaPermission.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val receiveLocationUri by viewModel.receiveLocationUri.collectAsStateWithLifecycle()
    val receiveLocationLabel by viewModel.receiveLocationLabel.collectAsStateWithLifecycle()

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var awaitingExternalModal by remember { mutableStateOf(false) }
    var isSharingApp by remember { mutableStateOf(false) }
    var missingSetting by remember { mutableStateOf<RequiredSetting?>(null) }
    var hasPromptedAllFilesOnStart by remember { mutableStateOf(false) }
    var showStartupAllFilesDialog by remember { mutableStateOf(false) }

    val proceedWithChecks: (() -> Unit) -> Unit = remember(context) {
        { action ->
            val missing = getMissingRequiredSetting(context)
            if (missing == null) {
                missingSetting = null
                pendingAction = null
                action()
            } else {
                pendingAction = action
                missingSetting = missing
            }
        }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        awaitingExternalModal = false
        if (uris.isNotEmpty()) {
            viewModel.onFilesPicked(context, uris)
        }
    }

    val recheckPendingSetting: () -> Unit = {
        val action = pendingAction
        if (action != null) {
            val missing = getMissingRequiredSetting(context)
            if (missing == null) {
                missingSetting = null
                pendingAction = null
                action()
            } else {
                missingSetting = missing
            }
        }
    }

    val startupAllFilesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        awaitingExternalModal = false
        if (DeviceBrowser.hasAllFilesAccess(context)) {
            viewModel.refreshMediaPermissionAndLoad(forceRefresh = true)
        }
    }

    val startupLegacyStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        awaitingExternalModal = false
        if (DeviceBrowser.hasAllFilesAccess(context)) {
            viewModel.refreshMediaPermissionAndLoad(forceRefresh = true)
        }
    }

    fun launchAllFilesPermissionAtStart() {
        awaitingExternalModal = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", context.packageName, null)
            )
            val launched = try {
                startupAllFilesSettingsLauncher.launch(appIntent)
                true
            } catch (_: Exception) {
                false
            }
            if (!launched) {
                try {
                    startupAllFilesSettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                    awaitingExternalModal = false
                }
            }
        } else {
            startupLegacyStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // Ask for All Files permission at app start if not yet granted
    LaunchedEffect(currentScreen) {
        if (currentScreen == AppScreen.HOME && !hasPromptedAllFilesOnStart) {
            hasPromptedAllFilesOnStart = true
            if (!DeviceBrowser.hasAllFilesAccess(context)) {
                showStartupAllFilesDialog = true
            }
        }
    }

    // Settings launcher
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        awaitingExternalModal = false
        recheckPendingSetting()
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        awaitingExternalModal = false
        val requiredGranted = grants
            .filterKeys { it != Manifest.permission.POST_NOTIFICATIONS }
            .values
            .all { it }
        val action = pendingAction
        if (requiredGranted && action != null) {
            proceedWithChecks(action)
        } else {
            pendingAction = null
            scope.launch {
                snackbarHostState.showSnackbar("Permissions are required for file sharing")
            }
        }
    }

    val requestActionWithPermissionsAndSettings: (() -> Unit) -> Unit = { action ->
        if (hasAllPermissions(context)) {
            proceedWithChecks(action)
        } else {
            pendingAction = action
            awaitingExternalModal = true
            permissionLauncher.launch(getRequiredPermissions())
        }
    }

    // Direct share when launched from Android Share menu
    LaunchedEffect(incomingSharedUris) {
        if (incomingSharedUris.isNotEmpty()) {
            val urisToShare = incomingSharedUris.toList()
            onConsumeSharedUris()
            requestActionWithPermissionsAndSettings {
                viewModel.onFilesPicked(context, urisToShare)
            }
        }
    }

    // Never run discovery, transfers, or broadcast receivers in background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewModel.onAppForegrounded()
                }
                Lifecycle.Event.ON_RESUME -> {
                    awaitingExternalModal = false
                    if (DeviceBrowser.hasAllFilesAccess(context) && !hasMediaPermission) {
                        viewModel.refreshMediaPermissionAndLoad(forceRefresh = true)
                    }
                    if (missingSetting != null) {
                        recheckPendingSetting()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    val activity = context as? Activity
                    val isConfigChange = activity?.isChangingConfigurations == true
                    if (!isConfigChange && !awaitingExternalModal) {
                        pendingAction = null
                        missingSetting = null
                        viewModel.onAppBackgrounded()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Show error toast/snackbar if transfer encounters an error
    LaunchedEffect(transferError) {
        transferError?.let { err ->
            if (err.isNotBlank() && !err.contains("closed", ignoreCase = true)) {
                snackbarHostState.showSnackbar(err)
            }
        }
    }

    missingSetting?.let { setting ->
        val colors = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = {
                missingSetting = null
                pendingAction = null
            },
            title = {
                Text(
                    text = when (setting) {
                        RequiredSetting.WIFI -> "TURN ON WI-FI"
                        RequiredSetting.BLUETOOTH -> "TURN ON BLUETOOTH"
                        RequiredSetting.LOCATION -> "TURN ON LOCATION"
                    },
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = when (setting) {
                        RequiredSetting.WIFI -> "Wi-Fi is off."
                        RequiredSetting.BLUETOOTH -> "Bluetooth is off."
                        RequiredSetting.LOCATION -> "Location is off."
                    },
                    color = colors.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        awaitingExternalModal = true
                        settingsLauncher.launch(createSettingIntent(context, setting))
                    },
                    modifier = Modifier.testTag("open_setting_button")
                ) {
                    Text("OPEN SETTINGS", color = colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        missingSetting = null
                        pendingAction = null
                    },
                    modifier = Modifier.testTag("cancel_setting_button")
                ) {
                    Text("CANCEL", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surfaceVariant
        )
    }

    if (showStartupAllFilesDialog) {
        val colors = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = { showStartupAllFilesDialog = false },
            title = {
                Text(
                    text = "Allow All Files Access",
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Max Share needs All Files permission to browse and share videos, music, APKs, and all file types from internal and external storage.",
                    color = colors.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStartupAllFilesDialog = false
                        launchAllFilesPermissionAtStart()
                    },
                    modifier = Modifier.testTag("startup_all_files_allow_button")
                ) {
                    Text("ALLOW", color = colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStartupAllFilesDialog = false },
                    modifier = Modifier.testTag("startup_all_files_later_button")
                ) {
                    Text("LATER", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surfaceVariant
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentScreen) {
                AppScreen.PROFILE -> {
                    if (isProfileCompleted) {
                        BackHandler {
                            viewModel.resetToHome()
                        }
                    }
                    ProfileScreen(
                        initialName = userName,
                        initialAvatarIndex = userAvatarIndex,
                        isFirstLaunch = !isProfileCompleted,
                        themeMode = themeMode,
                        receiveLocationLabel = receiveLocationLabel,
                        hasCustomReceiveLocation = !receiveLocationUri.isNullOrBlank(),
                        onThemeModeSelect = { mode -> viewModel.updateThemeMode(mode) },
                        onReceiveLocationSelected = { treeUri ->
                            viewModel.updateReceiveLocation(context, treeUri)
                        },
                        onExternalModalLaunch = { awaitingExternalModal = true },
                        onBackClick = if (isProfileCompleted) {
                            { viewModel.resetToHome() }
                        } else null,
                        onSaveProfile = { name, avatarIndex ->
                            viewModel.saveUserProfile(name, avatarIndex)
                        }
                    )
                }

                AppScreen.HOME -> {
                    HomeScreen(
                        userName = userName,
                        userAvatarIndex = userAvatarIndex,
                        isSharingApp = isSharingApp,
                        onProfileClick = { viewModel.openProfile() },
                        onShareAppClick = {
                            if (!isSharingApp) {
                                isSharingApp = true
                                awaitingExternalModal = true
                                scope.launch {
                                    val result = AppApkSharer.shareAppApk(context)
                                    isSharingApp = false
                                    if (result.isFailure) {
                                        awaitingExternalModal = false
                                        snackbarHostState.showSnackbar(
                                            result.exceptionOrNull()?.localizedMessage
                                                ?: "Unable to share app APK"
                                        )
                                    }
                                }
                            }
                        },
                        onSendClick = {
                            requestActionWithPermissionsAndSettings {
                                viewModel.openFilePicker()
                            }
                        },
                        onReceiveClick = {
                            requestActionWithPermissionsAndSettings {
                                viewModel.onReceiveClicked()
                            }
                        }
                    )
                }

                AppScreen.FILE_PICKER -> {
                    BackHandler {
                        viewModel.resetToHome()
                    }
                    FilePickerScreen(
                        images = pickerImages,
                        videos = pickerVideos,
                        music = pickerMusic,
                        files = pickerFiles,
                        installedApps = pickerInstalledApps,
                        apkFiles = pickerApkFiles,
                        isScanningMedia = isScanningMedia,
                        isScanningApps = isScanningApps,
                        hasMediaPermission = hasMediaPermission,
                        onEnsureLoaded = { viewModel.ensurePickerFilesLoaded() },
                        onMediaPermissionResult = { viewModel.refreshMediaPermissionAndLoad(forceRefresh = true) },
                        onBackClick = { viewModel.resetToHome() },
                        onSystemBrowseClick = {
                            awaitingExternalModal = true
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        onExternalPermissionLaunch = {
                            awaitingExternalModal = true
                        },
                        onConfirmSelection = { items ->
                            viewModel.onFileItemsPicked(items)
                        }
                    )
                }

                AppScreen.RADAR -> {
                    BackHandler {
                        viewModel.resetToHome()
                    }
                    RadarScreen(
                        peers = peers,
                        peerUserNames = peerUserNames,
                        peerAvatars = peerAvatars,
                        selectedFiles = selectedFiles,
                        isScanning = isScanning,
                        userName = userName,
                        userAvatarIndex = userAvatarIndex,
                        connectingDeviceAddress = connectingDeviceAddress,
                        onBackClick = { viewModel.resetToHome() },
                        onDeviceClick = { device -> viewModel.connectAndSend(device) }
                    )
                }

                AppScreen.RECEIVE -> {
                    BackHandler {
                        viewModel.resetToHome()
                    }
                    ReceiveScreen(
                        userName = userName,
                        userAvatarIndex = userAvatarIndex,
                        receiveLocationLabel = receiveLocationLabel,
                        onBackClick = { viewModel.resetToHome() }
                    )
                }

                AppScreen.TRANSFER -> {
                    BackHandler {
                        if (isTransferComplete || !isTransferActive) {
                            viewModel.resetToHome()
                        } else {
                            viewModel.cancelAllTransfers()
                        }
                    }
                    TransferScreen(
                        files = transferFiles,
                        stats = transferStats,
                        direction = transferDirection,
                        isActive = isTransferActive,
                        isComplete = isTransferComplete,
                        onCancelFile = { fileId -> viewModel.cancelTransferringFile(fileId) },
                        onCancelAll = { viewModel.resetToHome() },
                        onDone = { viewModel.resetToHome() }
                    )
                }
            }
        }
    }
}
