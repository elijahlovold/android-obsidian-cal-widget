package com.example.android_home_cal.notes

enum class InlineStyle { BOLD, ITALIC, STRIKETHROUGH, UNDERLINE, CODE }

data class StyleRange(val start: Int, val end: Int, val style: InlineStyle)

/** Plain [text] (markup removed) plus the style ranges to lay over it. */
data class StyledText(val text: String, val spans: List<StyleRange>)

/**
 * Inline markdown/wiki-link markup → plain text plus style ranges. Kept free of android.*
 * types so it can be unit-tested on the JVM; StyledTextSpannable applies the result.
 */
object InlineMarkdown {

    private class Rule(
        val regex: Regex,
        val style: InlineStyle,
        /** Group holding the visible text. */
        val contentGroup: Int,
        /** Whether the visible text may itself contain further markup. */
        val nested: Boolean = true
    )

    // Order matters only as a tiebreak for matches starting at the same index.
    private val rules = listOf(
        // [[target]] / [[target|alias]] - shows the alias when present.
        Rule(Regex("\\[\\[([^\\[\\]|]+)(?:\\|([^\\[\\]]+))?]]"), InlineStyle.UNDERLINE, 0, nested = false),
        // [text](url)
        Rule(Regex("\\[([^\\[\\]]+)]\\(([^)]*)\\)"), InlineStyle.UNDERLINE, 1),
        Rule(Regex("`([^`]+)`"), InlineStyle.CODE, 1, nested = false),
        Rule(Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*(?!\\*)"), InlineStyle.BOLD, 1),
        Rule(Regex("(?<!\\w)__(?=\\S)(.+?)(?<=\\S)__(?!\\w)"), InlineStyle.BOLD, 1),
        Rule(Regex("~~(?=\\S)(.+?)(?<=\\S)~~"), InlineStyle.STRIKETHROUGH, 1),
        Rule(Regex("\\*(?=[^\\s*])(.+?)(?<=[^\\s*])\\*"), InlineStyle.ITALIC, 1),
        Rule(Regex("(?<!\\w)_(?=[^\\s_])(.+?)(?<=[^\\s_])_(?!\\w)"), InlineStyle.ITALIC, 1)
    )

    fun parse(input: String): StyledText {
        val out = StringBuilder()
        val spans = mutableListOf<StyleRange>()
        appendParsed(input, out, spans)
        return StyledText(out.toString(), spans)
    }

    private fun appendParsed(input: String, out: StringBuilder, spans: MutableList<StyleRange>) {
        var pos = 0
        while (pos < input.length) {
            var best: MatchResult? = null
            var bestRule: Rule? = null
            for (rule in rules) {
                val match = rule.regex.find(input, pos) ?: continue
                if (best == null || match.range.first < best.range.first) {
                    best = match
                    bestRule = rule
                }
            }
            if (best == null || bestRule == null) {
                out.append(input, pos, input.length)
                return
            }

            out.append(input, pos, best.range.first)
            val spanStart = out.length
            if (bestRule.contentGroup == 0) {
                // Wiki link: visible text is the alias if there is one, otherwise the target.
                val visible = best.groupValues[2].ifEmpty { best.groupValues[1] }
                out.append(visible)
            } else {
                val content = best.groupValues[bestRule.contentGroup]
                if (bestRule.nested) appendParsed(content, out, spans) else out.append(content)
            }
            spans.add(StyleRange(spanStart, out.length, bestRule.style))
            pos = best.range.last + 1
        }
    }
}
