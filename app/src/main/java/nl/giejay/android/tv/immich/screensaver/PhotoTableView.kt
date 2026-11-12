package nl.giejay.android.tv.immich.screensaver

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.util.ApiUtil
import java.util.LinkedList
import java.util.Random
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import timber.log.Timber

class PhotoTableView(context: Context) : FrameLayout(context) {
    private val onTable = LinkedList<View>()
    private val assets = mutableListOf<Asset>()
    private val launcher = Runnable {
        scheduleNext(dropPeriodMillis)
        launch()
    }
    private val waitingTargets = mutableListOf<CustomTarget<Drawable>>()
    private val random = Random()
    private val inset = 12.dp
    private val throwSpeed = 400.dp.toFloat()
    private val dropInterpolator = DecelerateInterpolator(4f)
    private var started = false
    private var nextAssetIndex = 0
    private var loading = false

    var onNeedsMoreAssets: (() -> Unit)? = null

    init {
        setBackgroundColor(Color.rgb(18, 20, 22))
        isFocusable = true
    }

    fun setAssets(newAssets: List<Asset>) {
        assets.clear()
        assets.addAll(newAssets.imageAssets().shuffled())
        Timber.i("Photo Table received ${assets.size} initial image assets")
        nextAssetIndex = 0
        if (started) {
            scheduleNext(0)
        } else {
            start()
        }
    }

    fun addAssets(newAssets: List<Asset>) {
        val imageAssets = newAssets.imageAssets().shuffled()
        assets.addAll(imageAssets)
        Timber.i("Photo Table added ${imageAssets.size} image assets; ${assets.size} available")
        if (started && onTable.isEmpty()) {
            scheduleNext(0)
        } else if (!started) {
            start()
        }
    }

    fun stop() {
        removeCallbacks(launcher)
        waitingTargets.forEach { Glide.with(context).clear(it) }
        waitingTargets.clear()
        loading = false
        started = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        start()
    }

    private fun start() {
        if (!started && width > 0 && height > 0 && assets.isNotEmpty()) {
            started = true
            scheduleNext(0)
        }
    }

