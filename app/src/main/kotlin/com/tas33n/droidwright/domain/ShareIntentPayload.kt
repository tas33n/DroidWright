/**
 * Copyright (c) 2026 wi99
 *
 * Licensed under the MIT License
 * See LICENSE file or https://opensource.org/licenses/MIT
 *
 * @author wi99
 * @see <a href="https://github.com/tas33n/droidwright">GitHub</a>
 */
package com.tas33n.droidwright.domain

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat

/**
 * Serializable snapshot of an Intent that triggered a script (e.g. coming in
 * from the Android share sheet), exposed to scripts as ctx.intent.
 */
data class ShareIntentPayload(
    val action: String? = null,
    val mime: String? = null,
    val text: String? = null,
    val subject: String? = null,
    val url: String? = null,
    val referrer: String? = null,
    val extras: Map<String, String> = emptyMap()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "action" to action,
        "mime" to mime,
        "text" to text,
        "subject" to subject,
        "url" to url,
        "referrer" to referrer,
        "extras" to extras
    )

    companion object {
        private val urlRegex = Regex("""[A-Za-z][A-Za-z0-9+.-]*://\S+""")
        private val intentExtrasToSkipOnAndroid = setOf(
            Intent.EXTRA_TEXT,
            Intent.EXTRA_SUBJECT,
            Intent.EXTRA_STREAM,
            Intent.EXTRA_REFERRER_NAME
        )

        val EMPTY = ShareIntentPayload()

        /**
         * Extracts the first URL from a shared text payload. Applies to both
         * fromIntent() normalization and runtime pattern matching, so shares
         * that carry prose + link still route URL-style @match rules.
         */
        fun extractUrl(text: String?): String? = text?.takeIf { it.isNotBlank() }
            ?.let { urlRegex.find(it)?.value }

        /**
         * Normalizes the union of intents that can trigger a script through the
         * share sheet (ACTION_SEND text, ACTION_SENDTO/VIEW links) so scripts
         * always see the same fields.
         */
        fun fromIntent(intent: Intent): ShareIntentPayload {
            var text: String? = null
            var subject: String? = null
            var url: String? = null

            when (intent.action) {
                Intent.ACTION_SEND -> {
                    text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    val stream = getParcelableUriExtra(intent, Intent.EXTRA_STREAM)
                    if (stream != null) {
                        url = stream.toString()
                    }
                }
                Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> url = intent.dataString
            }

            if (url.isNullOrBlank()) {
                url = extractUrl(text)
            }

            val extras = intent.extras?.let { bundle ->
                bundle.keySet()
                    .filter { it !in intentExtrasToSkipOnAndroid }
                    .mapNotNull { key -> bundle.readPrimitiveExtra(key)?.let { key to it } }
                    .toMap()
            } ?: emptyMap()

            return ShareIntentPayload(
                action = intent.action,
                mime = intent.type,
                text = text?.takeIf { it.isNotBlank() },
                subject = subject?.takeIf { it.isNotBlank() },
                url = url?.takeIf { it.isNotBlank() },
                referrer = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME),
                extras = extras
            )
        }

        private fun getParcelableUriExtra(intent: Intent, key: String): Uri? =
            IntentCompat.getParcelableExtra(intent, key, Uri::class.java)

        private fun Bundle.readPrimitiveExtra(key: String): String? {
            getString(key)?.let { return it }
            if (getBoolean(key, false) == getBoolean(key, true)) {
                return getBoolean(key, false).toString()
            }
            if (getInt(key, 0) == getInt(key, 1)) {
                return getInt(key, 0).toString()
            }
            if (getLong(key, 0L) == getLong(key, 1L)) {
                return getLong(key, 0L).toString()
            }
            if (getDouble(key, 0.0) == getDouble(key, 1.0)) {
                return getDouble(key, 0.0).toString()
            }
            return null
        }
    }
}
