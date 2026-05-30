package cl.aiep.subtitulos.ai

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** PKCE (RFC 7636) code pair + OAuth state, used by the "Sign in with ChatGPT" flow. */
data class PkceCodes(
    val codeVerifier: String,
    val codeChallenge: String,
    val state: String,
)

object Pkce {
    private val random = SecureRandom()

    fun generate(): PkceCodes {
        val verifier = base64Url(randomBytes(64))
        val challenge = base64Url(sha256(verifier.toByteArray(Charsets.US_ASCII)))
        val state = base64Url(randomBytes(32))
        return PkceCodes(verifier, challenge, state)
    }

    private fun randomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)

    private fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
