package com.example.overlaycam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
        message = "Complete the Android permission request to enable the live camera preview.",
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Waiting for Android…",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
internal fun CameraAccessChoice(
    wasDenied: Boolean,
    onRequest: () -> Unit,
    onContinueWithPhotos: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    PermissionScreenShell(
        title = if (wasDenied) "Camera access is off" else "How would you like to start?",
        message = if (wasDenied) {
            "You can retry camera access, continue with photos, or open Android settings if permission was permanently denied."
        } else {
            "Use the live camera to freeze a scene, or continue with photos only. Photo mode works without camera access."
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (wasDenied) "Retry camera access" else "Use live camera")
            }
            OutlinedButton(
                onClick = onContinueWithPhotos,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue with photos")
            }
            if (wasDenied) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Android settings")
                }
            }
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
