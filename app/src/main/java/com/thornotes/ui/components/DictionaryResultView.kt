package com.thornotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thornotes.data.models.AppSettings
import com.thornotes.data.models.EnglishDictionaryEntry

@Composable
fun DictionaryResultView(
    result: List<EnglishDictionaryEntry>,
    textSize: Int = AppSettings.TEXT_SIZE_MEDIUM,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        result.forEach { entry ->
            EnglishDefinitionCard(entry = entry, textSize = textSize)
        }
    }
}

@Composable
private fun EnglishDefinitionCard(
    entry: EnglishDictionaryEntry,
    textSize: Int,
    modifier: Modifier = Modifier,
) {
    val wordSize = when (textSize) {
        AppSettings.TEXT_SIZE_SMALL -> 17.sp
        AppSettings.TEXT_SIZE_LARGE -> 24.sp
        else -> 20.sp
    }
    val bodySize = when (textSize) {
        AppSettings.TEXT_SIZE_SMALL -> 13.sp
        AppSettings.TEXT_SIZE_LARGE -> 18.sp
        else -> 15.sp
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = entry.word,
                fontSize = wordSize,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = entry.partOfSpeech,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = entry.definition,
            fontSize = bodySize,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = bodySize * 1.35,
        )

        if (entry.synonyms.isNotEmpty()) {
            Text(
                text = "Synonyms: ${entry.synonyms.take(8).joinToString(", ")}",
                fontSize = bodySize * 0.9,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = bodySize * 1.25,
            )
        }

        entry.examples.take(2).forEach { example ->
            Text(
                text = example,
                fontSize = bodySize * 0.9,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = bodySize * 1.25,
            )
        }
    }
}
