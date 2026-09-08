package com.image.word.converter.convert.docx.ui.components

import android.app.Application
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import com.image.word.converter.convert.docx.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RatingDialogViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("rating_prefs", Context.MODE_PRIVATE)
    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog

    fun tryShowAfterConversion() {
        val alreadyRated = prefs.getBoolean("already_rated", false)
        val firstConversionDone = prefs.getBoolean("first_conversion_done", false)
        if (!alreadyRated && !firstConversionDone) {
            prefs.edit().putBoolean("first_conversion_done", true).apply()
            _showDialog.value = true
        }
    }

    fun tryShowOnHomeReturn() {
        val alreadyRated = prefs.getBoolean("already_rated", false)
        if (!alreadyRated) {
            _showDialog.value = true
        }
    }

    fun onRated() {
        prefs.edit().putBoolean("already_rated", true).apply()
        _showDialog.value = false
    }

    fun dismiss() {
        _showDialog.value = false
    }
}

@Composable
fun RatingDialog(
    onDismiss: () -> Unit,
    onLowRating: () -> Unit,
    onHighRating: () -> Unit,
) {
    var rating by remember { mutableIntStateOf(0) }
    val emoji = when (rating.coerceAtLeast(1)) {
        2 -> R.drawable.star2_emoji
        3 -> R.drawable.star3_emoji
        4 -> R.drawable.star4_emoji
        5 -> R.drawable.star5_emoji
        else -> R.drawable.star1_emoji
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 430.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(32.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF8C8C8C)
                        )
                    }
                    Image(
                        painter = painterResource(emoji),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .height(70.dp),
                        contentScale = ContentScale.Fit,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.love_using_this_app),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.Black
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.your_feedback_makes_a_big_difference_and_inspires_us_to_keep_improving),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
                Spacer(Modifier.height(20.dp))

                Box(
                    contentAlignment = Alignment.TopEnd,
                    modifier = Modifier.width((44 * 5 + 60).dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_rating_arrow),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(64.dp),
                        colorFilter = ColorFilter.tint(Color(0xFF5C24FF)),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp)
                    ) {
                        Spacer(Modifier.weight(1f))
                        (1..5).forEach { index ->
                            Icon(
                                imageVector = if (index <= rating) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = "$index stars",
                                tint = if (index <= rating) Color(0xFFFFB300) else Color(0xFF8C8C8C),
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable { rating = index },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (rating > 3) onHighRating() else onLowRating()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF5C24FF),
                                        Color(0xFF371599)
                                    )
                                ),
                                CircleShape,
                            )
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(if (rating > 3) R.string.rate_us else R.string.feedback),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
