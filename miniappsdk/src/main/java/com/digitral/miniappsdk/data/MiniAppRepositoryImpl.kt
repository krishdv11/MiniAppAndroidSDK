package com.digitral.miniappsdk.data

// Internal repository implementation for sync, download, cache and metrics flows.

import android.Manifest
import android.os.Build
import com.digitral.miniappsdk.api.MiniAppApi
import com.digitral.miniappsdk.api.DeviceInfo
import com.digitral.miniappsdk.api.DownloadTokenRequest
import com.digitral.miniappsdk.api.MetricsEventRequest
import com.digitral.miniappsdk.api.PartnerAuthRequest
import com.digitral.miniappsdk.api.RuntimeMiniAppDto
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

    @Volatile
    private var latestAuthUpdatedDate: String? = null

    override suspend fun syncAndCacheMiniApps(): List<MiniAppService> {
        MiniAppDebugLogger.d("syncAndCacheMiniApps started")
        // 1) Auth/cache gate: skip network if cached auth updatedDate is unchanged.
        val existingSyncState = cacheManager.getSyncState()
        val bearer = getBearerTokenOrNull()
        val cachedServices = cacheManager.getServices()
        val authUpdatedDate = latestAuthUpdatedDate?.takeIf { it.isNotBlank() }
        if (
            cachedServices.isNotEmpty() &&
            !authUpdatedDate.isNullOrBlank() &&
            authUpdatedDate == existingSyncState.authUpdatedDate
        ) {
            MiniAppDebugLogger.d("Cache hit by updatedDate. Returning cached services count=${cachedServices.size}")
            return cachedServices
        }

        // 2) Fetch runtime list and update services cache.
        val runtimeItems = fetchRuntimeMiniAppsWithAuthFallback(bearer)
        MiniAppDebugLogger.d("Runtime list fetched. count=${runtimeItems.size}")
        val services = runtimeItems.map { it.toMiniAppService() }
        cacheManager.saveServices(services)
        val updatedSnapshots = mutableMapOf<String, MiniAppVersionSnapshot>()
        val updatedPermissions = mutableMapOf<String, List<String>>()
        runtimeItems.forEach { dto ->
            val serviceId = dto.appId?.takeIf { it.isNotBlank() } ?: appId
            updatedSnapshots[serviceId] = MiniAppVersionSnapshot(
                latestVersion = dto.latestVersion?.takeIf { it.isNotBlank() },
                bridgeVersion = dto.bridgeVersion?.takeIf { it.isNotBlank() }
            )
            updatedPermissions[serviceId] = dto.toPermissionKeys()
            if (!shouldDownloadZip(dto, existingSyncState)) {
                MiniAppDebugLogger.d("Skip zip download for appId=$serviceId due to unchanged version/bridge")
                return@forEach
            }
            // 3) Download/extract only when version/bridge changed or cache missing.
            cacheMiniAppZipForRuntimeItem(dto, bearer)
        }
        cacheManager.saveSyncState(
            MiniAppSyncState(
                authUpdatedDate = authUpdatedDate ?: existingSyncState.authUpdatedDate,
                appVersions = existingSyncState.appVersions + updatedSnapshots,
                appPermissions = existingSyncState.appPermissions + updatedPermissions
            )
        )
        return services
    }

    override fun getCachedServices(): List<MiniAppService> = cacheManager.getServices()

    private suspend fun getBearerTokenOrNull(): String? {
        val now = System.currentTimeMillis()
        if (!cachedBearerToken.isNullOrBlank() && now < tokenExpiryAtMillis) {
            return cachedBearerToken
        }

        return try {
            val authResponse = miniAppRetryIO {
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
                latestAuthUpdatedDate = authResponse.data?.updatedDate
                MiniAppDebugLogger.d("Partner auth returned empty token, continuing without auth header")
                null
            } else {
                val expiresInSeconds = authResponse.data?.expiresIn ?: 300L
                tokenExpiryAtMillis = now + (expiresInSeconds.coerceAtLeast(60L) - 10L) * 1_000L
                cachedBearerToken = "Bearer $token"
                latestAuthUpdatedDate = authResponse.data?.updatedDate
                MiniAppDebugLogger.d("Partner auth success. expiresInSeconds=$expiresInSeconds")
                cachedBearerToken
            }
        } catch (_: Exception) {
            MiniAppDebugLogger.d("Partner auth failed, continuing with auth fallback")
            cachedBearerToken = null
            tokenExpiryAtMillis = 0L
            null
        }
    }

    override fun getCachedEntryHtml(miniAppId: String): File? = cacheManager.findIndexHtml(miniAppId)

    override fun getRequiredPermissions(miniAppId: String): List<String> {
        val permissionKeys = cacheManager.getSyncState().appPermissions[miniAppId].orEmpty()
        return permissionKeys.flatMap { key -> resolveAndroidPermissions(key) }.distinct()
    }

    private fun resolveAndroidPermissions(rawKey: String): List<String> {
        val key = rawKey.trim()
        if (key.isBlank()) return emptyList()
        if (key.startsWith("android.permission.", ignoreCase = true)) {
            return listOf(key.uppercase())
        }
        return when (key.lowercase()) {
            "camera" -> listOf(Manifest.permission.CAMERA)
            "location" -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            "location_coarse", "coarse_location" -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
            "background_location" -> listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            "storage", "files" -> storagePermissions()
            "photos", "images" -> photosPermissions()
            "videos" -> videosPermissions()
            "audio", "media_audio" -> audioPermissions()
            "microphone", "mic", "record_audio" -> listOf(Manifest.permission.RECORD_AUDIO)
            "contacts" -> listOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
            )
            "calendar" -> listOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
            "sms" -> listOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            )
            "phone" -> listOf(Manifest.permission.READ_PHONE_STATE)
            "call_log" -> listOf(Manifest.permission.READ_CALL_LOG)
            "bluetooth" -> bluetoothPermissions()
            "nearby_devices" -> nearbyPermissions()
            "notifications", "notification" -> notificationPermissions()
            "network" -> emptyList() // Normal permission; no runtime prompt needed.
            else -> emptyList()
        }
    }

    private fun storagePermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    private fun photosPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun videosPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun audioPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun bluetoothPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            emptyList()
        }
    }

    private fun nearbyPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            emptyList()
        }
    }

    private fun notificationPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
    }

    private suspend fun cacheMiniAppZipForRuntimeItem(runtime: RuntimeMiniAppDto, bearer: String?): Unit {
        try {
            val service = runtime.toMiniAppService()
            if (cacheManager.findIndexHtml(service.id) != null) {
                MiniAppDebugLogger.d("Zip already cached for appId=${service.id}")
                return
            }
            // Download-token -> zip download (resume-capable) -> checksum append -> unzip -> manifest verify.
            val version = runtime.latestVersion?.takeIf { it.isNotBlank() } ?: "1.0.0"
            MiniAppDebugLogger.d("Preparing zip cache for appId=${service.id} version=$version")
            val currentVersion = resolveCurrentVersionForDownload(service.id)
            val tokenResponse = fetchDownloadTokenWithAuthFallback(
                miniAppId = service.id,
                currentVersion = currentVersion,
                bearer = bearer
            )
            recordLifecycleEvent(
                service = service,
                version = version,
                eventType = "ZipDownloadInitiated",
                message = ""
            )
            val downloadData = tokenResponse.data
            val downloadUrl = downloadData?.downloadUrl.orEmpty()
            val checksum = downloadData?.checksum.orEmpty()
            if (downloadUrl.isBlank()) {
                MiniAppDebugLogger.e("Missing downloadUrl for appId=${service.id}")
                recordZipMetric(service, version, false, "Missing downloadUrl")
                return
            }
            val artifactId = downloadData?.artifactId?.ifBlank { null } ?: "artifact"
            val zip = cacheManager.zipFile(service.id, artifactId)
            MiniAppDebugLogger.d("Downloading zip for appId=${service.id} -> ${zip.absolutePath}")
            downloadZip(downloadUrl, zip)
            appendChecksumPartToZip(zip, checksum)
            val extractionDir = cacheManager.extractionDir(service.id, artifactId)
            cacheManager.unzip(zip, extractionDir)
            verifyManifestFile(extractionDir)
            MiniAppDebugLogger.d("Zip cached successfully for appId=${service.id}")
            recordLifecycleEvent(
                service = service,
                version = version,
                eventType = "ManifestVerified",
                message = ""
            )
            recordLifecycleEvent(
                service = service,
                version = version,
                eventType = "ZipExtracted",
                message = ""
            )
            recordZipMetric(service, version, true, "")
        } catch (e: Exception) {
            MiniAppDebugLogger.e("Zip cache failed for appId=${runtime.appId}", e)
            val fallbackServiceId = runtime.appId?.takeIf { it.isNotBlank() } ?: appId
            val fallbackService = MiniAppService(
                id = fallbackServiceId,
                title = runtime.name?.takeIf { it.isNotBlank() } ?: fallbackServiceId,
                description = runtime.category.orEmpty(),
                imageUrl = runtime.iconUrl.orEmpty()
            )
            val version = runtime.latestVersion?.takeIf { it.isNotBlank() } ?: "1.0.0"
            if (e.message?.contains("Manifest verification failed", ignoreCase = true) == true) {
                recordLifecycleEvent(
                    service = fallbackService,
                    version = version,
                    eventType = "ManifestVerifyFailed",
                    message = e.message.orEmpty()
                )
            } else {
                recordLifecycleEvent(
                    service = fallbackService,
                    version = version,
                    eventType = "ZipExtractFailed",
                    message = e.message.orEmpty()
                )
            }
            recordZipMetric(fallbackService, version, false, e.message.orEmpty())
        }
    }

    private suspend fun fetchDownloadTokenWithAuthFallback(
        miniAppId: String,
        currentVersion: String,
        bearer: String?
    ) = try {
        MiniAppDebugLogger.d("Fetching download-token for appId=$miniAppId currentVersion=$currentVersion with auth=${!bearer.isNullOrBlank()}")
        miniAppRetryIO {
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
        MiniAppDebugLogger.d("download-token with auth failed for appId=$miniAppId. Retrying without auth")
        miniAppRetryIO {
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

    private fun shouldDownloadZip(
        runtime: RuntimeMiniAppDto,
        syncState: MiniAppSyncState
    ): Boolean {
        val serviceId = runtime.appId?.takeIf { it.isNotBlank() } ?: appId
        if (cacheManager.findIndexHtml(serviceId) == null) return true
        val snapshot = syncState.appVersions[serviceId] ?: return true
        val latestChanged = snapshot.latestVersion != runtime.latestVersion?.takeIf { it.isNotBlank() }
        val bridgeChanged = snapshot.bridgeVersion != runtime.bridgeVersion?.takeIf { it.isNotBlank() }
        return latestChanged || bridgeChanged
    }

    private suspend fun fetchRuntimeMiniAppsWithAuthFallback(
        bearer: String?
    ): List<RuntimeMiniAppDto> = try {
        MiniAppDebugLogger.d("Fetching runtime mini apps with auth=${!bearer.isNullOrBlank()}")
        miniAppRetryIO {
            api.getRuntimeMiniApps(authorization = bearer)
                .data
                .orEmpty()
                .sortedBy { it.displayOrder ?: Int.MAX_VALUE }
        }
    } catch (firstError: Exception) {
        if (bearer.isNullOrBlank()) throw firstError
        MiniAppDebugLogger.d("Runtime list with auth failed. Retrying without auth")
        miniAppRetryIO {
            api.getRuntimeMiniApps(authorization = null)
                .data
                .orEmpty()
                .sortedBy { it.displayOrder ?: Int.MAX_VALUE }
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
        destination.parentFile?.mkdirs()
        val existingBytes = if (destination.exists()) destination.length() else 0L
        MiniAppDebugLogger.d("downloadZip start url=$url existingBytes=$existingBytes file=${destination.name}")
        val rangeRequest = Request.Builder()
            .url(url)
            .get()
            .apply {
                if (existingBytes > 0L) {
                    header("Range", "bytes=$existingBytes-")
                }
            }
            .build()

        httpClient.newCall(rangeRequest).execute().use { response ->
            if (response.code == 416) {
                MiniAppDebugLogger.d("downloadZip received 416. Restarting from zero for ${destination.name}")
                destination.delete()
                downloadZip(url, destination)
                return
            }
            require(response.isSuccessful) { "Zip download failed with code ${response.code}" }
            val body = response.body ?: error("Zip download body is null")
            // If backend honors range (206), append from previous byte; otherwise restart clean.
            val append = existingBytes > 0L && response.code == 206
            if (existingBytes > 0L && response.code == 200) {
                MiniAppDebugLogger.d("Server ignored range and returned 200. Restarting write for ${destination.name}")
                destination.delete()
            }
            FileOutputStream(destination, append).use { output ->
                body.byteStream().use { input ->
                    val totalBytes = body.contentLength().takeIf { it > 0L }?.let { it + existingBytes }
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = existingBytes
                    var read = input.read(buffer)
                    var lastLoggedPercent = -1
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes != null && totalBytes > 0L) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                            if (percent / 10 > lastLoggedPercent / 10) {
                                lastLoggedPercent = percent
                                MiniAppDebugLogger.d(
                                    "downloadZip progress ${destination.name}: $percent% ($downloadedBytes/$totalBytes)"
                                )
                            }
                        }
                        read = input.read(buffer)
                    }
                    MiniAppDebugLogger.d(
                        "downloadZip complete ${destination.name}: bytesWritten=${destination.length()}"
                    )
                }
            }
        }
    }

    private fun verifyManifestFile(extractionDir: File): Unit {
        val manifest = extractionDir.walkTopDown().firstOrNull {
            it.isFile && (
                it.name.equals("manifest.json", ignoreCase = true) ||
                    it.name.equals("manifest.webmanifest", ignoreCase = true)
                )
        }
        require(manifest != null) { "Manifest verification failed: manifest file not found" }
    }

    private suspend fun recordZipMetric(
        service: MiniAppService,
        version: String,
        success: Boolean,
        message: String
    ): Unit {
        val eventType = if (success) "ZipDownloadCompleted" else "ZipDownloadFailed"
        recordLifecycleEvent(service, version, eventType, message)
    }

    override suspend fun recordLifecycleEvent(miniAppId: String, eventType: String, message: String) {
        val runtimeVersion = cacheManager.getSyncState().appVersions[miniAppId]?.latestVersion ?: "1.0.0"
        val service = MiniAppService(
            id = miniAppId,
            title = miniAppId,
            description = "",
            imageUrl = ""
        )
        recordLifecycleEvent(service, runtimeVersion, eventType, message)
    }

    private suspend fun recordLifecycleEvent(
        service: MiniAppService,
        version: String,
        eventType: String,
        message: String
    ): Unit {
        try {
            val bearer = cachedBearerToken
            val event = MetricsEventRequest(
                appId = service.id,
                version = version,
                eventType = eventType,
                deviceId = "sdk-device",
                os = "android",
                superAppVersion = "sdk-1.0.0",
                message = message,
                metadata = "{\"domain\":\"$domainUrl\"}"
            )
            miniAppRetryIO {
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

    private fun RuntimeMiniAppDto.toPermissionKeys(): List<String> {
        val raw = permissions ?: return emptyList()
        if (!raw.isJsonArray) return emptyList()
        return raw.asJsonArray.mapNotNull { node ->
            node?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
        }
    }
}
