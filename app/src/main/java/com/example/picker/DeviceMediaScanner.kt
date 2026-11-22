package com.example.picker

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import com.example.model.FileItem
import com.example.model.TransferStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class PickerCategory(val label: String) {
    IMAGE("Image"),
    VIDEO("Video"),
    MUSIC("Music"),
    FILES("Files"),
    APPS("Apps")
}

data class PickerMediaItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val uriString: String,
    val mimeType: String,
    val durationMs: Long = 0L,
    val artist: String = "",
    val dateModifiedSec: Long = 0L,
    val category: PickerCategory
)

data class PickerAppItem(
    val id: String,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val apkPath: String,
    val uriString: String,
    val sizeBytes: Long,
    val isStandaloneApk: Boolean = false,
    val fileNameOverride: String? = null
) {
    constructor(
        packageName: String,
        appName: String,
        versionName: String,
        apkPath: String,
        sizeBytes: Long
    ) : this(
        id = "app:$packageName",
        packageName = packageName,
        appName = appName,
        versionName = versionName,
        apkPath = apkPath,
        uriString = Uri.fromFile(File(apkPath)).toString(),
        sizeBytes = sizeBytes,
        isStandaloneApk = false,
        fileNameOverride = null
    )

    val shareFileName: String
        get() {
            if (!fileNameOverride.isNullOrBlank()) return fileNameOverride
            val cleanName = appName.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim()
                .ifEmpty { packageName }
            val cleanVersion = versionName.replace(Regex("[^a-zA-Z0-9._-]"), "").trim()
            return if (cleanVersion.isNotEmpty()) "${cleanName}_v${cleanVersion}.apk" else "${cleanName}.apk"
        }
}

fun PickerMediaItem.toFileItem(): FileItem = FileItem(
    id = UUID.randomUUID().toString(),
    name = name,
    sizeBytes = sizeBytes,
    uriString = uriString,
    mimeType = mimeType,
    status = TransferStatus.PENDING
)

fun PickerAppItem.toFileItem(): FileItem = FileItem(
    id = UUID.randomUUID().toString(),
    name = shareFileName,
    sizeBytes = sizeBytes,
    uriString = uriString,
    mimeType = "application/vnd.android.package-archive",
    status = TransferStatus.PENDING
)

object PickerThumbnailCache {
    private const val IMAGE_THUMB_PX = 250
    private val cache = object : LruCache<String, ImageBitmap>(120) {}

    fun getCached(key: String): ImageBitmap? = cache.get(key)

    suspend fun loadImageThumbnail(context: Context, uriString: String): ImageBitmap? {
        val key = "img:$uriString"
        cache.get(key)?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    uri.scheme == ContentResolver.SCHEME_CONTENT
                ) {
                    try {
                        context.contentResolver.loadThumbnail(
                            uri,
                            Size(IMAGE_THUMB_PX, IMAGE_THUMB_PX),
                            null
                        )
                    } catch (_: Exception) {
                        decodeSampledBitmap(context, uri, IMAGE_THUMB_PX, IMAGE_THUMB_PX)
                    }
                } else {
                    decodeSampledBitmap(context, uri, IMAGE_THUMB_PX, IMAGE_THUMB_PX)
                } ?: return@withContext null

