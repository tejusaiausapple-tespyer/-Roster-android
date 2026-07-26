package com.surainvestments.roster.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.surainvestments.roster.R
import com.surainvestments.roster.domain.model.AppSettings
import com.surainvestments.roster.domain.model.EmploymentType
import com.surainvestments.roster.domain.model.Payslip
import com.surainvestments.roster.domain.model.PayrollCalculator
import com.surainvestments.roster.domain.model.RosterFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A4 at 72dpi (iOS uses 595.2 x 841.8 — PdfDocument.PageInfo takes integer pixels, negligible rounding).
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 52f
private const val ROW_HEIGHT = 22f
private const val SECTION_GAP = 30f

/**
 * Renders a [Payslip] as a single-page A4 PDF — a **byte-for-byte port of iOS
 * `PayslipPDFService.swift`**, not an independent redesign, since the in-app detail view now
 * displays this exact rendering (see `PayslipDetailScreen`) rather than a separately hand-built
 * Compose layout that could silently drift from it, which is what prompted this rewrite.
 *
 * Design (iOS's own words): "monochrome ink on white — no colour fills or tinted text... the
 * company logo is the single colour element on the page." Hierarchy comes from weight, size,
 * letter-spaced section labels, hairlines and whitespace only.
 */
@Singleton
class PayslipPdfRenderer @Inject constructor(@ApplicationContext private val context: Context) {

    // Exact iOS Theme values (Services/PayslipPDFService.swift) — 0–1 float RGB converted to 0–255 int.
    private val ink = Color.rgb(26, 28, 38) // (0.10, 0.11, 0.15)
    private val secondary = Color.rgb(115, 120, 133) // (0.45, 0.47, 0.52)
    private val rule = Color.rgb(217, 219, 224) // (0.85, 0.86, 0.88)
    private val panelFill = Color.rgb(249, 249, 250) // (0.975, 0.975, 0.98)

    suspend fun render(payslip: Payslip, settings: AppSettings): File = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        draw(page.canvas, payslip, settings)
        document.finishPage(page)

        val dir = File(context.cacheDir, "payslip_shares").apply { mkdirs() }
        val file = File(dir, "payslip-${payslip.id}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        file
    }

