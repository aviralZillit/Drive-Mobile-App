package com.zillit.drive.data.remote.interceptor

import com.zillit.drive.data.local.prefs.SessionManager
import com.zillit.drive.util.crypto.AesCbcEncryptor
import com.zillit.drive.util.crypto.BodyHashGenerator
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that attaches moduledata and bodyhash headers to every request.
 *
 * Mirrors the web app's interceptor.js:
 *   1. Build moduledata payload: { device_id, project_id, user_id, scanner_device_id, [time_stamp] }
 *   2. AES-CBC encrypt it → hex string
 *   3. Generate bodyhash: SHA-256(JSON.stringify({payload: body, moduledata: encrypted}) + ivSalt)
 *   4. Attach headers: moduledata, bodyhash
 */
@Singleton
class ModuleDataInterceptor @Inject constructor(
    private val sessionManager: SessionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip header injection for external URLs (e.g., S3 presigned URLs)
        val host = originalRequest.url.host
        if (!host.contains("zillit") && !host.contains("localhost") && !host.contains("10.0.2.2")) {
            return chain.proceed(originalRequest)
        }

        val session = sessionManager.getCachedSession() ?: return chain.proceed(originalRequest)

        // Build moduledata payload matching web's encryptHeaders()
        val moduleDataPayload = buildString {
            append("{")
            append("\"device_id\":\"${session.deviceId}\",")
            append("\"project_id\":\"${session.projectId}\",")
            append("\"user_id\":\"${session.userId}\",")
            append("\"scanner_device_id\":\"${session.scannerDeviceId}\"")
            if (session.environment == "development" || session.environment == "preprod") {
                append(",\"time_stamp\":${System.currentTimeMillis()}")
            }
            append("}")
        }

        // AES-CBC encrypt the moduledata
        val encryptedModuleData = AesCbcEncryptor.encrypt(
            plaintext = moduleDataPayload,
            keyString = session.encryptionKey,
            ivString = session.encryptionIv
        )

        // Get request body as string (empty string for GET requests)
        val requestBody = if (originalRequest.method == "GET") {
            ""
        } else {
            originalRequest.body?.let { body ->
                val buffer = Buffer()
                body.writeTo(buffer)
                buffer.readUtf8()
            } ?: ""
        }

        // Generate bodyhash
        val bodyHash = BodyHashGenerator.generate(
            requestBody = requestBody,
            moduledata = encryptedModuleData,
            salt = session.encryptionIv
        )

        // Attach headers
        val newRequest = originalRequest.newBuilder()
            .header("moduledata", encryptedModuleData)
            .header("bodyhash", bodyHash)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/html")
            .header("appVersion", "1.0.0")
            .build()

        return chain.proceed(newRequest)
    }
}
