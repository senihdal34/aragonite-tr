package com.ethran.notable.reading.chapters

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Decryption of PDFs protected by the standard security handler, for the one case that matters here: a file that opens
 * without a password (an empty user password, with an owner password that only restricts printing or copying). Many
 * downloaded books are like that, and without decrypting the strings their bookmark titles are unreadable.
 *
 * Supports RC4 40/128-bit (revisions 2 and 3), V4 crypt filters (RC4 or AES-128) and AES-256 (revisions 5 and 6).
 * A file that needs a real password gives [PdfEncryptedException]. Uses only java.security / javax.crypto, which exist on
 * both the JVM and Android.
 */
internal class PdfCrypto private constructor(
    private val fileKey: ByteArray,
    private val stringMethod: Method,
    private val streamMethod: Method,
) {
    enum class Method { None, Rc4, Aes128, Aes256 }

    fun decryptString(data: ByteArray, num: Int, gen: Int): ByteArray = decrypt(data, num, gen, stringMethod)
    fun decryptStream(data: ByteArray, num: Int, gen: Int): ByteArray = decrypt(data, num, gen, streamMethod)

    private fun decrypt(data: ByteArray, num: Int, gen: Int, method: Method): ByteArray = try {
        when (method) {
            Method.None -> data
            Method.Rc4 -> rc4(objectKey(num, gen, false), data)
            Method.Aes128 -> aesCbc(objectKey(num, gen, true), data)
            Method.Aes256 -> aesCbc(fileKey, data)
        }
    } catch (e: java.security.GeneralSecurityException) {
        data
    }

    /** Algorithm 1: the file key plus the object and generation numbers (and "sAlT" for AES), hashed. */
    private fun objectKey(num: Int, gen: Int, aes: Boolean): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        md.update(fileKey)
        md.update(byteArrayOf(num.toByte(), (num shr 8).toByte(), (num shr 16).toByte(), gen.toByte(), (gen shr 8).toByte()))
        if (aes) md.update(byteArrayOf(0x73, 0x41, 0x6C, 0x54))
        return md.digest().copyOf(minOf(fileKey.size + 5, 16))
    }

    companion object {
        private val PAD = intArrayOf(
            0x28, 0xBF, 0x4E, 0x5E, 0x4E, 0x75, 0x8A, 0x41, 0x64, 0x00, 0x4E, 0x56, 0xFF, 0xFA, 0x01, 0x08,
            0x2E, 0x2E, 0x00, 0xB6, 0xD0, 0x68, 0x3E, 0x80, 0x2F, 0x0C, 0xA9, 0xFE, 0x64, 0x53, 0x69, 0x7A
        ).map { it.toByte() }.toByteArray()

        /**
         * The decryptor for [enc] (the /Encrypt dictionary, resolved by the caller) with the empty user password.
         * [id] is the first string of the trailer's /ID. Throws [PdfEncryptedException] when it cannot be opened that way.
         */
        fun open(enc: PdfDict, id: ByteArray, resolve: (Any?) -> Any?): PdfCrypto {
            val filter = (resolve(enc["Filter"]) as? PdfName)?.value
            if (filter != null && filter != "Standard") throw PdfEncryptedException()
            val v = resolve(enc["V"]).asInt() ?: 0
            val r = resolve(enc["R"]).asInt() ?: 0
            val o = (resolve(enc["O"]) as? PdfString)?.bytes ?: throw PdfEncryptedException()
            val u = (resolve(enc["U"]) as? PdfString)?.bytes ?: throw PdfEncryptedException()
            val p = (resolve(enc["P"]) as? Number)?.toLong()?.toInt() ?: 0
            val encryptMetadata = resolve(enc["EncryptMetadata"]) as? Boolean ?: true
            if (v !in 1..5) throw PdfEncryptedException()

            var stringMethod = Method.Rc4
            var streamMethod = Method.Rc4
            var bits = resolve(enc["Length"]).asInt() ?: 40
            if (v == 1) bits = 40
            if (v >= 4) {
                val cf = resolve(enc["CF"]) as? PdfDict
                fun methodOf(key: String): Method {
                    val name = (resolve(enc[key]) as? PdfName)?.value ?: "Identity"
                    if (name == "Identity") return Method.None
                    val f = resolve(cf?.get(name)) as? PdfDict ?: return Method.None
                    return when ((resolve(f["CFM"]) as? PdfName)?.value) {
                        "V2" -> Method.Rc4
                        "AESV2" -> Method.Aes128
                        "AESV3" -> Method.Aes256
                        else -> Method.None
                    }
                }
                streamMethod = methodOf("StmF")
                stringMethod = methodOf("StrF")
                if (v == 4) bits = 128
            }
            if (r >= 5) {
                val key = fileKeyR56(r, u, enc, resolve)
                return PdfCrypto(key, if (stringMethod == Method.None) Method.None else Method.Aes256, if (streamMethod == Method.None) Method.None else Method.Aes256)
            }
            if (r !in 2..4 || bits < 40 || bits > 128 || bits % 8 != 0) throw PdfEncryptedException()
            val n = if (r == 2) 5 else bits / 8
            // Algorithm 2 with an empty password.
            val md = MessageDigest.getInstance("MD5")
            md.update(PAD)
            md.update(o.copyOf(32))
            md.update(byteArrayOf(p.toByte(), (p shr 8).toByte(), (p shr 16).toByte(), (p shr 24).toByte()))
            md.update(id)
            if (r >= 4 && !encryptMetadata) md.update(byteArrayOf(-1, -1, -1, -1))
            var hash = md.digest()
            if (r >= 3) repeat(50) { hash = MessageDigest.getInstance("MD5").digest(hash.copyOf(n)) }
            val key = hash.copyOf(n)
            // Algorithms 4 to 6: the empty password must reproduce /U, or the file needs a real one.
            val ok = if (r == 2) rc4(key, PAD).contentEquals(u.copyOf(32))
            else {
                val h = MessageDigest.getInstance("MD5").digest(PAD + id)
                var x = rc4(key, h)
                for (i in 1..19) x = rc4(ByteArray(key.size) { (key[it].toInt() xor i).toByte() }, x)
                x.copyOf(16).contentEquals(u.copyOf(16))
            }
            if (!ok) throw PdfEncryptedException()
            return PdfCrypto(key, stringMethod, streamMethod)
        }

        /** Algorithm 2.A for the empty user password: validate /U, then unwrap /UE into the file key. */
        private fun fileKeyR56(r: Int, u: ByteArray, enc: PdfDict, resolve: (Any?) -> Any?): ByteArray {
            val ue = (resolve(enc["UE"]) as? PdfString)?.bytes ?: throw PdfEncryptedException()
            if (u.size < 48 || ue.size < 32) throw PdfEncryptedException()
            val validationSalt = u.copyOfRange(32, 40)
            val keySalt = u.copyOfRange(40, 48)
            val empty = ByteArray(0)
            if (!hash(r, empty, validationSalt, empty).contentEquals(u.copyOf(32))) throw PdfEncryptedException()
            val intermediate = hash(r, empty, keySalt, empty)
            val c = Cipher.getInstance("AES/CBC/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(intermediate, "AES"), IvParameterSpec(ByteArray(16)))
            return c.doFinal(ue.copyOf(32))
        }

        /** The hash of revision 5 (SHA-256) or 6 (algorithm 2.B). */
        private fun hash(r: Int, password: ByteArray, salt: ByteArray, udata: ByteArray): ByteArray {
            var k = MessageDigest.getInstance("SHA-256").digest(password + salt + udata)
            if (r == 5) return k
            var e = ByteArray(1)
            var i = 0
            while (i < 64 || (e.last().toInt() and 0xFF) > i - 32) {
                val one = password + k + udata
                val k1 = ByteArray(one.size * 64)
                for (j in 0 until 64) System.arraycopy(one, 0, k1, j * one.size, one.size)
                val c = Cipher.getInstance("AES/CBC/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k.copyOf(16), "AES"), IvParameterSpec(k.copyOfRange(16, 32)))
                e = c.doFinal(k1)
                var sum = 0
                for (j in 0 until 16) sum += e[j].toInt() and 0xFF
                val algo = when (sum % 3) { 0 -> "SHA-256"; 1 -> "SHA-384"; else -> "SHA-512" }
                k = MessageDigest.getInstance(algo).digest(e)
                i++
            }
            return k.copyOf(32)
        }

        fun rc4(key: ByteArray, data: ByteArray): ByteArray {
            val s = IntArray(256) { it }
            var j = 0
            for (i in 0 until 256) {
                j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
                val t = s[i]; s[i] = s[j]; s[j] = t
            }
            val out = ByteArray(data.size)
            var a = 0
            var b = 0
            for (k in data.indices) {
                a = (a + 1) and 0xFF
                b = (b + s[a]) and 0xFF
                val t = s[a]; s[a] = s[b]; s[b] = t
                out[k] = (data[k].toInt() xor s[(s[a] + s[b]) and 0xFF]).toByte()
            }
            return out
        }

        /** AES-CBC with the IV in the first 16 bytes and PKCS padding; data too short to hold both is empty. */
        fun aesCbc(key: ByteArray, data: ByteArray): ByteArray {
            if (data.size < 32) return ByteArray(0)
            val body = data.copyOfRange(16, data.size - (data.size - 16) % 16)
            val c = Cipher.getInstance("AES/CBC/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(data.copyOf(16)))
            val plain = c.doFinal(body)
            if (plain.isEmpty()) return plain
            val pad = plain.last().toInt() and 0xFF
            return if (pad in 1..16 && pad <= plain.size) plain.copyOf(plain.size - pad) else plain
        }
    }
}
