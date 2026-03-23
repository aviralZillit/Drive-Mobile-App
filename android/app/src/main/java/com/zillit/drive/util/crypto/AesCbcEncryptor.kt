package com.zillit.drive.util.crypto

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-CBC encryption/decryption matching the web app's encryptDecrypt.js.
 *
 * Web implementation:
 *   - Key: UTF-8 encoded string from VITE_KEY_ENCRYPTION_KEY
 *   - IV:  UTF-8 encoded string from VITE_IV_ENCRYPTION_KEY
 *   - Algorithm: AES-CBC with PKCS7 padding
 *   - Output: hex-encoded ciphertext
 *
 * Android equivalent:
 *   - PKCS5Padding in JCE is functionally identical to PKCS7 for AES
 *   - Key/IV are raw UTF-8 byte arrays (must be 16, 24, or 32 bytes for AES)
 */
object AesCbcEncryptor {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

    fun encrypt(plaintext: String, keyString: String, ivString: String): String {
        val keyBytes = keyString.toByteArray(Charsets.UTF_8)
        val ivBytes = ivString.toByteArray(Charsets.UTF_8)

        val secretKey = SecretKeySpec(keyBytes, ALGORITHM)
        val ivSpec = IvParameterSpec(ivBytes)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)

        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return encryptedBytes.toHexString()
    }

    fun decrypt(hexCiphertext: String, keyString: String, ivString: String): String {
        val keyBytes = keyString.toByteArray(Charsets.UTF_8)
        val ivBytes = ivString.toByteArray(Charsets.UTF_8)

        val secretKey = SecretKeySpec(keyBytes, ALGORITHM)
        val ivSpec = IvParameterSpec(ivBytes)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

        val encryptedBytes = hexCiphertext.fromHexString()
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { byte -> "%02x".format(byte) }

    private fun String.fromHexString(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
