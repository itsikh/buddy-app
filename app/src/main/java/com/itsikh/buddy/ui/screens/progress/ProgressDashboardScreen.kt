package com.itsikh.buddy.ui.screens.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itsikh.buddy.R
import com.itsikh.buddy.data.models.SessionLog
import com.itsikh.buddy.gamification.BadgeDefinitions
import com.itsikh.buddy.gamification.XpManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressDashboardScreen(
    onBack: () -> Unit,
    viewModel: ProgressDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.progress_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overview stats
            item {
                OverviewCard(uiState)
            }

            // CEFR level
            item {
                LevelCard(uiState)
            }

            // Streak
            item {
                StreakCard(uiState)
            }

            // Badges
            if (uiState.earnedBadgeIds.isNotEmpty()) {
                item {
                    BadgesCard(uiState.earnedBadgeIds)
                }
            }

            // Recent sessions
            if (uiState.recentSessions.isNotEmpty()) {
                item {
                    Text(
                        "מפגשים אחרונים",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(uiState.recentSessions.take(10)) { session ->
                    SessionCard(session)
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(uiState: ProgressDashboardUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("סיכום", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("📚", "${uiState.totalSessions}", "מפגשים")
                StatItem("🌱", "${uiState.vocabularyMastered}", "מילים")
                StatItem("⏱️", "${uiState.totalMinutes}", "דקות")
                StatItem("⭐", "${uiState.xpTotal}", "XP")
            }
        }
    }
}

@Composable
private fun StatItem(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 24.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LevelCard(uiState: ProgressDashboardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.progress_cefr_level, uiState.cefrLevel), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkillChip("🗣️ דיבור", uiState.speakingLevel)
                SkillChip("📖 מילים", uiState.vocabLevel)
                SkillChip("✏️ דקדוק", uiState.grammarLevel)
            }
        }
    }
}

@Composable
private fun SkillChip(label: String, level: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary) {
        Text(
            "$label: $level",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color    = MaterialTheme.colorScheme.onPrimary,
            style    = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun StreakCard(uiState: ProgressDashboardUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier            = Modifier.padding(16.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("🔥 רצף", fontWeight = FontWeight.Bold)
                Text("${uiState.streakDays} ימים נוכחי • ${uiState.longestStreak} ימים שיא", style = MaterialTheme.typography.bodySmall)
            }
            if (uiState.shieldsAvailable > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🛡️", fontSize = 24.sp)
                    Text("${uiState.shieldsAvailable} מגן", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun BadgesCard(earnedIds: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🏅 הישגים", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val earnedBadges = earnedIds.mapNotNull { BadgeDefinitions.findById(it) }
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                earnedBadges.take(8).forEach { badge ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(badge.icon, fontSize = 28.sp)
                        Text(badge.nameHe, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: SessionLog) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier            = Modifier.padding(12.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                val modeEmoji = when (session.mode) {
                    com.itsikh.buddy.data.models.ChatMode.FREE_CHAT  -> "💬"
                    com.itsikh.buddy.data.models.ChatMode.STORY_TIME -> "📖"
                    com.itsikh.buddy.data.models.ChatMode.ROLE_PLAY  -> "🎭"
                }
                val date = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date(session.startedAt))
                Text("$modeEmoji $date", fontWeight = FontWeight.Medium)
                session.sessionSummary?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${session.durationMinutes} דק'", style = MaterialTheme.typography.labelSmall)
                Text("+${session.xpEarned} XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
