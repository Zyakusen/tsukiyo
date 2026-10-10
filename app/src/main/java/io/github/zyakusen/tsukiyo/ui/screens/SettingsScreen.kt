package io.github.zyakusen.tsukiyo.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.zyakusen.tsukiyo.data.SettingsStore
import io.github.zyakusen.tsukiyo.data.api.NetworkModule
import io.github.zyakusen.tsukiyo.ui.LocalContainer
import io.github.zyakusen.tsukiyo.ui.components.MarkdownText
import io.github.zyakusen.tsukiyo.ui.components.TopBar
import io.github.zyakusen.tsukiyo.util.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/** 检查更新拿到的信息：版本号、发行说明、APK 直链（拿不到 APK 时退回发行页）。 */
private data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val htmlUrl: String
)

@Composable
fun SettingsScreen(navController: NavHostController) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsStore.state.collectAsState()
    val auth by container.authManager.state.collectAsState()

    var showChangePassword by remember { mutableStateOf(false) }
    var showProxy by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    // 应用内更新：下载中 / 进度 / 已下载好的安装包
    var updating by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(0f) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }

    val exportDirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                container.settingsStore.exportDirUri = uri.toString()
                Toast.makeText(context, "已设置导出目录", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "设置失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val currentVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0.0"
    }

    fun checkUpdate() {
        scope.launch {
            checkingUpdate = true
            val info = withContext(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder()
                        .url("https://api.github.com/repos/Zyakusen/tsukiyo/releases/latest")
                        .header("User-Agent", "tsukiyo")
                        .build()
                    NetworkModule.downloadClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) null else {
                            val json = JSONObject(resp.body?.string() ?: "")
                            val tag = json.optString("tag_name").removePrefix("v")
                            if (tag.isBlank()) null else {
                                // Release 里的 APK 资产直链（应用内下载用）
                                var apk = ""
                                val assets = json.optJSONArray("assets")
                                if (assets != null) {
                                    for (i in 0 until assets.length()) {
                                        val a = assets.getJSONObject(i)
                                        if (a.optString("name").endsWith(".apk", true)) {
                                            apk = a.optString("browser_download_url")
                                            break
                                        }
                                    }
                                }
                                UpdateInfo(
                                    version = tag,
                                    notes = json.optString("body"),
                                    apkUrl = apk,
                                    htmlUrl = json.optString("html_url")
                                )
                            }
                        }
                    }
                }.getOrNull()
            }
            checkingUpdate = false
            if (info == null) {
                Toast.makeText(context, "检查更新失败", Toast.LENGTH_SHORT).show()
            } else if (compareVersions(info.version, currentVersion) > 0) {
                downloadedApk = null
                updateProgress = 0f
                updateInfo = info
            } else {
                Toast.makeText(context, "已是最新版", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopBar("设置", onBack = { navController.popBackStack() })

        SectionTitle("账号")
        Row(
            Modifier.fillMaxWidth().clickable { navController.navigate(io.github.zyakusen.tsukiyo.ui.navigation.Routes.LOGIN) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (auth.isRealUser) "当前用户：${auth.userName}" else "点击登录 / 注册",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (auth.isRealUser) {
            Row(
                Modifier.fillMaxWidth().clickable { showChangePassword = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("修改密码", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionTitle("外观")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            items(listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")) { (k, label) ->
                SelectableChip(label, settings.themeMode == k) {
                    container.settingsStore.themeMode = k
                }
            }
        }

        SectionTitle("网络")
        SettingRow("使用代理") {
            Switch(
                checked = settings.proxyEnabled,
                onCheckedChange = { container.settingsStore.proxyEnabled = it; container.applyProxySettings() }
            )
        }
        if (settings.proxyEnabled) {
            Row(
                Modifier.fillMaxWidth().clickable { showProxy = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (settings.proxyServer.isBlank()) "配置代理" else "${settings.proxyProtocol.uppercase()} ${settings.proxyServer}:${settings.proxyPort}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionTitle("播放")
        SettingRow("音频质量") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("流畅", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = settings.audioQuality == SettingsStore.QUALITY_HIGH,
                    onCheckedChange = { container.settingsStore.audioQuality = if (it) SettingsStore.QUALITY_HIGH else SettingsStore.QUALITY_LOW }
                )
                Spacer(Modifier.width(12.dp))
                Text("高音质", style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionTitle("跳转时长")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            items(listOf(5, 10, 15, 30, 60)) { s ->
                SelectableChip("前进 ${s} 秒", settings.forwardSeekSeconds == s) {
                    container.settingsStore.forwardSeekSeconds = s
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            items(listOf(5, 10, 15, 30, 60)) { s ->
                SelectableChip("后退 ${s} 秒", settings.rewindSeekSeconds == s) {
                    container.settingsStore.rewindSeekSeconds = s
                }
            }
        }

        SectionTitle("智能路径")
        SettingRow("智能路径") {
            Switch(
                checked = settings.smartPathEnabled,
                onCheckedChange = { container.settingsStore.smartPathEnabled = it }
            )
        }
        Text(
            "效果音偏好",
            style = MaterialTheme.typography.bodyLarge,
            color = if (settings.smartPathEnabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(
                listOf(
                    SettingsStore.SE_MODE_ANY to "不限",
                    SettingsStore.SE_MODE_WITH to "优先含 SE",
                    SettingsStore.SE_MODE_WITHOUT to "优先无 SE"
                )
            ) { (key, label) ->
                SelectableChip(label, settings.seMode == key, enabled = settings.smartPathEnabled) {
                    container.settingsStore.seMode = key
                }
            }
        }
        Text(
            "先确保进入正片目录，再按此偏好选择含/不含效果音的目录；未标注的作品默认视为含效果音。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
        )
        Text(
            "音频类型偏好顺序",
            style = MaterialTheme.typography.bodyLarge,
            color = if (settings.smartPathEnabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(settings.audioTypeOrder) { type ->
                val idx = settings.audioTypeOrder.indexOf(type)
                SelectableChip("${idx + 1}. ${type.uppercase()}", idx == 0, enabled = settings.smartPathEnabled) {
                    val newOrder = listOf(type) + settings.audioTypeOrder.filter { it != type }
                    container.settingsStore.audioTypeOrder = newOrder
                }
            }
        }
        Text(
            "含/不含效果音确定后，再按此顺序选格式；以音频文件的真实扩展名为准，与目录名无关。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
        )

        SectionTitle("内容")
        SettingRow("显示 NSFW 封面") {
            Switch(
                checked = settings.showNsfw,
                onCheckedChange = { container.settingsStore.showNsfw = it }
            )
        }
        SettingRow("仅显示带字幕作品") {
            Switch(
                checked = settings.subtitlesOnly,
                onCheckedChange = { container.settingsStore.subtitlesOnly = it }
            )
        }

        SectionTitle("首页")
        SettingRow("显示热门作品") {
            Switch(
                checked = settings.showPopularAtHome,
                onCheckedChange = { container.settingsStore.showPopularAtHome = it }
            )
        }
        SettingRow("显示推荐作品") {
            Switch(
                checked = settings.showRecommendAtHome,
                onCheckedChange = { container.settingsStore.showRecommendAtHome = it }
            )
        }

        SectionTitle("默认排序")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            items(sortOptions) { option ->
                SelectableChip(option.label, settings.defaultSort == option.key) {
                    container.settingsStore.defaultSort = option.key
                }
            }
        }

        SectionTitle("标签语言")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            items(io.github.zyakusen.tsukiyo.util.tagLanguageOptions) { (lang, label) ->
                SelectableChip(label, settings.tagLanguage == lang) {
                    container.settingsStore.tagLanguage = lang
                }
            }
        }

        SectionTitle("下载")
        Row(
            Modifier.fillMaxWidth().clickable { exportDirLauncher.launch(null) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (settings.exportDirUri.isBlank()) "导出目录：未设置" else "导出目录：${exportDirDisplayName(settings.exportDirUri)}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionTitle("API 镜像")
        SettingsStore.MIRRORS.forEach { mirror ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    container.switchBaseUrl(mirror)
                    Toast.makeText(context, "已切换到 $mirror", Toast.LENGTH_SHORT).show()
                }.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = settings.baseUrl == mirror, onClick = {
                    container.switchBaseUrl(mirror)
                    Toast.makeText(context, "已切换到 $mirror", Toast.LENGTH_SHORT).show()
                })
                Text(mirror, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        SectionTitle("缓存")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("清理缓存（图片与接口缓存）", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                scope.launch {
                    val freed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        container.clearCache(context)
                    }
                    val text = if (freed > 0) "已清理 ${io.github.zyakusen.tsukiyo.util.formatSize(freed)}" else "缓存已清理"
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            }) { Text("清理") }
        }

        SectionTitle("检查更新")
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !checkingUpdate) { checkUpdate() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("检查更新", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (checkingUpdate) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionTitle("关于")
        Text(
            "Tsukiyo（月夜）v$currentVersion\n数据来源：asmr.one\n仅供个人学习交流使用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(24.dp))
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false },
            onSubmit = { old, new ->
                scope.launch {
                    runCatching { container.repository.changePassword(old, new) }
                        .onSuccess { Toast.makeText(context, "密码修改成功", Toast.LENGTH_SHORT).show(); showChangePassword = false }
                        .onFailure { Toast.makeText(context, "修改失败：${it.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        )
    }

    if (showProxy) {
        ProxyDialog(
            container = container,
            onDismiss = { showProxy = false }
        )
    }

    updateInfo?.let { info ->
        // 下载后先尝试直接装机；系统未授权「安装未知应用」时引导去授权，回来再点「安装」即可（包已下载好）
        fun installOrAsk() {
            val apk = downloadedApk ?: return
            if (AppUpdater.canInstall(context)) {
                if (AppUpdater.install(context, apk)) {
                    updateInfo = null
                    downloadedApk = null
                } else {
                    Toast.makeText(context, "无法调起安装器", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "请先允许「安装未知应用」", Toast.LENGTH_LONG).show()
                AppUpdater.openInstallPermission(context)
            }
        }

        AlertDialog(
            onDismissRequest = { if (!updating) { updateInfo = null; downloadedApk = null } },
            title = { Text("发现新版本 v${info.version}") },
            text = {
                Column {
                    when {
                        updating -> {
                            Text("正在下载安装包… ${(updateProgress * 100).toInt()}%")
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { updateProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        downloadedApk != null -> Text("安装包已下载完成，点「安装」继续。")
                        info.notes.isNotBlank() -> MarkdownText(
                            // GitHub Release 的说明是 Markdown 文本（不是文件），这里渲染它而不是显示源码
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
                TextButton(
                    enabled = !updating,
                    onClick = {
                        when {
                            downloadedApk != null -> installOrAsk()
                            // 兜底：Release 里没有 APK 资产时仍然跳发行页
                            info.apkUrl.isBlank() -> {
                                updateInfo = null
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl))) }
                            }
                            else -> scope.launch {
                                updating = true
                                updateProgress = 0f
                                val f = AppUpdater.download(context, info.apkUrl, info.version) { updateProgress = it }
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
                ) { Text(if (downloadedApk != null) "安装" else "下载并安装") }
            },
            dismissButton = {
                Row {
                    // 网络下不动 GitHub 时的退路：仍然可以用浏览器打开发行页
                    if (info.htmlUrl.isNotBlank()) {
                        TextButton(enabled = !updating, onClick = {
                            updateInfo = null
                            downloadedApk = null
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl))) }
                        }) { Text("浏览器打开") }
                    }
                    TextButton(enabled = !updating, onClick = { updateInfo = null; downloadedApk = null }) {
                        Text("稍后")
                    }
                }
            }
        )
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    val context = LocalContext.current
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column {
                OutlinedTextField(old, { old = it }, label = { Text("当前密码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(new, { new = it }, label = { Text("新密码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(confirm, { confirm = it }, label = { Text("确认新密码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (new.length < 5) { Toast.makeText(context, "密码长度至少 5 位", Toast.LENGTH_SHORT).show(); return@TextButton }
                if (new != confirm) { Toast.makeText(context, "两次密码不一致", Toast.LENGTH_SHORT).show(); return@TextButton }
                onSubmit(old, new)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ProxyDialog(container: io.github.zyakusen.tsukiyo.data.AppContainer, onDismiss: () -> Unit) {
    val settings by container.settingsStore.state.collectAsState()
    var protocol by remember { mutableStateOf(settings.proxyProtocol) }
    var server by remember { mutableStateOf(settings.proxyServer) }
    var port by remember { mutableStateOf(settings.proxyPort.toString()) }
    var username by remember { mutableStateOf(settings.proxyUsername) }
    var password by remember { mutableStateOf(settings.proxyPassword) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("代理设置") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableChip("HTTP", protocol == "http") { protocol = "http" }
                    SelectableChip("SOCKS", protocol == "socks") { protocol = "socks" }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(server, { server = it }, label = { Text("服务器") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(port, { port = it }, label = { Text("端口") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(username, { username = it }, label = { Text("用户名（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(password, { password = it }, label = { Text("密码（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                container.settingsStore.proxyProtocol = protocol
                container.settingsStore.proxyServer = server.trim()
                container.settingsStore.proxyPort = port.trim().toIntOrNull() ?: 0
                container.settingsStore.proxyUsername = username.trim()
                container.settingsStore.proxyPassword = password
                container.applyProxySettings()
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        content()
    }
}

/** 按「主.次.修订」逐段数值比较，a > b 返回正数、相等返回 0、小于返回负数。 */
private fun compareVersions(a: String, b: String): Int {
    // 每段只取开头的数字，保证 "1.3.1-beta" 也能正确比出 1.3.1
    val pa = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val pb = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return 0
}

/** 从 SAF 目录 URI 解析出可读的路径显示（如 primary:Download/ASMR → Download/ASMR）。 */
private fun exportDirDisplayName(uri: String): String {
    val docId = uri.substringAfterLast('/')
    val decoded = Uri.decode(docId)
    val name = decoded.substringAfterLast(':')
    return if (name.isBlank()) "已设置" else name
}
