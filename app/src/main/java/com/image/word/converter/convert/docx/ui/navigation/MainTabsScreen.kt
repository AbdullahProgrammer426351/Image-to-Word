package com.image.word.converter.convert.docx.ui.navigation

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ads.BannerAd
import com.image.word.converter.convert.docx.ads.InterstitialAdManager
import com.image.word.converter.convert.docx.model.ConvertedItem
import com.image.word.converter.convert.docx.ui.components.AssetImage
import com.image.word.converter.convert.docx.ui.components.HoldOnDialog
import com.image.word.converter.convert.docx.ui.components.WordMainTopBar
import com.image.word.converter.convert.docx.ui.screens.HomeTabScreen
import com.image.word.converter.convert.docx.ui.screens.SavedTabScreen
import com.image.word.converter.convert.docx.ui.screens.SettingsTabScreen
import com.image.word.converter.convert.docx.ui.state.MainViewModel
import com.image.word.converter.convert.docx.ui.state.SessionState
import com.image.word.converter.convert.docx.ui.util.WordFileHelper
import com.image.word.converter.convert.docx.ui.util.shareSavedItem
import com.image.word.converter.convert.docx.util.DailyAttemptManager
import com.image.word.converter.convert.docx.util.createCameraCaptureUri
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import kotlinx.coroutines.launch

private enum class Tab(val titleRes: Int, val icon: String) {
    Home(R.string.tab_home, "home_icon.png"),
    Saved(R.string.tab_saved, "saved_icon.png"),
    Settings(R.string.tab_settings, "setting_icon.png"),
}

@Composable
fun MainTabsScreen(
    navController: NavController,
    mainViewModel: MainViewModel,
    sessionState: SessionState,
    attemptManager: DailyAttemptManager,
    isSubscribed: Boolean,
    onOpenSubscription: () -> Unit,
    onLimitReached: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(Tab.Home) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isPreviewing by remember { mutableStateOf(false) }

    if (isPreviewing) {
        HoldOnDialog()
    }

    fun submitUris(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            sessionState.setSelectedImages(uris)
            navController.navigate(Routes.EditPreview)
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        submitUris(uris)
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        submitUris(uris)
    }

    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            sessionState.setSelectedImages(listOf(uri))
            navController.navigate(Routes.CapturePreview)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val uri = createCameraCaptureUri(context)
            pendingCameraUri = uri
            cameraPicker.launch(uri)
        }
    }

    fun launchCamera() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> {
                val uri = createCameraCaptureUri(context)
                pendingCameraUri = uri
                cameraPicker.launch(uri)
            }
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WordMainTopBar(
                showPremium = !isSubscribed,
                onPremiumClick = onOpenSubscription,
            )
        },
        bottomBar = {
            Column {
                BannerAd(isSubscribed = isSubscribed)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFB4CDE1)),
                )
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = {
                                if (selectedTab != tab && tab == Tab.Saved) {
                                    activity?.let { InterstitialAdManager.frequencyShared.show(it) }
                                }
                                selectedTab = tab
                            },
                            icon = {
                                AssetImage(
                                    name = tab.icon,
                                    modifier = Modifier.height(24.dp),
                                    tint = if (selectedTab == tab) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                            label = { Text(stringResource(tab.titleRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.background,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                Tab.Home -> HomeTabScreen(
                    attemptManager = attemptManager,
                    isSubscribed = isSubscribed,
                    onOpenSubscription = onOpenSubscription,
                    onCamera = { launchCamera() },
                    onGallery = { galleryPicker.launch("image/*") },
                    onFiles = { filePicker.launch(arrayOf("image/*")) },
                    onUrl = { navController.navigate(Routes.UrlImport) },
                )
                Tab.Saved -> SavedTabScreen(
                    mainViewModel = mainViewModel,
                    scope = scope,
                    onOpenItem = { item ->
                        scope.launch {
                            isPreviewing = true
                            val success = WordFileHelper.previewWord(context, item)
                            isPreviewing = false
                            if (!success) {
                                Toast.makeText(context, R.string.word_open_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onShareItem = { shareSavedItem(context, it, scope) },
                )
                Tab.Settings -> SettingsTabScreen(
                    onOpenSubscription = onOpenSubscription,
                    isSubscribed = isSubscribed,
                )
            }
        }
    }
}
