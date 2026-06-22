package app.pebo.auth

import android.net.Uri
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Single-flight bridge between the Custom Tab OAuth redirect (delivered to [OAuthRedirectActivity])
 * and the suspending [AndroidOAuthSignIn] flow — the Android analogue of the desktop loopback
 * [DesktopOAuthRedirectReceiver]. [await] launches the authorization request and suspends until the
 * browser redirects back to the app's custom scheme; [deliver] parses that redirect URI into an
 * [OAuthRedirectResult] and resumes the waiter.
 */
object OAuthRedirectBus {
    private var pending: CancellableContinuation<OAuthRedirectResult>? = null

    suspend fun await(timeoutMillis: Long, launch: () -> Unit): OAuthRedirectResult =
        withTimeout(timeoutMillis) {
            suspendCancellableCoroutine { cont ->
                resolve(null)
                pending = cont
                cont.invokeOnCancellation { if (pending === cont) pending = null }
                launch()
            }
        }

    fun deliver(uri: Uri) {
        resolve(parse(uri))
    }

    private fun resolve(result: OAuthRedirectResult?) {
        val cont = pending
        pending = null
        if (result != null && cont != null && cont.isActive) cont.resume(result)
    }

    private fun parse(uri: Uri): OAuthRedirectResult {
        val error = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        return when {
            error != null -> OAuthRedirectResult.Error(
                error = error,
                description = uri.getQueryParameter("error_description"),
                state = state,
            )
            code != null && state != null -> OAuthRedirectResult.Code(code = code, state = state)
            else -> OAuthRedirectResult.Error(
                error = "invalid_redirect",
                description = "OAuth redirect did not contain code/state or error.",
                state = state,
            )
        }
    }
}
