package com.example.android_home_cal

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan

object AgendaSpannable {

    fun from(agenda: StyledAgenda): CharSequence {
        val builder = SpannableStringBuilder(agenda.text)
        for (span in agenda.spans) {
            if (span.start >= span.end) continue
            val what: Any = when (span.style) {
                AgendaStyle.BOLD -> StyleSpan(Typeface.BOLD)
                AgendaStyle.ITALIC -> StyleSpan(Typeface.ITALIC)
                AgendaStyle.STRIKETHROUGH -> StrikethroughSpan()
                AgendaStyle.UNDERLINE -> UnderlineSpan()
                AgendaStyle.CODE -> TypefaceSpan("monospace")
            }
            builder.setSpan(what, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return builder
    }
}
