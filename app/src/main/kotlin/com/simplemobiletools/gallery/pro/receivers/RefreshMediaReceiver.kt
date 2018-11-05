package com.simplemobiletools.gallery.pro.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.REFRESH_PATH
import com.simplemobiletools.gallery.pro.extensions.galleryDB
import com.simplemobiletools.gallery.pro.helpers.*
import com.simplemobiletools.gallery.pro.models.Medium
import java.io.File

class RefreshMediaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.getStringExtra(REFRESH_PATH) ?: return

        Thread {
            val medium = Medium(null, path.getFilenameFromPath(), path, path.getParentPath(), System.currentTimeMillis(), System.currentTimeMillis(),
                    File(path).length(), getFileType(path), false, 0L)
            context.galleryDB.MediumDao().insert(medium)
        }.start()
    }

    private fun getFileType(path: String) = when {
        path.isVideoFast() -> TYPE_VIDEOS
        path.isGif() -> TYPE_GIFS
        path.isRawFast() -> TYPE_RAWS
        path.isSvg() -> TYPE_SVGS
        else -> TYPE_IMAGES
    }
}
