package com.munqesa.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.munqesa.app.R
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Tender (طلب مناقصة) A4 PDF report generator.
 *
 * Exact layout (A4 portrait 595x842pt, margins 12mm = 34pt):
 *  1. Title "طلب مناقصة" — centered purple rounded rectangle
 *  2. Customer (RIGHT, purple card) + Date (LEFT, white) — one horizontal row
 *  3. Products table — full width, RTL columns: رقم الصنف 9% | اسم الصنف 39% | الكمية 15% | السعر 17% | الإجمالي 20%
 *  4. Total summary — ONE row: LEFT purple card (30%) المبلغ الإجمالي + amount, RIGHT white area (70%) amount in Arabic words
 *  5. Five signature boxes — ONE horizontal row, RTL: مقدم الطلب، مدير الفرع، المالية، مدير التسويق، المدير التجاري
 */
class TenderReportPdfGenerator(private val context: Context) {

    data class TenderProduct(
        val itemNumber: Int,
        val productName: String,
        val quantity: Int,
        val unitPrice: Double,
        val totalPrice: Double
    )

    data class TenderData(
        val customerName: String,
        val invoiceDate: String,
        val products: List<TenderProduct>,
        val grandTotal: Double,
        val quotationNumber: String = "",
        val fileBaseName: String? = null
    )

    // ---- Palette ----
    private val colorPrimaryPurple = Color.parseColor("#6422C5")
    private val colorSecondaryPurple = Color.parseColor("#813CEB")
    private val colorLightLavender = Color.parseColor("#F1EDFF")
    private val colorBorder = Color.parseColor("#DED9E8")
    private val colorTextDark = Color.parseColor("#343A46")
    private val colorWhite = Color.WHITE

    // ---- Fonts (Tajawal, embedded in the PDF by PdfDocument) ----
    private val fontRegular: Typeface by lazy {
        try { ResourcesCompat.getFont(context, R.font.tajawal_regular) ?: Typeface.DEFAULT } catch (e: Exception) { Typeface.DEFAULT }
    }
    private val fontBold: Typeface by lazy {
        try { ResourcesCompat.getFont(context, R.font.tajawal_bold) ?: Typeface.DEFAULT_BOLD } catch (e: Exception) { Typeface.DEFAULT_BOLD }
    }

    // ---- Page geometry ----
    private val pageWidth = 595f
    private val pageHeight = 842f
    private val margin = 34f // ~12mm
    private val contentLeft = margin
    private val contentRight = pageWidth - margin
    private val contentWidth = contentRight - contentLeft // 527pt

