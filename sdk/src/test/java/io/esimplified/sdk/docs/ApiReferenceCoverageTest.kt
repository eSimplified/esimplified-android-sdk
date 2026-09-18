package io.esimplified.sdk.docs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiReferenceCoverageTest {

    // region Fixtures

    private data class Param(val name: String, val type: String, val default: String?) {
        override fun toString(): String = "$name: $type" + (default?.let { " = $it" } ?: "")
    }

    private data class Method(val repository: String, val name: String)

    private val repoRoot: File = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .firstOrNull { File(it, REFERENCE).isFile }
        ?: error("Could not find $REFERENCE above ${System.getProperty("user.dir")}")

    private val reference: String = File(repoRoot, REFERENCE).readText()

    private val sourceDir = File(repoRoot, "sdk/src/main/java/io/esimplified/sdk/repository")

    // endregion

    // region Parsing

    private fun matchingClose(text: String, openIndex: Int, open: Char, close: Char): Int {
        var depth = 0
        var i = openIndex
        while (i < text.length) {
            if (text[i] == open) depth++
            if (text[i] == close) {
                depth--
                if (depth == 0) return i
            }
            i++
        }
        error("Unbalanced $open in source")
    }

    private fun splitTopLevel(text: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var depth = 0
        var prev = ' '
        for (ch in text) {
            if (ch == '(' || ch == '[' || ch == '{' || (ch == '<' && prev != '-')) depth++
            if (ch == ')' || ch == ']' || ch == '}' || (ch == '>' && prev != '-')) depth--
            prev = ch
            if (ch == ',' && depth == 0) {
                out += buf.toString()
                buf.clear()
            } else {
                buf.append(ch)
            }
        }
        if (buf.isNotBlank()) out += buf.toString()
        return out
    }

    private fun parseParams(text: String): List<Param> = splitTopLevel(text)
        .map { it.replace(Regex("//[^\n]*"), "").split(Regex("\\s+")).filter { p -> p.isNotBlank() }.joinToString(" ") }
        .filter { it.isNotBlank() }
        .map { raw ->
            val name = raw.substringBefore(':').trim()
            val rest = raw.substringAfter(':', "")
            Param(
                name,
                rest.substringBefore('=').trim(),
                rest.substringAfter('=', "").trim().ifBlank { null },
            )
        }

    private fun sourceMethods(): Map<Method, List<Param>> {
        val found = linkedMapOf<Method, List<Param>>()
        val files = sourceDir.listFiles { f: File -> f.isFile && f.name.endsWith(".kt") }.orEmpty()
        for (file in files.sortedBy { it.name }) {
            val src = file.readText()
            for (header in Regex("""(?m)^(?:public )?interface\s+(\w+Repository)\s*\{""").findAll(src)) {
                val repo = header.groupValues[1]
                val bodyEnd = matchingClose(src, src.indexOf('{', header.range.first), '{', '}')
                val body = src.substring(header.range.last + 1, bodyEnd)
                for (fn in Regex("""(?m)^ {4}(?:suspend\s+)?fun\s+(\w+)\s*\(""").findAll(body)) {
                    val open = body.indexOf('(', fn.range.first)
                    val close = matchingClose(body, open, '(', ')')
                    val key = Method(repo, fn.groupValues[1])
                    assertTrue(
                        "$repo.${fn.groupValues[1]} is declared more than once. Overloads are not " +
                            "documentable as one row — give the extra parameter a default instead.",
                        !found.containsKey(key),
                    )
                    found[key] = parseParams(body.substring(open + 1, close))
                }
            }
        }
        return found
    }

    private fun section(repo: String): String {
        val header = Regex("""(?m)^### $repo\s*$""").find(reference)
            ?: error("$REFERENCE has no '### $repo' section")
        val rest = reference.substring(header.range.last + 1)
        val next = Regex("""(?m)^(### |## )""").find(rest)
        return if (next == null) rest else rest.substring(0, next.range.first)
    }

    private fun documentedRows(repo: String): Map<String, List<Param>> {
        val rows = linkedMapOf<String, List<Param>>()
        for (row in Regex("""(?m)^\|\s*`(\w+)`\s*\|([^|]*)\|""").findAll(section(repo))) {
            val cell = row.groupValues[2].replace("`", "").trim()
            val params = if (cell == "—" || cell == "-" || cell.isBlank()) emptyList() else parseParams(cell)
            assertTrue(
                "$repo.${row.groupValues[1]} has more than one row in $REFERENCE. " +
                    "Every method gets exactly one row.",
                !rows.containsKey(row.groupValues[1]),
            )
            rows[row.groupValues[1]] = params
        }
        return rows
    }

    private fun documentedResultTwins(): Set<Method> =
        Regex("""(?m)^\|\s*`(\w+Repository)`\s*\|\s*`(\w+)`\s*\|""")
            .findAll(reference)
            .map { Method(it.groupValues[1], it.groupValues[2]) }
            .toSet()

    // endregion

    // region Tests

    @Test
    fun `every repository method has exactly one row in the API reference`() {
        val source = sourceMethods()
        val repositories = source.keys.map { it.repository }.distinct()
        val documented = mutableSetOf<Method>()
        for (repo in repositories) {
            documentedRows(repo).keys.forEach { documented += Method(repo, it) }
        }
        documented += documentedResultTwins()

        val undocumented = (source.keys - documented).sortedBy { "${it.repository}.${it.name}" }
        val phantom = (documented - source.keys).sortedBy { "${it.repository}.${it.name}" }

        assertEquals(
            "Methods in the SDK that $REFERENCE does not document: " +
                undocumented.joinToString { "${it.repository}.${it.name}" },
            emptyList<Method>(),
            undocumented,
        )
        assertEquals(
            "Methods $REFERENCE documents that the SDK does not have: " +
                phantom.joinToString { "${it.repository}.${it.name}" },
            emptyList<Method>(),
            phantom,
        )
    }

    @Test
    fun `documented parameter lists match the declarations`() {
        val source = sourceMethods()
        val mismatches = mutableListOf<String>()
        for (repo in source.keys.map { it.repository }.distinct()) {
            val rows = documentedRows(repo)
            for ((name, documentedParams) in rows) {
                val declared = source[Method(repo, name)] ?: continue
                if (declared != documentedParams) {
                    mismatches += "$repo.$name\n" +
                        "      declared:   ${declared.joinToString()}\n" +
                        "      documented: ${documentedParams.joinToString()}"
                }
            }
        }
        assertEquals(
            "Parameter lists in $REFERENCE that no longer match the SDK:\n   " +
                mismatches.joinToString("\n   "),
            emptyList<String>(),
            mismatches,
        )
    }

    // endregion

    private companion object {
        const val REFERENCE = "SDK_API_REFERENCE.md"
    }
}
