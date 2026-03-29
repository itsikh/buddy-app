package com.itsikh.buddy.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itsikh.buddy.AppConfig
import com.itsikh.buddy.BuildConfig
import com.itsikh.buddy.security.ClearDataConfirmationDialog
import com.itsikh.buddy.security.KeyValidation
import com.itsikh.buddy.voice.TtsBackend
import com.itsikh.buddy.ui.components.SectionHeader
import com.itsikh.buddy.ui.components.SettingsScaffold
import com.itsikh.buddy.ui.screens.bugreport.ReportMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-featured settings screen for the template app.
 *
 * ## Sections
 * | Section | Purpose |
 * |---------|---------|
 * | **GitHub Token** | Configure the PAT used for bug reports and update checks |
 * | **Auto-Update** | Check for and install a new release from GitHub Releases |
 * | **Backup** | Export all data to any storage (local / Google Drive / Dropbox) and restore |
 * | **Support** | Open the bug report screen, send feedback, clear logs |
 * | **Debug** | Admin-only: log level toggle, bug button visibility (via [SettingsScaffold]) |
 * | **About** | App name and version (tap 7× to unlock admin mode) |
 *
 * ## Backup
 * The Export and Restore buttons use [ActivityResultContracts.CreateDocument] and
 * [ActivityResultContracts.OpenDocument] respectively. Android's Storage Access Framework
 * automatically presents all available storage providers — including Google Drive, Dropbox,
 * and local filesystem — without any extra SDK integration.
 *
 * To wire in your actual data, edit [SettingsViewModel.exportBackupToUri] and
 * [SettingsViewModel.restoreFromBackup] to call your concrete [backup.BaseBackupManager].
 *
 * @param onBack Called when the user taps the back arrow.
 * @param onOpenBugReport Called when the user taps "Report a Bug" or "Send Feedback",
 *                        with the appropriate [ReportMode].
 * @param viewModel Injected by Hilt via `hiltViewModel()`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBugReport: (ReportMode) -> Unit,
    onOpenProgress: (() -> Unit)? = null,
    onOpenMemory: (() -> Unit)? = null,
    onOpenGarden: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val adminMode         by viewModel.adminMode.collectAsState()
    val logLevel          by viewModel.logLevel.collectAsState()
    val showBugButton     by viewModel.showBugButton.collectAsState()
    val autoUpdate        by viewModel.autoUpdateEnabled.collectAsState()
    val autoBackup        by viewModel.autoBackupEnabled.collectAsState()
    val updateState       by viewModel.updateState.collectAsState()
    val exportState       by viewModel.exportState.collectAsState()
    val restoreState      by viewModel.restoreState.collectAsState()
    val driveUiState      by viewModel.driveUiState.collectAsState()
    val childProfileState  by viewModel.childProfileState.collectAsState()
    val clearMemoryState   by viewModel.clearMemoryState.collectAsState()
    val ttsBackend         by viewModel.ttsBackend.collectAsState()
    val geminiValidation   by viewModel.geminiValidation.collectAsState()
    val claudeValidation   by viewModel.claudeValidation.collectAsState()
    val ttsValidation      by viewModel.ttsValidation.collectAsState()

    // SAF launchers — CreateDocument shows all providers including Google Drive
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? -> if (uri != null) viewModel.exportBackupToUri(uri) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) viewModel.restoreFromBackup(uri) }

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val signedInAccount = try {
            com.google.android.gms.auth.api.signin.GoogleSignIn
                .getSignedInAccountFromIntent(result.data)
                .getResult(com.google.android.gms.common.api.ApiException::class.java)
        } catch (e: com.google.android.gms.common.api.ApiException) {
            viewModel.onGoogleSignInResult(null, "שגיאה בהתחברות (קוד ${e.statusCode}). ודא שה-SHA-1 רשום ב-Google Cloud Console.")
            null
        } catch (e: Exception) {
            viewModel.onGoogleSignInResult(null, "שגיאה: ${e.message}")
            null
        }
        if (signedInAccount != null) viewModel.onGoogleSignInResult(signedInAccount, null)
    }

    // Local UI state
    var githubToken           by remember { mutableStateOf("") }
    var tokenVisible          by remember { mutableStateOf(false) }
    var hasToken              by remember { mutableStateOf(viewModel.hasGitHubToken) }
    var showRestoreDialog      by remember { mutableStateOf(false) }
    var showClearLogsDialog    by remember { mutableStateOf(false) }
    var showClearMemoryDialog  by remember { mutableStateOf(false) }
    var logsCleared           by remember { mutableStateOf(false) }

    // Buddy API key local state
    var geminiKey      by remember { mutableStateOf("") }
    var claudeKey      by remember { mutableStateOf("") }
    var googleTtsKey   by remember { mutableStateOf("") }
    var geminiVisible  by remember { mutableStateOf(false) }
    var claudeVisible  by remember { mutableStateOf(false) }
    var ttsVisible     by remember { mutableStateOf(false) }

    // Buddy voice gender
    var buddyGender    by remember { mutableStateOf(viewModel.getBuddyGender()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val activeProvider = driveUiState.aiDefaultProvider
            val ttsVoice = if (buddyGender == AppConfig.BUDDY_GENDER_GIRL) "he-IL-Wavenet-A" else "he-IL-Wavenet-B"
            val ttsActive = when (ttsBackend) {
                TtsBackend.GOOGLE_CLOUD     -> "$ttsVoice (Google Cloud)"
                TtsBackend.ANDROID_FALLBACK -> "Android TTS (he-IL) [fallback]"
                TtsBackend.UNKNOWN          -> "$ttsVoice (not yet spoken)"
            }
            val modelDebugInfo = listOf(
                "AI provider" to if (activeProvider == AppConfig.AI_PROVIDER_CLAUDE) "Claude (primary)" else "Gemini (primary)",
                "Gemini chat" to "gemini-2.0-flash",
                "Claude chat" to "claude-haiku-4-5-20251001",
                "Claude analysis" to "claude-sonnet-4-6",
                "TTS active" to ttsActive,
                "Gemini key" to if (viewModel.hasGeminiKey) "✓ set" else "✗ missing",
                "Claude key" to if (viewModel.hasClaudeKey) "✓ set" else "✗ missing",
                "Google TTS key" to if (viewModel.hasGoogleTtsKey) "✓ set" else "✗ missing"
            )

            SettingsScaffold(
                appName = AppConfig.APP_NAME,
                versionName = BuildConfig.VERSION_NAME,
                adminMode = adminMode,
                logLevel = logLevel,
                showBugButton = showBugButton,
                onAdminModeToggle = { viewModel.setAdminMode(it) },
                onDetailedLoggingToggle = { viewModel.setDetailedLogging(it) },
                onShowBugButtonToggle = { viewModel.setShowBugButton(it) },
                debugInfo = modelDebugInfo
            ) {

                // ── Child Profile ─────────────────────────────────────────────
                SectionHeader("פרופיל הילד")
                Spacer(Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value         = childProfileState.name,
                            onValueChange = viewModel::onProfileNameChanged,
                            label         = { Text("שם הילד (עברית)") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value         = childProfileState.namePhonetic,
                            onValueChange = viewModel::onProfileNamePhoneticChanged,
                            label         = { Text("שם באנגלית (איך לבטא)") },
                            placeholder   = { Text("e.g. Yotam, Noa, Tal") },
                            supportingText = { Text("ישמש את ה-AI לבטא את השם נכון באנגלית") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value         = childProfileState.ageText,
                            onValueChange = viewModel::onProfileAgeChanged,
                            label         = { Text("גיל") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        // Gender selector
                        Text("מגדר", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = childProfileState.gender == "BOY",
                                onClick  = { viewModel.onProfileGenderChanged("BOY") },
                                label    = { Text("ילד 👦") }
                            )
                            FilterChip(
                                selected = childProfileState.gender == "GIRL",
                                onClick  = { viewModel.onProfileGenderChanged("GIRL") },
                                label    = { Text("ילדה 👧") }
                            )
                        }
                        Button(
                            onClick  = viewModel::saveChildProfile,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (childProfileState.saved) "נשמר!" else "שמור שינויים")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // ── Buddy AI Configuration ────────────────────────────────────
                SectionHeader("הגדרות AI — Buddy")
                Spacer(Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                        // Buddy voice gender
                        Text("קול Buddy", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "קובע את מגדר הקול בעברית (זכר/נקבה) ואת הדקדוק של Buddy בשיחה",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = buddyGender == "GIRL",
                                onClick  = {
                                    buddyGender = "GIRL"
                                    viewModel.setBuddyGender("GIRL")
                                },
                                label    = { Text("ילדה 👧 (ברירת מחדל)") }
                            )
                            FilterChip(
                                selected = buddyGender == "BOY",
                                onClick  = {
                                    buddyGender = "BOY"
                                    viewModel.setBuddyGender("BOY")
                                },
                                label    = { Text("ילד 👦") }
                            )
                        }

                        HorizontalDivider()

                        // Gemini key
                        ApiKeyField(
                            label     = "Gemini API Key",
                            hint      = "מופתח ב-aistudio.google.com",
                            value     = geminiKey,
                            visible   = geminiVisible,
                            hasKey    = viewModel.hasGeminiKey,
                            validation = geminiValidation,
                            onChange  = { geminiKey = it },
                            onToggleVisibility = { geminiVisible = !geminiVisible },
                            onSave    = { viewModel.saveGeminiKey(geminiKey); geminiKey = "" },
                            onClear   = { viewModel.clearGeminiKey() }
                        )

                        HorizontalDivider()

                        // Claude key
                        ApiKeyField(
                            label     = "Claude API Key",
                            hint      = "מופתח ב-console.anthropic.com",
                            value     = claudeKey,
                            visible   = claudeVisible,
                            hasKey    = viewModel.hasClaudeKey,
                            validation = claudeValidation,
                            onChange  = { claudeKey = it },
                            onToggleVisibility = { claudeVisible = !claudeVisible },
                            onSave    = { viewModel.saveClaudeKey(claudeKey); claudeKey = "" },
                            onClear   = { viewModel.clearClaudeKey() }
                        )

                        HorizontalDivider()

                        // Google TTS key
                        ApiKeyField(
                            label     = "Google Cloud TTS API Key",
                            hint      = "מופתח ב-console.cloud.google.com",
                            value     = googleTtsKey,
                            visible   = ttsVisible,
                            hasKey    = viewModel.hasGoogleTtsKey,
                            validation = ttsValidation,
                            onChange  = { googleTtsKey = it },
                            onToggleVisibility = { ttsVisible = !ttsVisible },
                            onSave    = { viewModel.saveGoogleTtsKey(googleTtsKey); googleTtsKey = "" },
                            onClear   = { viewModel.clearGoogleTtsKey() }
                        )

                        HorizontalDivider()

                        // AI default provider
                        Text("מנוע AI ראשי", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = driveUiState.aiDefaultProvider == com.itsikh.buddy.AppConfig.AI_PROVIDER_GEMINI,
                                onClick  = { viewModel.setAiDefaultProvider(com.itsikh.buddy.AppConfig.AI_PROVIDER_GEMINI) },
                                label    = { Text("Gemini (מומלץ)") }
                            )
                            FilterChip(
                                selected = driveUiState.aiDefaultProvider == com.itsikh.buddy.AppConfig.AI_PROVIDER_CLAUDE,
                                onClick  = { viewModel.setAiDefaultProvider(com.itsikh.buddy.AppConfig.AI_PROVIDER_CLAUDE) },
                                label    = { Text("Claude") }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Google Drive ──────────────────────────────────────────────
                SectionHeader("Google Drive")
                Spacer(Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (driveUiState.isSignedIn) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("מחובר", style = MaterialTheme.typography.labelLarge)
                                    driveUiState.accountEmail?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick  = { viewModel.syncNow() },
                                    enabled  = !driveUiState.isSyncing
                                ) {
                                    if (driveUiState.isSyncing) {
                                        CircularProgressIndicator(Modifier.size(16.dp))
                                    } else {
                                        Icon(Icons.Default.CloudUpload, null, Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text("סנכרן עכשיו")
                                }
                                TextButton(onClick = { viewModel.signOutFromGoogle() }) {
                                    Text("התנתק", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            driveUiState.syncError?.let {
                                Text("שגיאה: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text("חבר Google Drive לשמירת ההתקדמות של Buddy.", style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick  = { googleSignInLauncher.launch(viewModel.getDriveSignInIntent()) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("התחבר עם Google")
                            }
                            driveUiState.syncError?.let { err ->
                                Text(
                                    err,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── GitHub Token ──────────────────────────────────────────────
                SectionHeader("GitHub Token")
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (!hasToken)
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    else
                        CardDefaults.cardColors()
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (hasToken) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasToken) MaterialTheme.colorScheme.tertiary
                                       else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                if (hasToken) "Token configured — updates and bug reports enabled"
                                else "Token required for updates and bug reports",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (hasToken) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            "A GitHub Personal Access Token with the \"repo\" scope is required to check for updates and submit bug reports. Generate one at: github.com → Settings → Developer settings → Personal access tokens",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasToken) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onErrorContainer
                        )
                        OutlinedTextField(
                            value = githubToken,
                            onValueChange = { githubToken = it },
                            label = { Text("Personal Access Token") },
                            placeholder = { Text("ghp_...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (tokenVisible) VisualTransformation.None
                                                   else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                    Icon(
                                        if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle visibility"
                                    )
                                }
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.saveGitHubToken(githubToken)
                                    hasToken = viewModel.hasGitHubToken
                                    githubToken = ""
                                    tokenVisible = false
                                },
                                enabled = githubToken.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) { Text("Save Token") }

                            if (hasToken) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.clearGitHubToken()
                                        hasToken = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Clear") }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Auto-Update ───────────────────────────────────────────────
                SectionHeader("Auto-Update")
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Check for Updates", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Fetch latest release from GitHub on launch",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = autoUpdate, onCheckedChange = { viewModel.setAutoUpdateEnabled(it) })
                        }

                        when (val state = updateState) {
                            is SettingsViewModel.UpdateState.Idle -> {
                                Button(
                                    onClick = { viewModel.checkForUpdate() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Check Now")
                                }
                            }
                            is SettingsViewModel.UpdateState.Checking -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(Modifier.size(20.dp))
                                    Text("Checking for updates…")
                                }
                            }
                            is SettingsViewModel.UpdateState.UpToDate -> {
                                Text("App is up to date", color = MaterialTheme.colorScheme.tertiary)
                                TextButton(
                                    onClick = { viewModel.resetUpdateState() },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Check Again") }
                            }
                            is SettingsViewModel.UpdateState.UpdateAvailable -> {
                                Text(
                                    "Update available: v${state.info.version}",
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Button(
                                    onClick = { viewModel.downloadAndInstall(state.info) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Download, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Download & Install v${state.info.version}")
                                }
                            }
                            is SettingsViewModel.UpdateState.Downloading -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(Modifier.size(20.dp))
                                    Text("Downloading update…")
                                }
                            }
                            is SettingsViewModel.UpdateState.ReadyToInstall ->
                                Text("Installation started…", color = MaterialTheme.colorScheme.tertiary)
                            is SettingsViewModel.UpdateState.Error -> {
                                Text(
                                    state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(onClick = { viewModel.resetUpdateState() }) { Text("Dismiss") }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Backup ────────────────────────────────────────────────────
                SectionHeader("Backup & Restore")
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Auto-backup toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Auto Backup", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Automatically create a backup after key events. A notification lets you save it anywhere — Google Drive, Dropbox, or local storage.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = autoBackup, onCheckedChange = { viewModel.setAutoBackupEnabled(it) })
                        }

                        HorizontalDivider()

                        // Manual export
                        Text("Export to Any Location", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Save a backup ZIP to any location — Google Drive, Dropbox, USB, or local storage. Android's file picker handles all providers automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        when (val state = exportState) {
                            is SettingsViewModel.ExportState.Exporting -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(Modifier.size(20.dp))
                                    Text("Exporting…", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            is SettingsViewModel.ExportState.Done -> {
                                Text(
                                    "Backup exported (${state.itemCount} items)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                TextButton(
                                    onClick = { viewModel.resetExportState() },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Export Again") }
                            }
                            is SettingsViewModel.ExportState.Error -> {
                                Text(
                                    "Export failed: ${state.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                TextButton(
                                    onClick = { viewModel.resetExportState() },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Dismiss") }
                            }
                            else -> {
                                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                Button(
                                    onClick = {
                                        exportLauncher.launch("${AppConfig.APP_NAME.lowercase()}_backup_$ts.zip")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Export Backup Now")
                                }
                            }
                        }

                        HorizontalDivider()

                        // Restore
                        Text("Restore from Backup", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Restore from a previously exported backup ZIP. Existing data will be replaced.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        when (val state = restoreState) {
                            is SettingsViewModel.RestoreState.Restoring -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(Modifier.size(20.dp))
                                    Text("Restoring…", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            is SettingsViewModel.RestoreState.Done -> {
                                Text(
                                    "Restored successfully (${state.itemCount} items)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                TextButton(
                                    onClick = { viewModel.resetRestoreState() },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Dismiss") }
                            }
                            is SettingsViewModel.RestoreState.Error -> {
                                Text(
                                    "Restore failed: ${state.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                TextButton(
                                    onClick = { viewModel.resetRestoreState() },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Dismiss") }
                            }
                            else -> {
                                OutlinedButton(
                                    onClick = { showRestoreDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.RestoreFromTrash, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Restore from Backup…")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Buddy Quick Navigation ────────────────────────────────────
                if (onOpenProgress != null || onOpenGarden != null) {
                    SectionHeader("Buddy")
                    Spacer(Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            onOpenProgress?.let {
                                OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                                    Text("📊 התקדמות ודוחות")
                                }
                            }
                            onOpenMemory?.let {
                                OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                                    Text("🧠 מה Buddy זוכר")
                                }
                            }
                            onOpenGarden?.let {
                                OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                                    Text("🌱 גן המילים")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── Support ───────────────────────────────────────────────────
                SectionHeader("Support")
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onOpenBugReport(ReportMode.BUG_REPORT) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.BugReport, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Report a Bug")
                        }
                        OutlinedButton(
                            onClick = { onOpenBugReport(ReportMode.USER_FEEDBACK) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Feedback, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Send Feedback")
                        }
                        OutlinedButton(
                            onClick = { showClearLogsDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.DeleteSweep, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Clear Logs")
                        }
                        if (logsCleared) {
                            Text(
                                "Logs cleared. Future reports will only include new activity.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider()

                        // Clear AI Memory
                        Text("איפוס זיכרון AI", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "מוחק את כל ההיסטוריה של השיחות, הזיכרון והמילים שנלמדו. Buddy יתחיל מחדש.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        when (val state = clearMemoryState) {
                            is SettingsViewModel.ClearMemoryState.Cleared -> {
                                Text(
                                    "הזיכרון נמחק בהצלחה. Buddy יתחיל שיחה חדשה.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                TextButton(onClick = { viewModel.resetClearMemoryState() }) {
                                    Text("אישור")
                                }
                            }
                            is SettingsViewModel.ClearMemoryState.Error -> {
                                Text(
                                    "שגיאה: ${state.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                TextButton(onClick = { viewModel.resetClearMemoryState() }) {
                                    Text("סגור")
                                }
                            }
                            else -> {
                                OutlinedButton(
                                    onClick = { showClearMemoryDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.RestoreFromTrash, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("מחק זיכרון AI")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Restore confirmation dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Backup") },
            text = {
                Text(
                    "This will permanently replace all current data with the contents of the selected backup. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreDialog = false
                        restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    }
                ) {
                    Text("Choose Backup File", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Clear AI Memory confirmation dialog (biometric-gated)
    if (showClearMemoryDialog) {
        ClearDataConfirmationDialog(
            title               = "מחק זיכרון AI",
            deletedDescription  = "כל השיחות, הזיכרון והמילים שנלמדו",
            preservedDescription = "מפתחות API, הגדרות ופרופיל הילד",
            onDismiss  = { showClearMemoryDialog = false },
            onConfirmed = {
                showClearMemoryDialog = false
                viewModel.clearAiMemory()
            }
        )
    }

    // Clear logs confirmation dialog
    if (showClearLogsDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            title = { Text("Clear Logs") },
            text = {
                Text("This removes all stored log history. Future bug reports will only include activity after this point.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllLogs()
                        showClearLogsDialog = false
                        logsCleared = true
                    }
                ) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Reusable password-field for storing an API key.
 * Shows stored status + live validation result when a key exists,
 * or an input + Save button when no key is set.
 */
@Composable
private fun ApiKeyField(
    label: String,
    hint: String,
    value: String,
    visible: Boolean,
    hasKey: Boolean,
    validation: KeyValidation = KeyValidation.Idle,
    onChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (hasKey) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (validation) {
                        is KeyValidation.Validating -> {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(4.dp))
                            Text("בודק…", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is KeyValidation.Ok -> {
                            Icon(Icons.Default.CheckCircle, null,
                                tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("תקין", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary)
                        }
                        is KeyValidation.Error -> {
                            Icon(Icons.Default.Warning, null,
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("שגיאה", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                        else -> {
                            // Idle — key set but not yet validated (loaded from storage)
                            Icon(Icons.Default.CheckCircle, null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("מוגדר", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onClear, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("נקה", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Show validation details below the label row
        if (hasKey) {
            when (validation) {
                is KeyValidation.Ok ->
                    Text(validation.info, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary)
                is KeyValidation.Error ->
                    Text(validation.message, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                else -> {}
            }
        }

        if (!hasKey) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value         = value,
                    onValueChange = onChange,
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = onToggleVisibility) {
                            Icon(
                                if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null, modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    placeholder = { Text("הדבק מפתח…") }
                )
                Button(onClick = onSave, enabled = value.isNotBlank()) {
                    Text("שמור")
                }
            }
        }
    }
}
