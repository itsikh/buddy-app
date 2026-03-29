package com.itsikh.buddy.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itsikh.buddy.R

/**
 * Profile setup screen — step 2 of onboarding.
 * Parent enters the child's display name, age, and gender.
 */
@Composable
fun ProfileSetupScreen(
    onProfileCreated: () -> Unit,
    viewModel: ProfileSetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.profileSaved) {
        if (uiState.profileSaved) onProfileCreated()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("👤", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(R.string.profile_setup_title),
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value         = uiState.name,
            onValueChange = viewModel::onNameChanged,
            label         = { Text(stringResource(R.string.profile_setup_name_hint)) },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            isError       = uiState.nameError != null,
            supportingText = uiState.nameError?.let { { Text(it) } }
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value         = uiState.ageText,
            onValueChange = viewModel::onAgeChanged,
            label         = { Text(stringResource(R.string.profile_setup_age_hint)) },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError       = uiState.ageError != null,
            supportingText = uiState.ageError?.let { { Text(it) } }
        )

        Spacer(Modifier.height(24.dp))

        // Gender picker
        Text(
            "מי משתמש באפליקציה?",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GenderButton(
                emoji    = "👦",
                label    = "ילד",
                selected = uiState.gender == "BOY",
                onClick  = { viewModel.onGenderChanged("BOY") },
                modifier = Modifier.weight(1f)
            )
            GenderButton(
                emoji    = "👧",
                label    = "ילדה",
                selected = uiState.gender == "GIRL",
                onClick  = { viewModel.onGenderChanged("GIRL") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick  = viewModel::saveProfile,
            enabled  = !uiState.isSaving && uiState.name.isNotBlank() && uiState.ageText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    color    = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    stringResource(R.string.profile_setup_start),
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GenderButton(
    emoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                         else MaterialTheme.colorScheme.surface
    val contentColor   = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                         else MaterialTheme.colorScheme.onSurface
    val border         = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                         else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

    OutlinedButton(
        onClick  = onClick,
        modifier = modifier.height(80.dp),
        shape    = RoundedCornerShape(16.dp),
        border   = border,
        colors   = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor   = contentColor
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 28.sp)
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
