package com.surainvestments.roster.ui.staff.payslips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.domain.model.Payslip
import com.surainvestments.roster.domain.model.PayslipStatus
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.ui.components.Banner
import com.surainvestments.roster.ui.components.BannerKind
import com.surainvestments.roster.ui.components.EmptyState
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.ScreenLoadingSkeleton
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.StatusPill
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.TextTertiaryLight

/** Staff Payslips — pushed from Account. Mirrors iOS `PayslipsView`'s month-scoped, cache-first shape. */
@Composable
fun PayslipsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: PayslipsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val navBarPadding = LocalNavBarPadding.current
    var selected by remember { mutableStateOf<Payslip?>(null) }

    selected?.let { payslip ->
        PayslipDetailScreen(payslip = payslip, onBack = { selected = null }, modifier = modifier.fillMaxSize())
        return
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.isLoading -> ScreenLoadingSkeleton(modifier = Modifier.fillMaxSize())
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = ScreenPillTopBarHeight + 4.dp,
                    bottom = navBarPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    MonthSelector(
                        monthLabel = state.monthLabel,
                        canGoNext = state.canGoNext,
                        isRefreshing = state.isRefreshing,
                        onPrev = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                        onRefresh = viewModel::refresh,
                    )
                }
                state.errorMessage?.let { message ->
                    item { Banner(kind = BannerKind.Error, title = "Couldn't refresh", message = message) }
                }
                if (state.payslips.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Payments,
                            title = "No payslips this month",
                            message = "Payslips appear here once your manager issues them for a pay period.",
                        )
                    }
                } else {
                    items(state.payslips, key = { it.id }) { payslip ->
                        PayslipRow(payslip = payslip, onClick = { selected = payslip })
                    }
                }
            }
        }

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(
            title = "Payslips",
            icon = Icons.Outlined.Payments,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun MonthSelector(
    monthLabel: String,
    canGoNext: Boolean,
    isRefreshing: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month", tint = BrandIndigoStrong)
        }
        Text(
            text = monthLabel,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = BrandIndigoStrong)
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = BrandIndigoStrong)
                }
            }
            IconButton(onClick = onNext, enabled = canGoNext) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "Next month",
                    tint = if (canGoNext) BrandIndigoStrong else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PayslipRow(payslip: Payslip, onClick: () -> Unit) {
    val totals = remember(payslip) { payslip.totals }
    RosterCard(onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "${RosterFormat.dateShort(payslip.periodStart)} – ${RosterFormat.dateShort(payslip.periodEnd)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "Paid ${RosterFormat.dateShort(payslip.payDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = RosterFormat.money(totals.net),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                if (payslip.status == PayslipStatus.Archived) {
                    StatusPill(label = "Archived", tint = TextTertiaryLight, compact = true)
                }
            }
        }
    }
}
