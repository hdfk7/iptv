package cn.hdfk7.android.iptv.feature.update

import android.content.Context
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cn.hdfk7.android.iptv.BuildConfig
import cn.hdfk7.android.iptv.R
import cn.hdfk7.android.iptv.core.data.network.PlaylistDownloader
import cn.hdfk7.android.iptv.core.ui.components.TvIconButton
import cn.hdfk7.android.iptv.core.ui.theme.DialogSurface
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

class AppUpdateFileProvider : FileProvider()

private data class AppRelease(val versionCode: Int, val versionName: String, val downloadUrl: String)

private fun readAppRelease(address: String): AppRelease {
    val manifestUrl = address.toHttpUrl()
    require(manifestUrl.isHttps)
    val manifest = JSONObject(PlaylistDownloader().download(manifestUrl.toString()))
    val versionCode = manifest.get("versionCode") as? Int ?: error("Expected an integer versionCode")
    val versionName = manifest.getString("versionName")
    val downloadUrl = requireNotNull(manifestUrl.resolve(manifest.getString("apkUrl")))
    require(versionCode > 0 && versionName.isNotBlank() && downloadUrl.isHttps && downloadUrl != manifestUrl)
    return AppRelease(versionCode, versionName, downloadUrl.toString())
}

private suspend fun downloadApk(context: Context, release: AppRelease): File = withContext(context = Dispatchers.IO, block = {
    val directory = File(context.cacheDir, "updates")
    check(directory.isDirectory || directory.mkdirs())
    val pending = File.createTempFile("iptv-", ".apk", directory)
    try {
        val client = OkHttpClient.Builder().followSslRedirects(false).build()
        try {
            client.newCall(Request.Builder().url(release.downloadUrl).build()).execute().use(block = { response ->
                check(response.isSuccessful)
                val body = checkNotNull(response.body)
                body.byteStream().use(block = { input ->
                    pending.outputStream().use(block = { output ->
                        val buffer = ByteArray(8192)
                        var count = input.read(buffer)
                        while (count != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, count)
                            count = input.read(buffer)
                        }
                    })
                })
            })
        } finally {
            client.connectionPool.evictAll()
        }
        val archive = checkNotNull(context.packageManager.getPackageArchiveInfo(pending.path, 0))
        @Suppress("DEPRECATION")
        check(archive.packageName == context.packageName && archive.versionCode == release.versionCode)
        coroutineContext.ensureActive()
        val apk = File(directory, "iptv-update.apk")
        check(pending.renameTo(apk))
        apk
    } finally {
        pending.delete()
    }
})

private fun openInstaller(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(intent)
}

@Composable
fun AppUpdateDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val address = BuildConfig.APP_UPDATE_MANIFEST_URL
    val closeFocus = remember(calculation = { FocusRequester() })
    var result by remember(calculation = { mutableStateOf<Result<AppRelease>?>(null) })
    val scope = rememberCoroutineScope()
    var downloading by remember(calculation = { mutableStateOf(false) })
    var downloadedApk by remember(calculation = { mutableStateOf<File?>(null) })
    var actionMessage by remember(calculation = { mutableStateOf<Int?>(null) })
    val install: (File) -> Unit = { apk ->
        actionMessage = try {
            openInstaller(context, apk)
            R.string.app_update_install_ready
        } catch (_: RuntimeException) {
            R.string.app_update_install_failed
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult(), onResult = {
        val apk = downloadedApk
        if (apk != null && context.packageManager.canRequestPackageInstalls()) {
            install(apk)
        } else {
            actionMessage = R.string.app_update_allow_install
        }
    })
    val requestInstall: (File) -> Unit = { apk ->
        if (context.packageManager.canRequestPackageInstalls()) {
            install(apk)
        } else {
            actionMessage = R.string.app_update_allow_install
            try {
                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            } catch (_: RuntimeException) {
                actionMessage = R.string.app_update_install_failed
            }
        }
    }
    LaunchedEffect(key1 = address, block = {
        if (address.isNotBlank()) {
            result = withContext(context = Dispatchers.IO, block = { runCatching { readAppRelease(address) } })
        }
    })
    val latest = result?.getOrNull()
    val update = latest?.takeIf(predicate = { release -> release.versionCode > BuildConfig.VERSION_CODE })
    val message = when {
        downloading -> stringResource(R.string.app_update_downloading)
        actionMessage != null -> stringResource(checkNotNull(actionMessage))
        address.isBlank() -> stringResource(R.string.app_update_unconfigured)
        result == null -> stringResource(R.string.app_update_checking)
        latest == null -> stringResource(R.string.app_update_failed)
        update == null -> stringResource(R.string.app_update_current, BuildConfig.VERSION_NAME)
        else -> stringResource(R.string.app_update_available, update.versionName)
    }
    Dialog(onDismissRequest = onClose, content = {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
                .background(DialogSurface, RoundedCornerShape(22.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = {
                Text(text = stringResource(R.string.app_update_title), fontSize = 22.sp)
                Text(text = message)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = {
                    TvIconButton(
                        icon = R.drawable.ic_close,
                        description = stringResource(R.string.app_update_close),
                        onClick = onClose,
                        modifier = Modifier.focusRequester(closeFocus)
                    )
                    if (update != null && !downloading) {
                        TvIconButton(
                            icon = if (downloadedApk == null) R.drawable.ic_update else R.drawable.ic_check,
                            description = stringResource(if (downloadedApk == null) R.string.app_update_confirm_download else R.string.app_update_install),
                            onClick = {
                                val apk = downloadedApk
                                if (apk != null) {
                                    requestInstall(apk)
                                } else {
                                    downloading = true
                                    actionMessage = null
                                    scope.launch(block = {
                                        try {
                                            val file = downloadApk(context, update)
                                            downloadedApk = file
                                            requestInstall(file)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            actionMessage = R.string.app_update_download_failed
                                        } finally {
                                            downloading = false
                                        }
                                    })
                                }
                            }
                        )
                    }
                })
                LaunchedEffect(key1 = downloading, block = { closeFocus.requestFocus() })
            }
        )
    })
}
