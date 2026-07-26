package com.surainvestments.roster.ui.staff.payslips

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AppSettingsRepository
import com.surainvestments.roster.data.service.PayslipPdfRenderer
import com.surainvestments.roster.domain.model.Payslip
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Renders at ~2.5x the PDF's native 72dpi so the on-screen preview stays crisp when zoomed. */
private const val PREVIEW_SCALE = 2.5f

data class PayslipDetailUiState(
    val isRendering: Boolean = true,
    val previewBitmap: Bitmap? = null,
    val pdfFile: File? = null,
    val isSharing: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Renders the payslip PDF once and reuses the same [File] for both the on-screen preview (page 0
 * rasterized via [PdfRenderer]) and the "Share" action — the whole point being that what staff
 * see on screen and what they export/print/save are pixel-identical, since they're literally the
 * same file. A previous version hand-built a separate Compose breakdown for the preview, which
 * drifted visually from the actual PDF; this replaces that with the real thing.
 */
@HiltViewModel
class PayslipDetailViewModel @Inject constructor(
    private val pdfRenderer: PayslipPdfRenderer,
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PayslipDetailUiState())
    val state: StateFlow<PayslipDetailUiState> = _state.asStateFlow()

    fun render(payslip: Payslip) {
        _state.update { PayslipDetailUiState(isRendering = true) }
        viewModelScope.launch {
            try {
                // .appSettings.value would be an instant peek at a WhileSubscribed StateFlow seeded
                // with AppSettings.Fallback — if nothing else has actively collected it recently,
                // that peek can still be the fallback ("Rosterra") rather than the real company name.
                // Collecting the raw flow's first emission forces a live Firestore read instead.
                val settings = appSettingsRepository.appSettingsFlow().first()
                val file = pdfRenderer.render(payslip, settings)
                val bitmap = renderFirstPagePreview(file)
                _state.update { it.copy(isRendering = false, previewBitmap = bitmap, pdfFile = file) }
            } catch (e: Exception) {
                _state.update { it.copy(isRendering = false, errorMessage = "Couldn't generate the payslip PDF.") }
            }
        }
    }

    /** Returns the already-rendered file for sharing, tracking [PayslipDetailUiState.isSharing] for a button spinner. */
    suspend fun sharePreparedFile(action: suspend (File) -> Unit) {
        val file = _state.value.pdfFile ?: return
        _state.update { it.copy(isSharing = true) }
        try {
            action(file)
        } finally {
            _state.update { it.copy(isSharing = false) }
        }
    }

    private suspend fun renderFirstPagePreview(file: File): Bitmap = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(
                        (page.width * PREVIEW_SCALE).toInt(),
                        (page.height * PREVIEW_SCALE).toInt(),
                        Bitmap.Config.ARGB_8888,
                    )
                    Canvas(bitmap).drawColor(Color.WHITE)
                    val matrix = Matrix().apply { setScale(PREVIEW_SCALE, PREVIEW_SCALE) }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }
}
