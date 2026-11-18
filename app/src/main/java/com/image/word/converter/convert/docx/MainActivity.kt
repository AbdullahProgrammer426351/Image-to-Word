package com.image.word.converter.convert.docx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.espressotech.monetization.Monetization
import com.espressotech.monetization.ads.AdsGate
import com.espressotech.monetization.ads.AdsInitializer
import com.espressotech.monetization.ads.AdsRemoteConfig
import com.espressotech.monetization.ads.AppOpenAdManager
import com.espressotech.monetization.ads.ConsentManager
import com.espressotech.monetization.ads.FrequencyAppOpenAdManager
import com.espressotech.monetization.ads.InterstitialAdManager
import com.espressotech.monetization.subscriptions.SubscriptionViewModel
import com.espressotech.monetization.subscriptions.rememberSubscriptionCheckoutLauncher
import com.image.word.converter.convert.docx.monetization.AppMonetizationConfig
import com.image.word.converter.convert.docx.ui.AppRoot
import com.image.word.converter.convert.docx.ui.state.ThemeMode
import com.image.word.converter.convert.docx.ui.state.ThemeViewModel
import com.image.word.converter.convert.docx.ui.theme.ImageToWordTheme

class MainActivity : ComponentActivity() {
    private var didGoBackgroundAfterLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Monetization.initialize(this, AppMonetizationConfig.create(this))
        enableEdgeToEdge()

        setContent {
            val subscriptionViewModel: SubscriptionViewModel = viewModel()
            val themeViewModel: ThemeViewModel = viewModel()
            val uiState by subscriptionViewModel.uiState.collectAsState()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val launchStoreResolution = rememberSubscriptionCheckoutLauncher(subscriptionViewModel)

            SideEffect {
                // Until the active store answers the entitlement query, suppress ads as if
                // the user were subscribed. This avoids showing ads to existing subscribers.
                AdsGate.isSubscribedProvider = {
                    !uiState.isAdEligible
                }
            }

            LaunchedEffect(Unit) {
                subscriptionViewModel.onHostReady(this@MainActivity, launchStoreResolution)
            }

            LaunchedEffect(uiState.isEntitlementResolved) {
                if (!uiState.isEntitlementResolved) return@LaunchedEffect
                if (Monetization.config.bypassSubscriptionAndAds) return@LaunchedEffect
                AdsRemoteConfig.initialize(this@MainActivity) {
                    ConsentManager.shared.requestConsent(this@MainActivity) {
                        AdsInitializer.initialize(this@MainActivity)
                        InterstitialAdManager.shared.load(this@MainActivity)
                        InterstitialAdManager.frequencyShared.load(this@MainActivity)
                        AppOpenAdManager.shared.loadIfNeeded(this@MainActivity)
                        AdsGate.canShowResumeAppOpen = true
                    }
                }
            }

            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            ImageToWordTheme(darkTheme = darkTheme) {
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !darkTheme
                }
                AppRoot(activity = this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!AdsGate.canShowResumeAppOpen) return
        if (!didGoBackgroundAfterLaunch) return

        didGoBackgroundAfterLaunch = false
        FrequencyAppOpenAdManager.shared.showIfAvailable(this)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            didGoBackgroundAfterLaunch = true
            FrequencyAppOpenAdManager.shared.load(this)
        }
    }
}
