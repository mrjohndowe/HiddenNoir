package com.hiddennoir.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HiddenNoirUiState(
    val loading: Boolean = false,
    val replies: List<ReplyCandidate> = emptyList(),
    val model: String? = null,
    val error: String? = null
)

class HiddenNoirViewModel(
    private val api: HiddenNoirApi = HiddenNoirApi()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HiddenNoirUiState())
    val uiState: StateFlow<HiddenNoirUiState> = _uiState.asStateFlow()

    // Development session only; not persisted.
    private var accessToken: String? = null

    fun generate(
        conversation: String,
        relationship: Relationship,
        tone: Tone,
        intensity: Intensity,
        adultAttestation: Boolean
    ) {
        viewModelScope.launch {
            _uiState.value = HiddenNoirUiState(loading = true)

            runCatching {
                val token = accessToken ?: api.createAnonymousSession()
                    .also { accessToken = it.accessToken }
                    .accessToken

                api.generate(
                    accessToken = token,
                    request = GenerationRequest(
                        conversation = conversation,
                        relationship = relationship.apiValue,
                        tone = tone.apiValue,
                        intensity = intensity.apiValue,
                        adultAttestation = adultAttestation,
                        replyCount = 4
                    )
                )
            }.onSuccess { response ->
                _uiState.value = HiddenNoirUiState(
                    replies = response.replies,
                    model = response.model
                )
            }.onFailure { throwable ->
                val message = when (throwable) {
                    is ResponseException ->
                        "Server returned ${throwable.response.status.value}. Check the backend terminal."
                    is HttpRequestTimeoutException ->
                        "Generation took too long. Please try again."
                    else ->
                        throwable.message ?: "Unable to reach HiddenNoir backend."
                }

                _uiState.value = HiddenNoirUiState(error = message)
            }
        }
    }
}
