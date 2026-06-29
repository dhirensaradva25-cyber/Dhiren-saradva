package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AuthUiState
import com.example.ui.CaptionViewModel
import com.example.ui.components.AdMobRewardedHelper

@Composable
fun SettingsScreen(
    viewModel: CaptionViewModel,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val authState by viewModel.authUiState.collectAsState()
    val credits by viewModel.credits.collectAsState()
    val unlimitedCredits by viewModel.unlimitedCredits.collectAsState()

    var isAdLoading by remember { mutableStateOf(false) }

    // Pre-load rewarded ad
    LaunchedEffect(Unit) {
        AdMobRewardedHelper.loadAd(context) { loaded ->
            Logd("AdMob pre-load status: $loaded")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "App Customizations",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // 1. Theme Configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = "Theme Toggle",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Dark Theme",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Switch between light and dark modes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = onThemeToggle,
                    modifier = Modifier.testTag("theme_switch")
                )
            }
        }

        // 2. Credits & Rewarded Ads
        Text(
            text = "AdMob Advertising & Tokens",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current Credits Balance:",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (unlimitedCredits) "Unlimited" else "$credits",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Divider()

                // Unlimited credits toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Unlimited Pro Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Bypass credit limits for unlimited testing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = unlimitedCredits,
                        onCheckedChange = { viewModel.setUnlimitedCredits(it) },
                        modifier = Modifier.testTag("unlimited_credits_switch")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // AdMob Real Ad Button
                Button(
                    onClick = {
                        val activity = context as? Activity
                        if (activity != null) {
                            if (AdMobRewardedHelper.isAdReady()) {
                                AdMobRewardedHelper.showAd(
                                    activity = activity,
                                    onRewardEarned = { reward ->
                                        viewModel.addCredits(reward)
                                        Toast.makeText(context, "Congratulations! Earned $reward credits!", Toast.LENGTH_LONG).show()
                                    },
                                    onAdDismissed = {
                                        // Reload the ad
                                        AdMobRewardedHelper.loadAd(context)
                                    }
                                )
                            } else {
                                isAdLoading = true
                                AdMobRewardedHelper.loadAd(context) { loaded ->
                                    isAdLoading = false
                                    if (loaded) {
                                        AdMobRewardedHelper.showAd(
                                            activity = activity,
                                            onRewardEarned = { reward ->
                                                viewModel.addCredits(reward)
                                                Toast.makeText(context, "Congratulations! Earned $reward credits!", Toast.LENGTH_LONG).show()
                                            },
                                            onAdDismissed = {
                                                AdMobRewardedHelper.loadAd(context)
                                            }
                                        )
                                    } else {
                                        // Offline/Mock simulation if SDK fails to fetch (e.g., emulator networking)
                                        viewModel.addCredits(5)
                                        Toast.makeText(context, "AdMob Offline. Simulated watch completed! Earned 5 credits.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("rewarded_ad_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isAdLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Watch Rewarded Ad (+5 Credits)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Play Store Compliances Info
        Text(
            text = "Compliance & Security Info",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Play Store Policy Compliant", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    text = "• Uses secure, dynamic API configuration via Google AI Studio secrets.\n" +
                            "• Restrictive permissions: no background location or high-risk tracking.\n" +
                            "• Explicit user authorization: AdMob rewarded actions are fully user-triggered.\n" +
                            "• GDPR/Data Compliant: history database is kept 100% locally on the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        // 4. Session info
        Text(
            text = "Account Profile",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    when (val state = authState) {
                        is AuthUiState.SignedIn -> {
                            Text(text = "Logged in as ${state.name}", fontWeight = FontWeight.SemiBold)
                            Text(text = state.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            Text(text = "Not logged in", fontWeight = FontWeight.SemiBold)
                            Text(text = "Sign in on the Auth tab to sync.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (authState is AuthUiState.SignedIn) {
                    TextButton(onClick = { viewModel.signOut() }) {
                        Text("Sign Out", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun Logd(msg: String) {
    android.util.Log.d("SettingsScreen", msg)
}
