package com.munqesa.app.util

import android.content.Context
import android.widget.Toast
import com.munqesa.app.R
import com.munqesa.app.data.InvoiceItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {

    /** 1250 -> "1,250 $" ، 12.5 -> "12.50 $" (أرقام لاتينية دائمًا) */
    fun money(value: Double): String {
        val number = if (value % 1.0 == 0.0) {
            String.format(Locale.US, "%,.0f", value)
        } else {
            String.format(Locale.US, "%,.2f", value)
        }
        return "$number $"
    }

    /** للعرض داخل حقل التعديل بدون فواصل آلاف */
    fun plain(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    fun date(millis: Long): String =
        SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.US).format(Date(millis))

    /** يحوّل الأرقام العربية-الهندية إلى لاتينية ويزيل الرموز والفواصل. */
    fun normalizeNumber(text: String): String {
        val sb = StringBuilder()
        for (ch in text.trim()) {
            when (ch) {
                in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
                '\u066B' -> sb.append('.')
                '\u066C', ',', '$', ' ', '\u00A0' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parsePrice(text: String): Double? = normalizeNumber(text).toDoubleOrNull()

    fun parseInt(text: String): Int? = normalizeNumber(text).toIntOrNull()

    fun shareText(
        context: Context,
        header: String?,
        title: String,
        subtitle: String,
        items: List<InvoiceItem>,
        total: Double
    ): String {
        val sb = StringBuilder()
        if (!header.isNullOrBlank()) sb.append(header.trim()).append("\n\n")
        sb.append(title).append('\n').append(subtitle).append("\n\n")
        items.forEachIndexed { index, item ->
            sb.append(index + 1).append(". ").append(item.name).append('\n')
            sb.append("    ")
                .append(Money.line(context, item))
                .append('\n')
        }
        sb.append('\n').append(context.getString(R.string.total_label, money(total)))
        return sb.toString()
    }
}

object Money {
    /** "12 $ × 3 = 36 $" */
    fun line(context: Context, item: InvoiceItem): String =
        context.getString(
            R.string.item_line_fmt,
            Format.money(item.unitPrice),
            item.quantity.toString()
        ) + " = " + Format.money(item.subtotal)
}

fun Context.showShortToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.showLongToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
