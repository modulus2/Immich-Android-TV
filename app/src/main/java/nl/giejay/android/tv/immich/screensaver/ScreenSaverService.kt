package nl.giejay.android.tv.immich.screensaver

import android.annotation.SuppressLint
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.widget.Toast
import androidx.media3.datasource.DefaultHttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
<<<<<<< HEAD
=======
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ALBUMS
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ANIMATE_ASSET_SLIDE
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_INCLUDE_VIDEOS
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_INTERVAL
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_PLAY_SOUND
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_TYPE
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ANIMATION_SPEED
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_FORCE_ORIGINAL_VIDEO
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_GLIDE_TRANSFORMATION
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_HEIGHT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MERGE_PORTRAIT_PHOTOS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ONLY_USE_THUMBNAILS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_PAN_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_SCROLL_PANORAMAS
import nl.giejay.android.tv.immich.shared.util.toSliderItems
>>>>>>> 8a1cbc8 (Updates to add support for immich)
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.util.MediaSliderListener
import nl.giejay.mediaslider.view.MediaSliderView
import timber.log.Timber

<<<<<<< HEAD
class ScreenSaverService : DreamService(), MediaSliderListener, ScreenSaverAssetLoader.Host {
    private var ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private var mediaSliderView: MediaSliderView? = null
=======
class ScreenSaverService : DreamService(), MediaSliderListener {
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)
    private lateinit var apiClient: ApiClient
    private var mediaSliderView: MediaSliderView? = null
    private var photoTableView: PhotoTableView? = null
    private var currentPage = 0
    private var doneLoading = false
>>>>>>> 8a1cbc8 (Updates to add support for immich)

    @SuppressLint("UnsafeOptInUsageError")
    override fun onDreamingStarted() {
        ioScope = CoroutineScope(Job() + Dispatchers.IO)
        Timber.i("Starting screensaver")
        if (!PreferenceManager.isLoggedId()) {
            showScreenSaverMessage(R.string.screensaver_not_possible)
            finish()
            return
        }

        val apiKey = PreferenceManager.get(API_KEY)
<<<<<<< HEAD
        mediaSliderView = MediaSliderView(this)
        mediaSliderView!!.setDefaultExoFactory(
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("x-api-key" to apiKey))
        )
        setContentView(mediaSliderView)
        isInteractive = true
        ScreenSaverAssetLoader(
            ioScope,
            ApiClient.getClient(screenSaverApiClientConfig()),
            this
        ).start()
=======
        val config = ApiClientConfig(
            PreferenceManager.get(HOST_NAME),
            apiKey,
            PreferenceManager.get(DISABLE_SSL_VERIFICATION),
            PreferenceManager.get(DEBUG_MODE)
        )
        apiClient = ApiClient.getClient(config)
        isInteractive = true

