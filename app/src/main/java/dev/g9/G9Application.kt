package dev.g9

import android.app.Application
import life.furrer.nine.core.KeyHelp
import life.furrer.nine.core.Nine
import life.furrer.nine.core.NineApp
import life.furrer.nine.core.NineSurface

class G9Application : Application() {
    override fun onCreate() {
        super.onCreate()
        Nine.init(g9App)
    }
}

private val g9App = NineApp(
    id = "g9",
    name = "G9",
    tagline = "Gallery on 9 keys",
    settingsActivity = SettingsActivity::class.java,
    // Placeholders until G9 has a home of its own, as TG9's.
    websiteUrl = "https://github.com/",
    sourceUrl = "https://github.com/",
    trackerUrl = "https://github.com/",
    surfaces = listOf(
        NineSurface(
            id = "",
            name = "G9",
            introText = """
                G9 ("Gallery on 9 keys") shows the photos and videos on a keypad phone with no
                touchscreen, in the look and with the keys of the Nine apps: one column on black,
                a numbered "*" menu, and a hint bar at the bottom.

                It opens on everything, newest first, under a header for each day. A thumbnail,
                the time, and the folder, size and dimensions are on every row. "0" lists the
                folders (Camera, Screenshots, downloads, messengers...) and the trash.

                OK opens a photo or video full screen; Up and Down step through the rest from
                there. Photos zoom with OK and pan with the D-pad; videos pause with OK and seek
                with Left and Right.

                "#" marks photos and videos to share or move to the trash together. Nothing is
                deleted outright: the trash keeps things for 30 days, and Android asks each time.
                G9 also opens photos and videos other apps hand over.
            """.trimIndent(),
            keyMap = listOf(
                KeyHelp("— List —", ""),
                KeyHelp("Up / Down", "Move the selection."),
                KeyHelp("Left / Right", "Jump to the previous/next day."),
                KeyHelp("OK", "Open full screen."),
                KeyHelp("#", "Mark to share or move to the trash together."),
                KeyHelp("0", "Folders and the trash."),
                KeyHelp("*", "Open the menu: share, move to trash, mark the day, folders."),
                KeyHelp("Hold *", "Open Settings."),
                KeyHelp("Back", "Clear the marks; with none, go back."),
                KeyHelp("— Full screen —", ""),
                KeyHelp("Up / Down", "Previous/next photo or video."),
                KeyHelp("OK", "Photo: zoom in/out. Video: pause/play."),
                KeyHelp("Left / Right", "Video: back/forward 10 s. Photo: previous/next."),
                KeyHelp("D-pad, zoomed in", "Pan."),
                KeyHelp("#", "Show or hide the details: name, date, size, folder."),
                KeyHelp("*", "Open the menu: share, move to trash, details, settings."),
                KeyHelp("Back", "Back to the list."),
            ),
        ),
    ),
)
