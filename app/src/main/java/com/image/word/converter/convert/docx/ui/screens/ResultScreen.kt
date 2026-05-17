package com.image.word.converter.convert.docx.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ads.BannerAd
import com.image.word.converter.convert.docx.model.ConvertedItem
import com.image.word.converter.convert.docx.ui.components.AssetImage
import com.image.word.converter.convert.docx.ui.components.ChildTopBar
import com.image.word.converter.convert.docx.ui.components.GradientButton
import com.image.word.converter.convert.docx.ui.state.MainViewModel
import com.image.word.converter.convert.docx.ui.state.SessionState
import com.image.word.converter.convert.docx.ui.util.WordFileHelper
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun ResultScreen(
    sessionState: SessionState,
    mainViewModel: MainViewModel,
    isSubscribed: Boolean,
    onBackHome: () -> Unit,
    onPreview: (ConvertedItem) -> Unit,
) {
    BackHandler(onBack = onBackHome)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by sessionState.uiState.collectAsState()
    val items = session.convertedItems
    var exporting by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ChildTopBar(
            title = stringResource(R.string.converted_file),
            onBack = onBackHome,
            showBack = false,
            action = {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = stringResource(R.string.back_home),
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(onClick = onBackHome),
                )
            },
        )

        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.loading_data),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (items.size == 1) {
            val item = items.first()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (item.imagePath.isNotBlank() && File(item.imagePath).exists()) {
                    AsyncImage(
                        model = File(item.imagePath),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    AssetImage(name = "ic_word.png", modifier = Modifier.size(120.dp))
                }
                Text(item.fileName, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ResultAction("ic_eye.svg", stringResource(R.string.preview)) { onPreview(item) }
                    ResultAction("ic_share_ex.svg", stringResource(R.string.share)) {
                        scope.launch {
                            if (!WordFileHelper.shareWord(context, item)) {
                                Toast.makeText(context, R.string.word_share_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .clickable { onPreview(item) }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (item.imagePath.isNotBlank() && File(item.imagePath).exists()) {
                            AsyncImage(
                                model = File(item.imagePath),
                                contentDescription = null,
                                modifier = Modifier.size(100.dp),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            AssetImage(name = "ic_word.png", modifier = Modifier.size(80.dp))
                        }
                        Text(item.fileName, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }

        GradientButton(
            text = stringResource(R.string.export),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            icon = "ic_download.svg",
            enabled = items.isNotEmpty() && !exporting,
            onClick = {
                val first = items.firstOrNull() ?: return@GradientButton
                exporting = true
                scope.launch {
                    val ok = WordFileHelper.exportWord(context, first)
                    exporting = false
                    if (!ok) {
                        Toast.makeText(context, R.string.word_export_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )

        BannerAd(isSubscribed = isSubscribed)
    }
}

@Composable
private fun ResultAction(icon: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
    ) {
        AssetImage(name = icon, modifier = Modifier.size(28.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}
