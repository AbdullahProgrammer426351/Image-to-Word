package com.image.word.converter.convert.docx.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.image.word.converter.convert.docx.data.local.datastore.DailyAttemptManager
import com.image.word.converter.convert.docx.data.local.db.ConvertedItem
import com.image.word.converter.convert.docx.data.repository.ConversionRepository
import kotlinx.coroutines.launch

@Composable
fun ProcessingScreen(
    imageUris: List<String>,
    onConversionComplete: (List<ConvertedItem>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ConversionRepository(context) }
    val attemptManager = remember { DailyAttemptManager(context) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("scan_gif.json"))
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)

    LaunchedEffect(Unit) {
        scope.launch {
            val results = mutableListOf<ConvertedItem>()
            var successCount = 0
            
            for (uri in imageUris) {
                val result = repository.convertAndSave(uri)
                if (result.isSuccess) {
                    results.add(result.getOrThrow())
                    successCount++
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                    break
                }
            }
            
            if (successCount == imageUris.size) {
                // Increment attempt count
                attemptManager.increase()
                
                // Show Interstitial Ad before proceeding to result
                com.image.word.converter.convert.docx.ads.InterstitialAdManager.shared.show(context as android.app.Activity) {
                    onConversionComplete(results)
                }
            }
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (errorMessage != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text("Error", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMessage!!, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onBack) {
                        Text("Go Back")
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    LottieAnimation(
                        composition = composition,
                        progress = { progress },
                        modifier = Modifier.size(200.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Converting images...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "We're turning your image(s) into an editable Word file. Please wait.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
