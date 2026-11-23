package com.example.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.picker.PickerAppItem
import com.example.picker.PickerMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages a session-scoped temporary file cache inside [Context.getCacheDir].
 * The cache is built when the app is opened and deleted when the app closes.
 */
object TempFileCacheManager {

    private const val ROOT_DIR_NAME = "session_file_cache"
    private const val INDEX_DIR_NAME = "index"
    private const val THUMBNAILS_DIR_NAME = "thumbnails"
    private const val WORK_DIR_NAME = "temp"
    private const val SHARED_APK_DIR_NAME = "shared_apk"

    private val lock = Any()
    private val sessionActive = AtomicBoolean(false)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isSessionActive(): Boolean = sessionActive.get()

    fun getRootDir(context: Context): File =
        File(context.applicationContext.cacheDir, ROOT_DIR_NAME)

    fun getIndexDir(context: Context): File =
        File(getRootDir(context), INDEX_DIR_NAME)

    fun getThumbnailsDir(context: Context): File =
        File(getRootDir(context), THUMBNAILS_DIR_NAME)

    fun getWorkDir(context: Context): File =
        File(getRootDir(context), WORK_DIR_NAME)

    /**
     * Initializes a fresh temporary file cache for the active app session.
     * Clears any stale cache left over from an ungraceful process termination first.
     */
    fun openSession(context: Context, cleanStale: Boolean = false) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (cleanStale) {
                deleteCacheDirectoriesLocked(appContext)
            }
            getRootDir(appContext).mkdirs()
            getIndexDir(appContext).mkdirs()
            getThumbnailsDir(appContext).mkdirs()
            getWorkDir(appContext).mkdirs()
            sessionActive.set(true)
        }
    }

    /**
     * Deletes all temporary session cache files (and stray APK share/icon temps) asynchronously.
     */
    fun closeSessionAsync(context: Context) {
        val appContext = context.applicationContext
        sessionActive.set(false)
        ioScope.launch {
            closeSessionBlocking(appContext)
        }
    }

    /**
     * Deletes all temporary session cache files synchronously under lock.
     */
    fun closeSessionBlocking(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            sessionActive.set(false)
            deleteCacheDirectoriesLocked(appContext)
        }
    }

    private fun deleteCacheDirectoriesLocked(appContext: Context) {
        runCatching { getRootDir(appContext).deleteRecursively() }
        runCatching { File(appContext.cacheDir, SHARED_APK_DIR_NAME).deleteRecursively() }
        runCatching {
            appContext.cacheDir.listFiles()?.forEach { child ->
                if (child.isFile && child.name.startsWith("apk_icon_")) {
                    child.delete()
                }
            }
        }
    }

    fun createTempWorkFile(context: Context, prefix: String, suffix: String): File {
        val appContext = context.applicationContext
        synchronized(lock) {
            val dir = if (sessionActive.get()) {
                getWorkDir(appContext).apply { mkdirs() }
            } else {
                appContext.cacheDir
            }
            return File.createTempFile(prefix, suffix, dir)
        }
    }

    suspend fun writeMediaIndex(
        context: Context,
        categoryKey: String,
        items: List<PickerMediaItem>
    ) = withContext(Dispatchers.IO) {
        if (!sessionActive.get()) return@withContext
        val appContext = context.applicationContext
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("sizeBytes", item.sizeBytes)
                    put("uriString", item.uriString)
                    put("mimeType", item.mimeType)
                    put("durationMs", item.durationMs)
                    put("artist", item.artist)
                    put("dateModifiedSec", item.dateModifiedSec)
                    put("category", item.category.name)
                }
            )
        }
        writeIndexAtomically(appContext, "${categoryKey}.json", array.toString())
    }

    suspend fun writeAppsIndex(
        context: Context,
        fileKey: String,
        items: List<PickerAppItem>
    ) = withContext(Dispatchers.IO) {
        if (!sessionActive.get()) return@withContext
        val appContext = context.applicationContext
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("appName", item.appName)
                    put("packageName", item.packageName)
                    put("versionName", item.versionName)
                    put("apkPath", item.apkPath)
                    put("uriString", item.uriString)
                    put("sizeBytes", item.sizeBytes)
                    put("isStandaloneApk", item.isStandaloneApk)
                }
            )
        }
        writeIndexAtomically(appContext, "${fileKey}.json", array.toString())
    }

    private fun writeIndexAtomically(appContext: Context, fileName: String, content: String) {
        synchronized(lock) {
            if (!sessionActive.get()) return
            val indexDir = getIndexDir(appContext).apply { mkdirs() }
            val tempFile = File(indexDir, "$fileName.tmp")
            val targetFile = File(indexDir, fileName)
            runCatching {
                tempFile.writeText(content)
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }.onFailure {
                runCatching { tempFile.delete() }
            }
        }
    }

    fun readThumbnailBitmap(context: Context, cacheKey: String): Bitmap? {
        if (!sessionActive.get()) return null
        val file = thumbnailFileForKey(context.applicationContext, cacheKey)
        if (!file.exists() || !file.isFile || file.length() <= 0L) return null
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()
    }

    fun writeThumbnailBitmap(context: Context, cacheKey: String, bitmap: Bitmap) {
        if (!sessionActive.get()) return
        val appContext = context.applicationContext
        synchronized(lock) {
            if (!sessionActive.get()) return
            val thumbDir = getThumbnailsDir(appContext).apply { mkdirs() }
            val targetFile = File(thumbDir, "${sha256Hex(cacheKey)}.webp")
            if (targetFile.exists() && targetFile.length() > 0L) return
            val tempFile = File(thumbDir, "${targetFile.name}.tmp")
            runCatching {
                tempFile.outputStream().use { out ->
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 82, out)
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }.onFailure {
                runCatching { tempFile.delete() }
            }
        }
    }

    private fun thumbnailFileForKey(appContext: Context, cacheKey: String): File =
        File(getThumbnailsDir(appContext), "${sha256Hex(cacheKey)}.webp")

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return buildString(digest.size * 2) {
            for (b in digest) {
                val v = b.toInt() and 0xFF
                if (v < 16) append('0')
                append(v.toString(16))
            }
        }
    }
}
