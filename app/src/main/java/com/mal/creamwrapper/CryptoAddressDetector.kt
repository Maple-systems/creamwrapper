package com.mal.creamwrapper
import android.util.Base64
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.digests.KeccakDigest
import org.bouncycastle.crypto.digests.SHA512tDigest
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * Offline crypto-address format detector for Android API 23+.
 *
 * This validates address encodings/checksums where the format defines one. It does not
 * query a node/explorer, prove that an account exists, or identify an EVM chain from
 * its 20-byte address (the same address can be used on many EVM networks).
 */
object CryptoAddressDetector {

    enum class Validation {
        /** A supported checksum/encoding checksum passed. This is not an on-chain existence check. */
        CHECKSUM_VERIFIED,
        /** The shape looks like an address, but the format has no checksum or only shape was checked. */
        FORMAT_ONLY,
        /** A recognizable address shape had a failed checksum/encoding check. */
        CHECKSUM_FAILED,
        /** Not recognized by the formats implemented here. */
        NOT_RECOGNIZED
    }

    data class Result(
        val input: String,
        val isAddress: Boolean,
        val chain: String,
        val network: String? = null,
        val addressType: String? = null,
        val validation: Validation,
        val checksumValid: Boolean? = null,
        val possibleChains: List<String> = emptyList(),
        val details: Map<String, String> = emptyMap(),
        val message: String = ""
    )

    private const val BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val RIPPLE_BASE58 = "rpshnaf39wBUDNEGHJKLM4PQRST7VWXYZ2bcdeCg65jkm8oFqi1tuvAxyz"
    private const val BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val BECH32M_CONST = 0x2bc830a3L

    /** Main entry point. */
    fun analyze(input: String): Result {
        val s = input.trim()
        if (s.isEmpty() || s.length > 256) return unknown(s)

        detectCashAddr(s)?.let { return it }
        detectTonFriendly(s)?.let { return it }
        detectEthereumOrMove(s)?.let { return it }
        detectTronHex(s)?.let { return it }
        detectTonRaw(s)?.let { return it }
        detectBech32Address(s)?.let { return it }
        detectStellar(s)?.let { return it }
        detectAlgorand(s)?.let { return it }
        detectMoneroShape(s)?.let { return it }
        detectXrpXAddress(s)?.let { return it }
        detectXrpClassic(s)?.let { return it }

        val payload = decodeBase58Check(s)
        if (payload != null) {
            detectBase58CheckAddress(s, payload)?.let { return it }
        }

        val base58Decoded = decodeBase58(s)
        if (base58Decoded != null) {
            detectSs58(s, base58Decoded)?.let { return it }
            // Solana addresses are base58 encodings of 32-byte public keys and have no
            // address-level checksum. A matching byte length is therefore only a hint.
            if (base58Decoded.size == 32 && s.length in 32..44) {
                return Result(
                    input = s,
                    isAddress = true,
                    chain = "Solana / 32-byte Base58 公钥格式",
                    network = "通常为 Solana 主网；网络不能由地址字符串确认",
                    addressType = "32-byte public key",
                    validation = Validation.FORMAT_ONLY,
                    checksumValid = null,
                    possibleChains = listOf("Solana", "其他使用 32-byte Base58 公钥的链/系统"),
                    message = "长度与 Solana 公钥地址相符，但该格式没有独立校验和；这里只能判定格式疑似匹配。"
                )
            }
        }

        detectTezosShape(s)?.let { return it }
        return unknown(s)
    }

    private fun unknown(s: String) = Result(
        input = s,
        isAddress = false,
        chain = "未知",
        validation = Validation.NOT_RECOGNIZED,
        checksumValid = null,
        message = "不匹配当前实现支持的地址格式；也可能是尚未支持的链、域名式账户名或带附加信息的支付地址。"
    )

    private fun result(
        s: String,
        chain: String,
        network: String? = null,
        type: String? = null,
        validation: Validation = Validation.CHECKSUM_VERIFIED,
        checksumValid: Boolean? = validation == Validation.CHECKSUM_VERIFIED,
        possible: List<String> = emptyList(),
        details: Map<String, String> = emptyMap(),
        message: String = ""
    ) = Result(
        input = s,
        isAddress = validation == Validation.CHECKSUM_VERIFIED || validation == Validation.FORMAT_ONLY,
        chain = chain,
        network = network,
        addressType = type,
        validation = validation,
        checksumValid = checksumValid,
        possibleChains = possible,
        details = details,
        message = message
    )

    // -------------------------------------------------------------------------
    // Ethereum and EVM-compatible addresses
    // -------------------------------------------------------------------------

