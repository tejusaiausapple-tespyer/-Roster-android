package com.surainvestments.roster.data.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.surainvestments.roster.domain.model.AppSettings
import com.surainvestments.roster.domain.model.Payslip
import com.surainvestments.roster.domain.model.PayrollCalculator
import com.surainvestments.roster.domain.model.RosterFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A4 in points at 72dpi.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 42f

/**
 * Renders a [Payslip] as a single-page, monochrome, ink-on-white A4 PDF — mirrors iOS
 * `PayslipPDFService`'s layout (company block, employee/period columns, earnings table, tax &
 * deductions, superannuation, bordered net-pay panel, Fair Work Act footer) so an export looks
 * the same regardless of which platform a staff member is on. Used only when exporting/sharing —
 * the in-app detail screen is a native, theme-aware Compose view, not a rendering of this file.
 */
@Singleton
class PayslipPdfRenderer @Inject constructor(@ApplicationContext private val context: Context) {

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
        val rightX = PAGE_WIDTH - MARGIN
        val colHoursX = rightX - 200f
        val colRateX = rightX - 105f

        val sectionLabel = textPaint(size = 8.5f, bold = true, color = Color.rgb(90, 90, 90), letterSpacing = 0.12f)
        val body = textPaint(size = 10.5f)
        val bodyMuted = textPaint(size = 9.5f, color = Color.DKGRAY)
        val bodyBold = textPaint(size = 10.5f, bold = true)
        val companyName = textPaint(size = 17f, bold = true, letterSpacing = 0.02f)
        val payslipTitle = textPaint(size = 12.5f, bold = true, letterSpacing = 0.08f)
        val hairline = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 0.75f; color = Color.rgb(200, 200, 200) }
        val netPayLabel = textPaint(size = 12f, bold = true)
        val netPayValue = textPaint(size = 15f, bold = true)
        val footer = textPaint(size = 7.5f, color = Color.GRAY)

        var y = MARGIN + 14f

        // Company header.
        canvas.drawText(settings.companyName.uppercase(), MARGIN, y, companyName)
        y += 18f
        listOfNotNull(
            settings.businessAddress.takeIf { it.isNotBlank() },
            listOfNotNull(
                settings.abn.takeIf { it.isNotBlank() }?.let { "ABN $it" },
                settings.acn.takeIf { it.isNotBlank() }?.let { "ACN $it" },
            ).joinToString("   ").takeIf { it.isNotBlank() },
        ).forEach { line ->
            canvas.drawText(line, MARGIN, y, bodyMuted)
            y += 13f
        }
        y += 8f
        canvas.drawLine(MARGIN, y, rightX, y, hairline)
        y += 24f

        // Title row: "PAYSLIP" + period range, right-aligned.
        canvas.drawText("PAYSLIP", MARGIN, y, payslipTitle)
        canvas.drawText(
            "${RosterFormat.dateShort(slip.periodStart)} – ${RosterFormat.dateShort(slip.periodEnd)}",
            rightX,
            y,
            rightAligned(body),
        )
        y += 26f

        // Employee / period two-column block.
        val colStart = y
        val col2X = MARGIN + (rightX - MARGIN) / 2f
        val leftEnd = drawFieldColumn(
            canvas, MARGIN, colStart, sectionLabel, body,
            "EMPLOYEE" to slip.staffName.ifBlank { "—" },
            "EMPLOYEE ID" to slip.employeeId.ifBlank { "—" },
            "POSITION" to slip.position.ifBlank { "—" },
            "CLASSIFICATION" to slip.classification.ifBlank { slip.awardName.ifBlank { "—" } },
        )
        val rightEnd = drawFieldColumn(
            canvas, col2X, colStart, sectionLabel, body,
            "PAY DATE" to RosterFormat.dateShort(slip.payDate),
            "TFN" to (slip.tfnLast4.takeIf { it.isNotBlank() }?.let { "*** *** $it" } ?: "—"),
            "EMPLOYMENT TYPE" to slip.employmentType.replaceFirstChar { it.uppercase() }.ifBlank { "—" },
        )
        y = maxOf(leftEnd, rightEnd) + 8f
        canvas.drawLine(MARGIN, y, rightX, y, hairline)
        y += 22f

        // Earnings table.
        canvas.drawText("EARNINGS", MARGIN, y, sectionLabel)
        y += 8f
        canvas.drawText("HOURS", colHoursX, y, rightAligned(sectionLabel))
        canvas.drawText("RATE", colRateX, y, rightAligned(sectionLabel))
        canvas.drawText("AMOUNT", rightX, y, rightAligned(sectionLabel))
        y += 16f

        val rows = PayrollCalculator.earningsRows(slip)
        if (rows.isEmpty()) {
            canvas.drawText("No earnings recorded for this period.", MARGIN, y, bodyMuted)
            y += 18f
        } else {
            rows.forEach { row ->
                canvas.drawText(row.label, MARGIN, y, body)
                canvas.drawText(String.format(Locale.ENGLISH, "%.2f", row.hours), colHoursX, y, rightAligned(body))
                canvas.drawText(RosterFormat.money(row.rate), colRateX, y, rightAligned(body))
                canvas.drawText(RosterFormat.money(row.amount), rightX, y, rightAligned(body))
                y += 17f
            }
        }
        y += 6f
        canvas.drawLine(MARGIN, y, rightX, y, hairline)
        y += 18f
        canvas.drawText("GROSS PAY", MARGIN, y, bodyBold)
        canvas.drawText(RosterFormat.money(totals.gross), rightX, y, rightAligned(bodyBold))
        y += 28f

        // Tax & deductions.
        canvas.drawText("TAX & DEDUCTIONS", MARGIN, y, sectionLabel)
        y += 18f
        canvas.drawText("PAYG withholding", MARGIN, y, body)
        canvas.drawText("-${RosterFormat.money(totals.tax)}", rightX, y, rightAligned(body))
        y += 17f
        if (slip.otherDeductions > 0) {
            canvas.drawText("Other deductions", MARGIN, y, body)
            canvas.drawText("-${RosterFormat.money(slip.otherDeductions)}", rightX, y, rightAligned(body))
            y += 17f
        }
        if (slip.salarySacrifice > 0) {
            canvas.drawText("Salary sacrifice", MARGIN, y, body)
            canvas.drawText("-${RosterFormat.money(slip.salarySacrifice)}", rightX, y, rightAligned(body))
            y += 17f
        }
        y += 10f

        // Superannuation — omitted entirely when the staff member's super is disabled.
        if (slip.superRate > 0) {
            canvas.drawText("SUPERANNUATION", MARGIN, y, sectionLabel)
            y += 18f
            canvas.drawText("Super guarantee (${RosterFormat.decimalHours(slip.superRate)}%)", MARGIN, y, body)
            canvas.drawText(RosterFormat.money(totals.superAmount), rightX, y, rightAligned(body))
            y += 17f
            canvas.drawText("Paid by your employer — not deducted from net pay", MARGIN, y, bodyMuted.apply { textSize = 8f })
            y += 24f
        }

        // Net pay — bordered panel.
        val panelHeight = 42f
        canvas.drawRect(MARGIN, y, rightX, y + panelHeight, Paint(hairline).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.BLACK })
        canvas.drawText("NET PAY", MARGIN + 14f, y + panelHeight / 2f + 5f, netPayLabel)
        canvas.drawText(RosterFormat.money(totals.net), rightX - 14f, y + panelHeight / 2f + 5f, rightAligned(netPayValue))

        // Footer disclaimer, pinned near the bottom of the page.
        canvas.drawText(
            "Issued in accordance with the Fair Work Act 2009 (Cth) and the Fair Work Regulations 2009.",
            MARGIN,
            PAGE_HEIGHT - MARGIN,
            footer,
        )
    }

    /** Draws stacked "LABEL" / value pairs starting at [startY]; returns the y position after the last one. */
    private fun drawFieldColumn(
        canvas: Canvas,
        x: Float,
        startY: Float,
        labelPaint: Paint,
        valuePaint: Paint,
        vararg fields: Pair<String, String>,
    ): Float {
        var y = startY
        fields.forEach { (label, value) ->
            canvas.drawText(label, x, y, labelPaint)
            y += 12f
            canvas.drawText(value, x, y, valuePaint)
            y += 18f
        }
        return y
    }

    private fun textPaint(size: Float, bold: Boolean = false, color: Int = Color.BLACK, letterSpacing: Float = 0f): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.letterSpacing = letterSpacing
        }

    private fun rightAligned(paint: Paint): Paint = Paint(paint).apply { textAlign = Paint.Align.RIGHT }
}
