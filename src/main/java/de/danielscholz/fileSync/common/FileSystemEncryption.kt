package de.danielscholz.fileSync.common

import de.danielscholz.fileSync.actions.sync.SyncFiles
import de.danielscholz.fileSync.persistence.FileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES


private const val FS_ENCRYPTED = ".fsencrypted"


class FileSystemEncryption(private val source: SyncFiles.Env, private val target: SyncFiles.Env, private val changedDir: String, private val deletedDir: String) {

    fun createDirsFor(dir: File) {
        dir.mkdirs() || throw Exception("Creation of directory ${dir.absolutePath} failed")
    }

    @Suppress("NAME_SHADOWING")
    fun move(from: File, to: File) {
        val from = File2(from, source, target, changedDir, deletedDir)
        val to = File2(to, source, target, changedDir, deletedDir)
        when {
            from.encrypted && to.shouldEncrypt -> {
                if (from.encryptPassword == to.encryptPassword) {
                    Files.move(from.fileIn.toPath(), to.fileOut.toPath())
                } else {
                    runBlocking {
                        decryptFileToFlow(from.fileIn, from.encryptPassword).encryptToFile(to.fileOut, to.encryptPassword)
                    }
                    copyLastModified(from, to)
                    Files.delete(from.fileIn.toPath())
                }
            }
            !from.encrypted && to.shouldEncrypt -> {
                runBlocking {
                    readFile(from.fileIn).encryptToFile(to.fileOut, to.encryptPassword)
                }
                copyLastModified(from, to)
                Files.delete(from.fileIn.toPath())
            }
            from.encrypted && !to.shouldEncrypt -> {
                runBlocking {
                    decryptFileToFlow(from.fileIn, from.encryptPassword).writeToFile(to.fileOut)
                }
                copyLastModified(from, to)
                Files.delete(from.fileIn.toPath())
            }
            else -> {
                Files.move(from.fileIn.toPath(), to.fileOut.toPath())
            }
        }
    }

    @Suppress("NAME_SHADOWING")
    fun copy(from: File, to: File, expectedHash: String?) {
        val from = File2(from, source, target, changedDir, deletedDir)
        val to = File2(to, source, target, changedDir, deletedDir)
        when {
            from.encrypted && to.shouldEncrypt -> {
                if (from.encryptPassword == to.encryptPassword) {
                    Files.copy(from.fileIn.toPath(), to.fileOut.toPath(), COPY_ATTRIBUTES)
                } else {
                    runBlocking {
                        decryptFileToFlow(from.fileIn, from.encryptPassword).encryptToFile(to.fileOut, to.encryptPassword)
                    }
                    copyLastModified(from, to)
                }
            }
            !from.encrypted && to.shouldEncrypt -> {
                runBlocking {
                    readFile(from.fileIn).let {
                        val hash = it.tee({ encryptToFile(to.fileOut, to.encryptPassword) }, { computeSHA1() }).second
                        if (hash != expectedHash) throw Exception("Hash is not equal!")
                    }
                }
                copyLastModified(from, to)
            }
            from.encrypted && !to.shouldEncrypt -> {
                runBlocking {
                    decryptFileToFlow(from.fileIn, from.encryptPassword).writeToFile(to.fileOut)
                }
                copyLastModified(from, to)
            }
            else -> {
                Files.copy(from.fileIn.toPath(), to.fileOut.toPath(), COPY_ATTRIBUTES)
            }
        }
    }

    @Suppress("NAME_SHADOWING")
    fun copyLastModified(from: File, to: File) {
        val from = File2(from, source, target, changedDir, deletedDir)
        val to = File2(to, source, target, changedDir, deletedDir)

        copyLastModified(from, to)
    }

    private fun copyLastModified(from: File2, to: File2) {
        to.fileOut.setLastModified(from.fileIn.lastModified()) || throw Exception("set of last modification date failed!")
    }

    fun checkIsUnchanged(file: File, attributes: FileEntity) {
        val file2 = File2(file, source, target, changedDir, deletedDir)
        getBasicFileAttributes(file2.fileIn).let {
            if (it.lastModifiedTime().toKotlinInstantIgnoreMillis() != attributes.modified || it.size() != attributes.size) {
                throw Exception("File ${file.name} has changed since indexing!")
            }
        }
    }

    private fun readFile(file: File): Flow<ByteArray> = flow {
        FileInputStream(file).use { inputStream ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } > 0) {
                if (buffer.size == bytesRead) {
                    emit(buffer) // Attention: no copy!!
                } else {
                    emit(buffer.copyOf(bytesRead))
                }
            }
        }
    }

    private suspend fun Flow<ByteArray>.writeToFile(file: File) {
        FileOutputStream(file).use { outputStream ->
            this.collect { data ->
                outputStream.write(data)
            }
        }
    }
}

class File2(file: File, val source: SyncFiles.Env, val target: SyncFiles.Env, changedDir: String, deletedDir: String) {

    private val filePath: String

    private val env: SyncFiles.Env

    val encryptPassword: String get() = env.password!!

    init {
        val canonicalPath = file.canonicalPath
        val prefix1 = source.dir.canonicalPath
        val prefix2 = target.dir.canonicalPath

        fun String.removeOther(): String {
            return this.removePrefix(changedDir).removePrefix(deletedDir)
        }

        when {
            canonicalPath.startsWith(prefix1) -> {
                filePath = canonicalPath.removePrefix(prefix1).removeOther()
                env = source
            }
            canonicalPath.startsWith(prefix2) -> {
                filePath = canonicalPath.removePrefix(prefix2).removeOther()
                env = target
            }
            else -> throw IllegalStateException()
        }
    }

    val encrypted = file.isEncrypted()
    val shouldEncrypt = file.shouldEncrypt()

    val fileIn = if (encrypted) file.toEncryptedPath() else file
    val fileOut = if (shouldEncrypt) file.toEncryptedPath() else file

    private fun File.toEncryptedPath(): File {
        return File(this.path + FS_ENCRYPTED)
    }

    private fun File.isEncrypted(): Boolean {
        return toEncryptedPath().isFile
    }

    private fun File.shouldEncrypt(): Boolean {
        val name = this.name
        return env.password != null && env.encryptPaths.any { it.matches(filePath, name) }
    }
}
