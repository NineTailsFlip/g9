package dev.g9

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

/** What G9 does to photos and videos besides showing them. Deleting goes through Android's own
 *  confirmation every time (MediaStore's trash and delete requests): G9 holds no write access, so
 *  nothing can disappear by a stray key press. */
object Actions {
    const val REQUEST_TRASH = 501
    const val REQUEST_RESTORE = 502
    const val REQUEST_DELETE = 503

    /** Hands [uris] to another app (a messenger, Bluetooth, email) through the share sheet.
     *  @return false if nothing on the phone takes them. */
    fun share(activity: Activity, uris: List<Uri>, mimeTypes: List<String>): Boolean {
        if (uris.isEmpty()) return false
        val type = shareType(mimeTypes)
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = type
        // The read grant travels with the ClipData; the share sheet passes it on to the app chosen.
        send.clipData = ClipData.newRawUri("", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            activity.startActivity(Intent.createChooser(send, if (uris.size == 1) "Share" else "Share ${uris.size}"))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Asks Android to move [uris] to the trash ([trash] true) or back out of it. The answer comes
     *  to the activity's onActivityResult with [REQUEST_TRASH] / [REQUEST_RESTORE]. */
    fun requestTrash(activity: Activity, uris: List<Uri>, trash: Boolean): Boolean = launch(activity, if (trash) REQUEST_TRASH else REQUEST_RESTORE) {
        MediaStore.createTrashRequest(activity.contentResolver, uris, trash).intentSender
    }

    /** Asks Android to delete [uris] for good — offered only in the trash. */
    fun requestDelete(activity: Activity, uris: List<Uri>): Boolean = launch(activity, REQUEST_DELETE) {
        MediaStore.createDeleteRequest(activity.contentResolver, uris).intentSender
    }

    private fun launch(activity: Activity, requestCode: Int, sender: () -> IntentSender): Boolean = try {
        activity.startIntentSenderForResult(sender(), requestCode, null, 0, 0, 0)
        true
    } catch (e: Exception) {
        Log.w("G9", "media request $requestCode", e)
        false
    }
}
