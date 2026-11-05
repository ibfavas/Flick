package com.ibfavas.flick.tile

import android.content.Intent
import android.service.quicksettings.TileService
import com.ibfavas.flick.DrawActivity

/** Quick Settings tile: tapping it opens the transparent gesture pad. */
class FlickTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, DrawActivity::class.java).apply {
            // EXCLUDE_FROM_RECENTS (v1.2.1): the pad must not
            // appear in the recents list.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        startActivityAndCollapse(intent)
    }
}
