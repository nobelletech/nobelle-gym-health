package com.nobelle.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
fun ScreenTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * Nobelle Health pastel card tones (Samsung Health-style colourful cards).
 * Each tone is a soft container with a readable darker accent for titles.
 * Neutral keeps the theme default.
 */
enum class CardTone(val container: Color, val content: Color) {
    Neutral(Color.Unspecified, Color.Unspecified),
    Rose(Color(0xFFFBE3EE), Color(0xFF8E2B54)),
    Peach(Color(0xFFFEE8D4), Color(0xFF8A4B1E)),
    Mint(Color(0xFFDFF3E4), Color(0xFF1F6B3B)),
    Sky(Color(0xFFDEEDFB), Color(0xFF1C4E80)),
    Lilac(Color(0xFFECE4F8), Color(0xFF53418A)),
    Sand(Color(0xFFF6ECD9), Color(0xFF6B5324)),
}

@Composable
fun nobelleCardColors(tone: CardTone): CardColors =
    if (tone == CardTone.Neutral) {
        CardDefaults.cardColors()
    } else {
        CardDefaults.cardColors(
            containerColor = tone.container,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

@Composable
fun InfoCard(
    title: String,
    body: String,
    trailing: (@Composable () -> Unit)? = null,
    tone: CardTone = CardTone.Neutral,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = nobelleCardColors(tone)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.invoke()
        }
    }
}
