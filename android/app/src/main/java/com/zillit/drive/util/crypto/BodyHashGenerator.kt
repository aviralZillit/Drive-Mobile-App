package com.zillit.drive.util.crypto

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.security.MessageDigest

/**
 * Generates the bodyhash header matching the web app's generateBodyHash function.
 *
 * Web implementation (multipleFunction.js:1808-1818):
 *   1. Build object: { payload: requestBody, moduledata: encryptedModuleData }
 *   2. JSON.stringify that object
 *   3. Append the IV string (salt)
 *   4. SHA-256 hash the combined string
 *   5. Hex-encode the hash
 */
object BodyHashGenerator {

    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    fun generate(requestBody: Any?, moduledata: String, salt: String): String {
        // Build the moduleDataString object matching JS: { payload: requestBody, moduledata }
        val moduleDataMap = mapOf(
            "payload" to (requestBody ?: ""),
            "moduledata" to moduledata
        )

        // JSON.stringify equivalent
        val stringToHash = mapAdapter.toJson(moduleDataMap)

        // Append salt (IV string)
        val combinedString = stringToHash + salt

        // SHA-256 hash and hex-encode
        return hashWithSha256(combinedString)
    }

    private fun hashWithSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
