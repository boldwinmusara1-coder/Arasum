package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tradestrat.ui.BacktestViewModel
import com.example.tradestrat.ui.screens.*
import com.example.ui.theme.*

enum class AppNavigationTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_tab_dashboard"),
    BACKTEST("Backtest", Icons.AutoMirrored.Filled.ShowChart, Icons.AutoMirrored.Outlined.ShowChart, "nav_tab_backtest"),
    STRATEGIES("Strategies", Icons.Filled.Tune, Icons.Outlined.Tune, "nav_tab_strategies"),
    SMC_ICT("SMC/ICT", Icons.Filled.AccountBalance, Icons.Outlined.AccountBalance, "nav_tab_smc_ict"),
    RESULTS("Results", Icons.Filled.Assessment, Icons.Outlined.Assessment, "nav_tab_results"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_tab_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val backtestViewModel: BacktestViewModel = viewModel()
                var currentTab by remember { mutableStateOf(AppNavigationTab.DASHBOARD) }
                val theme = LocalAppTheme.current

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = theme.background,
                    bottomBar = {
                        NavigationBar(
                            containerColor = theme.surface,
                            contentColor = theme.textPrimary,
                            tonalElevation = 0.dp
                        ) {
                            AppNavigationTab.values().forEach { tab ->
                                val isSelected = currentTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = theme.brandPrimaryText,
                                        selectedTextColor = theme.brandPrimary,
                                        indicatorColor = theme.brandPrimaryContainer,
                                        unselectedIconColor = theme.textSecondary,
                                        unselectedTextColor = theme.textSecondary
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    when (currentTab) {
                        AppNavigationTab.DASHBOARD -> DashboardScreen(
                            viewModel = backtestViewModel,
                            modifier = Modifier.padding(innerPadding),
                            onNavigateToBacktest = { currentTab = AppNavigationTab.BACKTEST },
                            onNavigateToStrategies = { currentTab = AppNavigationTab.STRATEGIES },
                            onNavigateToResults = { currentTab = AppNavigationTab.RESULTS }
                        )

                        AppNavigationTab.BACKTEST -> BacktestScreen(
                            viewModel = backtestViewModel,
                            modifier = Modifier.padding(innerPadding),
                            onBacktestComplete = { currentTab = AppNavigationTab.RESULTS }
                        )

                        AppNavigationTab.STRATEGIES -> StrategiesScreen(
                            viewModel = backtestViewModel,
                            modifier = Modifier.padding(innerPadding),
                            onNavigateToBacktest = { currentTab = AppNavigationTab.BACKTEST }
                        )

                        AppNavigationTab.SMC_ICT -> SmcIctScreen(
                            viewModel = backtestViewModel,
                            modifier = Modifier.padding(innerPadding),
                            onNavigateToBacktest = { currentTab = AppNavigationTab.BACKTEST }
                        )

                        AppNavigationTab.RESULTS -> ResultsScreen(
                            viewModel = backtestViewModel,
                            modifier = Modifier.padding(innerPadding),
                            onNavigateToBacktest = { currentTab = AppNavigationTab.BACKTEST }
                        )

                        AppNavigationTab.SETTINGS -> SettingsScreen(
                            viewModel = backtestViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
