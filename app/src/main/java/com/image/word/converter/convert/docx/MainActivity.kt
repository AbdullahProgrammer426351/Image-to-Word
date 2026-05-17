package com.image.word.converter.convert.docx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import com.google.firebase.auth.FirebaseAuth
import com.image.word.converter.convert.docx.ads.AdsGate
import com.image.word.converter.convert.docx.ads.AdsInitializer
import com.image.word.converter.convert.docx.ads.AdsRemoteConfig
import com.image.word.converter.convert.docx.ads.AppOpenAdManager
import com.image.word.converter.convert.docx.ads.ConsentManager
import com.image.word.converter.convert.docx.ads.FrequencyAppOpenAdManager
import com.image.word.converter.convert.docx.ads.InterstitialAdManager
import com.image.word.converter.convert.docx.subscriptions.SubscriptionViewModel
import com.image.word.converter.convert.docx.ui.AppRoot
import com.image.word.converter.convert.docx.ui.state.ThemeMode
import com.image.word.converter.convert.docx.ui.state.ThemeViewModel
import com.image.word.converter.convert.docx.ui.theme.ImageToWordTheme

class MainActivity : ComponentActivity() {
    private var didGoBackgroundAfterLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val subscriptionViewModel: SubscriptionViewModel = viewModel()
            val themeViewModel: ThemeViewModel = viewModel()
            val uiState by subscriptionViewModel.uiState.collectAsState()
            val themeMode by themeViewModel.themeMode.collectAsState()

            SideEffect {
                AdsGate.isSubscribedProvider = { uiState.isSubscribed }
            }

            LaunchedEffect(Unit) {
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
