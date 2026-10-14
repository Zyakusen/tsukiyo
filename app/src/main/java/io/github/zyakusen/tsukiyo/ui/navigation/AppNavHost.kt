package io.github.zyakusen.tsukiyo.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import io.github.zyakusen.tsukiyo.ui.components.UpdateDialog
import io.github.zyakusen.tsukiyo.util.UpdateInfo
import io.github.zyakusen.tsukiyo.util.compareVersions
import io.github.zyakusen.tsukiyo.util.currentVersionName
import io.github.zyakusen.tsukiyo.util.fetchLatestRelease
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
import io.github.zyakusen.tsukiyo.ui.screens.TagFavoritesScreen
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

    /** 标签收藏（标签 / 社团 / 声优）。注意与 [FAVORITES]（我的收藏：评分/标记的作品）不是一回事。 */
    const val TAG_FAVORITES = "tag_favorites"

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

    // 启动时检查更新：每次启动一次，失败静默（不打扰用户）；
    // 用户点过「不再提示」的那个版本不再弹，但出了更新的版本还会弹。
    val context = LocalContext.current
    var startupUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        val info = fetchLatestRelease() ?: return@LaunchedEffect
        val ignored = container.settingsStore.ignoredUpdateVersion
        if (info.version != ignored && compareVersions(info.version, currentVersionName(context)) > 0) {
            startupUpdate = info
        }
    }

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
            // 默认转场：二级页从右侧平移进来、返回时平移回去（顶部 5 个 tab 见下方各自的覆盖）
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(260)
                )
            },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(260)
                )
            },
            modifier = androidx.compose.ui.Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .clipToBounds()
        ) {
            // 底部 tab 之间：不要淡入淡出，瞬间干脆地切换
            topLevelDestinations.forEach { dest ->
                composable(
                    dest.route,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None }
                ) { entry ->
                    when (entry.destination.route) {
                        Routes.BROWSE -> BrowseScreen(navController)
                        Routes.SEARCH -> SearchScreen(navController)
                        Routes.PLAYLISTS -> PlaylistsScreen(navController)
                        Routes.FAVORITES -> FavoritesScreen(navController)
                        Routes.DOWNLOADS -> DownloadsScreen(navController)
                    }
                }
            }

            composable(Routes.TAG_FAVORITES) { TagFavoritesScreen(navController) }
            // 历史页不是底部 tab，从首页右上角进入 → 走默认的"从右滑入"
            composable(Routes.HISTORY) { HistoryScreen(navController) }

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
                // 打开时**背景不动**（不淡出、不下移），由播放页直接盖上来
                exitTransition = { ExitTransition.None },
                // 返回时背景直接露出，不做淡入
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutVertically(animationSpec = tween(280), targetOffsetY = { it }) }
            ) { PlayerScreen(navController) }
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

    // 启动时发现新版本 → 弹窗（更新 / 稍后 / 不再提示）
    startupUpdate?.let { info ->
        UpdateDialog(
            info = info,
            allowIgnore = true,
            onIgnore = {
                // 只忽略这一个版本：下次出了更新的版本还会提示
                container.settingsStore.ignoredUpdateVersion = info.version
                startupUpdate = null
            },
            onDismiss = { startupUpdate = null }
        )
    }
}
