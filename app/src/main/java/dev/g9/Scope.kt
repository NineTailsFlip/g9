package dev.g9

import android.content.Intent
import dev.g9.kit.GalleryItem
import dev.g9.kit.GalleryStore
import android.content.Context
import java.time.Duration

/** Which photos and videos a list or the viewer walks through: all of them, one folder, or the
 *  trash. Travels between screens as intent extras. */
sealed interface Scope {
    val title: String

    data object All : Scope {
        override val title = "Photos and videos"
    }

    data class Folder(val bucketId: Long, val name: String) : Scope {
        override val title get() = name
    }

    data object Trash : Scope {
        override val title = "Trash"
    }

    /** Blocking, like [GalleryStore.load]. */
    fun load(context: Context): List<GalleryItem> = when (this) {
        All -> GalleryStore.load(context)
        is Folder -> GalleryStore.load(context, bucketId = bucketId, limit = GalleryStore.ALL_LIMIT)
        Trash -> GalleryStore.load(context, trashed = true, limit = GalleryStore.ALL_LIMIT)
    }

    fun putInto(intent: Intent): Intent = when (this) {
        All -> intent.putExtra(EXTRA_SCOPE, "all")
        is Folder -> intent.putExtra(EXTRA_SCOPE, "folder").putExtra(EXTRA_BUCKET, bucketId).putExtra(EXTRA_NAME, name)
        Trash -> intent.putExtra(EXTRA_SCOPE, "trash")
    }

    companion object {
        private const val EXTRA_SCOPE = "scope"
        private const val EXTRA_BUCKET = "bucket_id"
        private const val EXTRA_NAME = "bucket_name"

        fun from(intent: Intent): Scope = when (intent.getStringExtra(EXTRA_SCOPE)) {
            "folder" -> Folder(intent.getLongExtra(EXTRA_BUCKET, 0L), intent.getStringExtra(EXTRA_NAME).orEmpty())
            "trash" -> Trash
            else -> All
        }
    }
}

/** "gone in 12 days" for something in the trash, as Android will delete it on its own. */
fun trashExpiry(expiresAtMs: Long, nowMs: Long): String {
    if (expiresAtMs <= 0) return ""
    val days = Duration.ofMillis(expiresAtMs - nowMs).toDays()
    return when {
        days <= 0 -> "gone today"
        days == 1L -> "gone tomorrow"
        else -> "gone in $days days"
    }
}

/** The MIME type a share intent carries for these items: exact for one, a wildcard for several,
 *  so the share sheet offers only apps that take them. */
fun shareType(mimeTypes: List<String>): String = when {
    mimeTypes.isEmpty() -> "*/*"
    mimeTypes.size == 1 -> mimeTypes.first()
    mimeTypes.all { it.startsWith("image/") } -> "image/*"
    mimeTypes.all { it.startsWith("video/") } -> "video/*"
    else -> "*/*"
}
