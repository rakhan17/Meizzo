package com.meizzo.controller

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ControllerReleaseInfo(
    val tag: String,
    val notes: String,
    val publishedAt: String,
    val assetUrl: String,
    val assetName: String,
    val checksumUrl: String?,
    val prerelease: Boolean
)

class ControllerUpdateManager(private val context: Context) {
    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val packageName = "com.meizzo.controller"

    fun check(onResult: (Result<ControllerReleaseInfo?>) -> Unit) {
        Thread {
            runCatching {
                val request = Request.Builder().url("https://api.github.com/repos/rakhan17/Meizzo/releases?per_page=20")
                    .header("Accept", "application/vnd.github+json").build()
                val body = http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("GitHub release lookup failed (${response.code})")
                    response.body?.string() ?: error("GitHub returned an empty release response")
                }
                val releases = org.json.JSONArray(body)
                val current = Version(BuildConfig.VERSION_NAME)
                for (index in 0 until releases.length()) {
                    val release = releases.getJSONObject(index)
                    val tag = release.optString("tag_name")
                    if (Version(tag.removePrefix("v")) <= current) continue
                    val assets = release.optJSONArray("assets") ?: continue
                    var apk: JSONObject? = null
                    var checksums: JSONObject? = null
                    for (assetIndex in 0 until assets.length()) {
                        val asset = assets.getJSONObject(assetIndex)
                        when (asset.optString("name")) {
                            "MEIZZO-Controller-$tag.apk" -> apk = asset
                            "SHA256SUMS.txt" -> checksums = asset
                        }
                    }
                    if (apk != null) return@runCatching ControllerReleaseInfo(
                        tag, release.optString("body", "No release notes provided."),
                        release.optString("published_at"), apk!!.optString("browser_download_url"),
                        apk!!.optString("name"), checksums?.optString("browser_download_url"),
                        release.optBoolean("prerelease")
                    )
                }
                null
            }.onSuccess { onResult(Result.success(it)) }
                .onFailure { onResult(Result.failure(it)) }
        }.start()
    }

    fun download(release: ControllerReleaseInfo, onProgress: (Int) -> Unit, onResult: (Result<File>) -> Unit) {
        Thread {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val apk = File(dir, release.assetName)
                val response = http.newCall(Request.Builder().url(release.assetUrl).build()).execute()
                response.use {
                    if (!it.isSuccessful) error("APK download failed (${it.code})")
                    val body = it.body ?: error("APK download was empty")
                    val total = body.contentLength()
                    var done = 0L
                    body.byteStream().use { input -> apk.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            done += count
                            if (total > 0) onProgress((done * 100 / total).toInt())
                        }
                    } }
                }
                val checksumUrl = release.checksumUrl ?: error("Release is missing SHA256SUMS.txt")
                val checksumText = http.newCall(Request.Builder().url(checksumUrl).build()).execute().use {
                    if (!it.isSuccessful) error("Checksum download failed (${it.code})")
                    it.body?.string() ?: error("Checksum file was empty")
                }
                val expected = checksumText.lineSequence().firstOrNull { it.trim().endsWith("  ${release.assetName}") }
                    ?.trim()?.substringBefore(' ')?.lowercase() ?: error("Checksum for ${release.assetName} was not found")
                if (sha256(apk) != expected) error("APK SHA-256 verification failed")
                val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                    ?: error("Downloaded file is not a valid APK")
                if (archive.packageName != packageName) error("Downloaded APK package ID does not match $packageName")
                val installed = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                if (signatures(archive) != signatures(installed)) {
                    error("APK signing certificate does not match the installed app")
                }
                apk
            }.onSuccess { onResult(Result.success(it)) }
                .onFailure { onResult(Result.failure(it)) }
        }.start()
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "$packageName.updates", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun formatDate(value: String): String = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(value)?.let {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it)
        } ?: value
    }.getOrDefault(value)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun signatures(info: android.content.pm.PackageInfo): Set<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        }

    private class Version(value: String) : Comparable<Version> {
        private val parts = value.split("-", limit = 2)
        private val numbers = parts[0].split('.').map { it.toIntOrNull() ?: 0 }
        private val prerelease = parts.getOrNull(1)
        override fun compareTo(other: Version): Int {
            for (index in 0 until maxOf(numbers.size, other.numbers.size)) {
                val result = (numbers.getOrNull(index) ?: 0).compareTo(other.numbers.getOrNull(index) ?: 0)
                if (result != 0) return result
            }
            if (prerelease == null && other.prerelease != null) return 1
            if (prerelease != null && other.prerelease == null) return -1
            return (prerelease ?: "").compareTo(other.prerelease ?: "")
        }
    }
}
