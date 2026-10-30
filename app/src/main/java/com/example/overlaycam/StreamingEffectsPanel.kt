package com.example.overlaycam

import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StreamingEffectsPanel(
    state: StreamingEffectsState,
    outputPortText: String,
    streamStatus: String,
    presetName: String,
    presetNames: List<String>,
    obsHost: String,
    obsPort: String,
    obsPassword: String,
    obsScene: String,
    obsSecure: Boolean,
    obsStatus: String,
    obsState: ObsConnectionState,
    onStateChange: (StreamingEffectsState) -> Unit,
    onOutputPortTextChange: (String) -> Unit,
    onPickPictureInPicture: () -> Unit,
    onPickLogo: () -> Unit,
    onPickCustomFont: () -> Unit,
    onPresetNameChange: (String) -> Unit,
    onSavePreset: () -> Unit,
    onLoadPreset: (String) -> Unit,
    onObsHostChange: (String) -> Unit,
    onObsPortChange: (String) -> Unit,
    onObsPasswordChange: (String) -> Unit,
    onObsSceneChange: (String) -> Unit,
    onObsSecureChange: (Boolean) -> Unit,
    onObsConnect: () -> Unit,
    onObsDisconnect: () -> Unit,
    onObsSetScene: () -> Unit,
    onObsStartStream: () -> Unit,
    onObsStopStream: () -> Unit,
    onObsStartVirtualCamera: () -> Unit,
    onObsStopVirtualCamera: () -> Unit,
) {
    HorizontalDivider()
    FxHeading(stringResource(R.string.fx_title), MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.fx_intro),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    FxToggleRow(
        label = stringResource(R.string.fx_chroma_key),
        checked = state.chromaKey.enabled,
        onCheckedChange = { enabled ->
            onStateChange(state.copy(chromaKey = state.chromaKey.copy(enabled = enabled)))
        },
    )
    if (state.chromaKey.enabled) {
        Text(stringResource(R.string.fx_key_source), style = MaterialTheme.typography.labelLarge)
        FxFlow {
            ChromaTarget.entries.forEach { target ->
                FilterChip(
                    selected = state.chromaKey.target == target,
                    onClick = {
                        onStateChange(state.copy(chromaKey = state.chromaKey.copy(target = target)))
                    },
                    label = { Text(stringResource(targetLabel(target))) },
                )
            }
        }
        Text(stringResource(R.string.fx_key_color), style = MaterialTheme.typography.labelLarge)
        FxFlow {
            listOf(
                R.string.fx_color_green to Color.GREEN,
                R.string.fx_color_blue to Color.BLUE,
                R.string.fx_color_magenta to Color.MAGENTA,
            ).forEach { (label, color) ->
                FilterChip(
                    selected = state.chromaKey.keyColor == color,
                    onClick = {
                        onStateChange(state.copy(chromaKey = state.chromaKey.copy(keyColor = color)))
                    },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        FxSlider(
            label = stringResource(R.string.fx_tolerance),
            value = state.chromaKey.tolerance,
            range = 0f..0.75f,
            valueLabel = String.format(Locale.getDefault(), "%.2f", state.chromaKey.tolerance),
        ) {
            onStateChange(state.copy(chromaKey = state.chromaKey.copy(tolerance = it)))
        }
        FxSlider(
            label = stringResource(R.string.fx_edge_softness),
            value = state.chromaKey.softness,
            range = 0.01f..0.35f,
            valueLabel = String.format(Locale.getDefault(), "%.2f", state.chromaKey.softness),
        ) {
            onStateChange(state.copy(chromaKey = state.chromaKey.copy(softness = it)))
        }
    }

    HorizontalDivider()
    FxToggleRow(
        label = stringResource(R.string.fx_pip),
        checked = state.pictureInPicture.enabled,
        onCheckedChange = { enabled ->
            onStateChange(state.copy(pictureInPicture = state.pictureInPicture.copy(enabled = enabled)))
        },
    )
    if (state.pictureInPicture.enabled) {
        OutlinedButton(onClick = onPickPictureInPicture) { Text(stringResource(R.string.fx_choose_pip)) }
        FxSlider(stringResource(R.string.fx_horizontal), state.pictureInPicture.centerX, 0f..1f, percent(state.pictureInPicture.centerX)) {
            onStateChange(state.copy(pictureInPicture = state.pictureInPicture.copy(centerX = it)))
        }
        FxSlider(stringResource(R.string.fx_vertical), state.pictureInPicture.centerY, 0f..1f, percent(state.pictureInPicture.centerY)) {
            onStateChange(state.copy(pictureInPicture = state.pictureInPicture.copy(centerY = it)))
        }
        FxSlider(stringResource(R.string.fx_pip_size), state.pictureInPicture.widthFraction, 0.1f..0.75f, percent(state.pictureInPicture.widthFraction)) {
            onStateChange(state.copy(pictureInPicture = state.pictureInPicture.copy(widthFraction = it)))
        }
        FxSlider(stringResource(R.string.fx_pip_opacity), state.pictureInPicture.opacity, 0f..1f, percent(state.pictureInPicture.opacity)) {
            onStateChange(state.copy(pictureInPicture = state.pictureInPicture.copy(opacity = it)))
        }
    }

    HorizontalDivider()
    FxToggleRow(
        label = stringResource(R.string.fx_logo),
        checked = state.logo.enabled,
        onCheckedChange = { enabled ->
            onStateChange(state.copy(logo = state.logo.copy(enabled = enabled)))
        },
    )
    if (state.logo.enabled) {
        OutlinedButton(onClick = onPickLogo) { Text(stringResource(R.string.fx_choose_logo)) }
        FxSlider(stringResource(R.string.fx_horizontal), state.logo.centerX, 0f..1f, percent(state.logo.centerX)) {
            onStateChange(state.copy(logo = state.logo.copy(centerX = it)))
        }
        FxSlider(stringResource(R.string.fx_vertical), state.logo.centerY, 0f..1f, percent(state.logo.centerY)) {
            onStateChange(state.copy(logo = state.logo.copy(centerY = it)))
        }
        FxSlider(stringResource(R.string.fx_logo_size), state.logo.widthFraction, 0.05f..0.6f, percent(state.logo.widthFraction)) {
            onStateChange(state.copy(logo = state.logo.copy(widthFraction = it)))
        }
        FxSlider(stringResource(R.string.fx_logo_opacity), state.logo.opacity, 0f..1f, percent(state.logo.opacity)) {
            onStateChange(state.copy(logo = state.logo.copy(opacity = it)))
        }
    }

    HorizontalDivider()
    FxToggleRow(
        label = stringResource(R.string.fx_text),
        checked = state.text.enabled,
        onCheckedChange = { enabled ->
            onStateChange(state.copy(text = state.text.copy(enabled = enabled)))
        },
    )
    if (state.text.enabled) {
        OutlinedTextField(
            value = state.text.text,
            onValueChange = { onStateChange(state.copy(text = state.text.copy(text = it.take(160)))) },
            label = { Text(stringResource(R.string.fx_text_field)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Text(stringResource(R.string.fx_font), style = MaterialTheme.typography.labelLarge)
        FxFlow {
            TextOverlayFont.entries.forEach { font ->
                FilterChip(
                    selected = state.text.font == font,
                    onClick = {
                        if (font == TextOverlayFont.Custom) onPickCustomFont()
                        onStateChange(state.copy(text = state.text.copy(font = font)))
                    },
                    label = { Text(stringResource(fontLabel(font))) },
                )
            }
        }
        FxSlider(
            stringResource(R.string.fx_text_size),
            state.text.sizeSp,
            12f..120f,
            stringResource(R.string.fx_size_sp, state.text.sizeSp.roundToInt()),
        ) {
            onStateChange(state.copy(text = state.text.copy(sizeSp = it)))
        }
        FxSlider(stringResource(R.string.fx_horizontal), state.text.x, 0f..0.95f, percent(state.text.x)) {
            onStateChange(state.copy(text = state.text.copy(x = it)))
        }
        FxSlider(stringResource(R.string.fx_vertical), state.text.y, 0f..0.95f, percent(state.text.y)) {
            onStateChange(state.copy(text = state.text.copy(y = it)))
        }
        FxSlider(stringResource(R.string.fx_text_opacity), state.text.opacity, 0f..1f, percent(state.text.opacity)) {
            onStateChange(state.copy(text = state.text.copy(opacity = it)))
        }
        Text(stringResource(R.string.fx_animation), style = MaterialTheme.typography.labelLarge)
        FxFlow {
            TextOverlayAnimation.entries.forEach { animation ->
                FilterChip(
                    selected = state.text.animation == animation,
                    onClick = {
                        onStateChange(state.copy(text = state.text.copy(animation = animation)))
                    },
                    label = { Text(stringResource(animationLabel(animation))) },
                )
            }
        }
    }

    HorizontalDivider()
    FxHeading(stringResource(R.string.fx_presets), MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = presetName,
        onValueChange = { onPresetNameChange(it.take(64)) },
        label = { Text(stringResource(R.string.fx_preset_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    FxFlow {
        Button(enabled = presetName.isNotBlank(), onClick = onSavePreset) {
            Text(stringResource(R.string.fx_preset_save))
        }
        presetNames.forEach { name ->
            OutlinedButton(onClick = { onLoadPreset(name) }) {
                Text(stringResource(R.string.fx_preset_load, name))
            }
        }
    }

    HorizontalDivider()
    FxHeading(stringResource(R.string.fx_mjpeg_title), MaterialTheme.typography.titleSmall)
    FxToggleRow(
        label = stringResource(R.string.fx_mjpeg_serve),
        checked = state.output.enabled,
        onCheckedChange = { enabled ->
            onStateChange(state.copy(output = state.output.copy(enabled = enabled)))
        },
    )
    OutlinedTextField(
        value = outputPortText,
        onValueChange = onOutputPortTextChange,
        label = { Text(stringResource(R.string.fx_mjpeg_port)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = Modifier.width(180.dp),
    )
    Text(
        streamStatus,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (state.output.enabled) {
        Text(
            stringResource(R.string.fx_mjpeg_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    HorizontalDivider()
    FxHeading(stringResource(R.string.obs_title), MaterialTheme.typography.titleSmall)
    val obsBusy = obsState == ObsConnectionState.Connecting || obsState == ObsConnectionState.Authenticating
    val obsReady = obsState == ObsConnectionState.Ready
    OutlinedTextField(
        value = obsHost,
        onValueChange = onObsHostChange,
        label = { Text(stringResource(R.string.obs_host)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = obsPort,
        onValueChange = onObsPortChange,
        label = { Text(stringResource(R.string.obs_port)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = Modifier.width(140.dp),
    )
    FxToggleRow(
        label = stringResource(R.string.obs_secure),
        checked = obsSecure,
        onCheckedChange = onObsSecureChange,
    )
    OutlinedTextField(
        value = obsPassword,
        onValueChange = onObsPasswordChange,
        label = { Text(stringResource(R.string.obs_password)) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = obsScene,
        onValueChange = onObsSceneChange,
        label = { Text(stringResource(R.string.obs_scene)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        obsStatus,
        style = MaterialTheme.typography.bodySmall,
        color = if (obsState == ObsConnectionState.Error) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    FxFlow {
        if (obsReady || obsBusy) {
            OutlinedButton(onClick = onObsDisconnect) { Text(stringResource(R.string.obs_disconnect)) }
        } else {
            Button(enabled = obsHost.isNotBlank(), onClick = onObsConnect) {
                Text(stringResource(R.string.obs_connect))
            }
        }
        OutlinedButton(enabled = obsReady && obsScene.isNotBlank(), onClick = onObsSetScene) {
            Text(stringResource(R.string.obs_set_scene))
        }
        OutlinedButton(enabled = obsReady, onClick = onObsStartStream) { Text(stringResource(R.string.obs_start_stream)) }
        OutlinedButton(enabled = obsReady, onClick = onObsStopStream) { Text(stringResource(R.string.obs_stop_stream)) }
        OutlinedButton(enabled = obsReady, onClick = onObsStartVirtualCamera) { Text(stringResource(R.string.obs_start_vcam)) }
        OutlinedButton(enabled = obsReady, onClick = onObsStopVirtualCamera) { Text(stringResource(R.string.obs_stop_vcam)) }
    }
}

@Composable
private fun FxHeading(text: String, style: TextStyle) {
    Text(text, style = style, modifier = Modifier.semantics { heading() })
}

/** The whole row is the switch target (≥48dp) and TalkBack announces label and state as one control. */
@Composable
private fun FxToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun FxSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(valueLabel, style = MaterialTheme.typography.labelSmall)
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        modifier = Modifier.semantics {
            contentDescription = label
            stateDescription = valueLabel
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FxFlow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

private fun targetLabel(target: ChromaTarget): Int = when (target) {
    ChromaTarget.PictureInPicture -> R.string.fx_target_pip
    ChromaTarget.Logo -> R.string.fx_target_logo
    ChromaTarget.Both -> R.string.fx_target_both
}

private fun fontLabel(font: TextOverlayFont): Int = when (font) {
    TextOverlayFont.Sans -> R.string.fx_font_sans
    TextOverlayFont.Serif -> R.string.fx_font_serif
    TextOverlayFont.Monospace -> R.string.fx_font_monospace
    TextOverlayFont.Cursive -> R.string.fx_font_cursive
    TextOverlayFont.Custom -> R.string.fx_font_custom
}

private fun animationLabel(animation: TextOverlayAnimation): Int = when (animation) {
    TextOverlayAnimation.None -> R.string.fx_animation_none
    TextOverlayAnimation.Pulse -> R.string.fx_animation_pulse
    TextOverlayAnimation.Slide -> R.string.fx_animation_slide
}

private fun percent(value: Float): String =
    String.format(Locale.getDefault(), "%.0f%%", value * 100f)
