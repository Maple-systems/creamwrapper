
package com.mal.creamwrapper

import android.os.Build
import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AesDirectoryCrypto {

    private const val TAG = "AesDirectoryCrypto"
    private const val MANIFEST = ".aes_manifest.enc"

    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val TAG_SIZE_BITS = 128
    private const val KEY_SIZE_BITS = 256
    private const val PBKDF2_ITERATIONS = 120_000
    private const val BUFFER_SIZE = 1024 * 1024

    // 串行执行整个目录操作，避免同时加密和解密同一个目录
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val random = SecureRandom()

    private data class Entry(
        val original: String,
        val encrypted: String,
        val isDirectory: Boolean
    )

    /**
     * 异步加密。
     * 回调运行在后台线程，请勿直接在回调中更新 UI。
     */
    fun encrypt(
        directory: File,
        password: String,
        callback: (Result<Unit>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                encryptInternal(directory, password)
            }
            callback(result)
        }
    }

    /**
     * 异步解密。
     */
    fun decrypt(
        directory: File,
        password: String,
        callback: (Result<Unit>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                decryptInternal(directory, password)
            }
            callback(result)
        }
    }

    /**
     * 无回调版本，返回 Future，可通过 get() 等待结果。
     * 不要在主线程调用 Future.get()。
     */
    fun encrypt(directory: File, password: String): Future<Unit> =
        executor.submit<Unit> {
            encryptInternal(directory, password)
        }

    fun decrypt(directory: File, password: String): Future<Unit> =
        executor.submit<Unit> {
            decryptInternal(directory, password)
        }

    private fun validate(directory: File, password: String) {
        require(password.isNotEmpty()) {
            "密码不能为空"
        }

        require(directory.exists() && directory.isDirectory) {
            "目录不存在或不是目录：${directory.absolutePath}"
        }

        require(directory.canRead()) {
            "目录不可读：${directory.absolutePath}"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            require(Environment.isExternalStorageManager()) {
                "尚未获得管理所有文件权限"
            }
        }

        val root = directory.canonicalFile
        val parent = root.parentFile

        require(parent != null && parent.isDirectory) {
            "无法取得目标目录的父目录"
        }

        // 不允许把整个共享存储根目录当作目标目录。
        val externalRoot = Environment
            .getExternalStorageDirectory()
            .canonicalFile

        require(root != externalRoot) {
            "不能直接操作共享存储根目录，请选择其下的具体文件夹"
        }

        require(parent.canWrite()) {
            "目标目录的父目录不可写：${parent.absolutePath}"
        }

        // 实际尝试在父目录创建文件，检查临时目录所需权限。
        val probe = File(
            parent,
            ".aes_probe_${UUID.randomUUID()}"
        )

        try {
            FileOutputStream(probe).use { output ->
                output.write(0)
                output.flush()
            }
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "无法在目标目录的父目录创建临时文件：${parent.absolutePath}",
                e
            )
        } finally {
            if (probe.exists() && !probe.delete()) {
                Log.w(TAG, "无法删除权限测试文件：${probe.absolutePath}")
            }
        }
    }

    private fun encryptInternal(directory: File, password: String) {
        validate(directory, password)

        require(!File(directory, MANIFEST).exists()) {
            "检测到加密映射清单，目录可能已经加密，请使用 decrypt()"
        }

        val parent = directory.canonicalFile.parentFile!!
        val stage = File(
            parent,
            ".${directory.name}.aes-stage-${UUID.randomUUID()}"
        )

        require(stage.mkdir()) {
            "无法创建临时目录：${stage.absolutePath}"
        }

        var swapped = false

        try {
            val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
            val key = deriveKey(password, salt)
            val entries = mutableListOf<Entry>()

            val children = directory.listFiles()
                ?: throw IllegalStateException("无法读取目标目录内容")

            for (child in children) {
                require(child.name != MANIFEST) {
                    "源目录包含保留的映射清单文件"
                }

                encryptNode(
                    source = child,
                    stageRoot = stage,
                    encryptedParent = "",
                    originalParent = "",
                    key = key,
                    entries = entries
                )
            }

            val json = JSONObject()
            val array = JSONArray()

            for (entry in entries) {
                array.put(
                    JSONObject()
                        .put("original", entry.original)
                        .put("encrypted", entry.encrypted)
                        .put("directory", entry.isDirectory)
                )
            }

            json.put("version", 1)
            json.put("entries", array)

            val manifestBytes = json.toString().toByteArray(Charsets.UTF_8)
            val manifestFile = File(stage, MANIFEST)

            encryptManifest(manifestBytes, manifestFile, salt, key)

            swapContents(directory, stage)
            swapped = true

            Log.i(TAG, "目录加密完成：${directory.absolutePath}")
        } finally {
            // 只有在替换成功后 stage 才会被移动走。
            // 不删除可能用于恢复的备份目录。
            if (!swapped && stage.exists()) {
                stage.deleteRecursively()
            }
        }
    }

    private fun encryptNode(
        source: File,
        stageRoot: File,
        encryptedParent: String,
        originalParent: String,
        key: SecretKey,
        entries: MutableList<Entry>
    ) {
        require(source.exists()) {
            "源文件已不存在：${source.absolutePath}"
        }

        val originalRelative = join(originalParent, source.name)

        val encryptedName = (if (source.isDirectory) "d_" else "f_") +
                UUID.randomUUID().toString().replace("-", "")

        val encryptedRelative = join(encryptedParent, encryptedName)
        val destination = resolveSafe(stageRoot, encryptedRelative)

        if (source.isDirectory) {
            require(destination.mkdirs()) {
                "无法创建加密子目录：${destination.absolutePath}"
            }

            entries += Entry(
                original = originalRelative,
                encrypted = encryptedRelative,
                isDirectory = true
            )

            val children = source.listFiles()
                ?: throw IllegalStateException(
                    "无法读取子目录：${source.absolutePath}"
                )

            for (child in children) {
                encryptNode(
                    source = child,
                    stageRoot = stageRoot,
                    encryptedParent = encryptedRelative,
                    originalParent = originalRelative,
                    key = key,
                    entries = entries
                )
            }
        } else {
            require(source.isFile) {
                "不支持的文件类型：${source.absolutePath}"
            }

            destination.parentFile?.let {
                require(it.exists() || it.mkdirs()) {
                    "无法创建目标目录：${it.absolutePath}"
                }
            }

            encryptFile(source, destination, key)

            entries += Entry(
                original = originalRelative,
                encrypted = encryptedRelative,
                isDirectory = false
            )
        }
    }

    private fun decryptInternal(directory: File, password: String) {
        validate(directory, password)

        val manifestFile = File(directory, MANIFEST)

        require(manifestFile.isFile) {
            "未找到加密映射清单，目录可能尚未加密"
        }

        val (salt, manifestBytes) = decryptManifest(manifestFile, password)
        val key = deriveKey(password, salt)

        val json = JSONObject(String(manifestBytes, Charsets.UTF_8))
        require(json.optInt("version", -1) == 1) {
            "不支持的映射清单版本"
        }

        val array = json.getJSONArray("entries")
        val entries = mutableListOf<Entry>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)

            entries += Entry(
                original = item.getString("original"),
                encrypted = item.getString("encrypted"),
                isDirectory = item.getBoolean("directory")
            )
        }

        val stage = File(
            directory.canonicalFile.parentFile,
            ".${directory.name}.aes-stage-${UUID.randomUUID()}"
        )

        require(stage.mkdir()) {
            "无法创建解密临时目录：${stage.absolutePath}"
        }

        var swapped = false

        try {
            // 先创建目录，保证嵌套文件的父目录存在。
            entries.filter { it.isDirectory }
                .sortedBy { depth(it.original) }
                .forEach { entry ->
                    val target = resolveSafe(stage, entry.original)
                    require(target.mkdirs() || target.isDirectory) {
                        "无法恢复目录：${target.absolutePath}"
                    }
                }

            // 再解密普通文件。
            entries.filter { !it.isDirectory }
                .forEach { entry ->
                    val encryptedFile = resolveSafe(
                        directory,
                        entry.encrypted
                    )

                    require(encryptedFile.isFile) {
                        "加密文件缺失：${encryptedFile.absolutePath}"
                    }

                    val target = resolveSafe(stage, entry.original)

                    target.parentFile?.let { parent ->
                        require(parent.mkdirs() || parent.isDirectory) {
                            "无法创建恢复目录：${parent.absolutePath}"
                        }
                    }

                    decryptFile(encryptedFile, target, key)
                }

            swapContents(directory, stage)
            swapped = true

            Log.i(TAG, "目录解密完成：${directory.absolutePath}")
        } finally {
            if (!swapped && stage.exists()) {
                stage.deleteRecursively()
            }
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_SIZE_BITS
        )

        return try {
            val factory = SecretKeyFactory.getInstance(
                "PBKDF2WithHmacSHA1"
            )

            val bytes = factory.generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * 普通文件格式：
     * 12 字节随机 nonce + AES-GCM 密文及认证标签。
     */
    private fun encryptFile(
        source: File,
        destination: File,
        key: SecretKey
    ) {
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
            GCMParameterSpec(TAG_SIZE_BITS, nonce)
        )

        FileInputStream(source).use { input ->
            val raw = FileOutputStream(destination)
            try {
                raw.write(nonce)

                CipherOutputStream(raw, cipher).use { encryptedOutput ->
                    copyStream(input, encryptedOutput)
                }
            } catch (e: Exception) {
                destination.delete()
                throw e
            }
        }
    }

    private fun decryptFile(
        source: File,
        destination: File,
        key: SecretKey
    ) {
        FileInputStream(source).use { fileInput ->
            val input = DataInputStream(fileInput)
            val nonce = ByteArray(NONCE_SIZE)
            input.readFully(nonce)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(TAG_SIZE_BITS, nonce)
            )

            try {
                CipherInputStream(input, cipher).use { decryptedInput ->
                    FileOutputStream(destination).use { output ->
                        copyStream(decryptedInput, output)
                    }
                }
            } catch (e: Exception) {
                destination.delete()
                throw e
            }
        }
    }

    /**
     * 映射清单格式：
     * 16 字节 salt + 12 字节 nonce + AES-GCM 密文。
     */
    private fun encryptManifest(
        data: ByteArray,
        destination: File,
        salt: ByteArray,
        key: SecretKey
    ) {
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
            GCMParameterSpec(TAG_SIZE_BITS, nonce)
        )

        val encrypted = cipher.doFinal(data)

        FileOutputStream(destination).use { output ->
            output.write(salt)
            output.write(nonce)
            output.write(encrypted)
        }
    }

    private fun decryptManifest(
        source: File,
        password: String
    ): Pair<ByteArray, ByteArray> {
        val bytes = source.readBytes()

        require(bytes.size >= SALT_SIZE + NONCE_SIZE + 16) {
            "映射清单已损坏或格式不正确"
        }

        val salt = bytes.copyOfRange(0, SALT_SIZE)
        val nonce = bytes.copyOfRange(SALT_SIZE, SALT_SIZE + NONCE_SIZE)
        val ciphertext = bytes.copyOfRange(
            SALT_SIZE + NONCE_SIZE,
            bytes.size
        )

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_SIZE_BITS, nonce)
        )

        // 密码错误或清单被篡改时，GCM 认证会失败。
        return salt to cipher.doFinal(ciphertext)
    }

    /**
     * 把 stage 替换成目标目录。
     * 如果 stage 无法移动到目标路径，会尝试恢复原目录。
     */
    private fun swapContents(directory: File, stage: File) {
        val backup = File(
            directory.canonicalFile.parentFile,
            ".${directory.name}.aes-backup-${UUID.randomUUID()}"
        )

        require(directory.renameTo(backup)) {
            "无法备份原目录：${directory.absolutePath}"
        }

        if (!stage.renameTo(directory)) {
            val restored = backup.renameTo(directory)

            throw IllegalStateException(
                if (restored) {
                    "替换目录失败，已恢复原目录"
                } else {
                    "替换目录失败，且自动恢复失败。原数据仍可能位于：${backup.absolutePath}"
                }
            )
        }

        // 只有新目录已就位，才尝试删除旧目录。
        // 删除失败时保留备份，不影响新目录。
        if (!backup.deleteRecursively()) {
            Log.w(TAG, "旧目录备份未能完全删除：${backup.absolutePath}")
        }
    }

    private fun resolveSafe(root: File, relative: String): File {
        require(relative.isNotBlank()) {
            "映射清单包含空路径"
        }

        require(!relative.startsWith("/") && !relative.contains('\\')) {
            "映射清单包含非法路径"
        }

        val parts = relative.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." }) {
            "映射清单包含非法路径片段"
        }

        val rootPath = root.canonicalFile.path
        val result = File(root, relative.replace('/', File.separatorChar))
            .canonicalFile

        require(result.path.startsWith(rootPath + File.separator)) {
            "路径越界：$relative"
        }

        return result
    }

    private fun join(parent: String, name: String): String =
        if (parent.isEmpty()) name else "$parent/$name"

    private fun depth(path: String): Int = path.count { it == '/' }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)

        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) output.write(buffer, 0, count)
        }

        output.flush()
    }
}
