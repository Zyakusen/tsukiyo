package io.github.zyakusen.tsukiyo.data

import android.content.Context
import io.github.zyakusen.tsukiyo.data.model.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 认证状态管理：持有 JWT token 与当前用户信息。
 */
class AuthManager(context: Context) {

    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    @Volatile
    var token: String? = null
        private set

    @Volatile
    var userName: String = "guest"
        private set

    @Volatile
    var userGroup: String = "user"
        private set

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        token = prefs.getString("token", null)
        userName = prefs.getString("userName", "guest") ?: "guest"
        userGroup = prefs.getString("userGroup", "user") ?: "user"
        publish()
    }

    val isRealUser: Boolean get() = userName != "guest"

    fun save(user: AuthUser?, newToken: String?) {
        token = newToken
        userName = user?.name ?: "guest"
        userGroup = user?.group ?: "user"
        prefs.edit()
            .putString("token", newToken)
            .putString("userName", userName)
            .putString("userGroup", userGroup)
            .apply()
        publish()
    }

    fun logout() {
        save(null, null)
    }

    private fun publish() {
        _state.value = AuthState(
            hasToken = token != null,
            userName = userName,
            userGroup = userGroup,
            isRealUser = isRealUser
        )
    }
}

data class AuthState(
    val hasToken: Boolean = false,
    val userName: String = "guest",
    val userGroup: String = "user",
    val isRealUser: Boolean = false
)
