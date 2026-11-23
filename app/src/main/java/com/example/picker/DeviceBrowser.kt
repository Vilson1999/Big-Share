package com.example.picker

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import com.example.model.FileItem
import com.example.model.TransferStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class StorageKind {
    INTERNAL,
    SD_CARD,
    USB_DRIVE,
    FOLDER
}

data class BrowserStorageRoot(
    val id: String,
    val label: String,
    val kind: StorageKind,
    val fileRoot: File? = null,
    val treeUri: Uri? = null,
    val storageVolume: StorageVolume? = null,
    val isDirectlyReadable: Boolean = false
)

data class BrowserEntry(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val mimeType: String,
    val lastModifiedMs: Long,
    val childCount: Int? = null,
    val file: File? = null,
    val documentUri: Uri? = null,
    val treeUri: Uri? = null,
    val documentId: String? = null
)

data class BrowserPathCrumb(
    val label: String,
    val fileDir: File? = null,
    val treeUri: Uri? = null,
    val documentId: String? = null
)

object DeviceBrowser {

    private const val MAX_FOLDER_DEPTH = 16

    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun discoverStorageRoots(
        context: Context,
        sessionTreeUris: List<Uri> = emptyList()
    ): List<BrowserStorageRoot> = withContext(Dispatchers.IO) {
        val roots = LinkedHashMap<String, BrowserStorageRoot>()
        val seenFilePaths = HashSet<String>()

        fun canonicalPath(dir: File): String = try {
            dir.canonicalPath
        } catch (_: Exception) {
            dir.absolutePath
        }

        fun isReadableDir(dir: File?): Boolean {
            if (dir == null || !dir.exists() || !dir.isDirectory || !dir.canRead()) return false
            return try {
                dir.listFiles() != null
            } catch (_: Exception) {
                false
            }
        }

        fun classifyKind(description: String, path: String?, isPrimary: Boolean): StorageKind {
            if (isPrimary) return StorageKind.INTERNAL
            val combined = "$description ${path.orEmpty()}".lowercase()
            return if (
                combined.contains("usb") ||
                combined.contains("otg") ||
                combined.contains("pendrive") ||
                combined.contains("pen drive") ||
                combined.contains("flash") ||
                combined.contains("mass") ||
                combined.contains("thumb")
            ) {
                StorageKind.USB_DRIVE
            } else {
                StorageKind.SD_CARD
            }
        }

        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        val volumes = sm?.storageVolumes.orEmpty()

        // Pre-index external app dir roots to map UUIDs on API 28-29
        val externalRootCandidates = mutableListOf<File>()
        try {
            Environment.getExternalStorageDirectory()?.let { externalRootCandidates.add(it) }
        } catch (_: Exception) {}
        try {
            context.getExternalFilesDirs(null).forEach { appDir ->
                val path = appDir?.absolutePath ?: return@forEach
                val idx = path.indexOf("/Android/")
                if (idx > 0) {
                    externalRootCandidates.add(File(path.substring(0, idx)))
                }
            }
        } catch (_: Exception) {}
        listOf("/storage", "/mnt/media_rw").forEach { mountBase ->
            try {
                File(mountBase).listFiles()?.forEach { child ->
                    val name = child.name
                    if (name != "self" && name != "emulated" && child.isDirectory) {
                        externalRootCandidates.add(child)
                    }
                }
            } catch (_: Exception) {}
        }

        for (vol in volumes) {
            val state = vol.state
            if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) {
                continue
            }
            val desc = try {
                vol.getDescription(context)?.trim().orEmpty()
            } catch (_: Exception) {
                ""
            }
            val uuid = vol.uuid
            val resolvedDir: File? = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vol.directory != null -> vol.directory
                vol.isPrimary -> try {
                    Environment.getExternalStorageDirectory()
                } catch (_: Exception) {
                    null
                }
                !uuid.isNullOrBlank() -> {
                    externalRootCandidates.firstOrNull { candidate ->
                        candidate.name.equals(uuid, ignoreCase = true) ||
                            candidate.absolutePath.endsWith("/$uuid", ignoreCase = true)
                    } ?: File("/storage/$uuid").takeIf { it.exists() }
                }
                else -> null
            }