    private fun detectEthereumOrMove(s: String): Result? {
        if (!s.startsWith("0x", ignoreCase = true)) return null
        val hex = s.substring(2)
        if (!hex.matches(Regex("[0-9a-fA-F]{40}"))) {
            if (hex.matches(Regex("[0-9a-fA-F]{1,64}"))) {
                return result(
                    s = s,
                    chain = "Move / Starknet 等十六进制地址（链不确定）",
                    type = "0x + 1–64 hex chars",
                    validation = Validation.FORMAT_ONLY,
                    checksumValid = null,
                    possible = listOf("Aptos", "Sui", "Starknet", "其他十六进制地址体系"),
                    message = "这类地址缺少可统一识别的链标记；地址长度及规范化方式也因链而异。"
                )
            }
            return null
        }

        val lower = hex.lowercase(Locale.ROOT)
        val isMixedCase = hex != hex.lowercase(Locale.ROOT) && hex != hex.uppercase(Locale.ROOT)
        if (!isMixedCase) {
            return result(
                s = s,
                chain = "Ethereum / EVM 兼容链",
                type = "0x + 20-byte account address",
                validation = Validation.FORMAT_ONLY,
                checksumValid = null,
                possible = evmChains,
                message = "格式匹配。全小写/全大写形式没有 EIP-55 大小写校验信息；不能从地址本身确定具体 EVM 网络。"
            )
        }

        val hash = keccak256(lower.toByteArray(StandardCharsets.US_ASCII)).toHex()
        val expected = buildString(40) {
            for (i in lower.indices) {
                val c = lower[i]
                append(if (c in 'a'..'f' && hash[i].digitToInt(16) >= 8) c.uppercaseChar() else c)
            }
        }
        val valid = expected == hex
        return result(
            s = s,
            chain = "Ethereum / EVM 兼容链",
            type = "0x + 20-byte account address (EIP-55 mixed-case)",
            validation = if (valid) Validation.CHECKSUM_VERIFIED else Validation.CHECKSUM_FAILED,
            checksumValid = valid,
            possible = evmChains,
            message = if (valid) "EIP-55 大小写校验通过；依然无法仅凭地址区分 Ethereum、BSC、Polygon、Base 等网络。"
            else "地址格式像 EVM 地址，但混合大小写未通过 EIP-55 校验。"
        )
    }

    private val evmChains = listOf(
        "Ethereum", "BNB Smart Chain", "Polygon", "Arbitrum", "Optimism", "Base",
        "Avalanche C-Chain", "Gnosis Chain", "Linea", "zkSync Era", "Scroll", "Mantle", "Celo", "Cronos"
    )

    private fun detectTronHex(s: String): Result? {
        if (!s.matches(Regex("(?i)^41[0-9a-f]{40}$"))) return null
        return result(
            s = s,
            chain = "TRON",
            network = "通常为主网；该十六进制形式不编码网络信息",
            type = "TRON hex address (41 + 20 bytes)",
            validation = Validation.FORMAT_ONLY,
            checksumValid = null,
            message = "TRON 的 41 开头十六进制地址格式匹配，但此表示形式本身没有 Base58Check 校验和。"
        )
    }

    // -------------------------------------------------------------------------
    // Bitcoin-style Base58Check families
    // -------------------------------------------------------------------------

