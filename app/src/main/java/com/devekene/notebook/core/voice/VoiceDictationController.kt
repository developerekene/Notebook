package com.devekene.notebook.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class VoiceDictationState(
    val isAvailable: Boolean = false,
    val isListening: Boolean = false,
    val partialTranscript: String = "",
    val latestChunk: String = "",
    val latestChunkId: Long = 0L,
    val latestDurationSec: Int = 0,
    val rmsDb: Float = 0f,
    val errorMessage: String? = null
)

class VoiceDictationController(context: Context) {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var sessionStartMs: Long = 0L

    private val _state = MutableStateFlow(
        VoiceDictationState(isAvailable = SpeechRecognizer.isRecognitionAvailable(appContext))
    )
    val state: StateFlow<VoiceDictationState> = _state.asStateFlow()

    fun refreshAvailability(): Boolean {
        val available = SpeechRecognizer.isRecognitionAvailable(appContext)
        _state.update { it.copy(isAvailable = available) }
        return available
    }

    fun buildRecognizerIntent(languageTag: String = Locale.getDefault().toLanguageTag()): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to dictate into your note")
        }
    }

    fun startListening(languageTag: String = Locale.getDefault().toLanguageTag()): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.update {
                it.copy(
                    isAvailable = false,
                    isListening = false,
                    errorMessage = "System Speech Recognition service is not available on this device."
                )
            }
            return false
        }

        stopListeningInternal()

        val speechRecognizer = try {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isListening = false,
                    errorMessage = e.localizedMessage ?: "Unable to initialize speech recognizer."
                )
            }
            return false
        }

        recognizer = speechRecognizer
        sessionStartMs = System.currentTimeMillis()

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.update {
                    it.copy(isListening = true, errorMessage = null, partialTranscript = "")
                }
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1f)
                _state.update { it.copy(rmsDb = normalized) }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.update { it.copy(isListening = false, rmsDb = 0f) }
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio capture error. Check microphone."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Speech recognition network timeout. Check connectivity or offline speech pack."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap the mic and speak clearly."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech engine is busy. Try again."
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Selected speech language is unavailable."
                    else -> null
                }
                _state.update {
                    it.copy(
                        isListening = false,
                        rmsDb = 0f,
                        partialTranscript = "",
                        errorMessage = message
                    )
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()
                val duration = ((System.currentTimeMillis() - sessionStartMs) / 1000L)
                    .toInt()
                    .coerceAtLeast(1)
                _state.update { current ->
                    current.copy(
                        isListening = false,
                        rmsDb = 0f,
                        partialTranscript = "",
                        latestChunk = best,
                        latestChunkId = if (best.isNotBlank()) current.latestChunkId + 1L else current.latestChunkId,
                        latestDurationSec = if (best.isNotBlank()) duration else 0,
                        errorMessage = null
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotBlank()) {
                    _state.update { it.copy(partialTranscript = partial) }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        _state.update {
            it.copy(
                isAvailable = true,
                isListening = true,
                errorMessage = null,
                partialTranscript = ""
            )
        }
        return try {
            speechRecognizer.startListening(buildRecognizerIntent(languageTag))
            true
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isListening = false,
                    errorMessage = e.localizedMessage ?: "Failed to start voice dictation."
                )
            }
            false
        }
    }

    fun stopListening() {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
        _state.update { it.copy(isListening = false, rmsDb = 0f) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun stopListeningInternal() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            recognizer = null
        }
    }

    fun release() {
        stopListeningInternal()
    }
}
