package com.itsikh.buddy.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itsikh.buddy.bugreport.ScreenshotHolder
import com.itsikh.buddy.data.models.ChatMode
import com.itsikh.buddy.data.repository.ProfileRepository
import com.itsikh.buddy.ui.components.DebugOverlayViewModel
import com.itsikh.buddy.ui.components.FloatingBugButton
import com.itsikh.buddy.ui.screens.bugreport.BugReportScreen
import com.itsikh.buddy.ui.screens.bugreport.ReportMode
import com.itsikh.buddy.ui.screens.chat.ChatScreen
import com.itsikh.buddy.ui.screens.garden.VocabularyGardenScreen
import com.itsikh.buddy.ui.screens.home.HomeScreen
import com.itsikh.buddy.ui.screens.memory.MemoryViewerScreen
import com.itsikh.buddy.ui.screens.onboarding.ParentConsentScreen
import com.itsikh.buddy.ui.screens.onboarding.ProfileSetupScreen
import com.itsikh.buddy.ui.screens.progress.ProgressDashboardScreen
import com.itsikh.buddy.ui.screens.settings.SettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// ---- Startup routing ViewModel ----

/**
 * Determines the start destination based on whether the child has completed onboarding.
 * Runs before the NavHost renders to avoid a flash of the wrong screen.
 */
@HiltViewModel
class StartupViewModel @Inject constructor(
    profileRepository: ProfileRepository
) : androidx.lifecycle.ViewModel() {

    val startDestination = profileRepository.profile.map { profile ->
        when {
            profile == null || !profile.parentConsentGiven -> "parent_consent"
            !profile.onboardingComplete                    -> "profile_setup"
            else                                           -> "chat/FREE_CHAT"
        }
    }
}

// ---- Root navigation graph ----

/**
 * Root navigation graph for Buddy.
 *
 * Start destination is determined dynamically by [StartupViewModel]:
 *   - No profile / no consent → parent_consent → profile_setup → chat
 *   - Returning user           → chat (straight to conversation)
 *
 * Routes:
 *   parent_consent  → ParentConsentScreen
 *   profile_setup   → ProfileSetupScreen
 *   chat            → ChatScreen (main screen)
 *   settings        → SettingsScreen
 *   progress        → ProgressDashboardScreen (parent-gated)
 *   memory          → MemoryViewerScreen (parent-gated)
 *   garden          → VocabularyGardenScreen
 *   bug_report/{mode} → BugReportScreen
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val overlayVm: DebugOverlayViewModel = hiltViewModel()
    val startupVm: StartupViewModel      = hiltViewModel()

    val showBugButton by overlayVm.showBugButton.collectAsState()
    val startDest by startupVm.startDestination.collectAsState(initial = null)

    if (startDest == null) {
        // Brief loading state while we check the database
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController    = navController,
            startDestination = startDest!!
        ) {

            // ---- Onboarding ----
            composable("parent_consent") {
                ParentConsentScreen(
                    onConsent = { navController.navigate("profile_setup") {
                        popUpTo("parent_consent") { inclusive = true }
                    }}
                )
            }

            composable("profile_setup") {
                ProfileSetupScreen(
                    onProfileCreated = {
                        navController.navigate("chat/FREE_CHAT") {
                            popUpTo("profile_setup") { inclusive = true }
                        }
                    }
                )
            }

            // ---- Home (hub screen) ----
            composable("home") {
                HomeScreen(
                    onTalkWithBuddy = { navController.navigate("chat/FREE_CHAT") },
                    onStories       = { navController.navigate("chat/STORY_TIME") },
                    onGames         = { navController.navigate("garden") },
                    onProgress      = { navController.navigate("progress") },
                    onSettings      = { navController.navigate("settings") }
                )
            }

            // ---- Chat (mode passed as path argument) ----
            composable("chat/{mode}") { backStackEntry ->
                val modeArg = backStackEntry.arguments?.getString("mode") ?: "FREE_CHAT"
                val chatMode = runCatching { ChatMode.valueOf(modeArg) }.getOrDefault(ChatMode.FREE_CHAT)
                ChatScreen(
                    initialMode    = chatMode,
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenProgress = { navController.navigate("progress") },
                    onOpenHistory  = { navController.navigate("memory") },
                    onOpenGarden   = { navController.navigate("garden") },
                    onBack         = null
                )
            }

            // Legacy route (direct chat link)
            composable("chat") {
                ChatScreen(
                    initialMode    = ChatMode.FREE_CHAT,
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenProgress = { navController.navigate("progress") },
                    onOpenHistory  = { navController.navigate("memory") },
                    onOpenGarden   = { navController.navigate("garden") },
                    onBack         = null
                )
            }

            // ---- Settings ----
            composable("settings") {
                SettingsScreen(
                    onBack         = { navController.popBackStack() },
                    onOpenBugReport = { mode ->
                        navController.navigate("bug_report/${mode.name}")
                    },
                    onOpenProgress  = { navController.navigate("progress") },
                    onOpenMemory    = { navController.navigate("memory") },
                    onOpenGarden    = { navController.navigate("garden") },
                    onOpenKeyPack   = null
                )
            }

            // ---- Parent-gated screens ----
            composable("progress") {
                ProgressDashboardScreen(onBack = { navController.popBackStack() })
            }

            composable("memory") {
                MemoryViewerScreen(onBack = { navController.popBackStack() })
            }

            composable("garden") {
                VocabularyGardenScreen(
                    onBack      = { navController.popBackStack() },
                    onStartQuiz = { navController.navigate("chat/ROLE_PLAY") }
                )
            }

            // Legacy redirect — saved back stacks from versions prior to 0.0.46 may
            // still reference this route; redirect to garden instead of crashing.
            composable("word_quiz") {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    navController.navigate("garden") {
                        popUpTo("word_quiz") { inclusive = true }
                    }
                }
            }

            // ---- Bug report (existing infrastructure) ----
            composable("bug_report/{mode}") { backStackEntry ->
                val modeName = backStackEntry.arguments?.getString("mode")
                val mode     = modeName?.let { runCatching { ReportMode.valueOf(it) }.getOrNull() }
                    ?: ReportMode.BUG_REPORT
                BugReportScreen(
                    mode   = mode,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // Floating debug bug button (admin mode only)
        FloatingBugButton(
            visible = showBugButton,
            onScreenshotCaptured = { bitmap ->
                ScreenshotHolder.store(bitmap)
                navController.navigate("bug_report/BUG_REPORT")
            }
        )
    }
}