                // Ensure exact 250px x 250px center-cropped thumbnail
                if (decoded.width == IMAGE_THUMB_PX && decoded.height == IMAGE_THUMB_PX) {
                    decoded
                } else {
                    ThumbnailUtils.extractThumbnail(
                        decoded,
                        IMAGE_THUMB_PX,
                        IMAGE_THUMB_PX
                    )
                }
            } catch (_: Exception) {
                null
            }
        }

        val imageBitmap = bitmap?.asImageBitmap()
        if (imageBitmap != null) {
            cache.put(key, imageBitmap)
        }
        return imageBitmap
    }

    suspend fun loadAppIcon(context: Context, item: PickerAppItem): ImageBitmap? {
        val key = "app:${item.id}"
        cache.get(key)?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val drawable = if (!item.isStandaloneApk && item.packageName.isNotBlank()) {
                    pm.getApplicationIcon(item.packageName)
                } else {
                    val directPath = item.apkPath.takeIf { it.isNotBlank() && File(it).canRead() }
                    if (directPath != null) {
                        loadIconFromApkPath(pm, directPath)
                    } else if (item.uriString.isNotBlank()) {
                        loadIconFromContentUri(context, pm, Uri.parse(item.uriString))
                    } else {
                        null
                    }
                }
                drawable?.let { drawableToBitmap(it, 160) }
            } catch (_: Exception) {
                null
            }
        }

        val imageBitmap = bitmap?.asImageBitmap()
        if (imageBitmap != null) {
            cache.put(key, imageBitmap)
        }
        return imageBitmap
    }

    private fun loadIconFromApkPath(pm: PackageManager, apkPath: String): Drawable? {
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkPath, 0)
        }
        return pkgInfo?.applicationInfo?.let { appInfo ->
            appInfo.sourceDir = apkPath
            appInfo.publicSourceDir = apkPath
            appInfo.loadIcon(pm)
        }
    }

    private fun loadIconFromContentUri(context: Context, pm: PackageManager, uri: Uri): Drawable? {
        val tempFile = File.createTempFile("apk_icon_", ".apk", context.cacheDir)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            loadIconFromApkPath(pm, tempFile.absolutePath)
        } catch (_: Exception) {
            null
        } finally {
            tempFile.delete()
        }
    }

    private fun decodeSampledBitmap(
        context: Context,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        var inSampleSize = 1
        val halfHeight = boundsOptions.outHeight / 2
        val halfWidth = boundsOptions.outWidth / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.RGB_565
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val bmp = drawable.bitmap
            if (bmp.width == sizePx && bmp.height == sizePx) return bmp
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}

object DeviceMediaScanner {

    private const val PROGRESS_EMIT_INTERVAL_MS = 120L
    private const val FIRST_BATCH_COUNT = 12
    private const val SUBSEQUENT_BATCH_COUNT = 36

    fun hasAnyMediaReadPermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val fullImages = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                val partialVisual = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
                val fullVideo = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                val fullAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                fullImages || partialVisual || fullVideo || fullAudio
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    suspend fun scanMediaFilesProgressively(
        context: Context,
        onBatch: (images: List<PickerMediaItem>, videos: List<PickerMediaItem>, music: List<PickerMediaItem>) -> Unit
    ) = withContext(Dispatchers.IO) {
        val images = mutableListOf<PickerMediaItem>()
        val videos = mutableListOf<PickerMediaItem>()
        val music = mutableListOf<PickerMediaItem>()

        val seenSignature = HashSet<String>()
        val seenPaths = HashSet<String>()

        var totalEmittedCount = 0
        var lastEmitAt = 0L

        fun emitSnapshot(force: Boolean = false) {
            val totalNow = images.size + videos.size + music.size
            val now = System.currentTimeMillis()
            val delta = totalNow - totalEmittedCount
            val shouldEmit = force ||
                (totalEmittedCount == 0 && delta >= FIRST_BATCH_COUNT) ||
                (delta >= SUBSEQUENT_BATCH_COUNT) ||
                (delta > 0 && now - lastEmitAt >= PROGRESS_EMIT_INTERVAL_MS)
            if (!shouldEmit) return

            totalEmittedCount = totalNow
            lastEmitAt = now
            onBatch(images.toList(), videos.toList(), music.toList())
        }

        val volumes = getMediaStoreVolumes(context)
        for (volume in volumes) {
            queryMediaFromVolume(context, volume, PickerCategory.IMAGE, images, seenPaths, seenSignature) {
                emitSnapshot(force = false)
            }
            emitSnapshot(force = true)

            queryMediaFromVolume(context, volume, PickerCategory.VIDEO, videos, seenPaths, seenSignature) {
                emitSnapshot(force = false)
            }
            emitSnapshot(force = true)

            queryMediaFromVolume(context, volume, PickerCategory.MUSIC, music, seenPaths, seenSignature) {
                emitSnapshot(force = false)
            }
            emitSnapshot(force = true)
        }

        // Supplementary scan across all Internal + SD Card directories for unindexed files
        scanStorageRootsFallback(context, images, videos, music, seenPaths, seenSignature) {
            emitSnapshot(force = false)
        }

        onBatch(
            images.sortedByDescending { it.dateModifiedSec },
            videos.sortedByDescending { it.dateModifiedSec },
            music.sortedByDescending { it.dateModifiedSec }
        )
    }

