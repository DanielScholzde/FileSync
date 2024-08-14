package de.danielscholz.fileSync.common

import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime


data class FolderResult(
    val folders: List<FolderEntry>,
    val files: List<FileEntry>
)

class FolderEntry(
    val name: String,
    /*
     * full path including this folders name
     * starts and ends with '/'
     */
    val fullPath: String,
    val content: () -> FolderResult
) {
    init {
        if (!(fullPath.startsWith("/") && fullPath.endsWith("/"))) throw IllegalStateException()
    }
}

class FileEntry(
    val name: String,
    /**
     * excluding file name!
     * starts with '/'
     */
    val path: String,
    val created: Instant,
    val modified: Instant,
    val hidden: Boolean,
    val size: Long,
    val hash: Lazy<String?>
) {
    init {
        if (!path.startsWith("/")) throw IllegalStateException()
        if (path.contains("//")) throw IllegalStateException()
    }
}


fun readDir(dir: File, subPath: String = "/", fs: FileSystemEncryption): FolderResult {
    val files = mutableListOf<FileEntry>()
    val folders = mutableListOf<FolderEntry>()
    val filesAndFolders = dir.listFiles()
    if (filesAndFolders != null) {
        for (file in filesAndFolders.sortedBy { it.name.lowercase() }) {
            val attributes = getBasicFileAttributes(file)
            if (attributes.isDirectory) {
                val path = subPath + file.name + "/"
                folders += FolderEntry(
                    name = file.name,
                    fullPath = path,
                    content = { readDir(file, path, fs) }
                )
            } else if (attributes.isRegularFile) {
                val encrypted = file.name.endsWith(FS_ENCRYPTED)
                val file_ = if (encrypted) File(file.path.removeSuffix(FS_ENCRYPTED)) else file
                val size = fs.getSize(file_)
                val created = attributes.creationTime().toKotlinInstantIgnoreMillis()
                val modified = attributes.lastModifiedTime().toKotlinInstantIgnoreMillis()
                files += FileEntry(
                    name = file_.name,
                    path = subPath,
                    created = created,
                    modified = modified,
                    hidden = file.isHidden,
                    size = size,
                    hash = myLazy {
                        fs.checkIsUnchanged(file_, modified, size)
                        if (size > 0) fs.computeSHA1(file_) else null
                    }
                )
            } else {
                println("$file not processed because it is not a directory nor a regular file")
            }
        }
    } else {
        val msg = "ERROR: $dir: Directory is not readable"
        println(msg)
    }
    return FolderResult(folders, files)
}


fun getBasicFileAttributes(file: File): BasicFileAttributes =
    Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)

fun FileTime.toKotlinInstantIgnoreMillis(): Instant = toInstant().ignoreMillis().toKotlinInstant()


fun isCaseSensitiveFileSystem(dir: File): Boolean? {

    if (dir.path.lowercase() != dir.path.uppercase() && dir.isDirectory) {
        return !(File(dir.path.lowercase()).isDirectory && File(dir.path.uppercase()).isDirectory)
    }

    val filesAndDirs = dir.listFiles()
    if (filesAndDirs != null) {
        for (fileEntry in filesAndDirs) {
            val name = fileEntry.name
            val subDirs = mutableListOf<File>()
            if (fileEntry.isDirectory) {
                if (name.lowercase() != name.uppercase()) {
                    return !(File(fileEntry.path.lowercase()).isDirectory && File(fileEntry.path.uppercase()).isDirectory)
                }
                subDirs.add(fileEntry)
            } else {
                if (name.lowercase() != name.uppercase()) {
                    return !(File(fileEntry.path.lowercase()).isFile && File(fileEntry.path.uppercase()).isFile)
                }
            }
            for (subDir in subDirs) {
                val caseSensitiveFileSystem = isCaseSensitiveFileSystem(subDir)
                if (caseSensitiveFileSystem != null) {
                    return caseSensitiveFileSystem
                }
            }
        }
    }
    return null
}