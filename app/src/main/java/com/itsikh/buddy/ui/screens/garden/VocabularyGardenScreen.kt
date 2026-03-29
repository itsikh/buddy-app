package com.itsikh.buddy.ui.screens.garden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itsikh.buddy.R
import com.itsikh.buddy.data.models.VocabularyItem

/**
 * Shows the child's vocabulary as a "garden" — each word is a plant at various growth stages.
 * Mastery level 0-2 = small plant, 3-4 = growing, 5 = fully bloomed.
 * This is a positive visual that makes vocabulary progress tangible and motivating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyGardenScreen(
    onBack: () -> Unit,
    viewModel: VocabularyGardenViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.garden_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Stats header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GardenStat("🌸", "${uiState.masteredCount}", "פרחו")
                GardenStat("🌿", "${uiState.growingCount}", "צומחות")
                GardenStat("🌱", "${uiState.seedlingCount}", "זרעים")
            }

            if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌱", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.garden_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        WordPlant(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun GardenStat(emoji: String, count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 24.sp)
        Text(count, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun WordPlant(item: VocabularyItem) {
    Card(
        modifier = Modifier
            .aspectRatio(0.8f)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = plantEmoji(item.masteryLevel),
                fontSize = 28.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.word,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            // Mastery dots
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(5) { index ->
                    Text(
                        if (index < item.masteryLevel) "🟢" else "⚪",
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}

private fun plantEmoji(masteryLevel: Int): String = when (masteryLevel) {
    0    -> "🌰"  // seed
    1    -> "🌱"  // sprout
    2    -> "🌿"  // growing
    3    -> "🌻"  // flowering
    4    -> "🌺"  // bloomed
    else -> "🌸"  // fully mastered
}
