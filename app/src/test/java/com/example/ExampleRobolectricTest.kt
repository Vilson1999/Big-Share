package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.model.FileItem
import com.example.model.TransferStats
import com.example.model.TransferStatus
import com.example.model.formatBytes
import com.example.model.formatSeconds
import com.example.picker.toFileItem
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun readStringFromContext() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Big Share", appName)
    assertEquals("com.big.share", context.packageName)
  }

  @Test
  fun testFormatBytes() {
    assertEquals("0 B", formatBytes(0))
    assertEquals("500 B", formatBytes(500))
    assertEquals("1.0 KB", formatBytes(1024))
    assertEquals("10.0 MB", formatBytes(10 * 1024 * 1024))
    assertEquals("1.5 GB", formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
  }

  @Test
  fun testFormatSeconds() {
    assertEquals("00:00", formatSeconds(0))
    assertEquals("00:45", formatSeconds(45))
    assertEquals("02:15", formatSeconds(135))
    assertEquals("01:05:00", formatSeconds(3900))
  }

  @Test
  fun testFileItemProgress() {
    val file = FileItem(
      id = "1",
      name = "test.mp4",
      sizeBytes = 1000L,
      transferredBytes = 500L,
      status = TransferStatus.TRANSFERRING
    )
    assertEquals(0.5f, file.progress, 0.001f)
    assertEquals("500 B", file.formattedTransferred)
  }

  @Test
  fun testResolveSafeMimeType_avoidsWildcards() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = com.example.transfer.FileTransferEngine(context)
    assertEquals("application/octet-stream", engine.resolveSafeMimeType("archive.unknownext", "*/*"))
    assertEquals("application/octet-stream", engine.resolveSafeMimeType("photo.unknownext", "image/*"))
    assertEquals("application/octet-stream", engine.resolveSafeMimeType("noext", ""))
    assertEquals("application/pdf", engine.resolveSafeMimeType("doc.pdf", "application/pdf"))
  }

  @Test
  @Config(sdk = [28])
  fun testPermissions_android9() {
    val perms = getRequiredPermissions().toSet()
    assertTrue(perms.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
    assertTrue(perms.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION))
    assertTrue(perms.contains(android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
  }

  @Test
  @Config(sdk = [29, 30])
  fun testPermissions_android10And11() {
    val perms = getRequiredPermissions().toSet()
    assertTrue(perms.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
    assertTrue(perms.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION))
    assertEquals(2, perms.size)
  }

  @Test
  @Config(sdk = [31, 32])
  fun testPermissions_android12() {
    val perms = getRequiredPermissions().toSet()
    assertTrue(perms.contains(android.Manifest.permission.BLUETOOTH_SCAN))
    assertTrue(perms.contains(android.Manifest.permission.BLUETOOTH_ADVERTISE))
    assertTrue(perms.contains(android.Manifest.permission.BLUETOOTH_CONNECT))
    assertTrue(perms.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
    assertTrue(perms.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION))
  }

  @Test
  @Config(sdk = [33, 34, 36])
  fun testPermissions_android13To17() {
    val perms = getRequiredPermissions().toSet()
    assertTrue(perms.contains(android.Manifest.permission.NEARBY_WIFI_DEVICES))
    assertTrue(perms.contains(android.Manifest.permission.BLUETOOTH_SCAN))
    assertTrue(perms.contains(android.Manifest.permission.BLUETOOTH_ADVERTISE))
    assertTrue(perms.contains(android.Manifest.permission.BLUETOOTH_CONNECT))
    assertEquals(4, perms.size)
  }

  @Test
  @Config(sdk = [28, 34])
  fun testFallbackDestination_usesMiniShareFolder() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = com.example.transfer.FileTransferEngine(context)
    val dest = engine.getFallbackFile("../../sample.txt")
    assertEquals("Big Share", dest.parentFile?.name)
    assertEquals("sample.txt", dest.name)
    assertEquals("notes", engine.sanitizeFileName("/evil/path/notes"))
  }

  @Test
  @Config(sdk = [28, 32])
  fun testMediaScanPermissions_legacyStorage() {
    val perms = com.example.ui.screens.getMediaScanPermissions().toSet()
    assertTrue(perms.contains(android.Manifest.permission.READ_EXTERNAL_STORAGE))
    assertEquals(1, perms.size)
  }

  @Test
  @Config(sdk = [33])
  fun testMediaScanPermissions_android13() {
    val perms = com.example.ui.screens.getMediaScanPermissions().toSet()
    assertTrue(perms.contains(android.Manifest.permission.READ_MEDIA_IMAGES))
    assertTrue(perms.contains(android.Manifest.permission.READ_MEDIA_VIDEO))
    assertTrue(perms.contains(android.Manifest.permission.READ_MEDIA_AUDIO))
    assertEquals(3, perms.size)
  }

  @Test
  @Config(sdk = [34, 36])
  fun testMediaScanPermissions_android14Plus() {
    val perms = com.example.ui.screens.getMediaScanPermissions().toSet()
    assertTrue(perms.contains(android.Manifest.permission.READ_MEDIA_IMAGES))
    assertTrue(perms.contains(android.Manifest.permission.READ_MEDIA_VIDEO))
    assertTrue(perms.contains(android.Manifest.permission.READ_MEDIA_AUDIO))
    assertTrue(perms.contains(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
    assertEquals(4, perms.size)
  }

  @Test
  fun testPickerAppItemToFileItem() {
    val app = com.example.picker.PickerAppItem(
      packageName = "com.demo.app",
      appName = "Demo App",
      versionName = "1.2",
      apkPath = "/data/app/demo/base.apk",
      sizeBytes = 2048L
    )
    val fileItem = app.toFileItem()
    assertEquals("Demo App_v1.2.apk", fileItem.name)
    assertEquals(2048L, fileItem.sizeBytes)
    assertEquals("application/vnd.android.package-archive", fileItem.mimeType)
  }

  @Test
  fun testUserAvatarCatalog_hasTenDistinctPhotos() {
    val avatars = com.example.ui.components.UserAvatarCatalog.avatars
    assertEquals(10, avatars.size)
    assertEquals(10, avatars.map { it.drawableRes }.toSet().size)
  }

  @Test
  @Config(sdk = [28])
  fun testSynchronizedTransferStartAndCompletion() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempFile = File(context.cacheDir, "sync_test.bin").apply {
      writeBytes(ByteArray(128 * 1024) { (it % 251).toByte() })
    }
    val fileItem = FileItem(
      id = "sync-1",
      name = "sync_test.bin",
      sizeBytes = tempFile.length(),
      uriString = Uri.fromFile(tempFile).toString(),
      mimeType = "application/octet-stream"
    )

    val receiver = com.example.transfer.FileTransferEngine(context)
    val sender = com.example.transfer.FileTransferEngine(context)

    val receiverStartedAt = AtomicLong(0L)
    val senderStartedAt = AtomicLong(0L)
    val receiverDoneAt = AtomicLong(0L)
    val senderDoneAt = AtomicLong(0L)
    var senderLastProgress = 0f
    var receiverLastProgress = 0f
    var senderCompleted = false
    var receiverCompleted = false

    val rxJob = async {
      receiver.receiveFiles(
        isGroupOwner = true,
        hostAddress = "127.0.0.1",
        onTransferStarted = { _, _ -> receiverStartedAt.set(System.currentTimeMillis()) },
        onProgress = { _, stats -> receiverLastProgress = stats.overallProgress },
        onComplete = { ok, _ ->
          receiverCompleted = ok
          receiverDoneAt.set(System.currentTimeMillis())
        }
      )
    }

    val txJob = async {
      sender.sendFiles(
        isGroupOwner = false,
        hostAddress = "127.0.0.1",
        files = listOf(fileItem),
        onTransferStarted = { _, _ -> senderStartedAt.set(System.currentTimeMillis()) },
        onProgress = { _, stats -> senderLastProgress = stats.overallProgress },
        onComplete = { ok, _ ->
          senderCompleted = ok
          senderDoneAt.set(System.currentTimeMillis())
        }
      )
    }

    rxJob.await()
    txJob.await()
    tempFile.delete()

    assertTrue(senderCompleted)
    assertTrue(receiverCompleted)
    assertEquals(1.0f, senderLastProgress, 0.001f)
    assertEquals(1.0f, receiverLastProgress, 0.001f)
    assertTrue(kotlin.math.abs(senderStartedAt.get() - receiverStartedAt.get()) < 1000L)
    assertTrue(kotlin.math.abs(senderDoneAt.get() - receiverDoneAt.get()) < 1000L)
  }
}
