package app.pebo.platform

import android.app.Activity
import java.lang.ref.WeakReference

/**
 * Process-wide weak handle to the current foreground [Activity], so top-level platform `actual`
 * functions (folder picker, save dialogs) can reach an Activity/Context without threading one
 * through the shared call sites. Set in `MainActivity.onCreate` and cleared in `onDestroy`.
 */
object CurrentActivityHolder {
    private var ref: WeakReference<Activity>? = null

    fun set(activity: Activity) {
        ref = WeakReference(activity)
    }

    fun clear(activity: Activity) {
        if (ref?.get() === activity) ref = null
    }

    fun get(): Activity? = ref?.get()
}
