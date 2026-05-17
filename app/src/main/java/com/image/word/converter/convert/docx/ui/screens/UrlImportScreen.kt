package com.image.word.converter.convert.docx.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ads.BannerAd
import com.image.word.converter.convert.docx.ui.components.ChildTopBar
import com.image.word.converter.convert.docx.ui.components.FullWidthActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun UrlImportScreen(
    isSubscribed: Boolean,
    onBack: () -> Unit,
    onImageReady: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ChildTopBar(title = stringResource(R.string.scan_from_url), onBack = onBack)

        Column(
            modifier = Modifier.weight(1f).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.enter_your_url),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            error = null
                        },
                        label = {
                            Text(stringResource(R.string.enter_image_url))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(

                            // typed text color
                            focusedTextColor = MaterialTheme.colorScheme.primary,
                            unfocusedTextColor = MaterialTheme.colorScheme.primary,

                            // placeholder/label color
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.primary,

                            // cursor color
                            cursorColor = MaterialTheme.colorScheme.primary,

                            // border colors
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            if (loading) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
            }

            if (!error.isNullOrBlank()) {
                Text(text = error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }

            FullWidthActionButton(
                title = stringResource(R.string.import_image),
                filled = true,
                onClick = {
                    loading = true
                    CoroutineScope(Dispatchers.IO).launch {
                        val bitmap = runCatching {
                            URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                        }.getOrNull()
                        withContext(Dispatchers.Main) {
                            loading = false
                            if (bitmap == null) {
                                error = context.getString(R.string.invalid_url)
                            } else {
                                onImageReady(bitmap)
                            }
                        }
                    }
                },
            )
        }

        BannerAd(isSubscribed = isSubscribed)
    }
}
