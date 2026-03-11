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

internal class MiniAppRepositoryImpl(
    private val api: MiniAppApi,
    private val appId: String,
    private val secretKey: String,
    private val domainUrl: String,
    private val cacheManager: MiniAppCacheManager,
    private val httpClient: OkHttpClient
) : MiniAppRepository {

    @Volatile
    private var cachedBearerToken: String? = null

    @Volatile
    private var tokenExpiryAtMillis: Long = 0L

    override suspend fun syncAndCacheMiniApps(): List<MiniAppService> {
        val bearer = getBearerToken()
        val runtimeItems = retryIO {
            val runtimeResponse = api.getRuntimeMiniApps(authorization = bearer)
            runtimeResponse.data.orEmpty().sortedBy { it.displayOrder ?: Int.MAX_VALUE }
        }
        val services = runtimeItems.map { it.toMiniAppService() }
        cacheManager.saveServices(services)
        runtimeItems.forEach { dto ->
            cacheMiniAppZipForRuntimeItem(dto, bearer)
        }
        return services
    }

    override fun getCachedServices(): List<MiniAppService> = cacheManager.getServices()

    private suspend fun getBearerToken(): String {
        val now = System.currentTimeMillis()
        if (!cachedBearerToken.isNullOrBlank() && now < tokenExpiryAtMillis) {
            return cachedBearerToken.orEmpty()
        }

        val authResponse = retryIO {
            api.partnerAuth(
                request = PartnerAuthRequest(
                    partnerId = appId,
                    signature = secretKey
                )
            )
        }

        val token = authResponse.data?.token.orEmpty()
        require(token.isNotBlank()) { "Partner auth failed: empty token" }

        val expiresInSeconds = authResponse.data?.expiresIn ?: 300L
        // Renew a little earlier than exact expiration.
        tokenExpiryAtMillis = now + (expiresInSeconds.coerceAtLeast(60L) - 10L) * 1_000L
        cachedBearerToken = "Bearer $token"
        return cachedBearerToken.orEmpty()
    }

    override suspend fun getSessionToken(miniAppId: String): String {
        val bearer = getBearerToken()
        val response = retryIO {
            api.getSessionToken(
                authorization = bearer,
                appId = miniAppId,
                request = SessionTokenRequest(
                    userId = "sdk-user-${Build.MODEL}",
                    scope = listOf("profile.read")
                )
            )
        }
        val token = response.data?.token.orEmpty()
        require(token.isNotBlank()) { "Session token is empty" }
        return token
    }

    override fun getCachedEntryHtml(miniAppId: String): File? = cacheManager.findIndexHtml(miniAppId)

    private suspend fun cacheMiniAppZipForRuntimeItem(runtime: RuntimeMiniAppDto, bearer: String): Unit {
        try {
            val service = runtime.toMiniAppService()
            val version = runtime.latestVersion?.takeIf { it.isNotBlank() } ?: "1.0.0"
            val tokenResponse = retryIO {
                api.getDownloadToken(
                    authorization = bearer,
                    appId = service.id,
                    request = DownloadTokenRequest(
                        currentVersion = version,
                        deviceInfo = DeviceInfo(
                            os = "android",
                            superAppVersion = "sdk-1.0.0"
                        )
                    )
                )
            }
            val downloadData = tokenResponse.data
            val downloadUrl = downloadData?.downloadUrl.orEmpty()
            if (downloadUrl.isBlank()) {
                recordZipMetric(service, version, false, "Missing downloadUrl")
                return
            }
            val artifactId = downloadData?.artifactId?.ifBlank { null } ?: "artifact"
            val zip = cacheManager.zipFile(service.id, artifactId)
            downloadZip(downloadUrl, zip)
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
            val bearer = cachedBearerToken ?: return
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
