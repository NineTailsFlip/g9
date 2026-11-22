package dev.g9

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.g9.kit.GalleryItem
import dev.g9.kit.GalleryMarks
import dev.g9.kit.GalleryRow
import dev.g9.kit.GalleryStore
import dev.g9.kit.THUMB_DP
import dev.g9.kit.THUMB_HEADER_HEIGHT_DP
import dev.g9.kit.THUMB_ROW_HEIGHT_DP
import dev.g9.kit.ThumbRowView
import dev.g9.kit.ThumbnailLoader
import dev.g9.kit.buildGalleryRows
import dev.g9.kit.galleryCount
import dev.g9.kit.galleryDayJump
import dev.g9.kit.galleryMeta
import dev.g9.kit.galleryTitle
import java.time.LocalDate
import life.furrer.nine.core.settings.openNineSettings
import life.furrer.nine.core.ui.Dimens
import life.furrer.nine.core.ui.HintBar
import life.furrer.nine.core.ui.LongPressKey
import life.furrer.nine.core.ui.MenuItem
import life.furrer.nine.core.ui.MenuSheet
import life.furrer.nine.core.ui.NineActivity
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.ui.PermissionScreen
import life.furrer.nine.core.ui.RowBinder
import life.furrer.nine.core.ui.RowListView
import life.furrer.nine.core.ui.dp
import life.furrer.nine.core.util.ProviderWatcher
import life.furrer.nine.core.util.Reloader

private const val REQUEST_MEDIA = 300

/** A list of photos and videos — all of them (the launcher entry), one folder, or the trash —
 *  newest first under day headers, one row each with its thumbnail. It follows MediaStore live:
 *  a photo just taken, or one a messenger saved, shows up without leaving the screen. */
class BrowseActivity : NineActivity() {

    private lateinit var scope: Scope
    private var rows: List<GalleryRow> = emptyList()
    private var byId: Map<Long, GalleryItem> = emptyMap()
    private val marks = GalleryMarks()
    private var loaded = false
    /** The item to land on after the next load: the one the viewer was showing when it closed. */
    private var pendingSelect = 0L

    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var status: TextView
    private lateinit var rowListView: RowListView
    private lateinit var hintBar: HintBar
    private lateinit var menuSheet: MenuSheet
    private lateinit var permissionScreen: PermissionScreen
    private lateinit var thumbs: ThumbnailLoader
    private lateinit var watcher: ProviderWatcher

    private val starKey = LongPressKey(::openMenu) { openNineSettings() }

    private val reloader = Reloader(background = { scope.load(applicationContext) }, onResult = ::onLoaded)

    private val binder = object : RowBinder {
        override fun rowCount(): Int = rows.size
        override fun createRowView(context: Context): View = ThumbRowView(context)
        override fun bind(view: View, index: Int, selected: Boolean) {
            val rowView = view as ThumbRowView
            when (val row = rows[index]) {
                is GalleryRow.DayHeader -> rowView.bindHeader(row.label)
                is GalleryRow.Item -> rowView.bindRow(
                    galleryTitle(row.item),
                    meta(row.item),
                    selected,
                    marks.isMarked(row.item.id),
                    thumbs.get(row.item),
                )
            }
        }
        override fun rowHeight(index: Int): Int = if (rows[index] is GalleryRow.DayHeader) THUMB_HEADER_HEIGHT_DP else THUMB_ROW_HEIGHT_DP
        override fun isSelectable(index: Int): Boolean = rows[index].isSelectable()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope = Scope.from(intent)
        thumbs = ThumbnailLoader(this, dp(THUMB_DP)) { rowListView.notifyDataChanged() }
        watcher = ProviderWatcher(this, GalleryStore.CHANGES) { reloader.request() }

        title = TextView(this).apply {
            textSize = 18f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Palette.WHITE)
            setPadding(dp(Dimens.SIDE_PADDING_DP), dp(10), dp(Dimens.SIDE_PADDING_DP), 0)
            text = scope.title
        }
        subtitle = TextView(this).apply {
            textSize = 12f
            maxLines = 1
            setTextColor(Palette.GRAY_MUTED)
            setPadding(dp(Dimens.SIDE_PADDING_DP), 0, dp(Dimens.SIDE_PADDING_DP), dp(6))
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
        permissionScreen = PermissionScreen(this).apply {
            onGrant = { requestPermissions(GalleryStore.PERMISSIONS, REQUEST_MEDIA) }
            onLeave = { finish() }
        }
        val listArea = FrameLayout(this).apply {
            addView(rowListView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(subtitle)
            addView(listArea, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(hintBar)
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Palette.BLACK)
                val full = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                addView(column, full)
                addView(menuSheet, FrameLayout.LayoutParams(full))
                addView(permissionScreen, FrameLayout.LayoutParams(full))
            },
        )
        render()
    }

