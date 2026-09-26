package com.moonbench.bifrost.plugins

import com.moonbench.bifrost.io.BoundedInput
import android.content.SharedPreferences
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Retrieval + update logic for the plugin store. Zero-dependency networking
 * over HttpURLConnection (no OkHttp/Retrofit added). All calls BLOCK — invoke
 * off the main thread.
 */
object PluginRepository {

    private const val DEFAULT_TIMEOUT_MS = 10_000
    private const val MAX_BUNDLE_BYTES = 8 * 1024 * 1024   // 8 MB sanity cap

    sealed class CatalogResult {
        data class Success(val catalog: PluginCatalog) : CatalogResult()
        data class Failure(val message: String) : CatalogResult()
    }

    sealed class DownloadResult {
        data class Success(val file: File) : DownloadResult()
        data class Failure(val message: String) : DownloadResult()
    }

    /** Fetch + parse the catalogue. Never throws — folds errors into Failure. */
    fun fetchCatalog(url: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): CatalogResult =
        try {
            CatalogResult.Success(PluginCatalog.parse(httpGetText(url, timeoutMs)))
        } catch (e: CatalogParseException) {
            CatalogResult.Failure(e.message ?: "malformed catalogue")
        } catch (t: Exception) {
            CatalogResult.Failure("could not reach catalogue: ${t.message}")
        }

    /**
     * Of the catalogue's plugins, those that are installed AND whose catalogue
     * version exceeds the installed version. The single source of "update
     * available" truth — used by the launch check and the store UI.
     */
    fun computeUpdates(catalog: PluginCatalog, prefs: SharedPreferences): List<PluginUpdate> =
        updatesFor(catalog, PluginPrefs.installedVersions(prefs))

    /** Pure update-detection core (no Android) — see [computeUpdates]. */
    fun updatesFor(catalog: PluginCatalog, installed: Map<String, Int>): List<PluginUpdate> =
        catalog.plugins.mapNotNull { entry ->
            val iv = installed[entry.id] ?: return@mapNotNull null
            if (entry.version > iv) PluginUpdate(entry, iv) else null
        }

    /**
     * Download a plugin bundle to [dest]. Verifies SHA-256 if the entry carries
     * one. Never throws — folds errors into Failure (and deletes a partial /
     * mismatched file).
     */
    fun downloadBundle(
        entry: CatalogEntry,
        dest: File,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): DownloadResult {
        return try {
            httpDownload(entry.bundleUrl, dest, timeoutMs)
            val expected = entry.bundleSha256
            if (expected != null) {
                val actual = sha256(dest)
                if (!actual.equals(expected, ignoreCase = true)) {
                    dest.delete()
                    return DownloadResult.Failure(
                        "integrity check failed (sha256 mismatch)")
                }
            }
            DownloadResult.Success(dest)
        } catch (t: Exception) {
            dest.delete()
            DownloadResult.Failure("download failed: ${t.message}")
        }
    }

    // ---- HTTP ------------------------------------------------------------

    private fun openGet(url: String, timeoutMs: Int): HttpURLConnection {
        require(timeoutMs in 1..60_000) { "Invalid network timeout" }
        var target = url
        for (redirect in 0..5) {
            require(CatalogEntry.isHttpsUrl(target)) { "Plugin sources must use HTTPS" }
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json, application/octet-stream")
                setRequestProperty("User-Agent", "Bifrost-PluginStore")
            }
            try {
                val code = conn.responseCode
                if (code in 200..299) return conn
                if (code !in listOf(301, 302, 303, 307, 308)) throw IOException("HTTP $code")
                val location = conn.getHeaderField("Location") ?: throw IOException("Missing redirect location")
                target = URL(URL(target), location).toString()
            } catch (e: Exception) {
                conn.disconnect()
                throw e
            }
            conn.disconnect()
        }
        throw IOException("Too many redirects")
    }

    private fun readDeadline(): () -> Unit {
        val deadline = System.nanoTime() + 60_000_000_000L
        return {
            if (Thread.currentThread().isInterrupted || System.nanoTime() >= deadline)
                throw IOException("Download cancelled or timed out")
        }
    }

    private fun httpGetText(url: String, timeoutMs: Int): String {
        val conn = openGet(url, timeoutMs)
        try {
            return conn.inputStream.use { BoundedInput.read(it, 512 * 1024, readDeadline()) }
                .toString(Charsets.UTF_8)
        } finally { conn.disconnect() }
    }

    private fun httpDownload(url: String, dest: File, timeoutMs: Int) {
        val conn = openGet(url, timeoutMs)
        try {
            val parent = dest.parentFile
            if (parent != null && !parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create download directory")
            conn.inputStream.use { input ->
                dest.outputStream().use { output -> BoundedInput.copy(input, output, MAX_BUNDLE_BYTES, readDeadline()) }
            }
        } finally { conn.disconnect() }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
