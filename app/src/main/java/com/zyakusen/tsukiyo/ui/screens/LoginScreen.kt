package com.zyakusen.tsukiyo.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.zyakusen.tsukiyo.ui.LocalContainer
import com.zyakusen.tsukiyo.ui.components.TopBar
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavHostController) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth by container.authManager.state.collectAsState()

    var isRegister by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopBar(if (isRegister) "注册" else "登录", onBack = { navController.popBackStack() })

        Column(Modifier.padding(24.dp)) {
            if (auth.isRealUser) {
                Text("当前登录：${auth.userName}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        container.repository.logout()
                        Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("退出登录") }
                return@Column
            }

            Text(
                "游客可免费浏览、搜索与播放全部内容；登录后可评分、收藏与创建播放列表。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                name, { name = it },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                password, { password = it },
                label = { Text("密码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (isRegister) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    confirm, { confirm = it },
                    label = { Text("确认密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (name.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (isRegister && password != confirm) {
                        Toast.makeText(context, "两次密码不一致", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    loading = true
                    scope.launch {
                        val result = if (isRegister) container.repository.register(name, password)
                        else container.repository.login(name, password)
                        loading = false
                        result.onSuccess {
                            Toast.makeText(context, "成功", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }.onFailure {
                            Toast.makeText(context, "失败：${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (isRegister) "注册" else "登录")
            }

            TextButton(
                onClick = { isRegister = !isRegister; confirm = "" },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(if (isRegister) "已有账号？去登录" else "没有账号？去注册")
            }
        }
    }
}
