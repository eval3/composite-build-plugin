package com.eval.cbm.core

import com.eval.cbm.model.DepSubstitution

data class CbmState(
    val modules: List<CbmStateModule> = emptyList(),
    val customModules: List<CbmStateModule> = emptyList(),
    val updatedAt: String = ""
)

data class CbmStateModule(
    val name: String,
    val path: String = "",
    val substitutions: List<DepSubstitution> = emptyList()
)

/** Strict JSON codec for the generated state file, with legacy `deps` read compatibility. */
object CbmStateCodec {
    fun encode(state: CbmState): String = buildString {
        appendLine("{")
        appendModules("modules", state.modules, true)
        appendModules("customModules", state.customModules, true)
        appendLine("  \"updatedAt\": ${quote(state.updatedAt)}")
        appendLine("}")
    }

    fun decode(text: String): CbmState {
        val root = Parser(text).parseObject()
        val modules = root.array("modules").mapNotNull(::decodeModule)
        val customModules = root.array("customModules").mapNotNull(::decodeModule)
        val legacyEnabled = root.array("enabledModules").mapNotNull { it as? String }
        return CbmState(
            modules = if (modules.isNotEmpty() || root.containsKey("modules")) modules
                else legacyEnabled.map { CbmStateModule(it) },
            customModules = customModules,
            updatedAt = root.string("updatedAt")
        )
    }

    private fun decodeModule(value: Any?): CbmStateModule? {
        val obj = value as? Map<*, *> ?: return null
        val name = obj["name"] as? String ?: return null
        val structured = (obj["substitutions"] as? List<*>)
            .orEmpty()
            .mapNotNull { item ->
                val rule = item as? Map<*, *> ?: return@mapNotNull null
                val module = rule["module"] as? String ?: return@mapNotNull null
                val project = rule["project"] as? String ?: return@mapNotNull null
                if (!isValidModuleCoordinate(module)) return@mapNotNull null
                DepSubstitution(module, normalizeProjectPath(project))
            }
        val legacy = (obj["deps"] as? String)?.let(DepSubstitution::parseList).orEmpty()
            .filter { isValidModuleCoordinate(it.dep) }
        return CbmStateModule(
            name = name,
            path = obj["path"] as? String ?: "",
            substitutions = if (structured.isNotEmpty()) structured else legacy
        )
    }

    private fun StringBuilder.appendModules(
        key: String,
        modules: List<CbmStateModule>,
        trailingComma: Boolean
    ) {
        appendLine("  ${quote(key)}: [")
        modules.forEachIndexed { index, module ->
            appendLine("    {")
            appendLine("      \"name\": ${quote(module.name)},")
            appendLine("      \"path\": ${quote(module.path)},")
            appendLine("      \"substitutions\": [")
            module.substitutions.forEachIndexed { ruleIndex, rule ->
                val comma = if (ruleIndex < module.substitutions.lastIndex) "," else ""
                appendLine("        { \"module\": ${quote(rule.dep)}, \"project\": ${quote(normalizeProjectPath(rule.project))} }$comma")
            }
            appendLine("      ]")
            val comma = if (index < modules.lastIndex) "," else ""
            appendLine("    }$comma")
        }
        appendLine("  ]${if (trailingComma) "," else ""}")
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    private fun normalizeProjectPath(path: String): String =
        if (path.startsWith(":")) path else ":$path"

    private fun isValidModuleCoordinate(module: String): Boolean =
        Regex("""^[\w.-]+:[\w.-]+$""").matches(module)

    private fun Map<String, Any?>.string(key: String): String = this[key] as? String ?: ""
    private fun Map<String, Any?>.array(key: String): List<Any?> = this[key] as? List<Any?> ?: emptyList()

    private class Parser(private val source: String) {
        private var index = 0

        fun parseObject(): Map<String, Any?> {
            skipWhitespace()
            expect('{')
            val result = linkedMapOf<String, Any?>()
            skipWhitespace()
            while (peek() != '}') {
                val key = parseString()
                skipWhitespace(); expect(':')
                result[key] = parseValue()
                skipWhitespace()
                if (peek() == ',') { index++; skipWhitespace() } else break
            }
            expect('}')
            return result
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            return when (peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> { expectWord("true"); true }
                'f' -> { expectWord("false"); false }
                'n' -> { expectWord("null"); null }
                else -> parseNumber()
            }
        }

        private fun parseArray(): List<Any?> {
            expect('['); skipWhitespace()
            val values = mutableListOf<Any?>()
            while (peek() != ']') {
                values += parseValue()
                skipWhitespace()
                if (peek() == ',') { index++; skipWhitespace() } else break
            }
            expect(']')
            return values
        }

        private fun parseString(): String {
            skipWhitespace(); expect('"')
            val result = StringBuilder()
            while (index < source.length) {
                val c = source[index++]
                if (c == '"') return result.toString()
                if (c != '\\') result.append(c) else {
                    val escaped = source[index++]
                    result.append(when (escaped) {
                        '"', '\\', '/' -> escaped
                        'b' -> '\b'; 'f' -> '\u000c'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                        'u' -> source.substring(index, index + 4).toInt(16).toChar().also { index += 4 }
                        else -> error("Invalid JSON escape at $index")
                    })
                }
            }
            error("Unterminated JSON string")
        }

        private fun parseNumber(): Number {
            val start = index
            while (peek()?.let { it.isDigit() || it in "+-.eE" } == true) index++
            val raw = source.substring(start, index)
            return raw.toLongOrNull() ?: raw.toDouble()
        }

        private fun skipWhitespace() { while (peek()?.isWhitespace() == true) index++ }
        private fun peek(): Char? = source.getOrNull(index)
        private fun expect(c: Char) {
            skipWhitespace()
            require(peek() == c) { "Expected '$c' at $index" }
            index++
        }
        private fun expectWord(word: String) {
            require(source.startsWith(word, index)) { "Expected '$word' at $index" }
            index += word.length
        }
    }
}
