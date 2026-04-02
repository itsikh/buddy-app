package com.itsikh.buddy.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
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
    onOpenProgress: () -> Unit = {},
    onOpenHistory:  () -> Unit = {},
    onOpenGarden:   () -> Unit = {},
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
                totalCoins     = uiState.totalCoins,
                currentMode    = uiState.mode,
                onModeChange   = { viewModel.switchMode(it) },
                onOpenSettings = onOpenSettings,
                onOpenProgress = onOpenProgress,
                onOpenHistory  = onOpenHistory,
                onOpenGarden   = onOpenGarden,
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

            // ── Admin: active model strip ────────────────────────────────
            AnimatedVisibility(visible = uiState.adminMode) {
                AdminModelStrip(
                    aiModel    = uiState.activeAiModel,
                    ttsBackend = uiState.ttsBackend
                )
            }

            // ── Conversational face area ─────────────────────────────────
            ConversationArea(
                messages          = uiState.messages,
                voiceState        = uiState.voiceState,
                partialText       = uiState.partialSpeechText,
                currentBuddyText  = uiState.currentBuddyText,
                gender            = uiState.profile?.gender ?: "GIRL",
                lastSpokenText    = uiState.lastSpokenText,
                onRepeat          = { viewModel.repeatLastMessage() },
                onStopSpeaking    = { viewModel.stopSpeaking() },
                onWordTapped      = { viewModel.onWordTapped(it) },
                modifier          = Modifier.weight(1f)
            )

            // ── Word lookup popup ─────────────────────────────────────────
            uiState.wordLookup?.let { lookup ->
                WordLookupDialog(
                    state     = lookup,
                    onDismiss = { viewModel.dismissWordLookup() },
                    onSpeak   = { viewModel.speakEnglishWord(it) }
                )
            }

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

            // ── Admin debug text input ────────────────────────────────────
            if (uiState.adminMode && uiState.isSessionActive) {
                var debugInput by remember { mutableStateOf("") }
                val focusManager = LocalFocusManager.current
                val keyboardController = LocalSoftwareKeyboardController.current
                val sendDebugMessage = {
                    if (debugInput.isNotBlank()) {
                        viewModel.sendTextMessage(debugInput)
                        debugInput = ""
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = debugInput,
                        onValueChange = { debugInput = it },
                        placeholder = { Text("🛠 type message…", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendDebugMessage() })
                    )
                    IconButton(onClick = sendDebugMessage) {
                        Icon(Icons.Default.Send, contentDescription = "Send debug message")
                    }
                }
            }

            // ── Voice control bar ────────────────────────────────────────
            VoiceControlBar(
                isSessionActive     = uiState.isSessionActive,
                voiceState          = uiState.voiceState,
                hasAudioPermission  = hasAudioPermission,
                lastSpokenText      = uiState.lastSpokenText,
                onStart             = { viewModel.startSession(uiState.mode) },
                onRequestPermission = { requestMicPermission.launch(Manifest.permission.RECORD_AUDIO) },
                onStopSpeaking      = { viewModel.stopSpeaking() },
                onRepeat            = { viewModel.repeatLastMessage() },
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
    currentBuddyText: String?,
    gender: String,
    lastSpokenText: String?,
    onRepeat: () -> Unit,
    onStopSpeaking: () -> Unit,
    onWordTapped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Split last buddy / user messages
    val lastBuddy = messages.lastOrNull { it.role == "assistant" }
    val lastUser  = messages.lastOrNull { it.role == "user" }

    Column(
        modifier            = modifier.fillMaxWidth().clipToBounds(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(Modifier.height(12.dp))

        // ── Animated Buddy avatar — tappable to stop when speaking ──────
        BuddyAvatar(
            voiceState     = voiceState,
            gender         = gender,
            onStopSpeaking = onStopSpeaking
        )

        Spacer(Modifier.height(20.dp))

        // ── Buddy's current message ──────────────────────────────────────
        // currentBuddyText is set immediately on SPEAKING to avoid the Race Condition
        // where the Room Flow hasn't yet delivered the just-inserted DB row.
        val displayText = when {
            voiceState == VoiceState.THINKING -> null   // dots shown inside avatar ring
            currentBuddyText != null -> currentBuddyText
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
                BuddySpeechBubble(text = text, onWordTapped = onWordTapped, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
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
private fun BuddyAvatar(
    voiceState: VoiceState,
    gender: String,
    onStopSpeaking: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    // Reduce avatar size in landscape to fit the compressed vertical space
    val outerW = if (isLandscape) 130.dp else 185.dp
    val outerH = if (isLandscape) 150.dp else 215.dp
    val innerW = if (isLandscape) 110.dp else 155.dp
    val innerH = if (isLandscape) 130.dp else 185.dp

    val infiniteTransition = rememberInfiniteTransition(label = "avatar")

    val breathe by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.03f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.7f,
        targetValue   = 0f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "ring_alpha"
    )
    val ringScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.10f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring_scale"
    )
    val listenScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.07f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "listen_scale"
    )

    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val errorColor       = MaterialTheme.colorScheme.error

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible = voiceState == VoiceState.SPEAKING) {
            Text(
                text       = "הקש לעצור",
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
                modifier   = Modifier.padding(bottom = 4.dp)
            )
        }

        // Outer container — sized to contain rings + image
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = outerW, height = outerH)
                .then(if (voiceState == VoiceState.SPEAKING) Modifier.clickable { onStopSpeaking() } else Modifier)
        ) {
            // Speaking: outer pulsing glow ring
            if (voiceState == VoiceState.SPEAKING) {
                Box(
                    modifier = Modifier
                        .size(width = (outerW - 20.dp) * ringScale, height = (outerH - 20.dp) * ringScale)
                        .background(primaryContainer.copy(alpha = ringAlpha), RoundedCornerShape(28.dp))
                )
                Box(
                    modifier = Modifier
                        .size(width = outerW - 22.dp, height = outerH - 22.dp)
                        .background(primaryContainer.copy(alpha = 0.18f), RoundedCornerShape(26.dp))
                )
            }

            // Listening: red glow ring
            if (voiceState == VoiceState.LISTENING) {
                Box(
                    modifier = Modifier
                        .size(width = (outerW - 22.dp) * listenScale, height = (outerH - 22.dp) * listenScale)
                        .background(errorColor.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
                )
            }

            // Robot image — clip to rounded rectangle, breathe when idle
            Box(
                modifier = Modifier
                    .size(width = innerW, height = innerH)
                    .scale(if (voiceState == VoiceState.IDLE) breathe else 1f)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                // Show left half (girl) or right half (boy) by changing alignment
                Image(
                    painter        = painterResource(R.drawable.buddy_avatar),
                    contentDescription = "Buddy",
                    contentScale   = ContentScale.FillHeight,
                    alignment      = if (gender == "GIRL") Alignment.CenterStart else Alignment.CenterEnd,
                    modifier       = Modifier.fillMaxSize()
                )
                // State colour overlay
                val overlay = when (voiceState) {
                    VoiceState.LISTENING -> errorColor.copy(alpha = 0.10f)
                    VoiceState.THINKING  -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                    else                 -> Color.Transparent
                }
                if (overlay != Color.Transparent) {
                    Box(Modifier.fillMaxSize().background(overlay))
                }
            }
        }
    }
}

