package com.digitral.miniappsdk.data

import android.os.Build
import com.digitral.miniappsdk.api.MiniAppApi
import com.digitral.miniappsdk.api.DeviceInfo
import com.digitral.miniappsdk.api.DownloadTokenRequest
import com.digitral.miniappsdk.api.MetricsEventRequest
import com.digitral.miniappsdk.api.PartnerAuthRequest
import com.digitral.miniappsdk.api.RuntimeMiniAppDto
import com.digitral.miniappsdk.api.SessionTokenRequest
import com.digitral.miniappsdk.domain.model.MiniAppService
import com.digitral.miniappsdk.domain.repository.MiniAppRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

internal class MiniAppRepositoryImpl(
    private val api: MiniAppApi,
    private val appId: String,
    private val secretKey: String,
    private val domainUrl: String,
    private val cacheManager: MiniAppCacheManager,
    private val httpClient: OkHttpClient
) : MiniAppRepository {
    private companion object {
        private const val INITIAL_CURRENT_VERSION = "0.0.0"
    }

    @Volatile
    private var cachedBearerToken: String? = null

    @Volatile
    private var tokenExpiryAtMillis: Long = 0L

    override suspend fun syncAndCacheMiniApps(): List<MiniAppService> {
        val bearer = getBearerTokenOrNull()
        val runtimeItems = fetchRuntimeMiniAppsWithAuthFallback(bearer)
        val services = runtimeItems.map { it.toMiniAppService() }
        cacheManager.saveServices(services)
        runtimeItems.forEach { dto ->
            cacheMiniAppZipForRuntimeItem(dto, bearer)
        }
        return services
    }

    override fun getCachedServices(): List<MiniAppService> = cacheManager.getServices()

    override suspend fun ensureMiniAppCached(miniAppId: String): Boolean {
        if (miniAppId.isBlank()) return false
        if (cacheManager.findIndexHtml(miniAppId) != null) return true

        val bearer = getBearerTokenOrNull()
        val runtimeItem = fetchRuntimeMiniAppsWithAuthFallback(bearer)
            .firstOrNull { it.appId == miniAppId }
            ?: RuntimeMiniAppDto(
                appId = miniAppId,
                name = miniAppId,
                latestVersion = "1.0.0"
            )

        cacheMiniAppZipForRuntimeItem(runtimeItem, bearer)
        return cacheManager.findIndexHtml(miniAppId) != null
    }

    private suspend fun getBearerTokenOrNull(): String? {
        val now = System.currentTimeMillis()
        if (!cachedBearerToken.isNullOrBlank() && now < tokenExpiryAtMillis) {
            return cachedBearerToken
        }

        return try {
            val authResponse = retryIO {
                api.partnerAuth(
                    request = PartnerAuthRequest(
                        partnerId = appId,
                        signature = secretKey
                    )
                )
            }
            val token = authResponse.data?.token.orEmpty()
            if (token.isBlank()) {
                cachedBearerToken = null
                tokenExpiryAtMillis = 0L
                null
            } else {
                val expiresInSeconds = authResponse.data?.expiresIn ?: 300L
                tokenExpiryAtMillis = now + (expiresInSeconds.coerceAtLeast(60L) - 10L) * 1_000L
                cachedBearerToken = "Bearer $token"
                cachedBearerToken
            }
        } catch (_: Exception) {
            cachedBearerToken = null
            tokenExpiryAtMillis = 0L
            null
        }
    }

    override suspend fun getSessionToken(miniAppId: String): String {
        val bearer = getBearerTokenOrNull()
        val response = fetchSessionTokenWithAuthFallback(
            miniAppId = miniAppId,
            bearer = bearer,
            request = SessionTokenRequest(
                userId = "sdk-user-${Build.MODEL}",
                scope = listOf("profile.read")
            )
        )
        val token = response.data?.token.orEmpty()
        require(token.isNotBlank()) { "Session token is empty" }
        return token
    }

    override fun getCachedEntryHtml(miniAppId: String): File? = cacheManager.findIndexHtml(miniAppId)

    private suspend fun cacheMiniAppZipForRuntimeItem(runtime: RuntimeMiniAppDto, bearer: String?): Unit {
        try {
            val service = runtime.toMiniAppService()
            if (cacheManager.findIndexHtml(service.id) != null) {
                return
            }
            val version = runtime.latestVersion?.takeIf { it.isNotBlank() } ?: "1.0.0"
            val currentVersion = resolveCurrentVersionForDownload(service.id)
            val tokenResponse = fetchDownloadTokenWithAuthFallback(
                miniAppId = service.id,
                currentVersion = currentVersion,
                bearer = bearer
            )
            val downloadData = tokenResponse.data
            val downloadUrl = downloadData?.downloadUrl.orEmpty()
            val checksum = downloadData?.checksum.orEmpty()
            if (downloadUrl.isBlank()) {
                recordZipMetric(service, version, false, "Missing downloadUrl")
                return
            }
            val artifactId = downloadData?.artifactId?.ifBlank { null } ?: "artifact"
            val zip = cacheManager.zipFile(service.id, artifactId)
            downloadZip(downloadUrl, zip)
            appendChecksumPartToZip(zip, checksum)
            cacheManager.unzip(zip, cacheManager.extractionDir(service.id, artifactId))
            recordZipMetric(service, version, true, "")
        } catch (e: Exception) {
            val fallbackServiceId = runtime.appId?.takeIf { it.isNotBlank() } ?: appId
            val fallbackService = MiniAppService(
                id = fallbackServiceId,
                title = runtime.name?.takeIf { it.isNotBlank() } ?: fallbackServiceId,
                description = runtime.category.orEmpty(),
                imageUrl = runtime.iconUrl.orEmpty()
            )
            val version = runtime.latestVersion?.takeIf { it.isNotBlank() } ?: "1.0.0"
            recordZipMetric(fallbackService, version, false, e.message.orEmpty())
        }
    }

    private suspend fun fetchDownloadTokenWithAuthFallback(
        miniAppId: String,
        currentVersion: String,
        bearer: String?
    ) = try {
        retryIO {
            api.getDownloadToken(
                authorization = bearer,
                appId = miniAppId,
                request = DownloadTokenRequest(
                    currentVersion = currentVersion,
                    deviceInfo = DeviceInfo(
                        os = "android",
                        superAppVersion = "sdk-1.0.0"
                    )
                )
            )
        }
    } catch (firstError: Exception) {
        if (bearer.isNullOrBlank()) throw firstError
        retryIO {
            api.getDownloadToken(
                authorization = null,
                appId = miniAppId,
                request = DownloadTokenRequest(
                    currentVersion = currentVersion,
                    deviceInfo = DeviceInfo(
                        os = "android",
                        superAppVersion = "sdk-1.0.0"
                    )
                )
            )
        }
    }

    private fun resolveCurrentVersionForDownload(miniAppId: String): String {
        return if (cacheManager.findIndexHtml(miniAppId) != null) {
            "1.0.0"
        } else {
            INITIAL_CURRENT_VERSION
        }
    }

    private suspend fun fetchRuntimeMiniAppsWithAuthFallback(
        bearer: String?
    ): List<RuntimeMiniAppDto> = try {
        retryIO {
            api.getRuntimeMiniApps(authorization = bearer)
                .data
                .orEmpty()
                .sortedBy { it.displayOrder ?: Int.MAX_VALUE }
        }
    } catch (firstError: Exception) {
        if (bearer.isNullOrBlank()) throw firstError
        retryIO {
            api.getRuntimeMiniApps(authorization = null)
                .data
                .orEmpty()
                .sortedBy { it.displayOrder ?: Int.MAX_VALUE }
        }
    }

    private suspend fun fetchSessionTokenWithAuthFallback(
        miniAppId: String,
        bearer: String?,
        request: SessionTokenRequest
    ) = try {
        retryIO {
            api.getSessionToken(
                authorization = bearer,
                appId = miniAppId,
                request = request
            )
        }
    } catch (firstError: Exception) {
        if (bearer.isNullOrBlank()) throw firstError
        retryIO {
            api.getSessionToken(
                authorization = null,
                appId = miniAppId,
                request = request
            )
        }
    }

    private fun appendChecksumPartToZip(zipFile: File, checksumHex: String): Unit {
        if (checksumHex.isBlank()) return
        require(zipFile.exists() && zipFile.length() > 0L) { "Downloaded zip is empty" }
        val checksumBytes = checksumHex.decodeHexToByteArray()
        require(checksumBytes.isNotEmpty()) { "Checksum bytes are empty" }
        FileOutputStream(zipFile, true).use { output ->
            output.write(checksumBytes)
        }
    }

    private fun String.decodeHexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Invalid checksum hex length" }
        val output = ByteArray(length / 2)
        var i = 0
        while (i < length) {
            val high = this[i].digitToIntOrNull(16)
            val low = this[i + 1].digitToIntOrNull(16)
            require(high != null && low != null) { "Invalid checksum hex characters" }
            output[i / 2] = ((high shl 4) + low).toByte()
            i += 2
        }
        return output
    }

    private suspend fun downloadZip(url: String, destination: File): Unit {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Zip download failed with code ${response.code}" }
            val body = response.body ?: error("Zip download body is null")
            destination.parentFile?.mkdirs()
            destination.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
    }

    private suspend fun recordZipMetric(
        service: MiniAppService,
        version: String,
        success: Boolean,
        message: String
    ): Unit {
        try {
            val bearer = cachedBearerToken
            val event = MetricsEventRequest(
                appId = service.id,
                version = version,
                eventType = if (success) "ZipDownloadCompleted" else "ZipDownloadFailed",
                deviceId = "sdk-device",
                os = "android",
                superAppVersion = "sdk-1.0.0",
                message = message,
                metadata = "{\"domain\":\"$domainUrl\"}"
            )
            retryIO {
                api.recordEvent(authorization = bearer, request = event)
            }
        } catch (_: Exception) {
            // Metrics should not fail the core mini app flow.
        }
    }

    private fun RuntimeMiniAppDto.toMiniAppService(): MiniAppService {
        val serviceId = this.appId?.takeIf { it.isNotBlank() } ?: this@MiniAppRepositoryImpl.appId
        val serviceTitle = name?.takeIf { it.isNotBlank() } ?: serviceId
        val categoryText = category?.takeIf { it.isNotBlank() }
        val versionText = latestVersion?.takeIf { it.isNotBlank() }
        val serviceDescription = listOfNotNull(categoryText, versionText?.let { "v$it" }).joinToString(" | ")
            .ifBlank { "Mini app service" }
        return MiniAppService(
            id = serviceId,
            title = serviceTitle,
            description = serviceDescription,
            imageUrl = iconUrl.orEmpty()
        )
    }
}
