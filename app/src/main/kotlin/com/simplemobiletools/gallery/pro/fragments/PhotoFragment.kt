package com.simplemobiletools.gallery.pro.fragments

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.PictureDrawable
import android.media.ExifInterface.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.OTG_PATH
import com.simplemobiletools.gallery.pro.R
import com.simplemobiletools.gallery.pro.activities.PanoramaPhotoActivity
import com.simplemobiletools.gallery.pro.activities.PhotoActivity
import com.simplemobiletools.gallery.pro.activities.ViewPagerActivity
import com.simplemobiletools.gallery.pro.extensions.*
import com.simplemobiletools.gallery.pro.helpers.MEDIUM
import com.simplemobiletools.gallery.pro.helpers.PATH
import com.simplemobiletools.gallery.pro.helpers.PicassoDecoder
import com.simplemobiletools.gallery.pro.helpers.PicassoRegionDecoder
import com.simplemobiletools.gallery.pro.models.Medium
import com.simplemobiletools.gallery.pro.svg.SvgSoftwareLayerSetter
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import it.sephiroth.android.library.exif2.ExifInterface
import kotlinx.android.synthetic.main.pager_photo_item.view.*
import org.apache.sanselan.common.byteSources.ByteSourceInputStream
import org.apache.sanselan.formats.jpeg.JpegImageParser
import pl.droidsonroids.gif.InputSource
import java.io.File
import java.io.FileOutputStream
import java.util.*

class PhotoFragment : ViewPagerFragment() {
    private val DEFAULT_DOUBLE_TAP_ZOOM = 2f
    private val ZOOMABLE_VIEW_LOAD_DELAY = 300L

    // devices with good displays, but the rest of the hardware not good enough for them
    private val WEIRD_DEVICES = arrayListOf(
            "motorola xt1685",
            "google nexus 5x"
    )

    private var isFragmentVisible = false
    private var isFullscreen = false
    private var wasInit = false
    private var isPanorama = false
    private var isSubsamplingVisible = false    // checking view.visibility is unreliable, use an extra variable for it
    private var imageOrientation = -1
    private var loadZoomableViewHandler = Handler()

    private var storedShowExtendedDetails = false
    private var storedHideExtendedDetails = false
    private var storedAllowDeepZoomableImages = false
    private var storedShowHighestQuality = false
    private var storedAllowOneFingerZoom = false
    private var mOriginalSubsamplingScale = 0f
    private var storedExtendedDetails = 0

    lateinit var view: ViewGroup
    lateinit var medium: Medium

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        view = (inflater.inflate(R.layout.pager_photo_item, container, false) as ViewGroup).apply {
            subsampling_view.setOnClickListener { photoClicked() }
            photo_view.setOnClickListener { photoClicked() }
            gif_view.setOnClickListener { photoClicked() }
            instant_prev_item.setOnClickListener { listener?.goToPrevItem() }
            instant_next_item.setOnClickListener { listener?.goToNextItem() }
            panorama_outline.setOnClickListener { openPanorama() }

            instant_prev_item.parentView = container
            instant_next_item.parentView = container

            photo_brightness_controller.initialize(activity!!, slide_info, true, container) { x, y ->
                view.apply {
                    if (subsampling_view.isVisible()) {
                        subsampling_view.sendFakeClick(x, y)
                    } else {
                        photo_view.sendFakeClick(x, y)
                    }
                }
            }

            if (context.config.allowDownGesture) {
                gif_view.setOnTouchListener { v, event ->
                    handleEvent(event)
                    false
                }

                subsampling_view.setOnTouchListener { v, event ->
                    if (view.subsampling_view.scale == mOriginalSubsamplingScale) {
                        handleEvent(event)
                    }
                    false
                }
            }
        }

        if (ViewPagerActivity.screenWidth == 0 || ViewPagerActivity.screenHeight == 0) {
            measureScreen()
        }

        storeStateVariables()
        if (!isFragmentVisible && activity is PhotoActivity) {
            isFragmentVisible = true
        }

