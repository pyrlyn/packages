// The table structure of a TOML document and the rules on which table may be defined or extended where.
package toml

import kotlinx.serialization.json.*

internal sealed interface TomlNode {
    class Leaf(
        val value: JsonPrimitive,
    ) : TomlNode

    /** An array value, `a = [...]`: closed, never extended by `[[a]]`. */
    class Array(
        val items: List<TomlNode>,
    ) : TomlNode

    /** An array of tables, `[[a]]`. */
    class Tables : TomlNode {
        val items = mutableListOf<Table>()
    }

    class Table(
        var origin: Origin,
    ) : TomlNode {
        val entries = LinkedHashMap<String, TomlNode>()
    }

    enum class Origin {
        /** A parent named on the way to a header, `a` of `[a.b]`: a header may still define it once. */
        Implicit,

        /** Defined by its own `[header]`. */
        Header,

        /** Made by a dotted key, `a.b = 1`: further dotted keys extend it, a header of its own may not. */
        Dotted,

        /** An inline table: closed once written. */
        Inline,

        /** One `[[header]]` item. */
        Item,
    }
}

internal class TomlTree(
    private val fail: (String) -> Nothing,
) {
    private val root = TomlNode.Table(TomlNode.Origin.Header)
    private var path = emptyList<String>()
    private val lines = HashMap<String, Int>()

    var current: TomlNode.Table = root
        private set

    fun header(
        key: List<String>,
        array: Boolean,
        line: Int,
    ) {
        var table = root
        key.dropLast(1).forEachIndexed { i, k -> table = descend(table, k, key.take(i + 1)) }
        val name = key.last()
        val existing = table.entries[name]
        current =
            when {
                array -> items(table, name, existing, key)
                existing == null -> TomlNode.Table(TomlNode.Origin.Header).also { table.entries[name] = it }
                existing is TomlNode.Table && existing.origin == TomlNode.Origin.Implicit -> existing.also { it.origin = TomlNode.Origin.Header }
                else -> fail("table `${dotted(key)}` is already defined")
            }
        path = key
        record(key, line)
    }

    private fun items(
        table: TomlNode.Table,
        name: String,
        existing: TomlNode?,
        key: List<String>,
    ): TomlNode.Table {
        val items =
            when (existing) {
                null -> TomlNode.Tables().also { table.entries[name] = it }
                is TomlNode.Tables -> existing
                else -> fail("`${dotted(key)}` is already defined and is not an array of tables")
            }
        return TomlNode.Table(TomlNode.Origin.Item).also { items.items += it }
    }

    private fun descend(
        table: TomlNode.Table,
        name: String,
        key: List<String>,
    ): TomlNode.Table =
        when (val n = table.entries[name]) {
            null -> TomlNode.Table(TomlNode.Origin.Implicit).also { table.entries[name] = it }
            is TomlNode.Tables -> n.items.last()
            is TomlNode.Table -> if (n.origin == TomlNode.Origin.Inline) fail("inline table `${dotted(key)}` cannot be extended") else n
            else -> fail("`${dotted(key)}` is not a table")
        }

    /** Set [key] in [target] (the current table, or an inline table being read); [line] is null inside an inline table. */
    fun put(
        target: TomlNode.Table,
        key: List<String>,
        value: TomlNode,
        line: Int?,
    ) {
        var table = target
        key.dropLast(1).forEachIndexed { i, k ->
            table =
                when (val n = table.entries[k]) {
                    null -> TomlNode.Table(TomlNode.Origin.Dotted).also { table.entries[k] = it }
                    is TomlNode.Table -> if (n.origin == TomlNode.Origin.Dotted) n else fail("`${dotted(key.take(i + 1))}` cannot be extended by a dotted key")
                    else -> fail("`${dotted(key.take(i + 1))}` is not a table")
                }
        }
        if (key.last() in table.entries) fail("duplicate key `${dotted(key)}`")
        table.entries[key.last()] = value
        if (line != null) record(path + key, line)
    }

    private fun record(
        key: List<String>,
        line: Int,
    ) {
        for (n in 1..key.size) lines.getOrPut(key.take(n).joinToString(".")) { line }
    }

    fun document(): TomlDocument = Doc(json(root) as JsonObject, lines)

    private fun dotted(key: List<String>) = key.joinToString(".") { TomlStrings.key(it) }

    private class Doc(
        override val root: JsonObject,
        private val lines: Map<String, Int>,
    ) : TomlDocument {
        override fun lineOf(dotted: String) = lines[dotted] ?: 1
    }

    companion object {
        fun json(node: TomlNode): JsonElement =
            when (node) {
                is TomlNode.Leaf -> node.value
                is TomlNode.Array -> JsonArray(node.items.map(::json))
                is TomlNode.Tables -> JsonArray(node.items.map(::json))
                is TomlNode.Table -> JsonObject(node.entries.mapValues { json(it.value) })
            }
    }
}
