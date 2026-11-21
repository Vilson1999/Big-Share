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
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
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
    APPS("Apps")
}

enum class StorageLocation(val label: String) {
    INTERNAL("Internal"),
    SD_CARD("SD Card")
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
    val storageLocation: StorageLocation = StorageLocation.INTERNAL,
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
    private val cache = object : LruCache<String, ImageBitmap>(180) {}

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

    suspend fun scanMediaFiles(context: Context): Triple<List<PickerMediaItem>, List<PickerMediaItem>, List<PickerMediaItem>> {
        var result = Triple<List<PickerMediaItem>, List<PickerMediaItem>, List<PickerMediaItem>>(
            emptyList(),
            emptyList(),
            emptyList()
        )
        scanMediaFilesProgressively(context) { imgs, vids, auds ->
            result = Triple(imgs, vids, auds)
        }
        return result
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

        fun emitSnapshot(force: Boolean = false, sortAll: Boolean = false) {
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
            val imgSnap = if (sortAll) images.sortedByDescending { it.dateModifiedSec } else images.toList()
            val vidSnap = if (sortAll) videos.sortedByDescending { it.dateModifiedSec } else videos.toList()
            val musSnap = if (sortAll) music.sortedByDescending { it.dateModifiedSec } else music.toList()
            onBatch(imgSnap, vidSnap, musSnap)
        }

        val volumes = getMediaStoreVolumes(context)
        for (volume in volumes) {
            queryImagesFromVolume(context, volume, images, seenPaths, seenSignature) {
                emitSnapshot(force = false)
            }
            emitSnapshot(force = true)

            queryVideosFromVolume(context, volume, videos, seenPaths, seenSignature) {
                emitSnapshot(force = false)
            }
            emitSnapshot(force = true)

            queryAudioFromVolume(context, volume, music, seenPaths, seenSignature) {
                emitSnapshot(force = false)
            }
            emitSnapshot(force = true)
        }

        val countBeforeFallback = images.size + videos.size + music.size
        // Supplementary scan across Internal + SD Card directories for unindexed files
        scanStorageRootsFallback(context, images, videos, music, seenPaths, seenSignature) {
            emitSnapshot(force = false)
        }

        val fallbackAdded = (images.size + videos.size + music.size) > countBeforeFallback
        onBatch(
            images.sortedByDescending { it.dateModifiedSec },
            videos.sortedByDescending { it.dateModifiedSec },
            music.sortedByDescending { it.dateModifiedSec }
        )
        if (!fallbackAdded && totalEmittedCount == 0) {
            // Empty device case still already delivered empty snapshots above
        }
    }

    fun saveSafTreeUri(context: Context, treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        val prefs = context.getSharedPreferences(APK_SAF_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_TREE_URIS, emptySet())?.toMutableSet() ?: mutableSetOf()
        existing.add(treeUri.toString())
        prefs.edit().putStringSet(KEY_TREE_URIS, existing).apply()
    }