        if (PreferenceManager.get(SCREENSAVER_EFFECT) == ScreenSaverEffect.PHOTO_TABLE) {
            startPhotoTableScreensaver()
        } else {
            startSliderScreensaver(apiKey)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startSliderScreensaver(apiKey: String) {
        val sliderView = MediaSliderView(this)
        mediaSliderView = sliderView
        sliderView.setDefaultExoFactory(
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("x-api-key" to apiKey))
        )
        setContentView(sliderView)

        ioScope.launch {
            if (ScreenSaverType.ALBUMS == PreferenceManager.get(SCREENSAVER_TYPE)) {
                loadImagesFromAlbums(PreferenceManager.get(SCREENSAVER_ALBUMS))
            } else {
                loadRandomImages(PreferenceManager.get(SCREENSAVER_TYPE)).invoke().map {
                    setInitialAssets(it, suspend {
                        if (doneLoading) {
                            emptyList()
                        } else {
                            currentPage += 1
                            val newAssets = loadRandomImages(PreferenceManager.get(SCREENSAVER_TYPE))
                                .invoke()
                                .getOrElse { emptyList() }
                            doneLoading = newAssets.size < PAGE_COUNT
                            newAssets.toSliderItems(false, PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS))
                        }
                    })
                }
            }
        }
>>>>>>> 8a1cbc8 (Updates to add support for immich)
    }

    private fun startPhotoTableScreensaver() {
        val tableView = PhotoTableView(this)
        val screensaverType = PreferenceManager.get(SCREENSAVER_TYPE)
        photoTableView = tableView

        if (screensaverType != ScreenSaverType.ALBUMS) {
            tableView.onNeedsMoreAssets = {
                if (!doneLoading) {
                    ioScope.launch {
                        currentPage += 1
                        loadRandomImages(screensaverType, ContentType.IMAGE).invoke().fold(
                            { error ->
                                Timber.e("Could not load more Photo Table assets: $error")
                            },
                            { assets ->
                                doneLoading = assets.size < PAGE_COUNT
                                withContext(Dispatchers.Main) {
                                    tableView.addAssets(assets)
                                }
                            }
                        )
                    }
                }
            }
        }

        setContentView(tableView)
        ioScope.launch {
            if (ScreenSaverType.ALBUMS == screensaverType) {
                loadPhotoTableImagesFromAlbums(PreferenceManager.get(SCREENSAVER_ALBUMS))
            } else {
                loadRandomImages(screensaverType, ContentType.IMAGE).invoke().fold(
                    { error -> handlePhotoTableLoadError(error) },
                    { assets ->
                        doneLoading = assets.size < PAGE_COUNT
                        setInitialPhotoTableAssets(assets)
                    }
                )
            }
        }
    }

    override fun onDreamingStopped() {
<<<<<<< HEAD
        ioScope.cancel()
        mediaSliderView?.onDestroy()
        super.onDreamingStopped()
    }

    override fun showScreenSaverMessage(messageRes: Int, longDuration: Boolean) {
=======
        mediaSliderView?.onDestroy()
        photoTableView?.stop()
        super.onDreamingStopped()
    }

    private fun loadRandomImages(
        screenSaverType: ScreenSaverType,
        contentType: ContentType = if (PreferenceManager.get(SCREENSAVER_INCLUDE_VIDEOS)) ContentType.ALL else ContentType.IMAGE
    ): suspend () -> Either<String, List<Asset>> {
        return when (screenSaverType) {
            ScreenSaverType.RECENT -> {
                suspend {
                    apiClient.recentAssets(currentPage, PAGE_COUNT, contentType = contentType)
                }
            }

            ScreenSaverType.SIMILAR_TIME_PERIOD -> {
                suspend {
                    apiClient.similarAssets(currentPage, PAGE_COUNT, contentType = contentType)
                }
            }

            else -> {
                suspend {
                    apiClient.listAssets(currentPage, PAGE_COUNT, true, contentType = contentType)
                }
            }
        }
    }

    private suspend fun loadPhotoTableImagesFromAlbums(albums: Set<String>) {
        try {
            if (albums.isNotEmpty()) {
                val shuffledAlbums = albums.toList().shuffled()
                apiClient.listAssetsFromAlbum(shuffledAlbums.first()).fold(
                    { error -> handlePhotoTableLoadError(error) },
                    { album ->
                        val randomAssets = album.assets.filterImages().shuffled()
                        setInitialPhotoTableAssets(randomAssets)
                        if (shuffledAlbums.size > 1) {
                            val nextAlbums = shuffledAlbums.drop(1).map { apiClient.listAssetsFromAlbum(it) }
                            val assets = nextAlbums.flatMap { it.getOrNone().toList() }
                                .flatMap { it.assets }
                                .filterImages()
                                .shuffled()
                            withContext(Dispatchers.Main) {
                                photoTableView?.addAssets(assets)
                            }
                        }
                    }
                )
            } else {
                showErrorMessageMainScope(getString(R.string.set_albums_screensaver_error))
                finish()
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not fetch assets from Immich for Photo Table Screensaver")
            showErrorMessageMainScope(getString(R.string.could_not_load_assets))
            finish()
        }
    }

    private suspend fun loadImagesFromAlbums(albums: Set<String>) {
        try {
            if (albums.isNotEmpty()) {
                val shuffledAlbums = albums.toList().shuffled()
                apiClient.listAssetsFromAlbum(shuffledAlbums.first()).map { album ->
                    val randomAssets = getAssets(listOf(album))
                    setInitialAssets(randomAssets, null)
                    if (shuffledAlbums.size > 1) {
                        val nextAlbums = shuffledAlbums.drop(1).map { apiClient.listAssetsFromAlbum(it) }
                        val assets = getAssets(nextAlbums.flatMap { it.getOrNone().toList() })
                        setAllAssets((randomAssets + assets).shuffled().distinct())
                    }
                }
            } else {
                showErrorMessageMainScope(getString(R.string.set_albums_screensaver_error))
                finish()
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not fetch assets from Immich for Screensaver")
            showErrorMessageMainScope(getString(R.string.could_not_load_assets))
            finish()
        }
    }

    private suspend fun showErrorMessageMainScope(errorMessage: String) {
        withContext(Dispatchers.Main) {
            showErrorMessage(errorMessage)
        }
    }

    private suspend fun handlePhotoTableLoadError(error: String) {
        Timber.e("Could not load Photo Table assets: $error")
        showErrorMessageMainScope(getString(R.string.could_not_load_assets))
        finish()
    }

    private fun showErrorMessage(errorMessage: String) {
>>>>>>> 8a1cbc8 (Updates to add support for immich)
        Toast.makeText(
            this,
            getString(messageRes),
            if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }

    override fun exitScreenSaver() {
        finish()
    }

<<<<<<< HEAD
    override fun onScreenSaverConfigurationReady(configuration: MediaSliderConfiguration) {
        mediaSliderView?.loadMediaSliderView(configuration)
        mediaSliderView?.toggleSlideshow(false)
    }

    override fun onButtonPressed(keyEvent: KeyEvent): Boolean {
        if ((keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) && mediaSliderView?.isControllerVisible() == false) {
=======
    private fun filterVideos(assets: List<Asset>) = if (PreferenceManager.get(SCREENSAVER_INCLUDE_VIDEOS)) {
        assets.shuffled()
    } else {
        assets.filter { it.type != "VIDEO" }.shuffled()
    }

    private suspend fun setInitialAssets(assets: List<Asset>, loadMore: LoadMore?) = withContext(Dispatchers.Main) {
        if (assets.isEmpty()) {
            Toast.makeText(
                this@ScreenSaverService,
                getString(R.string.no_assets_for_screensaver),
                Toast.LENGTH_LONG
            ).show()
        } else {
            mediaSliderView?.loadMediaSliderView(
                MediaSliderConfiguration(
                    0,
                    PreferenceManager.get(SCREENSAVER_INTERVAL),
                    PreferenceManager.get(SLIDER_ONLY_USE_THUMBNAILS),
                    PreferenceManager.get(SCREENSAVER_PLAY_SOUND),
                    assets.toSliderItems(keepOrder = false, mergePortrait = PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS)),
                    loadMore,
                    animationSpeedMillis = PreferenceManager.get(SLIDER_ANIMATION_SPEED),
                    maxCutOffHeight = PreferenceManager.get(SLIDER_MAX_CUT_OFF_HEIGHT),
                    maxCutOffWidth = PreferenceManager.get(SLIDER_MAX_CUT_OFF_HEIGHT),
                    transformation = PreferenceManager.get(SLIDER_GLIDE_TRANSFORMATION),
                    debugEnabled = PreferenceManager.get(DEBUG_MODE),
                    enableSlideAnimation = PreferenceManager.get(SCREENSAVER_ANIMATE_ASSET_SLIDE),
                    gradiantOverlay = true,
                    metaDataConfig = PreferenceManager.getAllMetaData(MetaDataScreen.SCREENSAVER),
                    zoomAndScrollPanorama = PreferenceManager.get(SLIDER_ZOOM_SCROLL_PANORAMAS),
                    zoomEffectPercent = PreferenceManager.get(SLIDER_ZOOM_EFFECT),
                    panEffectPercent = PreferenceManager.get(SLIDER_PAN_EFFECT),
                    useLargeVideoBuffer = PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO)
                )
            )
            mediaSliderView?.toggleSlideshow(false)
        }
    }

    private suspend fun setInitialPhotoTableAssets(assets: List<Asset>) = withContext(Dispatchers.Main) {
        val imageAssets = assets.filterImages()
        if (imageAssets.isEmpty()) {
            Timber.w("Photo Table received no image assets")
            Toast.makeText(
                this@ScreenSaverService,
                getString(R.string.no_assets_for_screensaver),
                Toast.LENGTH_LONG
            ).show()
        } else {
            photoTableView?.setAssets(imageAssets)
        }
    }

    private suspend fun setAllAssets(assets: List<Asset>) = withContext(Dispatchers.Main) {
        mediaSliderView?.setItems(
            assets.toSliderItems(
                keepOrder = false,
                mergePortrait = PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS)
            )
        )
    }

    override fun onButtonPressed(keyEvent: KeyEvent): Boolean {
        val isSelect = keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyEvent.keyCode == KeyEvent.KEYCODE_ENTER
        if (photoTableView != null && isSelect) {
            finish()
            return true
        }
        if (isSelect && mediaSliderView?.isControllerVisible() == false) {
>>>>>>> 8a1cbc8 (Updates to add support for immich)
            finish()
            return true
        }
        return false
    }

    private fun List<Asset>.filterImages(): List<Asset> {
        return filter { it.type == "IMAGE" }
    }

    companion object {
        private const val PAGE_COUNT = 100
    }
}
