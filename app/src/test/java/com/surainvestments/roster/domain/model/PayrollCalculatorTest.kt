package com.surainvestments.roster.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises `PayrollCalculator`'s money math directly — the first UI to consume it (staff Payslips) has zero tolerance for a silent rounding/exemption bug. */
class PayrollCalculatorTest {

    private fun payslip(
        ordinaryHours: Double = 0.0,
        baseHourlyRate: Double = 0.0,
        weekendHours: Double = 0.0,
        weekendRate: Double = 0.0,
        publicHolidayHours: Double = 0.0,
        publicHolidayRate: Double = 0.0,
        overtimeHours: Double = 0.0,
        overtimeRate: Double = 0.0,
        extraEarnings: List<PayslipEarning> = emptyList(),
        payg: Double = 0.0,
        otherDeductions: Double = 0.0,
        salarySacrifice: Double = 0.0,
        superRate: Double = 12.0,
    ) = Payslip(
        id = "p1", staffId = "staff-1", staffName = "Test Staff", employeeId = "E1", tfnLast4 = "123",
        position = "Console Operator", employmentType = "casual", awardName = "Test Award", awardCode = "T1",
        classification = "Level 1", periodStart = "2026-06-01", periodEnd = "2026-06-07", payDate = "2026-06-10",
        status = PayslipStatus.Submitted,
        baseHourlyRate = baseHourlyRate, ordinaryHours = ordinaryHours,
        weekendHours = weekendHours, weekendRate = weekendRate,
        publicHolidayHours = publicHolidayHours, publicHolidayRate = publicHolidayRate,
        overtimeHours = overtimeHours, overtimeRate = overtimeRate,
        extraEarnings = extraEarnings,
        payg = payg, otherDeductions = otherDeductions, salarySacrifice = salarySacrifice,
        deductionNotes = "", superRate = superRate, notes = "",
        generatedAt = null, updatedAt = null, approvedAt = null, submittedAt = null,
    )

    private fun earning(name: String, quantity: Double, rate: Double, amount: Double, exemptFromSuper: Boolean = false, exemptFromTax: Boolean = false) =
        PayslipEarning(id = name, name = name, quantity = quantity, rate = rate, amount = amount, exemptFromTax = exemptFromTax, exemptFromSuper = exemptFromSuper)

    @Test
    fun `ordinary hours only`() {
        val slip = payslip(ordinaryHours = 20.0, baseHourlyRate = 25.0, payg = 50.0, superRate = 12.0)
        val t = PayrollCalculator.totals(slip)

        assertEquals(500.0, t.ordinaryAmount, 0.0)
        assertEquals(500.0, t.gross, 0.0)
        assertEquals(60.0, t.superAmount, 0.0) // 500 * 12%
        assertEquals(450.0, t.net, 0.0) // 500 - 50 payg
        assertEquals(20.0, t.totalHours, 0.0)
    }

    @Test
    fun `weekend, public holiday and overtime all combine into gross`() {
        val slip = payslip(
            ordinaryHours = 10.0, baseHourlyRate = 20.0,
            weekendHours = 5.0, weekendRate = 30.0,
            publicHolidayHours = 4.0, publicHolidayRate = 45.0,
            overtimeHours = 2.0, overtimeRate = 30.0,
        )
        val t = PayrollCalculator.totals(slip)

        assertEquals(200.0, t.ordinaryAmount, 0.0)
        assertEquals(150.0, t.weekendAmount, 0.0)
        assertEquals(180.0, t.publicHolidayAmount, 0.0)
        assertEquals(60.0, t.overtimeAmount, 0.0)
        assertEquals(590.0, t.gross, 0.0)
        assertEquals(21.0, t.totalHours, 0.0)
    }

    @Test
    fun `overtime is excluded from the superannuation base but ordinary, weekend and PH are not`() {
        val slip = payslip(
            ordinaryHours = 10.0, baseHourlyRate = 20.0, // 200
            weekendHours = 5.0, weekendRate = 30.0, // 150
            publicHolidayHours = 2.0, publicHolidayRate = 45.0, // 90
            overtimeHours = 10.0, overtimeRate = 30.0, // 300 gross, but must NOT count toward super
            superRate = 10.0,
        )
        val t = PayrollCalculator.totals(slip)

        // OTE = 200 + 150 + 90 = 440; super = 44. If overtime were wrongly included, this would be 84.
        assertEquals(44.0, t.superAmount, 0.0)
        assertEquals(740.0, t.gross, 0.0)
    }

    @Test
    fun `extra earnings add to gross and are superable unless flagged exempt`() {
        val slip = payslip(
            ordinaryHours = 10.0, baseHourlyRate = 20.0, // 200, superable
            extraEarnings = listOf(
                earning("Allowance", 1.0, 50.0, 50.0, exemptFromSuper = false),
                earning("Reimbursement", 1.0, 30.0, 30.0, exemptFromSuper = true),
            ),
            superRate = 10.0,
        )
        val t = PayrollCalculator.totals(slip)

        assertEquals(80.0, t.extrasAmount, 0.0)
        assertEquals(280.0, t.gross, 0.0) // 200 + 50 + 30
        // OTE = 200 (ordinary) + 50 (non-exempt allowance) = 250; super = 25. The 30 reimbursement is excluded.
        assertEquals(25.0, t.superAmount, 0.0)
    }

    @Test
    fun `zero super rate omits super entirely without affecting net`() {
        val slip = payslip(ordinaryHours = 10.0, baseHourlyRate = 20.0, payg = 20.0, superRate = 0.0)
        val t = PayrollCalculator.totals(slip)

        assertEquals(0.0, t.superAmount, 0.0)
        assertEquals(180.0, t.net, 0.0) // net is gross - payg - deductions; super never subtracts from it either way
    }

    @Test
    fun `other deductions and salary sacrifice both reduce net but never gross`() {
        val slip = payslip(ordinaryHours = 10.0, baseHourlyRate = 20.0, payg = 10.0, otherDeductions = 15.0, salarySacrifice = 25.0)
        val t = PayrollCalculator.totals(slip)

        assertEquals(200.0, t.gross, 0.0)
        assertEquals(40.0, t.deductions, 0.0)
        assertEquals(150.0, t.net, 0.0) // 200 - 10 payg - 40 deductions
    }

    @Test
    fun `round2 rounds half up to 2 decimal places`() {
        assertEquals(12.35, PayrollCalculator.round2(12.345), 0.0)
        assertEquals(0.01, PayrollCalculator.round2(0.005), 0.0)
        assertEquals(100.0, PayrollCalculator.round2(99.999), 0.0)
    }

    @Test
    fun `earningsRows omits zero-quantity standard rows and always appends extras last`() {
        val slip = payslip(
            ordinaryHours = 10.0, baseHourlyRate = 20.0,
            weekendHours = 0.0, weekendRate = 30.0, // present rate but zero hours — must be omitted
            overtimeHours = 3.0, overtimeRate = 30.0,
            extraEarnings = listOf(earning("Bonus", 1.0, 100.0, 100.0)),
        )
        val rows = PayrollCalculator.earningsRows(slip)

        assertEquals(listOf("Ordinary hours", "Overtime hours", "Bonus"), rows.map { it.label })
        assertTrue("weekend row must not appear when hours are zero", rows.none { it.label == "Weekend hours" })
    }

    @Test
    fun `earningsRows on an all-zero payslip is empty`() {
        assertEquals(emptyList<PayrollCalculator.EarningsRow>(), PayrollCalculator.earningsRows(payslip()))
    }
}