            val kind = classifyKind(desc, resolvedDir?.absolutePath, vol.isPrimary)
            val label = when {
                vol.isPrimary -> "Internal Storage"
                desc.isNotBlank() -> desc
                kind == StorageKind.USB_DRIVE -> "USB Drive"
                else -> "SD Card"
            }
            val readable = if (vol.isPrimary && resolvedDir != null && resolvedDir.exists()) {
                true
            } else {
                isReadableDir(resolvedDir)
            }
            if (resolvedDir != null) {
                seenFilePaths.add(canonicalPath(resolvedDir))
            }
            val key = resolvedDir?.let { "vol:${canonicalPath(it)}" } ?: "vol:${uuid ?: label}"
            roots[key] = BrowserStorageRoot(
                id = key,
                label = label,
                kind = kind,
                fileRoot = resolvedDir,
                storageVolume = vol,
                isDirectlyReadable = readable
            )
        }

        // Ensure Internal Storage is always present if Environment reports it
        try {
            val primary = Environment.getExternalStorageDirectory()
            if (primary != null && isReadableDir(primary)) {
                val cPath = canonicalPath(primary)
                if (seenFilePaths.add(cPath)) {
                    roots.putIfAbsent(
                        "vol:$cPath",
                        BrowserStorageRoot(
                            id = "vol:$cPath",
                            label = "Internal Storage",
                            kind = StorageKind.INTERNAL,
                            fileRoot = primary,
                            isDirectlyReadable = true
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // Add any extra readable mount directories from /storage or /mnt/media_rw
        for (candidate in externalRootCandidates) {
            if (!isReadableDir(candidate)) continue
            val cPath = canonicalPath(candidate)
            if (!seenFilePaths.add(cPath)) continue
            val kind = classifyKind(candidate.name, cPath, isPrimary = false)
            val label = when (kind) {
                StorageKind.USB_DRIVE -> "USB (${candidate.name})"
                StorageKind.SD_CARD -> "SD Card (${candidate.name})"
                else -> candidate.name
            }
            roots["fs:$cPath"] = BrowserStorageRoot(
                id = "fs:$cPath",
                label = label,
                kind = kind,
                fileRoot = candidate,
                isDirectlyReadable = true
            )
        }

        // Add SAF tree URIs (persisted or granted in session, e.g. USB Pendrive / SD Card)
        val safUris = LinkedHashSet<Uri>()
        try {
            context.contentResolver.persistedUriPermissions.forEach { perm ->
                if (perm.isReadPermission) safUris.add(perm.uri)
            }
        } catch (_: Exception) {}
        safUris.addAll(sessionTreeUris)

        for (treeUri in safUris) {
            val docId = try {
                DocumentsContract.getTreeDocumentId(treeUri)
            } catch (_: Exception) {
                continue
            }
            val label = resolveTreeDisplayName(context, treeUri, docId)
            val kind = classifyKind(label, docId, isPrimary = false).let {
                if (docId.startsWith("primary:", ignoreCase = true)) StorageKind.FOLDER else it
            }
            val id = "saf:$treeUri"
            roots[id] = BrowserStorageRoot(
                id = id,
                label = label,
                kind = kind,
                treeUri = treeUri,
                isDirectlyReadable = true
            )
        }

        roots.values.toList()
    }

    private fun resolveTreeDisplayName(context: Context, treeUri: Uri, docId: String): String {
        try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)?.trim()
                    if (!name.isNullOrBlank()) return name
                }
            }
        } catch (_: Exception) {}
        val clean = docId.substringAfter(':').substringAfterLast('/').trim()
        return clean.ifEmpty { "External Drive" }
    }

    fun initialCrumbFor(root: BrowserStorageRoot): BrowserPathCrumb? {
        return when {
            root.treeUri != null -> {
                val docId = try {
                    DocumentsContract.getTreeDocumentId(root.treeUri)
                } catch (_: Exception) {
                    return null
                }
                BrowserPathCrumb(
                    label = root.label,
                    treeUri = root.treeUri,
                    documentId = docId
                )
            }
            root.fileRoot != null && root.isDirectlyReadable -> {
                BrowserPathCrumb(
                    label = root.label,
                    fileDir = root.fileRoot
                )
            }
            else -> null
        }
    }

    suspend fun listDirectory(
        context: Context,
        crumb: BrowserPathCrumb
    ): List<BrowserEntry> = withContext(Dispatchers.IO) {
        when {
            crumb.fileDir != null -> listFileDirectory(context, crumb.fileDir)
            crumb.treeUri != null && crumb.documentId != null -> {
                listSafDirectory(context, crumb.treeUri, crumb.documentId)
            }
            else -> emptyList()
        }
    }

    private fun canonicalPathSafe(dir: File): String = try {
        dir.canonicalPath
    } catch (_: Exception) {
        dir.absolutePath
    }

    private fun listFileDirectory(context: Context, dir: File): List<BrowserEntry> {
        val children = try {
            dir.listFiles()
        } catch (_: Exception) {
            null
        }.orEmpty()

        val folders = mutableListOf<BrowserEntry>()
        val files = mutableListOf<BrowserEntry>()
        val seenFolderNames = HashSet<String>()
        val seenFileNames = HashSet<String>()

        for (child in children) {
            val name = child.name
            if (name == "." || name == "..") continue

            if (child.isDirectory) {
                if (seenFolderNames.add(name.lowercase())) {
                    val count = try {
                        child.listFiles()?.count { it.name != "." && it.name != ".." }
                    } catch (_: Exception) {
                        null
                    }
                    folders.add(
                        BrowserEntry(
                            id = "dir:${child.absolutePath}",
                            name = name,
                            isDirectory = true,
                            sizeBytes = 0L,
                            mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                            lastModifiedMs = child.lastModified(),
                            childCount = count,
                            file = child
                        )
                    )
                }
            } else if (child.isFile && child.canRead() && child.length() >= 0L) {
                if (seenFileNames.add(name.lowercase())) {
                    files.add(
                        BrowserEntry(
                            id = "file:${child.absolutePath}",
                            name = name,
                            isDirectory = false,
                            sizeBytes = child.length(),
                            mimeType = guessMimeType(name),
                            lastModifiedMs = child.lastModified(),
                            file = child
                        )
                    )
                }
            }
        }

        // Supplement with standard folders and MediaStore-indexed files on Android 11+ Scoped Storage
        val matchedRoot = resolveStorageVolumeMatch(context, dir)
        if (matchedRoot != null) {
            val (rootPath, volumeName, isPrimary) = matchedRoot
            val dirPath = canonicalPathSafe(dir).trimEnd('/')
            val relDir = if (dirPath == rootPath) {
                ""
            } else {
                dirPath.removePrefix("$rootPath/").trim('/')
            }

            if (isPrimary && relDir.isEmpty()) {
                val standardDirs = listOf(
                    "Download", "Documents", "Pictures", "DCIM",
                    "Music", "Movies", "Alarms", "Podcasts",
                    "Ringtones", "Audiobooks", "Recordings"
                )
                for (stdName in standardDirs) {
                    if (seenFolderNames.contains(stdName.lowercase())) continue
                    val sub = File(dir, stdName)
                    if (sub.exists() && sub.isDirectory && seenFolderNames.add(stdName.lowercase())) {
                        val count = try {
                            sub.listFiles()?.count { it.name != "." && it.name != ".." }
                        } catch (_: Exception) {
                            null
                        }
                        folders.add(
                            BrowserEntry(
                                id = "dir:${sub.absolutePath}",
                                name = sub.name,
                                isDirectory = true,
                                sizeBytes = 0L,
                                mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                                lastModifiedMs = sub.lastModified(),
                                childCount = count,
                                file = sub
                            )
                        )
                    }
                }
            }

            mergeMediaStoreDirectoryEntries(
                context = context,
                dir = dir,
                rootPath = rootPath,
                volumeName = volumeName,
                relDir = relDir,
                folders = folders,
                files = files,
                seenFolderNames = seenFolderNames,
                seenFileNames = seenFileNames
            )
        }

        folders.sortBy { it.name.lowercase() }
        files.sortBy { it.name.lowercase() }
        return folders + files
    }

    private data class VolumePathMatch(
        val rootPath: String,
        val volumeName: String,
        val isPrimary: Boolean
    )

    private fun resolveStorageVolumeMatch(context: Context, dir: File): VolumePathMatch? {
        val dirPath = canonicalPathSafe(dir).trimEnd('/')
        val candidates = mutableListOf<VolumePathMatch>()

        try {
            Environment.getExternalStorageDirectory()?.let { primary ->
                candidates.add(
                    VolumePathMatch(
                        rootPath = canonicalPathSafe(primary).trimEnd('/'),
                        volumeName = "external",
                        isPrimary = true
                    )
                )
            }
        } catch (_: Exception) {}

        try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            sm?.storageVolumes?.forEach { vol ->
                val volDir = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> vol.directory
                    vol.isPrimary -> Environment.getExternalStorageDirectory()
                    !vol.uuid.isNullOrBlank() -> File("/storage/${vol.uuid}").takeIf { it.exists() }
                    else -> null
                } ?: return@forEach
                val volPath = canonicalPathSafe(volDir).trimEnd('/')
                val volName = when {
                    vol.isPrimary -> "external"
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !vol.uuid.isNullOrBlank() -> vol.uuid!!.lowercase()
                    else -> "external"
                }
                candidates.add(VolumePathMatch(volPath, volName, vol.isPrimary))
            }
        } catch (_: Exception) {}

        return candidates.firstOrNull { match ->
            dirPath == match.rootPath || dirPath.startsWith("${match.rootPath}/", ignoreCase = true)
        }
    }

    private fun mergeMediaStoreDirectoryEntries(
        context: Context,
        dir: File,
        rootPath: String,
        volumeName: String,
        relDir: String,
        folders: MutableList<BrowserEntry>,
        files: MutableList<BrowserEntry>,
        seenFolderNames: MutableSet<String>,
        seenFileNames: MutableSet<String>
    ) {
        val collections = buildList {
            add(MediaStore.Images.Media.getContentUri(volumeName))
            add(MediaStore.Video.Media.getContentUri(volumeName))
            add(MediaStore.Audio.Media.getContentUri(volumeName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Downloads.getContentUri(volumeName))
            }
            add(MediaStore.Files.getContentUri(volumeName))
        }
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
        }.toTypedArray()

        val relPrefix = if (relDir.isEmpty()) "" else "$relDir/"

        for (collection in collections) {
            val cursor = try {
                context.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.MediaColumns.SIZE} > 0",
                    null,
                    null
                )
            } catch (_: Exception) {
                null
            } ?: continue

            try {
                cursor.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val modCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    val dataCol = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    } else -1

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val size = if (sizeCol != -1) c.getLong(sizeCol) else 0L
                        if (size <= 0L) continue

                        val dataPath = if (dataCol != -1) c.getString(dataCol) else null
                        val rawRelPath = if (relCol != -1) c.getString(relCol) else null
                        val itemRelDir = when {
                            rawRelPath != null -> rawRelPath.trim('/')
                            dataPath != null && dataPath.startsWith("$rootPath/", ignoreCase = true) -> {
                                dataPath.substring(rootPath.length + 1)
                                    .substringBeforeLast('/', "")
                                    .trim('/')
                            }
                            else -> continue
                        }

                        val displayName = if (nameCol != -1) c.getString(nameCol) else null
                        val name = displayName?.takeIf { it.isNotBlank() }
                            ?: dataPath?.substringAfterLast('/')
                            ?: continue
                        if (name == "." || name == "..") continue

                        if (itemRelDir.equals(relDir, ignoreCase = true)) {
                            if (seenFileNames.add(name.lowercase())) {
                                val mime = if (mimeCol != -1) {
                                    c.getString(mimeCol)?.takeIf { it.contains('/') } ?: guessMimeType(name)
                                } else {
                                    guessMimeType(name)
                                }
                                val modSec = if (modCol != -1) c.getLong(modCol) else 0L
                                val contentUri = ContentUris.withAppendedId(collection, id)
                                val localFile = dataPath?.let { File(it) }?.takeIf { it.exists() && it.canRead() }
                                files.add(
                                    BrowserEntry(
                                        id = if (localFile != null) "file:${localFile.absolutePath}" else "ms:${collection.lastPathSegment}:$id",
                                        name = name,
                                        isDirectory = false,
                                        sizeBytes = size,
                                        mimeType = mime,
                                        lastModifiedMs = modSec * 1000L,
                                        file = localFile,
                                        documentUri = if (localFile == null) contentUri else null
                                    )
                                )
                            }
                        } else if (
                            (relPrefix.isEmpty() && itemRelDir.isNotEmpty()) ||
                            (relPrefix.isNotEmpty() && itemRelDir.startsWith(relPrefix, ignoreCase = true))
                        ) {
                            val remainder = if (relPrefix.isEmpty()) itemRelDir else itemRelDir.substring(relPrefix.length)
                            val subName = remainder.substringBefore('/').trim()
                            if (subName.isNotEmpty() && seenFolderNames.add(subName.lowercase())) {
                                val subDir = File(dir, subName)
                                folders.add(
                                    BrowserEntry(
                                        id = "dir:${subDir.absolutePath}",
                                        name = subName,
                                        isDirectory = true,
                                        sizeBytes = 0L,
                                        mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                                        lastModifiedMs = subDir.lastModified(),
                                        childCount = null,
                                        file = subDir
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun listSafDirectory(
        context: Context,
        treeUri: Uri,
        parentDocId: String
    ): List<BrowserEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val folders = mutableListOf<BrowserEntry>()
        val files = mutableListOf<BrowserEntry>()

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (c.moveToNext()) {
                    val docId = c.getString(idCol) ?: continue
                    val name = if (nameCol != -1) c.getString(nameCol) else null
                    val cleanName = name?.takeIf { it.isNotBlank() } ?: docId.substringAfterLast('/')
                    if (cleanName == "." || cleanName == "..") continue
                    val mime = if (mimeCol != -1) c.getString(mimeCol) ?: "application/octet-stream" else "application/octet-stream"
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val size = if (sizeCol != -1 && !c.isNull(sizeCol)) c.getLong(sizeCol) else 0L
                    val mod = if (modCol != -1 && !c.isNull(modCol)) c.getLong(modCol) else 0L
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                    if (isDir) {
                        folders.add(
                            BrowserEntry(
                                id = "safdir:$treeUri:$docId",
                                name = cleanName,
                                isDirectory = true,
                                sizeBytes = 0L,
                                mimeType = mime,
                                lastModifiedMs = mod,
                                documentUri = docUri,
                                treeUri = treeUri,
                                documentId = docId
                            )
                        )
                    } else if (size >= 0L) {
                        files.add(
                            BrowserEntry(
                                id = "saffile:$treeUri:$docId",
                                name = cleanName,
                                isDirectory = false,
                                sizeBytes = size,
                                mimeType = if (mime.contains('/')) mime else guessMimeType(cleanName),
                                lastModifiedMs = mod,
                                documentUri = docUri,
                                treeUri = treeUri,
                                documentId = docId
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        folders.sortBy { it.name.lowercase() }
        files.sortBy { it.name.lowercase() }
        return folders + files
    }

    suspend fun collectFileItemsForSelection(
        context: Context,
        entries: List<BrowserEntry>
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileItem>()
        val seenKeys = HashSet<String>()

        fun addUnique(item: FileItem) {
            val key = "${item.relativeFolder}/${item.name}|${item.sizeBytes}"
            if (seenKeys.add(key)) {
                results.add(item)
            }
        }

        for (entry in entries) {
            if (!entry.isDirectory) {
                val uriStr = when {
                    entry.documentUri != null -> entry.documentUri.toString()
                    entry.file != null -> Uri.fromFile(entry.file).toString()
                    else -> continue
                }
                addUnique(
                    FileItem(
                        id = UUID.randomUUID().toString(),
                        name = entry.name,
                        sizeBytes = entry.sizeBytes,
                        uriString = uriStr,
                        mimeType = entry.mimeType,
                        status = TransferStatus.PENDING,
                        relativeFolder = ""
                    )
                )
            } else {
                when {
                    entry.file != null -> {
                        val visited = HashSet<String>()
                        collectFolderFromFile(
                            dir = entry.file,
                            relativeFolder = entry.name,
                            depth = 0,
                            visited = visited,
                            onFile = ::addUnique
                        )
                        collectMediaStoreFilesInFolder(
                            context = context,
                            dir = entry.file,
                            rootFolderName = entry.name,
                            onFile = ::addUnique
                        )
                    }
                    entry.treeUri != null && entry.documentId != null -> {
                        val visited = HashSet<String>()
                        collectFolderFromSaf(
                            context = context,
                            treeUri = entry.treeUri,
                            docId = entry.documentId,
                            relativeFolder = entry.name,
                            depth = 0,
                            visited = visited,
                            onFile = ::addUnique
                        )
                    }
                }
            }
        }

        results
    }

    private fun collectMediaStoreFilesInFolder(
        context: Context,
        dir: File,
        rootFolderName: String,
        onFile: (FileItem) -> Unit
    ) {
        val match = resolveStorageVolumeMatch(context, dir) ?: return
        val (rootPath, volumeName, _) = match
        val dirPath = canonicalPathSafe(dir).trimEnd('/')

        val relDir = if (dirPath == rootPath) "" else dirPath.removePrefix("$rootPath/").trim('/')
        val relPrefix = if (relDir.isEmpty()) "" else "$relDir/"

        val collections = buildList {
            add(MediaStore.Images.Media.getContentUri(volumeName))
            add(MediaStore.Video.Media.getContentUri(volumeName))
            add(MediaStore.Audio.Media.getContentUri(volumeName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Downloads.getContentUri(volumeName))
            }
            add(MediaStore.Files.getContentUri(volumeName))
        }
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
        }.toTypedArray()

        for (collection in collections) {
            val cursor = try {
                context.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.MediaColumns.SIZE} > 0",
                    null,
                    null
                )
            } catch (_: Exception) {
                null
            } ?: continue

            try {
                cursor.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val dataCol = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    } else -1

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val size = if (sizeCol != -1) c.getLong(sizeCol) else 0L
                        if (size <= 0L) continue
                        val dataPath = if (dataCol != -1) c.getString(dataCol) else null
                        val rawRelPath = if (relCol != -1) c.getString(relCol) else null
                        val itemRelDir = when {
                            rawRelPath != null -> rawRelPath.trim('/')
                            dataPath != null && dataPath.startsWith("$rootPath/", ignoreCase = true) -> {
                                dataPath.substring(rootPath.length + 1)
                                    .substringBeforeLast('/', "")
                                    .trim('/')
                            }
                            else -> continue
                        }
                        val matches = if (relDir.isEmpty()) {
                            true
                        } else {
                            itemRelDir.equals(relDir, ignoreCase = true) ||
                                itemRelDir.startsWith(relPrefix, ignoreCase = true)
                        }
                        if (!matches) continue

                        val displayName = if (nameCol != -1) c.getString(nameCol) else null
                        val name = displayName?.takeIf { it.isNotBlank() }
                            ?: dataPath?.substringAfterLast('/')
                            ?: continue
                        val subRel = when {
                            relDir.isEmpty() -> itemRelDir
                            itemRelDir.equals(relDir, ignoreCase = true) -> ""
                            else -> itemRelDir.substring(relPrefix.length).trim('/')
                        }
                        val targetRelFolder = if (subRel.isEmpty()) rootFolderName else "$rootFolderName/$subRel"
                        val mime = if (mimeCol != -1) {
                            c.getString(mimeCol)?.takeIf { it.contains('/') } ?: guessMimeType(name)
                        } else {
                            guessMimeType(name)
                        }
                        val contentUri = ContentUris.withAppendedId(collection, id)
                        onFile(
                            FileItem(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                sizeBytes = size,
                                uriString = contentUri.toString(),
                                mimeType = mime,
                                status = TransferStatus.PENDING,
                                relativeFolder = targetRelFolder
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun collectFolderFromFile(
        dir: File,
        relativeFolder: String,
        depth: Int,
        visited: MutableSet<String>,
        onFile: (FileItem) -> Unit
    ) {
        if (depth > MAX_FOLDER_DEPTH) return
        val key = try {
            dir.canonicalPath
        } catch (_: Exception) {
            dir.absolutePath
        }
        if (!visited.add(key)) return

        val children = try {
            dir.listFiles()
        } catch (_: Exception) {
            null
        } ?: return

        val sorted = children.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
        for (child in sorted) {
            val name = child.name
            if (name == "." || name == "..") continue
            if (child.isDirectory) {
                collectFolderFromFile(
                    dir = child,
                    relativeFolder = "$relativeFolder/$name",
                    depth = depth + 1,
                    visited = visited,
                    onFile = onFile
                )
            } else if (child.isFile && child.canRead() && child.length() >= 0L) {
                onFile(
                    FileItem(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        sizeBytes = child.length(),
                        uriString = Uri.fromFile(child).toString(),
                        mimeType = guessMimeType(name),
                        status = TransferStatus.PENDING,
                        relativeFolder = relativeFolder
                    )
                )
            }
        }
    }

    private fun collectFolderFromSaf(
        context: Context,
        treeUri: Uri,
        docId: String,
        relativeFolder: String,
        depth: Int,
        visited: MutableSet<String>,
        onFile: (FileItem) -> Unit
    ) {
        if (depth > MAX_FOLDER_DEPTH) return
        if (!visited.add(docId)) return

        val children = listSafDirectory(context, treeUri, docId)
        for (child in children) {
            if (child.isDirectory) {
                val childDocId = child.documentId ?: continue
                collectFolderFromSaf(
                    context = context,
                    treeUri = treeUri,
                    docId = childDocId,
                    relativeFolder = "$relativeFolder/${child.name}",
                    depth = depth + 1,
                    visited = visited,
                    onFile = onFile
                )
            } else if (child.documentUri != null) {
                onFile(
                    FileItem(
                        id = UUID.randomUUID().toString(),
                        name = child.name,
                        sizeBytes = child.sizeBytes,
                        uriString = child.documentUri.toString(),
                        mimeType = child.mimeType,
                        status = TransferStatus.PENDING,
                        relativeFolder = relativeFolder
                    )
                )
            }
        }
    }

    internal fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val fromMap = if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        } else null
        return fromMap ?: "application/octet-stream"
    }
}
