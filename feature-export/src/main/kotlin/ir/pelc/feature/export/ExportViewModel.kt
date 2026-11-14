package ir.pelc.feature.export

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pelc.core.export.ExportFormat
import ir.pelc.core.export.ExportOptions
import ir.pelc.core.model.PelcDocument
import ir.pelc.core.model.PelcJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

sealed class ExportState {
    data object Idle : ExportState()
    data object Running : ExportState()
    data class Done(val files: String) : ExportState()
    data class Error(val message: String) : ExportState()
}

/** Enqueues the export job and mirrors its progress to the UI. */
@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext context: Context,
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state = _state.asStateFlow()

    fun export(document: PelcDocument, format: ExportFormat, options: ExportOptions) {
        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(
                workDataOf(
                    ExportWorker.KEY_DOCUMENT to PelcJson.encode(document),
                    ExportWorker.KEY_FORMAT to format.name,
                    ExportWorker.KEY_SCALE to options.scale,
                    ExportWorker.KEY_QUALITY to options.jpgQuality,
                    ExportWorker.KEY_TRANSPARENT to options.transparentPng,
                    ExportWorker.KEY_BACKGROUND to options.backgroundColor,
                ),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        _state.value = ExportState.Running
        workManager.enqueueUniqueWork("pelc-export", ExistingWorkPolicy.REPLACE, request)

        workManager.getWorkInfoByIdFlow(request.id)
            .onEach { info ->
                when (info?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        _state.value = ExportState.Done(
                            info.outputData.getString(ExportWorker.KEY_OUTPUT_FILES).orEmpty(),
                        )
                    }
                    WorkInfo.State.FAILED -> {
                        _state.value = ExportState.Error(
                            info.outputData.getString(ExportWorker.KEY_ERROR) ?: "خطای ناشناخته",
                        )
                    }
                    else -> Unit
                }
            }
            .launchIn(viewModelScope)
    }

    fun reset() {
        _state.value = ExportState.Idle
    }
}
