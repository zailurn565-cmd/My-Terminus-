package com.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.providers.ForgeContainer
import com.example.providers.HostViewModel
import com.example.providers.SettingsViewModel
import com.example.providers.SnippetViewModel
import com.example.providers.TerminalViewModel
import com.example.screens.HostEditScreen
import com.example.screens.HostListScreen
import com.example.screens.SettingsScreen
import com.example.screens.SnippetsScreen
import com.example.screens.TerminalScreen
import com.example.ui.theme.ForgeShellTheme

object ForgeRoutes {
    const val HOST_LIST = "host_list"
    const val HOST_CREATE = "host_edit"
    const val HOST_EDIT_PATTERN = "host_edit?hostId={hostId}&missingCred={missingCred}"
    const val TERMINAL_PATTERN = "terminal_session/{hostId}?tab={tab}"
    const val SNIPPETS = "snippets"
    const val SETTINGS = "settings"

    fun hostEdit(hostId: String? = null, missingCred: Boolean = false): String {
        return if (hostId == null) {
            HOST_CREATE
        } else {
            "host_edit?hostId=$hostId&missingCred=$missingCred"
        }
    }

    fun terminalSession(hostId: String, initialTab: Int = 0): String =
        "terminal_session/$hostId?tab=$initialTab"
}

@Composable
fun ForgeShellApp() {
    val context = LocalContext.current
    val container = remember(context) { ForgeContainer.getInstance(context) }

    val hostViewModel: HostViewModel = viewModel(
        factory = HostViewModel.factory(container)
    )
    val snippetViewModel: SnippetViewModel = viewModel(
        factory = SnippetViewModel.factory(container)
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(container)
    )

    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    ForgeShellTheme(themeMode = settings.themeMode) {
        NavHost(
            navController = navController,
            startDestination = ForgeRoutes.HOST_LIST
        ) {
            composable(ForgeRoutes.HOST_LIST) {
                HostListScreen(
                    hostViewModel = hostViewModel,
                    onAddHost = { navController.navigate(ForgeRoutes.hostEdit()) },
                    onEditHost = { id -> navController.navigate(ForgeRoutes.hostEdit(hostId = id)) },
                    onConnectHost = { id, tab ->
                        navController.navigate(ForgeRoutes.terminalSession(id, tab))
                    },
                    onOpenSnippets = { navController.navigate(ForgeRoutes.SNIPPETS) },
                    onOpenSettings = { navController.navigate(ForgeRoutes.SETTINGS) }
                )
            }

            composable(
                route = ForgeRoutes.HOST_EDIT_PATTERN,
                arguments = listOf(
                    navArgument("hostId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("missingCred") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val editHostId = backStackEntry.arguments?.getString("hostId")
                val missingCred = backStackEntry.arguments?.getBoolean("missingCred") ?: false

                HostEditScreen(
                    editHostId = editHostId,
                    missingCredentialNotice = missingCred,
                    hostViewModel = hostViewModel,
                    settingsViewModel = settingsViewModel,
                    onSaved = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = ForgeRoutes.TERMINAL_PATTERN,
                arguments = listOf(
                    navArgument("hostId") { type = NavType.StringType },
                    navArgument("tab") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { backStackEntry ->
                val hostId = backStackEntry.arguments?.getString("hostId") ?: return@composable
                val initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
                val terminalViewModel: TerminalViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    key = "session_$hostId",
                    factory = TerminalViewModel.factory(
                        hostId = hostId,
                        initialTabIndex = initialTab,
                        container = container
                    )
                )

                TerminalScreen(
                    hostId = hostId,
                    terminalViewModel = terminalViewModel,
                    snippetViewModel = snippetViewModel,
                    settingsViewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onRedirectToEditCredentials = { missingId ->
                        navController.popBackStack()
                        navController.navigate(
                            ForgeRoutes.hostEdit(hostId = missingId, missingCred = true)
                        )
                    },
                    onOpenSnippetsManager = {
                        navController.navigate(ForgeRoutes.SNIPPETS)
                    }
                )
            }

            composable(ForgeRoutes.SNIPPETS) {
                SnippetsScreen(
                    snippetViewModel = snippetViewModel,
                    hostViewModel = hostViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(ForgeRoutes.SETTINGS) {
                SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