    private fun launch() {
        if (loading || width == 0 || height == 0) {
            return
        }

        val asset = nextAsset() ?: run {
            onNeedsMoreAssets?.invoke()
            scheduleNext(fastDropPeriodMillis)
            return
        }

        val url = ApiUtil.getThumbnailUrl(asset.id, "preview") ?: return
        Timber.d("Photo Table loading asset ${asset.id} from $url")
        loading = true
        val target = object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                waitingTargets.remove(this)
                loading = false
                dropDrawable(resource, asset)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                waitingTargets.remove(this)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                waitingTargets.remove(this)
                loading = false
                scheduleNext(fastDropPeriodMillis)
            }
        }
        waitingTargets.add(target)
        Glide.with(context)
            .load(url)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    Timber.e(e, "Photo Table failed to load asset ${asset.id} from $url")
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Timber.d("Photo Table loaded asset ${asset.id} from $dataSource")
                    return false
                }
            })
            .into(target)
    }

    private fun nextAsset(): Asset? {
        if (assets.isEmpty()) {
            return null
        }
        if (nextAssetIndex >= assets.size) {
            nextAssetIndex = 0
            assets.shuffle()
            onNeedsMoreAssets?.invoke()
        }
        return assets[nextAssetIndex++]
    }

    private fun dropDrawable(drawable: Drawable, asset: Asset) {
        val imageView = ImageView(context).apply {
            setImageDrawable(drawable)
            setBackgroundColor(Color.WHITE)
            setPadding(inset, inset, inset, inset)
            scaleType = ImageView.ScaleType.CENTER_CROP
            rotation = throwRotation
            x = -width.toFloat()
            y = -height.toFloat()
        }

        val size = photoSize(asset, drawable)
        addView(imageView, LayoutParams(size.first, size.second, Gravity.TOP or Gravity.START))
        imageView.bringToFront()
        imageView.post {
            imageView.x = -imageView.width.toFloat()
            imageView.y = -imageView.height.toFloat()
            onTable.offer(imageView)
            dropOnTable(imageView)
            trimTableIfNeeded()
            if (onTable.size < tableCapacity) {
                scheduleNext(fastDropPeriodMillis)
            }
        }
    }

    private fun photoSize(asset: Asset, drawable: Drawable): Pair<Int, Int> {
        val maxLongSide = (imageRatio * max(width, height)).toInt()
        val maxShortSide = (imageRatio * min(width, height)).toInt()
        val aspectRatio = asset.exifInfo?.let { exif ->
            val rawWidth = exif.exifImageWidth
            val rawHeight = exif.exifImageHeight
            if (rawWidth != null && rawHeight != null && rawWidth > 0 && rawHeight > 0) {
                val rotated = exif.orientation == 6 || exif.orientation == 8
                if (rotated) rawHeight.toFloat() / rawWidth else rawWidth.toFloat() / rawHeight
            } else {
                null
            }
        } ?: drawable.intrinsicWidth.takeIf { it > 0 }?.let { intrinsicWidth ->
            val intrinsicHeight = drawable.intrinsicHeight
            if (intrinsicHeight > 0) intrinsicWidth.toFloat() / intrinsicHeight else null
        } ?: 1f

        return if (aspectRatio >= 1f) {
            maxLongSide to max((maxLongSide / aspectRatio).toInt(), inset * 2)
        } else {
            max((maxShortSide * aspectRatio).toInt(), inset * 2) to maxShortSide
        }
    }

    private fun dropOnTable(photo: View) {
        val target = randomDropPoint()
        val x = target.x - photo.width / 2f
        val y = target.y - photo.height / 2f
        val distance = hypot(x - photo.x, y - photo.y)
        val duration = max((1000f * distance / throwSpeed).toInt(), minDropDurationMillis)

        photo.animate()
            .withLayer()
            .scaleX(tableRatio / imageRatio)
            .scaleY(tableRatio / imageRatio)
            .rotation(randomFloat(-maxImageRotation, maxImageRotation))
            .x(x)
            .y(y)
            .alpha(1f)
            .setDuration(duration.toLong())
            .setInterpolator(dropInterpolator)
    }

    private fun randomDropPoint(): PointF {
        val centersX = floatArrayOf(0.3f, 0.3f, 0.5f, 0.7f, 0.7f)
        val centersY = floatArrayOf(0.3f, 0.7f, 0.5f, 0.3f, 0.7f)
        val index = random.nextInt(centersX.size)
        return PointF(
            centersX[index] * width + 0.05f * width * random.nextGaussian().toFloat(),
            centersY[index] * height + 0.05f * height * random.nextGaussian().toFloat()
        )
    }

    private fun randomFloat(from: Float, until: Float): Float {
        return from + random.nextFloat() * (until - from)
    }

    private fun trimTableIfNeeded() {
        if (onTable.size <= tableCapacity) {
            return
        }
        val targetSize = max(0, onTable.size - redealCount)
        while (onTable.size > targetSize) {
            fadeAway(onTable.poll())
        }
    }

    private fun fadeAway(photo: View?) {
        if (photo == null) {
            return
        }
        photo.animate().cancel()
        photo.animate()
            .withLayer()
            .alpha(0f)
            .setDuration(pickUpDurationMillis.toLong())
            .withEndAction {
                removeView(photo)
            }
    }

    private fun scheduleNext(delay: Int) {
        removeCallbacks(launcher)
        postDelayed(launcher, delay.toLong())
    }

    private fun List<Asset>.imageAssets(): List<Asset> {
        return filter { it.type == "IMAGE" }
    }

    private val Int.dp: Int
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            toFloat(),
            resources.displayMetrics
        ).toInt()

    companion object {
        private const val dropPeriodMillis = 45000
        private const val fastDropPeriodMillis = 3000
        private const val minDropDurationMillis = 1000
        private const val pickUpDurationMillis = 1000
        private const val tableCapacity = 10
        private const val redealCount = 5
        private const val imageRatio = 0.5f
        private const val tableRatio = 0.25f
        private const val maxImageRotation = 30f
        private const val throwRotation = -200f
    }
}
