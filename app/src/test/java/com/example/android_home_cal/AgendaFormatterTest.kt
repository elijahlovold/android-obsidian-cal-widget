package com.example.android_home_cal

import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaFormatterTest {

    private fun spansOf(input: String) = AgendaFormatter.parseInline(input).spans

    @Test
    fun `drops sub-items under work but keeps other top-level items`() {
        val agenda = "- work (8-4):\n  - test\n  - do item x\n- go to the store"
        assertEquals("- work (8-4):\n- go to the store", AgendaFormatter.dropWorkSubItems(agenda))
    }

    @Test
    fun `keeps sub-items under non-work items`() {
        val agenda = "- groceries\n  - milk\n- work\n\t- tabbed sub-item\n  - other\n- gym\n    - squats"
        assertEquals(
            "- groceries\n  - milk\n- work\n- gym\n    - squats",
            AgendaFormatter.dropWorkSubItems(agenda)
        )
    }

    @Test
    fun `work match ignores bullet, case and formatting but not longer words`() {
        assertEquals("* **Work** 8-4", AgendaFormatter.dropWorkSubItems("* **Work** 8-4\n  - x"))
        assertEquals("- [[work]]", AgendaFormatter.dropWorkSubItems("- [[work]]\n  - x"))
        assertEquals("- [ ] work", AgendaFormatter.dropWorkSubItems("- [ ] work\n  - x"))
        assertEquals("- workout\n  - x", AgendaFormatter.dropWorkSubItems("- workout\n  - x"))
    }

    @Test
    fun `blank lines that only separated dropped sub-items are dropped too`() {
        val agenda = "- work\n  - a\n\n  - b\n\n- store"
        assertEquals("- work\n\n- store", AgendaFormatter.dropWorkSubItems(agenda))
    }

    @Test
    fun `plain text passes through untouched`() {
        val result = AgendaFormatter.parseInline("- just text")
        assertEquals("- just text", result.text)
        assertEquals(emptyList<AgendaSpan>(), result.spans)
    }

    @Test
    fun `wiki link is underlined and brackets removed`() {
        val result = AgendaFormatter.parseInline("see [[link]] now")
        assertEquals("see link now", result.text)
        assertEquals(listOf(AgendaSpan(4, 8, AgendaStyle.UNDERLINE)), result.spans)
    }

    @Test
    fun `wiki link with alias shows the alias`() {
        val result = AgendaFormatter.parseInline("[[Projects/Big Thing|the big thing]]")
        assertEquals("the big thing", result.text)
        assertEquals(listOf(AgendaSpan(0, 13, AgendaStyle.UNDERLINE)), result.spans)
    }

    @Test
    fun `markdown link shows only its label underlined`() {
        val result = AgendaFormatter.parseInline("a [docs](http://x.y) b")
        assertEquals("a docs b", result.text)
        assertEquals(listOf(AgendaSpan(2, 6, AgendaStyle.UNDERLINE)), result.spans)
    }

    @Test
    fun `bold italic strikethrough and code`() {
        assertEquals(
            listOf(AgendaSpan(0, 4, AgendaStyle.BOLD)),
            spansOf("**bold**")
        )
        assertEquals(listOf(AgendaSpan(0, 4, AgendaStyle.BOLD)), spansOf("__bold__"))
        assertEquals(listOf(AgendaSpan(0, 2, AgendaStyle.ITALIC)), spansOf("*it*"))
        assertEquals(listOf(AgendaSpan(0, 2, AgendaStyle.ITALIC)), spansOf("_it_"))
        assertEquals(listOf(AgendaSpan(0, 3, AgendaStyle.STRIKETHROUGH)), spansOf("~~old~~"))
        val code = AgendaFormatter.parseInline("run `ls *.kt` now")
        assertEquals("run ls *.kt now", code.text)
        assertEquals(listOf(AgendaSpan(4, 11, AgendaStyle.CODE)), code.spans)
    }

    @Test
    fun `nested formatting yields overlapping spans`() {
        val result = AgendaFormatter.parseInline("**bold [[link]] end**")
        assertEquals("bold link end", result.text)
        assertEquals(
            listOf(AgendaSpan(5, 9, AgendaStyle.UNDERLINE), AgendaSpan(0, 13, AgendaStyle.BOLD)),
            result.spans
        )
    }

    @Test
    fun `triple asterisks are bold and italic`() {
        val result = AgendaFormatter.parseInline("***both***")
        assertEquals("both", result.text)
        assertEquals(
            setOf(AgendaSpan(0, 4, AgendaStyle.ITALIC), AgendaSpan(0, 4, AgendaStyle.BOLD)),
            result.spans.toSet()
        )
    }

    @Test
    fun `stray markers and snake_case are left alone`() {
        val result = AgendaFormatter.parseInline("2 * 3 = 6, my_var_name, a_b")
        assertEquals("2 * 3 = 6, my_var_name, a_b", result.text)
        assertEquals(emptyList<AgendaSpan>(), result.spans)
    }

    @Test
    fun `format trims work sub-items then styles the rest`() {
        val result = AgendaFormatter.format("- **work** (8-4):\n  - [[test]]\n- go to [[store]]")
        assertEquals("- work (8-4):\n- go to store", result.text)
        assertEquals(
            listOf(AgendaSpan(2, 6, AgendaStyle.BOLD), AgendaSpan(22, 27, AgendaStyle.UNDERLINE)),
            result.spans
        )
    }
}
