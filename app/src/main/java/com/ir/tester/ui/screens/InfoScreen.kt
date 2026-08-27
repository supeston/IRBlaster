package com.ir.tester.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

const val CURRENT_APP_VERSION = "1.2.1"

data class ReleaseInfo(
    val tagName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val isNewer: Boolean
)

@Composable
fun InfoScreen() {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isChecking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    var availableRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    fun checkUpdates() {
        isChecking = true
        checkResult = null
        coroutineScope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    fetchLatestRelease()
                }
                isChecking = false
                if (release != null) {
                    if (release.isNewer) {
                        availableRelease = release
                        showDialog = true
                    } else {
                        checkResult = "у вас установлена самая свежая версия ($CURRENT_APP_VERSION)"
                    }
                } else {
                    checkResult = "нет данных об обновлениях"
                }
            } catch (e: Exception) {
                isChecking = false
                checkResult = "ошибка проверки (проверьте интернет)"
            }
        }
    }

    fun startDownloadAndInstall(release: ReleaseInfo) {
        isDownloading = true
        downloadProgress = 0f
        downloadError = null

        coroutineScope.launch {
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    downloadApk(
                        context = context,
                        downloadUrl = release.downloadUrl,
                        onProgress = { prog, current, total ->
                            downloadProgress = prog
                            downloadedBytes = current
                            totalBytes = total
                        }
                    )
                }
                isDownloading = false
                showDialog = false

                // Launch Android Package Installer
                installApk(context, apkFile)
            } catch (e: Exception) {
                isDownloading = false
                downloadError = "ошибка скачивания: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "IRBlaster",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "версия $CURRENT_APP_VERSION",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { uriHandler.openUri("https://t.me/teffun") },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "автор: ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "@teffun",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = { checkUpdates() },
            enabled = !isChecking,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "проверка...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "проверить обновления",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        AnimatedVisibility(
            visible = checkResult != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            checkResult?.let { msg ->
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showDialog && availableRelease != null) {
        val rel = availableRelease!!
        AlertDialog(
            onDismissRequest = {
                if (!isDownloading) showDialog = false
            },
            title = {
                Text(
                    text = "доступно обновление!",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    Text(
                        text = "найдена новая версия: ${rel.tagName}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "текущая версия: $CURRENT_APP_VERSION",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(18.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val currentMb = String.format(Locale.US, "%.1f", downloadedBytes / 1048576.0)
                        val totalMb = if (totalBytes > 0) String.format(Locale.US, "%.1f", totalBytes / 1048576.0) else "?"
                        Text(
                            text = "скачивание... ${(downloadProgress * 100).toInt()}% ($currentMb МБ / $totalMb МБ)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (downloadError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = downloadError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (rel.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = rel.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                if (!isDownloading) {
                    Button(
                        onClick = { startDownloadAndInstall(rel) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("скачать и установить", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { showDialog = false }) {
                        Text("позже", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

private fun fetchLatestRelease(): ReleaseInfo? {
    val url = URL("https://api.github.com/repos/supeston/IRBlaster/releases/latest")
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.setRequestProperty("User-Agent", "IRBlaster-App")
    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
    connection.connectTimeout = 8000
    connection.readTimeout = 8000

    if (connection.responseCode != 200) {
        return null
    }

    val reader = BufferedReader(InputStreamReader(connection.inputStream))
    val response = reader.readText()
    reader.close()
    connection.disconnect()

    val json = JSONObject(response)
    val rawTag = json.optString("tag_name", "v1.0.0")
    val cleanTag = rawTag.removePrefix("v").trim()
    val releaseNotes = json.optString("body", "")

    var downloadUrl = json.optString("html_url", "https://github.com/supeston/IRBlaster/releases")
    val assets = json.optJSONArray("assets")
    if (assets != null && assets.length() > 0) {
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true)) {
                downloadUrl = asset.optString("browser_download_url", downloadUrl)
                break
            }
        }
    }

    val isNewer = isVersionNewer(CURRENT_APP_VERSION, cleanTag)
    return ReleaseInfo(
        tagName = rawTag,
        downloadUrl = downloadUrl,
        releaseNotes = releaseNotes,
        isNewer = isNewer
    )
}

private fun downloadApk(
    context: Context,
    downloadUrl: String,
    onProgress: (Float, Long, Long) -> Unit
): File {
    var targetUrl = downloadUrl
    var connection = URL(targetUrl).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.setRequestProperty("User-Agent", "IRBlaster-App")
    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    connection.instanceFollowRedirects = true

    var status = connection.responseCode
    // Handle GitHub redirects (302/301 -> AWS S3 download)
    if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
        val newUrl = connection.getHeaderField("Location")
        if (!newUrl.isNullOrBlank()) {
            connection.disconnect()
            connection = URL(newUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "IRBlaster-App")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            status = connection.responseCode
        }
    }

    if (status != HttpURLConnection.HTTP_OK) {
        throw RuntimeException("Ошибка сервера: $status")
    }

    val fileLength = connection.contentLength.toLong()
    val apkFile = File(context.cacheDir, "IRBlaster_update.apk")
    if (apkFile.exists()) {
        apkFile.delete()
    }

    val input = BufferedInputStream(connection.inputStream)
    val output = FileOutputStream(apkFile)
    val buffer = ByteArray(8192)
    var totalRead = 0L
    var count: Int

    while (input.read(buffer).also { count = it } != -1) {
        totalRead += count
        output.write(buffer, 0, count)
        if (fileLength > 0) {
            onProgress(totalRead.toFloat() / fileLength.toFloat(), totalRead, fileLength)
        } else {
            onProgress(0.5f, totalRead, 0L)
        }
    }

    output.flush()
    output.close()
    input.close()
    connection.disconnect()

    return apkFile
}

private fun installApk(context: Context, apkFile: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
    )

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    context.startActivity(intent)
}

private fun isVersionNewer(current: String, latest: String): Boolean {
    try {
        val currParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(currParts.size, latestParts.size)
        for (i in 0 until maxLen) {
            val c = if (i < currParts.size) currParts[i] else 0
            val l = if (i < latestParts.size) latestParts[i] else 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    } catch (e: Exception) {
        return latest != current
    }
}
