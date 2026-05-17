package com.image.word.converter.convert.docx.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.image.word.converter.convert.docx.ui.screens.*

@Composable
fun AppNavGraph(backStack: SnapshotStateList<Destination>) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = { key ->
            when (key) {
                is Destination.Splash -> {
                    NavEntry(key) {
                        SplashScreen(
                            onNavigateToDashboard = {
                                if (backStack.isNotEmpty()) {
                                    backStack.clear()
                                }
                                backStack.add(Destination.Dashboard)
                            }
                        )
                    }
                }
                is Destination.Dashboard -> {
                    NavEntry(key) {
                        DashboardScreen(
                            onNavigateToCamera = {
                                backStack.add(Destination.Camera)
                            },
                            onNavigateToBatchPreview = { uris ->
                                backStack.add(Destination.BatchPreview(uris))
                            },
                            onNavigateToUrl = {
                                backStack.add(Destination.UrlImport)
                            },
                            onNavigateToPremium = {
                                backStack.add(Destination.Premium)
                            }
                        )
                    }
                }
                is Destination.Camera -> {
                    NavEntry(key) {
                        CameraScreen(
                            onImageCaptured = { uri ->
                                backStack.removeLastOrNull()
                                backStack.add(Destination.BatchPreview(listOf(uri)))
                            }
                        )
                    }
                }
                is Destination.BatchPreview -> {
                    NavEntry(key) {
                        BatchPreviewScreen(
                            imageUris = key.imageUris,
                            onBack = { backStack.removeLastOrNull() },
                            onNext = { uris ->
                                backStack.add(Destination.Processing(uris))
                            },
                            onLimitReached = {
                                backStack.add(Destination.Premium)
                            }
                        )
                    }
                }
                is Destination.UrlImport -> {
                    NavEntry(key) {
                        UrlImportScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onUrlConfirmed = { url ->
                                backStack.removeLastOrNull()
                                backStack.add(Destination.BatchPreview(listOf(url)))
                            }
                        )
                    }
                }
                is Destination.Processing -> {
                    NavEntry(key) {
                        ProcessingScreen(
                            imageUris = key.imageUris,
                            onConversionComplete = { items ->
                                backStack.removeLastOrNull()
                                backStack.add(Destination.Result(items))
                            },
                            onBack = { backStack.removeLastOrNull() }
                        )
                    }
                }
                is Destination.Result -> {
                    NavEntry(key) {
                        ResultScreen(
                            items = key.items,
                            onBack = {
                                // Clear up to dashboard
                                while (backStack.isNotEmpty() && backStack.lastOrNull() != Destination.Dashboard) {
                                    backStack.removeAt(backStack.size - 1)
                                }
                            }
                        )
                    }
                }
                is Destination.Premium -> {
                    NavEntry(key) {
                        SubscriptionScreen(onClose = { backStack.removeLastOrNull() })
                    }
                }
            }
        }
    )
}
