package com.example.overlaycam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api // 🔧 add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                OverlayCamApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) // 🔧 silence experimental warning
@Composable
fun OverlayCamApp() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Overlay Cam") })
        }
    ) { innerPadding ->
        OverlayScreen(Modifier.padding(innerPadding))
    }
}
