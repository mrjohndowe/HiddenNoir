package com.spec1.replyassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { ReplyAssistantApp() } }
    }
}

private enum class Intensity { FLIRTY, SUGGESTIVE, EXPLICIT }

@Composable
private fun ReplyAssistantApp() {
    var ageConfirmed by remember { mutableStateOf(false) }
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        if (!ageConfirmed) {
            AgeGate(Modifier.padding(padding)) { ageConfirmed = true }
        } else {
            ConversationScreen(Modifier.padding(padding))
        }
    }
}

@Composable
private fun AgeGate(modifier: Modifier = Modifier, onConfirm: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("18+ only", style = MaterialTheme.typography.headlineLarge)
        Text(
            "This app is intended only for adults. Explicit mode also requires confirmation that everyone represented in the conversation is 18 or older.",
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
        )
        Button(modifier = Modifier.fillMaxWidth(), onClick = onConfirm) {
            Text("I am 18 or older")
        }
    }
}

@Composable
private fun ConversationScreen(modifier: Modifier = Modifier) {
    var conversation by remember { mutableStateOf("") }
    var intensity by remember { mutableStateOf(Intensity.FLIRTY) }
    var adultAttestation by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Paste a conversation", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = conversation,
            minLines = 8,
            onValueChange = { conversation = it },
            label = { Text("Conversation") }
        )

        Text("Intensity", style = MaterialTheme.typography.titleMedium)

        Intensity.entries.forEach { option ->
            TextButton(onClick = {
                intensity = option
                if (option != Intensity.EXPLICIT) adultAttestation = false
            }) {
                RadioButton(selected = intensity == option, onClick = null)
                Text(option.name.lowercase().replaceFirstChar(Char::uppercase))
            }
        }

        if (intensity == Intensity.EXPLICIT) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Explicit mode requires confirmation that everyone represented in the conversation is 18 or older.")
                    Button(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        onClick = { adultAttestation = true }
                    ) {
                        Text(if (adultAttestation) "Confirmed" else "Confirm everyone is 18+")
                    }
                }
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = conversation.isNotBlank() &&
                (intensity != Intensity.EXPLICIT || adultAttestation),
            onClick = {
                message = "UI scaffold complete. API networking is the next implementation task."
            }
        ) {
            Text("Generate replies")
        }

        message?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
