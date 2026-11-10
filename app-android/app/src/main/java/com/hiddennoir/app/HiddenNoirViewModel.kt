package com.hiddennoir.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class HiddenNoirUiState(
    val adultGateAccepted: Boolean = false,
    val importing: Boolean = false,
    val loading: Boolean = false,
    val draft: Draft = Draft(),
    val model: String? = null,
    val modelError: String? = null,
    val error: String? = null
)

class HiddenNoirViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("studio", Application.MODE_PRIVATE)
    private val localModel = LocalModel(application)
    private val _uiState = MutableStateFlow(
        HiddenNoirUiState(
            adultGateAccepted = preferences.getBoolean("adult_gate", false),
            model = if (localModel.isInstalled()) preferences.getString("model", "Local GGUF") else null,
            draft = try {
                preferences.getString("draft", null)?.let { Json.decodeFromString<Draft>(it) } ?: Draft()
            } catch (_: SerializationException) {
                Draft()
            }
        )
    )
    val uiState = _uiState.asStateFlow()

    fun acceptAdultGate() {
        preferences.edit().putBoolean("adult_gate", true).apply()
        _uiState.value = _uiState.value.copy(adultGateAccepted = true)
    }

    fun updateDraft(draft: Draft) {
        saveDraft(draft.copy(replies = emptyList()))
    }

    fun importModel(uri: Uri) {
        val state = _uiState.value
        if (state.importing || state.loading) return
        _uiState.value = state.copy(importing = true, modelError = null, error = null)
        viewModelScope.launch {
            try {
                val name = withContext(Dispatchers.IO) { localModel.import(uri) }
                preferences.edit().putString("model", name).apply()
                _uiState.value = _uiState.value.copy(model = name)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.value = _uiState.value.copy(modelError = failure.message ?: "The model could not be imported.")
            } catch (_: LinkageError) {
                _uiState.value = _uiState.value.copy(modelError = "This device could not load the local inference engine.")
            } finally {
                _uiState.value = _uiState.value.copy(importing = false)
            }
        }
    }

    fun generate(adultConfirmed: Boolean) {
        val state = _uiState.value
        if (state.loading || state.importing || state.model == null || !state.adultGateAccepted) return
        _uiState.value = state.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val replies = withContext(Dispatchers.Default) { localModel.generate(state.draft, adultConfirmed) }
                if (_uiState.value.draft == state.draft) saveDraft(state.draft.copy(replies = replies))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                showError(failure.message ?: "On-device generation failed.")
            } catch (_: LinkageError) {
                showError("This device could not load the local inference engine.")
            } finally {
                _uiState.value = _uiState.value.copy(loading = false)
            }
        }
    }

    private fun saveDraft(draft: Draft) {
        preferences.edit().putString("draft", Json.encodeToString(draft)).apply()
        _uiState.value = _uiState.value.copy(draft = draft, error = null)
    }

    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }
}
