package com.example.core.voice

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
    val finalizedTranscript: String = "",
    val rmsDb: Float = 0f,
    val errorMessage: String? = null,
    val elapsedSeconds: Int = 0
)

class VoiceDictationController(context: Context) {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow(
        VoiceDictationState(isAvailable = SpeechRecognizer.isRecognitionAvailable(appContext))
    )
    val state: StateFlow<VoiceDictationState> = _state.asStateFlow()

    fun refreshAvailability() {
        _state.update {
            it.copy(isAvailable = SpeechRecognizer.isRecognitionAvailable(appContext))
        }
    }

    fun startListening(languageTag: String = Locale.getDefault().toLanguageTag()) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.update {
                it.copy(
                    isAvailable = false,
                    isListening = false,
                    errorMessage = "On-device or system Speech Recognition service is not installed on this device."
                )
            }
            return
        }

        stopListeningInternal()

        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = speechRecognizer
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.update {
                    it.copy(isListening = true, errorMessage = null, partialTranscript = "")
                }
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                _state.update { it.copy(rmsDb = normalized) }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.update { it.copy(isListening = false, rmsDb = 0f) }
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Speech engine unavailable offline; download an offline voice pack in system settings or type manually."
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Tap the mic to try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
                    else -> "Voice capture stopped (code $error)."
                }
                _state.update {
                    it.copy(
                        isListening = false,
                        rmsDb = 0f,
                        errorMessage = if (error == SpeechRecognizer.ERROR_CLIENT) null else message
                    )
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()
                _state.update { current ->
                    val combined = if (best.isBlank()) {
                        current.finalizedTranscript
                    } else if (current.finalizedTranscript.isBlank()) {
                        best
                    } else {
                        "${current.finalizedTranscript} $best"
                    }
                    current.copy(
                        isListening = false,
                        rmsDb = 0f,
                        partialTranscript = "",
                        finalizedTranscript = combined
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

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        _state.update {
            it.copy(
                isAvailable = true,
                isListening = true,
                errorMessage = null,
                partialTranscript = ""
            )
        }
        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
        _state.update { it.copy(isListening = false, rmsDb = 0f) }
    }

    fun clearSession() {
        stopListeningInternal()
        _state.value = VoiceDictationState(
            isAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        )
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
