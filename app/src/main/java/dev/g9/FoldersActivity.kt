package dev.g9

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.g9.kit.GalleryFolder
import dev.g9.kit.GalleryItem
import dev.g9.kit.GalleryStore
import dev.g9.kit.THUMB_DP
import dev.g9.kit.THUMB_ROW_HEIGHT_DP
import dev.g9.kit.ThumbRowView
import dev.g9.kit.ThumbnailLoader
import dev.g9.kit.buildFolders
import dev.g9.kit.galleryCount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import life.furrer.nine.core.settings.openNineSettings
import life.furrer.nine.core.ui.Dimens
import life.furrer.nine.core.ui.HintBar
import life.furrer.nine.core.ui.LongPressKey
import life.furrer.nine.core.ui.MenuItem
import life.furrer.nine.core.ui.MenuSheet
import life.furrer.nine.core.ui.NineActivity
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.ui.RowBinder
import life.furrer.nine.core.ui.RowListView
import life.furrer.nine.core.ui.dp
import life.furrer.nine.core.util.DateFmt
import life.furrer.nine.core.util.ProviderWatcher
import life.furrer.nine.core.util.Reloader

/** Every folder that holds photos or videos, the most recently added-to first, each with its
 *  newest picture — then the trash, when anything is in it. */
class FoldersActivity : NineActivity() {

    private sealed interface Row {
        data class Folder(val folder: GalleryFolder) : Row
        data class Trash(val items: List<GalleryItem>) : Row
    }

    private class Loaded(val folders: List<GalleryFolder>, val trash: List<GalleryItem>)

    private var rows: List<Row> = emptyList()
    private var loaded = false

    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var rowListView: RowListView
    private lateinit var hintBar: HintBar
    private lateinit var menuSheet: MenuSheet
    private lateinit var thumbs: ThumbnailLoader
    private lateinit var watcher: ProviderWatcher

    private val starKey = LongPressKey(::openMenu) { openNineSettings() }

    private val reloader = Reloader(
        background = {
            val all = GalleryStore.load(applicationContext, limit = GalleryStore.ALL_LIMIT)
            Loaded(buildFolders(all), GalleryStore.load(applicationContext, trashed = true, limit = GalleryStore.ALL_LIMIT))
        },
        onResult = ::onLoaded,
    )

    private val binder = object : RowBinder {
        override fun rowCount(): Int = rows.size
        override fun createRowView(context: Context): View = ThumbRowView(context)
        override fun bind(view: View, index: Int, selected: Boolean) {
            val rowView = view as ThumbRowView
            when (val row = rows[index]) {
                is Row.Folder -> rowView.bindRow(
                    row.folder.name,
                    "${row.folder.count} · newest ${dayOf(row.folder.newest)}",
                    selected,
                    false,
                    thumbs.get(row.folder.newest),
                )
                is Row.Trash -> rowView.bindRow(
                    "Trash",
                    "${galleryCount(row.items)} · kept 30 days",
                    selected,
                    false,
                    row.items.firstOrNull()?.let { thumbs.get(it) },
                )
            }
        }
        override fun rowHeight(index: Int): Int = THUMB_ROW_HEIGHT_DP
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        thumbs = ThumbnailLoader(this, dp(THUMB_DP)) { rowListView.notifyDataChanged() }
        watcher = ProviderWatcher(this, GalleryStore.CHANGES) { reloader.request() }
        title = TextView(this).apply {
            textSize = 18f
            setTextColor(Palette.WHITE)
            setPadding(dp(Dimens.SIDE_PADDING_DP), dp(10), dp(Dimens.SIDE_PADDING_DP), dp(6))
            text = "Folders"
        }
        rowListView = RowListView(this).apply { setBinder(binder) }
        status = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Palette.GRAY_DIM)
            text = "Loading…"
        }
        hintBar = HintBar(this)
        menuSheet = MenuSheet(this)
        val listArea = FrameLayout(this).apply {
            addView(rowListView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Palette.BLACK)
                addView(
                    LinearLayout(this@FoldersActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(title)
                        addView(listArea, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                        addView(hintBar)
                    },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                addView(menuSheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            },
        )
        hintBar.setText("ok open · * menu · ‹ back")
    }

    override fun onResume() {
        super.onResume()
        if (!GalleryStore.hasAccess(this)) {
            finish()
            return
        }
        watcher.start()
        reloader.request()
    }

    override fun onPause() {
        super.onPause()
        watcher.stop()
        starKey.reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        thumbs.close()
    }

    private fun onLoaded(result: Loaded) {
        if (isDestroyed) return
        rows = result.folders.map { Row.Folder(it) } + if (result.trash.isNotEmpty()) listOf(Row.Trash(result.trash)) else emptyList()
        loaded = true
        rowListView.notifyDataChanged()
        thumbs.focusRow = rowListView.selectedIndex
        status.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        if (rows.isEmpty()) status.text = "No photos or videos on this phone"
    }

    private fun dayOf(item: GalleryItem): String =
        DateFmt.dayLabel(Instant.ofEpochMilli(item.addedAtMs).atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now())

    private fun open(row: Row?) {
        when (row) {
            is Row.Folder -> startActivity(BrowseActivity.intentFor(this, Scope.Folder(row.folder.bucketId, row.folder.name)))
            is Row.Trash -> startActivity(BrowseActivity.intentFor(this, Scope.Trash))
            null -> {}
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menuSheet.handleKeyDown(keyCode, event)) return true
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> rowListView.moveSelection(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> rowListView.moveSelection(1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> open(rows.getOrNull(rowListView.selectedIndex))
            KeyEvent.KEYCODE_STAR -> {
                starKey.onDown(event.repeatCount)
                return true
            }
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_BACK -> finish()
            else -> return super.onKeyDown(keyCode, event)
        }
        thumbs.focusRow = rowListView.selectedIndex
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STAR) {
            starKey.onUp()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun openMenu() {
        val items = mutableListOf<MenuItem>()
        rows.getOrNull(rowListView.selectedIndex)?.let { row -> items += MenuItem("Open") { open(row) } }
        items += MenuItem("All photos and videos") { finish() }
        if (GalleryStore.hasPartialAccess(this)) {
            items += MenuItem("Choose which photos G9 sees") { requestPermissions(GalleryStore.PERMISSIONS, 300) }
        }
        items += MenuItem("Settings") { openNineSettings() }
        menuSheet.show(items)
    }
}
