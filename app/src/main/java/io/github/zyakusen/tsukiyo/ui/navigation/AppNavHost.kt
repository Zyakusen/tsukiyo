package io.github.zyakusen.tsukiyo.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.zyakusen.tsukiyo.player.PlayerManager
import io.github.zyakusen.tsukiyo.ui.LocalContainer
import io.github.zyakusen.tsukiyo.ui.components.MiniPlayerBar
import io.github.zyakusen.tsukiyo.ui.screens.BrowseScreen
import io.github.zyakusen.tsukiyo.ui.screens.CircleWorksScreen
import io.github.zyakusen.tsukiyo.ui.screens.DownloadsScreen
import io.github.zyakusen.tsukiyo.ui.screens.FavoritesScreen
import io.github.zyakusen.tsukiyo.ui.screens.HistoryScreen
import io.github.zyakusen.tsukiyo.ui.screens.LoginScreen
import io.github.zyakusen.tsukiyo.ui.screens.PlayerScreen
import io.github.zyakusen.tsukiyo.ui.screens.PlaylistWorksScreen
import io.github.zyakusen.tsukiyo.ui.screens.PlaylistsScreen
import io.github.zyakusen.tsukiyo.ui.screens.SearchScreen
import io.github.zyakusen.tsukiyo.ui.screens.SettingsScreen
import io.github.zyakusen.tsukiyo.ui.screens.TagWorksScreen
import io.github.zyakusen.tsukiyo.ui.screens.VaWorksScreen
import io.github.zyakusen.tsukiyo.ui.screens.WorkDetailScreen

object Routes {
    const val BROWSE = "browse"
    const val SEARCH = "search"
    const val CATEGORIES = "categories"
    const val FAVORITES = "favorites"
    const val DOWNLOADS = "downloads"
    const val HISTORY = "history"

    const val WORK = "work/{workId}"
    fun work(id: Long) = "work/$id"

    const val TAG = "tag/{tagId}"
    fun tag(id: Long) = "tag/$id"

    const val CIRCLE = "circle/{circleId}"
    fun circle(id: Long) = "circle/$id"

    const val VA = "va/{vaId}"
    fun va(id: String) = "va/$id"

    const val PLAYLIST = "playlist/{playlistId}"
    fun playlist(id: String) = "playlist/$id"

    const val PLAYER = "player"
    const val PLAYLISTS = "playlists"
    const val LOGIN = "login"
    const val SETTINGS = "settings"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.BROWSE, "首页", Icons.Filled.Home),
    TopLevelDestination(Routes.SEARCH, "搜索", Icons.Filled.Search),
    TopLevelDestination(Routes.PLAYLISTS, "播放列表", Icons.Filled.QueueMusic),
    TopLevelDestination(Routes.FAVORITES, "收藏", Icons.Filled.Favorite),
    TopLevelDestination(Routes.DOWNLOADS, "下载", Icons.Filled.Download)
)

private val topLevelRoutes = topLevelDestinations.map { it.route }.toSet()

/**
 * 切换到顶级 Tab 的统一入口：确保每个顶级目的地只在返回栈中出现一次，
 * 避免用裸 navigate 压入重复条目导致底部导航与返回键行为错乱。
 */
fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * 从详情页回到搜索页（应用预置筛选）。优先弹回栈中已存在的搜索页，
 * 否则新建一个搜索页（同时弹掉详情页）。
 */
fun NavHostController.navigateToSearch() {
    if (!popBackStack(Routes.SEARCH, false)) {
        navigate(Routes.SEARCH) {
            popUpTo(graph.findStartDestination().id)
        }
    }
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val container = LocalContainer.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    val isTopLevel = currentRoute in topLevelRoutes
    val isPlayer = currentRoute == Routes.PLAYER
    val playerState by PlayerManager.uiState.collectAsState()
    val playing = playerState.currentTrack != null

    BackHandler(enabled = isTopLevel && currentRoute != Routes.BROWSE) {
        navController.navigateToTopLevel(Routes.BROWSE)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column(
                Modifier
                    .zIndex(1f)
                    .navigationBarsPadding()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (playing && !isPlayer) MiniPlayerBar(onExpand = { navController.navigate(Routes.PLAYER) })
                if (isTopLevel) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, windowInsets = WindowInsets(0, 0, 0, 0)) {
                        topLevelDestinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true,
                                onClick = {
                                    if (dest.route == Routes.BROWSE) {
                                        if (currentRoute == Routes.BROWSE) {
                                            container.scrollHomeToTop()
                                        } else if (!navController.popBackStack(Routes.BROWSE, false)) {
                                            navController.navigate(Routes.BROWSE)
                                        }
                                    } else {
                                        navController.navigateToTopLevel(dest.route)
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.BROWSE,
            modifier = androidx.compose.ui.Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .clipToBounds()
        ) {            composable(Routes.BROWSE) { BrowseScreen(navController) }
            composable(Routes.SEARCH) { SearchScreen(navController) }
            composable(Routes.HISTORY) { HistoryScreen(navController) }
            composable(Routes.FAVORITES) { FavoritesScreen(navController) }
            composable(Routes.DOWNLOADS) { DownloadsScreen(navController) }

            composable(
                Routes.WORK,
                arguments = listOf(navArgument("workId") { type = NavType.LongType })
            ) { entry ->
                WorkDetailScreen(workId = entry.arguments?.getLong("workId") ?: 0L, navController = navController)
            }
            composable(
                Routes.TAG,
                arguments = listOf(navArgument("tagId") { type = NavType.LongType })
            ) { entry ->
                TagWorksScreen(tagId = entry.arguments?.getLong("tagId") ?: 0L, navController = navController)
            }
            composable(
                Routes.CIRCLE,
                arguments = listOf(navArgument("circleId") { type = NavType.LongType })
            ) { entry ->
                CircleWorksScreen(circleId = entry.arguments?.getLong("circleId") ?: 0L, navController = navController)
            }
            composable(
                Routes.VA,
                arguments = listOf(navArgument("vaId") { type = NavType.StringType })
            ) { entry ->
                VaWorksScreen(vaId = entry.arguments?.getString("vaId") ?: "", navController = navController)
            }
            composable(
                Routes.PLAYLIST,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
            ) { entry ->
                PlaylistWorksScreen(playlistId = entry.arguments?.getString("playlistId") ?: "", navController = navController)
            }

            composable(
                Routes.PLAYER,
                enterTransition = { slideInVertically(animationSpec = tween(280), initialOffsetY = { it }) },
                exitTransition = { slideOutVertically(animationSpec = tween(280), targetOffsetY = { it }) },
                popEnterTransition = { androidx.compose.animation.fadeIn(animationSpec = tween(200)) },
                popExitTransition = { slideOutVertically(animationSpec = tween(280), targetOffsetY = { it }) }
            ) { PlayerScreen(navController) }
            composable(Routes.PLAYLISTS) { PlaylistsScreen(navController) }
            composable(Routes.LOGIN) { LoginScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
        }
    }

    val sleepFinished by PlayerManager.sleepFinishedPending.collectAsState()
    if (sleepFinished) {
        AlertDialog(
            onDismissRequest = { PlayerManager.acknowledgeSleepFinished() },
            title = { Text("定时结束") },
            text = { Text("设定的时间到了，已暂停播放。") },
            confirmButton = {
                TextButton(onClick = { PlayerManager.acknowledgeSleepFinished() }) { Text("确认") }
            }
        )
    }
}
