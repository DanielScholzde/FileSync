package de.danielscholz.fileSync.common

import de.danielscholz.fileSync.actions.sync.SyncFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES


private const val _FSENCRYPTED = ".fsencrypted"


class FileSystemAbstraction(val sourceEnv: SyncFiles.Env, val targetEnv: SyncFiles.Env) {

    @Suppress("NAME_SHADOWING")
    fun move(from: File, to: File) {
        val from = File2(from, sourceEnv, targetEnv)
        val to = File2(to, sourceEnv, targetEnv)
        when {
            from.encrypted && to.shouldEncrypt -> {
                if (from.encryptPassword == to.encryptPassword) {
                    Files.move(from.fileIn.toPath(), to.fileOut.toPath())
                } else {
                    runBlocking {
                        decryptFileToFlow(from.fileIn, from.encryptPassword).encryptToFile(to.fileOut, to.encryptPassword)
                    }
                    to.fileOut.setLastModified(from.fileIn.lastModified())
                    Files.delete(from.fileIn.toPath())
                }
            }
            !from.encrypted && to.shouldEncrypt -> {
                runBlocking {
                    readFile(from.fileIn).encryptToFile(to.fileOut, to.encryptPassword)
                }
                to.fileOut.setLastModified(from.fileIn.lastModified())
                Files.delete(from.fileIn.toPath())
            }
            from.encrypted && !to.shouldEncrypt -> {
                runBlocking {
                    decryptFileToFlow(from.fileIn, from.encryptPassword).writeToFile(to.fileOut)
                }
                to.fileOut.setLastModified(from.fileIn.lastModified())
                Files.delete(from.fileIn.toPath())
            }
            else -> {
                Files.move(from.fileIn.toPath(), to.fileOut.toPath())
            }
        }
    }

    @Suppress("NAME_SHADOWING")
    fun copy(from: File, to: File) {
        val from = File2(from, sourceEnv, targetEnv)
        val to = File2(to, sourceEnv, targetEnv)
        when {
            from.encrypted && to.shouldEncrypt -> {
                if (from.encryptPassword == to.encryptPassword) {
                    Files.copy(from.fileIn.toPath(), to.fileOut.toPath(), COPY_ATTRIBUTES)
                } else {
                    runBlocking {
                        decryptFileToFlow(from.fileIn, from.encryptPassword).encryptToFile(to.fileOut, to.encryptPassword)
                    }
                    to.fileOut.setLastModified(from.fileIn.lastModified())
                }
            }
            !from.encrypted && to.shouldEncrypt -> {
                runBlocking {
                    readFile(from.fileIn).encryptToFile(to.fileOut, to.encryptPassword)
                }
                to.fileOut.setLastModified(from.fileIn.lastModified())
            }
            from.encrypted && !to.shouldEncrypt -> {
                runBlocking {
                    decryptFileToFlow(from.fileIn, from.encryptPassword).writeToFile(to.fileOut)
                }
                to.fileOut.setLastModified(from.fileIn.lastModified())
            }
            else -> {
                Files.copy(from.fileIn.toPath(), to.fileOut.toPath(), COPY_ATTRIBUTES)
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

class File2(file: File, source: SyncFiles.Env, target: SyncFiles.Env) {

    val encrypted = file.isEncrypted()
    val shouldEncrypt = file.shouldEncrypt(source, target)

    val fileIn = if (encrypted) file.toEncryptedPath() else file
    val fileOut = if (shouldEncrypt) file.toEncryptedPath() else file

    lateinit var encryptPassword: String
        private set

    init {
        if (encrypted) {
            val canonicalPath = file.canonicalPath

            encryptPassword = when {
                canonicalPath.startsWith(source.dir.canonicalPath) -> source.password!!
                canonicalPath.startsWith(target.dir.canonicalPath) -> target.password!!
                else -> throw Exception("Password for encryption is missing!")
            }
        }
    }
}

fun File.toEncryptedPath(): File {
    return File(this.path + _FSENCRYPTED)
}

fun File.isEncrypted(): Boolean {
    return toEncryptedPath().isFile
}

fun File.shouldEncrypt(source: SyncFiles.Env, target: SyncFiles.Env): Boolean {
    val canonicalPath = this.canonicalPath
    val name = this.name
    if (canonicalPath.startsWith(source.dir.canonicalPath)) {
        return source.password != null && source.encryptPaths.any { it.matches(canonicalPath, name) }
    }
    if (canonicalPath.startsWith(target.dir.canonicalPath)) {
        return target.password != null && target.encryptPaths.any { it.matches(canonicalPath, name) }
    }
    throw IllegalStateException()
}