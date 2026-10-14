package com.image.word.converter.convert.docx.ui

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ads.AppOpenAdManager
import com.image.word.converter.convert.docx.ads.InterstitialAdManager
import com.image.word.converter.convert.docx.subscriptions.SubscriptionScreen
import com.image.word.converter.convert.docx.subscriptions.SubscriptionViewModel
import com.image.word.converter.convert.docx.ui.components.AppNavigationHost
import com.image.word.converter.convert.docx.ui.navigation.MainTabsScreen
import com.image.word.converter.convert.docx.ui.navigation.Routes
import com.image.word.converter.convert.docx.ui.screens.AdjustScreen
import com.image.word.converter.convert.docx.ui.screens.BatchPreviewScreen
import com.image.word.converter.convert.docx.ui.screens.CapturePreviewScreen
import com.image.word.converter.convert.docx.ui.screens.FilterScreen
import com.image.word.converter.convert.docx.ui.screens.HighlighterScreen
import com.image.word.converter.convert.docx.ui.screens.CreditAwareProcessingScreen
import com.image.word.converter.convert.docx.ui.screens.ResultScreen
import com.image.word.converter.convert.docx.ui.screens.SignatureOverlayScreen
import com.image.word.converter.convert.docx.ui.screens.SplashScreen
import com.image.word.converter.convert.docx.ui.screens.TextOverlayScreen
import com.image.word.converter.convert.docx.ui.screens.UrlImportScreen
import com.image.word.converter.convert.docx.ui.screens.WalkthroughScreen
import com.image.word.converter.convert.docx.ui.state.MainViewModel
import com.image.word.converter.convert.docx.ui.state.SessionState
import com.image.word.converter.convert.docx.util.DailyAttemptManager
import com.image.word.converter.convert.docx.util.SubscriptionTriggerManager
import com.image.word.converter.convert.docx.backend.BackendEnvironment
import com.image.word.converter.convert.docx.backend.BackendAccountStore
import com.image.word.converter.convert.docx.backend.ConversionAccessPolicy
import com.image.word.converter.convert.docx.backend.ConversionBackendMode
import com.image.word.converter.convert.docx.backend.V2EntryState
import com.image.word.converter.convert.docx.util.bitmapToCacheUri
import androidx.core.content.edit

import com.image.word.converter.convert.docx.ui.components.RatingDialog
import com.image.word.converter.convert.docx.ui.components.RatingDialogViewModel
import com.image.word.converter.convert.docx.util.AppLinks
import kotlinx.coroutines.launch