    suspend fun scanFilesProgressively(
        context: Context,
        onBatch: (files: List<PickerMediaItem>) -> Unit
    ) = withContext(Dispatchers.IO) {
        val files = mutableListOf<PickerMediaItem>()
        val seenPaths = HashSet<String>()
        val seenSig = HashSet<String>()

        var lastEmittedCount = 0
        var lastEmitAt = 0L

        fun emitSnapshot(force: Boolean = false) {
            val totalNow = files.size
            val now = System.currentTimeMillis()
            val delta = totalNow - lastEmittedCount
            val shouldEmit = force ||
                (lastEmittedCount == 0 && delta >= FIRST_BATCH_COUNT) ||
                (delta >= SUBSEQUENT_BATCH_COUNT) ||
                (delta > 0 && now - lastEmitAt >= PROGRESS_EMIT_INTERVAL_MS)
            if (!shouldEmit) return

            lastEmittedCount = totalNow
            lastEmitAt = now
            onBatch(files.toList())
        }

        for (volume in getMediaStoreVolumes(context)) {
            queryFilesFromVolume(context, volume, files, seenPaths, seenSig) {
                emitSnapshot(force = false)
            }
            emitSnapshot(force = true)
        }

        scanFilesStorageRootsFallback(context, files, seenPaths, seenSig) {
            emitSnapshot(force = false)
        }

        onBatch(files.sortedByDescending { it.dateModifiedSec })
    }

    suspend fun scanAppsAndApksProgressively(
        context: Context,
        onBatch: (installedApps: List<PickerAppItem>, apkFiles: List<PickerAppItem>) -> Unit
    ) = withContext(Dispatchers.IO) {
        val installed = mutableListOf<PickerAppItem>()
        val apks = mutableListOf<PickerAppItem>()
        val seenPackages = HashSet<String>()
        val seenApkPaths = HashSet<String>()
        val seenApkSig = HashSet<String>()

        var lastEmittedTotal = 0
        var lastEmitAt = 0L

        fun emitSnapshot(force: Boolean = false, sortLists: Boolean = false) {
            val totalNow = installed.size + apks.size
            val now = System.currentTimeMillis()
            val delta = totalNow - lastEmittedTotal
            val shouldEmit = force ||
                (lastEmittedTotal == 0 && delta >= 8) ||
                delta >= 15 ||
                (delta > 0 && now - lastEmitAt >= PROGRESS_EMIT_INTERVAL_MS)
            if (!shouldEmit) return

            lastEmittedTotal = totalNow
            lastEmitAt = now
            val installedSnap = if (sortLists) installed.sortedBy { it.appName.lowercase() } else installed.toList()
            val apksSnap = if (sortLists) apks.sortedBy { it.appName.lowercase() } else apks.toList()
            onBatch(installedSnap, apksSnap)
        }

        // 1. Load installed apps first and emit immediately as they load
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        for (info in resolveInfos) {
            val activityInfo = info.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (!seenPackages.add(pkg)) continue

            try {
                val appInfo = activityInfo.applicationInfo
                val apkPath = appInfo.publicSourceDir ?: appInfo.sourceDir ?: continue
                val apkFile = File(apkPath)
                if (!apkFile.exists() || !apkFile.canRead()) continue
                val sizeBytes = apkFile.length()
                if (sizeBytes <= 0L) continue

                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                val version = pkgInfo.versionName ?: ""

                installed.add(
                    PickerAppItem(
                        packageName = pkg,
                        appName = label,
                        versionName = version,
                        apkPath = apkPath,
                        sizeBytes = sizeBytes
                    )
                )
                emitSnapshot(force = false)
            } catch (_: Exception) {
            }
        }
        emitSnapshot(force = true, sortLists = true)

        // 2. Auto-scan MediaStore Downloads & Files across all storage volumes (shown below installed apps)
        for (volume in getMediaStoreVolumes(context)) {
            queryApksFromVolume(context, volume, apks, seenApkPaths, seenApkSig) {
                emitSnapshot(force = false)
            }
            emitSnapshot(force = true)
        }

        // 3. Auto-scan all accessible folders across internal storage and SD cards for .apk files
        scanApkStorageRootsFallback(context, apks, seenApkPaths, seenApkSig) {
            emitSnapshot(force = false)
        }

        onBatch(
            installed.sortedBy { it.appName.lowercase() },
            apks.sortedBy { it.appName.lowercase() }
        )
    }

