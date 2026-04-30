package com.kanjilens.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(
                            text = "<",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HelpSection("What is ThorNotes?")
            HelpBody(
                "ThorNotes is a notebook for information-heavy games, visual novels, novels, " +
                    "and detective games on dual-screen Android devices. Keep the game on the top " +
                    "screen and keep notes on the bottom screen."
            )

            HelpDivider()

            HelpSection("Notebook")
            HelpBody("Each visual novel, novel, or game can have its own notebook page. Use the page selector to switch, + to create, and the page menu to delete a page.")
            HelpBody("Each page shows its current entry count and storage usage.")
            HelpBody("Screenshot captures the whole top screen and adds it to the notebook stream.")
            HelpBody("Region OCR captures the saved fixed region, reads Japanese text, and adds editable text to the stream. Long-press Region OCR to change the region.")
            HelpBody("OCR entries can be corrected directly in the notebook when recognition is inaccurate.")
            HelpBody("Swipe a screenshot or OCR entry sideways to delete it.")

            HelpDivider()

            HelpSection("Dictionary")
            HelpBody(
                "The Dictionary page uses manual text input. Type or paste Japanese text, then look it up " +
                    "to see readings and meanings from the bundled offline dictionary."
            )

            HelpDivider()

            HelpSection("Capture Region")
            HelpBody(
                "On the Notebook page, tap Set Region before using Region OCR, or long-press Region OCR to change it later. " +
                    "Screenshots always capture the whole screen; the fixed region is only used for notebook OCR."
            )

            HelpDivider()

            Text(
                text = "MIT License",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HelpSection(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun HelpBody(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 20.sp,
    )
}

@Composable
private fun HelpDivider() {
    Spacer(modifier = Modifier.height(12.dp))
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
}
