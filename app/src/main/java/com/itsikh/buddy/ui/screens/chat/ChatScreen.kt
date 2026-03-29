package com.itsikh.buddy.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itsikh.buddy.R
import com.itsikh.buddy.data.models.ChatMode
import com.itsikh.buddy.data.models.Message
import com.itsikh.buddy.gamification.BadgeDefinitions
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic   = LocalHapticFeedback.current
    val context  = LocalContext.current

    // ── Microphone runtime permission ────────────────────────────────────
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val requestMicPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    // ── Re-check API key when returning from Settings ────────────────────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.recheckApiKey()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.startSession(ChatMode.FREE_CHAT)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.endSession() }
    }

    // ── Badge earned dialog ──────────────────────────────────────────────
    val newBadges = uiState.newBadges
    if (newBadges.isNotEmpty()) {
        val badge = BadgeDefinitions.findById(newBadges.first())
        if (badge != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearNewBadges() },
                icon  = { Text(badge.icon, fontSize = 40.sp) },
                title = { Text("כל הכבוד! 🎉", textAlign = TextAlign.Center) },
                text  = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(badge.nameHe, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(badge.descriptionHe, textAlign = TextAlign.Center)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearNewBadges() }) { Text("תודה! 😊") }
                }
            )
        }
    }

    // ── Auto-dismiss error after 3 s ────────────────────────────────────
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            kotlinx.coroutines.delay(3000)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                profile        = uiState.profile,
                streakDays     = uiState.streakDays,
                onOpenSettings = onOpenSettings
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // ── No API key banner ────────────────────────────────────────
            AnimatedVisibility(visible = uiState.noApiKey) {
                NoApiKeyBanner(onOpenSettings = onOpenSettings)
            }

            // ── Mode selector tabs ───────────────────────────────────────
            ModeSelector(
                currentMode  = uiState.mode,
                onModeChange = { viewModel.switchMode(it) }
            )

            // ── Messages list ────────────────────────────────────────────
            MessagesList(
                messages    = uiState.messages,
                voiceState  = uiState.voiceState,
                partialText = uiState.partialSpeechText,
                modifier    = Modifier.weight(1f)
            )

            // ── Transient error bar ──────────────────────────────────────
            AnimatedVisibility(visible = uiState.error != null) {
                Surface(
                    color    = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text      = uiState.error ?: "",
                        color     = MaterialTheme.colorScheme.onErrorContainer,
                        modifier  = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── Voice control bar ────────────────────────────────────────
            VoiceControlBar(
                voiceState         = uiState.voiceState,
                hasAudioPermission = hasAudioPermission,
                onRequestPermission = { requestMicPermission.launch(Manifest.permission.RECORD_AUDIO) },
                onPressDown = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.startListening()
                },
                onPressUp   = { viewModel.stopListening() }
            )
        }
    }
}

// ── No API key banner ──────────────────────────────────────────────────────
@Composable
private fun NoApiKeyBanner(onOpenSettings: () -> Unit) {
    Surface(
        color    = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🔑", fontSize = 20.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "חסר מפתח AI",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    color      = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "הגדר מפתח Gemini או Claude בהגדרות כדי שBuddy יוכל לדבר",
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            // Force LTR for the button so the arrow points correctly
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                FilledTonalButton(
                    onClick  = onOpenSettings,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("הגדרות", fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Top bar ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    profile: com.itsikh.buddy.data.models.ChildProfile?,
    streakDays: Int,
    onOpenSettings: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤖", fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Buddy", fontWeight = FontWeight.Bold)
                    profile?.let {
                        Text(
                            "שלום, ${it.displayName}! רמה ${it.cefrLevel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        actions = {
            if (streakDays > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("🔥", fontSize = 18.sp)
                    Text(
                        "$streakDays",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "הגדרות")
            }
        }
    )
}

// ── Mode selector ──────────────────────────────────────────────────────────
@Composable
private fun ModeSelector(
    currentMode: ChatMode,
    onModeChange: (ChatMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChatMode.entries.forEach { mode ->
            FilterChip(
                selected = currentMode == mode,
                onClick  = { onModeChange(mode) },
                label = {
                    Text(
                        text = when (mode) {
                            ChatMode.FREE_CHAT  -> stringResource(R.string.chat_mode_free)
                            ChatMode.STORY_TIME -> stringResource(R.string.chat_mode_story)
                            ChatMode.ROLE_PLAY  -> stringResource(R.string.chat_mode_roleplay)
                        },
                        fontSize = 12.sp
                    )
                },
                leadingIcon = {
                    Text(
                        when (mode) {
                            ChatMode.FREE_CHAT  -> "💬"
                            ChatMode.STORY_TIME -> "📖"
                            ChatMode.ROLE_PLAY  -> "🎭"
                        },
                        fontSize = 14.sp
                    )
                }
            )
        }
    }
}

// ── Messages list ──────────────────────────────────────────────────────────
@Composable
private fun MessagesList(
    messages: List<Message>,
    voiceState: VoiceState,
    partialText: String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()

    LaunchedEffect(messages.size, voiceState) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    LazyColumn(
        state    = listState,
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(message = message)
        }

        if (partialText.isNotBlank()) {
            item { PartialSpeechBubble(text = partialText) }
        }

        if (voiceState == VoiceState.THINKING || voiceState == VoiceState.SPEAKING) {
            item { BuddyStatusBubble(voiceState = voiceState) }
        }
    }
}

// ── Message bubble ─────────────────────────────────────────────────────────
// Uses absoluteLeft/absoluteRight so user messages are ALWAYS on the right
// and AI messages ALWAYS on the left, regardless of global layout direction.
@Composable
private fun MessageBubble(message: Message) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        // Absolute positioning: unaffected by the global RTL CompositionLocal
        horizontalArrangement = if (isUser) Arrangement.Absolute.Right else Arrangement.Absolute.Left
    ) {
        if (!isUser) {
            Text("🤖", fontSize = 20.sp, modifier = Modifier.padding(end = 4.dp, top = 4.dp))
        }

        Surface(
            shape = if (isUser) {
                RoundedCornerShape(
                    topStart    = 16.dp,
                    topEnd      = 4.dp,
                    bottomStart = 16.dp,
                    bottomEnd   = 16.dp
                )
            } else {
                RoundedCornerShape(
                    topStart    = 4.dp,
                    topEnd      = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd   = 16.dp
                )
            },
            color = if (isUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // Force LTR for message content: English text should flow left-to-right
            // even though the app layout is RTL.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text  = message.text,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = TextDirection.ContentOrLtr
                    )
                )
            }
        }
    }
}