    private fun queryApksFromVolume(
        context: Context,
        volume: String,
        out: MutableList<PickerAppItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>,
        onItemAdded: () -> Unit = {}
    ) {
        val collections = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Downloads.getContentUri(volume))
            }
            add(MediaStore.Files.getContentUri(volume))
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA
        )
        val filteredSelection =
            "${MediaStore.MediaColumns.SIZE} > 0 AND (" +
                "${MediaStore.MediaColumns.MIME_TYPE} = 'application/vnd.android.package-archive' OR " +
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.apk' OR " +
                "${MediaStore.MediaColumns.DATA} LIKE '%.apk')"

        for (collection in collections) {
            val cursor = try {
                context.contentResolver.query(
                    collection,
                    projection,
                    filteredSelection,
                    null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )
            } catch (_: Exception) {
                try {
                    context.contentResolver.query(
                        collection,
                        projection,
                        "${MediaStore.MediaColumns.SIZE} > 0",
                        null,
                        "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                    )
                } catch (_: Exception) {
                    null
                }
            }
            try {
                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val titleCol = c.getColumnIndex(MediaStore.MediaColumns.TITLE)
                    val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val dataCol = c.getColumnIndex(MediaStore.MediaColumns.DATA)

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val displayName = if (nameCol != -1) c.getString(nameCol) else null
                        val title = if (titleCol != -1) c.getString(titleCol) else null
                        val mime = if (mimeCol != -1) c.getString(mimeCol) else null
                        val dataPath = if (dataCol != -1) c.getString(dataCol) else null
                        val size = if (sizeCol != -1) c.getLong(sizeCol) else 0L
                        if (size <= 0L) continue

                        val isApk = (displayName?.endsWith(".apk", ignoreCase = true) == true) ||
                            (title?.endsWith(".apk", ignoreCase = true) == true) ||
                            (dataPath?.endsWith(".apk", ignoreCase = true) == true) ||
                            mime.equals("application/vnd.android.package-archive", ignoreCase = true)
                        if (!isApk) continue

                        val rawName = displayName
                            ?: dataPath?.substringAfterLast('/')
                            ?: title?.let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }
                            ?: "app_$id.apk"
                        val fileName = if (rawName.endsWith(".apk", ignoreCase = true)) rawName else "$rawName.apk"
                        val uri = ContentUris.withAppendedId(collection, id)
                        if (!dataPath.isNullOrBlank()) seenPaths.add(dataPath)
                        val sig = "apk:${fileName.lowercase()}_${size}"
                        if (seenSig.add(sig)) {
                            val displayLabel = resolveApkDisplayLabel(context, dataPath, fileName)
                            out.add(
                                PickerAppItem(
                                    id = "apk:$volume:${collection.lastPathSegment}:$id",
                                    packageName = "",
                                    appName = displayLabel,
                                    versionName = "",
                                    apkPath = dataPath ?: "",
                                    uriString = uri.toString(),
                                    sizeBytes = size,
                                    isStandaloneApk = true,
                                    fileNameOverride = fileName
                                )
                            )
                            onItemAdded()
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun scanApkStorageRootsFallback(
        context: Context,
        out: MutableList<PickerAppItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>,
        onItemAdded: () -> Unit = {}
    ) {
        val priorityDirs = LinkedHashMap<String, File>()
        try {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloads != null && downloads.exists() && downloads.canRead()) {
                val bigShareDir = File(downloads, "Big Share")
                if (bigShareDir.exists() && bigShareDir.canRead()) {
                    priorityDirs[bigShareDir.absolutePath] = bigShareDir
                }
                priorityDirs[downloads.absolutePath] = downloads
            }
        } catch (_: Exception) {}
        try {
            @Suppress("DEPRECATION")
            val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (documents != null && documents.exists() && documents.canRead()) {
                priorityDirs[documents.absolutePath] = documents
            }
        } catch (_: Exception) {}

        val commonApkSubdirs = listOf("Download", "Downloads", "APKs", "Apk", "Shareit", "Xender", "Bluetooth", "Telegram", "WhatsApp", "Android/media")
        val roots = collectStorageRoots(context)
        for (rootDir in roots) {
            for (sub in commonApkSubdirs) {
                val candidate = File(rootDir, sub)
                if (candidate.exists() && candidate.isDirectory && candidate.canRead()) {
                    priorityDirs.putIfAbsent(candidate.absolutePath, candidate)
                }
            }
        }

        val visitedDirs = HashSet<String>()
        for (dir in priorityDirs.values) {
            walkApkDirectory(context, dir, 0, out, seenPaths, seenSig, visitedDirs, onItemAdded)
        }
        for (rootDir in roots) {
            walkApkDirectory(context, rootDir, 0, out, seenPaths, seenSig, visitedDirs, onItemAdded)
        }
    }

    private fun canonicalDirKey(dir: File): String {
        return try {
            dir.canonicalPath
        } catch (_: Exception) {
            dir.absolutePath
        }
    }

    private fun walkApkDirectory(
        context: Context,
        dir: File,
        depth: Int,
        out: MutableList<PickerAppItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>,
        visitedDirs: MutableSet<String>,
        onItemAdded: () -> Unit = {}
    ) {
        if (depth > 12) return
        if (!visitedDirs.add(canonicalDirKey(dir))) return
        val children = dir.listFiles() ?: return
        val isAndroidDir = dir.name.equals("Android", ignoreCase = true)
        for (file in children) {
            val name = file.name
            if (name.startsWith(".")) continue
            if (file.isDirectory) {
                if (isAndroidDir && (name.equals("data", ignoreCase = true) || name.equals("obb", ignoreCase = true))) {
                    continue
                }
                walkApkDirectory(context, file, depth + 1, out, seenPaths, seenSig, visitedDirs, onItemAdded)
            } else if (file.isFile && file.canRead() && file.length() > 0L && name.endsWith(".apk", ignoreCase = true)) {
                val absPath = file.absolutePath
                if (seenPaths.contains(absPath)) continue
                val sig = "apk:${name.lowercase()}_${file.length()}"
                if (seenPaths.add(absPath) && seenSig.add(sig)) {
                    val displayLabel = resolveApkDisplayLabel(context, absPath, name)
                    out.add(
                        PickerAppItem(
                            id = "apkfs:$absPath",
                            packageName = "",
                            appName = displayLabel,
                            versionName = "",
                            apkPath = absPath,
                            uriString = Uri.fromFile(file).toString(),
                            sizeBytes = file.length(),
                            isStandaloneApk = true,
                            fileNameOverride = name
                        )
                    )
                    onItemAdded()
                }
            }
        }
    }

    private fun resolveApkDisplayLabel(context: Context, apkPath: String?, fallbackName: String): String {
        if (apkPath.isNullOrBlank()) return fallbackName
        return try {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkPath, 0)
            }
            val appInfo = pkgInfo?.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = apkPath
                appInfo.publicSourceDir = apkPath
                appInfo.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackName
            } else {
                fallbackName
            }
        } catch (_: Exception) {
            fallbackName
        }
    }

    private fun getMediaStoreVolumes(context: Context): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val set = MediaStore.getExternalVolumeNames(context)
                if (set.isEmpty()) listOf(MediaStore.VOLUME_EXTERNAL_PRIMARY) else set.toList()
            } catch (_: Exception) {
                listOf("external")
            }
        } else {
            listOf("external")
        }
    }

    private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val videoExts = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "ts")
    private val audioExts = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "wma")

    private fun queryMediaFromVolume(
        context: Context,
        volume: String,
        category: PickerCategory,
        out: MutableList<PickerMediaItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>,
        onItemAdded: () -> Unit = {}
    ) {
        val collection = when (category) {
            PickerCategory.IMAGE -> MediaStore.Images.Media.getContentUri(volume)
            PickerCategory.VIDEO -> MediaStore.Video.Media.getContentUri(volume)
            PickerCategory.MUSIC -> MediaStore.Audio.Media.getContentUri(volume)
            PickerCategory.FILES, PickerCategory.APPS -> return
        }
        val projection = when (category) {
            PickerCategory.IMAGE -> arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATA
            )
            PickerCategory.VIDEO -> arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATA,
                MediaStore.Video.Media.DURATION
            )
            PickerCategory.MUSIC -> arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST
            )
            PickerCategory.FILES, PickerCategory.APPS -> return
        }
        val prefix = when (category) {
            PickerCategory.IMAGE -> "img"
            PickerCategory.VIDEO -> "vid"
            PickerCategory.MUSIC -> "aud"
            PickerCategory.FILES, PickerCategory.APPS -> ""
        }
        val defaultExt = when (category) {
            PickerCategory.IMAGE -> "jpg"
            PickerCategory.VIDEO -> "mp4"
            PickerCategory.MUSIC -> "mp3"
            PickerCategory.FILES, PickerCategory.APPS -> ""
        }
        val defaultMime = when (category) {
            PickerCategory.IMAGE -> "image/*"
            PickerCategory.VIDEO -> "video/*"
            PickerCategory.MUSIC -> "audio/*"
            PickerCategory.FILES, PickerCategory.APPS -> "*/*"
        }

        try {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.MediaColumns.SIZE} > 0",
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val modCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val durCol = if (category != PickerCategory.IMAGE) {
                    cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                } else -1
                val titleCol = if (category == PickerCategory.MUSIC) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                } else -1
                val artistCol = if (category == PickerCategory.MUSIC) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    if (size <= 0L) continue
                    val displayName = if (nameCol != -1) cursor.getString(nameCol) else null
                    val title = if (titleCol != -1) cursor.getString(titleCol) else null
                    val name = displayName ?: title ?: "${prefix}_$id.$defaultExt"
                    val rawArtist = if (artistCol != -1) cursor.getString(artistCol).orEmpty() else ""
                    val artist = if (rawArtist == "<unknown>") "" else rawArtist
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: defaultMime else defaultMime
                    val duration = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val mod = if (modCol != -1) cursor.getLong(modCol) else 0L
                    val dataPath = if (dataCol != -1) cursor.getString(dataCol) else null

                    if (!dataPath.isNullOrBlank()) seenPaths.add(dataPath)
                    val sig = "$prefix:${name}_${size}"
                    if (seenSig.add(sig)) {
                        out.add(
                            PickerMediaItem(
                                id = "$prefix:$volume:$id",
                                name = name,
                                sizeBytes = size,
                                uriString = ContentUris.withAppendedId(collection, id).toString(),
                                mimeType = mime,
                                durationMs = duration,
                                artist = artist,
                                dateModifiedSec = mod,
                                category = category
                            )
                        )
                        onItemAdded()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun queryFilesFromVolume(
        context: Context,
        volume: String,
        out: MutableList<PickerMediaItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>,
        onItemAdded: () -> Unit = {}
    ) {
        val collections = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Downloads.getContentUri(volume))
            }
            add(MediaStore.Files.getContentUri(volume))
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATA
        )

        for (collection in collections) {
            val cursor = try {
                context.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.MediaColumns.SIZE} > 0",
                    null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )
            } catch (_: Exception) {
                null
            }
            try {
                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val titleCol = c.getColumnIndex(MediaStore.MediaColumns.TITLE)
                    val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val modCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    val dataCol = c.getColumnIndex(MediaStore.MediaColumns.DATA)

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val size = if (sizeCol != -1) c.getLong(sizeCol) else 0L
                        if (size <= 0L) continue
                        val displayName = if (nameCol != -1) c.getString(nameCol) else null
                        val title = if (titleCol != -1) c.getString(titleCol) else null
                        val dataPath = if (dataCol != -1) c.getString(dataCol) else null
                        val mime = if (mimeCol != -1) c.getString(mimeCol) else null
                        val mod = if (modCol != -1) c.getLong(modCol) else 0L

                        val name = displayName
                            ?: dataPath?.substringAfterLast('/')
                            ?: title
                            ?: continue
                        if (name.startsWith(".")) continue
                        if (isMediaOrApkFile(name, mime)) continue

                        if (!dataPath.isNullOrBlank()) seenPaths.add(dataPath)
                        val sig = "file:${name.lowercase()}_${size}"
                        if (seenSig.add(sig)) {
                            out.add(
                                PickerMediaItem(
                                    id = "file:$volume:${collection.lastPathSegment}:$id",
                                    name = name,
                                    sizeBytes = size,
                                    uriString = ContentUris.withAppendedId(collection, id).toString(),
                                    mimeType = mime?.takeIf { it.isNotBlank() } ?: guessDocumentMime(name),
                                    dateModifiedSec = mod,
                                    category = PickerCategory.FILES
                                )
                            )
                            onItemAdded()
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun isMediaOrApkFile(name: String, mime: String?): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in imageExts || ext in videoExts || ext in audioExts || ext == "apk") return true
        val lowerMime = mime?.lowercase().orEmpty()
        return lowerMime.startsWith("image/") ||
            lowerMime.startsWith("video/") ||
            lowerMime.startsWith("audio/") ||
            lowerMime == "application/vnd.android.package-archive"
    }

    private fun guessDocumentMime(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "txt", "log", "md", "csv" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "application/octet-stream"
        }
    }

    private fun scanFilesStorageRootsFallback(
        context: Context,
        out: MutableList<PickerMediaItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>,
        onItemAdded: () -> Unit = {}
    ) {
        val priorityDirs = LinkedHashMap<String, File>()
        try {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloads != null && downloads.exists() && downloads.canRead()) {
                val bigShareDir = File(downloads, "Big Share")
                if (bigShareDir.exists() && bigShareDir.canRead()) {
                    priorityDirs[canonicalDirKey(bigShareDir)] = bigShareDir
                }
                priorityDirs[canonicalDirKey(downloads)] = downloads
            }
        } catch (_: Exception) {}
        try {
            @Suppress("DEPRECATION")
            val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (documents != null && documents.exists() && documents.canRead()) {
                priorityDirs[canonicalDirKey(documents)] = documents
            }
        } catch (_: Exception) {}

        val roots = collectStorageRoots(context)
        val commonSubdirs = listOf("Download", "Downloads", "Documents", "Big Share", "Bluetooth", "Telegram", "WhatsApp", "Shareit", "Xender", "Android/media")
        for (rootDir in roots) {
            for (sub in commonSubdirs) {
                val candidate = File(rootDir, sub)
                if (candidate.exists() && candidate.isDirectory && candidate.canRead()) {
                    priorityDirs.putIfAbsent(canonicalDirKey(candidate), candidate)
                }
            }
        }

        val visitedDirs = HashSet<String>()
        for (dir in priorityDirs.values) {
            walkFilesDirectory(dir, 0, out, seenPaths, seenSig, visitedDirs, onItemAdded)
        }
        for (rootDir in roots) {
            walkFilesDirectory(rootDir, 0, out, seenPaths, seenSig, visitedDirs, onItemAdded)
        }
    }

    private fun walkFilesDirectory(
        dir: File,
        depth: Int,
        out: MutableList<PickerMediaItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>,
        visitedDirs: MutableSet<String>,
        onItemAdded: () -> Unit = {}
    ) {
        if (depth > 12) return
        if (!visitedDirs.add(canonicalDirKey(dir))) return
        val children = dir.listFiles() ?: return
        val isAndroidDir = dir.name.equals("Android", ignoreCase = true)
        for (file in children) {
            val name = file.name
            if (name.startsWith(".")) continue
            if (file.isDirectory) {
                if (isAndroidDir && (name.equals("data", ignoreCase = true) || name.equals("obb", ignoreCase = true))) {
                    continue
                }
                walkFilesDirectory(file, depth + 1, out, seenPaths, seenSig, visitedDirs, onItemAdded)
            } else if (file.isFile && file.canRead() && file.length() > 0L) {
                if (isMediaOrApkFile(name, null)) continue
                val absPath = file.absolutePath
                if (seenPaths.contains(absPath)) continue
                val sig = "file:${name.lowercase()}_${file.length()}"
                if (seenPaths.add(absPath) && seenSig.add(sig)) {
                    out.add(
                        PickerMediaItem(
                            id = "filefs:$absPath",
                            name = name,
                            sizeBytes = file.length(),
                            uriString = Uri.fromFile(file).toString(),
                            mimeType = guessDocumentMime(name),
                            dateModifiedSec = file.lastModified() / 1000L,
                            category = PickerCategory.FILES
                        )
                    )
                    onItemAdded()
                }
            }
        }
    }

    private fun scanStorageRootsFallback(
        context: Context,
        images: MutableList<PickerMediaItem>,
        videos: MutableList<PickerMediaItem>,
        music: MutableList<PickerMediaItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>,
        onItemAdded: () -> Unit = {}
    ) {
        val visitedDirs = HashSet<String>()
        for (rootDir in collectStorageRoots(context)) {
            walkMediaDirectory(
                dir = rootDir,
                depth = 0,
                images = images,
                videos = videos,
                music = music,
                seenPaths = seenPaths,
                seenSig = seenSig,
                visitedDirs = visitedDirs,
                onItemAdded = onItemAdded
            )
        }
    }

    private fun collectStorageRoots(context: Context): List<File> {
        val roots = LinkedHashMap<String, File>()

        fun addRoot(dir: File?) {
            if (dir != null && dir.exists() && dir.isDirectory && dir.canRead()) {
                roots.putIfAbsent(canonicalDirKey(dir), dir)
            }
        }

        try {
            addRoot(Environment.getExternalStorageDirectory())
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                sm?.storageVolumes?.forEach { vol ->
                    addRoot(vol.directory)
                }
            } catch (_: Exception) {}
        }

        try {
            context.getExternalFilesDirs(null).forEach { appDir ->
                val path = appDir?.absolutePath ?: return@forEach
                val androidIdx = path.indexOf("/Android/")
                if (androidIdx > 0) {
                    addRoot(File(path.substring(0, androidIdx)))
                }
            }
        } catch (_: Exception) {}

        try {
            val storageMount = File("/storage")
            storageMount.listFiles()?.forEach { child ->
                val name = child.name
                if (name != "self" && name != "emulated") {
                    addRoot(child)
                }
            }
        } catch (_: Exception) {}

        return roots.values.toList()
    }

    private fun walkMediaDirectory(
        dir: File,
        depth: Int,
        images: MutableList<PickerMediaItem>,
        videos: MutableList<PickerMediaItem>,
        music: MutableList<PickerMediaItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>,
        visitedDirs: MutableSet<String>,
        onItemAdded: () -> Unit = {}
    ) {
        if (depth > 12) return
        if (!visitedDirs.add(canonicalDirKey(dir))) return
        val children = dir.listFiles() ?: return
        val isAndroidDir = dir.name.equals("Android", ignoreCase = true)
        for (file in children) {
            val name = file.name
            if (name.startsWith(".")) continue
            if (file.isDirectory) {
                if (isAndroidDir && (name.equals("data", ignoreCase = true) || name.equals("obb", ignoreCase = true))) {
                    continue
                }
                walkMediaDirectory(
                    dir = file,
                    depth = depth + 1,
                    images = images,
                    videos = videos,
                    music = music,
                    seenPaths = seenPaths,
                    seenSig = seenSig,
                    visitedDirs = visitedDirs,
                    onItemAdded = onItemAdded
                )
            } else if (file.isFile && file.canRead() && file.length() > 0L) {
                val absPath = file.absolutePath
                if (seenPaths.contains(absPath)) continue
                val ext = name.substringAfterLast('.', "").lowercase()
                val categoryInfo = when {
                    ext in imageExts -> Triple(PickerCategory.IMAGE, "img", "image/$ext")
                    ext in videoExts -> Triple(PickerCategory.VIDEO, "vid", "video/$ext")
                    ext in audioExts -> Triple(PickerCategory.MUSIC, "aud", "audio/$ext")
                    else -> null
                } ?: continue

                val (category, prefix, mime) = categoryInfo
                val sig = "$prefix:${name}_${file.length()}"
                if (seenPaths.add(absPath) && seenSig.add(sig)) {
                    val item = PickerMediaItem(
                        id = "fs:$absPath",
                        name = name,
                        sizeBytes = file.length(),
                        uriString = Uri.fromFile(file).toString(),
                        mimeType = mime,
                        dateModifiedSec = file.lastModified() / 1000L,
                        category = category
                    )
                    when (category) {
                        PickerCategory.IMAGE -> images.add(item)
                        PickerCategory.VIDEO -> videos.add(item)
                        PickerCategory.MUSIC -> music.add(item)
                        PickerCategory.FILES, PickerCategory.APPS -> Unit
                    }
                    onItemAdded()
                }
            }
        }
    }
}