    private fun detectBase58CheckAddress(s: String, p: ByteArray): Result? {
        val first = p.firstOrNull()?.toInt()?.and(0xff) ?: return null
        val size = p.size

        if (size == 21) {
            return when (first) {
                0x00 -> result(
                    s, "Bitcoin / Bitcoin Cash 旧式地址（存在歧义）", "主网", "P2PKH Base58Check",
                    possible = listOf("Bitcoin", "Bitcoin Cash (legacy address)"),
                    message = "校验和通过，但 Bitcoin 与 Bitcoin Cash 的旧式地址可使用相同编码，不能仅凭字符串唯一判断。"
                )
                0x05 -> result(
                    s, "Bitcoin / Bitcoin Cash / Litecoin 旧式地址（存在歧义）", "主网", "P2SH Base58Check",
                    possible = listOf("Bitcoin", "Bitcoin Cash (legacy address)", "Litecoin (legacy P2SH prefix)"),
                    message = "该版本字节被多个 UTXO 链复用，已验证编码校验和，但链归属不唯一。"
                )
                0x30 -> result(s, "Litecoin", "主网", "P2PKH Base58Check")
                0x32 -> result(s, "Litecoin", "主网", "P2SH Base58Check")
                0x1e -> result(
                    s, "Dogecoin / DigiByte 等前缀重叠链", "主网", "P2PKH Base58Check",
                    possible = listOf("Dogecoin", "DigiByte 或其他复用该版本字节的链"),
                    message = "校验和通过，但此版本字节并非全球唯一分配给某一条链。"
                )
                0x16 -> result(s, "Dogecoin", "主网", "P2SH Base58Check")
                0x4c -> result(s, "Dash", "主网", "P2PKH Base58Check")
                0x10 -> result(s, "Dash", "主网", "P2SH Base58Check")
                0x6f -> result(
                    s, "Bitcoin / Litecoin 测试网地址（存在歧义）", "测试网", "P2PKH Base58Check",
                    possible = listOf("Bitcoin testnet", "Litecoin testnet"),
                    message = "测试网版本字节可能由多条链复用。"
                )
                0xc4 -> result(
                    s, "UTXO 链测试网地址（存在歧义）", "测试网", "P2SH Base58Check",
                    possible = listOf("Bitcoin testnet", "Litecoin testnet", "其他复用 0xc4 的链"),
                    message = "测试网版本字节可能由多条链复用。"
                )
                0x71 -> result(s, "Dogecoin", "测试网", "P2PKH Base58Check")
                0x8c -> result(s, "Dash", "测试网", "P2PKH Base58Check")
                0x13 -> result(s, "Dash", "测试网", "P2SH Base58Check")
                0x41 -> if (size == 21 && s.startsWith("T")) {
                    result(s, "TRON", "主网", "Base58Check (0x41 + 20-byte account)")
                } else null
                else -> null
            }
        }

        if (size == 22) {
            val second = p[1].toInt() and 0xff
            val v2 = (first shl 8) or second
            val zcash = when (v2) {
                0x1cb8 -> "P2PKH"
                0x1cbd -> "P2SH"
                0x1d25 -> "P2PKH"
                0x1cba -> "P2SH"
                else -> null
            }
            if (zcash != null) {
                val network = if (v2 == 0x1d25 || v2 == 0x1cba) "测试网" else "主网"
                return result(s, "Zcash", network, "$zcash transparent Base58Check")
            }
        }

        if (size == 21 && first == 0x41 && s.startsWith("T")) {
            return result(s, "TRON", "主网", "Base58Check (0x41 + 20-byte account)")
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Bech32 / Bech32m families: Bitcoin, Litecoin, Cardano, Cosmos, BNB Beacon
    // -------------------------------------------------------------------------

    private data class Bech32Decoded(val hrp: String, val data: IntArray, val encoding: String)

    private fun detectBech32Address(original: String): Result? {
        var s = original
        var avaxKind: String? = null
        if (s.startsWith("X-avax1", true)) {
            avaxKind = "X-Chain"
            s = s.substring(2)
        } else if (s.startsWith("P-avax1", true)) {
            avaxKind = "P-Chain"
            s = s.substring(2)
        }

        val b = decodeBech32(s) ?: return null
        val hrp = b.hrp

        if (hrp == "bc" || hrp == "tb" || hrp == "bcrt" || hrp == "ltc") {
            val mainChain = if (hrp == "ltc") "Litecoin" else "Bitcoin"
            val network = when (hrp) {
                "bc" -> "主网"
                "tb" -> "测试网 / Signet"
                "bcrt" -> "Regtest 本地测试网"
                "ltc" -> "主网"
                else -> "未知网络"
            }
            if (s.length > 90 || b.data.isEmpty()) {
                return result(s = original, chain = mainChain, network = network, type = "SegWit Bech32/Bech32m", validation = Validation.CHECKSUM_FAILED, checksumValid = false, message = "编码校验和通过，但地址长度/数据区不符合 SegWit 规则。")
            }
            val version = b.data[0]
            val program = convertBits(b.data.copyOfRange(1, b.data.size), 5, 8, false)
            val validProgram = program != null && program.size in 2..40 &&
                (version != 0 || program.size == 20 || program.size == 32) &&
                ((version == 0 && b.encoding == "bech32") || (version > 0 && b.encoding == "bech32m"))
            if (version !in 0..16 || !validProgram) {
                return result(s = original, chain = mainChain, network = network, type = "SegWit Bech32/Bech32m", validation = Validation.CHECKSUM_FAILED, checksumValid = false, message = "校验和存在，但 witness version、程序长度或 Bech32/Bech32m 规则不符合要求。")
            }
            val p = program!!
            val type = when {
                version == 0 && p.size == 20 -> "Witness v0 P2WPKH"
                version == 0 && p.size == 32 -> "Witness v0 P2WSH"
                version == 1 && p.size == 32 -> "Witness v1 Taproot"
                else -> "Witness v$version"
            }
            return result(original, mainChain, network, type, details = mapOf("hrp" to hrp, "encoding" to b.encoding, "witnessVersion" to version.toString(), "programBytes" to p.size.toString()))
        }

        if (avaxKind != null || hrp == "avax") {
            val network = if (hrp == "avax") "通常为 Avalanche 主网；需结合前缀确认链" else "Avalanche 主网地址空间"
            val kind = avaxKind ?: "X/P-Chain 未明确"
            return result(original, "Avalanche", network, "$kind Bech32", details = mapOf("hrp" to hrp, "encoding" to b.encoding), message = "Bech32 校验和通过；只有带 X-/P- 前缀的形式才能从字符串进一步提示链类型。")
        }

        val cardano = when (hrp) {
            "addr" -> "Payment address"
            "addr_test" -> "Testnet payment address"
            "stake" -> "Stake/reward address"
            "stake_test" -> "Testnet stake/reward address"
            else -> null
        }
        if (cardano != null) {
            return result(original, "Cardano (ADA)", if (hrp.endsWith("_test")) "测试网" else "主网", cardano, details = mapOf("hrp" to hrp, "encoding" to b.encoding), message = "Bech32 校验和通过；这里不访问链上，也未验证该账户是否已存在。")
        }

        val cosmos = cosmosPrefixes[hrp]
        if (cosmos != null) {
            return result(original, cosmos.first, cosmos.second, "Bech32 account/role address", details = mapOf("hrp" to hrp, "encoding" to b.encoding), message = "Bech32 校验和通过。前缀通常表示生态/账户角色，但不能由此证明账户已在链上存在。")
        }

        if (hrp == "bnb") {
            return result(original, "BNB Beacon Chain（旧 Binance Chain）", "主网常见", "Bech32", details = mapOf("hrp" to hrp))
        }
        if (hrp == "dgb") {
            return result(original, "DigiByte", "主网常见", "Bech32", details = mapOf("hrp" to hrp))
        }
        if (hrp == "erd") {
            return result(original, "MultiversX (EGLD)", "网络需另行确认", "Bech32", details = mapOf("hrp" to hrp))
        }
        return null
    }

    private val cosmosPrefixes = mapOf(
        "cosmos" to ("Cosmos Hub" to "主网常见"),
        "cosmosvaloper" to ("Cosmos Hub" to "验证者运营账户"),
        "cosmosvalcons" to ("Cosmos Hub" to "验证者共识账户"),
        "osmo" to ("Osmosis" to "主网常见"),
        "terra" to ("Terra" to "主网常见"),
        "terra2" to ("Terra 2.0" to "主网常见"),
        "inj" to ("Injective" to "主网常见"),
        "sei" to ("Sei" to "主网常见"),
        "celestia" to ("Celestia" to "主网常见"),
        "evmos" to ("Evmos" to "主网常见"),
        "juno" to ("Juno" to "主网常见"),
        "secret" to ("Secret Network" to "主网常见"),
        "akash" to ("Akash Network" to "主网常见"),
        "kujira" to ("Kujira" to "主网常见"),
        "stride" to ("Stride" to "主网常见"),
        "neutron" to ("Neutron" to "主网常见"),
        "noble" to ("Noble" to "主网常见"),
        "dym" to ("Dymension" to "主网常见"),
        "persistence" to ("Persistence" to "主网常见"),
        "umee" to ("Umee" to "主网常见"),
        "regen" to ("Regen Network" to "主网常见"),
        "fetch" to ("Fetch.ai / Fetch Network" to "主网常见"),
        "stars" to ("Stargaze" to "主网常见"),
        "agoric" to ("Agoric" to "主网常见"),
        "sommelier" to ("Sommelier" to "主网常见"),
        "band" to ("Band Protocol" to "主网常见"),
        "kava" to ("Kava" to "主网常见"),
        "cro" to ("Cronos / Crypto.org 生态" to "需结合具体网络确认"),
        "sif" to ("Sifchain" to "主网常见"),
        "thor" to ("THORChain" to "主网常见")
    )

    private fun decodeBech32(s: String): Bech32Decoded? {
        if (s.length !in 8..200) return null
        if (s.any { it.code !in 33..126 }) return null
        if (s != s.lowercase(Locale.ROOT) && s != s.uppercase(Locale.ROOT)) return null
        val normalized = s.lowercase(Locale.ROOT)
        val pos = normalized.lastIndexOf('1')
        if (pos < 1 || pos + 7 > normalized.length) return null
        val hrp = normalized.substring(0, pos)
        val values = IntArray(normalized.length - pos - 1)
        for (i in values.indices) {
            val idx = BECH32_CHARSET.indexOf(normalized[pos + 1 + i])
            if (idx < 0) return null
            values[i] = idx
        }
        val check = bech32Polymod(hrpExpand(hrp) + values.toList())
        val encoding = when (check) {
            1L -> "bech32"
            BECH32M_CONST -> "bech32m"
            else -> return null
        }
        return Bech32Decoded(hrp, values.copyOfRange(0, values.size - 6), encoding)
    }

    private fun bech32Polymod(values: List<Int>): Long {
        val generators = longArrayOf(0x3b6a57b2L, 0x26508e6dL, 0x1ea119faL, 0x3d4233ddL, 0x2a1462b3L)
        var chk = 1L
        for (v in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffffL) shl 5) xor v.toLong()
            for (i in 0..4) if (((top ushr i) and 1L) != 0L) chk = chk xor generators[i]
        }
        return chk
    }

