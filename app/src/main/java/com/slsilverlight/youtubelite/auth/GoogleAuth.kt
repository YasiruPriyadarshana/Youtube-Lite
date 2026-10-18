package com.slsilverlight.youtubelite.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Google sign-in for read-only YouTube access.
 *
 * Nothing secret lives in the app: Google recognises it by its package name and the SHA-1 of
 * the signing key, which you register as an Android OAuth client in Google Cloud (see README).
 */
class GoogleAuth(private val context: Context) {

    sealed interface Outcome {
        data class SignedIn(val accessToken: String) : Outcome
        data class NeedsConsent(val intent: PendingIntent) : Outcome
        data class Failed(val message: String) : Outcome
    }

    private val client = Identity.getAuthorizationClient(context)

    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(YOUTUBE_READONLY)))
        .build()

    /** Gets a fresh access token, or the consent screen to show if the user hasn't signed in yet. */
    suspend fun authorize(): Outcome = try {
        toOutcome(client.authorize(request).await())
    } catch (e: Exception) {
        Outcome.Failed(e.message ?: "Sign-in failed")
    }

    /** Reads the result after the consent screen closes. */
    fun handleConsentResult(data: Intent?): Outcome = try {
        toOutcome(client.getAuthorizationResultFromIntent(data))
    } catch (e: Exception) {
        Outcome.Failed(e.message ?: "Sign-in was cancelled")
    }

    /** Revokes the token so the next sign-in shows the account picker again. */
    suspend fun signOut(accessToken: String) = withContext(Dispatchers.IO) {
        runCatching {
            val token = URLEncoder.encode(accessToken, "UTF-8")
            val conn = URL("https://oauth2.googleapis.com/revoke?token=$token").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.doOutput = true
            conn.outputStream.close()
            conn.responseCode
            conn.disconnect()
        }
    }

    private fun toOutcome(result: AuthorizationResult): Outcome {
        val pending = result.pendingIntent
        val token = result.accessToken
        return when {
            result.hasResolution() && pending != null -> Outcome.NeedsConsent(pending)
            token != null -> Outcome.SignedIn(token)
            else -> Outcome.Failed("Google didn't return an access token")
        }
    }

    private companion object {
        const val YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"
    }
}
