package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AuthUiState
import com.example.ui.CaptionViewModel
import com.example.ui.components.AdMobBanner
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CaptionScreen
import com.example.ui.screens.HashtagScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ImageGenScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.ads.MobileAds

enum class AppTab(val title: String, val icon: ImageVector) {
    CAPTIONS("Captions", Icons.Default.ChatBubble),
    HASHTAGS("Hashtags", Icons.Default.TrendingUp),
    VISUALS("Visuals", Icons.Default.Palette),
    HISTORY("History", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize AdMob Mobile Ads SDK
        try {
            MobileAds.initialize(this) {}
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error initializing MobileAds SDK", e)
        }

        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(false) }
            val systemDark = isSystemInDarkTheme()
            
            // Set default theme from system settings
            LaunchedEffect(systemDark) {
                isDarkTheme = systemDark
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                MainLayout(
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = { isDarkTheme = it }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLayout(
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    viewModel: CaptionViewModel = viewModel()
) {
    var activeTab by remember { mutableStateOf(AppTab.CAPTIONS) }
    val authState by viewModel.authUiState.collectAsState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            // Tablet / Landscape Mode: Side Navigation Rail
            if (isExpanded) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    AppTab.values().forEach { tab ->
                        NavigationRailItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            modifier = Modifier.testTag("nav_rail_item_${tab.name.lowercase()}")
                        )
                    }
                }
            }

            // Main Content Area
            Scaffold(
                modifier = Modifier.weight(1f),
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "AI Caption Pro",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                            }
                        },
                        actions = {
                            // Quick Status / Session Indicator
                            when (val state = authState) {
                                is AuthUiState.SignedIn -> {
                                    IconButton(
                                        onClick = { activeTab = AppTab.SETTINGS },
                                        modifier = Modifier.testTag("profile_top_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = "Profile Settings",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                else -> {
                                    IconButton(
                                        onClick = { activeTab = AppTab.SETTINGS },
                                        modifier = Modifier.testTag("auth_top_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = "Login",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                bottomBar = {
                    // Mobile Mode: Bottom Navigation Bar
                    if (!isExpanded) {
                        Column {
                            // Banner Ad embedded cleanly directly above bottom bar
                            AdMobBanner(modifier = Modifier.fillMaxWidth())

                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                AppTab.values().forEach { tab ->
                                    NavigationBarItem(
                                        selected = activeTab == tab,
                                        onClick = { activeTab = tab },
                                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                                        label = { Text(tab.title, fontSize = 11.sp) },
                                        modifier = Modifier.testTag("nav_bar_item_${tab.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    } else {
                        // Embedded Banner Ad on Tablet at bottom
                        AdMobBanner(modifier = Modifier.fillMaxWidth())
                    }
                },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                // Render selected tab content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (activeTab) {
                        AppTab.CAPTIONS -> CaptionScreen(viewModel = viewModel)
                        AppTab.HASHTAGS -> HashtagScreen(viewModel = viewModel)
                        AppTab.VISUALS -> ImageGenScreen(viewModel = viewModel)
                        AppTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                        AppTab.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            isDarkTheme = isDarkTheme,
                            onThemeToggle = onThemeToggle
                        )
                    }
                }
            }
        }
    }
}
