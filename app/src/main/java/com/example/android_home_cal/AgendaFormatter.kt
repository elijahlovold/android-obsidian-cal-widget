package com.example.android_home_cal

enum class AgendaStyle { BOLD, ITALIC, STRIKETHROUGH, UNDERLINE, CODE }

data class AgendaSpan(val start: Int, val end: Int, val style: AgendaStyle)

/** Plain [text] (markup removed) plus the style ranges to lay over it. */
data class StyledAgenda(val text: String, val spans: List<AgendaSpan>)

/**
 * Turns the raw `# Agenda` section into what the widget shows: sub-items under "work" entries
 * are dropped, and inline markdown/wiki-link markup is replaced by style ranges. Kept free of
 * android.* types so it can be unit-tested on the JVM; [AgendaSpannable] applies the result.
 */
object AgendaFormatter {

    /** Top-level items starting with one of these words lose their indented sub-items. */
    private val collapsedParents = Regex("^work\\b", RegexOption.IGNORE_CASE)

    private val bulletPrefix = Regex("^[-*+]\\s+")
    private val checkboxPrefix = Regex("^\\[[ xX]\\]\\s*")
    private val leadingMarkup = Regex("^[*_~`\\[]+")

    fun format(rawAgenda: String): StyledAgenda = parseInline(dropWorkSubItems(rawAgenda))

    /**
     * Drops every indented line belonging to a top-level "work" item. Blank lines that only
     * separated the dropped lines go with them.
     */
    fun dropWorkSubItems(rawAgenda: String): String {
        val kept = mutableListOf<String>()
        val pendingBlanks = mutableListOf<String>()
        var skipping = false
        for (line in rawAgenda.lines()) {
            when {
                line.isBlank() -> pendingBlanks.add(line)
                line.first().isWhitespace() -> {
                    if (skipping) {
                        pendingBlanks.clear()
                    } else {
                        kept.addAll(pendingBlanks)
                        pendingBlanks.clear()
                        kept.add(line)
                    }
                }
                else -> {
                    skipping = isCollapsedParent(line)
                    kept.addAll(pendingBlanks)
                    pendingBlanks.clear()
                    kept.add(line)
                }
            }
        }
        return kept.joinToString("\n").trim()
    }

    private fun isCollapsedParent(line: String): Boolean {
        val itemText = line
            .replace(bulletPrefix, "")
            .replace(checkboxPrefix, "")
            .replace(leadingMarkup, "")
        return collapsedParents.containsMatchIn(itemText)
    }

    private class Rule(
        val regex: Regex,
        val style: AgendaStyle,
        /** Group holding the visible text. */
        val contentGroup: Int,
        /** Whether the visible text may itself contain further markup. */
        val nested: Boolean = true
    )

    // Order matters only as a tiebreak for matches starting at the same index.
    private val rules = listOf(
        // [[target]] / [[target|alias]] - shows the alias when present.
        Rule(Regex("\\[\\[([^\\[\\]|]+)(?:\\|([^\\[\\]]+))?]]"), AgendaStyle.UNDERLINE, 0, nested = false),
        // [text](url)
        Rule(Regex("\\[([^\\[\\]]+)]\\(([^)]*)\\)"), AgendaStyle.UNDERLINE, 1),
        Rule(Regex("`([^`]+)`"), AgendaStyle.CODE, 1, nested = false),
        Rule(Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*(?!\\*)"), AgendaStyle.BOLD, 1),
        Rule(Regex("(?<!\\w)__(?=\\S)(.+?)(?<=\\S)__(?!\\w)"), AgendaStyle.BOLD, 1),
        Rule(Regex("~~(?=\\S)(.+?)(?<=\\S)~~"), AgendaStyle.STRIKETHROUGH, 1),
        Rule(Regex("\\*(?=[^\\s*])(.+?)(?<=[^\\s*])\\*"), AgendaStyle.ITALIC, 1),
        Rule(Regex("(?<!\\w)_(?=[^\\s_])(.+?)(?<=[^\\s_])_(?!\\w)"), AgendaStyle.ITALIC, 1)
    )

    fun parseInline(input: String): StyledAgenda {
        val out = StringBuilder()
        val spans = mutableListOf<AgendaSpan>()
        appendParsed(input, out, spans)
        return StyledAgenda(out.toString(), spans)
    }

    private fun appendParsed(input: String, out: StringBuilder, spans: MutableList<AgendaSpan>) {
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
            spans.add(AgendaSpan(spanStart, out.length, bestRule.style))
            pos = best.range.last + 1
        }
    }
}
