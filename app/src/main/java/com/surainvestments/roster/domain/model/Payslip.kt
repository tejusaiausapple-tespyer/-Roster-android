package com.surainvestments.roster.domain.model

import java.time.Instant

/** Mirrors iOS `PayslipStatus` (`Models/PayrollModels.swift`). */
enum class PayslipStatus(val rawValue: String) {
    Draft("draft"),
    UnderReview("under_review"),
    Approved("approved"),
    Submitted("submitted"),
    Archived("archived"),
    ;

    /** Staff may see a payslip only in these two terminal states — rules-enforced, not just this check. */
    val isStaffVisible: Boolean get() = this == Submitted || this == Archived

    companion object {
        fun fromRaw(value: String?): PayslipStatus = entries.firstOrNull { it.rawValue == value } ?: Draft
    }
}

/** One earnings row on a payslip (snapshot). Mirrors iOS `PayslipEarning`. */
data class PayslipEarning(
    val id: String,
    val name: String,
    val quantity: Double,
    val rate: Double,
    val amount: Double,
    val exemptFromTax: Boolean,
    val exemptFromSuper: Boolean,
) {
    companion object {
        fun fromMap(data: Map<String, Any?>): PayslipEarning =
            PayslipEarning(
                id = data.fsString("id") ?: "",
                name = data.fsString("name") ?: "",
                quantity = data.fsDouble("quantity"),
                rate = data.fsDouble("rate"),
                amount = data.fsDouble("amount"),
                exemptFromTax = data.fsBoolean("exemptFromTax"),
                exemptFromSuper = data.fsBoolean("exemptFromSuper"),
            )
    }
}

/**
 * `payslips/{periodStart}_{staffId}` — staff-visible slice only (read-only client; generation/
 * editing is manager-only and out of scope here). Mirrors iOS `Payslip`
 * (`Models/PayrollModels.swift`). Everything money-related is a stored *snapshot* — later edits
 * to wage profiles/awards/rosters never retroactively change an issued payslip.
 *
 * Audit trail (`audit[]`) is deliberately not parsed: staff never see it (manager-only surface
 * in both source apps).
 */
data class Payslip(
    val id: String,
    val staffId: String,
    val staffName: String,
    val employeeId: String,
    val tfnLast4: String,
    val position: String,
    val employmentType: String,
    val awardName: String,
    val awardCode: String,
    val classification: String,
    val periodStart: String, // yyyy-MM-dd, Monday
    val periodEnd: String, // yyyy-MM-dd, Sunday
    val payDate: String,
    val status: PayslipStatus,
    val baseHourlyRate: Double,
    val ordinaryHours: Double,
    val weekendHours: Double,
    val weekendRate: Double,
    val publicHolidayHours: Double,
    val publicHolidayRate: Double,
    val overtimeHours: Double,
    val overtimeRate: Double,
    val extraEarnings: List<PayslipEarning>,
    val payg: Double,
    val otherDeductions: Double,
    val salarySacrifice: Double,
    val deductionNotes: String,
    val superRate: Double,
    val notes: String,
    val generatedAt: Instant?,
    val updatedAt: Instant?,
    val approvedAt: Instant?,
    val submittedAt: Instant?,
) {
    val totals: PayrollCalculator.Totals get() = PayrollCalculator.totals(this)

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromDocument(id: String, data: Map<String, Any?>): Payslip? {
            val staffId = data.fsString("staffId") ?: return null
            return Payslip(
                id = id,
                staffId = staffId,
                staffName = data.fsString("staffName") ?: "",
                employeeId = data.fsString("employeeId") ?: "",
                tfnLast4 = data.fsString("tfnLast4") ?: "",
                position = data.fsString("position") ?: "",
                employmentType = data.fsString("employmentType") ?: "",
                awardName = data.fsString("awardName") ?: "",
                awardCode = data.fsString("awardCode") ?: "",
                classification = data.fsString("classification") ?: "",
                periodStart = data.fsString("periodStart") ?: "",
                periodEnd = data.fsString("periodEnd") ?: "",
                payDate = data.fsString("payDate") ?: "",
                status = PayslipStatus.fromRaw(data.fsString("status")),
                baseHourlyRate = data.fsDouble("baseHourlyRate"),
                ordinaryHours = data.fsDouble("ordinaryHours"),
                weekendHours = data.fsDouble("weekendHours"),
                weekendRate = data.fsDouble("weekendRate"),
                publicHolidayHours = data.fsDouble("publicHolidayHours"),
                publicHolidayRate = data.fsDouble("publicHolidayRate"),
                overtimeHours = data.fsDouble("overtimeHours"),
                overtimeRate = data.fsDouble("overtimeRate"),
                extraEarnings = (data["extraEarnings"] as? List<*>)
                    ?.filterIsInstance<Map<String, Any?>>()
                    ?.map { PayslipEarning.fromMap(it) }
                    ?: emptyList(),
                payg = data.fsDouble("payg"),
                otherDeductions = data.fsDouble("otherDeductions"),
                salarySacrifice = data.fsDouble("salarySacrifice"),
                deductionNotes = data.fsString("deductionNotes") ?: "",
                superRate = if (data.containsKey("superRate")) data.fsDouble("superRate") else 12.0,
                notes = data.fsString("notes") ?: "",
                generatedAt = data.fsInstant("generatedAt"),
                updatedAt = data.fsInstant("updatedAt"),
                approvedAt = data.fsInstant("approvedAt"),
                submittedAt = data.fsInstant("submittedAt"),
            )
        }
    }
}

