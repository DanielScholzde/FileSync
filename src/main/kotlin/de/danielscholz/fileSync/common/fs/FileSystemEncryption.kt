package de.danielscholz.fileSync.common.fs

import de.danielscholz.fileSync.actions.sync.Env
import de.danielscholz.fileSync.common.ensurePrefix
import de.danielscholz.fileSync.common.ensureSuffix
import de.danielscholz.fileSync.common.myLazy
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.attribute.FileTime
import kotlin.time.Instant


const val FS_ENCRYPTED_SUFFIX = ".fsencrypted"


class FileSystemEncryption private constructor(
    val source: Env,
    val target: Env,
    val changedDir: String,
    val deletedDir: String,
    val dryRun: Boolean,
    dummy: Unit
) {

    constructor(source: Env, target: Env, changedDir: String, deletedDir: String, dryRun: Boolean) :
            this(source, target, changedDir.replace('\\', '/').ensurePrefix("/"), deletedDir.replace('\\', '/').ensurePrefix("/"), dryRun, Unit)

    val sourceDirCanonicalPath: String = source.dir.canonicalPath.replace('\\', '/')
    val targetDirCanonicalPath: String = target.dir.canonicalPath.replace('\\', '/')

    enum class EncrState { ENCRYPTED, NOT_ENCRYPTED }

    private enum class Action { COPY, MOVE }

    fun move(from: File, to: File, expectedHash: String?): EncrState { // TODO moved files does from not encr. to encr. dir are not displayed as content to transfer within UI
        val fileSize = getSize(from)
        return execAction(
            fileDecorator(from, fileSize),
            fileDecorator(to, fileSize),
            expectedHash,
            Action.MOVE
        )
    }

    fun copy(from: File, to: File, expectedHash: String?): EncrState {
        val fileSize = getSize(from)
        return execAction(
            fileDecorator(from, fileSize),
            fileDecorator(to, fileSize),
            expectedHash,
            Action.COPY
        )
    }

    private fun execAction(from: FileDecorator, to: FileDecorator, expectedHash: String?, action: Action): EncrState {

        if (dryRun) return if (to.shouldEncrypt) EncrState.ENCRYPTED else EncrState.NOT_ENCRYPTED

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

        fun Action.execDirect(source: File, target: File) {
            when (this) {
                Action.COPY -> Files.copy(source.toPath(), target.toPath(), COPY_ATTRIBUTES)
                Action.MOVE -> Files.move(source.toPath(), target.toPath())
            }
        }

        when {
            from.encrypted && to.shouldEncrypt -> {
                if (from.encryptPassword == to.encryptPassword) {
                    action.execDirect(from.fileIn, to.fileOut)
                } else {
                    decryptFileToSeq(from.fileIn, from.encryptPassword).encryptSeqToFile(to.fileOut, to.encryptPassword)
                    if (decryptFileToSeq(to.fileOut, to.encryptPassword).computeSHA1() != expectedHash && expectedHash != null) throw Exception("Hash is not equal to expected")
                    copyLastModifiedIntern(from.fileIn, to.fileOut)
                    if (action == Action.MOVE) Files.delete(from.fileIn.toPath())
                }
            }
            !from.encrypted && to.shouldEncrypt -> {
                readFileToSeq(from.fileIn).encryptSeqToFile(to.fileOut, to.encryptPassword)
                if (decryptFileToSeq(to.fileOut, to.encryptPassword).computeSHA1() != expectedHash && expectedHash != null) throw Exception("Hash is not equal to expected")
                copyLastModifiedIntern(from.fileIn, to.fileOut)
                if (action == Action.MOVE) Files.delete(from.fileIn.toPath())
            }
            from.encrypted && !to.shouldEncrypt -> {
                decryptFileToSeq(from.fileIn, from.encryptPassword).writeSeqToFile(to.fileOut)
                if (readFileToSeq(to.fileOut).computeSHA1() != expectedHash && expectedHash != null) throw Exception("Hash is not equal to expected")
                copyLastModifiedIntern(from.fileIn, to.fileOut)
                if (action == Action.MOVE) Files.delete(from.fileIn.toPath())
            }
            else -> {
                action.execDirect(from.fileIn, to.fileOut)
            }
        }

        return if (to.shouldEncrypt) EncrState.ENCRYPTED else EncrState.NOT_ENCRYPTED
    }

    fun copyLastModified(from: File, to: File) {
        if (dryRun) return
        fileDecorator(to).fileIn.setLastModified(fileDecorator(from).fileIn.lastModified()) || throw Exception("set of last modification date failed!")
    }

    private fun copyLastModifiedIntern(from: File, to: File) {
        if (dryRun) return
        to.setLastModified(from.lastModified()) || throw Exception("set of last modification date failed!")
    }

    fun createDirsFor(dir: File) {
        if (dryRun || dir.isDirectory) return
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
        return fileDecorator(file).fileSize()
    }

    fun checkIsUnchanged(file: File, expectedLastModified: Instant, expectedSize: Long) {
        val file2 = fileDecorator(file)
        val modifiedChanged = FileTime.fromMillis(file2.fileIn.lastModified()).toKotlinInstantIgnoreMillis() != expectedLastModified
        val sizeChanged = file2.fileSize() != expectedSize
        if (modifiedChanged || sizeChanged) {
            throw Exception("File ${file.name} has changed since indexing!")
        }
    }

    fun computeSHA1(file: File): String {
        val file2 = fileDecorator(file)
        val hash = (if (file2.encrypted) decryptFileToSeq(file2.fileIn, file2.encryptPassword) else readFileToSeq(file2.fileIn)).computeSHA1()
        return hash
    }

    private fun fileDecorator(file: File) = FileDecorator(file, this)

    private fun fileDecorator(file: File, fileSize: Long) = FileDecorator(file, this, fileSize)
}


class FileDecorator(val file: File, fs: FileSystemEncryption, fileSize: Long? = null) {

    private val filePath: String // without a file name! starts and ends with a '/'

    private val env: Env

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

    private fun File.toEncryptedPath() = File(this.path + FS_ENCRYPTED_SUFFIX)

    private fun File.isEncrypted() = when {
        this.isFile -> false
        toEncryptedPath().isFile -> true
        else -> throw Exception("File $this does not exist!")
    }

    private fun shouldEncrypt(filePath: String, env: Env, fileSize: Long): Boolean {
        val folderName = filePath.removeSuffix("/").substringAfterLast('/')
        val b = env.password != null && env.encryptPaths.any { it.matches(filePath, folderName) }
        return b && fileSize > 0
    }
}
