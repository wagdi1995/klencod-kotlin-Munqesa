package com.munqesa.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.munqesa.app.R
import com.munqesa.app.util.Async
import com.munqesa.app.util.Press
import com.munqesa.app.util.TenderReportPdfGenerator
import com.munqesa.app.util.showShortToast
import java.io.File

class TenderReportPreviewActivity : AppCompatActivity() {

    private var pdfFile: File? = null
    private lateinit var pageContainer: LinearLayout

    private class Rendered(val file: File, val pages: List<Bitmap>)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quotation_preview)

        val quotationNumber = intent.getStringExtra("quotation_number") ?: ""
        val customerName = intent.getStringExtra("customer_name") ?: ""
        val quotationDate = intent.getStringExtra("quotation_date") ?: ""
        val total = intent.getDoubleExtra("total", 0.0)
        val productNames = intent.getStringArrayExtra("product_names") ?: arrayOf()
        val quantities = intent.getIntArrayExtra("quantities") ?: intArrayOf()
        val prices = intent.getDoubleArrayExtra("prices") ?: doubleArrayOf()
        val subtotals = intent.getDoubleArrayExtra("subtotals") ?: doubleArrayOf()

        val products = productNames.indices.map { i ->
            TenderReportPdfGenerator.TenderProduct(
                itemNumber = i + 1,
                productName = productNames[i],
                quantity = quantities[i],
                unitPrice = prices[i],
                totalPrice = subtotals[i]
            )
        }

        val tenderData = TenderReportPdfGenerator.TenderData(
            customerName = customerName,
            invoiceDate = quotationDate,
            products = products,
            grandTotal = total,
            quotationNumber = quotationNumber,
            fileBaseName = "عرض_سعر_${quotationNumber}_${customerName}"
        )

        pageContainer = findViewById(R.id.pageContainer)
        setupButtons()

        // مؤشر تحميل أثناء إنشاء الـ PDF
        val progress = ProgressBar(this)
        pageContainer.addView(
            progress,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = (48 * resources.displayMetrics.density).toInt() }
        )

        val targetWidth = minOf((resources.displayMetrics.widthPixels * 1.3f).toInt(), 1400)
        val appContext = applicationContext
        Async.background(
            work = {
                val file = TenderReportPdfGenerator(appContext).generateTenderReportPdf(tenderData)
                Rendered(file, renderPages(file, targetWidth))
            },
            onDone = { rendered ->
                if (isFinishing || isDestroyed) return@background
                pdfFile = rendered.file
                pageContainer.removeView(progress)
                showPages(rendered.pages)
            },
            onError = {
                if (isFinishing || isDestroyed) return@background
                pageContainer.removeView(progress)
                showShortToast(getString(R.string.preview_failed))
            }
        )
    }

    /** يحوّل صفحات الـ PDF إلى صور بدقة أعلى من الشاشة حتى يبقى النص حادًا عند التكبير. */
    private fun renderPages(file: File, targetWidth: Int): List<Bitmap> {
        val pages = mutableListOf<Bitmap>()
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        try {
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                try {
                    val scale = targetWidth.toFloat() / page.width
                    val bitmap = Bitmap.createBitmap(
                        targetWidth, (page.height * scale).toInt(), Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pages.add(bitmap)
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
        }
        return pages
    }

    private fun showPages(pages: List<Bitmap>) {
        val margin = (16 * resources.displayMetrics.density).toInt()
        pages.forEachIndexed { index, bitmap ->
            val imageView = ImageView(this).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                alpha = 0f
                translationY = 24 * resources.displayMetrics.density
            }
            pageContainer.addView(
                imageView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, margin) }
            )
            // ظهور تدريجي متتابع للصفحات
            imageView.animate().alpha(1f).translationY(0f)
                .setStartDelay(index * 90L).setDuration(380)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }
    }

    private fun setupButtons() {
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val share = findViewById<FloatingActionButton>(R.id.btnShare)
        Press.attach(share, 0.9f)
        share.setOnClickListener { sharePdf() }
    }

    private fun sharePdf() {
        val file = pdfFile ?: return
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "طلب مناقصة")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "مشاركة طلب المناقصة"))
        } catch (e: Exception) {
            showShortToast(getString(R.string.share_failed))
        }
    }

    companion object {
        fun start(context: Context, quotationData: TenderReportPdfGenerator.TenderData) {
            val intent = Intent(context, TenderReportPreviewActivity::class.java).apply {
                putExtra("quotation_number", quotationData.quotationNumber)
                putExtra("customer_name", quotationData.customerName)
                putExtra("quotation_date", quotationData.invoiceDate)
                putExtra("total", quotationData.grandTotal)
                val n = quotationData.products.size
                putExtra("product_names", Array(n) { quotationData.products[it].productName })
                putExtra("quantities", IntArray(n) { quotationData.products[it].quantity })
                putExtra("prices", DoubleArray(n) { quotationData.products[it].unitPrice })
                putExtra("subtotals", DoubleArray(n) { quotationData.products[it].totalPrice })
            }
            context.startActivity(intent)
        }
    }
}
