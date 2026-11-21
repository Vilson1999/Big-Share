package com.example.transfer

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.model.FileItem
import com.example.model.TransferStats
import com.example.model.TransferStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

class FileTransferEngine(private val context: Context) {

    companion object {
        const val PORT = 8888
        private const val CHUNK_SIZE = 64 * 1024 // 64 KB
        private const val SOCKET_BUFFER_BYTES = 256 * 1024 // 256 KB to keep sender/receiver progress tightly synced
        private const val MAGIC_HEADER = 0x50325046 // 'P2PF'
        private const val CODE_DATA = 1
        private const val CODE_SKIP = 2
        private const val CODE_CANCELLED_MID = -1
        private const val CODE_EOF = 0
        private const val ACK_METADATA_READY = 1
        private const val SYNC_START_TRANSFER = 2
        private const val ACK_FILE_DONE = 3
        private const val ACK_ALL_DONE = 4
        private const val PROGRESS_INTERVAL_MS = 250L
        private const val CONNECT_MAX_ATTEMPTS = 25
        private const val CONNECT_RETRY_DELAY_MS = 400L
    }

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private val isRunning = AtomicBoolean(false)
    private val cancelledFileIds = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun cancelFile(fileId: String) {
        cancelledFileIds.add(fileId)
    }

    fun stopTransfer() {
        isRunning.set(false)
        try {
            clientSocket?.close()
        } catch (_: Exception) {}
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        clientSocket = null
        serverSocket = null
    }

    private fun isExpectedSocketClosure(e: Exception, active: Boolean): Boolean {
        return !isRunning.get() || !active ||
            (e is SocketException && e.message?.contains("closed", ignoreCase = true) == true)
    }

    private suspend fun openPeerSocket(
        isGroupOwner: Boolean,
        groupOwnerAddress: String
    ): Socket {
        if (isGroupOwner) {
            val srv = ServerSocket().apply {
                reuseAddress = true
                soTimeout = 30000
                bind(InetSocketAddress(PORT))
            }
            serverSocket = srv
            val accepted = srv.accept()
            try {
                srv.close()
            } catch (_: Exception) {}
            serverSocket = null
            accepted.tcpNoDelay = true
            accepted.soTimeout = 60000
            try {
                accepted.sendBufferSize = SOCKET_BUFFER_BYTES
                accepted.receiveBufferSize = SOCKET_BUFFER_BYTES
            } catch (_: Exception) {}
            clientSocket = accepted
            return accepted
        } else {
            val peerAddress = resolveLocalPeerAddress(groupOwnerAddress)
            var lastException: Exception? = null
            repeat(CONNECT_MAX_ATTEMPTS) { attempt ->
                if (!isRunning.get() || !coroutineContext.isActive) {
                    throw CancellationException("Transfer cancelled")
                }
                val socket = Socket()
                try {
                    try {
                        socket.sendBufferSize = SOCKET_BUFFER_BYTES
                        socket.receiveBufferSize = SOCKET_BUFFER_BYTES
                    } catch (_: Exception) {}
                    val localP2pAddress = findLocalP2pInterfaceAddress()
                    if (localP2pAddress != null) {
                        socket.bind(InetSocketAddress(localP2pAddress, 0))
                    } else {
                        socket.bind(null)
                    }
                    socket.connect(InetSocketAddress(peerAddress, PORT), 3000)
                    socket.tcpNoDelay = true
                    socket.soTimeout = 60000
                    clientSocket = socket
                    return socket
                } catch (e: IOException) {
                    lastException = e
                    try {
                        socket.close()
                    } catch (_: Exception) {}
                    if (attempt < CONNECT_MAX_ATTEMPTS - 1) {
                        delay(CONNECT_RETRY_DELAY_MS)
                    }
                }
            }
            throw lastException ?: IOException("Unable to connect to peer")
        }
    }

