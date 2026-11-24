package com.example.android_home_cal.widget

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.example.android_home_cal.notes.InlineStyle
import com.example.android_home_cal.notes.StyledText

object StyledTextSpannable {

    fun from(agenda: StyledText): CharSequence {
        val builder = SpannableStringBuilder(agenda.text)
        for (span in agenda.spans) {
            if (span.start >= span.end) continue
            val what: Any = when (span.style) {
                InlineStyle.BOLD -> StyleSpan(Typeface.BOLD)
                InlineStyle.ITALIC -> StyleSpan(Typeface.ITALIC)
                InlineStyle.STRIKETHROUGH -> StrikethroughSpan()
                InlineStyle.UNDERLINE -> UnderlineSpan()
                InlineStyle.CODE -> TypefaceSpan("monospace")
            }
            builder.setSpan(what, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return builder
    }
}
