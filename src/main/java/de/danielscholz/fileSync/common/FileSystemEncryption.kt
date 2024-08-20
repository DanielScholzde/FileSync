package de.danielscholz.fileSync.common

import de.danielscholz.fileSync.actions.sync.SyncFiles
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.attribute.FileTime


const val FS_ENCRYPTED = ".fsencrypted"


class FileSystemEncryption private constructor(
    val source: SyncFiles.Env,
    val target: SyncFiles.Env,
    val changedDir: String,
    val deletedDir: String,
    val dryRun: Boolean,
    dummy: Unit
) {

    constructor(source: SyncFiles.Env, target: SyncFiles.Env, changedDir: String, deletedDir: String, dryRun: Boolean) :
            this(source, target, changedDir.replace('\\', '/').ensurePrefix("/"), deletedDir.replace('\\', '/').ensurePrefix("/"), dryRun, Unit)

    val sourceDirCanonicalPath: String = source.dir.canonicalPath.replace('\\', '/')
    val targetDirCanonicalPath: String = target.dir.canonicalPath.replace('\\', '/')

    enum class State { ENCRYPTED, NOT_ENCRYPTED }

    private enum class Action { COPY, MOVE }

    fun move(from: File, to: File, expectedHash: String?): State {
        val fileSize = getSize(from)
        return execAction(
            File2(from, fileSize),
            File2(to, fileSize),
            expectedHash,
            Action.MOVE
        )
    }

    fun copy(from: File, to: File, expectedHash: String?): State {
        val fileSize = getSize(from)
        return execAction(
            File2(from, fileSize),
            File2(to, fileSize),
            expectedHash,
            Action.COPY
        )
    }

    private fun execAction(from: File2, to: File2, expectedHash: String?, action: Action): State {

        if (dryRun) return if (to.shouldEncrypt) State.ENCRYPTED else State.NOT_ENCRYPTED

//        suspend fun Flow<ByteArray>.runWithHashCheck(sink: suspend Flow<ByteArray>.() -> Unit) {
            // TODO
//            if (expectedHash != null) {
//                val hash = this.tee(sink, { computeSHA1() }).second
//                if (hash != expectedHash) {
//                    //throw Exception("Hash is not equal! Maybe the Password is wrong or File has changed since indexing!")
//                    println("Hash different: $hash != $expectedHash")
//                }
//            } else {
//            this.sink()
//            }
//        }

        fun Action.exec(source: File, target: File) {
            when (this) {
                Action.COPY -> Files.copy(source.toPath(), target.toPath(), COPY_ATTRIBUTES)
                Action.MOVE -> Files.move(source.toPath(), target.toPath())
            }
        }

        when {
            from.encrypted && to.shouldEncrypt -> {
                if (from.encryptPassword == to.encryptPassword) {
                    action.exec(from.fileIn, to.fileOut)
                } else {
                    runBlocking {
                        decryptFileToFlow(from.fileIn, from.encryptPassword).encryptToFile(to.fileOut, to.encryptPassword)
                        if (decryptFileToFlow(
                                to.fileOut,
                                to.encryptPassword
                            ).computeSHA1() != expectedHash && expectedHash != null
                        ) throw Exception("Hash is not equal to expected")
                    }
                    copyLastModifiedIntern(from.fileIn, to.fileOut)
                    if (action == Action.MOVE) Files.delete(from.fileIn.toPath())
                }
            }
            !from.encrypted && to.shouldEncrypt -> {
                runBlocking {
                    readFile(from.fileIn).encryptToFile(to.fileOut, to.encryptPassword)
                    if (decryptFileToFlow(to.fileOut, to.encryptPassword).computeSHA1() != expectedHash && expectedHash != null) throw Exception("Hash is not equal to expected")
                }
                copyLastModifiedIntern(from.fileIn, to.fileOut)
                if (action == Action.MOVE) Files.delete(from.fileIn.toPath())
            }
            from.encrypted && !to.shouldEncrypt -> {
                runBlocking {
                    decryptFileToFlow(from.fileIn, from.encryptPassword).writeToFile(to.fileOut)
                    if (readFile(to.fileOut).computeSHA1() != expectedHash && expectedHash != null) throw Exception("Hash is not equal to expected")
                }
                copyLastModifiedIntern(from.fileIn, to.fileOut)
                if (action == Action.MOVE) Files.delete(from.fileIn.toPath())
            }
            else -> {
                action.exec(from.fileIn, to.fileOut)
            }
        }

        return if (to.shouldEncrypt) State.ENCRYPTED else State.NOT_ENCRYPTED
    }

    fun copyLastModified(from: File, to: File) {
        if (dryRun) return
        File2(to).fileIn.setLastModified(File2(from).fileIn.lastModified()) || throw Exception("set of last modification date failed!")
    }

    private fun copyLastModifiedIntern(from: File, to: File) {
        if (dryRun) return
        to.setLastModified(from.lastModified()) || throw Exception("set of last modification date failed!")
    }

    fun createDirsFor(dir: File) {
        if (dryRun) return
        dir.mkdirs() || throw Exception("Creation of directory ${dir.absolutePath} failed")
    }

    fun deleteFile(file: File) {
        if (dryRun) return
        file.delete() || throw Exception("File $file could not be deleted!")
    }

    fun deleteDir(file: File) {
        if (dryRun) return
        file.delete() || throw Exception("Directory $file could not be deleted!")
    }

    fun getSize(file: File): Long {
        return File2(file).fileSize()
    }

    fun checkIsUnchanged(file: File, expectedLastModified: Instant, expectedSize: Long) {
        val file2 = File2(file)
        val modifiedChanged = FileTime.fromMillis(file2.fileIn.lastModified()).toKotlinInstantIgnoreMillis() != expectedLastModified
        val sizeChanged = file2.fileSize() != expectedSize
        if (modifiedChanged || sizeChanged) {
            throw Exception("File ${file.name} has changed since indexing!")
        }
    }

    fun computeSHA1(file: File): String {
        val file2 = File2(file)
        val hash = runBlocking {
            val flow = if (file2.encrypted) decryptFileToFlow(file2.fileIn, file2.encryptPassword) else readFile(file2.fileIn)
            flow.computeSHA1()
        }
        return hash
    }

    private fun File2(file: File) = File2(file, this)

    private fun File2(file: File, fileSize: Long) = File2(file, this, fileSize)
}