    override fun onResume() {
        super.onResume()
        if (!GalleryStore.hasAccess(this)) {
            permissionScreen.show(
                "G9 shows the photos and videos on this phone, so it needs to be allowed to see " +
                    "them. Nothing leaves the phone unless you share it.\n\nYou can allow all of them, or pick some.",
            )
            return
        }
        permissionScreen.hide()
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // onResume follows the permission dialog and loads if access was granted.
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Actions.REQUEST_TRASH, Actions.REQUEST_RESTORE, Actions.REQUEST_DELETE -> {
                if (resultCode == RESULT_OK) {
                    marks.clear()
                    hintBar.setText(
                        when (requestCode) {
                            Actions.REQUEST_TRASH -> "moved to the trash"
                            Actions.REQUEST_RESTORE -> "restored"
                            else -> "deleted"
                        },
                    )
                }
                reloader.request()
            }
            REQUEST_VIEWER -> pendingSelect = data?.getLongExtra(ViewerActivity.RESULT_ITEM_ID, 0L) ?: 0L
        }
    }

    private fun onLoaded(items: List<GalleryItem>) {
        if (isDestroyed) return
        val keep = pendingSelect.takeIf { it != 0L } ?: selectedItem()?.id
        pendingSelect = 0L
        rows = buildGalleryRows(items, LocalDate.now())
        byId = items.associateBy { it.id }
        marks.retainAll(byId.keys)
        val rowOf = rows.withIndex().mapNotNull { (i, row) -> (row as? GalleryRow.Item)?.let { it.item.id to i } }.toMap()
        thumbs.rowOf = rowOf
        val firstLoad = !loaded
        loaded = true
        rowListView.notifyDataChanged()
        val keepRow = keep?.let { rowOf[it] }
        when {
            keepRow != null -> rowListView.setSelectedIndex(keepRow)
            firstLoad -> rowListView.selectFirstSelectable()
        }
        render()
    }

    private fun meta(item: GalleryItem): String =
        if (scope == Scope.Trash) "${trashExpiry(item.expiresAtMs, System.currentTimeMillis())} · ${galleryMeta(item)}" else galleryMeta(item)

    private fun selectedItem(): GalleryItem? = (rows.getOrNull(rowListView.selectedIndex) as? GalleryRow.Item)?.item

    /** The marked ones if any, else the selected one. */
    private fun chosen(): List<GalleryItem> =
        if (marks.size > 0) marks.resolve(byId) else listOfNotNull(selectedItem())

    private fun render() {
        thumbs.focusRow = rowListView.selectedIndex
        subtitle.text = when {
            !loaded -> ""
            marks.size > 0 -> "${marks.size} marked"
            else -> galleryCount(byId.values) + if (scope == Scope.All && GalleryStore.hasPartialAccess(this)) " you allowed G9 to see" else ""
        }
        subtitle.visibility = if (subtitle.text.isEmpty()) View.GONE else View.VISIBLE
        status.visibility = if (loaded && rows.isNotEmpty()) View.GONE else View.VISIBLE
        if (loaded && rows.isEmpty()) {
            status.text = when (scope) {
                Scope.Trash -> "The trash is empty"
                else -> "No photos or videos here"
            }
        }
        hintBar.setText(
            when {
                !loaded || rows.isEmpty() -> if (scope == Scope.All) "0 folders · * menu" else "* menu · ‹ back"
                marks.size > 0 -> "${marks.size} marked · * share or trash · ‹ unmark"
                scope == Scope.All -> "ok open · # mark · 0 folders · * menu"
                else -> "ok open · # mark · * menu · ‹ back"
            },
        )
    }

    // ── Keys ────────────────────────────────────────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (permissionScreen.handleKeyDown(keyCode)) return true
        if (menuSheet.handleKeyDown(keyCode, event)) return true
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> rowListView.moveSelection(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> rowListView.moveSelection(1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val direction = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                galleryDayJump(rows, rowListView.selectedIndex, direction)?.let { rowListView.setSelectedIndex(it) }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> selectedItem()?.let(::openViewer)
            KeyEvent.KEYCODE_POUND -> selectedItem()?.let { marks.toggle(it.id); rowListView.notifyDataChanged() }
            KeyEvent.KEYCODE_0 -> openFolders()
            KeyEvent.KEYCODE_STAR -> {
                starKey.onDown(event.repeatCount)
                return true
            }
            KeyEvent.KEYCODE_BACK -> if (marks.size > 0) {
                marks.clear()
                rowListView.notifyDataChanged()
            } else {
                finish()
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        render()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STAR) {
            starKey.onUp()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // ── Actions ─────────────────────────────────────────────────────────────────────────────

    private fun openViewer(item: GalleryItem) {
        @Suppress("DEPRECATION") // Activity's own result API: the suite uses no AndroidX.
        startActivityForResult(ViewerActivity.intentFor(this, scope, item.id), REQUEST_VIEWER)
    }

    private fun openFolders() {
        // From a folder or the trash, the folders screen is already behind this one.
        if (scope == Scope.All) startActivity(Intent(this, FoldersActivity::class.java)) else finish()
    }

    private fun openMenu() {
        val items = mutableListOf<MenuItem>()
        val chosen = chosen()
        val count = if (marks.size > 0) " ${marks.size} marked" else ""
        selectedItem()?.let { item -> items += MenuItem("Open") { openViewer(item) } }
        if (chosen.isNotEmpty()) {
            items += MenuItem("Share$count") { share(chosen) }
            if (scope == Scope.Trash) {
                items += MenuItem("Restore$count") { request { Actions.requestTrash(this, uris(chosen), trash = false) } }
                items += MenuItem("Delete for good$count…") { request { Actions.requestDelete(this, uris(chosen)) } }
            } else {
                items += MenuItem("Move to trash$count…") { request { Actions.requestTrash(this, uris(chosen), trash = true) } }
            }
        }
        selectedItem()?.let { item ->
            items += MenuItem(if (marks.isMarked(item.id)) "Unmark" else "Mark") {
                marks.toggle(item.id)
                rowListView.notifyDataChanged()
                render()
            }
            items += MenuItem("Mark this day") { markDayOf(rowListView.selectedIndex) }
        }
        if (marks.size > 0) {
            items += MenuItem("Clear marks") {
                marks.clear()
                rowListView.notifyDataChanged()
                render()
            }
        }
        if (scope != Scope.Trash) items += MenuItem("Folders and trash") { openFolders() }
        if (GalleryStore.hasPartialAccess(this)) {
            items += MenuItem("Choose which photos G9 sees") { requestPermissions(GalleryStore.PERMISSIONS, REQUEST_MEDIA) }
        }
        items += MenuItem("Settings") { openNineSettings() }
        menuSheet.show(items)
    }

    /** Marks every item under the selection's day header. */
    private fun markDayOf(index: Int) {
        var start = index
        while (start > 0 && rows[start] !is GalleryRow.DayHeader) start--
        var i = start + 1
        while (i < rows.size && rows[i] is GalleryRow.Item) {
            val id = (rows[i] as GalleryRow.Item).item.id
            if (!marks.isMarked(id)) marks.toggle(id)
            i++
        }
        rowListView.notifyDataChanged()
        render()
    }

    private fun uris(items: List<GalleryItem>) = items.map(GalleryStore::contentUri)

    private fun share(items: List<GalleryItem>) {
        if (!Actions.share(this, uris(items), items.map { it.mimeType })) hintBar.setText("no app on this phone takes these")
    }

    private fun request(launch: () -> Boolean) {
        if (!launch()) hintBar.setText("Android refused the request")
    }

    companion object {
        private const val REQUEST_VIEWER = 400

        fun intentFor(context: Context, scope: Scope): Intent = scope.putInto(Intent(context, BrowseActivity::class.java))
    }
}
