package com.example.android_home_cal.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineMarkdownTest {

    private fun spansOf(input: String) = InlineMarkdown.parse(input).spans

    @Test
    fun `plain text passes through untouched`() {
        val result = InlineMarkdown.parse("- just text")
        assertEquals("- just text", result.text)
        assertEquals(emptyList<StyleRange>(), result.spans)
    }

    @Test
    fun `wiki link is underlined and brackets removed`() {
        val result = InlineMarkdown.parse("see [[link]] now")
        assertEquals("see link now", result.text)
        assertEquals(listOf(StyleRange(4, 8, InlineStyle.UNDERLINE)), result.spans)
    }

    @Test
    fun `wiki link with alias shows the alias`() {
        val result = InlineMarkdown.parse("[[Projects/Big Thing|the big thing]]")
        assertEquals("the big thing", result.text)
        assertEquals(listOf(StyleRange(0, 13, InlineStyle.UNDERLINE)), result.spans)
    }

    @Test
    fun `markdown link shows only its label underlined`() {
        val result = InlineMarkdown.parse("a [docs](http://x.y) b")
        assertEquals("a docs b", result.text)
        assertEquals(listOf(StyleRange(2, 6, InlineStyle.UNDERLINE)), result.spans)
    }

    @Test
    fun `bold italic strikethrough and code`() {
        assertEquals(
            listOf(StyleRange(0, 4, InlineStyle.BOLD)),
            spansOf("**bold**")
        )
        assertEquals(listOf(StyleRange(0, 4, InlineStyle.BOLD)), spansOf("__bold__"))
        assertEquals(listOf(StyleRange(0, 2, InlineStyle.ITALIC)), spansOf("*it*"))
        assertEquals(listOf(StyleRange(0, 2, InlineStyle.ITALIC)), spansOf("_it_"))
        assertEquals(listOf(StyleRange(0, 3, InlineStyle.STRIKETHROUGH)), spansOf("~~old~~"))
        val code = InlineMarkdown.parse("run `ls *.kt` now")
        assertEquals("run ls *.kt now", code.text)
        assertEquals(listOf(StyleRange(4, 11, InlineStyle.CODE)), code.spans)
    }

    @Test
    fun `nested formatting yields overlapping spans`() {
        val result = InlineMarkdown.parse("**bold [[link]] end**")
        assertEquals("bold link end", result.text)
        assertEquals(
            listOf(StyleRange(5, 9, InlineStyle.UNDERLINE), StyleRange(0, 13, InlineStyle.BOLD)),
            result.spans
        )
    }

    @Test
    fun `triple asterisks are bold and italic`() {
        val result = InlineMarkdown.parse("***both***")
        assertEquals("both", result.text)
        assertEquals(
            setOf(StyleRange(0, 4, InlineStyle.ITALIC), StyleRange(0, 4, InlineStyle.BOLD)),
            result.spans.toSet()
        )
    }

    @Test
    fun `stray markers and snake_case are left alone`() {
        val result = InlineMarkdown.parse("2 * 3 = 6, my_var_name, a_b")
        assertEquals("2 * 3 = 6, my_var_name, a_b", result.text)
        assertEquals(emptyList<StyleRange>(), result.spans)
    }
}
