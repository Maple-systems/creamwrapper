
package com.mal.creamwrapper

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class AES(private val key: String) {

    private val random = SecureRandom()
    private val secretKey = SecretKeySpec(
        MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(StandardCharsets.UTF_8))
            .copyOf(16),
        "AES"
    )

    private val tagBits = 128
    private val nonceSize = 12
    private val bufferSize = 1024 * 1024

    private fun cipher(mode: Int, nonce: ByteArray): Cipher {
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                mode,
                secretKey,
                GCMParameterSpec(tagBits, nonce)
            )
        }
    }

    private fun encryptBytes(data: ByteArray): ByteArray {
        val nonce = ByteArray(nonceSize)
        random.nextBytes(nonce)
        val encrypted = cipher(Cipher.ENCRYPT_MODE, nonce)
            .doFinal(data)
        return nonce + encrypted
    }

    private fun decryptBytes(data: ByteArray): ByteArray {
        require(data.size > nonceSize + 16) {
            "Invalid encrypted data"
        }
        return cipher(
            Cipher.DECRYPT_MODE,
            data.copyOfRange(0, nonceSize)
        ).doFinal(data, nonceSize, data.size - nonceSize)
    }

    private fun encodeName(name: String): String {
        return Base64.encodeToString(
            encryptBytes(name.toByteArray(StandardCharsets.UTF_8)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private fun decodeName(name: String): String {
        return String(
            decryptBytes(
                Base64.decode(
                    name,
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
            ),
            StandardCharsets.UTF_8
        )
    }

    private fun randomName(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private fun encryptedName(name: String): String {
        return encodeName(name)
    }

    private fun decryptFile(source: File, target: File) {
        val temp = File(
            target.parentFile,
            ".tmp_${randomName()}"
        )
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(temp).use { output ->
                    val nonce = ByteArray(nonceSize)
                    if (input.read(nonce) != nonceSize) {
                        error("Invalid encrypted file")
                    }
                    val c = cipher(Cipher.DECRYPT_MODE, nonce)
                    val buffer = ByteArray(bufferSize)
                    var n: Int
                    while (input.read(buffer).also { n = it } != -1) {
                        output.write(c.update(buffer, 0, n))
                    }
                    output.write(c.doFinal())
                    output.fd.sync()
                }
            }
            if (!temp.renameTo(target)) {
                error("Unable to rename temporary file")
            }
        } finally {
            temp.delete()
        }
    }

    private fun encryptFile(source: File, target: File) {
        val temp = File(
            target.parentFile,
            ".tmp_${randomName()}"
        )
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(temp).use { output ->
                    val nonce = ByteArray(nonceSize)
                    random.nextBytes(nonce)
                    output.write(nonce)
                    val c = cipher(Cipher.ENCRYPT_MODE, nonce)
                    val buffer = ByteArray(bufferSize)
                    var n: Int
                    while (input.read(buffer).also { n = it } != -1) {
                        output.write(c.update(buffer, 0, n))
                    }
                    output.write(c.doFinal())
                    output.fd.sync()
                }
            }
            if (!temp.renameTo(target)) {
                error("Unable to rename temporary file")
            }
        } finally {
            temp.delete()
        }
    }

    private fun encryptDirectory(dir: File) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.name.startsWith(".tmp_")) continue
            if (child.isDirectory) {
                encryptDirectory(child)
                val newDir = File(
                    dir,
                    encryptedName(child.name)
                )
                if (!child.renameTo(newDir)) {
                    error("Unable to rename directory")
                }
            } else {
                val newFile = File(
                    dir,
                    encryptedName(child.name)
                )
                encryptFile(child, newFile)
                if (!child.delete()) {
                    error("Unable to delete original file")
                }
            }
        }
    }

    private fun decryptDirectory(dir: File) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.name.startsWith(".tmp_")) continue
            if (child.isDirectory) {
                decryptDirectory(child)
                val original = File(
                    dir,
                    decodeName(child.name)
                )
                if (!child.renameTo(original)) {
                    error("Unable to rename directory")
                }
            } else {
                val original = File(
                    dir,
                    decodeName(child.name)
                )
                decryptFile(child, original)
                if (!child.delete()) {
                    error("Unable to delete encrypted file")
                }
            }
        }
    }

    fun encrypt(directory: File) {
        require(directory.isDirectory) {
            "Directory does not exist"
        }
        encryptDirectory(directory)
    }

    fun decrypt(directory: File) {
        require(directory.isDirectory) {
            "Directory does not exist"
        }
        decryptDirectory(directory)
    }
}