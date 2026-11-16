package com.tas33n.droidwright.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareScriptMatcherTest {

    private fun payload(
        url: String? = null,
        text: String? = null,
        action: String? = "android.intent.action.SEND"
    ) = ShareIntentPayload(
        action = action,
        mime = "text/plain",
        text = text,
        url = url
    )

    private fun scriptWithHeader(vararg lines: String): String = buildString {
        appendLine("// ==DroidScript==")
        lines.forEach { appendLine(it) }
        appendLine("// ==/DroidScript==")
        appendLine("function droidRun(ctx) { return ctx.intent; }")
    }

    private fun triggers(vararg lines: String) = ShareScriptMatcher.parseTriggers(
        scriptWithHeader(*lines)
    )

    // --- ScriptMetadataParser.parse ---

    @Test
    fun `parse keeps every repeated trigger header`() {
        val source = scriptWithHeader(
            "// @match https://twitter.com/*",
            "// @match https://x.com/*",
            "// @include *example.com*",
            "// @exclude */blog/*"
        )
        val metadata = ScriptMetadataParser.parse(source)
        org.junit.Assert.assertEquals(
            listOf("https://twitter.com/*", "https://x.com/*"),
            metadata.match
        )
        org.junit.Assert.assertEquals(listOf("*example.com*"), metadata.include)
        org.junit.Assert.assertEquals(listOf("*/blog/*"), metadata.exclude)
    }

    @Test
    fun `parse ignores values outside header fences and unknown keys`() {
        val source = scriptWithHeader("// @name Demo", "// @six seven")
        val metadata = ScriptMetadataParser.parse(source)
        org.junit.Assert.assertEquals("Demo", metadata.name)
        org.junit.Assert.assertTrue(metadata.match.isEmpty())
        org.junit.Assert.assertEquals(setOf("name"), metadata.toJsValueMap().keys)
    }

    @Test
    fun `single valued headers keep the last value`() {
        val source = scriptWithHeader("// @name First", "// @name Second")
        val metadata = ScriptMetadataParser.parse(source)
        org.junit.Assert.assertEquals("Second", metadata.name)
    }

    @Test
    fun `header keys are case sensitive like tampermonkey`() {
        val source = scriptWithHeader("// @Name Demo", "// @TargetApp com.example.app")
        val metadata = ScriptMetadataParser.parse(source)
        org.junit.Assert.assertNull(metadata.name)
        org.junit.Assert.assertTrue(metadata.isEmpty())
    }

    @Test
    fun `repeated headers serialize as arrays and single ones as strings`() {
        val source = scriptWithHeader("// @name Demo", "// @match https://a.com/*")
        val jsValues = ScriptMetadataParser.parse(source).toJsValueMap()
        org.junit.Assert.assertEquals("Demo", jsValues["name"])
        org.junit.Assert.assertEquals(
            listOf("https://a.com/*"),
            jsValues["match"]
        )
    }

    @Test
    fun `missingRequired reports all missing fields at once`() {
        val metadata = ScriptMetadataParser.parse(scriptWithHeader("// @name Demo"))
        org.junit.Assert.assertEquals(
            listOf("id", "author", "description", "version", "targetApp", "url", "created"),
            metadata.missingRequired()
        )
    }

    // --- routing rule semantics ---

    @Test
    fun `script without trigger headers is never matched`() {
        val script = scriptWithHeader("// @name Demo")
        assertFalse(ShareScriptMatcher.matches(payload(url = "https://example.com/"), script))
    }

    @Test
    fun `match pattern follows scheme host path grammar`() {
        val script = scriptWithHeader("// @match https://example.com/*")

        assertTrue(ShareScriptMatcher.matches(payload(url = "https://example.com/video/123"), script))
        assertFalse(ShareScriptMatcher.matches(payload(url = "http://example.com/x"), script))
        assertFalse(ShareScriptMatcher.matches(payload(url = "https://evil.com/http://example.com"), script))
    }

    @Test
    fun `match pattern host wildcard matches host and subdomains`() {
        val script = scriptWithHeader("// @match https://*.example.com/*")

        assertTrue(ShareScriptMatcher.matches(payload(url = "https://example.com/a"), script))
        assertTrue(ShareScriptMatcher.matches(payload(url = "https://blog.example.com/a"), script))
        assertFalse(
            ShareScriptMatcher.matches(
                payload(url = "https://notexample.com/a"),
                script
            )
        )
    }

    @Test
    fun `match on multi subdomain wildcards and scheme star`() {
        val script = scriptWithHeader("// @match *://*.example.com/*")
        assertTrue(ShareScriptMatcher.matches(payload(url = "https://a.b.example.com/z"), script))
    }

    @Test
    fun `any host match pattern matches any site`() {
        val script = scriptWithHeader("// @match *://*/*")
        assertTrue(ShareScriptMatcher.matches(payload(url = "https://anything.example/"), script))
        assertTrue(ShareScriptMatcher.matches(payload(url = "http://other.org/x"), script))
    }

    @Test
    fun `match never fires for plain text without a url`() {
        val script = scriptWithHeader("// @match *://*/*")
        assertFalse(ShareScriptMatcher.matches(payload(text = "plain text only"), script))
    }

    @Test
    fun `url is extracted from shared text`() {
        val shared = payload(text = "watch this https://youtu.be/abc nice")
        val script = scriptWithHeader("// @match https://youtu.be/*")
        assertTrue(ShareScriptMatcher.matches(shared, script))

        // The extracted URL is also the match target, not the prose around it.
        org.junit.Assert.assertEquals(
            "https://youtu.be/abc",
            ShareIntentPayload.extractUrl("watch this https://youtu.be/abc nice")
        )
    }

    @Test
    fun `include glob matches text shares`() {
        val script = scriptWithHeader("// @include *password reminder*")
        assertTrue(
            ShareScriptMatcher.matches(
                payload(text = "Here is your password reminder for November"),
                script
            )
        )
        assertFalse(
            ShareScriptMatcher.matches(
                payload(text = "Totally unrelated text"),
                script
            )
        )
    }

    @Test
    fun `include regex syntax is supported`() {
        val script = scriptWithHeader("""// @include /^https:\/\/(www\.)?reddit\.com\//""")
        assertTrue(ShareScriptMatcher.matches(payload(url = "https://www.reddit.com/r/aww"), script))
        assertFalse(ShareScriptMatcher.matches(payload(url = "https://old.reddit.com/info"), script))
    }

    @Test
    fun `match ignores fragment but accounts for query`() {
        val exact = scriptWithHeader("// @match https://example.com/")

        org.junit.Assert.assertFalse(
            ShareScriptMatcher.matches(payload(url = "https://example.com/?abc"), exact)
        )
        org.junit.Assert.assertTrue(
            ShareScriptMatcher.matches(payload(url = "https://example.com/#top"), exact)
        )
        org.junit.Assert.assertFalse(
            ShareScriptMatcher.matches(payload(url = "https://example.com/?abc#top"), exact)
        )
    }

    @Test
    fun `regex syntax is not valid for match patterns`() {
        val script = scriptWithHeader("// @match /example/")
        assertFalse(ShareScriptMatcher.matches(payload(url = "https://example.com/"), script))
    }

    @Test
    fun `include glob matches embedded urls in query strings`() {
        val script = scriptWithHeader("// @include *://tmnk.net/*")
        assertTrue(
            ShareScriptMatcher.matches(
                payload(url = "https://example.com/?redirect=https://tmnk.net/x"),
                script
            )
        )
    }

    @Test
    fun `include is a pure glob over the raw shared content`() {
        val shared = payload(text = "stuff https://google.com")

        assertTrue(ShareScriptMatcher.matches(shared, scriptWithHeader("// @match https://google.com/*")))
        assertFalse(ShareScriptMatcher.matches(shared, scriptWithHeader("// @include https://google.com")))
        assertTrue(ShareScriptMatcher.matches(shared, scriptWithHeader("// @include *https://google.com*")))
        assertTrue(ShareScriptMatcher.matches(shared, scriptWithHeader("// @include *stuff*")))
    }

    @Test
    fun `bare link shares still match plain include patterns`() {
        val shared = payload(url = "https://google.com")

        assertTrue(ShareScriptMatcher.matches(shared, scriptWithHeader("// @include https://google.com")))
        assertTrue(ShareScriptMatcher.matches(shared, scriptWithHeader("// @match https://google.com/*")))
    }

    @Test
    fun `exclude vetoes even when include hits`() {
        val script = scriptWithHeader(
            "// @include *example.com*",
            "// @exclude */blog/*"
        )
        assertTrue(ShareScriptMatcher.matches(payload(url = "https://example.com/page"), script))
        assertFalse(ShareScriptMatcher.matches(payload(url = "https://example.com/blog/post-1"), script))
    }

    @Test
    fun `repeated match lines are all honored`() {
        val script = scriptWithHeader(
            "// @match https://twitter.com/*",
            "// @match https://x.com/*"
        )
        assertTrue(ShareScriptMatcher.matches(payload(url = "https://twitter.com/user/status/1"), script))
        assertTrue(ShareScriptMatcher.matches(payload(url = "https://x.com/user/status/1"), script))
        assertFalse(ShareScriptMatcher.matches(payload(url = "https://mastodon.social/x"), script))
    }
}
