package com.hiddennoir.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val Noir = Color(0xFF0C0A12)
private val SurfaceNoir = Color(0xFF17121F)
private val RaisedNoir = Color(0xFF21182D)
private val Violet = Color(0xFF9B63FF)
private val Lavender = Color(0xFFD9C4FF)
private val Muted = Color(0xFFB9B0C5)
private val Panel = RoundedCornerShape(24.dp)

class MainActivity : ComponentActivity() {
    private val viewModel: HiddenNoirViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HiddenNoirTheme { HiddenNoirApp(viewModel) } }
    }
}

@Composable private fun HiddenNoirTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = MaterialTheme.colorScheme.copy(primary = Violet, onPrimary = Color.White, surface = SurfaceNoir, onSurface = Color.White, background = Noir, onBackground = Color.White), content = content
)

@Composable private fun HiddenNoirApp(viewModel: HiddenNoirViewModel) {
    var confirmed by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = Noir) { if (confirmed) Studio(viewModel) else AgeGate { confirmed = true } }
}

@Composable private fun AgeGate(onConfirm: () -> Unit) = Box(
    Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF171021), Noir))).padding(24.dp), contentAlignment = Alignment.Center
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BrandHeader(false)
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceNoir), shape = Panel) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Pill("PRIVATE ADULT REPLY STUDIO")
                Text("For adults only.", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("HiddenNoir helps you shape a reply, on your terms. Explicit mode also asks you to confirm that every person represented is 18 or older.", color = Muted, lineHeight = 22.sp)
                PrimaryButton("I am 18 or older", onConfirm)
            }
        }
    }
}

@Composable private fun Studio(viewModel: HiddenNoirViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var conversation by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf(Relationship.NEW_MATCH) }
    var tone by remember { mutableStateOf(Tone.PLAYFUL) }
    var intensity by remember { mutableStateOf(Intensity.FLIRTY) }
    var adultConfirmed by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF160E22), Noir, Noir))).windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        BrandHeader(true)
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceNoir), shape = Panel) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Section("THE CONVERSATION")
                Text("What are you replying to?", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text("Paste the recent messages. Your raw conversation is used only to shape this generation.", color = Muted, fontSize = 14.sp)
                OutlinedTextField(value = conversation, onValueChange = { conversation = it }, modifier = Modifier.fillMaxWidth(), minLines = 6, label = { Text("Paste messages here") }, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Violet, unfocusedBorderColor = Color(0xFF51485E), focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Lavender, unfocusedLabelColor = Muted))
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceNoir), shape = Panel) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Section("DIRECTION")
                ChoiceGroup("Relationship", Relationship.entries, relationship, { it.label }) { relationship = it }
                ChoiceGroup("Tone", Tone.entries, tone, { it.label }) { tone = it }
                ChoiceGroup("Intensity", Intensity.entries, intensity, { it.label }) { intensity = it; if (it != Intensity.EXPLICIT) adultConfirmed = false }
            }
        }
        if (intensity == Intensity.EXPLICIT) AdultConfirm(adultConfirmed) { adultConfirmed = !adultConfirmed }
        PrimaryButton(if (state.loading) "Finding your next line…" else "Generate replies", { viewModel.generate(conversation, relationship, tone, intensity, adultConfirmed) }, !state.loading && conversation.isNotBlank() && (intensity != Intensity.EXPLICIT || adultConfirmed))
        state.model?.let { Text("Powered locally by $it", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        state.error?.let { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3B2027)), shape = Panel) { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(18.dp)) } }
        if (state.replies.isNotEmpty()) Section("YOUR OPTIONS")
        state.replies.forEachIndexed { index, reply -> ReplyCard(index + 1, reply.text) { copy(context, reply.text) } }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable private fun BrandHeader(compact: Boolean) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(if (compact) 44.dp else 58.dp).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Color(0xFF4B2670), Violet))), contentAlignment = Alignment.Center) { Text("N", fontWeight = FontWeight.Black, fontSize = if (compact) 25.sp else 32.sp) }
    Spacer(Modifier.width(12.dp)); Column { Text("HiddenNoir", fontSize = if (compact) 26.sp else 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp); Text("Private reply studio", color = Lavender, fontSize = if (compact) 13.sp else 15.sp) }
}

@Composable private fun AdultConfirm(confirmed: Boolean, onClick: () -> Unit) = Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1733)), border = BorderStroke(1.dp, Color(0xFF714381)), shape = Panel) {
    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF512A62)), contentAlignment = Alignment.Center) { Text("18+", color = Lavender, fontWeight = FontWeight.Bold, fontSize = 12.sp) }; Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text("Adult confirmation", fontWeight = FontWeight.SemiBold); Text("Everyone represented is 18 or older.", color = Muted, fontSize = 13.sp) }
        Text(if (confirmed) "CONFIRMED" else "CONFIRM", color = if (confirmed) Lavender else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (confirmed) Color(0xFF55306C) else Color(0xFF3B3144)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

@Composable private fun ReplyCard(number: Int, text: String, onCopy: () -> Unit) = Card(colors = CardDefaults.cardColors(containerColor = RaisedNoir), shape = Panel, border = BorderStroke(1.dp, Color(0xFF382E43))) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF342444)), contentAlignment = Alignment.Center) { Text("$number", color = Lavender, fontSize = 12.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(9.dp)); Text("REPLY OPTION", color = Lavender, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Text(text, fontSize = 17.sp, lineHeight = 24.sp)
        Text("COPY", color = Lavender, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onCopy).padding(vertical = 6.dp, horizontal = 2.dp))
    }
}

@Composable private fun <T> ChoiceGroup(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { option -> val active = selected == option; Text(label(option), color = if (active) Color.White else Muted, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(13.dp)).background(if (active) Color(0xFF6037A2) else Color(0xFF2B2433)).clickable { onSelect(option) }.padding(horizontal = 13.dp, vertical = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
}

@Composable private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) = Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = Violet, disabledContainerColor = Color(0xFF3A3142), disabledContentColor = Color(0xFF857C90)), contentPadding = PaddingValues()) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
@Composable private fun Section(text: String) = Text(text, color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
@Composable private fun Pill(text: String) = Text(text, color = Lavender, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(Color(0xFF31213F)).padding(horizontal = 11.dp, vertical = 7.dp))
private fun copy(context: Context, text: String) { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("HiddenNoir reply", text)) }