    private fun hrpExpand(hrp: String): List<Int> = buildList {
        hrp.forEach { add(it.code ushr 5) }
        add(0)
        hrp.forEach { add(it.code and 31) }
    }

    private fun convertBits(data: IntArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray? {
        var acc = 0
        var bits = 0
        val maxv = (1 shl toBits) - 1
        val maxAcc = (1 shl (fromBits + toBits - 1)) - 1
        val out = ArrayList<Byte>()
        for (value in data) {
            if (value < 0 || (value ushr fromBits) != 0) return null
            acc = ((acc shl fromBits) or value) and maxAcc
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                out.add(((acc ushr bits) and maxv).toByte())
            }
        }
        if (pad) {
            if (bits > 0) out.add(((acc shl (toBits - bits)) and maxv).toByte())
        } else {
            if (bits >= fromBits) return null
            if (((acc shl (toBits - bits)) and maxv) != 0) return null
        }
        return out.toByteArray()
    }

    // -------------------------------------------------------------------------
    // Bitcoin Cash CashAddr
    // -------------------------------------------------------------------------

    private fun detectCashAddr(s: String): Result? {
        val colon = s.indexOf(':')
        val suppliedPrefix = if (colon >= 0) s.substring(0, colon) else null
        val encoded = if (colon >= 0) s.substring(colon + 1) else s
        if (encoded.isEmpty() || encoded.any { it.lowercaseChar() !in BECH32_CHARSET }) return null
        if (s != s.lowercase(Locale.ROOT) && s != s.uppercase(Locale.ROOT)) {
            val p = suppliedPrefix?.lowercase(Locale.ROOT)
            if (p in setOf("bitcoincash", "bchtest", "bchreg")) {
                return result(s, "Bitcoin Cash", null, "CashAddr", Validation.CHECKSUM_FAILED, false, message = "CashAddr 不允许大小写混合。")
            }
            return null
        }
        val candidatePrefixes = if (suppliedPrefix != null) listOf(suppliedPrefix.lowercase(Locale.ROOT))
            else listOf("bitcoincash", "bchtest", "bchreg")
        for (prefix in candidatePrefixes) {
            if (prefix !in setOf("bitcoincash", "bchtest", "bchreg")) continue
            val values = encoded.lowercase(Locale.ROOT).map { BECH32_CHARSET.indexOf(it) }
            if (values.any { it < 0 } || values.size < 8) continue
            val polymodInput = prefix.map { it.code and 31 } + listOf(0) + values
            if (cashPolymod(polymodInput) != 0L) {
                if (suppliedPrefix != null) {
                    return result(s, "Bitcoin Cash", cashNetwork(prefix), "CashAddr", Validation.CHECKSUM_FAILED, false, message = "CashAddr 校验和不正确。")
                }
                continue
            }
            val dataBytes = convertBits(values.dropLast(8).toIntArray(), 5, 8, false) ?: continue
            if (dataBytes.size < 2) continue
            val version = dataBytes[0].toInt() and 0xff
            val hashBytes = dataBytes.size - 1
            val expectedHashSize = intArrayOf(20, 24, 28, 32, 40, 48, 56, 64)[version and 7]
            if (version and 0x80 != 0 || hashBytes != expectedHashSize) {
                return result(s, "Bitcoin Cash", cashNetwork(prefix), "CashAddr", Validation.CHECKSUM_FAILED, false, message = "校验和通过，但 CashAddr version/hash 长度不符合规范。")
            }
            val addressType = when ((version ushr 3) and 0x0f) {
                0 -> "P2PKH CashAddr"
                1 -> "P2SH CashAddr"
                else -> "CashAddr type ${((version ushr 3) and 0x0f)}"
            }
            return result(s, "Bitcoin Cash", cashNetwork(prefix), addressType, details = mapOf("prefix" to prefix, "hashBytes" to hashBytes.toString()))
        }
        return null
    }