/** Pure payroll arithmetic — unit-tested, no Firestore/UI dependency. Mirrors iOS `PayrollCalculator`. */
object PayrollCalculator {
    data class Totals(
        val ordinaryAmount: Double,
        val weekendAmount: Double,
        val publicHolidayAmount: Double,
        val overtimeAmount: Double,
        val extrasAmount: Double,
        val gross: Double,
        val tax: Double,
        val deductions: Double,
        val superAmount: Double,
        val net: Double,
        val totalHours: Double,
    )

    fun totals(slip: Payslip): Totals {
        val ordinary = round2(slip.ordinaryHours * slip.baseHourlyRate)
        val weekend = round2(slip.weekendHours * slip.weekendRate)
        val publicHoliday = round2(slip.publicHolidayHours * slip.publicHolidayRate)
        val overtime = round2(slip.overtimeHours * slip.overtimeRate)
        val extras = round2(slip.extraEarnings.sumOf { it.amount })
        val gross = round2(ordinary + weekend + publicHoliday + overtime + extras)

        // Super guarantee applies to ordinary-time earnings — overtime is excluded, as are
        // earnings rows flagged exempt (ATO SGR 2009/2).
        val superableExtras = slip.extraEarnings.filter { !it.exemptFromSuper }.sumOf { it.amount }
        val ote = ordinary + weekend + publicHoliday + superableExtras
        val superAmount = round2(ote * slip.superRate / 100)

        val deductions = round2(slip.otherDeductions + slip.salarySacrifice)
        val net = round2(gross - slip.payg - deductions)

        return Totals(
            ordinaryAmount = ordinary,
            weekendAmount = weekend,
            publicHolidayAmount = publicHoliday,
            overtimeAmount = overtime,
            extrasAmount = extras,
            gross = gross,
            tax = round2(slip.payg),
            deductions = deductions,
            superAmount = superAmount,
            net = net,
            totalHours = slip.ordinaryHours + slip.weekendHours + slip.publicHolidayHours + slip.overtimeHours,
        )
    }

    fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

    /** One populated earnings-table row, in display order — shared by the PDF renderer and any in-app breakdown. */
    data class EarningsRow(val label: String, val hours: Double, val rate: Double, val amount: Double)

    /** Only earnings rows with a non-zero quantity are shown; extras always follow the standard rows. */
    fun earningsRows(slip: Payslip): List<EarningsRow> {
        val totals = totals(slip)
        return buildList {
            if (slip.ordinaryHours > 0) add(EarningsRow("Ordinary hours", slip.ordinaryHours, slip.baseHourlyRate, totals.ordinaryAmount))
            if (slip.weekendHours > 0) add(EarningsRow("Weekend hours", slip.weekendHours, slip.weekendRate, totals.weekendAmount))
            if (slip.publicHolidayHours > 0) add(EarningsRow("Public holiday hours", slip.publicHolidayHours, slip.publicHolidayRate, totals.publicHolidayAmount))
            if (slip.overtimeHours > 0) add(EarningsRow("Overtime hours", slip.overtimeHours, slip.overtimeRate, totals.overtimeAmount))
            slip.extraEarnings.forEach { add(EarningsRow(it.name, it.quantity, it.rate, it.amount)) }
        }
    }
}
