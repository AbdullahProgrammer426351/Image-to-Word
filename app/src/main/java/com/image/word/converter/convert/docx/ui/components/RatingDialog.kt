package com.image.word.converter.convert.docx.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.image.word.converter.convert.docx.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class RatingDialogState(
    val showDialog: Boolean = false,
)

class RatingDialogViewModel(context: Context) {
    private val prefs = context.getSharedPreferences("rating_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(RatingDialogState())
    val state: StateFlow<RatingDialogState> = _state

    init {
        val shouldShow = prefs.getBoolean("should_show_rating", true)
        if (shouldShow) {
            _state.update { it.copy(showDialog = true) }
        }
    }

    fun hide(save: Boolean) {
        if (save) {
            prefs.edit().putBoolean("should_show_rating", false).apply()
        }
        _state.update { it.copy(showDialog = false) }
    }
}

@Composable
fun RatingDialog(
    onDismiss: () -> Unit,
    onLowRating: () -> Unit,
    onHighRating: () -> Unit,
) {
    var rating by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.rate_us),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { star ->
                        AssetImage(
                            name = "star${star}_emoji.png",
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { rating = star },
                        )
                    }
                }
                Button(
                    onClick = {
                        if (rating <= 3) onLowRating() else onHighRating()
                    },
                    enabled = rating > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.submit_rating))
                }
            }
        }
    }
}
