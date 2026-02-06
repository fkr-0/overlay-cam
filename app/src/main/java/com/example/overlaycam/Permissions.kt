package com.example.overlaycam

import android.Manifest
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val permission = Manifest.permission.CAMERA
    var granted by rememberSaveable { mutableStateOf(false) }
    var shouldExplain by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted = isGranted
        if (!isGranted) shouldExplain = true
    }

    LaunchedEffect(Unit) {
        // Kick off request on first launch
        launcher.launch(permission)
    }

    if (granted) {
        content()
    } else {
        PermissionDialog(
            onRequest = { launcher.launch(permission) }
        )
    }
}

@Composable
private fun PermissionDialog(onRequest: () -> Unit) {
    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Camera permission is required.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequest) { Text("Grant") }
        }
    }
}