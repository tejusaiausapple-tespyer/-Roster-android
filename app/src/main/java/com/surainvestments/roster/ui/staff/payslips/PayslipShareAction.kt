package com.surainvestments.roster.ui.staff.payslips

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AppSettingsRepository
import com.surainvestments.roster.data.service.PayslipPdfRenderer
import com.surainvestments.roster.domain.model.Payslip
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class PayslipShareViewModel @Inject constructor(
    private val pdfRenderer: PayslipPdfRenderer,
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModel() {
    suspend fun renderPdf(payslip: Payslip): File = pdfRenderer.render(payslip, appSettingsRepository.appSettings.value)
}

/** [isSharing] drives a button's loading spinner while the PDF renders; [share] triggers it. */
class SharePayslipAction internal constructor(val isSharing: Boolean, val share: (Payslip) -> Unit)

/**
 * Renders the monochrome A4 PDF on demand (see [PayslipPdfRenderer]) and opens it in the system
 * share sheet, so "share/print/save" is a single OS-native entry point — mirrors
 * [com.surainvestments.roster.ui.staff.shared.rememberAddToCalendarAction]'s shape.
 */
@Composable
fun rememberSharePayslipAction(viewModel: PayslipShareViewModel = hiltViewModel()): SharePayslipAction {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }

    val share: (Payslip) -> Unit = sharing@{ payslip ->
        if (isSharing) return@sharing
        isSharing = true
        scope.launch {
            try {
                val file = viewModel.renderPdf(payslip)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share payslip"))
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't generate the payslip PDF.", Toast.LENGTH_SHORT).show()
            } finally {
                isSharing = false
            }
        }
    }

    return SharePayslipAction(isSharing = isSharing, share = share)
}
