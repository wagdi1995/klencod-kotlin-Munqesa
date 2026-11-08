package com.munqesa.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.munqesa.app.R
import com.munqesa.app.data.InvoiceItem
import com.munqesa.app.data.Product
import com.munqesa.app.util.Format
import com.munqesa.app.util.Press

class ProductAdapter(
    private val onEdit: (Product) -> Unit,
    private val onDelete: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(a: Product, b: Product) = a.id == b.id
            override fun areContentsTheSame(a: Product, b: Product) = a == b
        }
    }

    /** [onCommitted] يُستدعى بعد تطبيق التغييرات على القائمة فعليًا. */
    fun submit(list: List<Product>, onCommitted: (() -> Unit)? = null) {
        submitList(list, onCommitted?.let { cb -> Runnable { cb() } })
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.productName)
        val price: TextView = view.findViewById(R.id.productPrice)
        val edit: View = view.findViewById(R.id.editButton)
        val delete: View = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vh = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false))
        Press.attach(vh.edit, 0.88f)
        Press.attach(vh.delete, 0.88f)
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val product = getItem(position)
        holder.name.text = product.name
        holder.price.text = Format.money(product.price)
        holder.edit.setOnClickListener { onEdit(product) }
        holder.delete.setOnClickListener { onDelete(product) }
    }
}

/** بطاقة عامة تُستخدم للفواتير والتقارير. */
data class Record(
    val id: Long,
    val title: String,
    val subtitle: String,
    val meta: String,
    val amount: String
)

class RecordAdapter(private val onClick: (Record) -> Unit) :
    ListAdapter<Record, RecordAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Record>() {
            override fun areItemsTheSame(a: Record, b: Record) = a.id == b.id
            override fun areContentsTheSame(a: Record, b: Record) = a == b
        }
    }

    fun submit(list: List<Record>) = submitList(list)

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.recordTitle)
        val subtitle: TextView = view.findViewById(R.id.recordSubtitle)
        val meta: TextView = view.findViewById(R.id.recordMeta)
        val amount: TextView = view.findViewById(R.id.recordAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vh = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false))
        Press.attach(vh.itemView, 0.97f)
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val record = getItem(position)
        holder.title.text = record.title
        holder.subtitle.text = record.subtitle
        holder.meta.text = record.meta
        holder.amount.text = record.amount
        holder.itemView.setOnClickListener { onClick(record) }
    }
}

class InvoiceItemAdapter(
    private val onRemove: (Int) -> Unit
) : ListAdapter<InvoiceItem, InvoiceItemAdapter.VH>(DIFF) {

    companion object {
        // الصنف الواحد يظهر مرة واحدة في الفاتورة (الكميات تُدمج) لذا productId مفتاح ثابت
        private val DIFF = object : DiffUtil.ItemCallback<InvoiceItem>() {
            override fun areItemsTheSame(a: InvoiceItem, b: InvoiceItem) = a.productId == b.productId
            override fun areContentsTheSame(a: InvoiceItem, b: InvoiceItem) = a == b
        }
    }

    fun submit(list: List<InvoiceItem>) = submitList(list)

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.itemName)
        val calc: TextView = view.findViewById(R.id.itemCalc)
        val subtotal: TextView = view.findViewById(R.id.itemSubtotal)
        val remove: View = view.findViewById(R.id.removeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vh = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_invoice_item, parent, false))
        Press.attach(vh.remove, 0.88f)
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context
        holder.name.text = item.name
        holder.calc.text = ctx.getString(
            R.string.item_line_fmt, Format.money(item.unitPrice), item.quantity.toString()
        )
        holder.subtotal.text = Format.money(item.subtotal)
        holder.remove.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onRemove(pos)
        }
    }
}
