package com.ir.tester.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

const val CURRENT_APP_VERSION = "2.1.1"

data class ReleaseHistoryItem(
    val tagName: String,
    val name: String,
    val changelog: String,
    val downloadUrl: String,
    val isCurrent: Boolean,
    val isNewer: Boolean,
    val isOlder: Boolean
)

@Composable
fun InfoScreen() {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isChecking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    var releasesList by remember { mutableStateOf<List<ReleaseHistoryItem>>(emptyList()) }
    var isLoadingReleases by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(0) }

    var downloadingTag by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var showDowngradeDialogFor by remember { mutableStateOf<ReleaseHistoryItem?>(null) }

    val pageSize = 10
    val totalPages = if (releasesList.isNotEmpty()) (releasesList.size + pageSize - 1) / pageSize else 1
    val pagedReleases = releasesList.drop(currentPage * pageSize).take(pageSize)

    fun loadAllReleases() {
        isLoadingReleases = true
        coroutineScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    fetchAllReleases()
                }
                releasesList = list
                currentPage = 0
                isLoadingReleases = false
            } catch (e: Exception) {
                isLoadingReleases = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadAllReleases()
    }

    fun startDownloadAndInstall(item: ReleaseHistoryItem) {
        if (item.downloadUrl.isBlank()) return
        downloadingTag = item.tagName
        downloadProgress = 0f
        downloadError = null

        coroutineScope.launch {
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    downloadApk(
                        context = context,
                        downloadUrl = item.downloadUrl,
                        onProgress = { prog, current, total ->
                            downloadProgress = prog
                            downloadedBytes = current
                            totalBytes = total
                        }
                    )
                }
                downloadingTag = null
                installApk(context, apkFile)
            } catch (e: Exception) {
                downloadingTag = null
                downloadError = "ошибка скачивания: ${e.message}"
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "IRBlaster",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "версия $CURRENT_APP_VERSION",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri("https://t.me/teffun") },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
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
        }

        item {
            Button(
                onClick = {
                    isChecking = true
                    checkResult = null
                    coroutineScope.launch {
                        try {
                            val list = withContext(Dispatchers.IO) {
                                fetchAllReleases()
                            }
                            releasesList = list
                            currentPage = 0
                            isChecking = false

                            val latest = list.firstOrNull()
                            if (latest != null) {
                                val cleanTag = latest.tagName.removePrefix("v").trim()
                                if (isVersionNewer(CURRENT_APP_VERSION, cleanTag)) {
                                    checkResult = "доступна новая версия: ${latest.tagName}"
                                } else {
                                    checkResult = "у вас установлена последняя версия ($CURRENT_APP_VERSION)"
                                }
                            } else {
                                checkResult = "нет данных"
                            }
                        } catch (e: Exception) {
                            isChecking = false
                            checkResult = "ошибка проверки"
                        }
                    }
                },
                enabled = !isChecking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
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
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            downloadError?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "история версий (${releasesList.size}):",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (isLoadingReleases) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        }

        if (releasesList.isEmpty() && !isLoadingReleases) {
            item {
                Text(
                    text = "список версий пуст или нет интернета",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(pagedReleases) { item ->
                val isDownloadingThis = downloadingTag == item.tagName
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.isCurrent)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.tagName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (item.isCurrent) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "текущая",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (item.changelog.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.changelog,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (isDownloadingThis) {
                            Column {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                val currentMb = String.format(Locale.US, "%.1f", downloadedBytes / 1048576.0)
                                val totalMb = if (totalBytes > 0) String.format(Locale.US, "%.1f", totalBytes / 1048576.0) else "?"
                                Text(
                                    text = "скачивание... ${(downloadProgress * 100).toInt()}% ($currentMb МБ / $totalMb МБ)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            if (item.isCurrent) {
                                OutlinedButton(
                                    onClick = { startDownloadAndInstall(item) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("переустановить эту версию", fontWeight = FontWeight.Bold)
                                }
                            } else if (item.isNewer) {
                                Button(
                                    onClick = { startDownloadAndInstall(item) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("обновить до ${item.tagName}", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { showDowngradeDialogFor = item },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("скачать ${item.tagName} (старая)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (totalPages > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { if (currentPage > 0) currentPage-- },
                            enabled = currentPage > 0,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("назад", fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "${currentPage + 1} / $totalPages",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = { if (currentPage < totalPages - 1) currentPage++ },
                            enabled = currentPage < totalPages - 1,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("вперед", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDowngradeDialogFor != null) {
        val target = showDowngradeDialogFor!!
        AlertDialog(
            onDismissRequest = { showDowngradeDialogFor = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "откат на старую версию",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Android на системном уровне блокирует установку более старой версии поверх новой. Чтобы поставить ${target.tagName}, сначала удалите текущее приложение с телефона, затем запустите скачанный файл.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val itemToDownload = target
                        showDowngradeDialogFor = null
                        startDownloadAndInstall(itemToDownload)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("скачать APK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDowngradeDialogFor = null }) {
                    Text("отмена", fontWeight = FontWeight.SemiBold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

private fun fetchAllReleases(): List<ReleaseHistoryItem> {
    val url = URL("https://api.github.com/repos/supeston/IRBlaster/releases")
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.setRequestProperty("User-Agent", "IRBlaster-App")
    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
    connection.connectTimeout = 8000
    connection.readTimeout = 8000

    if (connection.responseCode != 200) {
        return emptyList()
    }

    val reader = BufferedReader(InputStreamReader(connection.inputStream))
    val response = reader.readText()
    reader.close()
    connection.disconnect()

    val jsonArray = JSONArray(response)
    val list = ArrayList<ReleaseHistoryItem>()

    for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        val rawTag = obj.optString("tag_name", "")
        val cleanTag = rawTag.removePrefix("v").trim()
        val name = obj.optString("name", rawTag)
        val body = obj.optString("body", "").trim()

        var downloadUrl = obj.optString("html_url", "")
        val assets = obj.optJSONArray("assets")
        if (assets != null && assets.length() > 0) {
            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    downloadUrl = asset.optString("browser_download_url", downloadUrl)
                    break
                }
            }
        }

        val cmp = compareVersions(cleanTag, CURRENT_APP_VERSION)
        val isCurrent = cmp == 0
        val isNewer = cmp > 0
        val isOlder = cmp < 0

        list.add(
            ReleaseHistoryItem(
                tagName = rawTag,
                name = name,
                changelog = body,
                downloadUrl = downloadUrl,
                isCurrent = isCurrent,
                isNewer = isNewer,
                isOlder = isOlder
            )
        )
    }

    return list
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

private fun compareVersions(v1: String, v2: String): Int {
    try {
        val p1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val p2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val c1 = if (i < p1.size) p1[i] else 0
            val c2 = if (i < p2.size) p2[i] else 0
            if (c1 > c2) return 1
            if (c1 < c2) return -1
        }
        return 0
    } catch (e: Exception) {
        return v1.compareTo(v2)
    }
}

private fun isVersionNewer(current: String, latest: String): Boolean {
    return compareVersions(latest, current) > 0
}