@Composable
fun AppRoot(activity: Activity) {
    val navController = rememberNavController()
    val sessionState = remember { SessionState() }
    var returnHomeAfterSubscription by rememberSaveable { mutableStateOf(false) }
    val subscriptionViewModel: SubscriptionViewModel = viewModel()
    val mainViewModel: MainViewModel = viewModel()
    val ratingViewModel: RatingDialogViewModel = viewModel()
    val subscriptionState by subscriptionViewModel.uiState.collectAsState()
    val ratingState by ratingViewModel.showDialog.collectAsState()
    val attemptManager = remember { DailyAttemptManager(activity) }
    val limitTrigger = remember { SubscriptionTriggerManager() }
    val showLimit by limitTrigger.showLimitDialog.collectAsState()
    val conversionMode = remember {
        ConversionAccessPolicy.backendMode(BackendEnvironment.current())
    }
    val accountState by BackendAccountStore.repository.state.collectAsState()
    val recoveryScope = rememberCoroutineScope()
    var showBackendRecovery by remember { mutableStateOf(false) }

    if (showBackendRecovery) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBackendRecovery = false },
            title = { androidx.compose.material3.Text(stringResource(R.string.credits_unavailable)) },
            text = { androidx.compose.material3.Text(stringResource(R.string.account_state_retry_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showBackendRecovery = false
                    recoveryScope.launch { BackendAccountStore.repository.refresh() }
                }) {
                    androidx.compose.material3.Text(stringResource(R.string.retry))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showBackendRecovery = false }) {
                    androidx.compose.material3.Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (ratingState) {
        RatingDialog(
            onDismiss = { ratingViewModel.dismiss() },
            onLowRating = {
                ratingViewModel.onRated()
                AppLinks.sendFeedback(activity)
            },
            onHighRating = {
                ratingViewModel.onRated()
                AppLinks.rateApp(activity)
            }
        )
    }

    AppNavigationHost(
        showLimitDialog = showLimit,
        onDismissLimit = { limitTrigger.hideLimit() },
        onSubscribeFromLimit = {
            limitTrigger.hideLimit()
            navController.navigate(Routes.Subscription)
        },
    ) {
        NavHost(navController = navController, startDestination = Routes.Splash) {
            composable(Routes.Splash) {
                SplashScreen(
                    onFinished = { showWalkthrough ->
                        val destination = if (showWalkthrough) {
                            Routes.Walkthrough
                        } else {
                            Routes.main(autoSub = true)
                        }
                        var consumed = false
                        val proceed = {
                            if (!consumed) {
                                consumed = true
                                navController.safeNavigate(destination) {
                                    popUpTo(Routes.Splash) { inclusive = true }
                                }
                            }
                        }
                        val prefs = activity.getSharedPreferences("walkthrough_prefs", android.content.Context.MODE_PRIVATE)
                        if (prefs.getBoolean("seen_walkthrough", false)) {
                            InterstitialAdManager.shared.show(activity, proceed)
                        } else {
                            proceed()
                        }
                    },
                )
            }

            composable(Routes.Walkthrough) {
                var exitFlowStarted by rememberSaveable { mutableStateOf(false) }
                WalkthroughScreen(
                    isSubscribed = subscriptionState.isSubscribed,
                    onDone = {
                        if (exitFlowStarted) return@WalkthroughScreen
                        exitFlowStarted = true

                        activity.getSharedPreferences("walkthrough_prefs", android.content.Context.MODE_PRIVATE)
                            .edit { putBoolean("seen_walkthrough", true) }

                        InterstitialAdManager.shared.show(activity) {
                            navController.safeNavigate(Routes.main(autoSub = true)) {
                                popUpTo(Routes.Walkthrough) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }

            composable(
                route = Routes.Main,
                arguments = listOf(navArgument("autoSub") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                val autoSub = entry.arguments?.getBoolean("autoSub") ?: false
                var autoSubHandled by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(autoSub, subscriptionState.isSubscribed) {
                    if (autoSub && !autoSubHandled && !subscriptionState.isSubscribed) {
                        autoSubHandled = true
                        navController.navigate(Routes.Subscription)
                    }
                }
                MainTabsScreen(
                    navController = navController,
                    mainViewModel = mainViewModel,
                    ratingViewModel = ratingViewModel,
                    sessionState = sessionState,
                    attemptManager = attemptManager,
                    isSubscribed = subscriptionState.isSubscribed,
                    onOpenSubscription = { navController.navigate(Routes.Subscription) },
                    onLimitReached = { limitTrigger.showLimit() },
                )
            }

            composable(Routes.UrlImport) {
                UrlImportScreen(
                    isSubscribed = subscriptionState.isSubscribed,
                    onBack = { navController.popBackStack() },
                    onImageReady = { bitmap ->
                        sessionState.setSelectedImages(listOf(bitmapToCacheUri(activity, bitmap)))
                        navController.navigate(Routes.EditPreview)
                    },
                )
            }

            composable(Routes.CapturePreview) {
                CapturePreviewScreen(
                    sessionState = sessionState,
                    isSubscribed = subscriptionState.isSubscribed,
                    onBack = { navController.popBackStack() },
                    onNext = { navController.navigate(Routes.EditPreview) },
                )
            }

            composable(Routes.EditPreview) {
                BatchPreviewScreen(
                    sessionState = sessionState,
                    isSubscribed = subscriptionState.isSubscribed,
                    onBack = {
                        InterstitialAdManager.shared.show(activity) {
                            navController.safeNavigate { navController.popBackStack() }
                        }
                    },
                    onNext = {
                        if (conversionMode == ConversionBackendMode.V2) {
                            when (ConversionAccessPolicy.v2EntryState(accountState)) {
                                V2EntryState.ALLOWED -> navController.navigate(Routes.Processing)
                                V2EntryState.NOT_ENTITLED -> limitTrigger.showLimit()
                                V2EntryState.LOADING,
                                V2EntryState.TEMPORARILY_UNAVAILABLE,
                                -> {
                                    showBackendRecovery = true
                                    recoveryScope.launch { BackendAccountStore.repository.refresh() }
                                }
                            }
                        } else {
                            if (!ConversionAccessPolicy.canStart(
                                    mode = conversionMode,
                                    isLocallySubscribed = subscriptionState.isSubscribed,
                                    remainingLocalAttempts = attemptManager.remainingAttempts,
                                )
                            ) {
                                limitTrigger.showLimit()
                            } else {
                                navController.navigate(Routes.Processing)
                            }
                        }
                    },
                    onAdjust = { index -> navController.navigate(Routes.adjust(index)) },
                    onFilter = { index -> navController.navigate(Routes.filter(index)) },
                    onHighlighter = { index ->
                        if (!subscriptionState.isSubscribed) {
                            navController.navigate(Routes.Subscription)
                        } else {
                            navController.navigate(Routes.highlighter(index))
                        }
                    },
                    onSignature = { index ->
                        if (!subscriptionState.isSubscribed) {
                            navController.navigate(Routes.Subscription)
                        } else {
                            navController.navigate(Routes.signature(index))
                        }
                    },
                    onText = { index -> navController.navigate(Routes.textOverlay(index)) },
                    onOpenSubscription = { navController.navigate(Routes.Subscription) },
                )
            }

            composable(
                route = Routes.Adjust,
                arguments = listOf(navArgument("index") { type = NavType.IntType }),
            ) { entry ->
                val index = entry.arguments?.getInt("index") ?: 0
                AdjustScreen(
                    index = index,
                    sessionState = sessionState,
                    isSubscribed = subscriptionState.isSubscribed,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.Filter,
                arguments = listOf(navArgument("index") { type = NavType.IntType }),
            ) { entry ->
                val index = entry.arguments?.getInt("index") ?: 0
                FilterScreen(
                    index = index,
                    sessionState = sessionState,
                    isSubscribed = subscriptionState.isSubscribed,
                    onBack = { navController.popBackStack() },
                    onOpenSubscription = { navController.navigate(Routes.Subscription) },
                )
            }

            composable(
                route = Routes.Highlighter,
                arguments = listOf(navArgument("index") { type = NavType.IntType }),
            ) { entry ->
                val index = entry.arguments?.getInt("index") ?: 0
                HighlighterScreen(index = index, sessionState = sessionState, onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.Signature,
                arguments = listOf(navArgument("index") { type = NavType.IntType }),
            ) { entry ->
                val index = entry.arguments?.getInt("index") ?: 0
                SignatureOverlayScreen(
                    index = index,
                    sessionState = sessionState,
                    isSubscribed = subscriptionState.isSubscribed,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.TextOverlay,
                arguments = listOf(navArgument("index") { type = NavType.IntType }),
            ) { entry ->
                val index = entry.arguments?.getInt("index") ?: 0
                TextOverlayScreen(index = index, sessionState = sessionState, onBack = { navController.popBackStack() })
            }

            composable(Routes.Processing) {
                CreditAwareProcessingScreen(
                    activity = activity,
                    sessionState = sessionState,
                    mainViewModel = mainViewModel,
                    attemptManager = attemptManager,
                    isSubscribed = subscriptionState.isSubscribed,
                    onResetToHome = {
                        navController.navigate(Routes.main()) {
                            popUpTo(Routes.Main) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onDone = {
                        navController.safeNavigate(Routes.Result) {
                            popUpTo(Routes.Processing) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.Result) {
                ResultScreen(
                    sessionState = sessionState,
                    mainViewModel = mainViewModel,
                    ratingViewModel = ratingViewModel,
                    isSubscribed = subscriptionState.isSubscribed,
                    onBackHome = {
                        sessionState.clearSelectedImages()
                        sessionState.clearConvertedItems()
                        val shouldShowSubscription =
                            conversionMode == ConversionBackendMode.LEGACY &&
                                !subscriptionState.isSubscribed &&
                                attemptManager.remainingAttempts == 0

                        if (shouldShowSubscription) {
                            returnHomeAfterSubscription = true
                            navController.navigate(Routes.Subscription) {
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate(Routes.main()) {
                                popUpTo(Routes.Main) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }

            composable(Routes.Subscription) {
                SubscriptionScreen(
                    onClose = {
                        if (returnHomeAfterSubscription) {
                            returnHomeAfterSubscription = false
                            navController.navigate(Routes.main()) {
                                popUpTo(Routes.Main) { inclusive = false }
                                launchSingleTop = true
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    viewModel = subscriptionViewModel,
                )
            }
        }
    }
}

private fun NavController.safeNavigate(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    runCatching { navigate(route, builder) }
        .onFailure { Log.w("AppRoot", "Timed-out-safe navigation to $route failed; ignoring", it) }
}

private fun NavController.safeNavigate(builder: () -> Unit) {
    runCatching(builder)
        .onFailure { Log.w("AppRoot", "Safe navigation action failed; ignoring", it) }
}
