package com.ryanhami.mosslightstudio.data

import com.ryanhami.mosslightstudio.core.Cell
import com.ryanhami.mosslightstudio.core.DecorItem
import com.ryanhami.mosslightstudio.core.StudioLayout
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioSaveCodecTest {
    private val codec = StudioSaveCodec()

    @Test
    fun currentSaveRoundTripsWithDeterministicItemOrdering() {
        val save = StudioSave(
            StudioLayout(
                linkedMapOf(
                    DecorItem.TERRARIUM to Cell(9, 13),
                    DecorItem.FERN to Cell(2, 7),
                    DecorItem.LANTERN to Cell(12, 10),
                ),
            ),
        )

        val first = codec.encode(save)
        val second = codec.encode(save)

        assertArrayEquals(first, second)
        assertEquals(save, codec.decode(first))
        val text = first.toString(StandardCharsets.UTF_8)
        assertTrue(text.indexOf("fern") < text.indexOf("lantern"))
        assertTrue(text.indexOf("lantern") < text.indexOf("terrarium"))
    }

    @Test
    fun syntheticVersionOneFixtureMigratesToCurrentSchema() {
        val versionOne = """
            {
              "schema": 1,
              "decor": [
                {"id":"fern","x":4,"y":8},
                {"id":"stool","x":7,"y":12}
              ]
            }
        """.trimIndent().toByteArray()

        val migrated = codec.decode(versionOne)

        assertEquals(StudioSave.CURRENT_SCHEMA, migrated.schema)
        assertEquals(
            mapOf(
                DecorItem.FERN to Cell(4, 8),
                DecorItem.STOOL to Cell(7, 12),
            ),
            migrated.layout.placements,
        )
    }

    @Test
    fun futureSchemaHasTypedFailureWithoutRawContentInTheMessage() {
        val marker = "future-payload-marker"
        val failure = decodeFailure("""{"schema":99,"placements":[],"note":"$marker"}""")

        assertEquals(SaveProblem.FUTURE_VERSION, failure.kind)
        assertFalse(failure.message.orEmpty().contains(marker))
        assertNull(failure.cause)
    }

    @Test
    fun unknownItemsDuplicatesAndInvalidGeometryAreUnreadable() {
        assertEquals(
            SaveProblem.UNREADABLE,
            decodeFailure("""{"schema":2,"placements":[{"item":"unknown","column":2,"row":7}]}""").kind,
        )
        assertEquals(
            SaveProblem.UNREADABLE,
            decodeFailure(
                """{"schema":2,"placements":[{"item":"fern","column":2,"row":7},{"item":"fern","column":3,"row":7}]}""",
            ).kind,
        )
        assertEquals(
            SaveProblem.UNREADABLE,
            decodeFailure(
                """{"schema":2,"placements":[{"item":"fern","column":2,"row":7},{"item":"bloom","column":2,"row":7}]}""",
            ).kind,
        )
        assertEquals(
            SaveProblem.UNREADABLE,
            decodeFailure("""{"schema":2,"placements":[{"item":"fern","column":1,"row":7}]}""").kind,
        )
    }

    @Test
    fun malformedUtf8FractionalCoordinatesAndTrailingTextAreUnreadable() {
        val malformedUtf8 = byteArrayOf(0x7b, 0x22, 0x80.toByte(), 0x22, 0x7d)
        assertEquals(SaveProblem.UNREADABLE, decodeFailure(malformedUtf8).kind)
        assertEquals(
            SaveProblem.UNREADABLE,
            decodeFailure("""{"schema":2,"placements":[{"item":"fern","column":2.5,"row":7}]}""").kind,
        )
        assertEquals(
            SaveProblem.UNREADABLE,
            decodeFailure("""{"schema":2,"placements":[]} trailing""").kind,
        )
    }

    private fun decodeFailure(json: String): SaveReadException =
        decodeFailure(json.toByteArray(StandardCharsets.UTF_8))

    private fun decodeFailure(bytes: ByteArray): SaveReadException {
        return try {
            codec.decode(bytes)
            throw AssertionError("Expected decoding to fail")
        } catch (failure: SaveReadException) {
            failure
        }
    }
}
