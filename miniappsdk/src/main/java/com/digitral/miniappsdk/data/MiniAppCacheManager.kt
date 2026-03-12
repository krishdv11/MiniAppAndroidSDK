package com.digitral.miniappsdk.data

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

    fun saveServices(services: List<MiniAppService>): Unit {
        val tempFile = File(rootDir, "${servicesFile.name}.tmp")
        tempFile.writeText(gson.toJson(services))
        if (!tempFile.renameTo(servicesFile)) {
            servicesFile.writeText(gson.toJson(services))
        }
    }

    fun getServices(): List<MiniAppService> {
        if (!servicesFile.exists()) return emptyList()
        val json = servicesFile.readText()
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<MiniAppService>>() {}.type
        return gson.fromJson<List<MiniAppService>>(json, type).orEmpty()
    }

    fun appDir(appId: String): File = File(rootDir, appId).apply { mkdirs() }

    fun zipFile(appId: String, artifactId: String): File = File(appDir(appId), "$artifactId.zip")

    fun extractionDir(appId: String, artifactId: String): File =
        File(appDir(appId), "unzipped-$artifactId").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

    fun unzip(zip: File, outputDir: File): Unit {
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

    fun findIndexHtml(appId: String): File? {
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
