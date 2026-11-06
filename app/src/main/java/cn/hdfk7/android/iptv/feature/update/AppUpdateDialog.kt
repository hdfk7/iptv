package cn.hdfk7.android.iptv.feature.update

import android.content.Context
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cn.hdfk7.android.iptv.BuildConfig
import cn.hdfk7.android.iptv.R
import cn.hdfk7.android.iptv.core.data.network.PlaylistDownloader
import cn.hdfk7.android.iptv.core.ui.components.TvIconButton
import cn.hdfk7.android.iptv.core.ui.theme.Accent
import cn.hdfk7.android.iptv.core.ui.theme.DialogSurface
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

class AppUpdateFileProvider : FileProvider()

internal data class AppRelease(val versionCode: Int, val versionName: String, val downloadUrl: String)

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
    val downloadProgress = remember(calculation = { MutableStateFlow<AppDownloadProgress?>(null) })
    val progress by downloadProgress.collectAsState()
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
                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
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
    val current = progress
    val fraction = current?.fraction
    val message = when {
        downloading && current != null && fraction != null -> stringResource(R.string.app_update_progress, Formatter.formatFileSize(context, current.downloaded), Formatter.formatFileSize(context, current.total), (fraction * 100).toInt())
        downloading && current != null -> stringResource(R.string.app_update_progress_size, Formatter.formatFileSize(context, current.downloaded))
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
                Text(text = stringResource(R.string.app_update_title), color = Color.White, fontSize = 22.sp)
                Text(text = message, color = Accent, fontSize = 15.sp)
                if (downloading) {
                    if (fraction == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    }
                }
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
                                    downloadProgress.value = null
                                    scope.launch(block = {
                                        try {
                                            val file = downloadApk(context.applicationContext, update, onProgress = { status ->
                                                downloadProgress.value = status
                                            })
                                            downloadedApk = file
                                            requestInstall(file)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: AppUpdateDownloadException) {
                                            actionMessage = failure.messageRes
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
