package com.template.app.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.template.app.R

/**
 * Parent-facing consent screen shown on first launch.
 * Explains what data is collected, how it's used, and where it's stored.
 * Parent must explicitly agree before the child can use the app.
 */
@Composable
fun ParentConsentScreen(
    onConsent: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        Text("🤖", fontSize = 64.sp)

        Spacer(Modifier.height(16.dp))

        Text(
            text       = stringResource(R.string.onboarding_consent_title),
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text  = stringResource(R.string.onboarding_consent_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // Privacy info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_consent_body),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // What we collect
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "מה אנחנו שומרים:",
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))
                PrivacyRow("✅", "שיחות טקסט עם Buddy")
                PrivacyRow("✅", "רשימת מילים שנלמדו")
                PrivacyRow("✅", "עובדות שBuddy למד על ילדך (תחביבים, משפחה)")
                PrivacyRow("✅", "סיכומי מפגשים בעברית לך")
                Spacer(Modifier.height(8.dp))
                Text(
                    "מה אנחנו לא שומרים:",
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))
                PrivacyRow("❌", "הקלטות קוליות")
                PrivacyRow("❌", "שם אמיתי בענן — רק בטלפון")
                PrivacyRow("❌", "מידע מזהה כלשהו")
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick  = onConsent,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                stringResource(R.string.onboarding_consent_agree),
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PrivacyRow(icon: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
