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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun ChatScreen(
    initialMode:    ChatMode = ChatMode.FREE_CHAT,
    onOpenSettings: () -> Unit,
    onBack:         (() -> Unit)? = null,
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
        viewModel.startSession(initialMode)
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
                currentMode    = uiState.mode,
                onModeChange   = { viewModel.switchMode(it) },
                onOpenSettings = onOpenSettings,
                onBack         = onBack
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

            // ── Conversational face area ─────────────────────────────────
            ConversationArea(
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
                voiceState          = uiState.voiceState,
                hasAudioPermission  = hasAudioPermission,
                onRequestPermission = { requestMicPermission.launch(Manifest.permission.RECORD_AUDIO) },
                onPressDown = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.startListening()
                },
                onPressUp = { viewModel.stopListening() }
            )
        }
    }
}

// ── Conversational face area ───────────────────────────────────────────────
// Shows an animated Buddy face + the current exchange. Feels like talking
// to a person rather than reading a text chat log.
@Composable
private fun ConversationArea(
    messages: List<Message>,
    voiceState: VoiceState,
    partialText: String,
    modifier: Modifier = Modifier
) {
    // Split last buddy / user messages
    val lastBuddy = messages.lastOrNull { it.role == "assistant" }
    val lastUser  = messages.lastOrNull { it.role == "user" }

    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(Modifier.height(12.dp))

        // ── Animated Buddy avatar ────────────────────────────────────────
        BuddyAvatar(voiceState = voiceState, modifier = Modifier.size(160.dp))

        Spacer(Modifier.height(20.dp))

        // ── Buddy's current message ──────────────────────────────────────
        val displayText = when {
            voiceState == VoiceState.THINKING -> null   // dots shown inside avatar ring
            voiceState == VoiceState.SPEAKING && lastBuddy != null -> lastBuddy.text
            lastBuddy != null -> lastBuddy.text
            else -> null
        }

        AnimatedContent(
            targetState   = displayText,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically { it / 4 })
                    .togetherWith(fadeOut(tween(150)))
            },
            label = "buddy_message"
        ) { text ->
            if (text != null) {
                BuddySpeechBubble(
                    text     = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            } else if (voiceState == VoiceState.THINKING) {
                ThinkingIndicator(
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            } else {
                Spacer(Modifier.height(80.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        // ── User's last turn ─────────────────────────────────────────────
        val userText = if (partialText.isNotBlank()) partialText else lastUser?.text
        AnimatedVisibility(
            visible = userText != null,
            enter   = fadeIn() + slideInVertically { it / 3 },
            exit    = fadeOut()
        ) {
            UserEcho(
                text     = userText ?: "",
                partial  = partialText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

// ── Animated Buddy avatar ──────────────────────────────────────────────────
@Composable
private fun BuddyAvatar(voiceState: VoiceState, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar")

    // Breathing scale — always subtle
    val breathe by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.04f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // Speaking ring pulse
    val ringScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.22f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.6f,
        targetValue   = 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_alpha"
    )

    // Listening red pulse
    val listenScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.18f,
        animationSpec = infiniteRepeatable(
            animation  = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "listen_scale"
    )

    Box(
        modifier         = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring (speaking)
        if (voiceState == VoiceState.SPEAKING) {
            Box(
                modifier = Modifier
                    .fillMaxSize(ringScale)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = ringAlpha),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize(0.95f)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
            )
        }

        // Listening ring (red)
        if (voiceState == VoiceState.LISTENING) {
            Box(
                modifier = Modifier
                    .fillMaxSize(listenScale)
                    .background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
            )
        }

        // Face circle
        val faceColor = when (voiceState) {
            VoiceState.SPEAKING  -> MaterialTheme.colorScheme.primaryContainer
            VoiceState.LISTENING -> MaterialTheme.colorScheme.errorContainer
            VoiceState.THINKING  -> MaterialTheme.colorScheme.secondaryContainer
            VoiceState.IDLE      -> MaterialTheme.colorScheme.surfaceContainerHighest
        }

        Box(
            modifier = Modifier
                .fillMaxSize(0.82f)
                .scale(if (voiceState == VoiceState.IDLE) breathe else 1f)
                .background(faceColor, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Face emoji — changes per state
            val face = when (voiceState) {
                VoiceState.SPEAKING  -> "🗣️"
                VoiceState.LISTENING -> "👂"
                VoiceState.THINKING  -> "🤔"
                VoiceState.IDLE      -> "🤖"
            }
            Text(face, fontSize = 64.sp, textAlign = TextAlign.Center)
        }
    }
}

// ── Buddy speech bubble ────────────────────────────────────────────────────
@Composable
private fun BuddySpeechBubble(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        // Small label
        Text(
            text  = "Buddy אומר:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(
                topStart    = 4.dp,
                topEnd      = 24.dp,
                bottomStart = 24.dp,
                bottomEnd   = 24.dp
            ),
            color       = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp,
            modifier    = modifier.fillMaxWidth()
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text     = text,
                    style    = MaterialTheme.typography.bodyLarge.copy(
                        fontSize   = 18.sp,
                        lineHeight = 26.sp
                    ),
                    color    = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        }
    }
}

// ── Thinking dots ──────────────────────────────────────────────────────────
@Composable
private fun ThinkingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val dotAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 0), RepeatMode.Reverse),
        label = "d1"
    )
    val dotAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 166), RepeatMode.Reverse),
        label = "d2"
    )
    val dotAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 333), RepeatMode.Reverse),
        label = "d3"
    )

    Column(modifier = modifier) {
        Text(
            text  = "Buddy חושב...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp
            ),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        ) {
            Row(
                modifier            = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment   = Alignment.CenterVertically
            ) {
                listOf(dotAlpha1, dotAlpha2, dotAlpha3).forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha),
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

// ── User's last message echo ───────────────────────────────────────────────
@Composable
private fun UserEcho(text: String, partial: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.Absolute.Right,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 24.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp
            ),
            color          = MaterialTheme.colorScheme.primaryContainer.copy(
                alpha = if (partial) 0.6f else 1f
            ),
            tonalElevation = 1.dp
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier          = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (partial) {
                        Spacer(Modifier.width(6.dp))
                        Text("🎤", fontSize = 14.sp)
                    }
                }
            }
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
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
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
                    "הגדר מפתח Gemini או Claude בהגדרות",
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                FilledTonalButton(
                    onClick        = onOpenSettings,
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
    currentMode: ChatMode,
    onModeChange: (ChatMode) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    Column {
        TopAppBar(
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                    }
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤖", fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Buddy", fontWeight = FontWeight.Bold)
                        profile?.let {
                            Text(
                                "שלום, ${it.displayName}!  רמה ${it.cefrLevel}",
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
                        modifier          = Modifier.padding(end = 4.dp)
                    ) {
                        Text("🔥", fontSize = 16.sp)
                        Text(
                            "$streakDays",
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary,
                            fontSize   = 14.sp
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "הגדרות")
                }
            }
        )

        // Mode selector as compact row under the top bar
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 6.dp),
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
                            fontSize = 12.sp
                        )
                    }
                )
            }
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
        targetValue   = if (isListening) 1.18f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "button_scale"
    )

    Surface(
        modifier        = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color           = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!hasAudioPermission) {
                Button(
                    onClick  = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("אפשר גישה למיקרופון כדי לדבר עם Buddy")
                }
                return@Column
            }

            val enabled = voiceState == VoiceState.IDLE || voiceState == VoiceState.LISTENING

            // Status label
            Text(
                text = when (voiceState) {
                    VoiceState.IDLE      -> stringResource(R.string.chat_hold_to_speak)
                    VoiceState.LISTENING -> stringResource(R.string.chat_listening)
                    VoiceState.THINKING  -> stringResource(R.string.chat_thinking)
                    VoiceState.SPEAKING  -> stringResource(R.string.chat_speaking)
                },
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            // Big push-to-talk button
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(scale)
                    .background(
                        color = when {
                            !enabled    -> MaterialTheme.colorScheme.surfaceVariant
                            isListening -> MaterialTheme.colorScheme.error
                            else        -> MaterialTheme.colorScheme.primaryContainer
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
                    tint     = when {
                        !enabled    -> MaterialTheme.colorScheme.onSurfaceVariant
                        isListening -> Color.White
                        else        -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}