// ── Buddy speech bubble ────────────────────────────────────────────────────
@Composable
private fun BuddySpeechBubble(
    text: String,
    onWordTapped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val wordColor    = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    // Build AnnotatedString once per text+color change.
    // English word tokens get a clickable annotation + underline highlight.
    val annotated = remember(text, wordColor) {
        val englishWord = Regex("[a-zA-Z]+(?:'[a-zA-Z]+)*")
        buildAnnotatedString {
            var cursor = 0
            englishWord.findAll(text).forEach { match ->
                append(text.substring(cursor, match.range.first))
                pushStringAnnotation(tag = "WORD", annotation = match.value)
                withStyle(SpanStyle(color = wordColor, textDecoration = TextDecoration.Underline)) {
                    append(match.value)
                }
                pop()
                cursor = match.range.last + 1
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }

    Column(modifier = modifier) {
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
            color          = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp,
            modifier       = modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ClickableText(
                    text     = annotated,
                    style    = MaterialTheme.typography.bodyLarge.copy(
                        fontSize      = 18.sp,
                        lineHeight    = 26.sp,
                        textDirection = TextDirection.Content,
                        color         = contentColor
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    onClick  = { offset ->
                        annotated.getStringAnnotations("WORD", offset, offset)
                            .firstOrNull()?.let { onWordTapped(it.item) }
                    }
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
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = TextDirection.Content
                        ),
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

// ── Word Lookup Dialog ─────────────────────────────────────────────────────
@Composable
private fun WordLookupDialog(
    state: WordLookupState,
    onDismiss: () -> Unit,
    onSpeak: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Text(
                    text  = state.word,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { onSpeak(state.word) }) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = "Hear word")
                }
            }
        },
        text = {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier.fillMaxWidth()
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    state.hebrewTranslation != null -> Text(
                        text      = state.hebrewTranslation,
                        style     = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.End,
                        modifier  = Modifier.fillMaxWidth()
                    )
                    else -> Text(
                        text  = "לא נמצא תרגום",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("סגור") }
        }
    )
}