    private fun cashNetwork(prefix: String) = when (prefix) {
        "bitcoincash" -> "主网"
        "bchtest" -> "测试网"
        "bchreg" -> "Regtest"
        else -> "未知网络"
    }

    private fun cashPolymod(values: List<Int>): Long {
        val generators = longArrayOf(0x98f2bc8e61L, 0x79b76d99e2L, 0xf33e5fb3c4L, 0xae2eabe2a8L, 0x1e4f43e470L)
        var c = 1L
        for (d in values) {
            val c0 = c ushr 35
            c = ((c and 0x07ffffffffL) shl 5) xor d.toLong()
            for (i in 0..4) if (((c0 ushr i) and 1L) != 0L) c = c xor generators[i]
        }
        return c xor 1L
    }

    // -------------------------------------------------------------------------
    // TON addresses
    // -------------------------------------------------------------------------

    private fun detectTonFriendly(s: String): Result? {
        if (s.length != 48 || !s.firstOrNull().let { it == 'E' || it == 'U' || it == 'k' || it == '0' }) return null
        val raw = runCatching { Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP) }.getOrNull()
            ?: runCatching { Base64.decode(s, Base64.NO_WRAP) }.getOrNull()
            ?: return null
        if (raw.size != 36) return null
        val flags = raw[0].toInt() and 0xff
        val tag = flags and 0x7f
        if (tag != 0x11 && tag != 0x51) return null
        val actual = ((raw[34].toInt() and 0xff) shl 8) or (raw[35].toInt() and 0xff)
        val expected = crc16Xmodem(raw.copyOfRange(0, 34))
        if (actual != expected) {
            return result(s, "TON", null, "Friendly Base64 address", Validation.CHECKSUM_FAILED, false, message = "TON 地址形状匹配，但 CRC16 校验失败。")
        }
        val testnet = flags and 0x80 != 0
        val wc = raw[1].toInt()
        return result(
            s, "TON", if (testnet) "Testnet-only" else "主网格式",
            if (tag == 0x11) "Bounceable friendly address" else "Non-bounceable friendly address",
            details = mapOf("workchain" to wc.toString(), "testnetOnlyFlag" to testnet.toString(), "flags" to "0x${flags.toString(16)}")
        )
    }

    private fun detectTonRaw(s: String): Result? {
        val match = Regex("^(-?\\d+):([0-9a-fA-F]{64})$").matchEntire(s) ?: return null
        return result(
            s, "TON", "网络未编码在 raw 格式中", "Raw workchain:account-id",
            Validation.FORMAT_ONLY, null,
            details = mapOf("workchain" to match.groupValues[1], "accountIdHex" to match.groupValues[2].lowercase(Locale.ROOT)),
            message = "TON raw 地址没有校验和，只验证了 workchain:256-bit account ID 的形状。"
        )
    }

    // -------------------------------------------------------------------------
    // Stellar and Algorand
    // -------------------------------------------------------------------------

    private fun detectStellar(s: String): Result? {
        if (!s.startsWith("G") || s.length != 56) return null
        val raw = decodeBase32(s, BASE32) ?: return null
        if (raw.size != 35 || (raw[0].toInt() and 0xff) != (6 shl 3)) {
            return result(s, "Stellar", null, "StrKey account ID", Validation.CHECKSUM_FAILED, false, message = "形状像 Stellar G 地址，但版本/长度不正确。")
        }
        val expected = crc16Xmodem(raw.copyOfRange(0, 33))
        val actual = (raw[33].toInt() and 0xff) or ((raw[34].toInt() and 0xff) shl 8)
        return if (actual == expected) {
            result(s, "Stellar (XLM)", "网络需另行确认", "StrKey Ed25519 account ID")
        } else {
            result(s, "Stellar (XLM)", null, "StrKey account ID", Validation.CHECKSUM_FAILED, false, message = "Stellar StrKey CRC16 校验失败。")
        }
    }

    private fun detectAlgorand(s: String): Result? {
        if (s.length != 58 || s.any { it !in BASE32 }) return null
        val raw = decodeBase32(s, BASE32) ?: return null
        if (raw.size != 36) return null
        val publicKey = raw.copyOfRange(0, 32)
        val checksum = raw.copyOfRange(32, 36)
        val digest = SHA512tDigest(256)
        val hash = ByteArray(32)
        digest.update(publicKey, 0, publicKey.size)
        digest.doFinal(hash, 0)
        val expected = hash.copyOfRange(28, 32)
        return if (checksum.contentEquals(expected)) {
            result(s, "Algorand (ALGO)", "网络需另行确认", "Base32 public key + SHA-512/256 checksum")
        } else {
            result(s, "Algorand", null, "58-character Base32", Validation.CHECKSUM_FAILED, false, message = "Algorand 地址形状匹配，但 SHA-512/256 校验失败。")
        }
    }

    private fun decodeBase32(s: String, alphabet: String): ByteArray? {
        var buffer = 0
        var bits = 0
        val out = ArrayList<Byte>()
        for (ch in s) {
            val v = alphabet.indexOf(ch)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer ushr bits) and 0xff).toByte())
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0 && buffer != 0) return null
        return out.toByteArray()
    }

    // -------------------------------------------------------------------------
    // Monero: custom block Base58 encoding is not fully decoded in this detector.
    // -------------------------------------------------------------------------

    private fun detectMoneroShape(s: String): Result? {
        val type = when {
            s.length == 95 && s.startsWith("4") -> "Standard address candidate"
            s.length == 95 && s.startsWith("8") -> "Subaddress candidate"
            s.length == 106 && s.startsWith("4") -> "Integrated address candidate (payment ID)"
            s.length == 95 && s.startsWith("9") -> "Testnet address candidate"
            s.length == 95 && s.startsWith("5") -> "Stagenet address candidate"
            else -> return null
        }
        if (s.any { BASE58.indexOf(it) < 0 }) return null
        return result(
            s, "Monero (XMR)", "由前缀推测；需完整 Monero 解码确认", type,
            Validation.FORMAT_ONLY, null,
            message = "这里只检查了常见长度、起始字符和 Base58 字符集，没有执行 Monero 分块 Base58 解码或 Keccak 校验。"
        )
    }

    // -------------------------------------------------------------------------
    // XRP Ledger classic address (Ripple uses its own Base58 alphabet)
    // -------------------------------------------------------------------------

    private fun detectXrpXAddress(s: String): Result? {
        if (!(s.startsWith("X") || s.startsWith("T")) || s.length !in 40..60) return null
        val raw = decodeBase58(s, RIPPLE_BASE58) ?: return null
        // 2-byte prefix + 20-byte AccountID + flags byte + 8-byte tag field + 4-byte checksum.
        if (raw.size != 35) return null
        val payload = raw.copyOfRange(0, raw.size - 4)
        val expected = doubleSha256(payload).copyOfRange(0, 4)
        if (!raw.copyOfRange(raw.size - 4, raw.size).contentEquals(expected)) {
            return result(s, "XRP Ledger", null, "X-address", Validation.CHECKSUM_FAILED, false, message = "X-address 形状匹配，但 Base58Check 校验失败。")
        }
        val prefix0 = payload[0].toInt() and 0xff
        val prefix1 = payload[1].toInt() and 0xff
        val network = when {
            prefix0 == 0x05 && prefix1 == 0x44 -> "主网格式"
            prefix0 == 0x04 && prefix1 == 0x93 -> "测试网格式"
            else -> return null
        }
        val flag = payload[22].toInt() and 0xff
        val tagBytes = payload.copyOfRange(23, 31)
        val tagDescription = when (flag) {
            0 -> {
                if (tagBytes.any { it.toInt() != 0 }) return result(s, "XRP Ledger", network, "X-address", Validation.CHECKSUM_FAILED, false, message = "NO_TAG 标志对应的 tag 字段必须全为 0。")
                "无 destination tag"
            }
            1 -> {
                if (tagBytes.copyOfRange(4, 8).any { it.toInt() != 0 }) return result(s, "XRP Ledger", network, "X-address", Validation.CHECKSUM_FAILED, false, message = "TAG_32 的高 4 字节保留区必须为 0。")
                val tag = (tagBytes[0].toLong() and 0xff) or
                    ((tagBytes[1].toLong() and 0xff) shl 8) or
                    ((tagBytes[2].toLong() and 0xff) shl 16) or
                    ((tagBytes[3].toLong() and 0xff) shl 24)
                "destination tag = $tag"
            }
            2 -> "保留的 TAG_64 格式"
            else -> return result(s, "XRP Ledger", network, "X-address", Validation.CHECKSUM_FAILED, false, message = "X-address 使用了未定义 flags 值。")
        }
        return result(
            s, "XRP Ledger (XRP)", network, "X-address (account + destination tag)",
            details = mapOf("tagInfo" to tagDescription, "classicAccountIdHex" to payload.copyOfRange(2, 22).toHex()),
            message = "X-address 将账户和 destination tag 包装为一个地址；实际转账仍需确保交易系统正确处理 tag。"
        )
    }

    private fun detectXrpClassic(s: String): Result? {
        if (!s.startsWith("r") || s.length !in 25..35) return null
        val raw = decodeBase58(s, RIPPLE_BASE58) ?: return null
        if (raw.size != 25) return null
        val payload = raw.copyOfRange(0, raw.size - 4)
        val expected = doubleSha256(payload).copyOfRange(0, 4)
        val checksum = raw.copyOfRange(raw.size - 4, raw.size)
        if (!checksum.contentEquals(expected)) {
            return result(s, "XRP Ledger", null, "Classic address", Validation.CHECKSUM_FAILED, false, message = "XRP classic address 的 Base58 校验和失败。")
        }
        if (payload.size != 21 || payload[0].toInt() != 0) return null
        return result(s, "XRP Ledger (XRP)", "网络通常为 XRPL 主网；测试网络也采用类似 classic 地址形式", "Classic account address")
    }

    // -------------------------------------------------------------------------
    // Polkadot/Substrate SS58
    // -------------------------------------------------------------------------

    private fun detectSs58(s: String, raw: ByteArray): Result? {
        // Most common public-key SS58 form: 1-byte network prefix + 32-byte public key + 2-byte checksum.
        if (raw.size != 35) return null
        val format = raw[0].toInt() and 0xff
        if (format > 63) return null
        val body = raw.copyOfRange(0, 33)
        val input = "SS58PRE".toByteArray(StandardCharsets.US_ASCII) + body
        val digest = Blake2bDigest(512)
        val hash = ByteArray(64)
        digest.update(input, 0, input.size)
        digest.doFinal(hash, 0)
        val valid = raw[33] == hash[0] && raw[34] == hash[1]
        if (!valid) return null
        val network = when (format) {
            0 -> "Polkadot"
            2 -> "Kusama"
            42 -> "Substrate 通用/开发网络"
            else -> "SS58 format prefix $format（需查网络注册表）"
        }
        return result(s, "Polkadot SDK / Substrate", network, "SS58 32-byte account ID", details = mapOf("ss58Format" to format.toString()))
    }

    // Tezos is marked format-only here; its Base58Check implementation is not the Bitcoin one.
    private fun detectTezosShape(s: String): Result? {
        val validPrefix = s.startsWith("tz1") || s.startsWith("tz2") || s.startsWith("tz3") || s.startsWith("KT1")
        if (!validPrefix || s.length !in 33..38 || s.any { BASE58.indexOf(it) < 0 }) return null
        return result(
            s, "Tezos (XTZ)", "网络需另行确认", when {
                s.startsWith("tz1") -> "tz1 account address candidate"
                s.startsWith("tz2") -> "tz2 account address candidate"
                s.startsWith("tz3") -> "tz3 account address candidate"
                else -> "KT1 contract address candidate"
            }, Validation.FORMAT_ONLY, null,
            message = "仅匹配 Tezos 常见前缀/长度；本实现未对 Tezos 专用 Base58Check 做完整校验。"
        )
    }

    // -------------------------------------------------------------------------
    // Base58Check + hashing helpers
    // -------------------------------------------------------------------------

    private fun decodeBase58Check(s: String, alphabet: String = BASE58): ByteArray? {
        val raw = decodeBase58(s, alphabet) ?: return null
        if (raw.size < 5) return null
        val payload = raw.copyOfRange(0, raw.size - 4)
        val supplied = raw.copyOfRange(raw.size - 4, raw.size)
        val expected = doubleSha256(payload).copyOfRange(0, 4)
        return if (supplied.contentEquals(expected)) payload else null
    }

    private fun decodeBase58(s: String, alphabet: String = BASE58): ByteArray? {
        if (s.isEmpty()) return null
        var number = BigInteger.ZERO
        val radix = BigInteger.valueOf(58L)
        for (ch in s) {
            val digit = alphabet.indexOf(ch)
            if (digit < 0) return null
            number = number.multiply(radix).add(BigInteger.valueOf(digit.toLong()))
        }
        var body = number.toByteArray()
        if (body.isNotEmpty() && body[0] == 0.toByte()) body = body.copyOfRange(1, body.size)
        val leadingZeroes = s.takeWhile { it == alphabet[0] }.length
        return ByteArray(leadingZeroes) + body
    }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
    private fun doubleSha256(data: ByteArray): ByteArray = sha256(sha256(data))

    private fun keccak256(data: ByteArray): ByteArray {
        val digest = KeccakDigest(256)
        val out = ByteArray(32)
        digest.update(data, 0, data.size)
        digest.doFinal(out, 0)
        return out
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun crc16Xmodem(data: ByteArray): Int {
        var crc = 0
        for (byte in data) {
            crc = crc xor ((byte.toInt() and 0xff) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xffff
            }
        }
        return crc and 0xffff
    }
}
