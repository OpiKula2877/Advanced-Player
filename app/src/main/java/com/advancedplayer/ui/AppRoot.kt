package com.advancedplayer.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.advancedplayer.R
import com.advancedplayer.data.db.Track
import com.advancedplayer.ui.components.AddToPlaylistDialog
import com.advancedplayer.ui.components.LocalTrackActions
import com.advancedplayer.ui.components.TrackActions
import com.advancedplayer.ui.player.MiniPlayer
import com.advancedplayer.ui.player.NowPlayingScreen
import com.advancedplayer.ui.screens.CarModeScreen
import com.advancedplayer.ui.screens.EditTrackScreen
import com.advancedplayer.ui.screens.EqualizerScreen
import com.advancedplayer.ui.screens.HomeScreen
import com.advancedplayer.ui.screens.LibraryScreen
import com.advancedplayer.ui.screens.QueueScreen
import com.advancedplayer.ui.screens.SearchScreen
import com.advancedplayer.ui.screens.SettingsScreen
import com.advancedplayer.ui.screens.StatsScreen
import com.advancedplayer.ui.screens.TrackListScreen
import com.advancedplayer.ui.screens.VideoScreen
import com.advancedplayer.ui.screens.VisualizerScreen
import com.advancedplayer.ui.screens.WrappedScreen
import com.advancedplayer.ui.screens.findActivity
import com.advancedplayer.ui.theme.AdvancedPlayerTheme
import com.advancedplayer.ui.theme.LocalPlayerColors

private data class NavTab(val route: String, val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val navTabs = listOf(
    NavTab(Routes.HOME, R.string.nav_home, Icons.Rounded.Home),
    NavTab(Routes.SEARCH, R.string.nav_search, Icons.Rounded.Search),
    NavTab(Routes.LIBRARY, R.string.nav_library, Icons.Rounded.LibraryMusic),
)

@Composable
fun AppRoot(vm: MainViewModel, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    val settings = vm.settings.collectAsState().value ?: return
    AdvancedPlayerTheme(settings.themeMode, settings.accent) {
        val colors = LocalPlayerColors.current
        val activity = LocalContext.current.findActivity() as? ComponentActivity
        LaunchedEffect(colors.isDark) {
            val style = if (colors.isDark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            }
            activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }

        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val playerState by vm.playerState.collectAsState()
        var playlistPick by remember { mutableStateOf<List<Track>?>(null) }
        val navigate: (String) -> Unit = remember(nav) { { r -> nav.navigate(r) { launchSingleTop = true } } }
        val actions = remember(vm, nav) { TrackActions(vm, navigate) { playlistPick = it } }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

        val showNav = route == null || route in Routes.topLevel
        val showMini = playerState.hasMedia && route !in Routes.fullScreen
        var barsPx by remember { mutableIntStateOf(0) }
        val barsHeight = with(LocalDensity.current) { barsPx.toDp() }
        val fullScreen = route in Routes.fullScreen

        CompositionLocalProvider(
            LocalTrackActions provides actions,
            // Without this, Text outside a Surface defaults to black and vanishes on the dark theme.
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                NavHost(
                    nav,
                    startDestination = Routes.HOME,
                    modifier = Modifier.fillMaxSize().padding(bottom = if (fullScreen) 0.dp else barsHeight),
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(vm, navigate, 0.dp, hasPermission, onRequestPermission)
                    }
                    composable(Routes.SEARCH) { SearchScreen(vm, navigate, 0.dp) }
                    composable(Routes.LIBRARY) { LibraryScreen(vm, navigate, 0.dp) }
                    composable(
                        Routes.LIST,
                        arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("key") { type = NavType.StringType }),
                    ) { e ->
                        TrackListScreen(
                            vm, e.arguments?.getString("kind").orEmpty(), Routes.decodeKey(e.arguments?.getString("key")),
                            navigate, onBack = { nav.popBackStack() },
                        )
                    }
                    composable(
                        Routes.NOW,
                        enterTransition = { slideInVertically { it } },
                        popExitTransition = { slideOutVertically { it } },
                    ) { NowPlayingScreen(vm, navigate, onBack = { nav.popBackStack() }) }
                    composable(Routes.QUEUE) { QueueScreen(vm, onBack = { nav.popBackStack() }) }
                    composable(Routes.EQ) { EqualizerScreen(vm, onBack = { nav.popBackStack() }) }
                    composable(Routes.SETTINGS) { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
                    composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                        EditTrackScreen(vm, e.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
                    }
                    composable(Routes.STATS) { StatsScreen(vm, navigate, onBack = { nav.popBackStack() }) }
                    composable(Routes.WRAPPED) { WrappedScreen(vm, onBack = { nav.popBackStack() }) }
                    composable(Routes.CAR) { CarModeScreen(vm, onBack = { nav.popBackStack() }) }
                    composable(Routes.VIDEO) { VideoScreen(vm, onBack = { nav.popBackStack() }) }
                    composable(Routes.VISUALIZER) { VisualizerScreen(vm, onBack = { nav.popBackStack() }) }
                }

                if (!fullScreen) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .onSizeChanged { barsPx = it.height }
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.96f), MaterialTheme.colorScheme.background),
                                ),
                            )
                            .then(if (showNav) Modifier else Modifier.navigationBarsPadding()),
                    ) {
                        if (showMini) MiniPlayer(vm, onOpen = { navigate(Routes.NOW) })
                        if (showNav) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                                navTabs.forEach { tab ->
                                    NavigationBarItem(
                                        selected = route == tab.route,
                                        onClick = {
                                            nav.navigate(tab.route) {
                                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(tab.icon, null) },
                                        label = { Text(stringResource(tab.label)) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                            indicatorColor = colors.accent.copy(alpha = 0.22f),
                                            unselectedIconColor = colors.muted,
                                            unselectedTextColor = colors.muted,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = barsHeight + 8.dp))
            }
            playlistPick?.let { AddToPlaylistDialog(vm, it) { playlistPick = null } }
        }
    }
}
