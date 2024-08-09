@file:OptIn(ExperimentalStdlibApi::class)

package de.danielscholz.fileSync.common

import kotlinx.coroutines.flow.flow
import java.io.*
import java.nio.file.Files
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.time.measureTimedValue


private fun deriveSecretKeyFromPassword(salt: ByteArray, password: String): SecretKey {
    val (aesSecretKey, duration) = measureTimedValue {
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keySpec = PBEKeySpec(password.toCharArray(), salt, 3_000_000, 256)
        val secretKey = secretKeyFactory.generateSecret(keySpec)
        val aesSecretKey = SecretKeySpec(secretKey.encoded, "AES")
        aesSecretKey
    }
    println("Password key derivation function duration: $duration")
    return aesSecretKey
}

typealias SaltHexStr = String

private val keyCache = mutableMapOf<Pair<SaltHexStr, String>, SecretKey>()

private fun deriveSecretKeyFromPasswordCached(salt: ByteArray, password: String) =
    keyCache.computeIfAbsent(salt.toHexString(HexFormat.UpperCase) to password) {
        deriveSecretKeyFromPassword(salt, password)
    }

private fun getCachedSaltOrNull() =
    keyCache.keys.firstOrNull()?.first?.hexToByteArray(HexFormat.UpperCase)


private const val randomBytesSize = 16

private fun generateRandomBytes(): ByteArray {
    val bytes = ByteArray(randomBytesSize)
    SecureRandom().nextBytes(bytes)
    return bytes
}

fun encryptFile(password: String, inputFile: File, outputFile: File) {
    val iv = generateRandomBytes()
    val salt = getCachedSaltOrNull() ?: generateRandomBytes()

    val key = deriveSecretKeyFromPasswordCached(salt, password)
    val cipher = getCipher(key, iv, Cipher.ENCRYPT_MODE)

    FileInputStream(inputFile).use { inputStream ->
        //val size = inputFile.length()
        FileOutputStream(outputFile).use { outputStream ->
            //outputStream.write()
            outputStream.write(iv)
            outputStream.write(salt)

            process(cipher, inputStream, outputStream)
        }
    }
}

fun decryptFile(password: String, inputFile: File, outputFile: File) {
    FileInputStream(inputFile).use { inputStream ->
        val iv = ByteArray(randomBytesSize)
        val salt = ByteArray(randomBytesSize)
        if (inputStream.read(iv) != randomBytesSize) throw Exception()
        if (inputStream.read(salt) != randomBytesSize) throw Exception()

        val key = deriveSecretKeyFromPasswordCached(salt, password)
        val cipher = getCipher(key, iv, Cipher.DECRYPT_MODE)

        FileOutputStream(outputFile).use { outputStream ->
            process(cipher, inputStream, outputStream)
        }
    }
}

fun decryptFileTo(password: String, inputFile: File): InputStream {
    return object : InputStream() {

        lateinit var cipher: Cipher
        lateinit var inputStream: InputStream

        val buffer = ByteArray(4096)
        var bufferFilledBytes: Int = 0
        var output: ByteArray = ByteArray(0)
        var outputRead: Int = 0
        var initialized = false
        var finished = false

        fun init() {
            inputStream = FileInputStream(inputFile)
            val iv = ByteArray(randomBytesSize)
            val salt = ByteArray(randomBytesSize)
            if (inputStream.read(iv) != randomBytesSize) throw Exception()
            if (inputStream.read(salt) != randomBytesSize) throw Exception()

            val key = deriveSecretKeyFromPasswordCached(salt, password)
            cipher = getCipher(key, iv, Cipher.DECRYPT_MODE)
            initialized = true
        }

        fun fetchBuffer(): Boolean {
            if (!initialized) init()
            if (finished) return false

            if (inputStream.read(buffer).also { bufferFilledBytes = it } > 0) {
                output = cipher.update(buffer, 0, bufferFilledBytes)!!
                bufferFilledBytes = output.size
                return true
            } else {
                output = cipher.doFinal()
                bufferFilledBytes = output.size
                finished = true
                return true
            }
        }

        override fun read(): Int {
            if (finished) return -1
            if (outputRead >= bufferFilledBytes) {
                fetchBuffer()
                outputRead = 0
            }
            val res = output[outputRead].toInt()
            outputRead++
            return res
        }

        override fun close() {
            if (initialized) inputStream.close()
        }
    }
}

fun decryptFileToFlow(password: String, inputFile: File) = flow<ByteArray> {
    FileInputStream(inputFile).use { inputStream ->
        val iv = ByteArray(randomBytesSize)
        val salt = ByteArray(randomBytesSize)
        if (inputStream.read(iv) != randomBytesSize) throw Exception()
        if (inputStream.read(salt) != randomBytesSize) throw Exception()

        val key = deriveSecretKeyFromPasswordCached(salt, password)
        val cipher = getCipher(key, iv, Cipher.DECRYPT_MODE)

        val buffer = ByteArray(4096)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            cipher.update(buffer, 0, bytesRead)?.let {
                emit(it)
            }
        }
        emit(cipher.doFinal())
    }
}


private fun getCipher(key: SecretKey, iv: ByteArray, mode: Int): Cipher =
    Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(mode, key, IvParameterSpec(iv)) }

private fun process(cipher: Cipher, inputStream: InputStream, outputStream: OutputStream) {
    val buffer = ByteArray(4096)
    var bytesRead: Int
    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        cipher.update(buffer, 0, bytesRead)?.let {
            outputStream.write(it)
        }
    }
    outputStream.write(cipher.doFinal())
}


fun main() {
    val password = "test"
    encryptFile(password, File("Test.txt"), File("Test.txt.encrypt"))
    decryptFile(password, File("Test.txt.encrypt"), File("Test2.txt"))
    if (!Files.readAllBytes(File("Test.txt").toPath()).contentEquals(Files.readAllBytes(File("Test2.txt").toPath()))) throw Exception("sdfhjk")
}