    fun generateTenderReportPdf(tenderData: TenderData): File {
        val pdfDocument = PdfDocument()

        var pageNumber = 1
        var page = pdfDocument.startPage(
            PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), pageNumber).create()
        )
        var canvas = page.canvas

        fun drawWhiteBackground(c: Canvas) {
            val bgPaint = Paint().apply { color = Color.WHITE }
            c.drawRect(0f, 0f, pageWidth, pageHeight, bgPaint)
        }

        drawWhiteBackground(canvas)

        fun newPage() {
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), pageNumber).create()
            )
            canvas = page.canvas
            drawWhiteBackground(canvas)
        }

        // ---------- Paints ----------
        val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextDark; textSize = 11f; typeface = fontBold
        }
        val paintTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorWhite; textSize = 16f; typeface = fontBold; textAlign = Paint.Align.CENTER
        }
        val paintCustomer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorWhite; textSize = 14f; typeface = fontBold; textAlign = Paint.Align.CENTER
        }
        val paintDate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextDark; textSize = 13f; typeface = fontBold; textAlign = Paint.Align.CENTER
        }
        val paintHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorWhite; textSize = 10.5f; typeface = fontBold; textAlign = Paint.Align.CENTER
        }
        val paintCellBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextDark; textSize = 11f; typeface = fontBold; textAlign = Paint.Align.CENTER
        }
        val paintCellName = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextDark; textSize = 11f; typeface = fontBold; textAlign = Paint.Align.RIGHT
        }
        val paintCellNameCentered = Paint(paintCellName).apply { textAlign = Paint.Align.CENTER }
        val paintCellTotal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorPrimaryPurple; textSize = 11f; typeface = fontBold; textAlign = Paint.Align.CENTER
        }
        val paintTotalLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorWhite; textSize = 12f; typeface = fontBold; textAlign = Paint.Align.CENTER
        }
        val paintTotalAmount = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorWhite; textSize = 15f; typeface = fontBold; textAlign = Paint.Align.CENTER
        }
        val paintWords = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextDark; textSize = 13f; typeface = fontBold
        }
        val paintSignatureTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorPrimaryPurple; textSize = 10f; typeface = fontBold; textAlign = Paint.Align.CENTER
        }

        val fillPurple = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorPrimaryPurple }
        val fillLavender = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorLightLavender }
        val fillWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorWhite }
        val strokeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorBorder; style = Paint.Style.STROKE; strokeWidth = 1f
        }
        val strokeSeparator = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorBorder; style = Paint.Style.STROKE; strokeWidth = 0.8f
        }
        val strokeColumn = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorBorder; style = Paint.Style.STROKE; strokeWidth = 0.5f
        }
        val strokeSignatureLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorPrimaryPurple; style = Paint.Style.STROKE; strokeWidth = 1.2f
        }

        var yPos = margin

        // ---------- Header image (full width, high quality) ----------
        try {
            val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.header_image)
            if (bitmap != null) {
                // Scale to full content width with high quality bilinear filtering
                val headerWidth = contentWidth.toInt()
                val scale = headerWidth.toFloat() / bitmap.width.toFloat()
                val headerHeight = (bitmap.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(bitmap, headerWidth, headerHeight, true)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(scaled, contentLeft, yPos, paint)
                yPos += headerHeight + 14f
            }
        } catch (e: Exception) { e.printStackTrace() }

        yPos += 6f

        // ---------- SECTION 1: REPORT TITLE ----------
        val titleWidth = 150f
        val titleHeight = 34f
        val titleRect = RectF(
            pageWidth / 2f - titleWidth / 2f, yPos,
            pageWidth / 2f + titleWidth / 2f, yPos + titleHeight
        )
        canvas.drawRoundRect(titleRect, 8f, 8f, fillPurple)
        canvas.drawText("طلب مناقصة", pageWidth / 2f, yPos + 23f, paintTitle)
        yPos += titleHeight + 16f

        // ---------- SECTION 2: CUSTOMER (RIGHT) + DATE (LEFT), ONE ROW ----------
        val infoRowHeight = 40f
        val customerCardWidth = 300f
        val dateCardWidth = 180f

        // Customer card — RIGHT
        val customerRect = RectF(
            contentRight - customerCardWidth, yPos,
            contentRight, yPos + infoRowHeight
        )
        canvas.drawRoundRect(customerRect, 8f, 8f, fillPurple)
        val customerText = "اسم العميل: ${tenderData.customerName}"
        canvas.drawText(
            fitText(customerText, paintCustomer, customerCardWidth - 20f),
            contentRight - customerCardWidth / 2f, yPos + 24f, paintCustomer
        )

        // Date — LEFT
        val dateRect = RectF(contentLeft, yPos, contentLeft + dateCardWidth, yPos + infoRowHeight)
        canvas.drawRoundRect(dateRect, 8f, 8f, fillWhite)
        canvas.drawRoundRect(dateRect, 8f, 8f, strokeBorder)
        canvas.drawText(tenderData.invoiceDate, contentLeft + dateCardWidth / 2f, yPos + 24f, paintDate)

        yPos += infoRowHeight + 16f

        // ---------- SECTION 3: PRODUCTS TABLE (full width, RTL columns) ----------
        // Column widths (right -> left): رقم الصنف 9% | اسم الصنف 39% | الكمية 15% | السعر 17% | الإجمالي 20%
        val colWidths = floatArrayOf(
            contentWidth * 0.09f,  // رقم الصنف
            contentWidth * 0.39f,  // اسم الصنف
            contentWidth * 0.15f,  // الكمية
            contentWidth * 0.17f,  // السعر ($)
            contentWidth * 0.20f   // الإجمالي ($)
        )
        val headers = arrayOf("#", "اسم الصنف", "الكمية", "السعر ($)", "الإجمالي ($)")

        val headerHeight = 30f
        val rowHeight = 26f

        fun drawTableHeader(): Float {
            val headerRect = RectF(contentLeft, yPos, contentRight, yPos + headerHeight)
            canvas.drawRoundRect(headerRect, 6f, 6f, fillPurple)
            var x = contentRight
            for (i in headers.indices) {
                x -= colWidths[i]
                canvas.drawText(headers[i], x + colWidths[i] / 2f, yPos + 20f, paintHeader)
            }
            return yPos + headerHeight
        }

        fun drawTableSegmentBorders(segmentTop: Float, segmentBottom: Float) {
            canvas.drawRoundRect(
                RectF(contentLeft, segmentTop, contentRight, segmentBottom), 6f, 6f, strokeBorder
            )
            var vx = contentRight
            for (i in colWidths.indices) {
                vx -= colWidths[i]
                if (i < colWidths.lastIndex) {
                    canvas.drawLine(vx, segmentTop, vx, segmentBottom, strokeColumn)
                }
            }
        }

        var segmentTop = yPos
        // Ensure space for header + first row
        if (yPos + headerHeight + rowHeight > pageHeight - margin) newPage()
        yPos = drawTableHeader()

        tenderData.products.forEachIndexed { index, product ->
            if (yPos + rowHeight > pageHeight - margin) {
                drawTableSegmentBorders(segmentTop, yPos)
                newPage()
                segmentTop = yPos
                yPos = drawTableHeader()
            }
            // Row background (alternating)
            val rowRect = RectF(contentLeft, yPos, contentRight, yPos + rowHeight)
            canvas.drawRect(rowRect, if (index % 2 == 0) fillLavender else fillWhite)

            // Row content (right -> left)
            var x = contentRight

            // رقم الصنف
            x -= colWidths[0]
            canvas.drawText(product.itemNumber.toString(), x + colWidths[0] / 2f, yPos + 18f, paintCellBold)

            // اسم الصنف (centered, fits column)
            x -= colWidths[1]
            canvas.drawText(
                fitText(product.productName, paintCellName, colWidths[1] - 12f),
                x + colWidths[1] / 2f, yPos + 18f, paintCellNameCentered
            )

            // الكمية
            x -= colWidths[2]
            canvas.drawText(product.quantity.toString(), x + colWidths[2] / 2f, yPos + 18f, paintCellBold)

            // السعر ($)
            x -= colWidths[3]
            canvas.drawText(money(product.unitPrice), x + colWidths[3] / 2f, yPos + 18f, paintCellBold)

            // الإجمالي ($) — purple bold
            x -= colWidths[4]
            canvas.drawText(money(product.totalPrice), x + colWidths[4] / 2f, yPos + 18f, paintCellTotal)

            // Row separator
            canvas.drawLine(contentLeft, yPos + rowHeight, contentRight, yPos + rowHeight, strokeSeparator)
            yPos += rowHeight
        }

        // Close the final table segment with outer border + vertical separators
        drawTableSegmentBorders(segmentTop, yPos)

        yPos += 16f

        // ---------- SECTION 4: TOTAL SUMMARY — ONE ROW (LEFT purple card 30%, RIGHT words 70%) ----------
        val totalRowHeight = 56f
        val sigRowHeight = 70f
        val gapBetweenRows = 14f
        // Keep total + signatures together
        if (yPos + totalRowHeight + gapBetweenRows + sigRowHeight > pageHeight - margin) newPage()

        val totalCardWidth = contentWidth * 0.30f
        val wordsWidth = contentWidth - totalCardWidth - 8f

        // LEFT: purple total card
        val totalCardRect = RectF(contentLeft, yPos, contentLeft + totalCardWidth, yPos + totalRowHeight)
        canvas.drawRoundRect(totalCardRect, 8f, 8f, fillPurple)
        canvas.drawText("المبلغ الإجمالي", contentLeft + totalCardWidth / 2f, yPos + 19f, paintTotalLabel)
        canvas.drawText(money(tenderData.grandTotal), contentLeft + totalCardWidth / 2f, yPos + 39f, paintTotalAmount)

        // RIGHT: white area with amount in Arabic words
        val wordsRect = RectF(
            contentLeft + totalCardWidth + 8f, yPos,
            contentRight, yPos + totalRowHeight
        )
        canvas.drawRoundRect(wordsRect, 8f, 8f, fillWhite)
        canvas.drawRoundRect(wordsRect, 8f, 8f, strokeBorder)
        val wordsText = ArabicNumberToWords.convertUsdToWords(tenderData.grandTotal)
        val wordsLayout = StaticLayout(
            wordsText, paintWords, (wordsWidth - 16f).toInt(),
            Layout.Alignment.ALIGN_CENTER, 1f, 0f, false
        )
        canvas.save()
        val wordsY = yPos + (totalRowHeight - wordsLayout.height) / 2f
        canvas.translate(contentLeft + totalCardWidth + 8f + 8f, wordsY)
        wordsLayout.draw(canvas)
        canvas.restore()

        yPos += totalRowHeight + gapBetweenRows

        // ---------- SECTION 5: SIGNATURE BOXES — ONE ROW, RTL ----------
        val signatureTitles = arrayOf(
            "مقدم الطلب",   // rightmost
            "مدير الفرع",
            "المالية",
            "مدير التسويق",
            "المدير التجاري" // leftmost
        )
        val sigGap = 6f
        val sigBoxWidth = (contentWidth - sigGap * 4) / 5f
        for (i in signatureTitles.indices) {
            val sigX = contentRight - (i + 1) * sigBoxWidth - i * sigGap
            val sigRect = RectF(sigX, yPos, sigX + sigBoxWidth, yPos + sigRowHeight)
            canvas.drawRoundRect(sigRect, 6f, 6f, fillWhite)
            canvas.drawRoundRect(sigRect, 6f, 6f, strokeBorder)
            // Purple title at top
            canvas.drawText(signatureTitles[i], sigX + sigBoxWidth / 2f, yPos + 15f, paintSignatureTitle)
            // Purple signature line near bottom
            canvas.drawLine(
                sigX + 10f, yPos + sigRowHeight - 12f,
                sigX + sigBoxWidth - 10f, yPos + sigRowHeight - 12f,
                strokeSignatureLine
            )
        }

        pdfDocument.finishPage(page)

        // ---------- Write file ----------
        val safeName = (tenderData.fileBaseName
            ?: "طلب_مناقصة_${tenderData.quotationNumber}_${System.currentTimeMillis()}")
            .replace("[/\\\\:*?\"<>|]".toRegex(), "_")
            .replace("\\s+".toRegex(), "_")
        val file = File(context.cacheDir, "$safeName.pdf")
        FileOutputStream(file).use { out -> pdfDocument.writeTo(out) }
        pdfDocument.close()
        return file
    }

    private fun money(value: Double): String =
        "$" + String.format(Locale.US, "%.2f", value)

    /** Shrinks text size until it fits maxWidth; ellipsizes as last resort. */
    private fun fitText(text: String, paint: Paint, maxWidth: Float): String {
        val tp = TextPaint(paint)
        var size = tp.textSize
        var result = text
        while (tp.measureText(result) > maxWidth && size > 7f) {
            size -= 0.5f
            tp.textSize = size
        }
        if (tp.measureText(result) > maxWidth) {
            result = TextUtils.ellipsize(result, tp, maxWidth, TextUtils.TruncateAt.END).toString()
        }
        return result
    }
}
