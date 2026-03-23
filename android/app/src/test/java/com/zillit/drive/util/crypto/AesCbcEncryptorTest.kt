package com.zillit.drive.util.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for AES-CBC encryption matching the web app's encryptDecrypt.js.
 *
 * To generate test vectors:
 * 1. Open browser console on zillit_web
 * 2. Run: await EncryptDecrypt.aesEncrypt('test', key, iv)
 * 3. Use that output as the expected value here
 *
 * IMPORTANT: Replace the key/iv below with actual values from your
 * VITE_KEY_ENCRYPTION_KEY and VITE_IV_ENCRYPTION_KEY env vars.
 */
class AesCbcEncryptorTest {

    // TODO: Replace with actual key/iv from your env vars for real validation
    // These must be 16 bytes (128-bit AES) for the test to work
    private val testKey = "0123456789abcdef" // 16 bytes
    private val testIv = "abcdef0123456789"  // 16 bytes

    @Test
    fun `encrypt and decrypt roundtrip produces original text`() {
        val plaintext = "Hello, Zillit Drive!"
        val encrypted = AesCbcEncryptor.encrypt(plaintext, testKey, testIv)
        val decrypted = AesCbcEncryptor.decrypt(encrypted, testKey, testIv)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt produces hex-encoded output`() {
        val plaintext = "test"
        val encrypted = AesCbcEncryptor.encrypt(plaintext, testKey, testIv)
        // Hex string should only contain 0-9a-f
        assert(encrypted.matches(Regex("[0-9a-f]+")))
        // AES-CBC with 4-byte input + PKCS7 padding = 16 bytes = 32 hex chars
        assertEquals(32, encrypted.length)
    }

    @Test
    fun `encrypt JSON moduledata roundtrip`() {
        val moduleData = """{"device_id":"dev123","project_id":"proj456","user_id":"user789","scanner_device_id":""}"""
        val encrypted = AesCbcEncryptor.encrypt(moduleData, testKey, testIv)
        val decrypted = AesCbcEncryptor.decrypt(encrypted, testKey, testIv)
        assertEquals(moduleData, decrypted)
    }

    @Test
    fun `different plaintexts produce different ciphertexts`() {
        val enc1 = AesCbcEncryptor.encrypt("text1", testKey, testIv)
        val enc2 = AesCbcEncryptor.encrypt("text2", testKey, testIv)
        assertNotEquals(enc1, enc2)
    }

    @Test
    fun `empty string encryption works`() {
        val encrypted = AesCbcEncryptor.encrypt("", testKey, testIv)
        val decrypted = AesCbcEncryptor.decrypt(encrypted, testKey, testIv)
        assertEquals("", decrypted)
    }
}
