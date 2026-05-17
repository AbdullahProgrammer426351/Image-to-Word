package com.image.word.converter.convert.docx.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ui.components.AssetImage
import com.image.word.converter.convert.docx.ui.components.IosTopBar
import com.image.word.converter.convert.docx.ui.state.SessionState
import com.image.word.converter.convert.docx.ui.theme.appWhite
import com.yalantis.ucrop.UCrop
import com.image.word.converter.convert.docx.util.bitmapToCacheUri
import com.image.word.converter.convert.docx.util.decodeBitmap
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun BatchPreviewScreen(
    sessionState: SessionState,
    isSubscribed: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onAdjust: (Int) -> Unit,
    onFilter: (Int) -> Unit,
    onHighlighter: (Int) -> Unit,
    onSignature: (Int) -> Unit,
    onText: (Int) -> Unit,
    onOpenSubscription: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val uiStateState = sessionState.uiState.collectAsState()
    val uiState = uiStateState.value
    val images = uiState.selectedImageUris

    val pagerState = rememberPagerState { images.size }
    val selectedIndex = pagerState.currentPage
    val scope = rememberCoroutineScope()

    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resultUri = UCrop.getOutput(result.data!!)
            resultUri?.let {
                val bitmap = decodeBitmap(context.contentResolver, it)
                if (bitmap != null) {
                    sessionState.updateImage(pagerState.currentPage, bitmapToCacheUri(context, bitmap))
                }
            }
        }
    }

    if (images.isEmpty()) {
        onBack()
        return
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        IosTopBar(
            title = stringResource(R.string.preview),
            onBack = onBack,
            onAction = onNext
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                HorizontalPager(state = pagerState) { index ->
                    val imageUri = images.getOrNull(index)
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.large
                            ),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                androidx.compose.material3.IconButton(onClick = {
                    scope.launch {
                        if (pagerState.currentPage > 0) {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.prev_page),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${selectedIndex + 1}/${images.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.material3.IconButton(onClick = {
                    scope.launch {
                        if (pagerState.currentPage < images.size - 1) {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.next_page),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(appWhite())
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                EditActionButton(
                    icon = "icLeft.svg",
                    label = stringResource(R.string.left),
                    isSubscribed = isSubscribed,
                    onClick = {
                        val bitmap = decodeBitmap(
                            context.contentResolver,
                            images[selectedIndex]
                        ) ?: return@EditActionButton

                        sessionState.updateImage(
                            selectedIndex,
                            bitmapToCacheUri(context, rotate(bitmap, -90f))
                        )
                    },
                    modifier = Modifier.padding(start = 8.dp)
                )

                EditActionButton(
                    icon = "icRight.svg",
                    label = stringResource(R.string.right),
                    isSubscribed = isSubscribed,
                    onClick = {
                        val bitmap = decodeBitmap(
                            context.contentResolver,
                            images[selectedIndex]
                        ) ?: return@EditActionButton

                        sessionState.updateImage(
                            selectedIndex,
                            bitmapToCacheUri(context, rotate(bitmap, 90f))
                        )
                    }
                )

                EditActionButton(
                    icon = "icCrop.svg",
                    label = stringResource(R.string.crop),
                    isSubscribed = isSubscribed,
                    onClick = {
                        val sourceUri = images[pagerState.currentPage]

                        val destinationUri = Uri.fromFile(
                            File(
                                context.cacheDir,
                                "cropped_${System.currentTimeMillis()}.jpg"
                            )
                        )

                        val intent = UCrop.of(sourceUri, destinationUri)
                            .withOptions(
                                UCrop.Options().apply {
                                    setFreeStyleCropEnabled(true)
                                    setToolbarTitle("Crop Image")
                                }
                            )
                            .getIntent(context)

                        cropLauncher.launch(intent)
                    }
                )

                EditActionButton(
                    icon = "adjustIconEx.svg",
                    label = stringResource(R.string.adjust),
                    isSubscribed = isSubscribed,
                    onClick = { onAdjust(selectedIndex) }
                )

                EditActionButton(
                    icon = "colorIconEx.svg",
                    label = stringResource(R.string.filter),
                    isSubscribed = isSubscribed,
                    onClick = { onFilter(selectedIndex) }
                )

                EditActionButton(
                    icon = "highlighterIconEx.svg",
                    label = stringResource(R.string.highlighter),
                    isSubscribed = isSubscribed,
                    onClick = {
                        if (!isSubscribed) onOpenSubscription() else onHighlighter(selectedIndex)
                    },
                    paid = true
                )

                EditActionButton(
                    icon = "signature_icon.png",
                    label = stringResource(R.string.signature),
                    isSubscribed = isSubscribed,
                    onClick = {
                        if (!isSubscribed) onOpenSubscription() else onSignature(selectedIndex)
                    },
                    paid = true
                )

                EditActionButton(
                    icon = "textIconEx.svg",
                    label = stringResource(R.string.text),
                    isSubscribed = isSubscribed,
                    onClick = {
                        if (!isSubscribed) onOpenSubscription() else onText(selectedIndex)
                    },
                    paid = true,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            com.image.word.converter.convert.docx.ads.BannerAd(
                isSubscribed = isSubscribed
            )

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun EditActionButton(
    icon: String,
    label: String,
    isSubscribed: Boolean,
    modifier: Modifier = Modifier,
    paid: Boolean = false,
    onClick: () -> Unit
) {
    Box(contentAlignment = Alignment.TopEnd){
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .clickable { onClick() }
                .padding(8.dp)
        ) {
             
            AssetImage(
                name = icon,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )

            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (paid && !isSubscribed) {
            AssetImage(
                name = "icCrown.svg",
                modifier = Modifier
                    .padding(8.dp)
                    .size(12.dp)
                    .background(Color.Transparent)

            )
        }
    }
}

private fun rotate(source: Bitmap, degree: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degree) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}
