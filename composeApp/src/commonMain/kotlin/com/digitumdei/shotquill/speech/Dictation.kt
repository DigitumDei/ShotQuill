package com.digitumdei.shotquill.speech

sealed interface DictationState {
    data object Idle : DictationState

    /** The microphone is open. [heard] is what has been recognised so far in the current phrase. */
    data class Listening(val heard: String = "") : DictationState

    data class Failed(val message: String) : DictationState
}

/**
 * Turns speech into text for the caption. This is transcription only: the
 * words that come back are the ones that were spoken.
 */
interface DictationController {
    /** False when the device has no speech recognition service at all. */
    val isAvailable: Boolean

    /** True when recognition runs on the device itself, so no audio leaves it. */
    val isOnDevice: Boolean

    val state: DictationState

    /**
     * Opens the microphone and keeps listening phrase after phrase until
     * [stop] is called or there is a silence. [onText] is called once per
     * finished phrase.
     */
    fun start(onText: (String) -> Unit)

    /** Closes the microphone. Anything already heard is still delivered. */
    fun stop()
}
