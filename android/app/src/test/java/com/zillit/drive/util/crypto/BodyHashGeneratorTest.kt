package com.zillit.drive.util.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for body hash generation matching multipleFunction.js:1808-1818.
 */
class BodyHashGeneratorTest {

    private val testSalt = "abcdef0123456789"

    @Test
    fun `generates consistent hash for same input`() {
        val hash1 = BodyHashGenerator.generate("body", "moduledata", testSalt)
        val hash2 = BodyHashGenerator.generate("body", "moduledata", testSalt)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `generates SHA-256 hex string (64 chars)`() {
        val hash = BodyHashGenerator.generate("test", "mod", testSalt)
        assertEquals(64, hash.length)
        assert(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `different bodies produce different hashes`() {
        val hash1 = BodyHashGenerator.generate("body1", "mod", testSalt)
        val hash2 = BodyHashGenerator.generate("body2", "mod", testSalt)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `empty body generates valid hash`() {
        val hash = BodyHashGenerator.generate("", "mod", testSalt)
        assertEquals(64, hash.length)
    }

    @Test
    fun `null body treated as empty string`() {
        val hash = BodyHashGenerator.generate(null, "mod", testSalt)
        assertEquals(64, hash.length)
    }
}
