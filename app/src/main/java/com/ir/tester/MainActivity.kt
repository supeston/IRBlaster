package com.ir.tester

import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.text.style.TextOverflow
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

                LaunchedEffect(uiState.isUsbDongleConnected) {
                    requestedOrientation = if (uiState.isUsbDongleConnected) {
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                    }
                }

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
                NavigationBarItem(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.selectedTab == 0)
                                Icons.Filled.Tune
                            else
                                Icons.Outlined.Tune,
                            contentDescription = "универсал",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "универсал",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.selectedTab == 1)
                                Icons.Filled.PowerSettingsNew
                            else
                                Icons.Outlined.PowerSettingsNew,
                            contentDescription = "брут",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "брут",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    },
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
                            contentDescription = "джаммер",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "джаммер",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )

                NavigationBarItem(
                    selected = uiState.selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = {
                        androidx.compose.material3.BadgedBox(
                            badge = {
                                if (uiState.availableUpdate != null) {
                                    androidx.compose.material3.Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(6.dp)
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (uiState.selectedTab == 3)
                                    Icons.Filled.Info
                                else
                                    Icons.Outlined.Info,
                                contentDescription = "инфо",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    label = {
                        Text(
                            text = "инфо",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    },
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
                .statusBarsPadding()
                .padding(bottom = innerPadding.calculateBottomPadding())
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

                    3 -> InfoScreen(viewModel = viewModel)
                }
            }
        }
    }
}
