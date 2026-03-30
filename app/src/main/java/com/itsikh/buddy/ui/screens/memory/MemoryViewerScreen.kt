package com.itsikh.buddy.ui.screens.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itsikh.buddy.R
import com.itsikh.buddy.data.models.MemoryCategory
import com.itsikh.buddy.data.models.MemoryFact

/**
 * Parent-facing screen (admin-gated) that shows everything Buddy has learned about the child.
 * Parents can review and delete individual facts.
 * In admin mode, parents can also edit any fact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryViewerScreen(
    onBack: () -> Unit,
    viewModel: MemoryViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.memory_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.facts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.memory_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val grouped = uiState.facts.groupBy { it.category }
            MemoryCategory.entries.forEach { category ->
                val catFacts = grouped[category] ?: return@forEach
                item {
                    Text(
                        text = categoryLabel(category),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(catFacts, key = { it.id }) { fact ->
                    MemoryFactItem(
                        fact      = fact,
                        adminMode = uiState.adminMode,
                        onDelete  = { viewModel.deleteFact(fact) },
                        onUpdate  = { newKey, newValue -> viewModel.updateFact(fact, newKey, newValue) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryFactItem(
    fact: MemoryFact,
    adminMode: Boolean,
    onDelete: () -> Unit,
    onUpdate: (String, String) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        EditFactDialog(
            fact    = fact,
            onSave  = { newKey, newValue ->
                onUpdate(newKey, newValue)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier            = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(fact.key, fontWeight = FontWeight.Medium)
                Text(
                    fact.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (adminMode) {
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "ערוך",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "מחק",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EditFactDialog(
    fact: MemoryFact,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var key   by remember { mutableStateOf(fact.key) }
    var value by remember { mutableStateOf(fact.value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ערוך עובדה") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = key,
                    onValueChange = { key = it },
                    label         = { Text("מפתח (key)") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value         = value,
                    onValueChange = { value = it },
                    label         = { Text("ערך (value)") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { onSave(key, value) },
                enabled  = key.isNotBlank() && value.isNotBlank()
            ) { Text("שמור") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        }
    )
}

private fun categoryLabel(category: MemoryCategory): String = when (category) {
    MemoryCategory.FAMILY     -> "👨‍👩‍👧 משפחה"
    MemoryCategory.INTERESTS  -> "❤️ תחביבים"
    MemoryCategory.ACTIVITIES -> "⚽ פעילויות"
    MemoryCategory.SCHOOL     -> "🏫 בית ספר"
    MemoryCategory.OTHER      -> "💡 אחר"
}
