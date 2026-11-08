package com.munqesa.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.munqesa.app.R
import com.munqesa.app.data.Report
import com.munqesa.app.util.Format
import com.munqesa.app.util.Press
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuotationCardAdapter(
    private val onPreview: (Report) -> Unit,
    private val onEdit: (Report) -> Unit,
    private val onDelete: (Report) -> Unit,
    private val onShare: (Report) -> Unit
) : ListAdapter<Report, QuotationCardAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Report>() {
            override fun areItemsTheSame(a: Report, b: Report) = a.id == b.id
            override fun areContentsTheSame(a: Report, b: Report) = a == b
        }
    }

    // SimpleDateFormat مكلف الإنشاء، لذا نستخدم نسخة واحدة (الربط يتم دائمًا على الخيط الرئيسي)
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quotation_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvQuotationNumber: TextView = itemView.findViewById(R.id.tvQuotationNumber)
        private val tvCustomerName: TextView = itemView.findViewById(R.id.tvCustomerName)
        private val tvDateTime: TextView = itemView.findViewById(R.id.tvDateTime)
        private val tvTotal: TextView = itemView.findViewById(R.id.tvTotal)
        private val btnPreview: View = itemView.findViewById(R.id.btnPreview)
        private val btnEdit: View = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)
        private val btnShare: View = itemView.findViewById(R.id.btnShare)

        init {
            Press.attach(btnPreview, 0.88f)
            Press.attach(btnEdit, 0.88f)
            Press.attach(btnDelete, 0.88f)
            Press.attach(btnShare, 0.88f)
        }

        fun bind(report: Report) {
            tvQuotationNumber.text = "عرض سعر #${report.invoiceId}"
            tvCustomerName.text = report.customer
            tvDateTime.text = dateFormat.format(Date(report.createdAt))
            tvTotal.text = Format.money(report.total)

            btnPreview.setOnClickListener { onPreview(report) }
            btnEdit.setOnClickListener { onEdit(report) }
            btnDelete.setOnClickListener { onDelete(report) }
            btnShare.setOnClickListener { onShare(report) }
        }
    }
}
