package com.thornotes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Welcome to ThorNotes",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "A quick guide to the less obvious gestures that make the notebook faster.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WelcomeItem(
                "📓 Top bar",
                welcomeText(
                    bold("Tap the notebook name"),
                    plain(" to switch pages, create a page, or open "),
                    italic("settings"),
                    plain(". Long-press a page in the menu for page details, rename, and delete."),
                ),
            )
            WelcomeItem(
                "💾 Backup",
                welcomeText(
                    plain("Use "),
                    bold("Settings"),
                    plain(" to export or import a ZIP backup. Import adds missing pages, skips existing pages, and normalizes older backup entries where possible. Do not close ThorNotes while import or export is running."),
                ),
            )
            WelcomeItem(
                "📸 Capture",
                welcomeText(
                    bold("Shot"),
                    plain(" saves the top screen. "),
                    bold("OCR"),
                    plain(" reads the saved region; "),
                    italic("long-press OCR"),
                    plain(" to adjust that region later."),
                ),
            )
            WelcomeItem(
                "✍️ Text",
                welcomeText(
                    bold("Text"),
                    plain(" creates an editable text block directly in the notebook, with the "),
                    italic("same thumbnail style"),
                    plain(" as OCR output."),
                ),
            )
            WelcomeItem(
                "🔎 OCR model",
                welcomeText(
                    plain("For best OCR, open "),
                    bold("Settings"),
                    plain(" and download "),
                    bold("PP-OCRv5"),
                    plain(" before relying on OCR-heavy notes."),
                ),
            )
            WelcomeItem(
                "📌 Thumbnails",
                welcomeText(
                    bold("Tap"),
                    plain(" to jump. "),
                    bold("Double-tap"),
                    plain(" to pin. "),
                    bold("Long-press"),
                    plain(" to favorite."),
                ),
            )
            WelcomeItem(
                "↔️ Entries",
                welcomeText(
                    bold("Swipe sideways"),
                    plain(" to delete an entry. Pinned entries stay first in the thumbnail rail."),
                ),
            )
            WelcomeItem(
                "🌑 Blackout",
                welcomeText(
                    bold("Double-tap the clock"),
                    plain(" for a black screen; double-tap again to return."),
                ),
            )
        }

        Text(
            text = "Let's Roll",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onDone() }
                .padding(vertical = 11.dp),
        )
    }
}

@Composable
private fun WelcomeItem(
    title: String,
    body: AnnotatedString,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.32f),
        )
        Text(
            text = body,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
            modifier = Modifier.weight(0.68f),
        )
    }
    Spacer(modifier = Modifier.height(2.dp))
}

private data class WelcomeTextPart(
    val text: String,
    val style: SpanStyle? = null,
)

private fun welcomeText(vararg parts: WelcomeTextPart): AnnotatedString {
    return buildAnnotatedString {
        parts.forEach { part ->
            if (part.style == null) {
                append(part.text)
            } else {
                withStyle(part.style) {
                    append(part.text)
                }
            }
        }
    }
}

private fun plain(text: String): WelcomeTextPart = WelcomeTextPart(text)

private fun bold(text: String): WelcomeTextPart {
    return WelcomeTextPart(text, SpanStyle(fontWeight = FontWeight.Bold))
}

private fun italic(text: String): WelcomeTextPart {
    return WelcomeTextPart(text, SpanStyle(fontStyle = FontStyle.Italic))
}
