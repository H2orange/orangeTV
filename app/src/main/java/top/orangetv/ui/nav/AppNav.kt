package top.orangetv.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import top.orangetv.ui.AppViewModel
import top.orangetv.ui.category.CategoryScreen
import top.orangetv.ui.common.LocalOpenConfig
import top.orangetv.ui.common.TopTab
import top.orangetv.ui.config.ConfigScreen
import top.orangetv.ui.detail.DetailScreen
import top.orangetv.ui.home.HomeScreen
import top.orangetv.ui.live.LiveScreen
import top.orangetv.ui.player.PlayerScreen
import top.orangetv.ui.search.SearchScreen

private object Routes {
    const val HOME = "home"
    const val LIVE = "live"
    const val CATEGORY = "category"
    const val SEARCH = "search"
    const val DETAIL = "detail/{id}"
    const val PLAYER = "player"
    const val CONFIG = "config"
}

@Composable
fun AppNav(vm: AppViewModel) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val openConfig = remember(nav) { { nav.navigate(Routes.CONFIG) } }

    fun goTab(tab: TopTab) {
        if (nav.currentDestination?.route == tab.route) return
        nav.navigate(tab.route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalOpenConfig provides openConfig) {
        NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                onOpenDetail = { nav.navigate("detail/${it.id}") },
                onNavigate = { goTab(it) },
                onPlayFeatured = { item ->
                    scope.launch {
                        val url = vm.playUrl(item.id, 0, 0)
                        if (url != null) {
                            vm.requestPlayback(
                                url = url,
                                title = item.title,
                                subtitle = item.remarks ?: "第 1 集",
                                vodId = item.id,
                                line = 0,
                                episode = 0
                            )
                            nav.navigate(Routes.PLAYER)
                        }
                    }
                }
            )
        }

        composable(Routes.LIVE) {
            LiveScreen(vm = vm, onNavigate = { goTab(it) })
        }

        composable(Routes.CATEGORY) {
            CategoryScreen(
                vm = vm,
                onOpenDetail = { nav.navigate("detail/${it.id}") },
                onNavigate = { goTab(it) }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                vm = vm,
                onOpenDetail = { nav.navigate("detail/${it.id}") },
                onNavigate = { goTab(it) }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DetailScreen(
                vm = vm,
                vodId = id,
                onBack = { nav.popBackStack() },
                onPlay = { detail, line, episode ->
                    scope.launch {
                        val url = vm.playUrl(detail.summary.id, line, episode)
                        if (url != null) {
                            vm.requestPlayback(
                                url = url,
                                title = detail.summary.title,
                                subtitle = "第 ${episode + 1} 集",
                                vodId = detail.summary.id,
                                line = line,
                                episode = episode
                            )
                            nav.navigate(Routes.PLAYER)
                        }
                    }
                }
            )
        }

        composable(Routes.PLAYER) {
            PlayerScreen(vm = vm, onBack = {
                vm.clearPlayback()
                nav.popBackStack()
            })
        }

        composable(Routes.CONFIG) {
            ConfigScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        } // NavHost
    } // CompositionLocalProvider(LocalOpenConfig)
}