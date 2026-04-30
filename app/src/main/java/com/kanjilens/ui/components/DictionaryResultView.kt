package com.kanjilens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kanjilens.data.models.AnalysisResult
import com.kanjilens.data.models.AppSettings

@Composable
fun DictionaryResultView(
    result: AnalysisResult,
    textSize: Int = AppSettings.TEXT_SIZE_MEDIUM,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        result.words.forEach { word ->
            WordCard(word = word, textSize = textSize)
        }
    }
}

