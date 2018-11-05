package com.simplemobiletools.gallery.pro.helpers

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder

class PicassoRegionDecoder : ImageRegionDecoder {
    private var decoder: BitmapRegionDecoder? = null
    private val decoderLock = Any()

    override fun init(context: Context, uri: Uri): Point {
        val newUri = Uri.parse(uri.toString().replace("%", "%25").replace("#", "%23"))
        val inputStream = context.contentResolver.openInputStream(newUri)
        decoder = BitmapRegionDecoder.newInstance(inputStream, false)
        return Point(decoder!!.width, decoder!!.height)
    }

    override fun decodeRegion(rect: Rect, sampleSize: Int): Bitmap {
        synchronized(decoderLock) {
            val options = BitmapFactory.Options()
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.RGB_565
            val bitmap = decoder!!.decodeRegion(rect, options)
            return bitmap ?: throw RuntimeException("Region decoder returned null bitmap - image format may not be supported")
        }
    }

    override fun isReady() = decoder != null && !decoder!!.isRecycled

    override fun recycle() {
        decoder!!.recycle()
    }
}
