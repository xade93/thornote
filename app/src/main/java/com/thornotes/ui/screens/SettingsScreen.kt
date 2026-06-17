package com.thornotes.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thornotes.data.models.AppSettings
import com.thornotes.ocr.TextRecognizer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    textRecognizer: TextRecognizer,
    onBack: () -> Unit,
    onShowWelcome: () -> Unit,
) {
    val textSize by settings.textSize.collectAsState()
    val cropEnabled by settings.cropEnabled.collectAsState()
    val ocrLanguage by settings.ocrLanguage.collectAsState()
    val floatingToggleEnabled by settings.floatingToggleEnabled.collectAsState()
    val paddleStatus by textRecognizer.paddleOcr.assets.status.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        textRecognizer.paddleOcr.assets.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection(title = "Notebook Text Size") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingsOption(
                        label = "S",
                        selected = textSize == AppSettings.TEXT_SIZE_SMALL,
                        onClick = { settings.setTextSize(AppSettings.TEXT_SIZE_SMALL) },
                        modifier = Modifier.weight(1f),
                    )
                    SettingsOption(
                        label = "M",
                        selected = textSize == AppSettings.TEXT_SIZE_MEDIUM,
                        onClick = { settings.setTextSize(AppSettings.TEXT_SIZE_MEDIUM) },
                        modifier = Modifier.weight(1f),
                    )
                    SettingsOption(
                        label = "L",
                        selected = textSize == AppSettings.TEXT_SIZE_LARGE,
                        onClick = { settings.setTextSize(AppSettings.TEXT_SIZE_LARGE) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            SettingsSection(title = "Display") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsOption(
                        label = if (floatingToggleEnabled) "Floating Toggle On" else "Floating Toggle Off",
                        selected = floatingToggleEnabled,
                        onClick = {
                            val enable = !floatingToggleEnabled
                            settings.setFloatingToggleEnabled(enable)
                            if (enable && !Settings.canDrawOverlays(context)) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingsOption(
                        label = "Show Welcome Page",
                        selected = false,
                        onClick = onShowWelcome,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            SettingsSection(title = "OCR Language") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ThorNotes tries PP-OCRv5 first when installed. If it is missing or fails, OCR falls back to ML Kit with the selected language.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SettingsOption(
                            label = "中 + EN",
                            selected = ocrLanguage == AppSettings.OCR_LANGUAGE_CHINESE_ENGLISH,
                            onClick = { settings.setOcrLanguage(AppSettings.OCR_LANGUAGE_CHINESE_ENGLISH) },
                            modifier = Modifier.weight(1f),
                        )
                        SettingsOption(
                            label = "EN",
                            selected = ocrLanguage == AppSettings.OCR_LANGUAGE_ENGLISH,
                            onClick = { settings.setOcrLanguage(AppSettings.OCR_LANGUAGE_ENGLISH) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SettingsOption(
                            label = "中文",
                            selected = ocrLanguage == AppSettings.OCR_LANGUAGE_CHINESE,
                            onClick = { settings.setOcrLanguage(AppSettings.OCR_LANGUAGE_CHINESE) },
                            modifier = Modifier.weight(1f),
                        )
                        SettingsOption(
                            label = "日本語",
                            selected = ocrLanguage == AppSettings.OCR_LANGUAGE_JAPANESE,
                            onClick = { settings.setOcrLanguage(AppSettings.OCR_LANGUAGE_JAPANESE) },
                            modifier = Modifier.weight(1f),
                        )
                        SettingsOption(
                            label = "All",
                            selected = ocrLanguage == AppSettings.OCR_LANGUAGE_ALL,
                            onClick = { settings.setOcrLanguage(AppSettings.OCR_LANGUAGE_ALL) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            SettingsSection(title = "OCR Engine") {
                Text(
                    text = paddleStatus.message,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
                SettingsOption(
                    label = if (paddleStatus.ready) "Refresh" else if (paddleStatus.downloading) "Downloading" else "Download PP-OCRv5",
                    selected = paddleStatus.ready,
                    enabled = !paddleStatus.downloading,
                    onClick = {
                        scope.launch {
                            if (paddleStatus.ready) {
                                textRecognizer.paddleOcr.assets.refresh()
                            } else {
                                textRecognizer.paddleOcr.assets.ensureDownloaded()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (paddleStatus.ready) {
                    SettingsOption(
                        label = "Uninstall PP-OCRv5",
                        selected = false,
                        enabled = !paddleStatus.downloading,
                        onClick = {
                            scope.launch {
                                textRecognizer.paddleOcr.assets.uninstall()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            SettingsSection(title = "Capture Region") {
                Text(
                    text = if (cropEnabled) {
                        "A fixed OCR region is saved. Long-press Region OCR on the notebook page to update it."
                    } else {
                        "No fixed OCR region is saved. Tap Set Region on the notebook page before Region OCR."
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
                if (cropEnabled) {
                    Text(
                        text = "Clear Region",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { settings.clearCropRegion() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun SettingsOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