class File2(val file: File, fs: FileSystemEncryption, fileSize: Long? = null) {

    private val filePath: String // without file name! starts and ends with a '/'

    private val env: SyncFiles.Env

    val encryptPassword: String get() = env.password!!

    init {
        val canonicalPath = file.parentFile.canonicalPath.replace('\\', '/')

        fun String.removeOther(): String {
            return this.removePrefix(fs.changedDir).removePrefix(fs.deletedDir).ensurePrefix("/").ensureSuffix("/")
        }

        when {
            canonicalPath.startsWith(fs.sourceDirCanonicalPath) -> {
                filePath = canonicalPath.removePrefix(fs.sourceDirCanonicalPath).removeOther()
                env = fs.source
            }
            canonicalPath.startsWith(fs.targetDirCanonicalPath) -> {
                filePath = canonicalPath.removePrefix(fs.targetDirCanonicalPath).removeOther()
                env = fs.target
            }
            else -> throw Error()
        }
        if (filePath.contains('\\') || filePath.contains("//")) throw Error("ERROR: $filePath")
        if (!filePath.startsWith("/") || !filePath.endsWith("/")) throw Error("ERROR: $filePath")
    }

    val encrypted by myLazy { file.isEncrypted() }
    val shouldEncrypt by myLazy { shouldEncrypt(filePath, env, fileSize!!) }

    val fileIn by myLazy { if (encrypted) file.toEncryptedPath() else file }
    val fileOut by myLazy { if (shouldEncrypt) file.toEncryptedPath() else file }

    fun fileSize() = if (encrypted) (fileIn.length() - AES_FILESIZE_OVERHEAD) else fileIn.length()

    private fun File.toEncryptedPath() = File(this.path + FS_ENCRYPTED)

    private fun File.isEncrypted() = when {
        this.isFile -> false
        toEncryptedPath().isFile -> true
        else -> throw Exception("File $this does not exist!")
    }

    private fun shouldEncrypt(filePath: String, env: SyncFiles.Env, fileSize: Long): Boolean {
        val folderName = filePath.removeSuffix("/").substringAfterLast('/')
        val b = env.password != null && env.encryptPaths.any { it.matches(filePath, folderName) }
        return b && fileSize > 0
    }
}
