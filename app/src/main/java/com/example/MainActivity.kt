package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
    STUDIO("Chart", Icons.Filled.ShowChart, Icons.Outlined.ShowChart, "nav_tab_studio"),
    STRATEGY("Strategy", Icons.Filled.Tune, Icons.Outlined.Tune, "nav_tab_strategy"),
    OPTIMIZER("Optimizer", Icons.Filled.AutoFixHigh, Icons.Outlined.AutoFixHigh, "nav_tab_optimizer"),
    RISK("Risk", Icons.Filled.Shield, Icons.Outlined.Shield, "nav_tab_risk"),
    LIBRARY("Library", Icons.Filled.FolderOpen, Icons.Outlined.FolderOpen, "nav_tab_library")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val backtestViewModel: BacktestViewModel = viewModel()
                var currentTab by remember { mutableStateOf(AppNavigationTab.STUDIO) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BentoBackground,
                    bottomBar = {
                        NavigationBar(
                            containerColor = BentoCardBg,
                            contentColor = BentoTextPrimary,
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
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = BentoLilacText,
                                        selectedTextColor = BentoLilac,
                                        indicatorColor = BentoLilacContainer,
                                        unselectedIconColor = BentoTextSecondary,
                                        unselectedTextColor = BentoTextSecondary
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    when (currentTab) {
                        AppNavigationTab.STUDIO -> BacktestStudioScreen(
                            viewModel = backtestViewModel,
                            modifier = Modifier.padding(innerPadding),
                            onNavigateToStrategyBuilder = { currentTab = AppNavigationTab.STRATEGY },
                            onNavigateToRiskManager = { currentTab = AppNavigationTab.RISK }
                        )

                        AppNavigationTab.STRATEGY -> StrategyBuilderScreen(
                            viewModel = backtestViewModel,
                            modifier = Modifier.padding(innerPadding),
                            onBacktestNow = { currentTab = AppNavigationTab.STUDIO }
                        )

                        AppNavigationTab.RISK -> RiskManagementScreen(
                            viewModel = backtestViewModel,
                            modifier = Modifier.padding(innerPadding),
                            onBacktestNow = { currentTab = AppNavigationTab.STUDIO }
                        )

                        AppNavigationTab.OPTIMIZER -> OptimizerScreen(
                            viewModel = backtestViewModel,
                            modifier = Modifier.padding(innerPadding),
                            onNavigateToStudio = { currentTab = AppNavigationTab.STUDIO }
                        )

                        AppNavigationTab.LIBRARY -> SavedStrategiesScreen(
                            viewModel = backtestViewModel,
                            modifier = Modifier.padding(innerPadding),
                            onNavigateToStudio = { currentTab = AppNavigationTab.STUDIO }
                        )
                    }
                }
            }
        }
    }
}
