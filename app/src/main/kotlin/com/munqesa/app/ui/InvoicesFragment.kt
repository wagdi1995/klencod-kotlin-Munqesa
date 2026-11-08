package com.munqesa.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.munqesa.app.NewInvoiceActivity
import com.munqesa.app.R
import com.munqesa.app.data.DatabaseHelper
import com.munqesa.app.data.Invoice
import com.munqesa.app.util.Async
import com.munqesa.app.util.Format
import com.munqesa.app.util.Press
import com.munqesa.app.util.showShortToast

class InvoicesFragment : Fragment(R.layout.fragment_invoices) {

    private lateinit var db: DatabaseHelper
    private lateinit var adapter: RecordAdapter
    private lateinit var emptyView: View
    private var invoices: List<Invoice> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = DatabaseHelper.get(requireContext())
        emptyView = view.findViewById(R.id.emptyView)

        adapter = RecordAdapter { record -> invoices.firstOrNull { it.id == record.id }?.let { showDetails(it) } }
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        val addFab = view.findViewById<View>(R.id.addFab)
        Press.attach(addFab, 0.9f)
        addFab.setOnClickListener {
            startActivity(Intent(requireContext(), NewInvoiceActivity::class.java))
        }
    }

    // الأقسام تبقى حيّة (إخفاء/إظهار) لذلك نحدّث عند العودة أو عند ظهور القسم
    override fun onResume() {
        super.onResume()
        if (!isHidden) load()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) load()
    }

    private fun load() {
        Async.background({ db.getInvoices() }, { list ->
            if (view == null) return@background
            invoices = list
            adapter.submit(list.map {
                Record(
                    id = it.id,
                    title = it.customer,
                    subtitle = getString(R.string.invoice_subtitle, it.id.toString(), it.itemCount.toString()),
                    meta = Format.date(it.createdAt),
                    amount = Format.money(it.total)
                )
            })
            emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        })
    }

    private fun showDetails(invoice: Invoice) {
        val ctx = requireContext()
        DetailDialog.show(
            context = ctx,
            title = invoice.customer,
            subtitle = getString(R.string.invoice_subtitle, invoice.id.toString(), invoice.itemCount.toString()) +
                "\n" + Format.date(invoice.createdAt),
            header = null,
            items = db.getInvoiceItems(invoice.id),
            total = invoice.total,
            onDelete = {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.invoice_confirm_delete)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        db.deleteInvoice(invoice.id)
                        load()
                        ctx.showShortToast(getString(R.string.invoice_deleted))
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
            }
        )
    }
}
