package com.image.word.converter.convert.docx.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.image.word.converter.convert.docx.data.local.datastore.DailyAttemptManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchPreviewScreen(
    imageUris: List<String>,
    onBack: () -> Unit,
    onNext: (List<String>) -> Unit,
    onLimitReached: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var images by remember { mutableStateOf(imageUris) }
    var selectedIndex by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }

    val attemptManager = remember { DailyAttemptManager(context) }

    LaunchedEffect(Unit) {
        com.image.word.converter.convert.docx.ads.InterstitialAdManager.shared.load(context as android.app.Activity)
    }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { croppedUri ->
                val newList = images.toMutableList()
                newList[selectedIndex] = croppedUri.toString()
                images = newList
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Preview (${selectedIndex + 1}/${images.size})",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                if (attemptManager.canUse(false)) {
                                    onNext(images)
                                } else {
                                    onLimitReached()
                                }
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Next", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        },
        bottomBar = {
            EditingToolbar(
                onRotate = {
                    scope.launch {
                        isProcessing = true
                        val rotatedUri = rotateImage(context, images[selectedIndex])
                        rotatedUri?.let {
                            val newList = images.toMutableList()
                            newList[selectedIndex] = it
                            images = newList
                        }
                        isProcessing = false
                    }
                },
                onCrop = {
                    val uri = Uri.parse(images[selectedIndex])
                    cropLauncher.launch(
                        CropImageContractOptions(uri, CropImageOptions())
                    )
                },
                onFilter = {
                    scope.launch {
                        isProcessing = true
                        val filteredUri = applyGrayscaleFilter(context, images[selectedIndex])
                        filteredUri?.let {
                            val newList = images.toMutableList()
                            newList[selectedIndex] = it
                            images = newList
                        }
                        isProcessing = false
                    }
                },
                onDelete = {
                    if (images.size > 1) {
                        val newList = images.toMutableList()
                        newList.removeAt(selectedIndex)
                        images = newList
                        selectedIndex = selectedIndex.coerceAtMost(images.size - 1)
                    } else {
                        onBack()
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (images.isNotEmpty()) {
                    AsyncImage(
                        model = images[selectedIndex],
                        contentDescription = "Selected Image",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                itemsIndexed(images) { index, uri ->
                    Card(
                        onClick = { selectedIndex = index },
                        modifier = Modifier
                            .size(76.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = if (selectedIndex == index) {
                            CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                width = 3.dp
                            )
                        } else null,
                        elevation = CardDefaults.cardElevation(defaultElevation = if (selectedIndex == index) 4.dp else 0.dp)
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditingToolbar(
    onRotate: () -> Unit,
    onCrop: () -> Unit,
    onFilter: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(vertical = 12.dp, horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolbarAction(icon = Icons.AutoMirrored.Rounded.RotateRight, label = "Rotate", onClick = onRotate)
            ToolbarAction(icon = Icons.Rounded.Crop, label = "Crop", onClick = onCrop)
            ToolbarAction(icon = Icons.Rounded.FilterBAndW, label = "Filter", onClick = onFilter)
            ToolbarAction(icon = Icons.Rounded.Delete, label = "Delete", onClick = onDelete, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun ToolbarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(icon, contentDescription = label, tint = tint)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Medium)
        }
    }
}

private suspend fun rotateImage(context: Context, uriString: String): String? = withContext(Dispatchers.IO) {
    try {
        val uri = Uri.parse(uriString)
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        val matrix = Matrix().apply { postRotate(90f) }
        val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
        
        saveBitmapToInternal(context, rotatedBitmap)
    } catch (e: Exception) {
        null
    }
}

private suspend fun applyGrayscaleFilter(context: Context, uriString: String): String? = withContext(Dispatchers.IO) {
    try {
        val uri = Uri.parse(uriString)
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        
        val width = originalBitmap.width
        val height = originalBitmap.height
        val grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val canvas = android.graphics.Canvas(grayBitmap)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
        
        saveBitmapToInternal(context, grayBitmap)
    } catch (e: Exception) {
        null
    }
}

private fun saveBitmapToInternal(context: Context, bitmap: Bitmap): String {
    val file = File(context.cacheDir, "edited_${UUID.randomUUID()}.jpg")
    val out = FileOutputStream(file)
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    out.flush()
    out.close()
    return Uri.fromFile(file).toString()
}