    private fun findLocalP2pInterfaceAddress(): InetAddress? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
            var fallbackP2pSubnet: InetAddress? = null
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val isP2pName = iface.name.startsWith("p2p", ignoreCase = true)
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        if (isP2pName) {
                            return addr
                        }
                        if (addr.hostAddress?.startsWith("192.168.49.") == true) {
                            fallbackP2pSubnet = addr
                        }
                    }
                }
            }
            fallbackP2pSubnet
        } catch (_: Exception) {
            null
        }
    }

    suspend fun sendFiles(
        isGroupOwner: Boolean,
        hostAddress: String,
        files: List<FileItem>,
        onTransferStarted: (List<FileItem>, TransferStats) -> Unit,
        onProgress: (List<FileItem>, TransferStats) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        isRunning.set(true)
        cancelledFileIds.clear()

        val mutableFiles = files.toMutableList()
        val totalBytes = files.sumOf { it.sizeBytes }
        var totalTransferred = 0L

        var lastSampleTime = System.currentTimeMillis()
        var bytesSample = 0L
        var currentSpeed = 0L

        try {
            val socket = openPeerSocket(isGroupOwner = isGroupOwner, groupOwnerAddress = hostAddress)

            val outputStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val inputStream = DataInputStream(BufferedInputStream(socket.getInputStream()))

            // Send Magic Header & file count
            outputStream.writeInt(MAGIC_HEADER)
            outputStream.writeInt(files.size)

            // Send metadata for all files
            for (file in files) {
                outputStream.writeUTF(file.id)
                outputStream.writeUTF(file.name)
                outputStream.writeLong(file.sizeBytes)
                outputStream.writeUTF(file.mimeType)
            }
            outputStream.flush()

            // Wait for receiver metadata ACK, then send START_SYNC so both devices show TransferScreen together
            val ack = inputStream.readByte().toInt()
            if (ack != ACK_METADATA_READY) {
                throw IllegalStateException("Receiver declined transfer")
            }
            outputStream.writeByte(SYNC_START_TRANSFER)
            outputStream.flush()

            onTransferStarted(
                mutableFiles.toList(),
                calculateStats(totalBytes, 0L, 0L, maxProgress = 0.99f)
            )

            val buffer = ByteArray(CHUNK_SIZE)

            for (i in mutableFiles.indices) {
                if (!isRunning.get() || !coroutineContext.isActive) break

                val currentFile = mutableFiles[i]
                if (cancelledFileIds.contains(currentFile.id)) {
                    outputStream.writeByte(CODE_SKIP)
                    outputStream.flush()
                    inputStream.readByte() // Wait for receiver ACK_FILE_DONE
                    mutableFiles[i] = currentFile.copy(status = TransferStatus.CANCELLED)
                    onProgress(
                        mutableFiles.toList(),
                        calculateStats(totalBytes, totalTransferred, currentSpeed, maxProgress = 0.99f)
                    )
                    continue
                }

                val uri = currentFile.uriString?.let { Uri.parse(it) }
                val fileInputStream: InputStream? = if (uri != null) {
                    try {
                        context.contentResolver.openInputStream(uri)
                    } catch (_: Exception) {
                        null
                    }
                } else null

                if (fileInputStream == null) {
                    outputStream.writeByte(CODE_SKIP)
                    outputStream.flush()
                    inputStream.readByte() // Wait for receiver ACK_FILE_DONE
                    mutableFiles[i] = currentFile.copy(status = TransferStatus.FAILED)
                    onProgress(
                        mutableFiles.toList(),
                        calculateStats(totalBytes, totalTransferred, currentSpeed, maxProgress = 0.99f)
                    )
                    continue
                }

                outputStream.writeByte(CODE_DATA)
                outputStream.flush()

                mutableFiles[i] = currentFile.copy(status = TransferStatus.TRANSFERRING)
                onProgress(
                    mutableFiles.toList(),
                    calculateStats(totalBytes, totalTransferred, currentSpeed, maxProgress = 0.99f)
                )

                var fileTransferred = 0L
                var fileCancelled = false

                fileInputStream.use { fis ->
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        if (!isRunning.get() || !coroutineContext.isActive) {
                            outputStream.writeInt(CODE_CANCELLED_MID)
                            outputStream.flush()
                            fileCancelled = true
                            break
                        }

                        if (cancelledFileIds.contains(currentFile.id)) {
                            outputStream.writeInt(CODE_CANCELLED_MID)
                            outputStream.flush()
                            fileCancelled = true
                            break
                        }

                        outputStream.writeInt(bytesRead)
                        outputStream.write(buffer, 0, bytesRead)

                        fileTransferred += bytesRead
                        totalTransferred += bytesRead
                        bytesSample += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastSampleTime
                        if (elapsed >= PROGRESS_INTERVAL_MS) {
                            outputStream.flush()
                            currentSpeed = (bytesSample * 1000L) / elapsed.coerceAtLeast(1)
                            bytesSample = 0L
                            lastSampleTime = now

                            // Cap sender UI below 100% of file/batch until receiver confirms file flush on disk
                            val cappedFileBytes = fileTransferred.coerceAtMost((currentFile.sizeBytes - 1L).coerceAtLeast(0L))
                            mutableFiles[i] = currentFile.copy(
                                transferredBytes = cappedFileBytes,
                                status = TransferStatus.TRANSFERRING
                            )
                            onProgress(
                                mutableFiles.toList(),
                                calculateStats(totalBytes, totalTransferred, currentSpeed, maxProgress = 0.99f)
                            )
                        }
                    }

                    if (!fileCancelled) {
                        outputStream.writeInt(CODE_EOF)
                        outputStream.flush()
                    }
                }

                // Wait until receiver finishes writing to storage and finalizing MediaStore for this file
                val fileAck = inputStream.readByte().toInt()
                if (fileAck != ACK_FILE_DONE) {
                    throw IllegalStateException("Unexpected receiver confirmation")
                }

                mutableFiles[i] = currentFile.copy(
                    transferredBytes = fileTransferred,
                    status = if (fileCancelled) TransferStatus.CANCELLED else TransferStatus.COMPLETED
                )
                if (i < mutableFiles.lastIndex) {
                    onProgress(
                        mutableFiles.toList(),
                        calculateStats(totalBytes, totalTransferred, currentSpeed, maxProgress = 0.99f)
                    )
                }
            }

            // Wait for receiver's final ACK_ALL_DONE so both devices hit 100% and Complete at the exact same moment
            inputStream.readByte()
            onProgress(
                mutableFiles.toList(),
                calculateStats(totalBytes, totalTransferred, currentSpeed, maxProgress = 1.0f)
            )
            onComplete(true, null)

            try {
                socket.close()
            } catch (_: Exception) {}
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isExpectedSocketClosure(e, coroutineContext.isActive)) {
                Log.e("FileTransferEngine", "Send failed", e)
                onComplete(false, e.localizedMessage)
            }
        } finally {
            stopTransfer()
        }
    }

    suspend fun receiveFiles(
        isGroupOwner: Boolean,
        hostAddress: String,
        onTransferStarted: (List<FileItem>, TransferStats) -> Unit,
        onProgress: (List<FileItem>, TransferStats) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        isRunning.set(true)
        cancelledFileIds.clear()

        try {
            val socket = openPeerSocket(isGroupOwner = isGroupOwner, groupOwnerAddress = hostAddress)

            val inputStream = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val outputStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            val magic = inputStream.readInt()
            if (magic != MAGIC_HEADER) {
                throw IllegalStateException("Invalid transfer protocol")
            }

            val count = inputStream.readInt()
            val filesList = mutableListOf<FileItem>()
            for (i in 0 until count) {
                val id = inputStream.readUTF()
                val name = inputStream.readUTF()
                val size = inputStream.readLong()
                val mime = inputStream.readUTF()
                filesList.add(FileItem(id = id, name = name, sizeBytes = size, mimeType = mime))
            }

            val mutableFiles = filesList.toMutableList()
            val totalBytes = filesList.sumOf { it.sizeBytes }
            var totalTransferred = 0L

            var lastSampleTime = System.currentTimeMillis()
            var bytesSample = 0L
            var currentSpeed = 0L

            // Send metadata ACK, then wait for sender START_SYNC so both screens open together
            outputStream.writeByte(ACK_METADATA_READY)
            outputStream.flush()

            val startSync = inputStream.readByte().toInt()
            if (startSync != SYNC_START_TRANSFER) {
                throw IllegalStateException("Transfer handshake failed")
            }

            onTransferStarted(
                mutableFiles.toList(),
                calculateStats(totalBytes, 0L, 0L, maxProgress = 0.99f)
            )

            val buffer = ByteArray(CHUNK_SIZE)

            for (i in mutableFiles.indices) {
                if (!isRunning.get() || !coroutineContext.isActive) break

                val file = mutableFiles[i]
                val fileCode = inputStream.readByte().toInt()

                if (fileCode == CODE_SKIP) {
                    mutableFiles[i] = file.copy(status = TransferStatus.CANCELLED)
                    outputStream.writeByte(ACK_FILE_DONE)
                    outputStream.flush()
                    if (i < mutableFiles.lastIndex) {
                        onProgress(
                            mutableFiles.toList(),
                            calculateStats(totalBytes, totalTransferred, currentSpeed, maxProgress = 0.99f)
                        )
                    }
                    continue
                }

                mutableFiles[i] = file.copy(status = TransferStatus.TRANSFERRING)
                onProgress(
                    mutableFiles.toList(),
                    calculateStats(totalBytes, totalTransferred, currentSpeed, maxProgress = 0.99f)
                )

                val destination = createDestination(file.name, file.mimeType)
                var fileTransferred = 0L
                var isCancelled = false

                try {
                    destination.outputStream.use { fos ->
                        while (true) {
                            val chunkSize = inputStream.readInt()
                            if (chunkSize == CODE_EOF) {
                                break
                            }
                            if (chunkSize == CODE_CANCELLED_MID) {
                                isCancelled = true
                                break
                            }

                            if (cancelledFileIds.contains(file.id)) {
                                isCancelled = true
                            }

                            inputStream.readFully(buffer, 0, chunkSize)
                            if (!isCancelled) {
                                fos.write(buffer, 0, chunkSize)
                            }

                            fileTransferred += chunkSize
                            totalTransferred += chunkSize
                            bytesSample += chunkSize

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastSampleTime
                            if (elapsed >= PROGRESS_INTERVAL_MS) {
                                currentSpeed = (bytesSample * 1000L) / elapsed.coerceAtLeast(1)
                                bytesSample = 0L
                                lastSampleTime = now

                                mutableFiles[i] = file.copy(
                                    transferredBytes = fileTransferred,
                                    status = TransferStatus.TRANSFERRING
                                )
                                onProgress(
                                    mutableFiles.toList(),
                                    calculateStats(totalBytes, totalTransferred, currentSpeed, maxProgress = 0.99f)
                                )
                            }
                        }
                    }
                    finalizeDestination(destination, success = !isCancelled)
                } catch (e: Exception) {
                    Log.e("FileTransferEngine", "Error receiving file ${file.name}", e)
                    isCancelled = true
                    finalizeDestination(destination, success = false)
                }

                mutableFiles[i] = file.copy(
                    transferredBytes = fileTransferred,
                    status = if (isCancelled) TransferStatus.CANCELLED else TransferStatus.COMPLETED
                )
                outputStream.writeByte(ACK_FILE_DONE)
                outputStream.flush()
                if (i < mutableFiles.lastIndex) {
                    onProgress(
                        mutableFiles.toList(),
                        calculateStats(totalBytes, totalTransferred, currentSpeed, maxProgress = 0.99f)
                    )
                }
            }

            // Emit 100% + Complete simultaneously with sender right as ACK_ALL_DONE is flushed
            outputStream.writeByte(ACK_ALL_DONE)
            outputStream.flush()
            onProgress(
                mutableFiles.toList(),
                calculateStats(totalBytes, totalTransferred, currentSpeed, maxProgress = 1.0f)
            )
            onComplete(true, null)

            // Wait briefly for sender to read ACK_ALL_DONE and close socket cleanly
            try {
                socket.soTimeout = 1500
                inputStream.read()
            } catch (_: Exception) {}
            try {
                socket.close()
            } catch (_: Exception) {}
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isExpectedSocketClosure(e, coroutineContext.isActive)) {
                Log.e("FileTransferEngine", "Receive failed", e)
                onComplete(false, e.localizedMessage)
            }
        } finally {
            stopTransfer()
        }
    }

    private data class ReceiveDestination(
        val outputStream: OutputStream,
        val mediaUri: Uri? = null,
        val file: File? = null
    )

    internal fun resolveSafeMimeType(fileName: String, rawMime: String): String {
        val trimmed = rawMime.trim()
        if (trimmed.isNotEmpty() && !trimmed.contains('*') && trimmed.contains('/')) {
            return trimmed
        }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val guessed = if (ext.isNotEmpty()) {
            android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        } else null
        return guessed ?: "application/octet-stream"
    }

    internal fun sanitizeFileName(rawName: String): String {
        val cleaned = rawName.replace('\\', '/').substringAfterLast('/').trim()
        return if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") {
            "file_${System.currentTimeMillis()}"
        } else {
            cleaned
        }
    }

    private fun createDestination(fileName: String, mimeType: String): ReceiveDestination {
        val safeName = sanitizeFileName(fileName)
        val safeMime = resolveSafeMimeType(safeName, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver: ContentResolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, safeMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Big Share")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    val stream = resolver.openOutputStream(uri)
                    if (stream != null) {
                        return ReceiveDestination(outputStream = stream, mediaUri = uri)
                    }
                    resolver.delete(uri, null, null)
                }
            } catch (e: Exception) {
                Log.e("FileTransferEngine", "MediaStore insert failed", e)
            }
        }

        val fallback = getFallbackFile(safeName)
        return ReceiveDestination(outputStream = FileOutputStream(fallback), file = fallback)
    }

    private fun finalizeDestination(destination: ReceiveDestination, success: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && destination.mediaUri != null) {
            try {
                if (success) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    context.contentResolver.update(destination.mediaUri, values, null, null)
                } else {
                    context.contentResolver.delete(destination.mediaUri, null, null)
                }
            } catch (_: Exception) {}
        } else if (destination.file != null) {
            try {
                if (success) {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(destination.file.absolutePath),
                        null,
                        null
                    )
                } else {
                    destination.file.delete()
                }
            } catch (_: Exception) {}
        }
    }

    internal fun getFallbackFile(fileName: String): File {
        val safeName = sanitizeFileName(fileName)
        val publicMiniShare = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Big Share"
        )
        val targetDir = try {
            if (!publicMiniShare.exists()) {
                publicMiniShare.mkdirs()
            }
            if (publicMiniShare.exists() && publicMiniShare.canWrite()) {
                publicMiniShare
            } else {
                File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                    "Big Share"
                ).apply { mkdirs() }
            }
        } catch (_: Exception) {
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                "Big Share"
            ).apply { mkdirs() }
        }

        var target = File(targetDir, safeName)
        var count = 1
        val baseName = safeName.substringBeforeLast(".")
        val extension = safeName.substringAfterLast(".", "")
        while (target.exists()) {
            val newName = if (extension.isNotEmpty() && safeName.contains('.')) {
                "$baseName($count).$extension"
            } else {
                "$baseName($count)"
            }
            target = File(targetDir, newName)
            count++
        }
        return target
    }

    private fun calculateStats(
        totalBytes: Long,
        transferredBytes: Long,
        speed: Long,
        maxProgress: Float = 1.0f
    ): TransferStats {
        val effectiveTotal = totalBytes.coerceAtLeast(transferredBytes)
        val safeTransferred = transferredBytes.coerceAtMost(effectiveTotal)
        val rawProgress = if (effectiveTotal > 0) (safeTransferred.toFloat() / effectiveTotal).coerceIn(0f, 1f) else 0f
        val progress = rawProgress.coerceAtMost(maxProgress)
        val remainingBytes = (effectiveTotal - safeTransferred).coerceAtLeast(0)
        val remainingSeconds = if (speed > 0) remainingBytes / speed else 0L

        return TransferStats(
            totalBytes = effectiveTotal,
            transferredBytes = safeTransferred,
            speedBytesPerSec = speed,
            remainingSeconds = remainingSeconds,
            overallProgress = progress
        )
    }

    private fun resolveLocalPeerAddress(hostAddress: String): InetAddress {
        val parts = hostAddress.trim().split('.')
        require(parts.size == 4) { "Invalid local peer address" }
        val bytes = ByteArray(4) { index ->
            val segment = parts[index].toIntOrNull()
                ?: throw IllegalArgumentException("Invalid local peer address")
            require(segment in 0..255) { "Invalid local peer address" }
            segment.toByte()
        }
        val address = InetAddress.getByAddress(bytes)
        require(address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress) {
            "Only local Wi-Fi Direct peer addresses are allowed"
        }
        return address
    }
}
