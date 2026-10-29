// TOML text the way Rust's `toml` 1.1 writes it (`to_string_pretty` and `Value`'s Display, over `toml_writer` 1.1),
// so a file one program writes is byte for byte the file the other would have written.
package toml

import kotlinx.serialization.json.*

object TomlWriter {
    /**
     * A whole document: each table's plain keys, then its tables and arrays of tables in key order, depth first.
     * A table holding only tables gets no header of its own, arrays of two or more items take one line per item,
     * and a null is left out, as Rust leaves out a `None`.
     */
    fun document(root: JsonObject): String {
        val tables = mutableListOf<Pair<String?, String>>()
        table(root, null, false, tables)
        return tables.joinToString("\n") { (header, body) -> header?.let { "$it\n$body" } ?: body }
    }

    /** One value on one line (Rust `Value::to_string`): arrays and tables inline. */
    fun value(v: JsonElement): String = buildString { value(v, false, this) }

    /** A string in the form `toml_writer` picks: basic when nothing needs escaping, else literal, else multi-line. */
    fun string(s: String): String = TomlStrings.string(s)

    /** A key: bare when it can be, else a basic string, else a literal one, else an escaped basic string. */
    fun key(s: String): String = TomlStrings.key(s)

    private fun table(
        obj: JsonObject,
        path: List<String>?,
        array: Boolean,
        out: MutableList<Pair<String?, String>>,
    ) {
        val at = out.size
        out += null to ""
        val body = StringBuilder()
        var children = false
        for ((k, v) in obj) {
            val key = key(k)
            when {
                // Rust leaves out a `None`.
                v is JsonNull -> {
                    continue
                }

                v is JsonObject -> {
                    children = true
                    table(v, path.orEmpty() + key, false, out)
                }

                isArrayOfTables(v) -> {
                    children = true
                    (v as JsonArray).forEach { table(it as JsonObject, path.orEmpty() + key, true, out) }
                }

                else -> {
                    body.append(key).append(" = ")
                    value(v, true, body)
                    body.append('\n')
                }
            }
        }
        // Rust drops a header that says nothing: the root when empty, a table with only tables under it.
        val required = if (path == null) body.isNotEmpty() else array || body.isNotEmpty() || !children
        if (!required) {
            out.removeAt(at)
            return
        }
        val header = path?.joinToString(".")?.let { if (array) "[[$it]]" else "[$it]" }
        out[at] = header to body.toString()
    }

    private fun isArrayOfTables(v: JsonElement) = v is JsonArray && v.isNotEmpty() && v.all { it is JsonObject }

    private fun value(
        v: JsonElement,
        pretty: Boolean,
        out: StringBuilder,
    ) {
        when (v) {
            is JsonNull -> out.append("\"\"")
            is JsonPrimitive -> out.append(primitive(v))
            is JsonArray -> array(v, pretty, out)
            is JsonObject -> inlineTable(v, pretty, out)
        }
    }

    private fun array(
        a: JsonArray,
        pretty: Boolean,
        out: StringBuilder,
    ) {
        val multiline = pretty && a.size >= 2
        out.append('[')
        a.forEachIndexed { i, item ->
            if (multiline) {
                out.append("\n    ")
            } else if (i > 0) {
                out.append(", ")
            }
            value(item, pretty, out)
            if (multiline) out.append(',')
        }
        if (multiline) out.append('\n')
        out.append(']')
    }

    private fun inlineTable(
        o: JsonObject,
        pretty: Boolean,
        out: StringBuilder,
    ) {
        val entries = o.filterValues { it !is JsonNull }
        if (entries.isEmpty()) {
            out.append("{}")
            return
        }
        out.append("{ ")
        entries.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) out.append(", ")
            out.append(key(k)).append(" = ")
            value(v, pretty, out)
        }
        out.append(" }")
    }

    private fun primitive(p: JsonPrimitive): String =
        when {
            p.isString -> string(p.content)
            TomlValues.isBool(p) -> p.content
            TomlValues.isInteger(p) -> p.long.toString()
            else -> TomlStrings.float(p.double)
        }
}
