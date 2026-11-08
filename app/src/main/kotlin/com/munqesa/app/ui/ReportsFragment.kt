package com.munqesa.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.munqesa.app.NewInvoiceActivity
import com.munqesa.app.R
import com.munqesa.app.data.DatabaseHelper
import com.munqesa.app.data.Report
import com.munqesa.app.util.Async
import com.munqesa.app.util.TenderReportPdfGenerator
import com.munqesa.app.util.showShortToast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportsFragment : Fragment(R.layout.fragment_reports) {

    private lateinit var db: DatabaseHelper
    private lateinit var adapter: QuotationCardAdapter
    private lateinit var emptyView: View

    /** يمنع تشغيل أكثر من معاينة/مشاركة في نفس الوقت عند الضغط المتكرر. */
    private var busy = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = DatabaseHelper.get(requireContext())
        emptyView = view.findViewById(R.id.emptyView)

        adapter = QuotationCardAdapter(
            onPreview = { report -> showPreview(report) },
            onEdit = { report -> showEditReport(report) },
            onDelete = { report -> showDeleteConfirm(report) },
            onShare = { report -> shareReport(report) }
        )
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        // لا يوجد زر إضافة: عروض الأسعار تُنشأ تلقائيًا عند حفظ الفاتورة
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) load()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) load()
    }

    private fun load() {
        Async.background({ db.getReports() }, { reports ->
            if (view == null) return@background
            adapter.submitList(reports)
            emptyView.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE
        })
    }

    /** يبني بيانات ورقة العرض من التقرير (يُستدعى من الخلفية لأنه يقرأ قاعدة البيانات). */
    private fun buildTenderData(report: Report): TenderReportPdfGenerator.TenderData {
        val products = db.getInvoiceItems(report.invoiceId).mapIndexed { index, item ->
            TenderReportPdfGenerator.TenderProduct(
                itemNumber = index + 1,
                productName = item.name,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                totalPrice = item.subtotal
            )
        }
        return TenderReportPdfGenerator.TenderData(
            customerName = report.customer,
            invoiceDate = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(report.createdAt)),
            products = products,
            grandTotal = report.total,
            quotationNumber = report.invoiceId.toString(),
            fileBaseName = "عرض_سعر_${report.invoiceId}_${report.customer}"
        )
    }

    private fun showPreview(report: Report) {
        if (busy) return
        busy = true
        val ctx = requireContext()
        Async.background({ buildTenderData(report) }, { data ->
            busy = false
            if (view != null) TenderReportPreviewActivity.start(ctx, data)
        }, {
            busy = false
            ctx.showShortToast(getString(R.string.preview_failed))
        })
    }

    private fun showEditReport(report: Report) {
        val intent = Intent(requireContext(), NewInvoiceActivity::class.java).apply {
            putExtra(NewInvoiceActivity.EXTRA_EDIT_INVOICE_ID, report.invoiceId)
        }
        startActivity(intent)
    }

    private fun showDeleteConfirm(report: Report) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("حذف عرض السعر")
            .setMessage("هل أنت متأكد من حذف عرض السعر؟")
            .setPositiveButton("نعم") { _, _ ->
                Async.background({ db.deleteReport(report.id) }, {
                    if (view != null) load()
                    context?.showShortToast("تم حذف عرض السعر")
                })
            }
            .setNegativeButton("لا", null)
            .show()
    }

    private fun shareReport(report: Report) {
        if (busy) return
        busy = true
        val ctx = requireContext()
        Async.background(
            work = {
                val data = buildTenderData(report)
                TenderReportPdfGenerator(ctx).generateTenderReportPdf(data)
            },
            onDone = { pdfFile ->
                busy = false
                if (!isAdded) return@background
                try {
                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", pdfFile)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "عرض سعر - ${report.customer}")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "مشاركة عرض السعر"))
                } catch (e: Exception) {
                    ctx.showShortToast(getString(R.string.share_failed))
                }
            },
            onError = {
                busy = false
                ctx.showShortToast(getString(R.string.share_failed))
            }
        )
    }
}
