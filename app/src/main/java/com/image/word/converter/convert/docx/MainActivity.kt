package com.image.word.converter.convert.docx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import com.image.word.converter.convert.docx.ui.components.AssetImage
import com.image.word.converter.convert.docx.ui.state.ThemeMode
import com.image.word.converter.convert.docx.ui.state.ThemeViewModel
import com.image.word.converter.convert.docx.ui.theme.DarkSurfaceVariant
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
