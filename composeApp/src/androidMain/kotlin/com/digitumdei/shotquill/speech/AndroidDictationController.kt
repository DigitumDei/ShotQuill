package com.digitumdei.shotquill.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Dictation through the phone's own speech recogniser. The on-device
 * recogniser is used when the phone has one; otherwise the phone's default
 * speech service is used, which may process audio on its provider's servers.
 * ShotQuill itself sends nothing anywhere.
 *
 * Every method must be called on the main thread, as the platform requires.
 */
class AndroidDictationController(
    private val context: Context,
    private val requestMicrophone: (onGranted: () -> Unit, onDenied: () -> Unit) -> Unit,
) : DictationController {
    private val serviceAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override var isOnDevice: Boolean by mutableStateOf(
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context),
    )
        private set

    override val isAvailable: Boolean = isOnDevice || serviceAvailable

    override var state: DictationState by mutableStateOf(DictationState.Idle)
        private set

    private var recognizer: SpeechRecognizer? = null
    private var deliver: ((String) -> Unit)? = null
    private var keepListening = false

    // Recognisers normally end a session themselves after a few seconds of
    // silence. This is the backstop for the ones that do not, so the
    // microphone is never left open by accident.
    private val handler = Handler(Looper.getMainLooper())
    private val silenceTimeout = Runnable { stop() }

    override fun start(onText: (String) -> Unit) {
        if (!isAvailable) {
            state = DictationState.Failed("This phone has no speech recognition service.")
            return
        }
        if (state is DictationState.Listening) return
        deliver = onText
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            beginListening()
        } else {
            requestMicrophone(
                { beginListening() },
                { state = DictationState.Failed("Dictation needs the microphone. Allow it and try again.") },
            )
        }
    }

    override fun stop() {
        handler.removeCallbacks(silenceTimeout)
        if (!keepListening) return
        keepListening = false
        // stopListening lets the recogniser finish the phrase it is in the
        // middle of, so the last words still arrive through onResults.
        recognizer?.stopListening()
        state = DictationState.Idle
    }

    /** Frees the recogniser. Call when the screen that owns this goes away for good. */
    fun release() {
        handler.removeCallbacks(silenceTimeout)
        keepListening = false
        recognizer?.destroy()
        recognizer = null
        deliver = null
        state = DictationState.Idle
    }

    private fun beginListening() {
        keepListening = true
        state = DictationState.Listening()
        listen()
    }

    private fun listen() {
        restartSilenceTimeout()
        val active = recognizer ?: createRecognizer().also { recognizer = it }
        active.startListening(recognitionIntent())
    }

    private fun restartSilenceTimeout() {
        handler.removeCallbacks(silenceTimeout)
        handler.postDelayed(silenceTimeout, SilenceTimeoutMillis)
    }

    private fun createRecognizer(): SpeechRecognizer {
        val created = if (isOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        created.setRecognitionListener(listener)
        return created
    }

    private fun recognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Asks for punctuation and capitals where the recogniser can supply them.
                putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
            }
        }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            val heard = partialResults.firstResult() ?: return
            if (heard.isNotBlank()) restartSilenceTimeout()
            if (state is DictationState.Listening) state = DictationState.Listening(heard)
        }

        override fun onResults(results: Bundle?) {
            results.firstResult()?.takeIf { it.isNotBlank() }?.let { deliver?.invoke(it) }
            if (keepListening) {
                state = DictationState.Listening()
                listen()
            } else {
                state = DictationState.Idle
            }
        }

        override fun onError(error: Int) {
            if (!keepListening) {
                state = DictationState.Idle
                return
            }
            if (isOnDevice && error in LanguageErrors && serviceAvailable) {
                // The on-device model for this language is not installed: use
                // the phone's default speech service for the rest of the session.
                recognizer?.destroy()
                recognizer = null
                isOnDevice = false
                listen()
                return
            }
            keepListening = false
            handler.removeCallbacks(silenceTimeout)
            state = when (error) {
                // Silence simply ends the session.
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_CLIENT,
                -> DictationState.Idle

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    DictationState.Failed("Dictation needs the microphone. Allow it and try again.")

                SpeechRecognizer.ERROR_AUDIO ->
                    DictationState.Failed("The microphone could not be used.")

                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER,
                -> DictationState.Failed("The phone's speech service could not be reached.")

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    DictationState.Failed("Speech recognition is busy. Try again in a moment.")

                in LanguageErrors ->
                    DictationState.Failed("Speech recognition for your language is not installed on this phone.")

                else -> DictationState.Failed("Dictation stopped unexpectedly (code $error).")
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private companion object {
        // ERROR_LANGUAGE_NOT_SUPPORTED and ERROR_LANGUAGE_UNAVAILABLE, named only from API 31.
        val LanguageErrors = setOf(12, 13)
        const val SilenceTimeoutMillis = 15_000L
    }
}

/** Creates the dictation controller and wires it to the microphone permission prompt. */
@Composable
fun rememberDictationController(context: Context): DictationController {
    var pending by remember { mutableStateOf<Pair<() -> Unit, () -> Unit>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val callbacks = pending
        pending = null
        if (granted) callbacks?.first?.invoke() else callbacks?.second?.invoke()
    }
    val controller = remember(context) {
        AndroidDictationController(context) { onGranted, onDenied ->
            pending = onGranted to onDenied
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    return controller
}
