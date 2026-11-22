package dev.g9

import io.github.ninetailsflip.update.updatesSection
import life.furrer.nine.core.NineSurface
import life.furrer.nine.core.settings.NineSettingsActivity
import life.furrer.nine.core.settings.SettingsScreen

/** G9's Settings; Theme, Backup and About come from :core like in every Nine app. */
class SettingsActivity : NineSettingsActivity() {
    override fun buildAppSections(screen: SettingsScreen, surface: NineSurface) {
        screen.section("Videos") {
            toggle(
                label = "Play on open",
                get = { Prefs.autoplay(this@SettingsActivity) },
                set = { Prefs.setAutoplay(this@SettingsActivity, it) },
                on = "on (OK pauses)",
                off = "off (OK plays)",
            )
        }
        updatesSection(screen, this, ::refresh)
    }
}
