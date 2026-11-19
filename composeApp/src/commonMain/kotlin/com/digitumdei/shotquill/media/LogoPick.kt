package com.digitumdei.shotquill.media

/**
 * What came back from the phone's file chooser when a logo was asked for. It
 * is handed to the app as plain state rather than through a callback, because
 * Android may restart the app while the chooser is open, and a callback from
 * before the restart is gone by the time the file comes back.
 */
sealed interface LogoPick {
    /** A logo was chosen and copied in. [caution] is anything about it worth fixing at the source. */
    data class Picked(val uri: String, val caution: String? = null) : LogoPick

    /** Choosing a logo failed; [message] says why. */
    data class Failed(val message: String) : LogoPick
}