        medium = arguments!!.getSerializable(MEDIUM) as Medium
        if (medium.path.startsWith("content://") && !medium.path.startsWith("content://mms/")) {
            val originalPath = medium.path
            medium.path = context!!.getRealPathFromURI(Uri.parse(originalPath)) ?: medium.path

            if (medium.path.isEmpty()) {
                var out: FileOutputStream? = null
                try {
                    var inputStream = context!!.contentResolver.openInputStream(Uri.parse(originalPath))
                    val exif = ExifInterface()
                    exif.readExif(inputStream, ExifInterface.Options.OPTION_ALL)
                    val tag = exif.getTag(ExifInterface.TAG_ORIENTATION)
                    val orientation = tag?.getValueAsInt(-1) ?: -1
                    inputStream = context!!.contentResolver.openInputStream(Uri.parse(originalPath))
                    val original = BitmapFactory.decodeStream(inputStream)
                    val rotated = rotateViaMatrix(original, orientation)
                    exif.setTagValue(ExifInterface.TAG_ORIENTATION, 1)
                    exif.removeCompressedThumbnail()

                    val file = File(context!!.externalCacheDir, Uri.parse(originalPath).lastPathSegment)
                    out = FileOutputStream(file)
                    rotated.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    medium.path = file.absolutePath
                } catch (e: Exception) {
                    activity!!.toast(R.string.unknown_error_occurred)
                    return view
                } finally {
                    out?.close()
                }
            }
        }

        isFullscreen = activity!!.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN == View.SYSTEM_UI_FLAG_FULLSCREEN
        loadImage()
        initExtendedDetails()
        wasInit = true
        checkIfPanorama()
        updateInstantSwitchWidths()

        return view
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onResume() {
        super.onResume()
        val config = context!!.config
        if (wasInit && (config.showExtendedDetails != storedShowExtendedDetails || config.extendedDetails != storedExtendedDetails)) {
            initExtendedDetails()
        }

        if (wasInit) {
            if (config.allowZoomingImages != storedAllowDeepZoomableImages || config.showHighestQuality != storedShowHighestQuality ||
                    config.oneFingerZoom != storedAllowOneFingerZoom) {
                isSubsamplingVisible = false
                view.subsampling_view.beGone()
                loadImage()
            } else if (medium.isGIF()) {
                loadGif()
            }
        }

        val allowPhotoGestures = config.allowPhotoGestures
        val allowInstantChange = config.allowInstantChange

        view.apply {
            photo_brightness_controller.beVisibleIf(allowPhotoGestures)
            instant_prev_item.beVisibleIf(allowInstantChange)
            instant_next_item.beVisibleIf(allowInstantChange)
            photo_view.setAllowFingerDragZoom(config.oneFingerZoom)
        }

        storeStateVariables()
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        isFragmentVisible = menuVisible
        if (wasInit) {
            if (!medium.isGIF()) {
                photoFragmentVisibilityChanged(menuVisible)
            }
        }
    }

    private fun storeStateVariables() {
        context!!.config.apply {
            storedShowExtendedDetails = showExtendedDetails
            storedHideExtendedDetails = hideExtendedDetails
            storedAllowDeepZoomableImages = allowZoomingImages
            storedShowHighestQuality = showHighestQuality
            storedAllowOneFingerZoom = oneFingerZoom
            storedExtendedDetails = extendedDetails
        }
    }

    private fun measureScreen() {
        val metrics = DisplayMetrics()
        activity!!.windowManager.defaultDisplay.getRealMetrics(metrics)
        ViewPagerActivity.screenWidth = metrics.widthPixels
        ViewPagerActivity.screenHeight = metrics.heightPixels
    }

    private fun photoFragmentVisibilityChanged(isVisible: Boolean) {
        if (isVisible) {
            scheduleZoomableView()
        } else {
            hideZoomableView()
        }
    }

    private fun degreesForRotation(orientation: Int) = when (orientation) {
        ORIENTATION_ROTATE_270 -> 270
        ORIENTATION_ROTATE_180 -> 180
        ORIENTATION_ROTATE_90 -> 90
        else -> 0
    }

