package io.github.zyakusen.tsukiyo.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.zyakusen.tsukiyo.util.AppUpdater
import io.github.zyakusen.tsukiyo.util.UpdateInfo
import kotlinx.coroutines.launch
import java.io.File

/**
 * 更新弹窗（设置页手动检查 + 启动时自动检查共用一套）。
 *
 *  * 发行说明按 **Markdown 渲染**（`/releases/latest` 的 body 就是 Markdown 文本）；
 *  * 「下载并安装」在应用内下载（带进度），下完直接调起系统安装器；
 *  * 首次需要允许「安装未知应用」会引导过去，回来点「安装」即可（包已下好，不重复下载）；
 *  * 网络下不动 GitHub 时保留「浏览器打开」的退路。
 *
 * @param allowIgnore 启动时提示才给「不再提示」（设置页手动检查不给，用户主动点的就该看到）
 * @param onIgnore 记下"忽略这个版本"
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    allowIgnore: Boolean = false,
    onIgnore: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }

    // 下完先尝试直接装机；系统未授权「安装未知应用」时引导去授权，回来再点「安装」
    fun installOrAsk() {
        val apk = downloadedApk ?: return
        if (AppUpdater.canInstall(context)) {
            if (AppUpdater.install(context, apk)) {
                onDismiss()
            } else {
                Toast.makeText(context, "无法调起安装器", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "请先允许「安装未知应用」", Toast.LENGTH_LONG).show()
            AppUpdater.openInstallPermission(context)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!updating) onDismiss() },
        title = { Text("发现新版本 v${info.version}") },
        text = {
            Column {
                when {
                    updating -> {
                        Text("正在下载安装包… ${(progress * 100).toInt()}%")
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }

                    downloadedApk != null -> Text("安装包已下载完成，点「安装」继续。")

                    info.notes.isNotBlank() -> MarkdownText(
                        markdown = info.notes,
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    )

                    else -> Text("最新版本：v${info.version}")
                }
            }
        },
        confirmButton = {
            // 四个按钮（下载并安装 / 浏览器打开 / 不再提示 / 稍后）在窄弹窗里放不下一行，
            // 用 FlowRow 自动折到第二行；以前塞在 AlertDialog 的 confirm+dismiss 两个槽里会被挤到内容上方
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // 顺序（左→右）：🌐 / 不再提示（仅启动时） / 稍后 / 立即更新
                if (info.htmlUrl.isNotBlank()) {
                    IconButton(enabled = !updating, onClick = {
                        onDismiss()
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl)))
                        }
                    }) { Icon(Icons.Filled.Public, "用浏览器打开发行页") }
                }
                if (allowIgnore) {
                    TextButton(enabled = !updating, onClick = onIgnore) { Text("不再提示") }
                }
                TextButton(enabled = !updating, onClick = onDismiss) { Text("稍后") }
                TextButton(
                    enabled = !updating,
                    onClick = {
                        when {
                            downloadedApk != null -> installOrAsk()

                            // 兜底：Release 里没有 APK 资产时仍然跳发行页
                            info.apkUrl.isBlank() -> {
                                onDismiss()
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl)))
                                }
                            }

                            else -> scope.launch {
                                updating = true
                                progress = 0f
                                val f = AppUpdater.download(context, info.apkUrl, info.version) { progress = it }
                                updating = false
                                if (f == null) {
                                    Toast.makeText(context, "下载失败，请稍后重试", Toast.LENGTH_SHORT).show()
                                } else {
                                    downloadedApk = f
                                    installOrAsk()
                                }
                            }
                        }
                    }
                ) { Text(if (downloadedApk != null) "安装" else "立即更新") }
            }
        },
        dismissButton = {}
    )
}
