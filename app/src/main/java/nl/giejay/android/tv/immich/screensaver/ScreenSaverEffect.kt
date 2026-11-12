package nl.giejay.android.tv.immich.screensaver

import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.EnumWithTitle

enum class ScreenSaverEffect : EnumWithTitle {
    SLIDESHOW {
        override fun getTitle(): String {
            return ImmichApplication.appContext!!.getString(R.string.slideshow)
        }
    },
    PHOTO_TABLE {
        override fun getTitle(): String {
            return ImmichApplication.appContext!!.getString(R.string.photo_table)
        }
    }
}