    private fun rotateViaMatrix(original: Bitmap, orientation: Int): Bitmap {
        val degrees = degreesForRotation(orientation).toFloat()
        return if (degrees == 0f) {
            original
        } else {
            val matrix = Matrix()
            matrix.setRotate(degrees)
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        }
    }

    private fun loadImage() {
        imageOrientation = getImageOrientation()
        when {
            medium.isGIF() -> loadGif()
            medium.isSVG() -> loadSVG()
            else -> loadBitmap()
        }
    }

    private fun loadGif() {
        try {
            val pathToLoad = getPathToLoad(medium)
            val source = if (pathToLoad.startsWith("content://") || pathToLoad.startsWith("file://")) {
                InputSource.UriSource(context!!.contentResolver, Uri.parse(pathToLoad))
            } else {
                InputSource.FileSource(pathToLoad)
            }

            view.photo_view.beGone()
            view.gif_view.beVisible()
            view.gif_view.setInputSource(source)
        } catch (e: Exception) {
            loadBitmap()
        } catch (e: OutOfMemoryError) {
            loadBitmap()
        }
    }

    private fun loadSVG() {
        Glide.with(context!!)
                .`as`(PictureDrawable::class.java)
                .listener(SvgSoftwareLayerSetter())
                .load(medium.path)
                .into(view.photo_view)
    }

    private fun loadBitmap(degrees: Int = 0) {
        var pathToLoad = if (medium.path.startsWith("content://")) medium.path else "file://${medium.path}"
        pathToLoad = pathToLoad.replace("%", "%25").replace("#", "%23")

        try {
            val picasso = Picasso.get()
                    .load(pathToLoad)
                    .centerInside()
                    .resize(ViewPagerActivity.screenWidth, ViewPagerActivity.screenHeight)

            if (degrees != 0) {
                picasso.rotate(degrees.toFloat())
            }

            picasso.into(view.photo_view, object : Callback {
                override fun onSuccess() {
                    view.photo_view.isZoomable = degrees != 0 || context?.config?.allowZoomingImages == false
                    if (isFragmentVisible && degrees == 0) {
                        scheduleZoomableView()
                    }
                }

                override fun onError(e: Exception) {
                    if (context != null) {
                        tryLoadingWithGlide()
                    }
                }
            })
        } catch (ignored: Exception) {
        }
    }

    private fun tryLoadingWithGlide() {
        var targetWidth = if (ViewPagerActivity.screenWidth == 0) com.bumptech.glide.request.target.Target.SIZE_ORIGINAL else ViewPagerActivity.screenWidth
        var targetHeight = if (ViewPagerActivity.screenHeight == 0) com.bumptech.glide.request.target.Target.SIZE_ORIGINAL else ViewPagerActivity.screenHeight

        if (imageOrientation == ORIENTATION_ROTATE_90) {
            targetWidth = targetHeight
            targetHeight = com.bumptech.glide.request.target.Target.SIZE_ORIGINAL
        }

        val options = RequestOptions()
                .signature(medium.path.getFileSignature())
                .format(DecodeFormat.PREFER_ARGB_8888)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .override(targetWidth, targetHeight)

        Glide.with(context!!)
                .asBitmap()
                .load(getPathToLoad(medium))
                .apply(options)
                .listener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: com.bumptech.glide.request.target.Target<Bitmap>?, isFirstResource: Boolean) = false

