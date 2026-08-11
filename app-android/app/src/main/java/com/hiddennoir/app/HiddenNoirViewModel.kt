package com.hiddennoir.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HiddenNoirUiState(
    val loading: Boolean = false,
    val replies: List<ReplyCandidate> = emptyList(),
    val error: String? = null
)

class HiddenNoirViewModel(
    private val api: HiddenNoirApi = HiddenNoirApi()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HiddenNoirUiState())
    val uiState: StateFlow<HiddenNoirUiState> = _uiState.asStateFlow()

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
                    token,
                    GenerationRequest(
                        conversation = conversation,
                        relationship = relationship.apiValue,
                        tone = tone.apiValue,
                        intensity = intensity.apiValue,
                        adultAttestation = adultAttestation,
                        replyCount = 4
                    )
                )
            }.onSuccess {
                _uiState.value = HiddenNoirUiState(replies = it.replies)
            }.onFailure {
                _uiState.value = HiddenNoirUiState(
                    error = it.message ?: "Generation failed"
                )
            }
        }
    }
}
