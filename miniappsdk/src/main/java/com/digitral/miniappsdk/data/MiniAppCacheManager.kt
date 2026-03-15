package com.digitral.miniappsdk.data

// Internal cache manager for SDK metadata and extracted mini app assets.

import android.content.Context
import com.digitral.miniappsdk.domain.model.MiniAppService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.zip.ZipFile

internal class MiniAppCacheManager(
    context: Context
) {
    private val gson = Gson()
    private val rootDir = File(context.cacheDir, "miniapps-cache").apply { mkdirs() }
    private val servicesFile = File(rootDir, "services.json")
    private val syncStateFile = File(rootDir, "sync-state.json")

    internal fun saveServices(services: List<MiniAppService>): Unit {
        val tempFile = File(rootDir, "${servicesFile.name}.tmp")
        tempFile.writeText(gson.toJson(services))
        if (!tempFile.renameTo(servicesFile)) {
            servicesFile.writeText(gson.toJson(services))
        }
    }

    internal fun getServices(): List<MiniAppService> {
        if (!servicesFile.exists()) return emptyList()
        val json = servicesFile.readText()
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<MiniAppService>>() {}.type
        return gson.fromJson<List<MiniAppService>>(json, type).orEmpty()
    }

    internal fun getSyncState(): MiniAppSyncState {
        if (!syncStateFile.exists()) return MiniAppSyncState()
        val json = syncStateFile.readText()
        if (json.isBlank()) return MiniAppSyncState()
        return gson.fromJson(json, MiniAppSyncState::class.java) ?: MiniAppSyncState()
    }

    internal fun saveSyncState(state: MiniAppSyncState): Unit {
        val tempFile = File(rootDir, "${syncStateFile.name}.tmp")
        tempFile.writeText(gson.toJson(state))
        if (!tempFile.renameTo(syncStateFile)) {
            syncStateFile.writeText(gson.toJson(state))
        }
    }

    internal fun appDir(appId: String): File = File(rootDir, appId).apply { mkdirs() }

    internal fun zipFile(appId: String, artifactId: String): File = File(appDir(appId), "$artifactId.zip")

    internal fun extractionDir(appId: String, artifactId: String): File =
        File(appDir(appId), "unzipped-$artifactId").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

    internal fun unzip(zip: File, outputDir: File): Unit {
        if (!outputDir.exists()) outputDir.mkdirs()
        val outputCanonicalPath = outputDir.canonicalPath + File.separator
        ZipFile(zip).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val out = File(outputDir, entry.name)
                val outCanonicalPath = out.canonicalPath
                require(outCanonicalPath.startsWith(outputCanonicalPath)) {
                    "Invalid zip entry path: ${entry.name}"
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        out.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    internal fun findIndexHtml(appId: String): File? {
        val appDir = appDir(appId)
        val candidate = appDir.walkTopDown().firstOrNull {
            it.isFile && it.name.equals("index.html", ignoreCase = true)
        }
        if (candidate != null) return candidate
        return appDir.walkTopDown().firstOrNull {
            it.isFile && (
                it.name.equals("index.htm", ignoreCase = true) ||
                    it.extension.equals("html", ignoreCase = true) ||
                    it.extension.equals("htm", ignoreCase = true)
                )
        }
    }
}

internal data class MiniAppSyncState(
    val authUpdatedDate: String? = null,
    val appVersions: Map<String, MiniAppVersionSnapshot> = emptyMap(),
    val appPermissions: Map<String, List<String>> = emptyMap()
)

internal data class MiniAppVersionSnapshot(
    val latestVersion: String? = null,
    val bridgeVersion: String? = null
)