                    override fun onResourceReady(resource: Bitmap?, model: Any?, target: com.bumptech.glide.request.target.Target<Bitmap>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                        if (isFragmentVisible) {
                            scheduleZoomableView()
                        }
                        return false
                    }
                }).into(view.photo_view)
    }

    private fun openPanorama() {
        Intent(context, PanoramaPhotoActivity::class.java).apply {
            putExtra(PATH, medium.path)
            startActivity(this)
        }
    }

    private fun scheduleZoomableView() {
        loadZoomableViewHandler.removeCallbacksAndMessages(null)
        loadZoomableViewHandler.postDelayed({
            if (isFragmentVisible && context?.config?.allowZoomingImages == true && medium.isImage() && !isSubsamplingVisible) {
                addZoomableView()
            }
        }, ZOOMABLE_VIEW_LOAD_DELAY)
    }

    private fun addZoomableView() {
        val rotation = degreesForRotation(imageOrientation)
        val path = getPathToLoad(medium)
        isSubsamplingVisible = true

        view.subsampling_view.apply {
            setMaxTileSize(if (context!!.config.showHighestQuality) Integer.MAX_VALUE else 4096)
            setMinimumTileDpi(if (context!!.config.showHighestQuality) -1 else getMinTileDpi())
            background = ColorDrawable(Color.TRANSPARENT)
            setBitmapDecoderFactory { PicassoDecoder(path, Picasso.get(), rotation) }
            setRegionDecoderFactory { PicassoRegionDecoder() }
            maxScale = 10f
            beVisible()
            isQuickScaleEnabled = context.config.oneFingerZoom
            setResetScaleOnSizeChange(false)
            setImage(ImageSource.uri(path))
            orientation = rotation
            setEagerLoadingEnabled(false)
            setOnImageEventListener(object : SubsamplingScaleImageView.OnImageEventListener {
                override fun onImageLoaded() {
                }

                override fun onReady() {
                    background = ColorDrawable(if (context.config.blackBackground) Color.BLACK else context.config.backgroundColor)
                    val useWidth = if (imageOrientation == ORIENTATION_ROTATE_90 || imageOrientation == ORIENTATION_ROTATE_270) sHeight else sWidth
                    val useHeight = if (imageOrientation == ORIENTATION_ROTATE_90 || imageOrientation == ORIENTATION_ROTATE_270) sWidth else sHeight
                    setDoubleTapZoomScale(getDoubleTapZoomScale(useWidth, useHeight))
                    mOriginalSubsamplingScale = scale
                }

                override fun onTileLoadError(e: Exception?) {
                }

                override fun onPreviewReleased() {
                }

                override fun onImageLoadError(e: Exception) {
                    view.photo_view.isZoomable = true
                    background = ColorDrawable(Color.TRANSPARENT)
                    isSubsamplingVisible = false
                    beGone()
                }

                override fun onPreviewLoadError(e: Exception?) {
                    background = ColorDrawable(Color.TRANSPARENT)
                    isSubsamplingVisible = false
                    beGone()
                }
            })
        }
    }

    private fun getMinTileDpi(): Int {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        val device = "${Build.BRAND} ${Build.MODEL}".toLowerCase()
        return when {
            WEIRD_DEVICES.contains(device) -> 240
            averageDpi > 400 -> 280
            averageDpi > 300 -> 220
            else -> 160
        }
    }

    private fun checkIfPanorama() {
        isPanorama = try {
            val inputStream = if (medium.path.startsWith("content:/")) context!!.contentResolver.openInputStream(Uri.parse(medium.path)) else File(medium.path).inputStream()
            val imageParser = JpegImageParser().getXmpXml(ByteSourceInputStream(inputStream, medium.name), HashMap<String, Any>())
            imageParser.contains("GPano:UsePanoramaViewer=\"True\"", true) || imageParser.contains("<GPano:UsePanoramaViewer>True</GPano:UsePanoramaViewer>", true)
        } catch (e: Exception) {
            false
        } catch (e: OutOfMemoryError) {
            false
        }

        view.panorama_outline.beVisibleIf(isPanorama)
    }

    private fun getImageOrientation(): Int {
        val defaultOrientation = -1
        var orient = defaultOrientation

        try {
            val pathToLoad = getPathToLoad(medium)
            val exif = android.media.ExifInterface(pathToLoad)
            orient = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, defaultOrientation)

            if (orient == defaultOrientation || medium.path.startsWith(OTG_PATH)) {
                val uri = if (pathToLoad.startsWith("content:/")) Uri.parse(pathToLoad) else Uri.fromFile(File(pathToLoad))
                val inputStream = context!!.contentResolver.openInputStream(uri)
                val exif2 = ExifInterface()
                exif2.readExif(inputStream, ExifInterface.Options.OPTION_ALL)
                orient = exif2.getTag(ExifInterface.TAG_ORIENTATION)?.getValueAsInt(defaultOrientation) ?: defaultOrientation
            }
        } catch (ignored: Exception) {
        } catch (ignored: OutOfMemoryError) {
        }
        return orient
    }

    private fun getDoubleTapZoomScale(width: Int, height: Int): Float {
        val bitmapAspectRatio = height / width.toFloat()
        val screenAspectRatio = ViewPagerActivity.screenHeight / ViewPagerActivity.screenWidth.toFloat()

        return if (context == null || bitmapAspectRatio == screenAspectRatio) {
            DEFAULT_DOUBLE_TAP_ZOOM
        } else if (context!!.portrait && bitmapAspectRatio <= screenAspectRatio) {
            ViewPagerActivity.screenHeight / height.toFloat()
        } else if (context!!.portrait && bitmapAspectRatio > screenAspectRatio) {
            ViewPagerActivity.screenWidth / width.toFloat()
        } else if (!context!!.portrait && bitmapAspectRatio >= screenAspectRatio) {
            ViewPagerActivity.screenWidth / width.toFloat()
        } else if (!context!!.portrait && bitmapAspectRatio < screenAspectRatio) {
            ViewPagerActivity.screenHeight / height.toFloat()
        } else {
            DEFAULT_DOUBLE_TAP_ZOOM
        }
    }

    fun rotateImageViewBy(degrees: Int) {
        loadZoomableViewHandler.removeCallbacksAndMessages(null)
        view.subsampling_view.beGone()
        isSubsamplingVisible = false
        loadBitmap(degrees)
    }

    private fun initExtendedDetails() {
        if (context!!.config.showExtendedDetails) {
            view.photo_details.apply {
                beInvisible()   // make it invisible so we can measure it, but not show yet
                text = getMediumExtendedDetails(medium)
                onGlobalLayout {
                    if (isAdded) {
                        val realY = getExtendedDetailsY(height)
                        if (realY > 0) {
                            y = realY
                            beVisibleIf(text.isNotEmpty())
                            alpha = if (!context!!.config.hideExtendedDetails || !isFullscreen) 1f else 0f
                        }
                    }
                }
            }
        } else {
            view.photo_details.beGone()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (activity?.isDestroyed == false) {
            view.subsampling_view.recycle()
        }
        loadZoomableViewHandler.removeCallbacksAndMessages(null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // avoid GIFs being skewed, played in wrong aspect ratio
        if (medium.isGIF()) {
            view.onGlobalLayout {
                Handler().postDelayed({
                    loadGif()
                }, 50)
            }
        } else {
            hideZoomableView()
            loadImage()
        }

        initExtendedDetails()
        updateInstantSwitchWidths()
    }

    private fun hideZoomableView() {
        if (context?.config?.allowZoomingImages == true) {
            isSubsamplingVisible = false
            view.subsampling_view.recycle()
            view.subsampling_view.beGone()
            loadZoomableViewHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun photoClicked() {
        listener?.fragmentClicked()
    }

    private fun updateInstantSwitchWidths() {
        val newWidth = resources.getDimension(R.dimen.instant_change_bar_width) + if (activity?.portrait == false) activity!!.navigationBarWidth else 0
        view.instant_prev_item.layoutParams.width = newWidth.toInt()
        view.instant_next_item.layoutParams.width = newWidth.toInt()
    }

    override fun fullscreenToggled(isFullscreen: Boolean) {
        this.isFullscreen = isFullscreen
        view.photo_details.apply {
            if (storedShowExtendedDetails && isVisible()) {
                animate().y(getExtendedDetailsY(height))

                if (storedHideExtendedDetails) {
                    animate().alpha(if (isFullscreen) 0f else 1f).start()
                }
            }
        }
    }

    private fun getExtendedDetailsY(height: Int): Float {
        val smallMargin = resources.getDimension(R.dimen.small_margin)
        val fullscreenOffset = smallMargin + if (isFullscreen) 0 else context!!.navigationBarHeight
        val actionsHeight = if (context!!.config.bottomActions && !isFullscreen) resources.getDimension(R.dimen.bottom_actions_height) else 0f
        return context!!.realScreenSize.y - height - actionsHeight - fullscreenOffset
    }
}
