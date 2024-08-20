package de.danielscholz.fileSync.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max
import kotlin.time.measureTime
import kotlin.time.measureTimedValue


private fun deriveSecretKeyFromPassword(salt: ByteArray, password: String): SecretKey {
    val (aesSecretKey, duration) = measureTimedValue {
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keySpec = PBEKeySpec(password.toCharArray(), salt, 1_000_000, 256)
        val secretKey = secretKeyFactory.generateSecret(keySpec)
        val aesSecretKey = SecretKeySpec(secretKey.encoded, "AES")
        aesSecretKey
    }
    //println("Password key derivation function duration: $duration")
    return aesSecretKey
}

typealias SaltHexStr = String

private val keyCache = mutableMapOf<Pair<SaltHexStr, String>, SecretKey>()

@OptIn(ExperimentalStdlibApi::class)
private fun deriveSecretKeyFromPasswordCached(salt: ByteArray, password: String) =
    keyCache.computeIfAbsent(salt.toHexString(kotlin.text.HexFormat.UpperCase) to password) {
        deriveSecretKeyFromPassword(salt, password)
    }

@OptIn(ExperimentalStdlibApi::class)
private fun getCachedSaltOrNull() =
    keyCache.keys.firstOrNull()?.first?.hexToByteArray(kotlin.text.HexFormat.UpperCase)


private const val randomBytesSize = 16
internal const val BUFFER_SIZE = 1024 * 16 // 16 kb

private const val SHA1_BYTES = 160 / 8

const val AES_FILESIZE_OVERHEAD = randomBytesSize * 2 + SHA1_BYTES // iv + salt + SHA1

private fun generateRandomBytes(): ByteArray {
    val bytes = ByteArray(randomBytesSize)
    SecureRandom().nextBytes(bytes)
    return bytes
}


suspend fun Flow<ByteArray>.encryptToFile(outputFile: File, password: String) {
    val salt = getCachedSaltOrNull() ?: generateRandomBytes()
    val key = deriveSecretKeyFromPasswordCached(salt, password)

    val iv = generateRandomBytes()
    val cipher = getCipher(key, iv, Cipher.ENCRYPT_MODE)

    val digest = MessageDigest.getInstance("SHA-1")
    if (digest.digestLength != SHA1_BYTES) throw Exception()

    FileOutputStream(outputFile).use { outputStream ->
        outputStream.write(iv)
        outputStream.write(salt)

        this.collect { data ->
            //println("encryptToFile.collect: ${data.size}  ${data.sum()}  ${Thread.currentThread().name}")
            digest.update(data)
            cipher.update(data)?.let { encryptedData ->
                //println("write encrypted block (${Thread.currentThread().name})")
                outputStream.write(encryptedData)
            }
        }
        //println("write encrypted finished")
        outputStream.write(cipher.doFinal())

        val sha1 = digest.digest()
        outputStream.write(sha1)
        //println("SHA-1 of encrypted file ${outputFile.name}: ${sha1.toBase64()}")
    }
}

//fun Flow<ByteArray>.encryptToFlow(password: String) = flow<ByteArray> {
//    val salt = getCachedSaltOrNull() ?: generateRandomBytes()
//    val key = deriveSecretKeyFromPasswordCached(salt, password)
//
//    val iv = generateRandomBytes()
//    val cipher = getCipher(key, iv, Cipher.ENCRYPT_MODE)
//
//    val digest = MessageDigest.getInstance("SHA-1")
//    if (digest.digestLength != SHA1_BYTES) throw Exception()
//
//    emit(iv)
//    emit(salt)
//
//    this@encryptToFlow.collect { data ->
//        digest.update(data)
//        cipher.update(data, 0, data.size)?.let { encryptedData ->
//            //println("write encrypted block (${Thread.currentThread().name})")
//            emit(encryptedData)
//        }
//    }
//    //println("write encrypted finished")
//    emit(cipher.doFinal())
//    emit(digest.digest())
//}


fun decryptFileToFlow(inputFile: File, password: String) = flow<ByteArray> {
    FileInputStream(inputFile).use { inputStream ->
        val iv = ByteArray(randomBytesSize)
        val salt = ByteArray(randomBytesSize)
        if (inputStream.read(iv) != randomBytesSize) throw Exception()
        if (inputStream.read(salt) != randomBytesSize) throw Exception()

        val key = deriveSecretKeyFromPasswordCached(salt, password)
        val cipher = getCipher(key, iv, Cipher.DECRYPT_MODE)

        val digest = MessageDigest.getInstance("SHA-1")
        if (digest.digestLength != SHA1_BYTES) throw Exception()

        val buffer = ByteArray(BUFFER_SIZE)
        val fileSizeNetto = inputFile.length() - AES_FILESIZE_OVERHEAD
        var bytesRead: Int
        var totalBytesRead = 0L
        var sha1: ByteArray? = null

        while (inputStream.read(buffer).also { bytesRead = it } > 0) {
            totalBytesRead += bytesRead

            if (totalBytesRead > fileSizeNetto) {
                val overEnd = (totalBytesRead - fileSizeNetto).toInt() // must be > 0 and <= 20
                // hint: bytesRead - overEnd can be less than 0 if last part of sha1 bytes are read into last buffer (bytesRead could be 10)
                sha1 = buffer.copyOfRange(max(bytesRead - overEnd, 0), bytesRead).let { if (sha1 != null) sha1!! + it else it }

                cipher.update(buffer, 0, max(bytesRead - overEnd, 0))?.let {
                    digest.update(it)
                    emit(it)
                }
            } else {
                cipher.update(buffer, 0, bytesRead)?.let {
                    digest.update(it)
                    emit(it)
                }
            }
        }
        cipher.doFinal().let {
            digest.update(it)
            emit(it)
        }

        if (!digest.digest().contentEquals(sha1)) throw Exception("Decoding of encrypted file results in different SHA-1 checksum!")
        //println("SHA-1 included within ${inputFile.name}: ${sha1.toBase64()}")
    }
}


private fun getCipher(key: SecretKey, iv: ByteArray, mode: Int): Cipher =
    Cipher.getInstance("AES/CFB/NoPadding").apply { init(mode, key, IvParameterSpec(iv)) }


fun main(): Unit = runBlocking {

//    val flow = flow {
//        emit(1)
//        emit(2)
//        emit(3)
//        emit(4)
//    }
//    flow.tee({ collect { println("1: "+it+" "+System.nanoTime()) } }, { collect { println("2: "+it+" "+System.nanoTime()) } })
//    println()
//    flow.tee({ collect { println("1: "+it) } }, { collect { println("2: "+it) } })
//
//return@runBlocking Unit

    val password = "test"
    val file = "Test_copy.mpg"

    // warmup
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)

    measureTime {
        readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
    }.let { println(it) }

    if (File("$file.encrypt").length() != File(file).length() + AES_FILESIZE_OVERHEAD) throw Exception("Size")

    decryptFileToFlow(File("$file.encrypt"), password).writeToFile(File("$file.tmp"))

    if (!Files.readAllBytes(File(file).toPath()).contentEquals(Files.readAllBytes(File("$file.tmp").toPath()))) throw Exception("UNGLEICH!")

    File("$file.tmp").delete()
    File("$file.encrypt").delete()
}