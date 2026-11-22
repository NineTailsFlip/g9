package dev.g9

import android.content.Context
import life.furrer.nine.core.prefs.NinePrefs

/** G9's own settings, in the same prefs file as core's so Settings' Backup exports them together. */
object Prefs {
    private const val KEY_AUTOPLAY = "autoplay_videos"

    /** On by default: opening a video is asking to watch it. */
    fun autoplay(context: Context): Boolean = NinePrefs.of(context).getBoolean(KEY_AUTOPLAY, true)

    fun setAutoplay(context: Context, value: Boolean) {
        NinePrefs.of(context).edit().putBoolean(KEY_AUTOPLAY, value).apply()
    }
}
