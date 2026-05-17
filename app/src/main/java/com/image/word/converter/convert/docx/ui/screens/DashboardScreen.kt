package com.image.word.converter.convert.docx.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.image.word.converter.convert.docx.ui.navigation.Tab

@Composable
fun DashboardScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateToBatchPreview: (List<String>) -> Unit,
    onNavigateToUrl: () -> Unit,
    onNavigateToPremium: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(Tab.Home) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.title) },
                        icon = {
                            val icon = when (tab) {
                                Tab.Home -> Icons.Rounded.Home
                                Tab.Saved -> Icons.Rounded.History
                                Tab.Settings -> Icons.Rounded.Settings
                            }
                            Icon(icon, contentDescription = tab.title)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                Tab.Home -> HomeScreen(
                    onNavigateToCamera = onNavigateToCamera,
                    onNavigateToBatchPreview = onNavigateToBatchPreview,
                    onNavigateToUrl = onNavigateToUrl
                )
                Tab.Saved -> SavedScreen()
                Tab.Settings -> SettingsScreen(
                    onNavigateToPremium = onNavigateToPremium
                )
            }
        }
    }
}
