package dev.g9

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import dev.g9.kit.Format
import dev.g9.kit.GalleryItem
import dev.g9.kit.GalleryStore
import dev.g9.kit.ZoomImageView
import dev.g9.kit.galleryMeta
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import life.furrer.nine.core.settings.openNineSettings
import life.furrer.nine.core.ui.Dimens
import life.furrer.nine.core.ui.HintBar
import life.furrer.nine.core.ui.LongPressKey
import life.furrer.nine.core.ui.MenuItem
import life.furrer.nine.core.ui.MenuSheet
import life.furrer.nine.core.ui.NineActivity
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.ui.dp

private const val SEEK_MS = 10_000
private const val TICK_MS = 500L

/** One photo or video full screen on black.
 *
 *  Opened from a list, it walks that list (all, a folder, or the trash): Up/Down step to the
 *  newer/older one, and Left/Right do the same for photos. A photo zooms with OK and pans with the
 *  D-pad while zoomed; a video plays with OK, and Left/Right seek ten seconds. `#` shows the
 *  details. Opened by another app with a single photo or video, it shows just that one. */
class ViewerActivity : NineActivity() {

    /** What's on screen: a gallery item (with its place in the list), or another app's file. */
    private sealed interface Shown {
        val uri: Uri
        val isVideo: Boolean
        val mimeType: String

        data class Item(val item: GalleryItem) : Shown {
            override val uri: Uri get() = GalleryStore.contentUri(item)
            override val isVideo get() = item.isVideo
            override val mimeType get() = item.mimeType
        }

        data class External(override val uri: Uri, override val isVideo: Boolean, override val mimeType: String, val name: String, val size: Long) : Shown
    }

    private val main = Handler(Looper.getMainLooper())
    private val decoder = Executors.newSingleThreadExecutor()

    private var scope: Scope = Scope.All
    private var items: List<GalleryItem> = emptyList()
    private var index = -1
    private var shown: Shown? = null
    private var videoReady = false
    private var detailsShown = false

    private lateinit var image: ZoomImageView
    private lateinit var video: VideoView
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var hintBar: HintBar
    private lateinit var menuSheet: MenuSheet

    private val starKey = LongPressKey(::openMenu) { openNineSettings() }

