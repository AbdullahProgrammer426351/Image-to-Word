package com.image.word.converter.convert.docx.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ads.InterstitialAdManager
import com.image.word.converter.convert.docx.data.ImageToWordApi
import com.image.word.converter.convert.docx.ui.components.LottieAssetView
import com.image.word.converter.convert.docx.ui.state.MainViewModel
import com.image.word.converter.convert.docx.ui.state.SessionState
import com.image.word.converter.convert.docx.util.DailyAttemptManager
import com.image.word.converter.convert.docx.util.NetworkMonitor
import com.image.word.converter.convert.docx.util.decodeBitmap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Composable
fun ProcessingScreen(
    activity: Activity,
    sessionState: SessionState,
    mainViewModel: MainViewModel,
    attemptManager: DailyAttemptManager,
    isSubscribed: Boolean,
    onResetToHome: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val session by sessionState.uiState.collectAsState()
    val api = remember { ImageToWordApi() }
    var showNoInternet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!NetworkMonitor.isConnected(context)) {
            showNoInternet = true
            return@LaunchedEffect
        }

        val imageUris = session.selectedImageUris
        if (imageUris.isEmpty()) {
            onResetToHome()
            return@LaunchedEffect
        }

        val images = imageUris.mapNotNull { decodeBitmap(context.contentResolver, it) }
        if (images.isEmpty()) {
            onResetToHome()
            return@LaunchedEffect
        }

        val converted = coroutineScope {
            images.mapIndexed { index, bitmap ->
                async {
                    val fileUrl = api.uploadImage(bitmap)
                    if (fileUrl.isNullOrBlank()) return@async null
                    
                    var fileName = "Word_${System.currentTimeMillis()}_$index"
                    var attempt = 1
                    while (mainViewModel.doesNameExist(fileName)) {
                        fileName = "Word_${System.currentTimeMillis()}_${index}_$attempt"
                        attempt++
                    }

                    mainViewModel.saveConvertedNow(
                        image = bitmap,
                        fileName = fileName,
                        fileUrl = fileUrl,
                    )
                }
            }.awaitAll().filterNotNull()
        }

        if (converted.isEmpty()) {
            showNoInternet = true
            return@LaunchedEffect
        }

        converted.forEach { mainViewModel.downloadAndUpdateItem(it) }

        if (!isSubscribed) {
            attemptManager.increase()
        }
        sessionState.setConvertedItems(converted)

        InterstitialAdManager.shared.show(activity) {
            onDone()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(20.dp)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(18.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LottieAssetView(
                fileName = "scan_gif.json",
                modifier = Modifier.size(200.dp),
            )
            Text(
                text = stringResource(R.string.converting_images),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.word_processing_message),
                modifier = Modifier.padding(top = 8.dp, start = 20.dp, end = 20.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.navigationBarsPadding())
    }

    if (showNoInternet) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.no_internet_title)) },
            text = { Text(stringResource(R.string.word_processing_failed_message)) },
            confirmButton = {
                TextButton(onClick = onResetToHome) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}