// ── Partial speech bubble (always user = right) ────────────────────────────
@Composable
private fun PartialSpeechBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Absolute.Right
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp
            ),
            color    = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = text, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(4.dp))
                    Text("🎤", fontSize = 14.sp)
                }
            }
        }
    }
}

// ── Buddy status bubble (always AI = left) ─────────────────────────────────
@Composable
private fun BuddyStatusBubble(voiceState: VoiceState) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot_anim")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Absolute.Left
    ) {
        Text("🤖", fontSize = 20.sp, modifier = Modifier.padding(end = 4.dp, top = 4.dp))
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = when (voiceState) {
                    VoiceState.THINKING -> stringResource(R.string.chat_thinking)
                    VoiceState.SPEAKING -> stringResource(R.string.chat_speaking)
                    else                -> "..."
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color    = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha),
                style    = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ── Voice control bar ──────────────────────────────────────────────────────
@Composable
private fun VoiceControlBar(
    voiceState: VoiceState,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPressDown: () -> Unit,
    onPressUp: () -> Unit
) {
    val isListening = voiceState == VoiceState.LISTENING
    val scale by animateFloatAsState(
        targetValue   = if (isListening) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "button_scale"
    )

    Surface(
        modifier       = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color          = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier              = Modifier.padding(16.dp),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            // ── Permission not granted: show a clear request button ────
            if (!hasAudioPermission) {
                Column(
                    horizontalAlignment   = Alignment.CenterHorizontally,
                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "נדרשת גישה למיקרופון כדי לדבר עם Buddy",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = onRequestPermission) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("אפשר גישה למיקרופון")
                    }
                }
                return@Column
            }

            // ── Status label ───────────────────────────────────────────
            Text(
                text = when (voiceState) {
                    VoiceState.IDLE      -> stringResource(R.string.chat_hold_to_speak)
                    VoiceState.LISTENING -> stringResource(R.string.chat_listening)
                    VoiceState.THINKING  -> stringResource(R.string.chat_thinking)
                    VoiceState.SPEAKING  -> stringResource(R.string.chat_speaking)
                },
                style    = MaterialTheme.typography.labelLarge,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // ── Push-to-talk button ────────────────────────────────────
            val enabled = voiceState == VoiceState.IDLE || voiceState == VoiceState.LISTENING
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale)
                    .background(
                        color = when {
                            !enabled    -> MaterialTheme.colorScheme.surfaceVariant
                            isListening -> MaterialTheme.colorScheme.error
                            else        -> MaterialTheme.colorScheme.primary
                        },
                        shape = CircleShape
                    )
                    .pointerInput(enabled, voiceState) {
                        if (!enabled) return@pointerInput
                        detectTapGestures(
                            onPress = {
                                onPressDown()
                                tryAwaitRelease()
                                onPressUp()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (voiceState) {
                        VoiceState.LISTENING -> Icons.Default.Stop
                        VoiceState.SPEAKING  -> Icons.Default.VolumeUp
                        VoiceState.THINKING  -> Icons.Default.HourglassTop
                        else                 -> Icons.Default.Mic
                    },
                    contentDescription = "Push to talk",
                    tint     = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
