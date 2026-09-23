package io.github.zyakusen.tsukiyo.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.zyakusen.tsukiyo.data.AppContainer

val LocalContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer 未初始化")
}