// ── Coin Rewards Dialog ────────────────────────────────────────────────────

private data class CoinReward(val coins: Int, val emoji: String, val title: String, val description: String)

private val coinRewards = listOf(
    CoinReward(100, "🍦", "גלידה לבחירה",           "תבחר איזה גלידה שתרצה!"),
    CoinReward(200, "🍽️", "לבחור מה לארוחת ערב",   "הערב אתה מחליט מה אוכלים!"),
    CoinReward(300, "🎮", "Brawl Stars Pass",        "Pass עונה ל-Brawl Stars!"),
    CoinReward(400, "🍬", "ממתקים לבחירה",            "ממתקים שאתה רוצה בשווי 70 שקל"),
    CoinReward(500, "💵", "100 ₪ לקנות מה שרוצה",   "עם אישור אבא — 100 שקל לקנות כל מה שתרצה!"),
)

@Composable
private fun CoinRewardsDialog(totalCoins: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("🪙", fontSize = 48.sp)
                Text(
                    "$totalCoins מטבעות",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "הצטברו לך $totalCoins מטבעות Buddy!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "מה אפשר לקבל?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                coinRewards.forEach { reward ->
                    val unlocked = totalCoins >= reward.coins
                    val progress = (totalCoins.toFloat() / reward.coins).coerceAtMost(1f)
                    Card(
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = if (unlocked) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(reward.emoji, fontSize = 24.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    reward.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (unlocked) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    reward.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!unlocked) {
                                    Spacer(Modifier.height(4.dp))
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    Text(
                                        "${totalCoins}/${reward.coins} מטבעות",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (unlocked) {
                                Text("✅", fontSize = 20.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "מרוויחים מטבעות על שיחה אמיתית של 10 דקות עם Buddy 🎯",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("סגור") }
        }
    )
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

// ── Admin model info strip ─────────────────────────────────────────────────
@Composable
private fun AdminModelStrip(aiModel: String, ttsBackend: com.itsikh.buddy.voice.TtsBackend) {
    val ttsLabel = when (ttsBackend) {
        com.itsikh.buddy.voice.TtsBackend.GOOGLE_CLOUD_CHIRP   -> "Chirp3-HD ✓"
        com.itsikh.buddy.voice.TtsBackend.GOOGLE_CLOUD_WAVENET -> "WaveNet ✓"
        com.itsikh.buddy.voice.TtsBackend.ANDROID_FALLBACK     -> "Android TTS ⚠ (fallback)"
        com.itsikh.buddy.voice.TtsBackend.UNKNOWN               -> "TTS: not used yet"
    }
    val ttsColor = when (ttsBackend) {
        com.itsikh.buddy.voice.TtsBackend.GOOGLE_CLOUD_CHIRP   -> MaterialTheme.colorScheme.tertiary
        com.itsikh.buddy.voice.TtsBackend.GOOGLE_CLOUD_WAVENET -> MaterialTheme.colorScheme.tertiary
        com.itsikh.buddy.voice.TtsBackend.ANDROID_FALLBACK     -> MaterialTheme.colorScheme.error
        com.itsikh.buddy.voice.TtsBackend.UNKNOWN               -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("🛠", fontSize = 11.sp)
            Text(
                text     = "AI: $aiModel",
                fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("·", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text     = ttsLabel,
                fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color    = ttsColor
            )
        }
    }
}

// ── Top bar ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    profile: com.itsikh.buddy.data.models.ChildProfile?,
    streakDays: Int,
    totalCoins: Int,
    currentMode: ChatMode,
    onModeChange: (ChatMode) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenGarden: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var showCoinDialog by remember { mutableStateOf(false) }
    if (showCoinDialog) {
        CoinRewardsDialog(totalCoins = totalCoins, onDismiss = { showCoinDialog = false })
    }

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
                // Coin button with badge
                Box(contentAlignment = Alignment.Center) {
                    IconButton(onClick = { showCoinDialog = true }) {
                        Text("🪙", fontSize = 22.sp)
                    }
                    if (totalCoins > 0) {
                        Surface(
                            color    = MaterialTheme.colorScheme.error,
                            shape    = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 2.dp)
                                .defaultMinSize(minWidth = 20.dp, minHeight = 18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                                Text(
                                    if (totalCoins > 999) "999+" else totalCoins.toString(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onError
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onOpenGarden) {
                    Text("🌱", fontSize = 20.sp)
                }
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Default.History, contentDescription = "היסטוריה")
                }
                IconButton(onClick = onOpenProgress) {
                    Icon(Icons.Default.BarChart, contentDescription = "התקדמות")
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
    isSessionActive: Boolean,
    voiceState: VoiceState,
    hasAudioPermission: Boolean,
    lastSpokenText: String?,
    onStart: () -> Unit,
    onRequestPermission: () -> Unit,
    onStopSpeaking: () -> Unit,
    onRepeat: () -> Unit,
    onPressDown: () -> Unit,
    onPressUp: () -> Unit
) {
    // ── Not started yet: show a big Start button ──────────────────────────
    if (!isSessionActive) {
        Surface(
            modifier        = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color           = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier            = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick  = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape  = MaterialTheme.shapes.large
                ) {
                    Text("התחל לדבר עם Buddy ▶", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
        return
    }

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

            // ── SPEAKING: full-width stop button ──────────────────────────
            if (voiceState == VoiceState.SPEAKING) {
                Button(
                    onClick  = onStopSpeaking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape    = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("עצור", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                return@Column
            }

            // ── THINKING: disabled indicator ──────────────────────────────
            if (voiceState == VoiceState.THINKING) {
                Text(
                    text  = stringResource(R.string.chat_thinking),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                Box(
                    modifier         = Modifier
                        .size(88.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                }
                return@Column
            }

            // ── IDLE / LISTENING: push-to-talk ────────────────────────────
            val enabled = voiceState == VoiceState.IDLE || voiceState == VoiceState.LISTENING

            Text(
                text = when (voiceState) {
                    VoiceState.IDLE      -> stringResource(R.string.chat_hold_to_speak)
                    VoiceState.LISTENING -> stringResource(R.string.chat_listening)
                    else                 -> ""
                },
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Spacer on left to balance the repeat button on the right
                Spacer(Modifier.size(56.dp))

                Spacer(Modifier.width(16.dp))

                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .scale(scale)
                        .background(
                            color = if (isListening) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        )
                        .pointerInput(enabled) {
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
                        imageVector        = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Push to talk",
                        tint               = if (isListening) Color.White
                                             else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier           = Modifier.size(40.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                // ── Repeat last message button ────────────────────────────
                if (lastSpokenText != null && voiceState == VoiceState.IDLE) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            .clickable(onClick = onRepeat),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Default.VolumeUp,
                            contentDescription = "הפעל שוב",
                            tint               = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier           = Modifier.size(26.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.size(56.dp))
                }
            }
        }
    }
}
