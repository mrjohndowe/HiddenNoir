package com.hiddennoir.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: HiddenNoirViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { HiddenNoirApp(viewModel) } }
    }
}

@Composable
private fun HiddenNoirApp(viewModel: HiddenNoirViewModel) {
    var ageConfirmed by remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        if (!ageConfirmed) {
            AgeGate(Modifier.padding(padding)) { ageConfirmed = true }
        } else {
            ConversationScreen(viewModel, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AgeGate(
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("HiddenNoir", style = MaterialTheme.typography.displaySmall)
        Text("18+ only", style = MaterialTheme.typography.headlineMedium)
        Text(
            "HiddenNoir is intended only for adults. Explicit mode also requires confirmation that everyone represented in the pasted conversation is 18 or older.",
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onConfirm
        ) {
            Text("I am 18 or older")
        }
    }
}

@Composable
private fun ConversationScreen(
    viewModel: HiddenNoirViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var conversation by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf(Relationship.NEW_MATCH) }
    var tone by remember { mutableStateOf(Tone.PLAYFUL) }
    var intensity by remember { mutableStateOf(Intensity.FLIRTY) }
    var adultAttestation by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("HiddenNoir", style = MaterialTheme.typography.headlineMedium)
        Text("Paste a conversation to generate reply ideas.")

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = conversation,
            minLines = 8,
            onValueChange = { conversation = it },
            label = { Text("Conversation") }
        )

        Selector("Relationship", Relationship.entries, relationship, { it.label }) {
            relationship = it
        }

        Selector("Tone", Tone.entries, tone, { it.label }) {
            tone = it
        }

        Selector("Intensity", Intensity.entries, intensity, { it.label }) {
            intensity = it
            if (it != Intensity.EXPLICIT) adultAttestation = false
        }

        if (intensity == Intensity.EXPLICIT) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Confirm everyone represented in this conversation is 18 or older.")
                    Button(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        onClick = { adultAttestation = !adultAttestation }
                    ) {
                        Text(if (adultAttestation) "18+ confirmed" else "Confirm everyone is 18+")
                    }
                }
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.loading &&
                conversation.isNotBlank() &&
                (intensity != Intensity.EXPLICIT || adultAttestation),
            onClick = {
                viewModel.generate(
                    conversation,
                    relationship,
                    tone,
                    intensity,
                    adultAttestation
                )
            }
        ) {
            Text(if (uiState.loading) "Generating…" else "Generate replies")
        }

        uiState.error?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(16.dp))
            }
        }

        uiState.replies.forEachIndexed { index, reply ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Reply ${index + 1}", style = MaterialTheme.typography.labelLarge)
                    Text(reply.text, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun <T> Selector(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    options.forEach { option ->
        TextButton(onClick = { onSelect(option) }) {
            RadioButton(selected = selected == option, onClick = null)
            Text(label(option))
        }
    }
}
