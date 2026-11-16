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

/**
 * Matches shared content against script trigger headers, following the
 * userscript conventions established by Greasemonkey/Tampermonkey:
 *
 * - `// @match scheme://host/path` - URL-style pattern applied to the shared
 *   link. `*` in the scheme position matches any scheme.
 * - `// @include pattern` - glob (or /regex/) pattern applied to the raw
 *   shared content, whatever the share carries: prose including any link,
 *   plain text, or just the link for link-only shares.
 * - `// @exclude pattern` - glob (or /regex/) that vetoes the script even
 *   when a match/include hit.
 *
 * A script is selected for a share when at least one match/include rule hits
 * and no exclude rule does. Scripts without trigger headers are never run
 * from the share sheet - matching is an explicit opt-in.
 */
object ShareScriptMatcher {

    fun matches(payload: ShareIntentPayload, scriptCode: String): Boolean =
        matches(PatternEvaluator(payload), parseTriggers(scriptCode))

    internal fun matches(evaluator: PatternEvaluator, triggers: ScriptTriggers): Boolean {
        if (triggers.excludes.any { evaluator.matches(it, urlOnly = false) }) return false
        if (triggers.matches.any { evaluator.matches(it, urlOnly = true) }) return true
        if (triggers.includes.any { evaluator.matches(it, urlOnly = false) }) return true
        return false
    }

    internal fun parseTriggers(source: String): ScriptTriggers {
        val metadata = ScriptMetadataParser.parse(source)
        return ScriptTriggers(
            matches = metadata.match,
            includes = metadata.include,
            excludes = metadata.exclude
        )
    }

    internal data class ScriptTriggers(
        val matches: List<String>,
        val includes: List<String>,
        val excludes: List<String>
    )

    /**
     * The evaluation context for one incoming share. Each pattern tier has
     * its own subject, so the semantics are easy to state:
     *
     * - @match: the link in the share (extracted from prose when needed),
     *   in URL grammar. No link, no @match hit.
     * - @include/@exclude: the raw shared content, words and link together,
     *   as glob or /regex/. No implicit link preference.
     */
    internal class PatternEvaluator(
        payload: ShareIntentPayload
    ) {
        private val url: String?
        private val content: String

        init {
            val link = (payload.url ?: ShareIntentPayload.extractUrl(payload.text))
                ?.substringBefore('#')
            url = link?.let { withDefaultPath(it) }
            content = payload.text?.takeIf { it.isNotBlank() }
                ?: link
                ?: ""
        }

        // A URL without a path implicitly has the root path (Chrome/Tampermonkey
        // semantics), so a bare host gets one appended before matching.
        private fun withDefaultPath(url: String): String {
            val hostStart = url.indexOf("://").takeIf { it >= 0 }?.plus(3) ?: return url
            return if (url.indexOf('/', hostStart) == -1) "$url/" else url
        }

        fun matches(pattern: String, urlOnly: Boolean): Boolean {
            if (pattern.isBlank()) return false

            if (urlOnly) {
                if (!pattern.contains("://")) return false
                return url?.let { wildcardToRegex(pattern, isMatch = true).containsMatchIn(it) }
                    ?: false
            }

            if (content.isBlank()) return false

            // /regex/ patterns are @include/@exclude syntax only.
            if (pattern.length >= 2 && pattern.startsWith("/") && pattern.endsWith("/")) {
                val body = pattern.substring(1, pattern.length - 1)
                return runCatching {
                    Regex(body).containsMatchIn(content)
                }.getOrDefault(false)
            }

            return wildcardToRegex(pattern, isMatch = false).containsMatchIn(content)
        }
    }

    /**
     * Turns a `@match` or `@include` pattern into a full-match regex over the
     * target string, both case-insensitive. For @match the pattern keeps its
     * URL grammar (scheme://host/path); for @include it is a glob.
     */
    private fun wildcardToRegex(pattern: String, isMatch: Boolean): Regex {
        val source = if (isMatch) {
            buildMatchRegexSource(pattern)
        } else {
            "(?i)^(?:${buildGlobRegexSource(pattern)})$"
        }
        return runCatching { Regex(source) }.getOrElse { Regex("(?i)" + Regex.escape(pattern)) }
    }

    /**
     * Greasemonkey @match semantics:
     * - `<scheme>://<host><path>`
     * - host `*` matches everything; `*.example.com` matches example.com and
     *   its subdomains
     * - path is a glob; when omitted it matches any path
     */
    private fun buildMatchRegexSource(pattern: String): String {
        val separatorIndex = pattern.indexOf("://")

        val scheme = pattern.substring(0, separatorIndex)
        val rest = pattern.substring(separatorIndex + 3)
        val pathStart = rest.indexOf('/')
        val host = if (pathStart >= 0) rest.substring(0, pathStart) else rest
        val path = if (pathStart >= 0) rest.substring(pathStart) else ""

        // DroidWright deviation from Tampermonkey: a '*' scheme matches any
        // protocol, not just http/https, so custom app links (obsidian://,
        // spotify://, ...) can be targeted from the share sheet.
        val schemePart = when {
            scheme.equals("*", ignoreCase = true) -> "[A-Za-z][A-Za-z0-9+.-]*"
            else -> Regex.escape(scheme.lowercase())
        }

        val hostPart = when {
            host == "*" -> "[^/?\\s]*"
            host.startsWith("*.") -> "(?:[^/?\\s]+\\.)?" + Regex.escape(host.substring(2))
            else -> Regex.escape(host)
        }

        val pathPart = if (path.isBlank()) ".*" else buildGlobRegexSource(path)

        return "^(?i:$schemePart://$hostPart)$pathPart$"
    }

    /**
     * Glob-body semantics for @include/@exclude: `*` matches anything,
     * everything else is literal. Returns an un-anchored body; callers add
     * ^/$ as needed.
     */
    private fun buildGlobRegexSource(pattern: String): String = buildString {
        pattern.forEach { char ->
            when (char) {
                '*' -> append(".*")
                in "\\()[].{}$^|+?" -> append('\\').append(char)
                else -> append(char)
            }
        }
    }
}
