package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.voice.VoiceDictationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDictationBottomSheet(
    state: VoiceDictationState,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onInsertTranscript: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = {
            onStopListening()
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Voice-to-Text Dictation",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Animated audio level bars
            Row(
                modifier = Modifier.height(52.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val weights = listOf(0.45f, 0.7f, 1.0f, 0.85f, 1.0f, 0.65f, 0.45f)
                weights.forEachIndexed { index, factor ->
                    val targetHeight = if (state.isListening) {
                        val wave = (state.rmsDb * factor).coerceIn(0.18f, 1f)
                        14f + 36f * wave
                    } else {
                        10f + (index % 3) * 4f
                    }
                    val animatedHeight by animateFloatAsState(
                        targetValue = targetHeight,
                        animationSpec = tween(durationMillis = 120),
                        label = "rms_bar_$index"
                    )
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(animatedHeight.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isListening) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                }
            }

            val previewText = buildString {
                if (state.finalizedTranscript.isNotBlank()) append(state.finalizedTranscript)
                if (state.partialTranscript.isNotBlank()) {
                    if (isNotEmpty()) append(" ")
                    append(state.partialTranscript)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = when {
                        !hasAudioPermission -> "Microphone permission is required for hands-free voice dictation."
                        state.errorMessage != null && previewText.isBlank() -> state.errorMessage
                        previewText.isNotBlank() -> previewText
                        state.isListening -> "Listening… speak naturally to transcribe into your note."
                        else -> "Tap Start Listening to dictate hands-free."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (previewText.isNotBlank()) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (state.errorMessage != null && previewText.isNotBlank()) {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!hasAudioPermission) {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_audio_permission_btn")
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Grant Microphone Access")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.isListening) {
                        FilledTonalButton(
                            onClick = onStopListening,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stop_dictation_btn")
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onStartListening,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("start_dictation_btn")
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (previewText.isBlank()) "Start Listening" else "Dictate More")
                        }
                    }

                    Button(
                        onClick = {
                            onStopListening()
                            if (previewText.isNotBlank()) {
                                onInsertTranscript(previewText)
                            }
                            onDismiss()
                        },
                        enabled = previewText.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("insert_transcript_btn")
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Insert Text")
                    }
                }
            }
        }
    }
}
