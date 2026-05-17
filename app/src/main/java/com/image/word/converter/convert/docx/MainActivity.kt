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
import com.image.word.converter.convert.docx.ads.AdsGate
import com.image.word.converter.convert.docx.ads.AdsRemoteConfig
import com.image.word.converter.convert.docx.ads.AppOpenAdManager
import com.image.word.converter.convert.docx.ads.ConsentManager
import com.image.word.converter.convert.docx.ads.FrequencyAppOpenAdManager
import com.image.word.converter.convert.docx.ads.InterstitialAdManager
import com.image.word.converter.convert.docx.subscriptions.SubscriptionViewModel
import com.image.word.converter.convert.docx.ui.navigation.AppNavGraph
import com.image.word.converter.convert.docx.ui.theme.ImageToWordTheme

class MainActivity : ComponentActivity() {
    private var didGoBackgroundAfterLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val subscriptionViewModel: SubscriptionViewModel = viewModel()
            val subscriptionState by subscriptionViewModel.uiState.collectAsState()

            SideEffect {
                AdsGate.isSubscribedProvider = { subscriptionState.isSubscribed }
            }

            LaunchedEffect(Unit) {
                AdsRemoteConfig.initialize(this@MainActivity) {
                    ConsentManager.shared.requestConsent(this@MainActivity) {
                        InterstitialAdManager.shared.load(this@MainActivity)
                        InterstitialAdManager.frequencyShared.load(this@MainActivity)
                        AppOpenAdManager.shared.loadIfNeeded(this@MainActivity)
                        FrequencyAppOpenAdManager.shared.load(this@MainActivity)
                        AdsGate.canShowResumeAppOpen = true
                    }
                }
            }

            ImageToWordTheme {
                AppNavGraph(
                    subscriptionState = subscriptionState,
                    subscriptionViewModel = subscriptionViewModel,
                    activity = this@MainActivity
                )
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
