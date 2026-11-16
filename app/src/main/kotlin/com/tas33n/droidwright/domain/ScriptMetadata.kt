/**
 * Copyright (c) 2025 tas33n
 *
 * Licensed under the MIT License
 * See LICENSE file or https://opensource.org/licenses/MIT
 *
 * @author tas33n
 * @see <a href="https://github.com/tas33n/droidwright">GitHub</a>
 */
package com.tas33n.droidwright.domain

class ScriptMetadata internal constructor(
    private val values: Map<ScriptMetadata.Key, List<String>>
) {
    enum class Key(val header: String, val required: Boolean, val repeated: Boolean) {
        ID("id", true, false),
        NAME("name", true, false),
        AUTHOR("author", true, false),
        DESCRIPTION("description", true, false),
        VERSION("version", true, false),
        TARGET_APP("targetApp", true, false),
        URL("url", true, false),
        CREATED("created", true, false),
        TAGS("tags", false, false),
        MATCH("match", false, true),
        INCLUDE("include", false, true),
        EXCLUDE("exclude", false, true);

        companion object {
            private val byHeader = entries.associateBy { it.header }

            fun fromHeader(header: String): Key? = byHeader[header]
        }
    }

    val id: String? get() = single(Key.ID)
    val name: String? get() = single(Key.NAME)
    val author: String? get() = single(Key.AUTHOR)
    val description: String? get() = single(Key.DESCRIPTION)
    val version: String? get() = single(Key.VERSION)
    val targetApp: String? get() = single(Key.TARGET_APP)
    val url: String? get() = single(Key.URL)
    val created: String? get() = single(Key.CREATED)
    val tags: String? get() = single(Key.TAGS)
    val match: List<String> get() = list(Key.MATCH)
    val include: List<String> get() = list(Key.INCLUDE)
    val exclude: List<String> get() = list(Key.EXCLUDE)

    fun isEmpty(): Boolean = values.isEmpty()

    fun missingRequired(): List<String> =
        Key.entries.filter { it.required && values[it].isNullOrEmpty() }.map { it.header }

    fun toJsValueMap(): Map<String, Any> = buildMap {
        Key.entries.forEach { key ->
            val value = this@ScriptMetadata.values[key] ?: return@forEach
            if (key.repeated) {
                put(key.header, value.toList())
            } else {
                put(key.header, value.last())
            }
        }
    }

    private fun single(key: Key): String? = values[key]?.lastOrNull()

    private fun list(key: Key): List<String> = values[key].orEmpty()
}

object ScriptMetadataParser {
    private val entryRegex = Regex("""^//\s*@([A-Za-z0-9_\-]+)\s+(.*)$""")

    fun parse(source: String): ScriptMetadata {
        val values = mutableMapOf<ScriptMetadata.Key, MutableList<String>>()
        var insideHeader = false
        source.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.equals("// ==DroidScript==", ignoreCase = true) -> {
                    insideHeader = true
                    return@forEach
                }
                line.equals("// ==/DroidScript==", ignoreCase = true) -> {
                    insideHeader = false
                    return@forEach
                }
                insideHeader && line.startsWith("// @") -> {
                    val headerMatch = entryRegex.find(line) ?: return@forEach
                    val key = ScriptMetadata.Key.fromHeader(headerMatch.groupValues[1]) ?: return@forEach
                    val value = headerMatch.groupValues[2].trim()
                    if (value.isNotEmpty()) {
                        values.getOrPut(key) { mutableListOf() }.add(value)
                    }
                }
            }
        }
        return ScriptMetadata(values)
    }
}
