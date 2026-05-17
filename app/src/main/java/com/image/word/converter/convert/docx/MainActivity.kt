package com.image.word.converter.convert.docx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.google.android.gms.ads.MobileAds
import com.image.word.converter.convert.docx.ui.navigation.AppNavGraph
import com.image.word.converter.convert.docx.ui.navigation.Destination
import com.image.word.converter.convert.docx.ui.theme.ImageToWordTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MobileAds.initialize(this) {}
        setContent {
            ImageToWordTheme {
                val backStack = remember { mutableStateListOf<Destination>(Destination.Splash) }
                AppNavGraph(backStack = backStack)
            }
        }
    }
}
