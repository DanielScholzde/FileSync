package de.danielscholz.fileSync.common

import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes


data class ReadDirResult(
    val content: FolderResult,
    val caseSensitiveFileSystem: Boolean
)

data class FolderResult(
    val folders: List<FolderEntry>,
    val files: List<FileResult>
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

class FileResult(
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


private val logger = LoggerFactory.getLogger("ReadDir")


fun readDir(dir: File, subPath: String = "/"): FolderResult {
    val files = mutableListOf<FileResult>()
    val folders = mutableListOf<FolderEntry>()
    val filesAndFolders = dir.listFiles()
    if (filesAndFolders != null) {
        for (fileEntry in filesAndFolders.sortedBy { it.name.lowercase() }) {
            val attributes = Files.readAttributes(fileEntry.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            if (attributes.isDirectory) {
                val path = subPath + fileEntry.name + "/"
                folders += FolderEntry(
                    name = fileEntry.name,
                    fullPath = path,
                    content = { readDir(fileEntry, path) }
                )
            } else if (attributes.isRegularFile) {
                val size = attributes.size()
                files += FileResult(
                    name = fileEntry.name,
                    path = subPath,
                    created = attributes.creationTime().toInstant().ignoreMillis().toKotlinInstant(),
                    modified = attributes.lastModifiedTime().toInstant().ignoreMillis().toKotlinInstant(),
                    hidden = fileEntry.isHidden,
                    size = size,
                    hash = myLazy {
                        if (size > 0) {
                            // TODO size may have changed
                            val checksumCreator = ChecksumCreator(BufferedInputStream(FileInputStream(fileEntry)), size, null, null)
                            checksumCreator.calcChecksum().sha1
                        } else null
                    }
                )
            } else {
                logger.warn("$fileEntry not processed because it is not a directory nor a regular file")
            }
        }
    } else {
        val msg = "ERROR: $dir: Directory is not readable"
        logger.error(msg)
    }
    return FolderResult(folders, files)
}


//class Path(val path: String, val originalPath: String, var used: Boolean = false)
//
//private fun List<Path>.removeFirstPathElement(): List<Path> {
//    if (this.isEmpty()) return this
//    return this.mapNotNull {
//        val s = it.path.removePrefix("/").removeSuffix("/")
//        if (s.contains('/')) Path(s.substring(s.indexOf('/') + 1), it.originalPath) else null
//    }
//}
//
//private fun matchesPath(name: String, includePaths: List<Path>, caseSensitive: Boolean): List<Path> {
//    var matched = listOf<Path>()
//    if (includePaths.isNotEmpty()) {
//        matched = includePaths.filter {
//            val b = it.path.equals(name, !caseSensitive) || it.path.startsWith("$name/", !caseSensitive)
//            if (b) it.used = true
//            b
//        }
//    }
//    return matched
//}


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