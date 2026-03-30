package com.itsikh.buddy.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itsikh.buddy.AppConfig
import com.itsikh.buddy.BuildConfig
import com.itsikh.buddy.security.ClearDataConfirmationDialog
import com.itsikh.buddy.ui.theme.AppTheme
import com.itsikh.buddy.ui.theme.colorSchemeFor
import com.itsikh.buddy.security.KeyValidation
import com.itsikh.buddy.voice.TtsBackend
import com.itsikh.buddy.ui.components.SettingsScaffold
import com.itsikh.buddy.ui.screens.bugreport.ReportMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val appTheme           by viewModel.appTheme.collectAsState()
    val levelsState        by viewModel.levelsState.collectAsState()

    val keyExportState     by viewModel.keyExportState.collectAsState()
    val keyRestoreState    by viewModel.keyRestoreState.collectAsState()
    val driveRestoreState  by viewModel.driveRestoreState.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? -> if (uri != null) viewModel.exportBackupToUri(uri) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) viewModel.restoreFromBackup(uri) }

    // Key backup: password entered first, then file picker
    var pendingKeyExportPassword by remember { mutableStateOf("") }
    val keyExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null && pendingKeyExportPassword.isNotBlank()) {
            viewModel.exportKeyBackupToUri(uri, pendingKeyExportPassword)
        }
        pendingKeyExportPassword = ""
    }

    // Key restore: file picked first, then password dialog
    var pendingKeyRestoreUri by remember { mutableStateOf<Uri?>(null) }
    val keyRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) { pendingKeyRestoreUri = uri }
    }

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

    var githubToken           by remember { mutableStateOf("") }
    var tokenVisible          by remember { mutableStateOf(false) }
    var hasToken              by remember { mutableStateOf(viewModel.hasGitHubToken) }
    var showRestoreDialog             by remember { mutableStateOf(false) }
    var showKeyExportPasswordDialog   by remember { mutableStateOf(false) }
    var showKeyRestorePasswordDialog  by remember { mutableStateOf(false) }
    var showDriveRestoreDialog        by remember { mutableStateOf(false) }
    var showClearLogsDialog    by remember { mutableStateOf(false) }
    var showClearMemoryDialog  by remember { mutableStateOf(false) }
    var logsCleared           by remember { mutableStateOf(false) }
    var geminiKey      by remember { mutableStateOf("") }
    var claudeKey      by remember { mutableStateOf("") }
    var googleTtsKey   by remember { mutableStateOf("") }
    var geminiVisible  by remember { mutableStateOf(false) }
    var claudeVisible  by remember { mutableStateOf(false) }
    var ttsVisible     by remember { mutableStateOf(false) }
    var buddyGender    by remember { mutableStateOf(viewModel.getBuddyGender()) }

    // When key restore file is picked, show password dialog
    if (pendingKeyRestoreUri != null && !showKeyRestorePasswordDialog) {
        showKeyRestorePasswordDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("הגדרות") },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val activeProvider = driveUiState.aiDefaultProvider
            val ttsVoice = if (buddyGender == AppConfig.BUDDY_GENDER_GIRL) "he-IL-Wavenet-A" else "he-IL-Wavenet-B"
            val ttsChirpVoice = if (buddyGender == AppConfig.BUDDY_GENDER_BOY) "he-IL-Chirp3-HD-Puck" else "he-IL-Chirp3-HD-Aoede"
            val ttsActive = when (ttsBackend) {
                TtsBackend.GOOGLE_CLOUD_CHIRP   -> "$ttsChirpVoice (Chirp3-HD)"
                TtsBackend.GOOGLE_CLOUD_WAVENET -> "$ttsVoice (WaveNet)"
                TtsBackend.ANDROID_FALLBACK     -> "Android TTS (he-IL) [fallback]"
                TtsBackend.UNKNOWN              -> "$ttsChirpVoice (not yet spoken)"
            }
            val modelDebugInfo = listOf(
                "AI provider" to if (activeProvider == AppConfig.AI_PROVIDER_CLAUDE) "Claude (primary)" else "Gemini (primary)",
                "Gemini chat" to "gemini-2.5-flash",
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

                // ── 0. Appearance ─────────────────────────────────────────────
                ExpandableSection(icon = "🎨", title = "מראה — ערכת צבעים") {
                    Text(
                        "בחר ערכת צבעים לאפליקציה",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppTheme.entries.forEach { theme ->
                            val scheme = colorSchemeFor(theme)
                            val isSelected = appTheme == theme
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setAppTheme(theme) }
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .then(
                                            if (isSelected) Modifier.border(
                                                3.dp,
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape
                                            ) else Modifier
                                        )
                                        .padding(4.dp)
                                        .size(36.dp)
                                        .background(scheme.primary, CircleShape)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    theme.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ── 0b. English Levels ───────────────────────────────────────
                ExpandableSection(icon = "📚", title = "רמות אנגלית — CEFR") {
                    LevelsSection(
                        levelsState = levelsState,
                        adminMode   = adminMode,
                        onSetCoins  = { viewModel.setCoins(it) }
                    )
                }

                // ── 1. Child Profile ──────────────────────────────────────────
                ExpandableSection(icon = "👤", title = "פרופיל הילד") {
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

                // ── 2. Buddy AI ───────────────────────────────────────────────
                ExpandableSection(icon = "🤖", title = "Buddy — קול ובינה מלאכותית") {
                    Text("קול Buddy", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "קובע את מגדר הקול בעברית ואת הדקדוק של Buddy בשיחה",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = buddyGender == "GIRL",
                            onClick  = { buddyGender = "GIRL"; viewModel.setBuddyGender("GIRL") },
                            label    = { Text("ילדה 👧 (ברירת מחדל)") }
                        )
                        FilterChip(
                            selected = buddyGender == "BOY",
                            onClick  = { buddyGender = "BOY"; viewModel.setBuddyGender("BOY") },
                            label    = { Text("ילד 👦") }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("מנוע AI ראשי", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = driveUiState.aiDefaultProvider == AppConfig.AI_PROVIDER_GEMINI,
                            onClick  = { viewModel.setAiDefaultProvider(AppConfig.AI_PROVIDER_GEMINI) },
                            label    = { Text("Gemini (מומלץ)") }
                        )
                        FilterChip(
                            selected = driveUiState.aiDefaultProvider == AppConfig.AI_PROVIDER_CLAUDE,
                            onClick  = { viewModel.setAiDefaultProvider(AppConfig.AI_PROVIDER_CLAUDE) },
                            label    = { Text("Claude") }
                        )
                    }
                }

                // ── 3. Google Drive ───────────────────────────────────────────
                ExpandableSection(icon = "☁️", title = "Google Drive — סנכרון") {
                    if (driveUiState.isSignedIn) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("מחובר", style = MaterialTheme.typography.labelLarge)
                                driveUiState.accountEmail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick  = { viewModel.syncNow() },
                                enabled  = !driveUiState.isSyncing
                            ) {
                                if (driveUiState.isSyncing) CircularProgressIndicator(Modifier.size(16.dp))
                                else Icon(Icons.Default.CloudUpload, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("סנכרן עכשיו")
                            }
                            TextButton(onClick = { viewModel.signOutFromGoogle() }) {
                                Text("התנתק", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        driveUiState.syncError?.let {
                            Text("שגיאה: $it", color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(
                            "חבר Google Drive לגיבוי אוטומטי של ההתקדמות, זיכרון Buddy, ומילים שנלמדו.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick  = { googleSignInLauncher.launch(viewModel.getDriveSignInIntent()) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("התחבר עם Google") }
                        driveUiState.syncError?.let { err ->
                            Spacer(Modifier.height(4.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("שגיאת חיבור", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer)
                                    Text(err, color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall)
                                    if (err.contains("10") || err.contains("DEVELOPER_ERROR", ignoreCase = true) || err.contains("SHA", ignoreCase = true)) {
                                        Text(
                                            "פתרון: יש לרשום את ה-SHA-1 של האפליקציה ב-Google Cloud Console תחת OAuth 2.0 → Android app. ודא שה-google-services.json עדכני.",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 4. Progress & Learning ────────────────────────────────────
                if (onOpenProgress != null || onOpenMemory != null || onOpenGarden != null) {
                    ExpandableSection(icon = "📊", title = "התקדמות ולמידה") {
                        onOpenProgress?.let {
                            OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                                Text("📈 דוחות התקדמות")
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

                // ── 5. Backup & Restore ───────────────────────────────────────
                ExpandableSection(icon = "🛡️", title = "גיבוי ושחזור") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("גיבוי אוטומטי", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "יצור גיבוי אוטומטי לאחר אירועים חשובים.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = autoBackup, onCheckedChange = { viewModel.setAutoBackupEnabled(it) })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("ייצוא גיבוי", style = MaterialTheme.typography.labelLarge)
                    when (val state = exportState) {
                        is SettingsViewModel.ExportState.Exporting -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                                Text("מייצא…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is SettingsViewModel.ExportState.Done -> {
                            Text("גיבוי יוצא (${state.itemCount} פריטים)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            TextButton(onClick = { viewModel.resetExportState() }, modifier = Modifier.fillMaxWidth()) { Text("ייצא שוב") }
                        }
                        is SettingsViewModel.ExportState.Error -> {
                            Text("שגיאה: ${state.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.resetExportState() }, modifier = Modifier.fillMaxWidth()) { Text("סגור") }
                        }
                        else -> {
                            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            Button(
                                onClick = { exportLauncher.launch("${AppConfig.APP_NAME.lowercase()}_backup_$ts.zip") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudUpload, null)
                                Spacer(Modifier.width(8.dp))
                                Text("ייצא גיבוי")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("שחזור מגיבוי", style = MaterialTheme.typography.labelLarge)
                    when (val state = restoreState) {
                        is SettingsViewModel.RestoreState.Restoring -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                                Text("משחזר…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is SettingsViewModel.RestoreState.Done -> {
                            Text("שוחזר בהצלחה (${state.itemCount} פריטים)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            TextButton(onClick = { viewModel.resetRestoreState() }, modifier = Modifier.fillMaxWidth()) { Text("אישור") }
                        }
                        is SettingsViewModel.RestoreState.Error -> {
                            Text("שגיאה: ${state.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.resetRestoreState() }, modifier = Modifier.fillMaxWidth()) { Text("סגור") }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = { showRestoreDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.RestoreFromTrash, null)
                                Spacer(Modifier.width(8.dp))
                                Text("שחזר מגיבוי…")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // ── Encrypted key backup ──────────────────────────────────
                    Text("מפתחות API (מוצפן)", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "גיבוי מוצפן של כל מפתחות ה-API. לשחזור נדרשת הסיסמה.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    when (val state = keyExportState) {
                        is SettingsViewModel.KeyExportState.Exporting -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                                Text("מייצא…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is SettingsViewModel.KeyExportState.Done -> {
                            Text("מפתחות יוצאו בהצלחה", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            TextButton(onClick = { viewModel.resetKeyExportState() }, modifier = Modifier.fillMaxWidth()) { Text("אישור") }
                        }
                        is SettingsViewModel.KeyExportState.Error -> {
                            Text("שגיאה: ${state.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.resetKeyExportState() }, modifier = Modifier.fillMaxWidth()) { Text("סגור") }
                        }
                        else -> {
                            Button(
                                onClick = { showKeyExportPasswordDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudUpload, null)
                                Spacer(Modifier.width(8.dp))
                                Text("ייצא מפתחות (עם סיסמה)")
                            }
                        }
                    }

                    when (val state = keyRestoreState) {
                        is SettingsViewModel.KeyRestoreState.Restoring -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                                Text("משחזר מפתחות…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is SettingsViewModel.KeyRestoreState.Done -> {
                            Text("מפתחות שוחזרו בהצלחה", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            TextButton(onClick = { viewModel.resetKeyRestoreState() }, modifier = Modifier.fillMaxWidth()) { Text("אישור") }
                        }
                        is SettingsViewModel.KeyRestoreState.Error -> {
                            Text("שגיאה: ${state.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.resetKeyRestoreState() }, modifier = Modifier.fillMaxWidth()) { Text("סגור") }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = {
                                    keyRestoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, null)
                                Spacer(Modifier.width(8.dp))
                                Text("שחזר מפתחות…")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // ── Drive restore ─────────────────────────────────────────
                    Text("שחזור מ-Google Drive", style = MaterialTheme.typography.labelLarge)
                    when (val state = driveRestoreState) {
                        is SettingsViewModel.DriveRestoreState.Restoring -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                                Text("משחזר מ-Drive…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is SettingsViewModel.DriveRestoreState.Done -> {
                            Text("שוחזר מ-Drive בהצלחה", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            TextButton(onClick = { viewModel.resetDriveRestoreState() }, modifier = Modifier.fillMaxWidth()) { Text("אישור") }
                        }
                        is SettingsViewModel.DriveRestoreState.Error -> {
                            Text("שגיאה: ${state.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.resetDriveRestoreState() }, modifier = Modifier.fillMaxWidth()) { Text("סגור") }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = { showDriveRestoreDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = driveUiState.isSignedIn,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.RestoreFromTrash, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (driveUiState.isSignedIn) "שחזר מ-Google Drive…" else "נדרש חיבור ל-Google Drive")
                            }
                        }
                    }
                }

                // ── 6. Support ────────────────────────────────────────────────
                ExpandableSection(icon = "🐛", title = "תמיכה ודיווח") {
                    Button(
                        onClick = { onOpenBugReport(ReportMode.BUG_REPORT) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.BugReport, null)
                        Spacer(Modifier.width(8.dp))
                        Text("דווח על תקלה")
                    }
                    OutlinedButton(
                        onClick = { onOpenBugReport(ReportMode.USER_FEEDBACK) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Feedback, null)
                        Spacer(Modifier.width(8.dp))
                        Text("שלח משוב")
                    }
                    OutlinedButton(
                        onClick = { showClearLogsDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteSweep, null)
                        Spacer(Modifier.width(8.dp))
                        Text("נקה לוגים")
                    }
                    if (logsCleared) {
                        Text("הלוגים נוקו.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("איפוס זיכרון AI", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "מוחק את כל ההיסטוריה של השיחות, הזיכרון והמילים שנלמדו.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    when (val state = clearMemoryState) {
                        is SettingsViewModel.ClearMemoryState.Cleared -> {
                            Text("הזיכרון נמחק. Buddy יתחיל מחדש.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            TextButton(onClick = { viewModel.resetClearMemoryState() }) { Text("אישור") }
                        }
                        is SettingsViewModel.ClearMemoryState.Error -> {
                            Text("שגיאה: ${state.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.resetClearMemoryState() }) { Text("סגור") }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = { showClearMemoryDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.RestoreFromTrash, null)
                                Spacer(Modifier.width(8.dp))
                                Text("מחק זיכרון AI")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // GitHub Token (inside Support)
                    Text("GitHub Token", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "נדרש לדיווח תקלות ובדיקת עדכונים. צור ב-github.com → Settings → Developer settings → Personal access tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hasToken) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                            Text("Token מוגדר", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.clearGitHubToken(); hasToken = false }) {
                                Text("נקה", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = githubToken,
                                onValueChange = { githubToken = it },
                                label = { Text("Personal Access Token") },
                                placeholder = { Text("ghp_...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                        Icon(if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            )
                            Button(
                                onClick = { viewModel.saveGitHubToken(githubToken); hasToken = viewModel.hasGitHubToken; githubToken = ""; tokenVisible = false },
                                enabled = githubToken.isNotBlank()
                            ) { Text("שמור") }
                        }
                    }
                }

                // ── 7. Auto-Update (second-to-last) ──────────────────────────
                ExpandableSection(icon = "🔄", title = "עדכון גרסה") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("בדיקת עדכונים אוטומטית", style = MaterialTheme.typography.bodyLarge)
                            Text("בדוק עדכון מ-GitHub בכל הפעלה", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoUpdate, onCheckedChange = { viewModel.setAutoUpdateEnabled(it) })
                    }
                    when (val state = updateState) {
                        is SettingsViewModel.UpdateState.Idle -> {
                            Button(onClick = { viewModel.checkForUpdate() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Refresh, null)
                                Spacer(Modifier.width(8.dp))
                                Text("בדוק עכשיו")
                            }
                        }
                        is SettingsViewModel.UpdateState.Checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                                Text("בודק…")
                            }
                        }
                        is SettingsViewModel.UpdateState.UpToDate -> {
                            Text("הגרסה עדכנית", color = MaterialTheme.colorScheme.tertiary)
                            TextButton(onClick = { viewModel.resetUpdateState() }, modifier = Modifier.fillMaxWidth()) { Text("בדוק שוב") }
                        }
                        is SettingsViewModel.UpdateState.UpdateAvailable -> {
                            Text("עדכון זמין: v${state.info.version}", color = MaterialTheme.colorScheme.primary)
                            Button(onClick = { viewModel.downloadAndInstall(state.info) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Download, null)
                                Spacer(Modifier.width(8.dp))
                                Text("הורד והתקן v${state.info.version}")
                            }
                        }
                        is SettingsViewModel.UpdateState.Downloading -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                                Text("מוריד עדכון…")
                            }
                        }
                        is SettingsViewModel.UpdateState.ReadyToInstall ->
                            Text("התקנה החלה…", color = MaterialTheme.colorScheme.tertiary)
                        is SettingsViewModel.UpdateState.Error -> {
                            Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { viewModel.resetUpdateState() }) { Text("סגור") }
                        }
                    }
                }

                // About is rendered last by SettingsScaffold (7-tap to unlock admin mode)
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("שחזר גיבוי") },
            text = { Text("זה ימחק את כל הנתונים הנוכחיים ויחליף בגיבוי שתבחר. פעולה זו בלתי הפיכה.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreDialog = false
                    restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                }) { Text("בחר קובץ גיבוי", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showRestoreDialog = false }) { Text("ביטול") } }
        )
    }

    // ── Key export password dialog ─────────────────────────────────────────────
    if (showKeyExportPasswordDialog) {
        var exportPwd  by remember { mutableStateOf("") }
        var exportPwd2 by remember { mutableStateOf("") }
        var pwdVisible by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showKeyExportPasswordDialog = false },
            title = { Text("סיסמה לגיבוי מפתחות") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("בחר סיסמה להצפנת הגיבוי. תזדקק לה לשחזור.")
                    OutlinedTextField(
                        value = exportPwd,
                        onValueChange = { exportPwd = it },
                        label = { Text("סיסמה") },
                        visualTransformation = if (pwdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { pwdVisible = !pwdVisible }) {
                                Icon(if (pwdVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = exportPwd2,
                        onValueChange = { exportPwd2 = it },
                        label = { Text("אשר סיסמה") },
                        visualTransformation = if (pwdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = exportPwd2.isNotEmpty() && exportPwd != exportPwd2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (exportPwd2.isNotEmpty() && exportPwd != exportPwd2) {
                        Text("הסיסמאות אינן זהות", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showKeyExportPasswordDialog = false
                        pendingKeyExportPassword = exportPwd
                        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        keyExportLauncher.launch("buddy_keys_$ts.buddy_keys")
                    },
                    enabled = exportPwd.length >= 4 && exportPwd == exportPwd2
                ) { Text("ייצא") }
            },
            dismissButton = { TextButton(onClick = { showKeyExportPasswordDialog = false }) { Text("ביטול") } }
        )
    }

    // ── Key restore password dialog ────────────────────────────────────────────
    if (showKeyRestorePasswordDialog) {
        var restorePwd by remember { mutableStateOf("") }
        var pwdVisible by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                showKeyRestorePasswordDialog = false
                pendingKeyRestoreUri = null
            },
            title = { Text("סיסמה לשחזור מפתחות") },
            text = {
                OutlinedTextField(
                    value = restorePwd,
                    onValueChange = { restorePwd = it },
                    label = { Text("סיסמה") },
                    visualTransformation = if (pwdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { pwdVisible = !pwdVisible }) {
                            Icon(if (pwdVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingKeyRestoreUri
                        showKeyRestorePasswordDialog = false
                        pendingKeyRestoreUri = null
                        if (uri != null) viewModel.restoreKeyBackupFromUri(uri, restorePwd)
                    },
                    enabled = restorePwd.isNotBlank()
                ) { Text("שחזר") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showKeyRestorePasswordDialog = false
                    pendingKeyRestoreUri = null
                }) { Text("ביטול") }
            }
        )
    }

    // ── Drive restore confirmation dialog ──────────────────────────────────────
    if (showDriveRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showDriveRestoreDialog = false },
            title = { Text("שחזר מ-Google Drive") },
            text = { Text("זה ימחק את כל הנתונים המקומיים ויחליף בגיבוי מ-Drive. פעולה זו בלתי הפיכה.") },
            confirmButton = {
                TextButton(onClick = {
                    showDriveRestoreDialog = false
                    viewModel.restoreFromDrive()
                }) { Text("שחזר", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDriveRestoreDialog = false }) { Text("ביטול") } }
        )
    }

    if (showClearMemoryDialog) {
        ClearDataConfirmationDialog(
            title               = "מחק זיכרון AI",
            deletedDescription  = "כל השיחות, הזיכרון והמילים שנלמדו",
            preservedDescription = "מפתחות API, הגדרות ופרופיל הילד",
            onDismiss  = { showClearMemoryDialog = false },
            onConfirmed = { showClearMemoryDialog = false; viewModel.clearAiMemory() }
        )
    }

    if (showClearLogsDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            title = { Text("נקה לוגים") },
            text = { Text("מוחק את כל היסטוריית הלוגים. דוחות עתידיים יכללו רק פעילות חדשה.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllLogs(); showClearLogsDialog = false; logsCleared = true }) {
                    Text("נקה", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showClearLogsDialog = false }) { Text("ביטול") } }
        )
    }
}

// ── CEFR levels info section ──────────────────────────────────────────────────

private data class CefrLevel(
    val code: String,
    val name: String,
    val emoji: String,
    val description: String,
    val howToAdvance: String,
    val order: Int
)

private val cefrLevels = listOf(
    CefrLevel(
        code = "A1", name = "מתחיל", emoji = "🌱",
        description = "מילים בודדות ומשפטים קצרים מאוד. אוצר מילים של עד 500 מילים. מדבר על עצמו, צבעים, מספרים, בעלי חיים.",
        howToAdvance = "לדבר עם Buddy לפחות 3 פעמים בשבוע. ללמוד 5 מילים חדשות בכל שיחה.",
        order = 1
    ),
    CefrLevel(
        code = "A2", name = "בסיסי", emoji = "🌿",
        description = "משפטים קצרים על נושאים מוכרים. מתאר פעילויות יומיות, תחביבים, משפחה. כ-1,000 מילים.",
        howToAdvance = "להגיב ב-2-3 מילים לפחות בכל תור. לנסות לבנות משפטים שלמים כמו 'I like...' / 'Yesterday I...'",
        order = 2
    ),
    CefrLevel(
        code = "B1", name = "בינוני", emoji = "🌳",
        description = "שיחה על נושאים מגוונים, הבעת דעות ורגשות. מתאר אירועים ומסביר סיבות. כ-2,000 מילים.",
        howToAdvance = "להשתמש במשפטים מחוברים עם 'because', 'but', 'so'. לנסות role-play ומשחקי תפקידים.",
        order = 3
    ),
    CefrLevel(
        code = "B2", name = "עצמאי", emoji = "🏆",
        description = "שיחה שוטפת ורהוטה. מבין טקסטים מורכבים. מכיר כ-4,000 מילים.",
        howToAdvance = "לדון בנושאים מופשטים ולהביע דעות מורכבות. לקרוא ולספר סיפורים.",
        order = 4
    ),
)

@Composable
private fun LevelsSection(
    levelsState: SettingsViewModel.LevelsState,
    adminMode: Boolean = false,
    onSetCoins: (Int) -> Unit = {}
) {
    var coinEditText by remember(levelsState.coins) { mutableStateOf(levelsState.coins.toString()) }

    // Current levels bar
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "הרמה הנוכחית",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            LevelRow("כולל", levelsState.overall)
            LevelRow("דיבור", levelsState.speaking)
            LevelRow("אוצר מילים", levelsState.vocabulary)
            LevelRow("דקדוק", levelsState.grammar)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("⭐ ${levelsState.xpTotal} XP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("📖 ${levelsState.wordsLearned} מילים", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("⏱ ${levelsState.sessionMinutes} דקות", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("🪙 ${levelsState.coins} מטבעות", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }

    // Admin coin editor
    if (adminMode) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🛡️ עריכת מטבעות (Admin)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = coinEditText,
                        onValueChange = { if (it.all { c -> c.isDigit() }) coinEditText = it },
                        label = { Text("מטבעות") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    Button(
                        onClick = { onSetCoins(coinEditText.toIntOrNull() ?: levelsState.coins) },
                        enabled = coinEditText.toIntOrNull() != null
                    ) { Text("שמור") }
                }
            }
        }
    }

    Spacer(Modifier.height(4.dp))
    Text(
        "מה כל רמה אומרת?",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface
    )

    // CEFR level cards
    cefrLevels.forEach { level ->
        val isCurrent = level.code == levelsState.overall
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
                                 else MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(level.emoji, fontSize = 20.sp)
                    Text(
                        "${level.code} — ${level.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface
                    )
                    if (isCurrent) {
                        Text(
                            "← הרמה שלך",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    level.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "📈 איך מתקדמים: ${level.howToAdvance}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LevelRow(label: String, level: String) {
    val progress = when (level) {
        "A1" -> 0.25f; "A2" -> 0.5f; "B1" -> 0.75f; "B2" -> 1f; else -> 0.25f
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.width(80.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
        )
        Text(level, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.width(24.dp))
    }
}

// ── Expandable section card ────────────────────────────────────────────────────

@Composable
private fun ExpandableSection(
    icon: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

// ── API key field ──────────────────────────────────────────────────────────────

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
                            Text("בודק…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is KeyValidation.Ok -> {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("תקין", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        is KeyValidation.Error -> {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("שגיאה", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        else -> {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("מוגדר", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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

        if (hasKey) {
            when (validation) {
                is KeyValidation.Ok -> Text(validation.info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                is KeyValidation.Error -> Text(validation.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                else -> {}
            }
        }

        if (!hasKey) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value         = value,
                    onValueChange = onChange,
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = onToggleVisibility) {
                            Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, modifier = Modifier.size(18.dp))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    placeholder = { Text("הדבק מפתח…") }
                )
                Button(onClick = onSave, enabled = value.isNotBlank()) { Text("שמור") }
            }
        }
    }
}
