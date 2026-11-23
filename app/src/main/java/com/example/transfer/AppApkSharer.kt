package com.example.transfer

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppApkSharer {

    suspend fun shareAppApk(context: Context): Result<Unit> = runCatching {
        val apkFile = withContext(Dispatchers.IO) {
            prepareShareableApk(context)
        }
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, apkUri)
            putExtra(Intent.EXTRA_SUBJECT, "Max Share")
            clipData = ClipData.newUri(context.contentResolver, apkFile.name, apkUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share App").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }

    internal fun prepareShareableApk(context: Context): File {
        val appInfo = context.applicationInfo
        val sourcePath = appInfo.publicSourceDir ?: appInfo.sourceDir
            ?: throw IllegalStateException("App APK path is unavailable")
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists() || !sourceFile.canRead()) {
            throw IllegalStateException("App APK file cannot be read")
        }

        val versionName = try {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            pkgInfo.versionName?.replace(Regex("[^a-zA-Z0-9._-]"), "")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

        val fileName = if (versionName != null) "Max_Share_v${versionName}.apk" else "Max_Share.apk"
        val sharedDir = File(context.cacheDir, "shared_apk").apply { mkdirs() }
        val targetFile = File(sharedDir, fileName)

        sharedDir.listFiles()?.forEach { existing ->
            if (existing.name != fileName) {
                existing.delete()
            }
        }

        val isUpToDate = targetFile.exists() &&
            targetFile.length() == sourceFile.length() &&
            targetFile.lastModified() >= sourceFile.lastModified()

        if (!isUpToDate) {
            val tempFile = File(sharedDir, "$fileName.tmp")
            sourceFile.inputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        }

        return targetFile
    }
}
