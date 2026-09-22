package com.zyakusen.tsukiyo.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.zyakusen.tsukiyo.data.AppContainer

val LocalContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer 未初始化")
}
