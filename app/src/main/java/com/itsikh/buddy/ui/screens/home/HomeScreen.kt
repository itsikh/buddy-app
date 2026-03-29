package com.itsikh.buddy.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itsikh.buddy.data.models.ChildProfile

// ── Buddy moods — shown in Hebrew since the UI is Hebrew ─────────────────
private val buddyMoods = listOf(
    "נרגש! 🥳"   to "\"מוכן ללמוד מילים חדשות?\"",
    "שמח! 😊"    to "\"מה שלומך היום?\"",
    "סקרן! 🤔"   to "\"רוצה לשמוע סיפור מגניב?\"",
    "אנרגטי! ⚡" to "\"בוא נשחק במשחק מילים!\""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTalkWithBuddy: () -> Unit,
    onStories:       () -> Unit,
    onGames:         () -> Unit,
    onProgress:      () -> Unit,
    onSettings:      () -> Unit,
    currentTab:      HomeTab = HomeTab.BUDDY,
    onTabChange:     (HomeTab) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mood    = remember { buddyMoods.random() }

    Scaffold(
        topBar = {
            HomeTopBar(profile = uiState.profile, onSettings = onSettings)
        },
        bottomBar = {
            HomeBottomNav(
                current    = currentTab,
                onBuddy    = { onTabChange(HomeTab.BUDDY) },
                onStories  = onStories,
                onGames    = onGames,
                onProgress = onProgress
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BuddyHero(profileName = uiState.profile?.displayName ?: "", mood = mood)
            BentoGrid(
                onDailyLesson   = onTalkWithBuddy,
                onStories       = onStories,
                onGames         = onGames,
                onTalkWithBuddy = onTalkWithBuddy
            )
            WeeklyProgress(sessions = uiState.weekSessions, goal = uiState.weekGoal)
            Spacer(Modifier.height(8.dp))
        }
    }
}

enum class HomeTab { BUDDY, STORIES, GAMES, PROGRESS }

// ── Top bar ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(profile: ChildProfile?, onSettings: () -> Unit) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier         = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("🤖", fontSize = 18.sp) }
                Text(
                    "Buddy English",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 17.sp,
                    color      = MaterialTheme.colorScheme.primary
                )
            }
        },
        actions = {
            // XP pill
            profile?.let { p ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 13.dp, vertical = 5.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("⭐", fontSize = 13.sp)
                        Text(
                            "${p.xpTotal}",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 13.sp,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "הגדרות",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

// ── Hero ───────────────────────────────────────────────────────────────────
@Composable
private fun BuddyHero(profileName: String, mood: Pair<String, String>) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    val scale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.05f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "scale"
    )

    Surface(
        shape    = RoundedCornerShape(22.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier            = Modifier.padding(20.dp, 20.dp, 20.dp, 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🤖", fontSize = 68.sp, modifier = Modifier.scale(scale))
            Spacer(Modifier.height(10.dp))

            // "BUDDY מרגיש..." chip
            Surface(
                shape  = RoundedCornerShape(999.dp),
                color  = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Text(
                    text      = "BUDDY מרגיש...",
                    modifier  = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    fontSize  = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color     = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(mood.first, fontSize = 26.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                text      = mood.second,
                fontSize  = 12.sp,
                fontWeight = FontWeight.Medium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Bento grid ─────────────────────────────────────────────────────────────
@Composable
private fun BentoGrid(
    onDailyLesson:   () -> Unit,
    onStories:       () -> Unit,
    onGames:         () -> Unit,
    onTalkWithBuddy: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // שיעור יומי — full width, golden
        BentoCard(
            modifier = Modifier.fillMaxWidth(),
            color    = MaterialTheme.colorScheme.primaryContainer,
            onClick  = onDailyLesson
        ) {
            Text("📚", fontSize = 40.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "שיעור יומי",
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 19.sp,
                color      = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "שיחה חופשית — קדימה!",
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
        }

        // Two half cards
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // סיפורים קסומים — blue
            BentoCard(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.secondaryContainer, onClick = onStories) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("📖", fontSize = 20.sp) }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "סיפורים\nקסומים",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 15.sp,
                    lineHeight = 20.sp,
                    color      = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // משחקי מילים — coral
            BentoCard(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.tertiaryContainer, onClick = onGames) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("🎮", fontSize = 20.sp) }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "משחקי\nמילים",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 15.sp,
                    lineHeight = 20.sp,
                    color      = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        // דבר עם Buddy — full width, warm surface
        BentoCard(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHighest, onClick = onTalkWithBuddy) {
            // In RTL this row: arrow on physical-left, text+icon on physical-right
            Row(
                modifier              = Modifier.fillMaxWidth(),
                // Use absoluteLeft/Right so it's fixed regardless of layout direction
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // arrow — use «  for RTL "forward" direction
                Text(
                    "«",
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "דבר עם Buddy",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 14.sp,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "תרגל לדבר עכשיו!",
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape    = CircleShape,
                        color    = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("🗣️", fontSize = 22.sp) }
                    }
                }
            }
        }
    }
}

// ── Reusable card with 3-D press ───────────────────────────────────────────
@Composable
private fun BentoCard(
    modifier: Modifier = Modifier,
    color: Color,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val offsetY by animateFloatAsState(
        targetValue   = if (pressed) 3f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "press"
    )

    Surface(
        modifier = modifier
            .offset(y = offsetY.dp)
            .clickable(
                onClick           = onClick,
                indication        = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ),
        shape           = RoundedCornerShape(18.dp),
        color           = color,
        shadowElevation = if (pressed) 1.dp else 4.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) { content() }
    }
}

// ── Weekly progress ────────────────────────────────────────────────────────
@Composable
private fun WeeklyProgress(sessions: Int, goal: Int) {
    val progress = (sessions.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    val animProg by animateFloatAsState(
        targetValue   = progress,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label         = "prog"
    )

    Surface(
        shape    = RoundedCornerShape(18.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerLowest,
        border   = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "$sessions / $goal ימים",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 12.sp,
                    color      = MaterialTheme.colorScheme.primary
                )
                Text(
                    "🎯 יעד שבועי",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 14.sp,
                    color      = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(11.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animProg)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }

            if (sessions >= goal) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "🎉 שבוע מעולה! You hit your goal!",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ── Bottom navigation ──────────────────────────────────────────────────────
@Composable
private fun HomeBottomNav(
    current:    HomeTab,
    onBuddy:    () -> Unit,
    onStories:  () -> Unit,
    onGames:    () -> Unit,
    onProgress: () -> Unit
) {
    Surface(
        shadowElevation = 8.dp,
        shape           = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color           = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .height(62.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            NavItem("🤖",  "Buddy",      current == HomeTab.BUDDY,    onBuddy)
            NavItem("📖",  "סיפורים",   current == HomeTab.STORIES,  onStories)
            NavItem("🎮",  "משחקים",    current == HomeTab.GAMES,    onGames)
            NavItem("📈",  "התקדמות",   current == HomeTab.PROGRESS, onProgress)
        }
    }
}

@Composable
private fun NavItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape    = RoundedCornerShape(18.dp),
        color    = if (selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(icon, fontSize = 20.sp)
            Text(
                label,
                fontSize      = 9.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.7.sp,
                color         = if (selected)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}
