package com.digitral.miniappsdk.api

// Internal API DTOs for network serialization/deserialization.

import com.google.gson.JsonElement

internal data class ApiEnvelope<T>(
    val success: Boolean? = null,
    val data: T? = null
)

internal data class PartnerAuthRequest(
    val partnerId: String,
    val signature: String
)

internal data class PartnerAuthData(
    val token: String? = null,
    val expiresIn: Long? = null,
    val updatedDate: String? = null
)

internal data class DeviceInfo(
    val os: String,
    val superAppVersion: String
)

internal data class DownloadTokenRequest(
    val currentVersion: String,
    val deviceInfo: DeviceInfo
)

internal data class DownloadTokenData(
    val downloadUrl: String? = null,
    val checksum: String? = null,
    val artifactId: String? = null,
    val expiresIn: Long? = null
)

internal data class MetricsEventRequest(
    val appId: String,
    val version: String,
    val eventType: String,
    val deviceId: String,
    val os: String,
    val superAppVersion: String,
    val message: String,
    val metadata: String
)

internal data class RuntimeMiniAppDto(
    val appId: String? = null,
    val name: String? = null,
    val category: String? = null,
    val iconUrl: String? = null,
    val displayOrder: Int? = null,
    val partnerId: String? = null,
    val latestVersion: String? = null,
    val rolloutPercent: Int? = null,
    val bridgeVersion: String? = null,
    val permissions: JsonElement? = null
)
