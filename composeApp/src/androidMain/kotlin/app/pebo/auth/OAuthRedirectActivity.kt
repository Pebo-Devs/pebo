package app.pebo.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Receives the OAuth redirect on the app's custom scheme (declared with an intent-filter in the
 * manifest), hands the URI to [OAuthRedirectBus] to resume the suspended [AndroidOAuthSignIn] flow,
 * and finishes immediately — the Android counterpart of the desktop loopback receiver. `singleTask`
 * launch mode means an existing instance receives the redirect via [onNewIntent].
 */
class OAuthRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { OAuthRedirectBus.deliver(it) }
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { OAuthRedirectBus.deliver(it) }
        finish()
    }
}
