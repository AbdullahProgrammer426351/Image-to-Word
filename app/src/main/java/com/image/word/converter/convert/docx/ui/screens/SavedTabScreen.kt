package com.image.word.converter.convert.docx.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.model.ConvertedItem
import com.image.word.converter.convert.docx.ui.components.AssetImage
import com.image.word.converter.convert.docx.ui.state.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope

@Composable
fun SavedTabScreen(
    mainViewModel: MainViewModel,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
    onOpenItem: (ConvertedItem) -> Unit,
    onShareItem: (ConvertedItem) -> Unit,
) {
    val context = LocalContext.current
    val state by mainViewModel.uiState.collectAsState()
    var renameTarget by remember { mutableStateOf<ConvertedItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ConvertedItem?>(null) }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_file)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.file_name)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget
                    if (target != null && renameText.isNotBlank()) {
                        val newName = renameText.trim()
                        if (newName != target.fileName && mainViewModel.doesNameExist(newName)) {
                            Toast.makeText(context, R.string.name_already_exists, Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        mainViewModel.rename(target, newName)
                    }
                    renameTarget = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget?.let { mainViewModel.delete(it) }
                    deleteTarget = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (state.savedItems.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AssetImage(name = "ic_empty.svg", modifier = Modifier.size(120.dp))
            Text(
                text = stringResource(R.string.no_files_yet),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.your_converted_files_will_appear_here),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        items(state.savedItems, key = { it.id }) { item ->
            SavedRow(
                item = item,
                onOpen = { onOpenItem(item) },
                onRename = {
                    renameTarget = item
                    renameText = item.fileName
                },
                onDelete = { deleteTarget = item },
                onShare = { onShareItem(item) },
            )
        }
    }
}

@Composable
private fun SavedRow(
    item: ConvertedItem,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val time = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(item.createdAt))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .border(
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(10.dp),
            )
            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.imagePath.isNotBlank() && File(item.imagePath).exists()) {
            AsyncImage(
                model = File(item.imagePath),
                contentDescription = null,
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            AssetImage(name = "ic_word.png", modifier = Modifier.size(62.dp))
        }
        Column(modifier = Modifier
            .weight(1f)
            .padding(start = 10.dp)) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 3.dp),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onRename)
                        .background(Color.Gray.copy(alpha = 0.2f), CircleShape)
                        .padding(5.dp),
                )
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.share),
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(24.dp)
                        .clickable(onClick = onShare)
                        .background(Color.Gray.copy(alpha = 0.2f), CircleShape)
                        .padding(5.dp),
                )
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onDelete)
                        .background(Color.Gray.copy(alpha = 0.2f), CircleShape)
                        .padding(5.dp),
                )
            }
        }
    }
}
