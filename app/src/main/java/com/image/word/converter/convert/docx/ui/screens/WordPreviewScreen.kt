package com.image.word.converter.convert.docx.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ui.components.ChildTopBar
import com.image.word.converter.convert.docx.ui.state.MainViewModel
import com.image.word.converter.convert.docx.ui.util.WordFileHelper
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun WordPreviewScreen(
    itemId: String,
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by mainViewModel.uiState.collectAsState()
    val item = state.savedItems.firstOrNull { it.id == itemId }
    var loading by remember { mutableStateOf(true) }
    var localFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(item?.id) {
        loading = true
        localFile = item?.let { WordFileHelper.downloadDocx(context, it) }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChildTopBar(title = item?.fileName.orEmpty(), onBack = onBack)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                item == null -> Text(stringResource(R.string.no_saved_files))
                loading -> CircularProgressIndicator()
                localFile != null -> {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        localFile!!,
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            uri,
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    LaunchedEffect(uri) {
                        runCatching {
                            context.startActivity(Intent.createChooser(intent, null))
                        }.onFailure {
                            Toast.makeText(context, R.string.word_open_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                    Text(
                        text = stringResource(R.string.preview),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Text(stringResource(R.string.word_open_failed))
            }
        }
    }
}
