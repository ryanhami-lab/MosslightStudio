package com.ryanhami.mosslightstudio.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ryanhami.mosslightstudio.core.Cell
import com.ryanhami.mosslightstudio.core.DecorItem
import com.ryanhami.mosslightstudio.core.StudioLayout
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

class StudioSaveCodec {
    private val gson = Gson()

    fun decode(bytes: ByteArray): StudioSave = decodeDetailed(bytes).save

    fun encode(save: StudioSave): ByteArray {
        require(save.schema == StudioSave.CURRENT_SCHEMA) { "Only current studio saves can be encoded." }
        val root = JsonObject().apply {
            addProperty("schema", StudioSave.CURRENT_SCHEMA)
            add("placements", JsonArray().apply {
                DecorItem.entries.forEach { item ->
                    save.layout.placements[item]?.let { cell ->
                        add(JsonObject().apply {
                            addProperty("item", item.id)
                            addProperty("column", cell.column)
                            addProperty("row", cell.row)
                        })
                    }
                }
            })
        }
        return gson.toJson(root).toByteArray(Charsets.UTF_8)
    }

    internal fun decodeDetailed(bytes: ByteArray): DecodedSave {
        if (bytes.isEmpty()) throw SaveReadException(SaveProblem.UNREADABLE)
        try {
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
            val root = JsonParser.parseString(text).requiredObject()
            val schema = root.requiredInt("schema")
            if (schema > StudioSave.CURRENT_SCHEMA) {
                throw SaveReadException(SaveProblem.FUTURE_VERSION)
            }
            val placements = when (schema) {
                1 -> decodeVersionOne(root)
                StudioSave.CURRENT_SCHEMA -> decodeVersionTwo(root)
                else -> throw SaveReadException(SaveProblem.UNREADABLE)
            }
            return DecodedSave(
                save = StudioSave(StudioLayout(placements.toMap())),
                sourceSchema = schema,
            )
        } catch (expected: SaveReadException) {
            throw expected
        } catch (_: Exception) {
            throw SaveReadException(SaveProblem.UNREADABLE)
        }
    }

    private fun decodeVersionOne(root: JsonObject): Map<DecorItem, Cell> =
        decodePlacements(root.requiredArray("decor"), "id", "x", "y")

    private fun decodeVersionTwo(root: JsonObject): Map<DecorItem, Cell> =
        decodePlacements(root.requiredArray("placements"), "item", "column", "row")

    private fun decodePlacements(
        array: JsonArray,
        idKey: String,
        columnKey: String,
        rowKey: String,
    ): Map<DecorItem, Cell> {
        val placements = linkedMapOf<DecorItem, Cell>()
        array.forEach { element ->
            val entry = element.requiredObject()
            val id = entry.requiredString(idKey)
            val item = DecorItem.entries.singleOrNull { it.id == id }
                ?: throw SaveReadException(SaveProblem.UNREADABLE)
            if (placements.containsKey(item)) throw SaveReadException(SaveProblem.UNREADABLE)
            placements[item] = Cell(
                column = entry.requiredInt(columnKey),
                row = entry.requiredInt(rowKey),
            )
        }
        return placements
    }

    private fun JsonObject.requiredArray(name: String): JsonArray {
        val element = get(name) ?: throw SaveReadException(SaveProblem.UNREADABLE)
        if (!element.isJsonArray) throw SaveReadException(SaveProblem.UNREADABLE)
        return element.asJsonArray
    }

    private fun JsonObject.requiredString(name: String): String {
        val element = get(name) ?: throw SaveReadException(SaveProblem.UNREADABLE)
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            throw SaveReadException(SaveProblem.UNREADABLE)
        }
        return element.asString
    }

    private fun JsonObject.requiredInt(name: String): Int {
        val element = get(name) ?: throw SaveReadException(SaveProblem.UNREADABLE)
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            throw SaveReadException(SaveProblem.UNREADABLE)
        }
        val literal = element.asJsonPrimitive.toString()
        if (!INTEGER.matches(literal)) throw SaveReadException(SaveProblem.UNREADABLE)
        return literal.toIntOrNull() ?: throw SaveReadException(SaveProblem.UNREADABLE)
    }

    private fun JsonElement.requiredObject(): JsonObject {
        if (!isJsonObject) throw SaveReadException(SaveProblem.UNREADABLE)
        return asJsonObject
    }

    internal data class DecodedSave(
        val save: StudioSave,
        val sourceSchema: Int,
    )

    private companion object {
        val INTEGER = Regex("-?(0|[1-9][0-9]*)")
    }
}
