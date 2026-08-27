package com.ir.tester
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ir.tester.ui.screens.AutoScanScreen
import com.ir.tester.ui.screens.InfoScreen
import com.ir.tester.ui.screens.NoIrScreen
import com.ir.tester.ui.screens.SignalGeneratorScreen
import com.ir.tester.ui.screens.UniversalScanScreen
import com.ir.tester.ui.theme.IrTesterTheme
import com.ir.tester.viewmodel.MainViewModel
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IrTesterTheme {
                val uiState by viewModel.uiState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(uiState.feedbackMessage) {
                    uiState.feedbackMessage?.let { msg ->
                        snackbarHostState.showSnackbar(
                            message = msg.lowercase(),
                            duration = SnackbarDuration.Short
                        )
                        viewModel.clearFeedbackMessage()
                    }
                }
                if (!uiState.hasIrEmitter && !uiState.isDemoMode) {
                    NoIrScreen(
                        onRetry = { viewModel.checkIrEmitter() },
                        onDemoMode = { viewModel.enableDemoMode() }
                    )
                } else {
                    MainAppScaffold(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                tonalElevation = 6.dp
            ) {
                // Tab 0: универсал
                NavigationBarItem(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.selectedTab == 0)
                                Icons.Filled.Tune
                            else
                                Icons.Outlined.Tune,
                            contentDescription = "универсал"
                        )
                    },
                    label = { Text("универсал", fontWeight = FontWeight.SemiBold, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                // Tab 1: брут
                NavigationBarItem(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.selectedTab == 1)
                                Icons.Filled.PowerSettingsNew
                            else
                                Icons.Outlined.PowerSettingsNew,
                            contentDescription = "брут"
                        )
                    },
                    label = { Text("брут", fontWeight = FontWeight.SemiBold, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.selectedTab == 2)
                                Icons.Filled.GraphicEq
                            else
                                Icons.Outlined.GraphicEq,
                            contentDescription = "джаммер"
                        )
                    },
                    label = { Text("джаммер", fontWeight = FontWeight.SemiBold, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )

                NavigationBarItem(
                    selected = uiState.selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "инфо"
                        )
                    },
                    label = { Text("инфо", fontWeight = FontWeight.SemiBold, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding(), top = 16.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = uiState.selectedTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "main_tab_switch"
            ) { tab ->
                when (tab) {
                    0 -> UniversalScanScreen(
                        state = uiState.universalState,
                        onCategorySelected = { viewModel.selectUniversalCategory(it) },
                        onBrandSelected = { viewModel.selectUniversalBrand(it) },
                        onBackToCategories = { viewModel.backUniversalToCategories() },
                        onBackToBrands = { viewModel.backUniversalToBrands() },
                        onToggleScan = { viewModel.toggleUniversalScan() },
                        onTestCode = { viewModel.transmitSingleCode(it) }
                    )
                    1 -> AutoScanScreen(
                        state = uiState.globalState,
                        onCategorySelected = { viewModel.selectGlobalCategory(it) },
                        onBackToCategories = { viewModel.backGlobalToCategories() },
                        onToggleScan = { viewModel.toggleGlobalScan() },
                        onTestCode = { viewModel.transmitSingleCode(it) }
                    )
                    2 -> SignalGeneratorScreen(
                        state = uiState.jammerState,
                        onFrequencySelected = { viewModel.setJammerFrequency(it) },
                        onModeSelected = { viewModel.setJammerMode(it) },
                        onToggleGenerator = { viewModel.toggleJammer() }
                    )
                    3 -> InfoScreen()
                }
            }
        }
    }
}