    private fun draw(canvas: Canvas, slip: Payslip, settings: AppSettings) {
        val totals = PayrollCalculator.totals(slip)
        val pageWidth = PAGE_WIDTH.toFloat()
        val pageHeight = PAGE_HEIGHT.toFloat()
        val contentWidth = pageWidth - MARGIN * 2
        var y = MARGIN

        // ── Header: logo + company block left, PAYSLIP + status right.
        drawLogo(canvas, x = MARGIN, y = y)
        drawText(canvas, settings.companyName, x = MARGIN + 52, y = y + 1, width = 260f, size = 16f, bold = true, color = ink)
        var companyLineY = y + 22
        val addressLine = settings.businessAddress.ifBlank {
            listOf(settings.businessStreet, settings.businessSuburb, settings.businessState).filter { it.isNotBlank() }.joinToString(", ")
        }
        if (addressLine.isNotBlank()) {
            companyLineY += drawText(canvas, addressLine, x = MARGIN + 52, y = companyLineY, width = 260f, size = 9f, color = secondary)
        }
        if (settings.abn.isNotBlank()) {
            drawText(canvas, "ABN ${settings.abn}", x = MARGIN + 52, y = companyLineY, width = 260f, size = 9f, color = secondary)
        }
        drawText(canvas, "PAYSLIP", x = pageWidth - MARGIN - 160, y = y + 1, width = 160f, size = 19f, bold = true, color = ink, align = Layout.Alignment.ALIGN_OPPOSITE, letterSpacingEm = 2.5f / 19f)
        drawText(canvas, slip.status.label.uppercase(Locale.ENGLISH), x = pageWidth - MARGIN - 160, y = y + 28, width = 160f, size = 8f, bold = true, color = secondary, align = Layout.Alignment.ALIGN_OPPOSITE, letterSpacingEm = 1.5f / 8f)
        y += 58
        hairline(canvas, y, pageWidth)
        y += 22

        // ── Employee + period two-column block.
        val leftPairs = listOf(
            "Employee" to slip.staffName,
            "Employee ID" to slip.employeeId.ifBlank { "—" },
            "Position" to slip.position.ifBlank { "—" },
            "Employment type" to (EmploymentType.fromRaw(slip.employmentType)?.label ?: "—"),
        )
        val rightPairs = listOf(
            "Pay period" to "${RosterFormat.dateShort(slip.periodStart)} – ${RosterFormat.dateShort(slip.periodEnd)}",
            "Pay date" to RosterFormat.dateShort(slip.payDate),
            "Award" to slip.awardName.ifBlank { "—" }.let { if (slip.awardName.isBlank()) it else awardLabel(slip) },
            "Classification" to slip.classification.ifBlank { "—" },
        )
        val colWidth = contentWidth / 2
        var leftY = y
        leftPairs.forEach { (label, value) -> leftY += drawPair(canvas, label, value, x = MARGIN, y = leftY, width = colWidth - 16) }
        var rightY = y
        rightPairs.forEach { (label, value) -> rightY += drawPair(canvas, label, value, x = MARGIN + colWidth + 8, y = rightY, width = colWidth - 8) }
        y = maxOf(leftY, rightY) + SECTION_GAP - 12

        // ── Earnings table.
        y = sectionTitle(canvas, "EARNINGS", y, pageWidth)
        y = tableHeader(canvas, y, pageWidth)

        val rows = PayrollCalculator.earningsRows(slip).map { row ->
            listOf(row.label, String.format(Locale.ENGLISH, "%.2f", row.hours), RosterFormat.money(row.rate), RosterFormat.money(row.amount))
        }.ifEmpty { listOf(listOf("No earnings recorded", "—", "—", RosterFormat.money(0.0))) }
        rows.forEach { row -> y = tableRow(canvas, y, row, pageWidth) }
        y = totalRow(canvas, y, "Gross earnings", totals.gross, pageWidth)
        y += SECTION_GAP

        // ── Tax & deductions.
        y = sectionTitle(canvas, "TAX & DEDUCTIONS", y, pageWidth)
        val deductionRows = buildList {
            add(listOf("PAYG withholding", "", "", RosterFormat.money(totals.tax)))
            if (slip.salarySacrifice > 0) add(listOf("Salary sacrifice", "", "", RosterFormat.money(slip.salarySacrifice)))
            if (slip.otherDeductions > 0) {
                val label = if (slip.deductionNotes.isBlank()) "Other deductions" else "Other — ${slip.deductionNotes}"
                add(listOf(label, "", "", RosterFormat.money(slip.otherDeductions)))
            }
        }
        deductionRows.forEach { row -> y = tableRow(canvas, y, row, pageWidth) }
        y = totalRow(canvas, y, "Total tax & deductions", totals.tax + totals.deductions, pageWidth)
        y += SECTION_GAP

        // ── Superannuation — omitted entirely when disabled (e.g. under-18 staff not entitled to SG).
        val hasSuper = slip.superRate > 0
        if (hasSuper) {
            y = sectionTitle(canvas, "SUPERANNUATION", y, pageWidth)
            y = tableRow(canvas, y, listOf("Employer contribution (SG ${RosterFormat.decimalHours(slip.superRate)}%)", "", "", RosterFormat.money(totals.superAmount)), pageWidth)
            y += SECTION_GAP
        }

        // ── Net pay: bordered panel, ink text — no colour fill beyond the near-white panel tint.
        val panelRect = RectF(MARGIN, y, pageWidth - MARGIN, y + 46f)
        canvas.drawRoundRect(panelRect, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = panelFill })
        canvas.drawRoundRect(panelRect, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = rule })
        drawText(canvas, "NET PAY", x = MARGIN + 18, y = y + 17, width = 200f, size = 10f, bold = true, color = ink, letterSpacingEm = 1.5f / 10f)
        drawText(canvas, RosterFormat.money(totals.net), x = pageWidth - MARGIN - 218, y = y + 13, width = 200f, size = 17f, bold = true, color = ink, align = Layout.Alignment.ALIGN_OPPOSITE)
        y += 46 + 20

        // ── Notes.
        if (slip.notes.isNotBlank()) {
            y += drawText(canvas, "Notes: ${slip.notes}", x = MARGIN, y = y, width = contentWidth, size = 9f, color = secondary)
            y += 28
        }

        // ── Footer, pinned to the bottom of the page (not part of the flowing layout above).
        val footerY = pageHeight - MARGIN - 30
        hairline(canvas, footerY - 10, pageWidth)
        val footerText = if (hasSuper) {
            "Superannuation is paid by the employer to the employee's nominated fund and is not included in net pay. This payslip is issued in accordance with the Fair Work Act 2009 record-keeping requirements."
        } else {
            "This payslip is issued in accordance with the Fair Work Act 2009 record-keeping requirements."
        }
        drawText(canvas, footerText, x = MARGIN, y = footerY, width = contentWidth, size = 7.5f, color = secondary)
        val generatedAt = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH).withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.now())
        drawText(canvas, "Generated by ${settings.companyName} · $generatedAt", x = MARGIN, y = footerY + 21, width = contentWidth, size = 7.5f, color = secondary)
    }

    private fun awardLabel(slip: Payslip): String =
        if (slip.awardCode.isBlank()) slip.awardName else "${slip.awardName} (${slip.awardCode})"

    // ── Drawing primitives — StaticLayout throughout so `y` uniformly means "top of the text
    // block" (matching iOS's NSString draw(in:) semantics), with wrapping, alignment, and
    // letter-spacing (converted from iOS's point-based kern to Android's em-based letterSpacing:
    // em = kernPoints / fontSizePoints) all going through one path. Returns the height consumed.

    private fun drawLogo(canvas: Canvas, x: Float, y: Float) {
        val bitmap = runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.app_logo) }.getOrNull() ?: return
        val scaled = Bitmap.createScaledBitmap(bitmap, 40, 40, true)
        canvas.save()
        val clip = Path().apply { addRoundRect(RectF(x, y, x + 40f, y + 40f), 9f, 9f, Path.Direction.CW) }
        canvas.clipPath(clip)
        canvas.drawBitmap(scaled, x, y, null)
        canvas.restore()
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        letterSpacingEm: Float = 0f,
    ): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            letterSpacing = letterSpacingEm
        }
        val safeWidth = width.toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(align)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    /** Label/value pair (label left-column, value right of it) — long values wrap, returning the taller of the two as the row height consumed, mirroring iOS `drawPair`. */
    private fun drawPair(canvas: Canvas, label: String, value: String, x: Float, y: Float, width: Float): Float {
        drawText(canvas, label, x = x, y = y, width = 104f, size = 9f, color = secondary)
        val valueHeight = drawText(canvas, value, x = x + 104, y = y, width = width - 104, size = 9.5f, bold = true, color = ink)
        return maxOf(19f, valueHeight + 7f)
    }

    private fun hairline(canvas: Canvas, y: Float, pageWidth: Float) {
        canvas.drawLine(MARGIN, y, pageWidth - MARGIN, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 0.7f; color = rule })
    }

    /** Letter-spaced section label with a hairline underneath. */
    private fun sectionTitle(canvas: Canvas, title: String, y: Float, pageWidth: Float): Float {
        drawText(canvas, title, x = MARGIN, y = y, width = pageWidth - MARGIN * 2, size = 9f, bold = true, color = ink, letterSpacingEm = 1.8f / 9f)
        return y + 20
    }

    private val columnOffsets = listOf(0f, 0.52f, 0.68f, 0.84f)

    private fun tableHeader(canvas: Canvas, y: Float, pageWidth: Float): Float {
        val width = pageWidth - MARGIN * 2
        listOf("Description", "Hours/Units", "Rate", "Amount").forEachIndexed { index, title ->
            val isFirst = index == 0
            drawText(
                canvas, title, x = MARGIN + width * columnOffsets[index], y = y, width = width * 0.16f,
                size = 8f, color = secondary, align = if (isFirst) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE,
            )
        }
        val bottom = y + 15
        hairline(canvas, bottom, pageWidth)
        return bottom + 7
    }

    private fun tableRow(canvas: Canvas, y: Float, values: List<String>, pageWidth: Float): Float {
        val width = pageWidth - MARGIN * 2
        values.forEachIndexed { index, value ->
            if (value.isEmpty()) return@forEachIndexed
            val isFirst = index == 0
            drawText(
                canvas, value, x = MARGIN + width * columnOffsets[index], y = y,
                width = if (isFirst) width * 0.5f else width * 0.16f,
                size = 9.5f, bold = !isFirst, color = ink,
                align = if (isFirst) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE,
            )
        }
        return y + ROW_HEIGHT
    }

    private fun totalRow(canvas: Canvas, y: Float, label: String, amount: Double, pageWidth: Float): Float {
        hairline(canvas, y - 2, pageWidth)
        val width = pageWidth - MARGIN * 2
        val rowY = y + 7
        drawText(canvas, label, x = MARGIN, y = rowY, width = width * 0.6f, size = 9.5f, bold = true, color = ink)
        drawText(canvas, RosterFormat.money(amount), x = MARGIN + width * 0.84f, y = rowY, width = width * 0.16f, size = 10.5f, bold = true, color = ink, align = Layout.Alignment.ALIGN_OPPOSITE)
        return rowY + 20
    }
}
