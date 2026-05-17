package com.image.word.converter.convert.docx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ui.components.AssetImage
import com.image.word.converter.convert.docx.ui.theme.WordGradients
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: (showWalkthrough: Boolean) -> Unit) {
    val context = LocalContext.current
    var progress by androidx.compose.runtime.remember { mutableFloatStateOf(0f) }
    val splashGradient = Brush.verticalGradient(WordGradients.splashBackground)

    LaunchedEffect(Unit) {
        repeat(100) {
            delay(50)
            progress = (it + 1) / 100f
        }
        val prefs = context.getSharedPreferences("walkthrough_prefs", android.content.Context.MODE_PRIVATE)
        val seenWalkthrough = prefs.getBoolean("seen_walkthrough", false)
        onFinished(!seenWalkthrough)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashGradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            AssetImage(
                name = "ic_image_to_word.png",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
                    .height(300.dp),
            )
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 0.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(10.dp)),
                color = Color(0xFF0046C8),
                trackColor = Color(0xFFF2F2F2),
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
            )
            Text(
                text = stringResource(R.string.loading_data),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }
}
