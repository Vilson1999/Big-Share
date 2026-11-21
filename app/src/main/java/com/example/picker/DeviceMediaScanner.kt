package com.example.picker

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
                } else if (item.apkPath.isNotBlank()) {
                    val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageArchiveInfo(item.apkPath, PackageManager.PackageInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageArchiveInfo(item.apkPath, 0)
                    }
                    pkgInfo?.applicationInfo?.let { appInfo ->
                        appInfo.sourceDir = item.apkPath
                        appInfo.publicSourceDir = item.apkPath
                        appInfo.loadIcon(pm)
                    }
                } else {
                    null
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

    private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val videoExts = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "ts")
    private val audioExts = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "wma")

    suspend fun scanMediaFiles(context: Context): Triple<List<PickerMediaItem>, List<PickerMediaItem>, List<PickerMediaItem>> =
        withContext(Dispatchers.IO) {
            val images = mutableListOf<PickerMediaItem>()
            val videos = mutableListOf<PickerMediaItem>()
            val music = mutableListOf<PickerMediaItem>()

            val seenSignature = HashSet<String>()
            val seenPaths = HashSet<String>()

            val volumes = getMediaStoreVolumes(context)
            for (volume in volumes) {
                queryImagesFromVolume(context, volume, images, seenPaths, seenSignature)
                queryVideosFromVolume(context, volume, videos, seenPaths, seenSignature)
                queryAudioFromVolume(context, volume, music, seenPaths, seenSignature)
            }

            // Supplementary scan across Internal + SD Card directories for unindexed files
            scanStorageRootsFallback(context, images, videos, music, seenPaths, seenSignature)

            Triple(
                images.sortedByDescending { it.dateModifiedSec },
                videos.sortedByDescending { it.dateModifiedSec },
                music.sortedByDescending { it.dateModifiedSec }
            )
        }

    suspend fun scanInstalledApps(context: Context): List<PickerAppItem> = withContext(Dispatchers.IO) {
        val apks = mutableListOf<PickerAppItem>()
        val seenApkPaths = HashSet<String>()
        val seenApkSig = HashSet<String>()

        for (volume in getMediaStoreVolumes(context)) {
            queryApksFromVolume(context, volume, apks, seenApkPaths, seenApkSig)
        }
        scanApkStorageRootsFallback(context, apks, seenApkPaths, seenApkSig)

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

        val seenPackages = HashSet<String>()
        val installed = mutableListOf<PickerAppItem>()

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
            } catch (_: Exception) {
            }
        }

        apks.sortedBy { it.appName.lowercase() } + installed.sortedBy { it.appName.lowercase() }
    }

    private fun queryApksFromVolume(
        context: Context,
        volume: String,
        out: MutableList<PickerAppItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>
    ) {
        val collection = MediaStore.Files.getContentUri(volume)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATA
        )
        val selection = "${MediaStore.Files.FileColumns.SIZE} > 0 AND (" +
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.apk' OR " +
            "${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/vnd.android.package-archive')"
        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val fileName = if (nameCol != -1) cursor.getString(nameCol) ?: "app_$id.apk" else "app_$id.apk"
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    if (size <= 0L) continue
                    val dataPath = if (dataCol != -1) cursor.getString(dataCol) else null
                    val uri = ContentUris.withAppendedId(collection, id)
                    if (!dataPath.isNullOrBlank()) seenPaths.add(dataPath)
                    val sig = "apk:${fileName}_${size}"
                    if (seenSig.add(sig)) {
                        val displayLabel = resolveApkDisplayLabel(context, dataPath, fileName)
                        out.add(
                            PickerAppItem(
                                id = "apk:$volume:$id",
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
        seenSig: MutableSet<String>
    ) {
        for ((rootDir, _) in collectStorageRoots(context)) {
            walkApkDirectory(context, rootDir, 0, out, seenPaths, seenSig)
        }
    }

    private fun walkApkDirectory(
        context: Context,
        dir: File,
        depth: Int,
        out: MutableList<PickerAppItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>
    ) {
        if (depth > 4) return
        val children = dir.listFiles() ?: return
        for (file in children) {
            val name = file.name
            if (name.startsWith(".")) continue
            if (file.isDirectory) {
                if (name.equals("Android", ignoreCase = true) && depth <= 1) continue
                walkApkDirectory(context, file, depth + 1, out, seenPaths, seenSig)
            } else if (file.isFile && file.canRead() && file.length() > 0L && name.endsWith(".apk", ignoreCase = true)) {
                val absPath = file.absolutePath
                if (seenPaths.contains(absPath)) continue
                val sig = "apk:${name}_${file.length()}"
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

    private fun queryImagesFromVolume(
        context: Context,
        volume: String,
        out: MutableList<PickerMediaItem>,
        seenPaths: MutableSet<String>,
        seenSig: MutableSet<String>
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
        seenSig: MutableSet<String>
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
        seenSig: MutableSet<String>
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
        seenSig: MutableSet<String>
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
                seenSig = seenSig
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
        seenSig: MutableSet<String>
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
                    seenSig = seenSig
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
                        }
                    }
                }
            }
        }
    }
}