    private val ticker = object : Runnable {
        override fun run() {
            updateHint()
            if (videoReady && video.isPlaying) main.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        image = ZoomImageView(this)
        video = VideoView(this).apply {
            visibility = View.GONE
            setOnPreparedListener { player ->
                videoReady = true
                image.visibility = View.GONE
                showStatus(null)
                player.isLooping = false
                if (Prefs.autoplay(this@ViewerActivity)) start() else seekTo(1)
                startTicker()
            }
            setOnCompletionListener { updateHint() }
            setOnErrorListener { _, what, extra ->
                Log.w("G9", "video playback failed: $what/$extra")
                videoReady = false
                visibility = View.GONE
                image.visibility = View.VISIBLE
                showStatus("Can't play this video on this phone")
                updateHint()
                true
            }
        }
        status = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Palette.GRAY_DIM)
            setShadowLayer(4f, 0f, 0f, Palette.BLACK)
        }
        details = TextView(this).apply {
            textSize = 13f
            setTextColor(Palette.WHITE)
            setBackgroundColor(0xB3000000.toInt())
            setPadding(dp(Dimens.SIDE_PADDING_DP), dp(10), dp(Dimens.SIDE_PADDING_DP), dp(10))
            visibility = View.GONE
        }
        hintBar = HintBar(this)
        menuSheet = MenuSheet(this)
        val stage = FrameLayout(this).apply {
            setBackgroundColor(Palette.BLACK)
            val full = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(image, full)
            addView(video, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
            addView(status, FrameLayout.LayoutParams(full))
            addView(details, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Palette.BLACK)
                addView(
                    LinearLayout(this@ViewerActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                        addView(hintBar)
                    },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                addView(menuSheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            },
        )

        val external = intent.data.takeIf { intent.action == Intent.ACTION_VIEW }
        if (external != null) openExternal(external) else openFromList(Scope.from(intent), intent.getLongExtra(EXTRA_ITEM_ID, 0L))
    }

    override fun onPause() {
        super.onPause()
        if (videoReady && video.isPlaying) video.pause()
        main.removeCallbacks(ticker)
        starKey.reset()
        updateHint()
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(ticker)
        decoder.shutdownNow()
        video.stopPlayback()
    }

    /** Tells the list which item was last on screen, so it lands there on return. */
    override fun finish() {
        (shown as? Shown.Item)?.let { setResult(RESULT_OK, Intent().putExtra(RESULT_ITEM_ID, it.item.id)) }
        super.finish()
    }

    // ── Opening ─────────────────────────────────────────────────────────────────────────────

    private fun openFromList(listScope: Scope, itemId: Long) {
        scope = listScope
        showStatus("Loading…")
        val appContext = applicationContext
        decoder.execute {
            val loaded = listScope.load(appContext)
            main.post {
                if (isDestroyed) return@post
                items = loaded
                val at = loaded.indexOfFirst { it.id == itemId }
                if (at < 0) {
                    showStatus("This photo or video is gone")
                    updateHint()
                } else {
                    showAt(at)
                }
            }
        }
    }

    private fun openExternal(uri: Uri) {
        val type = intent.type ?: contentResolver.getType(uri).orEmpty()
        var name = uri.lastPathSegment.orEmpty()
        var size = 0L
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    size = c.getLong(1)
                }
            }
        }
        show(Shown.External(uri, type.startsWith("video/"), type, name, size))
    }

    private fun showAt(at: Int) {
        index = at
        show(Shown.Item(items[at]))
    }

    private fun show(next: Shown) {
        shown = next
        main.removeCallbacks(ticker)
        if (videoReady || video.visibility == View.VISIBLE) video.stopPlayback()
        videoReady = false
        video.visibility = View.GONE
        image.visibility = View.VISIBLE
        image.show(null)
        renderDetails()
        if (next.isVideo) {
            // The still first, so there's a picture while the player gets ready.
            loadStill(next) { GalleryStore.thumbnail(applicationContext, next.uri, longestSide())?.let { android.graphics.drawable.BitmapDrawable(resources, it) } }
            showStatus(null)
            video.visibility = View.VISIBLE
            video.setVideoURI(next.uri)
        } else {
            showStatus(null)
            loadStill(next) { GalleryStore.decodeForViewing(applicationContext, next.uri, longestSide() * 2) }
        }
        updateHint()
    }

    private fun loadStill(forShown: Shown, load: () -> android.graphics.drawable.Drawable?) {
        decoder.execute {
            val picture = load()
            main.post {
                if (isDestroyed || shown != forShown || videoReady) return@post
                if (picture == null) {
                    if (!forShown.isVideo) showStatus("Can't open this picture")
                } else {
                    image.show(picture)
                }
            }
        }
    }

    private fun longestSide(): Int = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)

    private fun showStatus(text: String?) {
        status.text = text.orEmpty()
        status.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    // ── Details ─────────────────────────────────────────────────────────────────────────────

    private fun renderDetails() {
        details.visibility = if (detailsShown) View.VISIBLE else View.GONE
        if (!detailsShown) return
        details.text = when (val current = shown) {
            is Shown.Item -> itemDetails(current.item)
            is Shown.External -> listOfNotNull(
                current.name.takeIf { it.isNotBlank() },
                current.mimeType.takeIf { it.isNotBlank() },
                current.size.takeIf { it > 0 }?.let { Format.fileSize(it) },
                "opened from another app",
            ).joinToString("\n")
            null -> ""
        }
    }

    private fun itemDetails(item: GalleryItem): String {
        val date = Instant.ofEpochMilli(item.addedAtMs).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault()))
        return listOfNotNull(
            item.displayName.takeIf { it.isNotBlank() },
            "added $date",
            if (item.isVideo) "video, ${Format.duration((item.durationMs / 1000).toInt())}" else "photo",
            galleryMeta(item),
            trashExpiry(item.expiresAtMs, System.currentTimeMillis()).takeIf { it.isNotEmpty() },
            item.path,
        ).joinToString("\n")
    }

    // ── Moving and controls ─────────────────────────────────────────────────────────────────

    /** -1 newer (up the list), +1 older. */
    private fun step(direction: Int) {
        if (shown !is Shown.Item || items.isEmpty()) return
        val next = index + direction
        if (next !in items.indices) {
            hintBar.setText(if (direction < 0) "this is the newest" else "this is the oldest")
            return
        }
        showAt(next)
    }

    private fun togglePlay() {
        if (!videoReady) return
        if (video.isPlaying) video.pause() else video.start()
        startTicker()
    }

    private fun seek(deltaMs: Int) {
        if (!videoReady) return
        video.seekTo((video.currentPosition + deltaMs).coerceIn(0, video.duration.coerceAtLeast(0)))
        updateHint()
    }

    private fun startTicker() {
        main.removeCallbacks(ticker)
        main.post(ticker)
    }

    private fun updateHint() {
        val position = if (shown is Shown.Item && items.isNotEmpty()) "${index + 1}/${items.size} · " else ""
        hintBar.setText(
            when {
                shown == null -> "‹ back"
                videoReady -> {
                    val time = "${Format.duration(video.currentPosition / 1000)} / ${Format.duration(video.duration / 1000)}"
                    "$time · ok ${if (video.isPlaying) "pause" else "play"} · ←→ 10 s · ↑↓"
                }
                image.isZoomed -> "✥ pan · ok zoom out · ‹ back"
                shown?.isVideo == true -> "$position‹ back"
                else -> "${position}ok zoom · ↑↓ · # info · * menu"
            },
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menuSheet.handleKeyDown(keyCode, event)) return true
        val panStep = image.panStep()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (shown?.isVideo == true) togglePlay() else image.toggleZoom()
            KeyEvent.KEYCODE_DPAD_LEFT -> when {
                image.isZoomed -> image.pan(panStep, 0f)
                videoReady -> seek(-SEEK_MS)
                else -> step(-1)
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> when {
                image.isZoomed -> image.pan(-panStep, 0f)
                videoReady -> seek(SEEK_MS)
                else -> step(1)
            }
            KeyEvent.KEYCODE_DPAD_UP -> if (image.isZoomed) image.pan(0f, panStep) else step(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (image.isZoomed) image.pan(0f, -panStep) else step(1)
            KeyEvent.KEYCODE_POUND -> {
                detailsShown = !detailsShown
                renderDetails()
            }
            KeyEvent.KEYCODE_STAR -> {
                starKey.onDown(event.repeatCount)
                return true
            }
            KeyEvent.KEYCODE_BACK -> when {
                image.isZoomed -> image.toggleZoom()
                detailsShown -> {
                    detailsShown = false
                    renderDetails()
                }
                else -> finish()
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        updateHint()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STAR) {
            starKey.onUp()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // ── Menu and actions ────────────────────────────────────────────────────────────────────

    private fun openMenu() {
        val current = shown ?: return
        val items = mutableListOf<MenuItem>()
        if (current.isVideo && videoReady) items += MenuItem(if (video.isPlaying) "Pause" else "Play") { togglePlay() }
        if (!current.isVideo && image.hasPicture) items += MenuItem(if (image.isZoomed) "Zoom out" else "Zoom in") { image.toggleZoom(); updateHint() }
        items += MenuItem("Share") {
            if (!Actions.share(this, listOf(current.uri), listOf(current.mimeType))) hintBar.setText("no app on this phone takes it")
        }
        if (current is Shown.Item) {
            if (scope == Scope.Trash) {
                items += MenuItem("Restore") { request { Actions.requestTrash(this, listOf(current.uri), trash = false) } }
                items += MenuItem("Delete for good…") { request { Actions.requestDelete(this, listOf(current.uri)) } }
            } else {
                items += MenuItem("Move to trash…") { request { Actions.requestTrash(this, listOf(current.uri), trash = true) } }
            }
        }
        items += MenuItem(if (detailsShown) "Hide details" else "Details") {
            detailsShown = !detailsShown
            renderDetails()
        }
        if (current is Shown.External && isMediaStoreUri(current.uri)) {
            items += MenuItem("Move to trash…") { request { Actions.requestTrash(this, listOf(current.uri), trash = true) } }
        }
        items += MenuItem("Settings") { openNineSettings() }
        items += MenuItem(if (current is Shown.Item) "Back to the list" else "Close") { finish() }
        menuSheet.show(items)
    }

    private fun isMediaStoreUri(uri: Uri) = uri.authority == MediaStore.AUTHORITY

    private fun request(launch: () -> Boolean) {
        if (!launch()) hintBar.setText("Android refused the request")
    }

    /** After a confirmed trash, restore or delete, the item leaves this list: show the next one
     *  (the older neighbour, or the newer when it was the oldest), or go back when none is left. */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode !in setOf(Actions.REQUEST_TRASH, Actions.REQUEST_RESTORE, Actions.REQUEST_DELETE)) return
        if (resultCode != RESULT_OK) return
        val current = shown
        if (current !is Shown.Item) {
            super.finish()
            return
        }
        items = items.filterIndexed { i, _ -> i != index }
        if (items.isEmpty()) {
            shown = null
            super.finish()
            return
        }
        showAt(index.coerceAtMost(items.size - 1))
        hintBar.setText(
            when (requestCode) {
                Actions.REQUEST_TRASH -> "moved to the trash"
                Actions.REQUEST_RESTORE -> "restored"
                else -> "deleted"
            },
        )
    }

    companion object {
        private const val EXTRA_ITEM_ID = "item_id"
        const val RESULT_ITEM_ID = "item_id"

        fun intentFor(context: Context, scope: Scope, itemId: Long): Intent =
            scope.putInto(Intent(context, ViewerActivity::class.java)).putExtra(EXTRA_ITEM_ID, itemId)
    }
}
