package com.meizzo.controller

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
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
    val prerelease: Boolean,
    val versionName: String,
    val versionCode: Long,
    val assetSize: Long,
    val expectedSha256: String,
    val signingCertificateSha256: String
)

class ControllerUpdateManager(private val context: Context) {
    private val allowedUpdateHosts = setOf(
        "api.github.com",
        "github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com"
    )
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        // Validate every network hop, including redirects, not just the initial URL.
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            require(url.isHttps && url.host in allowedUpdateHosts) {
                "Update request redirected to an untrusted host"
            }
            chain.proceed(chain.request())
        }
        .build()
    private val packageName = "com.meizzo.controller"
    private val appKey = "controller"
    private val apiUrl = "https://api.github.com/repos/rakhan17/Meizzo/releases?per_page=30"
    private val signingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    fun check(onResult: (Result<ControllerReleaseInfo?>) -> Unit) {
        Thread {
            runCatching {
                val currentPackage = packageInfo()
                val currentCode = appVersionCode(currentPackage)
                val betaChannel = BuildConfig.VERSION_NAME.contains("-")
                val releasesBody = executeText(apiUrl, "application/vnd.github+json")
                val releases = org.json.JSONArray(releasesBody)

                var bestRelease: ControllerReleaseInfo? = null
                for (index in 0 until releases.length()) {
                    val release = releases.getJSONObject(index)
                    if (release.optBoolean("draft")) continue
                    val prerelease = release.optBoolean("prerelease")
                    if (prerelease != betaChannel) continue

                    val tag = release.optString("tag_name")
                    if (tag.isBlank()) continue
                    val assets = release.optJSONArray("assets") ?: continue
                    var manifestUrl: String? = null
                    val assetUrls = mutableMapOf<String, Pair<String, Long>>()
                    for (assetIndex in 0 until assets.length()) {
                        val asset = assets.getJSONObject(assetIndex)
                        val name = asset.optString("name")
                        val url = asset.optString("browser_download_url")
                        val size = asset.optLong("size", -1L)
                        if (name == "MEIZZO-UPDATE.json") {
                            manifestUrl = url
                        } else if (name.isNotBlank()) {
                            assetUrls[name] = url to size
                        }
                    }
                    val metadataUrl = manifestUrl ?: continue
                    requireHttps(metadataUrl)
                    val manifest = JSONObject(executeText(metadataUrl, maxBytes = 128L * 1024L))
                    require(manifest.optInt("schemaVersion") == 1) { "Unsupported update manifest schema" }
                    require(manifest.optString("tag") == tag) { "Release tag does not match update manifest" }
                    val expectedChannel = if (prerelease) "beta" else "stable"
                    require(manifest.optString("channel") == expectedChannel) { "Release channel mismatch" }

                    val app = manifest.optJSONObject("apps")?.optJSONObject(appKey)
                        ?: error("Update manifest does not contain the $appKey application")
                    require(app.optString("packageName") == packageName) { "Update manifest package ID mismatch" }
                    val versionName = app.optString("versionName")
                    require(versionName == tag.removePrefix("v")) { "Version name does not match release tag" }
                    val versionCode = app.optLong("versionCode", -1L)
                    if (versionCode <= currentCode) continue
                    require(versionCode > 0L && versionCode <= Int.MAX_VALUE) { "Invalid Android versionCode" }
                    require(manifest.optLong("versionCode", versionCode) == versionCode) {
                        "Manifest versionCode mismatch"
                    }
                    require(manifest.optString("versionName") == versionName) {
                        "Manifest versionName mismatch"
                    }

                    val assetName = app.optString("assetName")
                    require(Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.apk").matches(assetName)) {
                        "Invalid APK asset name"
                    }
                    val asset = assetUrls[assetName] ?: error("Release is missing $assetName")
                    val assetUrl = asset.first
                    requireHttps(assetUrl)
                    val assetSize = app.optLong("assetSize", -1L)
                    require(assetSize in 1L..(512L * 1024L * 1024L)) { "Invalid APK size" }
                    require(asset.second == assetSize) { "APK size does not match release metadata" }

                    val sha256 = app.optString("sha256").lowercase(Locale.ROOT)
                    val certSha256 = app.optString("signingCertificateSha256").lowercase(Locale.ROOT)
                    require(sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid APK SHA-256 in manifest" }
                    require(certSha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid signing certificate digest" }

                    val candidate = ControllerReleaseInfo(
                        tag = tag,
                        notes = release.optString("body", "No release notes provided."),
                        publishedAt = release.optString("published_at"),
                        assetUrl = assetUrl,
                        assetName = assetName,
                        checksumUrl = null,
                        prerelease = prerelease,
                        versionName = versionName,
                        versionCode = versionCode,
                        assetSize = assetSize,
                        expectedSha256 = sha256,
                        signingCertificateSha256 = certSha256
                    )
                    if (bestRelease == null || candidate.versionCode > bestRelease!!.versionCode) {
                        bestRelease = candidate
                    }
                }
                bestRelease
            }.onSuccess { onResult(Result.success(it)) }
                .onFailure { onResult(Result.failure(it)) }
        }.start()
    }

    fun download(
        release: ControllerReleaseInfo,
        onProgress: (Int) -> Unit,
        onResult: (Result<File>) -> Unit
    ) {
        Thread {
            val partial = File(context.cacheDir, "updates/${release.assetName}.part")
            runCatching {
                requireHttps(release.assetUrl)
                require(release.assetSize > 0L) { "Invalid expected APK size" }
                val directory = partial.parentFile?.apply { mkdirs() }
                    ?: error("Unable to create update directory")
                val finalFile = File(directory, release.assetName)
                if (partial.exists()) partial.delete()

                val request = Request.Builder()
                    .url(release.assetUrl)
                    .header("Accept", "application/octet-stream")
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("APK download failed (${response.code})")
                    val body = response.body ?: error("APK download was empty")
                    val responseLength = body.contentLength()
                    if (responseLength >= 0L && responseLength != release.assetSize) {
                        error("APK content length does not match update manifest")
                    }
                    var downloaded = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(partial).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                downloaded += count
                                if (downloaded > release.assetSize) error("APK exceeds expected size")
                                output.write(buffer, 0, count)
                                onProgress(((downloaded * 100) / release.assetSize).toInt().coerceIn(0, 100))
                            }
                            output.fd.sync()
                        }
                    }
                    if (downloaded != release.assetSize) error("APK download is incomplete")
                }

                val actualHash = sha256(partial)
                require(actualHash == release.expectedSha256) { "APK SHA-256 verification failed" }

                val archive = context.packageManager.getPackageArchiveInfo(
                    partial.absolutePath,
                    signingFlags
                ) ?: error("Downloaded file is not a valid APK")
                require(archive.packageName == packageName) { "Downloaded APK package ID does not match $packageName" }
                require(appVersionCode(archive) == release.versionCode) { "Downloaded APK versionCode mismatch" }
                require(archive.versionName == release.versionName) { "Downloaded APK versionName mismatch" }

                val installed = packageInfo()
                val installedCert = signingCertificateSha256(installed)
                val downloadedCert = signingCertificateSha256(archive)
                require(downloadedCert == installedCert) {
                    "APK signing certificate does not match the installed application"
                }
                require(downloadedCert == release.signingCertificateSha256) {
                    "APK signing certificate does not match trusted release metadata"
                }

                if (finalFile.exists() && !finalFile.delete()) error("Unable to replace previous update file")
                if (!partial.renameTo(finalFile)) error("Unable to finalize verified APK download")
                finalFile
            }.onSuccess { onResult(Result.success(it)) }
                .onFailure {
                    partial.delete()
                    onResult(Result.failure(it))
                }
        }.start()
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallPackages()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun install(apk: File) {
        require(apk.exists() && apk.isFile) { "Verified update APK is missing" }
        if (!canInstallPackages()) {
            requestInstallPermission()
            return
        }
        val uri = FileProvider.getUriForFile(context, "$packageName.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun formatDate(value: String): String = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(value)?.let {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it)
        } ?: value
    }.getOrDefault(value)

    private fun executeText(
        url: String,
        accept: String? = null,
        maxBytes: Long = 2L * 1024L * 1024L
    ): String {
        requireHttps(url)
        val request = Request.Builder().url(url).apply {
            if (accept != null) header("Accept", accept)
        }.build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 403 || response.code == 429) {
                    error("GitHub API rate limit reached. Try again later.")
                }
                error("GitHub update request failed (${response.code})")
            }
            val body = response.body ?: error("GitHub returned an empty response")
            val bytes = body.source().readByteArray(maxBytes + 1L)
            require(bytes.isNotEmpty()) { "GitHub returned an empty response" }
            require(bytes.size.toLong() <= maxBytes) { "GitHub update response exceeded the allowed size" }
            bytes.toString(Charsets.UTF_8)
        }
    }

    private fun requireHttps(url: String) {
        require(Uri.parse(url).scheme == "https") { "Update URLs must use HTTPS" }
    }

    private fun packageInfo(): PackageInfo =
        context.packageManager.getPackageInfo(packageName, signingFlags)

    private fun appVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun signingCertificateSha256(info: PackageInfo): String {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: error("APK signing information is unavailable")
        require(signatures.isNotEmpty()) { "APK has no signing certificate" }
        val digests = signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.sorted()
        return digests.singleOrNull() ?: error("Multiple APK signers are not supported by this updater")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
