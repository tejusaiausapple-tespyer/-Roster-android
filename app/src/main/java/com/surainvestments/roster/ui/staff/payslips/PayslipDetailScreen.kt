package com.surainvestments.roster.ui.staff.payslips

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.domain.model.Payslip
import com.surainvestments.roster.ui.components.Banner
import com.surainvestments.roster.ui.components.BannerKind
import com.surainvestments.roster.ui.components.PrimaryButton
import com.surainvestments.roster.ui.components.QuietOutlinedButton
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.ScreenPadding
import kotlinx.coroutines.launch

/**
 * Shows the actual generated payslip PDF (page 0, rasterized) rather than a hand-built Compose
 * recreation of it — a previous version rendered its own breakdown here, which visually drifted
 * from the real PDF over time. Now there's exactly one design: [PayslipPdfRenderer]'s, a port of
 * iOS `PayslipPDFService.swift`. "Share" reuses the same rendered file, so what's on screen and
 * what gets exported are always the identical artifact.
 */
@Composable
fun PayslipDetailScreen(
    payslip: Payslip,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PayslipDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val navBarPadding = LocalNavBarPadding.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(payslip.id) { viewModel.render(payslip) }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(navBarPadding)
                .padding(horizontal = ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.height(ScreenPillTopBarHeight + 8.dp))

            when {
                state.isRendering -> Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandIndigoStrong)
                }
                state.errorMessage != null -> Banner(kind = BannerKind.Error, title = "Couldn't load payslip", message = state.errorMessage)
                state.previewBitmap != null -> Image(
                    bitmap = state.previewBitmap!!.asImageBitmap(),
                    contentDescription = "Payslip",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = "Share",
                    enabled = state.pdfFile != null,
                    loading = state.isPreparingFile,
                    leadingIcon = Icons.Outlined.Share,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            viewModel.withPreparedFile { file ->
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share payslip"))
                            }
                        }
                    },
                )
                QuietOutlinedButton(
                    text = "Print",
                    enabled = state.pdfFile != null,
                    leadingIcon = Icons.Outlined.Print,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            viewModel.withPreparedFile { file ->
                                val printManager = ContextCompat.getSystemService(context, PrintManager::class.java)
                                printManager?.print(
                                    "Payslip",
                                    PdfPrintAdapter(file),
                                    PrintAttributes.Builder().build(),
                                )
                            }
                        }
                    },
                )
            }

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
