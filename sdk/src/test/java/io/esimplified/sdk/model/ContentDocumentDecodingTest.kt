package io.esimplified.sdk.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentDocumentDecodingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    // region Document
    @Test
    fun `root fields decode including an explicit null and two levels of children`() {
        val document = document()

        assertEquals("en", document.language)
        assertEquals("terms", document.id)
        assertEquals("Terms of Service", document.title)
        assertNull(document.description)
        assertEquals("Last updated: 28 April 2025", document.updatedAt)
        assertTrue(document.blocks.isEmpty())
        assertEquals(2, document.children.size)

        val general = child("general", document)
        assertNotNull(general)
        assertTrue(general!!.blocks.isEmpty())
        assertEquals(1, general.children.size)

        val article = general.children.first()
        assertEquals("are-esims-safe", article.id)
        assertEquals("Are eSIMs safe?", article.title)
        assertTrue(article.children.isEmpty())
    }
    // endregion

    // region Blocks
    @Test
    fun `heading and paragraph blocks decode with their text`() {
        val eligibility = child("eligibility", document())!!

        assertEquals(3, eligibility.blocks.size)
        assertEquals(ContentBlock.Heading("Who can sign up"), eligibility.blocks[0])
        assertEquals(
            ContentBlock.Paragraph("You must be able to hold an account."),
            eligibility.blocks[1],
        )

        val article = child("general", document())!!.children.first()
        assertEquals(listOf(ContentBlock.Paragraph("Yes, an eSIM cannot be removed.")), article.blocks)
    }

    @Test
    fun `list block decodes ordered marker and its nested items`() {
        val eligibility = child("eligibility", document())!!
        val block = eligibility.blocks[2]

        assertTrue(block is ContentBlock.ListBlock)
        val list = (block as ContentBlock.ListBlock).list
        assertTrue(list.ordered)
        assertEquals(ContentListMarker.DECIMAL, list.marker)
        assertEquals(3, list.items.size)

        val first = list.items.first()
        assertEquals("Access to benefits: eligible cardholders must:", first.text)
        assertEquals(false, first.ordered)
        assertEquals(ContentListMarker.BULLET, first.marker)
        assertEquals(2, first.items.size)

        val leaf = first.items.first()
        assertEquals("Select your region.", leaf.text)
        assertTrue(leaf.items.isEmpty())
        assertNull(leaf.ordered)
        assertNull(leaf.marker)

        assertTrue(list.items[1].items.isEmpty())
        assertNull(list.items[1].ordered)
        assertNull(list.items[1].marker)
    }

    @Test
    fun `a nested list item carries its own marker at the right depth`() {
        val payload = """
            {
              "type": "list",
              "ordered": true,
              "marker": "decimal",
              "items": [
                {
                  "text": "Top",
                  "ordered": true,
                  "marker": "alpha",
                  "items": [
                    {
                      "text": "Middle",
                      "ordered": false,
                      "marker": "bullet",
                      "items": [{ "text": "Leaf", "items": [] }]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val list = (json.decodeFromString<ContentBlock>(payload) as ContentBlock.ListBlock).list

        assertEquals(ContentListMarker.DECIMAL, list.marker)
        val top = list.items.single()
        assertEquals(ContentListMarker.ALPHA, top.marker)
        val middle = top.items.single()
        assertEquals(ContentListMarker.BULLET, middle.marker)
        assertEquals("Middle", middle.text)
        val leaf = middle.items.single()
        assertEquals("Leaf", leaf.text)
        assertNull(leaf.marker)
    }
    // endregion

    // region Forward compatibility
    @Test
    fun `an unrecognised block type decodes to Unknown without throwing`() {
        val payload = """[{"type":"video","url":"x"},{"type":"paragraph","text":"p"}]"""

        val blocks = json.decodeFromString<List<ContentBlock>>(payload)

        assertEquals(listOf(ContentBlock.Unknown, ContentBlock.Paragraph("p")), blocks)
    }

    @Test
    fun `a block with no type key decodes to Unknown without throwing`() {
        val payload = """[{"text":"orphan"},{"type":"heading","text":"h"}]"""

        val blocks = json.decodeFromString<List<ContentBlock>>(payload)

        assertEquals(listOf(ContentBlock.Unknown, ContentBlock.Heading("h")), blocks)
    }

    @Test
    fun `an unknown block does not stop the rest of the document decoding`() {
        val payload = """
            {
              "language": "en",
              "blocks": [
                {"type":"carousel","slides":[{"image":"a"}]},
                {"type":"paragraph","text":"Still here."}
              ]
            }
        """.trimIndent()

        val document = json.decodeFromString<ContentDocument>(payload)

        assertEquals(
            listOf(ContentBlock.Unknown, ContentBlock.Paragraph("Still here.")),
            document.blocks,
        )
    }

    @Test
    fun `an unrecognised list marker falls back to bullet`() {
        val payload = """{"type":"list","ordered":false,"marker":"roman","items":[]}"""

        val block = json.decodeFromString<ContentBlock>(payload)

        assertEquals(ContentBlock.ListBlock(ContentList(false, ContentListMarker.BULLET, emptyList())), block)
    }
    // endregion

    // region Round trip
    @Test
    fun `encoding then decoding a document is lossless`() {
        val document = document()

        val encoded = json.encodeToString(ContentDocument.serializer(), document)

        assertEquals(document, json.decodeFromString<ContentDocument>(encoded))
    }

    @Test
    fun `unknown blocks survive a round trip`() {
        val node = ContentNode(id = "x", blocks = listOf(ContentBlock.Unknown, ContentBlock.Heading("h")))

        val encoded = json.encodeToString(ContentNode.serializer(), node)

        assertEquals(node, json.decodeFromString<ContentNode>(encoded))
    }

    @Test
    fun `a list block survives a round trip with its items`() {
        val block = ContentBlock.ListBlock(
            ContentList(
                ordered = true,
                marker = ContentListMarker.ALPHA,
                items = listOf(
                    ContentListItem(
                        text = "Top",
                        items = listOf(ContentListItem("Leaf")),
                        ordered = false,
                        marker = ContentListMarker.BULLET,
                    ),
                ),
            ),
        )

        val encoded = json.encodeToString(ContentBlock.serializer(), block)

        assertEquals(block, json.decodeFromString<ContentBlock>(encoded))
    }
    // endregion

    private fun document(): ContentDocument = json.decodeFromString(DOCUMENT_JSON)

    private fun child(id: String, document: ContentDocument): ContentNode? =
        document.children.firstOrNull { it.id == id }

    private companion object {
        val DOCUMENT_JSON = """
            {
              "language": "en",
              "id": "terms",
              "title": "Terms of Service",
              "description": null,
              "updatedAt": "Last updated: 28 April 2025",
              "blocks": [],
              "children": [
                {
                  "id": "eligibility",
                  "title": "Eligibility",
                  "description": null,
                  "updatedAt": null,
                  "blocks": [
                    {"type": "heading", "text": "Who can sign up"},
                    {"type": "paragraph", "text": "You must be able to hold an account."},
                    {
                      "type": "list",
                      "ordered": true,
                      "marker": "decimal",
                      "items": [
                        {
                          "text": "Access to benefits: eligible cardholders must:",
                          "items": [
                            {"text": "Select your region.", "items": []},
                            {"text": "Log in to an account.", "items": []}
                          ],
                          "ordered": false,
                          "marker": "bullet"
                        },
                        {"text": "Verification happens on entry.", "items": []},
                        {"text": "Your device must support eSIM.", "items": []}
                      ]
                    }
                  ],
                  "children": []
                },
                {
                  "id": "general",
                  "title": "General",
                  "description": null,
                  "updatedAt": null,
                  "blocks": [],
                  "children": [
                    {
                      "id": "are-esims-safe",
                      "title": "Are eSIMs safe?",
                      "description": null,
                      "updatedAt": null,
                      "blocks": [{"type": "paragraph", "text": "Yes, an eSIM cannot be removed."}],
                      "children": []
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}
