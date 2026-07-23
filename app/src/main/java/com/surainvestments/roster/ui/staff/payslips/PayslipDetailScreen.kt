package com.surainvestments.roster.ui.staff.payslips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.surainvestments.roster.domain.model.Payslip
import com.surainvestments.roster.domain.model.PayrollCalculator
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.ui.components.PrimaryButton
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.TextTertiaryLight

/**
 * Native, theme-aware payslip breakdown — the staff member's normal way of *viewing* a payslip.
 * The monochrome A4 PDF (via [PayslipPdfRenderer][com.surainvestments.roster.data.service.PayslipPdfRenderer])
 * is generated only on demand for the Share action, not rendered back into this screen: a staff
 * payslip is an immutable, already-issued snapshot with no manager-editing preview to stay in
 * sync with, so there's no WYSIWYG risk in using the platform-idiomatic Compose view day to day.
 */
@Composable
fun PayslipDetailScreen(
    payslip: Payslip,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navBarPadding = LocalNavBarPadding.current
    val totals = remember(payslip) { payslip.totals }
    val rows = remember(payslip) { PayrollCalculator.earningsRows(payslip) }
    val shareAction = rememberSharePayslipAction()

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(navBarPadding)
                .padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(modifier = Modifier.height(ScreenPillTopBarHeight + 8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "${RosterFormat.dateShort(payslip.periodStart)} – ${RosterFormat.dateShort(payslip.periodEnd)}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Paid ${RosterFormat.dateShort(payslip.payDate)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            RosterCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow("Position", payslip.position.ifBlank { "—" })
                    DetailRow("Classification", payslip.classification.ifBlank { payslip.awardName.ifBlank { "—" } })
                    DetailRow("Employee ID", payslip.employeeId.ifBlank { "—" })
                }
            }

            RosterCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Earnings")
                    if (rows.isEmpty()) {
                        Text(
                            "No earnings recorded for this period.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        rows.forEach { row ->
                            EarningsRowLine(row)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TotalLine(label = "Gross pay", amount = totals.gross, bold = true)
                }
            }

            RosterCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Tax & deductions")
                    TotalLine(label = "PAYG withholding", amount = -totals.tax)
                    if (payslip.otherDeductions > 0) TotalLine(label = "Other deductions", amount = -payslip.otherDeductions)
                    if (payslip.salarySacrifice > 0) TotalLine(label = "Salary sacrifice", amount = -payslip.salarySacrifice)
                }
            }

            if (payslip.superRate > 0) {
                RosterCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Superannuation")
                        TotalLine(label = "Super guarantee (${RosterFormat.decimalHours(payslip.superRate)}%)", amount = totals.superAmount)
                        Text(
                            text = "Paid by your employer — not deducted from your net pay.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            RosterCard(accentColor = BrandIndigoStrong) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("NET PAY", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = RosterFormat.money(totals.net),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = BrandIndigoStrong,
                    )
                }
            }

            PrimaryButton(
                text = "Share payslip",
                onClick = { shareAction.share(payslip) },
                loading = shareAction.isSharing,
                leadingIcon = Icons.Outlined.Share,
            )

            Box(modifier = Modifier.height(24.dp))
        }

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(
            title = "Payslip",
            icon = Icons.Outlined.Payments,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = TextTertiaryLight,
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun EarningsRowLine(row: PayrollCalculator.EarningsRow) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(row.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${String.format(Locale.ENGLISH, "%.2f", row.hours)} h @ ${RosterFormat.money(row.rate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(RosterFormat.money(row.amount), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun TotalLine(label: String, amount: Double, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
        )
        Text(
            RosterFormat.money(amount),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium),
        )
    }
}
