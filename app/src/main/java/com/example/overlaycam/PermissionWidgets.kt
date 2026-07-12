package com.example.overlaycam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun PermissionRequestInProgress() {
    PermissionScreenShell(
        title = "Camera permission",
        message = "Approve the Android permission sheet to use the live camera preview for recreation matching.",
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Waiting for system permission…",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
internal fun PermissionDeniedPrompt(
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    PermissionScreenShell(
        title = "Camera access needed",
        message = "Overlay Cam needs the camera to freeze the recreated scene. You can retry the permission prompt or open Android settings if it was denied permanently.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRequest) { Text("Retry permission") }
            OutlinedButton(onClick = onOpenSettings) { Text("Open settings") }
        }
    }
}

@Composable
private fun PermissionScreenShell(
    title: String,
    message: String,
    content: @Composable () -> Unit,
) {
    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                modifier = Modifier.widthIn(max = 520.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}
