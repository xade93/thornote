package com.thornotes.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thornotes.capture.CaptureDebugLog
import com.thornotes.data.NotebookRepository
import com.thornotes.data.models.AppSettings
import com.thornotes.ocr.TextRecognizer
import com.thornotes.ui.theme.themePrimaryColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    textRecognizer: TextRecognizer,
    notebook: NotebookRepository,
    onBack: () -> Unit,
    onShowWelcome: () -> Unit,
) {
    val textSize by settings.textSize.collectAsState()
    val cropEnabled by settings.cropEnabled.collectAsState()
    val ocrLanguage by settings.ocrLanguage.collectAsState()
    val floatingToggleEnabled by settings.floatingToggleEnabled.collectAsState()
    val captureDebugLogEnabled by settings.captureDebugLogEnabled.collectAsState()
    val themeColor by settings.themeColor.collectAsState()
    val paddleStatus by textRecognizer.paddleOcr.assets.status.collectAsState()
    val context = LocalContext.current
    val versionName = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }
    val scope = rememberCoroutineScope()
    var backupBusy by rememberSaveable { mutableStateOf(false) }
    var backupErrorTitle by rememberSaveable { mutableStateOf("") }
    var backupErrorDetails by rememberSaveable { mutableStateOf("") }
    val backupFileName = rememberSaveable {
        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
        "thornotes-backup-$stamp.zip"
    }
    val exportDiagnosticsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        CaptureDebugLog.export(context, output)
                    } ?: error("Could not open selected file.")
                }
            }
            Toast.makeText(context, if (result.isSuccess) "Log exported." else "Couldn’t export the log. Try again.", Toast.LENGTH_LONG).show()
        }
    }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            backupBusy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        notebook.exportBackup(output)
                    } ?: error("Could not open selected file.")
                }
            }
            backupBusy = false
            result.fold(
                onSuccess = {
                    Toast.makeText(context, "Backup saved.", Toast.LENGTH_LONG).show()
                },
                onFailure = {
                    backupErrorTitle = "Couldn’t save backup"
                    backupErrorDetails = it.message ?: "Please try again."
                },
            )
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            backupBusy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        notebook.importBackup(input)
                    } ?: error("Could not open selected file.")
                }
            }
            backupBusy = false
            result.fold(
                onSuccess = {
                    Toast.makeText(
                        context,
                        "Imported ${it.pagesImported} pages. Skipped ${it.pagesSkipped}.",
                        Toast.LENGTH_LONG,
                    ).show()
                },
                onFailure = {
                    backupErrorTitle = "Couldn’t import backup"
                    backupErrorDetails = it.message ?: "Please try again."
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        textRecognizer.paddleOcr.assets.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection(title = "Appearance") {
                Text(
                    text = "Text size",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingsOption(
                        label = "Small",
                        selected = textSize == AppSettings.TEXT_SIZE_SMALL,
                        onClick = { settings.setTextSize(AppSettings.TEXT_SIZE_SMALL) },
                        modifier = Modifier.weight(1f),
                    )
                    SettingsOption(
                        label = "Medium",
                        selected = textSize == AppSettings.TEXT_SIZE_MEDIUM,
                        onClick = { settings.setTextSize(AppSettings.TEXT_SIZE_MEDIUM) },
                        modifier = Modifier.weight(1f),
                    )
                    SettingsOption(
                        label = "Large",
                        selected = textSize == AppSettings.TEXT_SIZE_LARGE,
                        onClick = { settings.setTextSize(AppSettings.TEXT_SIZE_LARGE) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = "Accent color",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ThemeColorPicker(
                    selectedThemeColor = themeColor,
                    onThemeColorSelected = settings::setThemeColor,
                )

                SettingsRow(label = "Show floating button") {
                    Switch(
                        checked = floatingToggleEnabled,
                        modifier = Modifier.semantics { contentDescription = "Show floating button" },
                        onCheckedChange = { enable ->
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
                    )
                }
            }

            SettingsSection(title = "Text recognition") {
                Text(
                    text = paddleStatus.message,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
                SettingsRow(label = "PP-OCRv5") {
                    CompactSettingsOption(
                        label = if (paddleStatus.ready) "Refresh" else if (paddleStatus.downloading) "Downloading…" else "Download",
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
                    )
                }
                if (paddleStatus.ready) {
                    SettingsRow(label = "Downloaded model") {
                        CompactSettingsOption(
                            label = "Remove",
                            selected = false,
                            enabled = !paddleStatus.downloading,
                            onClick = {
                                scope.launch {
                                    textRecognizer.paddleOcr.assets.uninstall()
                                }
                            },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This language is used when PP-OCRv5 is unavailable.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                    SettingsRow(label = "Fallback language") {
                        OcrLanguageDropdown(
                            selectedLanguage = ocrLanguage,
                            onLanguageSelected = settings::setOcrLanguage,
                        )
                    }
                }

                Text(
                    text = if (cropEnabled) {
                        "Long-press OCR on your notebook to change the capture area."
                    } else {
                        "Tap Set Region on your notebook to choose where OCR reads."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
                SettingsRow(label = "Capture area") {
                    if (cropEnabled) {
                        CompactSettingsOption(
                            label = "Clear",
                            selected = false,
                            onClick = { settings.clearCropRegion() },
                        )
                    } else {
                        Text(
                            text = "Not set",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SettingsSection(title = "Backup") {
                Text(
                    text = "Save your pages as a ZIP file, or import a backup. Pages you already have are skipped.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
                if (backupBusy) {
                    Text("Working… Keep ThorNotes open.", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactSettingsOption(
                        label = "Export",
                        selected = false,
                        enabled = !backupBusy,
                        onClick = { exportBackupLauncher.launch(backupFileName) },
                    )
                    CompactSettingsOption(
                        label = "Import",
                        selected = false,
                        enabled = !backupBusy,
                        onClick = { importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    )
                }
            }

            SettingsSection(title = "Help") {
                SettingsRow(label = "Welcome guide") {
                    CompactSettingsOption(
                        label = "Open",
                        selected = false,
                        onClick = onShowWelcome,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsRow(label = "Diagnostic log") {
                        Switch(
                            checked = captureDebugLogEnabled,
                            modifier = Modifier.semantics { contentDescription = "Diagnostic log" },
                            onCheckedChange = { enabled ->
                                settings.setCaptureDebugLogEnabled(enabled)
                                if (enabled) CaptureDebugLog.session(context)
                            },
                        )
                    }
                    if (captureDebugLogEnabled) {
                        Text(
                            text = "Repeat the problem, then export the log. Your note text isn’t recorded.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                        SettingsRow(label = "Log file") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CompactSettingsOption(
                                    label = "Export",
                                    selected = false,
                                    onClick = { exportDiagnosticsLauncher.launch("thornotes-diagnostics-${System.currentTimeMillis()}.txt") },
                                )
                                CompactSettingsOption(
                                    label = "Clear",
                                    selected = false,
                                    onClick = {
                                        val result = runCatching {
                                            CaptureDebugLog.clear(context)
                                            CaptureDebugLog.session(context)
                                        }
                                        Toast.makeText(context, if (result.isSuccess) "Log cleared." else "Couldn’t clear the log. Try again.", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }
                    }
                }
            }

            SettingsSection(title = "About") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ThorNotes", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("v$versionName", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = "MIT License",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (backupErrorDetails.isNotBlank()) {
        AlertDialog(
            onDismissRequest = {
                backupErrorTitle = ""
                backupErrorDetails = ""
            },
            title = { Text(backupErrorTitle.ifBlank { "Backup didn’t finish" }) },
            text = {
                Text(
                    text = backupErrorDetails,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        backupErrorTitle = ""
                        backupErrorDetails = ""
                    },
                ) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun ThemeColorPicker(
    selectedThemeColor: Int,
    onThemeColorSelected: (Int) -> Unit,
) {
    val options = listOf(
        ThemeColorOption(AppSettings.THEME_COLOR_PINK),
        ThemeColorOption(AppSettings.THEME_COLOR_AMBER),
        ThemeColorOption(AppSettings.THEME_COLOR_TEAL),
        ThemeColorOption(AppSettings.THEME_COLOR_VIOLET),
        ThemeColorOption(AppSettings.THEME_COLOR_RED),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            ThemeColorSwatch(
                option = option,
                selected = selectedThemeColor == option.value,
                onClick = { onThemeColorSelected(option.value) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeColorSwatch(
    option: ThemeColorOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = themePrimaryColor(option.value)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .semantics {
                contentDescription = when (option.value) {
                    AppSettings.THEME_COLOR_AMBER -> "Amber"
                    AppSettings.THEME_COLOR_TEAL -> "Teal"
                    AppSettings.THEME_COLOR_VIOLET -> "Violet"
                    AppSettings.THEME_COLOR_RED -> "Red"
                    else -> "Pink"
                }
            }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(accent)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                    shape = CircleShape,
                ),
        )
    }
}

private data class ThemeColorOption(
    val value: Int,
)

@Composable
private fun OcrLanguageDropdown(
    selectedLanguage: Int,
    onLanguageSelected: (Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val options = listOf(
        OcrLanguageOption(AppSettings.OCR_LANGUAGE_CHINESE_ENGLISH, "Chinese + English"),
        OcrLanguageOption(AppSettings.OCR_LANGUAGE_ENGLISH, "English"),
        OcrLanguageOption(AppSettings.OCR_LANGUAGE_CHINESE, "Chinese"),
        OcrLanguageOption(AppSettings.OCR_LANGUAGE_JAPANESE, "Japanese"),
    )
    val selectedLabel = options.firstOrNull { it.value == selectedLanguage }?.label
        ?: options.first().label

    Box {
        CompactSettingsOption(
            label = "$selectedLabel ▾",
            selected = false,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            fontSize = 14.sp,
                            color = if (option.value == selectedLanguage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = {
                        onLanguageSelected(option.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

private data class OcrLanguageOption(
    val value: Int,
    val label: String,
)

@Composable
private fun SettingsRow(
    label: String,
    action: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        action()
    }
}

@Composable
private fun CompactSettingsOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, role = Role.Button) { onClick() }
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
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
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, role = Role.Button) { onClick() }
            .heightIn(min = 48.dp)
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
