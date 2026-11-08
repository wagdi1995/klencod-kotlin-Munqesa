package com.munqesa.app.ui

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.munqesa.app.R
import com.munqesa.app.data.InvoiceItem
import com.munqesa.app.util.Format

/** نافذة عرض تفاصيل فاتورة أو تقرير، مع مشاركة وحذف. */
object DetailDialog {

    fun show(
        context: Context,
        title: String,
        subtitle: String,
        header: String?,
        items: List<InvoiceItem>,
        total: Double,
        onDelete: () -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_detail, null)
        val headerView = view.findViewById<TextView>(R.id.detailHeader)
        if (!header.isNullOrBlank()) {
            headerView.text = header.trim()
            headerView.visibility = View.VISIBLE
        }
        view.findViewById<TextView>(R.id.detailTitle).text = title
        view.findViewById<TextView>(R.id.detailSubtitle).text = subtitle
        view.findViewById<TextView>(R.id.detailTotal).text = Format.money(total)

        val container = view.findViewById<LinearLayout>(R.id.detailItems)
        val inflater = LayoutInflater.from(context)
        for (item in items) {
            val row = inflater.inflate(R.layout.item_detail_row, container, false)
            row.findViewById<TextView>(R.id.rowName).text = item.name
            row.findViewById<TextView>(R.id.rowCalc).text = context.getString(
                R.string.item_line_fmt, Format.money(item.unitPrice), item.quantity.toString()
            )
            row.findViewById<TextView>(R.id.rowSubtotal).text = Format.money(item.subtotal)
            container.addView(row)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(view)
            .setPositiveButton(R.string.share) { _, _ ->
                val text = Format.shareText(context, header, title, subtitle, items, total)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, null))
            }
            .setNegativeButton(R.string.close, null)
            .setNeutralButton(R.string.delete) { _, _ -> onDelete() }
            .create()
        dialog.show()
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
            .setTextColor(ContextCompat.getColor(context, R.color.error_red))
    }
}