    fun saveSafApkUris(context: Context, uris: List<Uri>) {
        val prefs = context.getSharedPreferences(APK_SAF_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_FILE_URIS, emptySet())?.toMutableSet() ?: mutableSetOf()
        for (uri in uris) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            existing.add(uri.toString())
        }
        prefs.edit().putStringSet(KEY_FILE_URIS, existing).apply()
    }

    private const val APK_SAF_PREFS = "apk_saf_prefs"
    private const val KEY_TREE_URIS = "apk_tree_uris"
    private const val KEY_FILE_URIS = "apk_file_uris"

    suspend fun scanInstalledApps(context: Context): List<PickerAppItem> {
        var combined: List<PickerAppItem> = emptyList()
        scanAppsAndApksProgressively(context) { installed, apks ->
            combined = installed + apks
        }
        return combined
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

        // 2. Scan persisted SAF APK files/folders and MediaStore APKs (shown below installed apps)
        scanPersistedSafApks(context, apks, seenApkSig) {
            emitSnapshot(force = false)
        }
        emitSnapshot(force = true)

        for (volume in getMediaStoreVolumes(context)) {
            queryApksFromVolume(context, volume, apks, seenApkPaths, seenApkSig) {
                emitSnapshot(force = false)
            }
            emitSnapshot(force = true)
        }

        // 3. Scan Downloads / Documents / storage directories for remaining .apk files
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

    private fun scanPersistedSafApks(
        context: Context,
        out: MutableList<PickerAppItem>,
        seenSig: MutableSet<String>,
        onItemAdded: () -> Unit = {}
    ) {
        val prefs = context.getSharedPreferences(APK_SAF_PREFS, Context.MODE_PRIVATE)
        val treeUris = prefs.getStringSet(KEY_TREE_URIS, emptySet()).orEmpty()
        for (treeUriStr in treeUris) {
            try {
                val treeUri = Uri.parse(treeUriStr)
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                walkSafTreeForApks(context, treeUri, rootDocId, 0, out, seenSig, onItemAdded)
            } catch (_: Exception) {
            }
        }

        val fileUris = prefs.getStringSet(KEY_FILE_URIS, emptySet()).orEmpty()
        for (uriStr in fileUris) {
            try {
                val uri = Uri.parse(uriStr)
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                        val size = if (sizeIdx != -1) cursor.getLong(sizeIdx) else 0L
                        if (!name.isNullOrBlank() && name.endsWith(".apk", ignoreCase = true) && size > 0L) {
                            val sig = "apk:${name.lowercase()}_${size}"
                            if (seenSig.add(sig)) {
                                out.add(
                                    PickerAppItem(
                                        id = "apksaf:$uriStr",
                                        packageName = "",
                                        appName = name,
                                        versionName = "",
                                        apkPath = "",
                                        uriString = uriStr,
                                        sizeBytes = size,
                                        isStandaloneApk = true,
                                        fileNameOverride = name
                                    )
                                )
                                onItemAdded()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun walkSafTreeForApks(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        depth: Int,
        out: MutableList<PickerAppItem>,
        seenSig: MutableSet<String>,
        onItemAdded: () -> Unit = {}
    ) {
        if (depth > 4) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val docId = if (idCol != -1) cursor.getString(idCol) else continue
                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "" else ""
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (!name.startsWith(".")) {
                            walkSafTreeForApks(context, treeUri, docId, depth + 1, out, seenSig, onItemAdded)
                        }
                    } else if (size > 0L && (name.endsWith(".apk", ignoreCase = true) ||
                            mime.equals("application/vnd.android.package-archive", ignoreCase = true))
                    ) {
                        val fileName = if (name.endsWith(".apk", ignoreCase = true)) name else "$name.apk"
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val sig = "apk:${fileName.lowercase()}_${size}"
                        if (seenSig.add(sig)) {
                            out.add(
                                PickerAppItem(
                                    id = "apksaf:$docUri",
                                    packageName = "",
                                    appName = fileName,
                                    versionName = "",
                                    apkPath = "",
                                    uriString = docUri.toString(),
                                    sizeBytes = size,
                                    isStandaloneApk = true,
                                    fileNameOverride = fileName
                                )
                            )
                            onItemAdded()
                        }
                    }
                }
            }
        } catch (_: Exception) {
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

        val commonApkSubdirs = listOf("Download", "Downloads", "APKs", "Apk", "Shareit", "Xender", "Bluetooth", "Telegram", "WhatsApp")
        val roots = collectStorageRoots(context)
        for ((rootDir, _) in roots) {
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
        for ((rootDir, _) in roots) {
            walkApkDirectory(context, rootDir, 0, out, seenPaths, seenSig, visitedDirs, onItemAdded)
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
        if (depth > 4) return
        if (!visitedDirs.add(dir.absolutePath)) return
        val children = dir.listFiles() ?: return
        for (file in children) {
            val name = file.name
            if (name.startsWith(".")) continue
            if (file.isDirectory) {
                if (name.equals("Android", ignoreCase = true) && depth <= 1) continue
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

    private fun resolveStorageLocation(volume: String, filePath: String?): StorageLocation {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (volume != MediaStore.VOLUME_EXTERNAL_PRIMARY && volume != "external") {
                return StorageLocation.SD_CARD
            }
        }
        if (!filePath.isNullOrBlank()) {
            val lower = filePath.lowercase()
            if (lower.startsWith("/storage/") && !lower.startsWith("/storage/emulated/")) {
                return StorageLocation.SD_CARD
            }
        }
        return StorageLocation.INTERNAL
    }

    private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val videoExts = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "ts")
    private val audioExts = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "wma")

    private fun queryImagesFromVolume(
        context: Context,
        volume: String,
        out: MutableList<PickerMediaItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>,
        onItemAdded: () -> Unit = {}
    ) {
        val collection = MediaStore.Images.Media.getContentUri(volume)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATA
        )
        try {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Images.Media.SIZE} > 0",
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val modCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "image_$id.jpg" else "image_$id.jpg"
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    if (size <= 0L) continue
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "image/*" else "image/*"
                    val mod = if (modCol != -1) cursor.getLong(modCol) else 0L
                    val dataPath = if (dataCol != -1) cursor.getString(dataCol) else null

                    val uri = ContentUris.withAppendedId(collection, id)
                    val storage = resolveStorageLocation(volume, dataPath)
                    if (!dataPath.isNullOrBlank()) seenPaths.add(dataPath)
                    val sig = "img:${name}_${size}"
                    if (seenSig.add(sig)) {
                        out.add(
                            PickerMediaItem(
                                id = "img:$volume:$id",
                                name = name,
                                sizeBytes = size,
                                uriString = uri.toString(),
                                mimeType = mime,
                                dateModifiedSec = mod,
                                storageLocation = storage,
                                category = PickerCategory.IMAGE
                            )
                        )
                        onItemAdded()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun queryVideosFromVolume(
        context: Context,
        volume: String,
        out: MutableList<PickerMediaItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>,
        onItemAdded: () -> Unit = {}
    ) {
        val collection = MediaStore.Video.Media.getContentUri(volume)
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATA
        )
        try {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Video.Media.SIZE} > 0",
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val modCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "video_$id.mp4" else "video_$id.mp4"
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    if (size <= 0L) continue
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "video/*" else "video/*"
                    val duration = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val mod = if (modCol != -1) cursor.getLong(modCol) else 0L
                    val dataPath = if (dataCol != -1) cursor.getString(dataCol) else null

                    val uri = ContentUris.withAppendedId(collection, id)
                    val storage = resolveStorageLocation(volume, dataPath)
                    if (!dataPath.isNullOrBlank()) seenPaths.add(dataPath)
                    val sig = "vid:${name}_${size}"
                    if (seenSig.add(sig)) {
                        out.add(
                            PickerMediaItem(
                                id = "vid:$volume:$id",
                                name = name,
                                sizeBytes = size,
                                uriString = uri.toString(),
                                mimeType = mime,
                                durationMs = duration,
                                dateModifiedSec = mod,
                                storageLocation = storage,
                                category = PickerCategory.VIDEO
                            )
                        )
                        onItemAdded()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun queryAudioFromVolume(
        context: Context,
        volume: String,
        out: MutableList<PickerMediaItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>,
        onItemAdded: () -> Unit = {}
    ) {
        val collection = MediaStore.Audio.Media.getContentUri(volume)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATA
        )
        try {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.SIZE} > 0",
                null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val modCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val displayName = if (nameCol != -1) cursor.getString(nameCol) else null
                    val title = if (titleCol != -1) cursor.getString(titleCol) else null
                    val name = displayName ?: title ?: "audio_$id.mp3"
                    val artist = if (artistCol != -1) cursor.getString(artistCol) ?: "" else ""
                    val cleanArtist = if (artist == "<unknown>") "" else artist
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    if (size <= 0L) continue
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "audio/*" else "audio/*"
                    val duration = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val mod = if (modCol != -1) cursor.getLong(modCol) else 0L
                    val dataPath = if (dataCol != -1) cursor.getString(dataCol) else null

                    val uri = ContentUris.withAppendedId(collection, id)
                    val storage = resolveStorageLocation(volume, dataPath)
                    if (!dataPath.isNullOrBlank()) seenPaths.add(dataPath)
                    val sig = "aud:${name}_${size}"
                    if (seenSig.add(sig)) {
                        out.add(
                            PickerMediaItem(
                                id = "aud:$volume:$id",
                                name = name,
                                sizeBytes = size,
                                uriString = uri.toString(),
                                mimeType = mime,
                                durationMs = duration,
                                artist = cleanArtist,
                                dateModifiedSec = mod,
                                storageLocation = storage,
                                category = PickerCategory.MUSIC
                            )
                        )
                        onItemAdded()
                    }
                }
            }
        } catch (_: Exception) {
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
        val roots = collectStorageRoots(context)
        for ((rootDir, storageLoc) in roots) {
            walkMediaDirectory(
                dir = rootDir,
                depth = 0,
                storageLocation = storageLoc,
                images = images,
                videos = videos,
                music = music,
                seenPaths = seenPaths,
                seenSig = seenSig,
                onItemAdded = onItemAdded
            )
        }
    }

    private fun collectStorageRoots(context: Context): List<Pair<File, StorageLocation>> {
        val roots = LinkedHashMap<String, Pair<File, StorageLocation>>()

        try {
            val primary = Environment.getExternalStorageDirectory()
            if (primary != null && primary.exists() && primary.canRead()) {
                roots[primary.absolutePath] = primary to StorageLocation.INTERNAL
            }
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                sm?.storageVolumes?.forEach { vol ->
                    val dir = vol.directory
                    if (dir != null && dir.exists() && dir.canRead()) {
                        val loc = if (vol.isRemovable) StorageLocation.SD_CARD else StorageLocation.INTERNAL
                        roots[dir.absolutePath] = dir to loc
                    }
                }
            } catch (_: Exception) {}
        }

        try {
            context.getExternalFilesDirs(null).forEachIndexed { index, appDir ->
                val path = appDir?.absolutePath ?: return@forEachIndexed
                val androidIdx = path.indexOf("/Android/")
                if (androidIdx > 0) {
                    val rootFile = File(path.substring(0, androidIdx))
                    if (rootFile.exists() && rootFile.canRead()) {
                        val loc = if (index == 0) StorageLocation.INTERNAL else StorageLocation.SD_CARD
                        roots.putIfAbsent(rootFile.absolutePath, rootFile to loc)
                    }
                }
            }
        } catch (_: Exception) {}

        return roots.values.toList()
    }

    private fun walkMediaDirectory(
        dir: File,
        depth: Int,
        storageLocation: StorageLocation,
        images: MutableList<PickerMediaItem>,
        videos: MutableList<PickerMediaItem>,
        music: MutableList<PickerMediaItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>,
        onItemAdded: () -> Unit = {}
    ) {
        if (depth > 4) return
        val children = dir.listFiles() ?: return
        for (file in children) {
            val name = file.name
            if (name.startsWith(".")) continue
            if (file.isDirectory) {
                if (name.equals("Android", ignoreCase = true) && depth <= 1) continue
                walkMediaDirectory(
                    dir = file,
                    depth = depth + 1,
                    storageLocation = storageLocation,
                    images = images,
                    videos = videos,
                    music = music,
                    seenPaths = seenPaths,
                    seenSig = seenSig,
                    onItemAdded = onItemAdded
                )
            } else if (file.isFile && file.canRead() && file.length() > 0L) {
                val absPath = file.absolutePath
                if (seenPaths.contains(absPath)) continue
                val ext = name.substringAfterLast('.', "").lowercase()
                val modSec = file.lastModified() / 1000L
                val uri = Uri.fromFile(file).toString()

                when {
                    ext in imageExts -> {
                        val sig = "img:${name}_${file.length()}"
                        if (seenPaths.add(absPath) && seenSig.add(sig)) {
                            images.add(
                                PickerMediaItem(
                                    id = "fs:$absPath",
                                    name = name,
                                    sizeBytes = file.length(),
                                    uriString = uri,
                                    mimeType = "image/$ext",
                                    dateModifiedSec = modSec,
                                    storageLocation = storageLocation,
                                    category = PickerCategory.IMAGE
                                )
                            )
                            onItemAdded()
                        }
                    }
                    ext in videoExts -> {
                        val sig = "vid:${name}_${file.length()}"
                        if (seenPaths.add(absPath) && seenSig.add(sig)) {
                            videos.add(
                                PickerMediaItem(
                                    id = "fs:$absPath",
                                    name = name,
                                    sizeBytes = file.length(),
                                    uriString = uri,
                                    mimeType = "video/$ext",
                                    dateModifiedSec = modSec,
                                    storageLocation = storageLocation,
                                    category = PickerCategory.VIDEO
                                )
                            )
                            onItemAdded()
                        }
                    }
                    ext in audioExts -> {
                        val sig = "aud:${name}_${file.length()}"
                        if (seenPaths.add(absPath) && seenSig.add(sig)) {
                            music.add(
                                PickerMediaItem(
                                    id = "fs:$absPath",
                                    name = name,
                                    sizeBytes = file.length(),
                                    uriString = uri,
                                    mimeType = "audio/$ext",
                                    dateModifiedSec = modSec,
                                    storageLocation = storageLocation,
                                    category = PickerCategory.MUSIC
                                )
                            )
                            onItemAdded()
                        }
                    }
                }
            }
        }
    }
}
