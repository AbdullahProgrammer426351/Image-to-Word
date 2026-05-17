package com.image.word.converter.convert.docx.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.image.word.converter.convert.docx.ui.components.IconOptionCard
import com.image.word.converter.convert.docx.ui.components.ScreenBigBox

@Composable
fun HomeScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateToBatchPreview: (List<String>) -> Unit,
    onNavigateToUrl: () -> Unit
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onNavigateToBatchPreview(uris.map { it.toString() })
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Welcome to smarter scanning",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        ScreenBigBox(
            title = "Gallery",
            desc = "Import photos to create Word documents",
            icon = Icons.Rounded.PhotoLibrary,
            gradientColors = listOf(Color(0xFFFFE0E0), Color(0xFFFF8D8D)),
            onClick = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )

        ScreenBigBox(
            title = "Camera",
            desc = "Capture moments with perfect clarity",
            icon = Icons.Rounded.CameraAlt,
            gradientColors = listOf(Color(0xFFD7E2FF), Color(0xFF8AB9FF)),
            onClick = onNavigateToCamera
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "More Import Options",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Upload images from Drive, links, or files",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                IconOptionCard(
                    title = "G-Drive",
                    icon = Icons.Rounded.CloudUpload,
                    gradientColors = listOf(Color(0xFFF3F7FF), Color(0xFFD3E3FF)),
                    onClick = { /* TODO */ }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                IconOptionCard(
                    title = "URL Link",
                    icon = Icons.Rounded.Link,
                    gradientColors = listOf(Color(0xFFFFFDEB), Color(0xFFFFF9C8)),
                    onClick = onNavigateToUrl
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                IconOptionCard(
                    title = "Files",
                    icon = Icons.Rounded.Description,
                    gradientColors = listOf(Color(0xFFEBFFE4), Color(0xFFD9FFCC)),
                    onClick = { /* TODO */ }
                )
            }
        }
    }
}
