package com.example.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cache.TempFileCacheManager
import com.example.model.AppScreen
import com.example.model.AppThemeMode
import com.example.model.FileItem
import com.example.model.TransferDirection
import com.example.model.TransferStats
import com.example.model.TransferStatus
import com.example.model.formatBytes
import com.example.p2p.BluetoothDiscoveryHelper
import com.example.p2p.WifiDirectManager
import com.example.picker.DeviceMediaScanner
import com.example.picker.PickerAppItem
import com.example.picker.PickerMediaItem
import com.example.picker.PickerThumbnailCache
import com.example.transfer.FileTransferEngine
import com.example.transfer.TransferForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val wifiDirectManager = WifiDirectManager(application)
    private val bluetoothDiscoveryHelper = BluetoothDiscoveryHelper(application)
    private val transferEngine = FileTransferEngine(application)

    private val prefs = application.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    private val _isProfileCompleted = MutableStateFlow(
        prefs.getBoolean("profile_completed", false)
    )
    val isProfileCompleted: StateFlow<Boolean> = _isProfileCompleted.asStateFlow()

    private val _currentScreen = MutableStateFlow(
        if (_isProfileCompleted.value) AppScreen.HOME else AppScreen.PROFILE
    )
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val selectedFiles: StateFlow<List<FileItem>> = _selectedFiles.asStateFlow()

    private val _transferFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val transferFiles: StateFlow<List<FileItem>> = _transferFiles.asStateFlow()

    private val _transferStats = MutableStateFlow(TransferStats())
    val transferStats: StateFlow<TransferStats> = _transferStats.asStateFlow()

    private val _transferDirection = MutableStateFlow(TransferDirection.SEND)
    val transferDirection: StateFlow<TransferDirection> = _transferDirection.asStateFlow()

    private val _isTransferActive = MutableStateFlow(false)
    val isTransferActive: StateFlow<Boolean> = _isTransferActive.asStateFlow()

    private val _isTransferComplete = MutableStateFlow(false)
    val isTransferComplete: StateFlow<Boolean> = _isTransferComplete.asStateFlow()

    private val _transferError = MutableStateFlow<String?>(null)
    val transferError: StateFlow<String?> = _transferError.asStateFlow()

    private val _connectingDeviceAddress = MutableStateFlow<String?>(null)
    val connectingDeviceAddress: StateFlow<String?> = _connectingDeviceAddress.asStateFlow()

    private var transferJob: Job? = null
    private var discoveryJob: Job? = null
    private var connectTimeoutJob: Job? = null
    private var mediaPreloadJob: Job? = null
    private var filesPreloadJob: Job? = null
    private var appsPreloadJob: Job? = null
    private var cacheBuildJob: Job? = null
    private var isAppInBackground = false
    private var hasCompletedMediaScan = false
    private var hasCompletedFilesScan = false
    private var hasCompletedAppsScan = false
    private var lastNotificationUpdateAt = 0L

    private val _pickerImages = MutableStateFlow<List<PickerMediaItem>>(emptyList())
    val pickerImages: StateFlow<List<PickerMediaItem>> = _pickerImages.asStateFlow()

    private val _pickerVideos = MutableStateFlow<List<PickerMediaItem>>(emptyList())
    val pickerVideos: StateFlow<List<PickerMediaItem>> = _pickerVideos.asStateFlow()

    private val _pickerMusic = MutableStateFlow<List<PickerMediaItem>>(emptyList())
    val pickerMusic: StateFlow<List<PickerMediaItem>> = _pickerMusic.asStateFlow()

    private val _pickerFiles = MutableStateFlow<List<PickerMediaItem>>(emptyList())
    val pickerFiles: StateFlow<List<PickerMediaItem>> = _pickerFiles.asStateFlow()

    private val _pickerInstalledApps = MutableStateFlow<List<PickerAppItem>>(emptyList())
    val pickerInstalledApps: StateFlow<List<PickerAppItem>> = _pickerInstalledApps.asStateFlow()

    private val _pickerApkFiles = MutableStateFlow<List<PickerAppItem>>(emptyList())
    val pickerApkFiles: StateFlow<List<PickerAppItem>> = _pickerApkFiles.asStateFlow()

    private val _isScanningMedia = MutableStateFlow(false)
    val isScanningMedia: StateFlow<Boolean> = _isScanningMedia.asStateFlow()

    private val _isScanningApps = MutableStateFlow(false)
    val isScanningApps: StateFlow<Boolean> = _isScanningApps.asStateFlow()

    private val _hasMediaPermission = MutableStateFlow(
        DeviceMediaScanner.hasAnyMediaReadPermission(application)
    )
    val hasMediaPermission: StateFlow<Boolean> = _hasMediaPermission.asStateFlow()

    val peers = wifiDirectManager.peers
    val peerUserNames = wifiDirectManager.peerUserNames
    val peerAvatars = wifiDirectManager.peerAvatars
    val isDiscovering = wifiDirectManager.isDiscovering

    private val _userName = MutableStateFlow(
        prefs.getString("user_name", null) ?: (android.os.Build.MODEL ?: "User")
    )
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userAvatarIndex = MutableStateFlow(
        prefs.getInt("user_avatar_index", 0).coerceIn(0, 9)
    )
    val userAvatarIndex: StateFlow<Int> = _userAvatarIndex.asStateFlow()

    private val _themeMode = MutableStateFlow(
        AppThemeMode.fromKey(prefs.getString("app_theme_mode", AppThemeMode.DEFAULT.key))
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _receiveLocationUri = MutableStateFlow(
        prefs.getString("receive_tree_uri", null)?.takeIf { it.isNotBlank() }
    )
    val receiveLocationUri: StateFlow<String?> = _receiveLocationUri.asStateFlow()

    private val _receiveLocationLabel = MutableStateFlow(
        prefs.getString("receive_location_label", null)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_RECEIVE_LOCATION_LABEL
    )
    val receiveLocationLabel: StateFlow<String> = _receiveLocationLabel.asStateFlow()

    fun openProfile() {
        _transferError.value = null
        _currentScreen.value = AppScreen.PROFILE
    }

    fun updateThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("app_theme_mode", mode.key).apply()
    }

    fun updateReceiveLocation(context: Context, treeUri: Uri?) {
        if (treeUri == null) {
            _receiveLocationUri.value = null
            _receiveLocationLabel.value = DEFAULT_RECEIVE_LOCATION_LABEL
            prefs.edit()
                .remove("receive_tree_uri")
                .remove("receive_location_label")
                .apply()
            return
        }

        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }

        val resolvedLabel = resolveFolderDisplayLabel(context, treeUri)
        val uriString = treeUri.toString()
        _receiveLocationUri.value = uriString
        _receiveLocationLabel.value = resolvedLabel
        prefs.edit()
            .putString("receive_tree_uri", uriString)
            .putString("receive_location_label", resolvedLabel)
            .apply()
    }

    private fun resolveFolderDisplayLabel(context: Context, treeUri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            var displayName: String? = null
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    displayName = c.getString(0)?.trim()?.takeIf { it.isNotBlank() }
                }
            }
            val volumePrefix = when {
                docId.startsWith("primary:", ignoreCase = true) -> "Internal"
                docId.contains(':') -> "External"
                else -> ""
            }
            val rawPath = docId.substringAfter(':', docId).trim('/').ifBlank { displayName ?: "Selected Folder" }
            val cleanName = displayName ?: rawPath.substringAfterLast('/')
            if (volumePrefix.isNotEmpty() && !rawPath.equals(cleanName, ignoreCase = true)) {
                "$volumePrefix / $rawPath"
            } else if (volumePrefix.isNotEmpty()) {
                "$volumePrefix / $cleanName"
            } else {
                cleanName
            }
        } catch (_: Exception) {
            "Custom Folder"
        }
    }

    fun saveUserProfile(name: String, avatarIndex: Int) {
        val trimmed = name.trim().ifEmpty { _userName.value }
        val safeAvatar = avatarIndex.coerceIn(0, 9)
        _userName.value = trimmed
        _userAvatarIndex.value = safeAvatar
        _isProfileCompleted.value = true
        prefs.edit()
            .putString("user_name", trimmed)
            .putInt("user_avatar_index", safeAvatar)
            .putBoolean("profile_completed", true)
            .apply()
        _currentScreen.value = AppScreen.HOME
    }

    companion object {
        const val DEFAULT_RECEIVE_LOCATION_LABEL = "Downloads/Max Share"
    }

    init {
        TempFileCacheManager.openSession(application, cleanStale = true)
        preloadDeviceFilesOnStartup()
        var wasServiceRunning = false
        viewModelScope.launch {
            TransferForegroundService.isRunning.collect { running ->
                if (wasServiceRunning && !running) {
                    if (_isTransferActive.value) {
                        cancelAllTransfers()
                    }
                    if (isAppInBackground) {
                        stopBackgroundActivity()
                    }
                }
                wasServiceRunning = running
            }
        }
        viewModelScope.launch {
            wifiDirectManager.connectionInfo.collect { info ->
                if (info != null && info.groupFormed) {
                    val host = info.groupOwnerAddress?.hostAddress ?: "192.168.49.1"
                    val isGo = info.isGroupOwner
                    when (_currentScreen.value) {
                        AppScreen.RADAR -> {
                            if (_selectedFiles.value.isNotEmpty() && !_isTransferActive.value) {
                                connectTimeoutJob?.cancel()
                                stopDiscoveryLoop()
                                wifiDirectManager.stopDiscovery()
                                startSendingFiles(isGroupOwner = isGo, hostAddress = host)
                            }
                        }
                        AppScreen.RECEIVE -> {
                            if (!_isTransferActive.value) {
                                stopDiscoveryLoop()
                                wifiDirectManager.stopDiscovery()
                                wifiDirectManager.stopReceiveListening()
                                startReceivingFiles(isGroupOwner = isGo, hostAddress = host)
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun preloadDeviceFilesOnStartup() {
        ensureAppsAndApksLoaded(forceRefresh = false)
        ensureFilesLoaded(forceRefresh = false)
        refreshMediaPermissionAndLoad(forceRefresh = false)
    }

    fun ensurePickerFilesLoaded() {
        ensureAppsAndApksLoaded(forceRefresh = appsPreloadJob?.isActive != true)
        ensureFilesLoaded(forceRefresh = false)
        refreshMediaPermissionAndLoad(forceRefresh = false)
    }

    fun refreshMediaPermissionAndLoad(forceRefresh: Boolean = false) {
        val wasGranted = _hasMediaPermission.value
        val granted = DeviceMediaScanner.hasAnyMediaReadPermission(getApplication())
        _hasMediaPermission.value = granted
        if (!granted) return
        if (!wasGranted || forceRefresh) {
            ensureAppsAndApksLoaded(forceRefresh = true)
            ensureFilesLoaded(forceRefresh = true)
        }
        if (!forceRefresh && (hasCompletedMediaScan || mediaPreloadJob?.isActive == true)) return

        mediaPreloadJob?.cancel()
        mediaPreloadJob = viewModelScope.launch {
            _isScanningMedia.value = true
            try {
                DeviceMediaScanner.scanMediaFilesProgressively(getApplication()) { imgs, vids, mus ->
                    _pickerImages.value = imgs
                    _pickerVideos.value = vids
                    _pickerMusic.value = mus
                }
                hasCompletedMediaScan = true
                persistTempCache(includeMedia = true)
            } finally {
                _isScanningMedia.value = false
            }
        }
    }

    fun ensureFilesLoaded(forceRefresh: Boolean = false) {
        if (!forceRefresh && (hasCompletedFilesScan || filesPreloadJob?.isActive == true)) return

        filesPreloadJob?.cancel()
        filesPreloadJob = viewModelScope.launch {
            try {
                DeviceMediaScanner.scanFilesProgressively(getApplication()) { scannedFiles ->
                    if (!forceRefresh || scannedFiles.isNotEmpty() || !hasCompletedFilesScan) {
                        _pickerFiles.value = scannedFiles
                    }
                }
                hasCompletedFilesScan = true
                persistTempCache(includeFiles = true)
            } catch (_: Exception) {
            }
        }
    }

    fun ensureAppsAndApksLoaded(forceRefresh: Boolean = false) {
        if (!forceRefresh && (hasCompletedAppsScan || appsPreloadJob?.isActive == true)) return

        appsPreloadJob?.cancel()
        appsPreloadJob = viewModelScope.launch {
            _isScanningApps.value = true
            try {
                DeviceMediaScanner.scanAppsAndApksProgressively(getApplication()) { installed, apks ->
                    if (!forceRefresh || installed.isNotEmpty() || !hasCompletedAppsScan) {
                        _pickerInstalledApps.value = installed
                    }
                    if (!forceRefresh || apks.isNotEmpty() || !hasCompletedAppsScan) {
                        _pickerApkFiles.value = apks
                    }
                }
                hasCompletedAppsScan = true
                persistTempCache(includeApps = true)
            } finally {
                _isScanningApps.value = false
            }
        }
    }

    private fun persistTempCache(
        includeMedia: Boolean = false,
        includeFiles: Boolean = false,
        includeApps: Boolean = false
    ) {
        val app = getApplication<Application>()
        if (!TempFileCacheManager.isSessionActive()) return
        val shouldWriteMedia = includeMedia && hasCompletedMediaScan
        val shouldWriteFiles = includeFiles && hasCompletedFilesScan
        val shouldWriteApps = includeApps && hasCompletedAppsScan
        if (!shouldWriteMedia && !shouldWriteFiles && !shouldWriteApps) return

        val previousJob = cacheBuildJob
        cacheBuildJob = viewModelScope.launch {
            previousJob?.join()
            if (!TempFileCacheManager.isSessionActive()) return@launch
            if (shouldWriteMedia) {
                TempFileCacheManager.writeMediaIndex(app, "images", _pickerImages.value)
                TempFileCacheManager.writeMediaIndex(app, "videos", _pickerVideos.value)
                TempFileCacheManager.writeMediaIndex(app, "music", _pickerMusic.value)
            }
            if (shouldWriteFiles) {
                TempFileCacheManager.writeMediaIndex(app, "files", _pickerFiles.value)
            }
            if (shouldWriteApps) {
                TempFileCacheManager.writeAppsIndex(app, "installed_apps", _pickerInstalledApps.value)
                TempFileCacheManager.writeAppsIndex(app, "apk_files", _pickerApkFiles.value)
            }
            if (shouldWriteMedia || shouldWriteApps) {
                PickerThumbnailCache.prewarmThumbnails(
                    context = app,
                    images = if (shouldWriteMedia) _pickerImages.value else emptyList(),
                    apps = if (shouldWriteApps) _pickerInstalledApps.value else emptyList()
                )
            }
        }
    }

    fun openFilePicker() {
        _transferError.value = null
        ensurePickerFilesLoaded()
        _currentScreen.value = AppScreen.FILE_PICKER
    }

    fun onFilesPicked(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        val items = uris.mapNotNull { uri ->
            extractFileItem(context, uri)
        }

        onFileItemsPicked(items)
    }

    fun onFileItemsPicked(items: List<FileItem>) {
        if (items.isNotEmpty()) {
            connectTimeoutJob?.cancel()
            stopDiscoveryLoop()
            cancelAllTransfers()
            _connectingDeviceAddress.value = null
            _transferError.value = null
            _selectedFiles.value = items
            _transferFiles.value = items
            _transferDirection.value = TransferDirection.SEND
            _currentScreen.value = AppScreen.RADAR
            wifiDirectManager.prepareCleanSession {
                if (_currentScreen.value == AppScreen.RADAR) {
                    startDiscoveryLoop()
                }
            }
        }
    }

    private fun extractFileItem(context: Context, uri: Uri): FileItem? {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L
        val mime = context.contentResolver.getType(uri) ?: "*/*"

        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { path ->
                val file = File(path)
                if (file.name.isNotBlank()) name = file.name
                if (file.exists()) size = file.length()
            }
        } else {
            try {
                val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) {
                            name = it.getString(nameIndex) ?: name
                        }
                        if (sizeIndex != -1) {
                            size = it.getLong(sizeIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error query URI", e)
            }
        }

        if (size <= 0) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0) size = afd.length
                }
            } catch (_: Exception) {}
        }
        if (size <= 0) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    size = stream.available().toLong()
                }
            } catch (_: Exception) {}
        }

        return FileItem(
            id = UUID.randomUUID().toString(),
            name = name,
            sizeBytes = size,
            uriString = uri.toString(),
            mimeType = mime,
            status = TransferStatus.PENDING
        )
    }

    private fun startDiscoveryLoop() {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            var firstRun = true
            var lastPeerDiscoveryAt = 0L
            var lastServiceDiscoveryAt = 0L
            var serviceWindowUntil = 0L
            while (isActive && (_currentScreen.value == AppScreen.RADAR || _currentScreen.value == AppScreen.RECEIVE)) {
                val isReceive = _currentScreen.value == AppScreen.RECEIVE
                val isRadar = _currentScreen.value == AppScreen.RADAR
                val now = System.currentTimeMillis()

                if (isReceive && firstRun) {
                    firstRun = false
                    lastPeerDiscoveryAt = now
                    bluetoothDiscoveryHelper.startAdvertising()
                    wifiDirectManager.registerLocalReceiveService(_userName.value, _userAvatarIndex.value) {
                        wifiDirectManager.startReceiveListening(
                            onFailure = { reason ->
                                Log.e("MainViewModel", "Receive listen failed: $reason")
                                if (reason == WifiP2pManager.P2P_UNSUPPORTED) {
                                    _transferError.value = "Wi-Fi Direct not supported"
                                }
                            }
                        )
                    }
                } else if (isRadar && _connectingDeviceAddress.value == null && !_isTransferActive.value) {
                    if (firstRun) {
                        bluetoothDiscoveryHelper.startScanning {
                            wifiDirectManager.requestPeers()
                        }
                    }
                    val needsPeerRefresh = firstRun ||
                        (!wifiDirectManager.isDiscovering.value && now >= serviceWindowUntil) ||
                        (now - lastPeerDiscoveryAt >= 22000L && now >= serviceWindowUntil)

                    if (needsPeerRefresh) {
                        firstRun = false
                        lastPeerDiscoveryAt = now
                        lastServiceDiscoveryAt = 0L
                        wifiDirectManager.discoverPeers(
                            onFailure = { reason ->
                                Log.e("MainViewModel", "Discovery failed: $reason")
                                if (reason == WifiP2pManager.P2P_UNSUPPORTED) {
                                    _transferError.value = "Wi-Fi Direct not supported"
                                }
                            }
                        )
                    } else if (now - lastServiceDiscoveryAt >= 6000L) {
                        // Query DNS-SD TXT records promptly for peer user names and avatar icons
                        lastServiceDiscoveryAt = now
                        serviceWindowUntil = now + 3500L
                        wifiDirectManager.discoverMiniShareServices()
                    }
                } else if (isReceive && !_isTransferActive.value) {
                    val needsReceiveRefresh = !wifiDirectManager.isDiscovering.value ||
                        (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU &&
                            now - lastPeerDiscoveryAt >= 22000L)
                    if (needsReceiveRefresh) {
                        lastPeerDiscoveryAt = now
                        wifiDirectManager.startReceiveListening()
                    }
                }

                if (_connectingDeviceAddress.value != null || isReceive) {
                    wifiDirectManager.requestConnectionInfo()
                }
                delay(2000L)
            }
        }
    }

    private fun stopDiscoveryLoop() {
        discoveryJob?.cancel()
        discoveryJob = null
        bluetoothDiscoveryHelper.stopAll()
    }

    fun onReceiveClicked() {
        _connectingDeviceAddress.value = null
        _transferError.value = null
        _transferDirection.value = TransferDirection.RECEIVE
        _currentScreen.value = AppScreen.RECEIVE
        wifiDirectManager.prepareCleanSession {
            if (_currentScreen.value == AppScreen.RECEIVE) {
                startDiscoveryLoop()
            }
        }
    }

    fun connectAndSend(device: WifiP2pDevice) {
        if (_connectingDeviceAddress.value == device.deviceAddress) return
        _connectingDeviceAddress.value = device.deviceAddress
        _transferError.value = null

        // Timeout guard if peer declines or system invitation expires
        connectTimeoutJob?.cancel()
        connectTimeoutJob = viewModelScope.launch {
            delay(25000L)
            if (_connectingDeviceAddress.value == device.deviceAddress &&
                _currentScreen.value == AppScreen.RADAR &&
                !_isTransferActive.value
            ) {
                _connectingDeviceAddress.value = null
                wifiDirectManager.cancelConnect()
            }
        }

        if (device.status == WifiP2pDevice.CONNECTED) {
            wifiDirectManager.requestConnectionInfo { info ->
                if (info.groupFormed && _currentScreen.value == AppScreen.RADAR && !_isTransferActive.value) {
                    stopDiscoveryLoop()
                    wifiDirectManager.stopDiscovery()
                    val host = info.groupOwnerAddress?.hostAddress ?: "192.168.49.1"
                    startSendingFiles(isGroupOwner = info.isGroupOwner, hostAddress = host)
                }
            }
            return
        }

        val initiateConnect: () -> Unit = {
            wifiDirectManager.connectToDevice(
                device = device,
                groupOwnerIntent = 0,
                onFailure = { reason ->
                    if (reason == WifiP2pManager.BUSY || reason == WifiP2pManager.ERROR) {
                        wifiDirectManager.cancelConnect {
                            viewModelScope.launch {
                                delay(700)
                                if (_currentScreen.value == AppScreen.RADAR) {
                                    wifiDirectManager.connectToDevice(
                                        device = device,
                                        groupOwnerIntent = 0,
                                        onFailure = { retryReason ->
                                            _connectingDeviceAddress.value = null
                                            _transferError.value = "Connect failed (${reasonText(retryReason)})"
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        _connectingDeviceAddress.value = null
                        _transferError.value = "Connect failed (${reasonText(reason)})"
                    }
                }
            )
        }

        if (device.status == WifiP2pDevice.INVITED) {
            wifiDirectManager.cancelConnect {
                viewModelScope.launch {
                    delay(500)
                    if (_currentScreen.value == AppScreen.RADAR) {
                        initiateConnect()
                    }
                }
            }
        } else {
            initiateConnect()
        }
    }

    private fun reasonText(reason: Int): String = when (reason) {
        WifiP2pManager.BUSY -> "Busy"
        WifiP2pManager.P2P_UNSUPPORTED -> "Unsupported"
        else -> "Error"
    }

    private fun formatServiceNotificationText(
        files: List<FileItem>,
        stats: TransferStats
    ): String {
        val percent = (stats.overallProgress * 100f).toInt().coerceIn(0, 100)
        val completedCount = files.count { it.status == TransferStatus.COMPLETED }
        val totalCount = files.size
        val activeFile = files.firstOrNull { it.status == TransferStatus.TRANSFERRING }
        val speedPart = if (stats.speedBytesPerSec > 0L) "${formatBytes(stats.speedBytesPerSec)}/s • " else ""
        return when {
            activeFile != null && totalCount > 0 ->
                "${activeFile.name} ($completedCount/$totalCount) • $speedPart$percent%"
            totalCount > 0 ->
                "$completedCount/$totalCount files • $speedPart$percent%"
            else -> "Preparing transfer..."
        }
    }

    private fun startTransferForegroundService(
        direction: TransferDirection,
        files: List<FileItem>,
        stats: TransferStats
    ) {
        lastNotificationUpdateAt = System.currentTimeMillis()
        val percent = (stats.overallProgress * 100f).toInt().coerceIn(0, 100)
        TransferForegroundService.start(
            context = getApplication(),
            direction = direction,
            content = formatServiceNotificationText(files, stats),
            progressPercent = percent
        )
    }

    private fun updateTransferForegroundService(
        direction: TransferDirection,
        files: List<FileItem>,
        stats: TransferStats
    ) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateAt < 1000L) return
        lastNotificationUpdateAt = now
        val percent = (stats.overallProgress * 100f).toInt().coerceIn(0, 100)
        TransferForegroundService.update(
            context = getApplication(),
            direction = direction,
            content = formatServiceNotificationText(files, stats),
            progressPercent = percent
        )
    }

    private fun stopTransferForegroundService() {
        TransferForegroundService.stop(getApplication())
    }

    private fun handleTransferComplete(
        success: Boolean,
        error: String?,
        clearConnecting: Boolean
    ) {
        if (clearConnecting) {
            _connectingDeviceAddress.value = null
        }
        _isTransferActive.value = false
        _isTransferComplete.value = success
        stopTransferForegroundService()
        if (isAppInBackground) {
            stopBackgroundActivity()
        }
        if (!success && error != null && !error.contains("closed", ignoreCase = true)) {
            _transferError.value = error
        }
    }

    private fun startSendingFiles(isGroupOwner: Boolean, hostAddress: String) {
        if (_isTransferActive.value) return
        _isTransferActive.value = true
        _isTransferComplete.value = false
        _transferError.value = null
        startTransferForegroundService(
            direction = TransferDirection.SEND,
            files = _selectedFiles.value,
            stats = TransferStats(totalBytes = _selectedFiles.value.sumOf { it.sizeBytes })
        )

        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            transferEngine.sendFiles(
                isGroupOwner = isGroupOwner,
                hostAddress = hostAddress,
                files = _selectedFiles.value,
                onTransferStarted = { initialFiles, initialStats ->
                    _connectingDeviceAddress.value = null
                    _transferFiles.value = initialFiles
                    _transferStats.value = initialStats
                    _currentScreen.value = AppScreen.TRANSFER
                    updateTransferForegroundService(TransferDirection.SEND, initialFiles, initialStats)
                },
                onProgress = { updatedFiles, stats ->
                    _transferFiles.value = updatedFiles
                    _transferStats.value = stats
                    updateTransferForegroundService(TransferDirection.SEND, updatedFiles, stats)
                },
                onComplete = { success, error ->
                    handleTransferComplete(success, error, clearConnecting = true)
                }
            )
        }
    }

    private fun startReceivingFiles(isGroupOwner: Boolean, hostAddress: String) {
        if (_isTransferActive.value) return
        _isTransferActive.value = true
        _isTransferComplete.value = false
        _transferError.value = null
        _transferFiles.value = emptyList()
        _transferStats.value = TransferStats()
        startTransferForegroundService(
            direction = TransferDirection.RECEIVE,
            files = emptyList(),
            stats = TransferStats()
        )

        transferEngine.stopTransfer()
        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            transferEngine.receiveFiles(
                isGroupOwner = isGroupOwner,
                hostAddress = hostAddress,
                onTransferStarted = { initialFiles, initialStats ->
                    _transferFiles.value = initialFiles
                    _transferStats.value = initialStats
                    _currentScreen.value = AppScreen.TRANSFER
                    updateTransferForegroundService(TransferDirection.RECEIVE, initialFiles, initialStats)
                },
                onProgress = { updatedFiles, stats ->
                    _transferFiles.value = updatedFiles
                    _transferStats.value = stats
                    updateTransferForegroundService(TransferDirection.RECEIVE, updatedFiles, stats)
                },
                onComplete = { success, error ->
                    handleTransferComplete(success, error, clearConnecting = false)
                }
            )
        }
    }

    fun cancelTransferringFile(fileId: String) {
        transferEngine.cancelFile(fileId)
        _transferFiles.update { list ->
            list.map { item ->
                if (item.id == fileId) {
                    item.copy(status = TransferStatus.CANCELLED)
                } else item
            }
        }
    }

    fun cancelAllTransfers() {
        transferEngine.stopTransfer()
        transferJob?.cancel()
        _isTransferActive.value = false
        stopTransferForegroundService()
        if (isAppInBackground) {
            stopBackgroundActivity()
        }
        _transferFiles.update { list ->
            list.map {
                if (it.status == TransferStatus.TRANSFERRING || it.status == TransferStatus.PENDING) {
                    it.copy(status = TransferStatus.CANCELLED)
                } else it
            }
        }
    }

    fun onAppForegrounded() {
        isAppInBackground = false
        if (!_isTransferActive.value) {
            stopTransferForegroundService()
        }
        TempFileCacheManager.openSession(getApplication(), cleanStale = false)
        wifiDirectManager.ensureRegistered()
        ensurePickerFilesLoaded()
        persistTempCache(includeMedia = true, includeFiles = true, includeApps = true)
    }

    fun onAppBackgrounded() {
        isAppInBackground = true
        cacheBuildJob?.cancel()
        PickerThumbnailCache.clearMemoryCache()
        if (_isTransferActive.value || TransferForegroundService.isRunning.value) {
            // Keep running in background only while the foreground transfer service is running
            updateTransferForegroundService(_transferDirection.value, _transferFiles.value, _transferStats.value)
            return
        }

        // Foreground service is not running: close background work immediately
        stopTransferForegroundService()
        resetToHome()
        wifiDirectManager.unregister()
        TempFileCacheManager.closeSessionAsync(getApplication())
    }

    private fun stopBackgroundActivity() {
        connectTimeoutJob?.cancel()
        stopDiscoveryLoop()
        wifiDirectManager.stopDiscovery()
        wifiDirectManager.disconnect()
        wifiDirectManager.unregister()
        TempFileCacheManager.closeSessionAsync(getApplication())
    }

    fun resetToHome() {
        connectTimeoutJob?.cancel()
        stopDiscoveryLoop()
        cancelAllTransfers()
        wifiDirectManager.stopDiscovery()
        wifiDirectManager.disconnect()
        _connectingDeviceAddress.value = null
        _selectedFiles.value = emptyList()
        _transferFiles.value = emptyList()
        _transferStats.value = TransferStats()
        _isTransferComplete.value = false
        _transferError.value = null
        _currentScreen.value = if (_isProfileCompleted.value) AppScreen.HOME else AppScreen.PROFILE
    }

    override fun onCleared() {
        super.onCleared()
        cacheBuildJob?.cancel()
        stopDiscoveryLoop()
        cancelAllTransfers()
        stopTransferForegroundService()
        wifiDirectManager.unregister()
        PickerThumbnailCache.clearMemoryCache()
        TempFileCacheManager.closeSessionBlocking(getApplication())
    }
}
